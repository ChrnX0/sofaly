package br.paulo.tvlauncher.home

import br.paulo.tvlauncher.observer.SofyBusTv
import br.paulo.tvlauncher.observer.SofyHabits
import br.paulo.tvlauncher.observer.SofyHabitsTv
import br.paulo.tvlauncher.observer.SofyLearnTv
import br.paulo.tvlauncher.observer.SofyScore
import br.paulo.tvlauncher.observer.SofyTv
import android.app.Activity
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import br.paulo.tvlauncher.AvisoTv
import br.paulo.tvlauncher.CaixaTv
import br.paulo.tvlauncher.Fundo
import br.paulo.tvlauncher.Prefs
import br.paulo.tvlauncher.R
import br.paulo.tvlauncher.Rotinas
import br.paulo.tvlauncher.alarm.AutoDesligar
import br.paulo.tvlauncher.alarm.Despertador
import br.paulo.tvlauncher.family.Perfil
import br.paulo.tvlauncher.family.PerfilCriancaTv
import br.paulo.tvlauncher.family.ModoVisita
import br.paulo.tvlauncher.settings.Extras
import br.paulo.tvlauncher.settings.ExtrasTv
import java.util.TimeZone

/**
 * Ideia 44: lê o uso da última semana (UsageStatsManager, a mesma permissão que o modo simples usa) e, se uma regra
 * de [Sugestoes] disparar, pergunta na HOME. Só confere uma vez por dia e só com a HOME na frente (nunca por cima
 * de um filme). Sem a permissão de uso, não há sinais e nada aparece.
 */
object SugestoesTv {
    private const val CONFERIDO = "sugestao_conferida_em"
    private const val ULTIMA = "sugestao_ultima_em"
    private const val DISPENSADAS = "sugestao_dispensadas"
    private const val DIA_MS = 24 * 60 * 60_000L
    private const val PERFIL_EM = "sugestao_perfil_em"
    private const val PERFIL_GAP_MS = 4 * 60 * 60_000L

    /** O que a Sofy achou nos hábitos (ideias 18, 20, 21 e 22): o necessário para a pergunta e para o "Ligar". */
    private class Sofy(val dormiu: Pair<Int, Int?>, val alarme: Pair<Despertador.Alarme, Int>?, val rotina: SofyHabits.Routine?, val teto: Int?)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(Prefs.EXTRAS, Context.MODE_PRIVATE)

    fun aoRetomarHome(a: Activity) {
        val p = prefs(a)
        val agora = System.currentTimeMillis()
        talvezTrocarPerfil(a, agora)
        if (agora - p.getLong(CONFERIDO, 0) < DIA_MS) return   // leitura barata antes de qualquer consulta
        if (ModoVisita.ativo(a) || Extras.ligado(a, Extras.Chave.MODO_SIMPLES)) return
        p.edit().putLong(CONFERIDO, agora).apply()
        val app = a.applicationContext
        Fundo.rodar("sugestoes") {
            val momentos = usoDaSemana(app, agora)
            val fuso = TimeZone.getDefault()
            val eventos = runCatching { SofyBusTv.events(app) }.getOrDefault(emptyList())
            val sofy = Sofy(
                dormiu = runCatching { SofyHabitsTv.dozed(app, eventos) }.getOrDefault(0 to null),
                alarme = runCatching { SofyHabitsTv.snooze(app, eventos) }.getOrNull(),
                rotina = runCatching { SofyHabitsTv.routine(app, agora) }.getOrNull(),
                teto = runCatching { SofyHabitsTv.nightCeiling(app) }.getOrNull(),
            )
            val s = Sugestoes.Sinais(
                noitesMadrugada = Sugestoes.noites(momentos, fuso, 0),
                noitesTarde = Sugestoes.noites(momentos, fuso, 22),
                desligarSozinhaMin = AutoDesligar.minutos(app),
                filtroLigado = Extras.ligado(app, Extras.Chave.FILTRO_NOTURNO),
                dispensadas = p.getStringSet(DISPENSADAS, emptySet()) ?: emptySet(),
                ultimaEm = p.getLong(ULTIMA, 0),
                agora = agora,
                noitesDormiu = sofy.dormiu.first,
                desligarPara = sofy.dormiu.second,
                alarmeAdiar = sofy.alarme?.second,
                rotina = sofy.rotina != null,
                tetoNoite = sofy.teto,
            )
            val tipo = Sugestoes.escolher(s) ?: return@rodar
            // Uma voz só (Sofy, ideia 3): a sugestão passa pelo mesmo freio das falas da Sofy; sem licença, tenta outro dia.
            if (!SofyTv.mayAsk(app, chave(tipo))) { p.edit().putLong(CONFERIDO, agora - DIA_MS / 2).apply(); return@rodar }
            a.runOnUiThread {
                if (a.isFinishing || a.isDestroyed || !a.hasWindowFocus()) return@runOnUiThread
                p.edit().putLong(ULTIMA, System.currentTimeMillis()).apply()
                SofyTv.asked(app, chave(tipo))
                perguntar(a, tipo, s, sofy)
            }
        }
    }

