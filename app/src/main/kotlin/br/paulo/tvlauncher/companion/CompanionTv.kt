package br.paulo.tvlauncher.companion

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import br.paulo.tvlauncher.CalibracaoActivity
import org.json.JSONObject

/**
 * O lado Android do "celular como parceiro" (24/11/2026, proposta B): de qual TV se trata, onde ficam as medidas e
 * como cada uma chega à Sofy.
 *
 * Por quê: medir pelo celular só vale se o sistema lembrar o resultado e souber quando ele deixou de valer (outra TV no
 * HDMI, outro fone). Para quê: a Sofy sabe, sem perguntar, se a TV da frente já foi calibrada e qual atraso de som usar
 * em cada saída. Como: cada TV ganha uma chave pelo EDID (fabricante + produto + ano, lidos pelo Android, sem shell);
 * as medidas ficam na pref "companion", que vai no backup (é da casa: a TV da pessoa); e cada medida vira um evento do
 * barramento da Sofy ([sofy]).
 */
object CompanionTv {
    private const val TAG = "TvLauncher.Companion"
    const val PREFS = "companion"

    /**
     * A chave da TV ligada no HDMI. Android 12+ lê o EDID (DeviceProductInfo); antes disso, ou sem EDID, vale "hdmi":
     * a medida ainda serve para a TV de sempre, só não percebe a troca.
     */
    fun displayKey(ctx: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return "hdmi"
        val d = runCatching { ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) }.getOrNull()
        val info = runCatching { d?.deviceProductInfo }.getOrNull() ?: return "hdmi"
        return CompanionRules.displayKey(info.manufacturerPnpId, info.productId, info.modelYear, info.name)
    }

    /** O nome da TV que o EDID dá (ex.: "LG TV"), para o painel mostrar. Pode não haver. */
    fun displayName(ctx: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val d = runCatching { ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) }.getOrNull()
        return runCatching { d?.deviceProductInfo?.name }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.take(40)
    }

    // ------------------------------------------------------------------ imagem

    /** A medida de imagem desta TV (o que o painel e a Sofy leem). Vazio quando ela nunca foi calibrada. */
    fun picture(ctx: Context, key: String = displayKey(ctx)): JSONObject =
        runCatching { JSONObject(prefs(ctx).getString("picture.$key", null) ?: "{}") }.getOrDefault(JSONObject())

    /** Grava uma parte da medida ([part] = overscan, black, white, color) e avisa a Sofy. */
    @Synchronized fun savePicture(ctx: Context, part: String, r: PictureMath.Result, at: Long = System.currentTimeMillis()) {
        val key = displayKey(ctx)
        val atual = picture(ctx, key)
        atual.put(part, JSONObject().put("verdict", r.verdict.name).put("advice", r.advice.name).put("detail", r.detail).put("at", at))
        prefs(ctx).edit().putString("picture.$key", atual.toString()).putString("last_display", key).apply()
        sofy(ctx, CompanionRules.pictureKind(part), r.verdict != PictureMath.Verdict.UNSURE, CompanionRules.eventDetail(r))
    }

    /** As TVs já vistas por esta caixa e se cada uma tem medida. Serve à Sofy para notar "TV nova". */
    fun knownDisplays(ctx: Context): Set<String> =
        prefs(ctx).all.keys.filter { it.startsWith("picture.") }.map { it.removePrefix("picture.") }.toSet()

    // ------------------------------------------------------------------ a tela de padrões, comandada pelo celular

    /** O padrão que a TV deve mostrar agora, ou null = a tela de padrões normal (◀ ▶ no controle). */
    @Volatile var phonePattern: PictureMath.Pattern? = null
        private set
    /** Quando o celular mandou o último padrão: sem pedido por [PHONE_IDLE_MS], a TV sai sozinha da medição. */
    @Volatile var phoneAt = 0L
        private set
    const val PHONE_IDLE_MS = 90_000L

    @Volatile private var listener: (() -> Unit)? = null
    fun listen(l: (() -> Unit)?) { listener = l }

    /** O celular pediu um padrão: abre a tela, se não estiver aberta, e troca o desenho. */
    fun showPattern(ctx: Context, p: PictureMath.Pattern) {
        phonePattern = p; phoneAt = System.currentTimeMillis()
        val l = listener
        if (l != null) l() else ctx.startActivity(Intent(ctx, CalibracaoActivity::class.java)
            .putExtra(CalibracaoActivity.EXTRA_PHONE, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    /** O celular terminou (ou desistiu): a tela de padrões fecha e a TV volta ao que estava. */
    fun stopPattern() {
        phonePattern = null; phoneAt = 0L
        listener?.invoke()
    }

    // ------------------------------------------------------------------ Sofy

    /**
     * Manda a medida para o barramento da Sofy (SofyBusTv, thread "Sofy", 24/11/2026). O barramento entra na main por
     * outro PR; até lá a chamada procura a classe pelo nome e, sem ela, só registra no log. Nunca lança erro.
     * [detail] já vem curto e sem dado pessoal ([CompanionRules.eventDetail]).
     */
    fun sofy(ctx: Context, kind: String, ok: Boolean, detail: String) {
        runCatching {
            val bus = Class.forName("br.paulo.tvlauncher.observer.SofyBusTv")
            val source = Class.forName("br.paulo.tvlauncher.observer.SofyBus\$Source").enumConstants!!
                .first { (it as Enum<*>).name == "MEASURE" }
            val emit = bus.methods.first { it.name == "emit" && it.parameterCount >= 5 }
            val inst = bus.getField("INSTANCE").get(null)
            val args = arrayOfNulls<Any>(emit.parameterCount)
            args[0] = ctx; args[1] = source; args[2] = kind; args[3] = ok; args[4] = detail
            if (emit.parameterCount > 5) args[5] = 0L
            emit.invoke(inst, *args)
        }.onFailure { Log.i(TAG, "sofy $kind ok=$ok $detail") }
    }

    internal fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Regras puras do [CompanionTv] (CompanionRulesTest). */
object CompanionRules {
    /** Chave da TV pelo EDID: só letras, números e "-"; nada de nome livre na chave (o nome vai à parte). */
    fun displayKey(pnp: String?, productId: String?, year: Int, name: String?): String {
        val partes = listOf(pnp, productId, year.takeIf { it > 0 }?.toString())
            .map { it.orEmpty().filter { c -> c.isLetterOrDigit() }.take(12) }
        if (partes.all { it.isEmpty() }) return name.orEmpty().filter { it.isLetterOrDigit() }.take(24).ifEmpty { "hdmi" }
        return partes.joinToString("-")
    }

    /** O "kind" do evento da Sofy para cada parte da medida (nomes combinados com a thread da Sofy). */
    fun pictureKind(part: String) = when (part) {
        "overscan" -> "picture_overscan"
        "black" -> "picture_black"
        "white" -> "picture_white"
        "color" -> "picture_color_gain"
        else -> "picture_$part"
    }

    /** O detalhe do evento: até 120 caracteres, sem TAB nem quebra de linha. */
    fun eventDetail(r: PictureMath.Result): String =
        "${r.verdict.name.lowercase()} ${r.advice.name.lowercase()} ${r.detail}".replace(Regex("[\\t\\r\\n]"), " ").take(120)
}