    /** Momentos em que a TV estava em uso (um app veio para a frente ou a tela acendeu) nos últimos 7 dias. */
    private fun usoDaSemana(ctx: Context, agora: Long): List<Long> = try {
        val ev = ctx.getSystemService(UsageStatsManager::class.java).queryEvents(agora - 7 * DIA_MS, agora)
        val e = UsageEvents.Event()
        val saida = ArrayList<Long>()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED || e.eventType == UsageEvents.Event.SCREEN_INTERACTIVE) saida += e.timeStamp
        }
        saida
    } catch (_: Exception) { emptyList() }

    private fun chave(tipo: Sugestoes.Tipo) = "suggestion.${tipo.name}"

    /** A resposta conta para a Sofy: recusa seguida cala o tipo (SofyPolicy) e o placar anota o acerto (ideia 34). */
    private fun respondeu(a: Activity, tipo: Sugestoes.Tipo, o: SofyScore.Outcome) {
        val app = a.applicationContext
        Fundo.rodar("sugestao-resposta") {
            runCatching { SofyTv.answered(app, chave(tipo), o != SofyScore.Outcome.HIT) }
            runCatching { SofyLearnTv.record(app, SofyScore.Action.SUGGESTION, o, detail = tipo.name) }
        }
    }

    private fun hhmm(min: Int) = "%02d:%02d".format(min / 60, min % 60)

    private fun perguntar(a: Activity, tipo: Sugestoes.Tipo, s: Sugestoes.Sinais, sofy: Sofy) {
        val (titulo, texto) = when (tipo) {
            Sugestoes.Tipo.DESLIGAR_SOZINHA -> a.getString(R.string.sug_desligar_titulo) to a.getString(R.string.sug_desligar, s.noitesMadrugada)
            Sugestoes.Tipo.FILTRO_NOTURNO -> a.getString(R.string.sug_filtro_titulo) to a.getString(R.string.sug_filtro, s.noitesTarde)
            Sugestoes.Tipo.DESLIGAR_MAIS_CEDO -> a.getString(R.string.sofy_sug_cedo_titulo) to
                a.getString(R.string.sofy_sug_cedo, s.noitesDormiu, s.desligarPara ?: 0, s.desligarSozinhaMin)
            Sugestoes.Tipo.ALARME_MAIS_TARDE -> {
                val (al, min) = sofy.alarme!!
                val de = al.hora * 60 + al.minuto
                a.getString(R.string.sofy_sug_alarme_titulo) to a.getString(R.string.sofy_sug_alarme, hhmm(de), min, hhmm(de + min))
            }
            Sugestoes.Tipo.ROTINA -> {
                val r = sofy.rotina!!
                a.getString(R.string.sofy_sug_rotina_titulo) to
                    a.getString(R.string.sofy_sug_rotina, Rotinas.rotuloApp(a, r.pkg) ?: r.pkg, hhmm(r.minuteOfDay), r.days)
            }
            Sugestoes.Tipo.TETO_NOITE -> a.getString(R.string.sofy_sug_teto_titulo) to a.getString(R.string.sofy_sug_teto, sofy.teto ?: 0)
        }
        CaixaTv(a).setTitle(titulo).setMessage(texto)
            .setPositiveButton(a.getString(R.string.sug_ligar)) { _, _ -> aplicar(a, tipo, sofy); respondeu(a, tipo, SofyScore.Outcome.HIT) }
            .setNegativeButton(a.getString(R.string.sug_agora_nao)) { _, _ -> respondeu(a, tipo, SofyScore.Outcome.MISS) }
            .setNeutralButton(a.getString(R.string.sug_nunca)) { _, _ ->
                respondeu(a, tipo, SofyScore.Outcome.UNDONE)
                val p = prefs(a)
                p.edit().putStringSet(DISPENSADAS, HashSet(p.getStringSet(DISPENSADAS, emptySet()) ?: emptySet()) + tipo.name).apply()
            }
            .show()
    }

    private fun aplicar(a: Activity, tipo: Sugestoes.Tipo, sofy: Sofy) {
        val app = a.applicationContext
        when (tipo) {
            Sugestoes.Tipo.DESLIGAR_SOZINHA -> AutoDesligar.definir(a, 120)   // 2 h sem ninguém apertar o controle
            Sugestoes.Tipo.FILTRO_NOTURNO -> if (!Extras.ligado(a, Extras.Chave.FILTRO_NOTURNO)) ExtrasTv.alternar(a, Extras.Chave.FILTRO_NOTURNO)
            Sugestoes.Tipo.DESLIGAR_MAIS_CEDO -> sofy.dormiu.second?.let { AutoDesligar.definir(a, it) }
            Sugestoes.Tipo.ALARME_MAIS_TARDE -> sofy.alarme?.let { (al, min) ->
                val novo = al.hora * 60 + al.minuto + min
                Fundo.rodar("sofy-alarme") { Despertador.salvar(app, al.copy(hora = novo / 60, minuto = novo % 60)) }
            }
            Sugestoes.Tipo.ROTINA -> sofy.rotina?.let { r ->
                Fundo.rodar("sofy-rotina") {
                    Rotinas.salvar(app, Rotinas.Rotina(0, r.minuteOfDay / 60, r.minuteOfDay % 60, r.weekdays, Rotinas.Acao.ABRIR, pacote = r.pkg))
                }
            }
            Sugestoes.Tipo.TETO_NOITE -> sofy.teto?.let { Extras.definirNumero(a, SofyHabitsTv.KEY_NIGHT_CEILING, it) }
        }
        val feito = when (tipo) {
            Sugestoes.Tipo.DESLIGAR_SOZINHA, Sugestoes.Tipo.FILTRO_NOTURNO -> a.getString(R.string.sug_feito)
            else -> a.getString(R.string.sofy_sug_feito)
        }
        AvisoTv.rapido(a, a.getString(R.string.sug_feito_titulo), feito)
        runCatching { SofyBusTv.emit(app, br.paulo.tvlauncher.observer.SofyBus.Source.SOFY, "suggestion_applied", detail = tipo.name) }
    }

    /**
     * Ideia 17: quem está vendo, sem câmera. Os apps abertos na última meia hora são a cara de outro perfil = pergunta se
     * troca (com o PIN dos pais quando sai de um perfil de criança, como a troca à mão). No máximo a cada 4 h, pelo
     * mesmo freio de uma voz só; "Não sugerir mais" cala para sempre.
     */
    private fun talvezTrocarPerfil(a: Activity, agora: Long) {
        val p = prefs(a)
        if (agora - p.getLong(PERFIL_EM, 0) < PERFIL_GAP_MS) return
        if (ModoVisita.ativo(a) || Extras.ligado(a, Extras.Chave.MODO_SIMPLES)) return
        if ("TROCAR_PERFIL" in (p.getStringSet(DISPENSADAS, emptySet()) ?: emptySet())) return
        p.edit().putLong(PERFIL_EM, agora).apply()
        val app = a.applicationContext
        Fundo.rodar("sofy-quem-ve") {
            val conta = runCatching { SofyHabitsTv.whoIsWatching(app, agora) }.getOrNull() ?: return@rodar
            val chave = "suggestion.TROCAR_PERFIL"
            if (!SofyTv.mayAsk(app, chave)) return@rodar
            val nome = Perfil.nome(app, conta)
            a.runOnUiThread {
                if (a.isFinishing || a.isDestroyed || !a.hasWindowFocus()) return@runOnUiThread
                SofyTv.asked(app, chave)
                fun resp(o: SofyScore.Outcome) = Fundo.rodar("sugestao-resposta") {
                    runCatching { SofyTv.answered(app, chave, o != SofyScore.Outcome.HIT) }
                    runCatching { SofyLearnTv.record(app, SofyScore.Action.SUGGESTION, o, detail = "TROCAR_PERFIL") }
                }
                CaixaTv(a).setTitle(a.getString(R.string.sofy_sug_perfil_titulo)).setMessage(a.getString(R.string.sofy_sug_perfil, nome))
                    .setPositiveButton(a.getString(R.string.sofy_sug_trocar)) { _, _ ->
                        resp(SofyScore.Outcome.HIT)
                        PerfilCriancaTv.trocar(a, Perfil.ativa(a), conta) { trocou ->
                            val m = a as? br.paulo.tvlauncher.MainActivity
                            if (trocou && m != null && !m.isDestroyed) { m.atualizarFotoPerfil(); m.reorganizar(); m.preencherContinuar() }
                        }
                    }
                    .setNegativeButton(a.getString(R.string.sug_agora_nao)) { _, _ -> resp(SofyScore.Outcome.MISS) }
                    .setNeutralButton(a.getString(R.string.sug_nunca)) { _, _ ->
                        resp(SofyScore.Outcome.UNDONE)
                        p.edit().putStringSet(DISPENSADAS, HashSet(p.getStringSet(DISPENSADAS, emptySet()) ?: emptySet()) + "TROCAR_PERFIL").apply()
                    }
                    .show()
            }
        }
    }
}
