package br.paulo.tvlauncher

import android.app.Application
import br.paulo.tvlauncher.family.UsoLocal
import br.paulo.tvlauncher.home.IdeiasInicio
import br.paulo.tvlauncher.home.Moldura
import br.paulo.tvlauncher.home.SugestoesTv
import br.paulo.tvlauncher.input.VozFoco
import br.paulo.tvlauncher.mcp.BuildInfo
import br.paulo.tvlauncher.mcp.PedidoIa
import br.paulo.tvlauncher.observer.LeakWatch
import br.paulo.tvlauncher.observer.ObserverCore
import br.paulo.tvlauncher.observer.ObserverTv
import br.paulo.tvlauncher.observer.RegistroErros
import br.paulo.tvlauncher.observer.VitaisTv
import br.paulo.tvlauncher.player.PlayerLogos
import br.paulo.tvlauncher.privilege.CompilarAot
import br.paulo.tvlauncher.settings.AltoContraste
import br.paulo.tvlauncher.settings.CobrancaPlay
import br.paulo.tvlauncher.settings.TamanhoTexto

/**
 * Classe do app (05/11/2026). Roda uma vez em cada processo do launcher, antes de qualquer tela ou receiver.
 * Hoje só liga o registro local de erros (RegistroErros.kt), para que um crash em QUALQUER ponto (HOME, boot,
 * acessibilidade, painel) fique gravado. Mantenha leve: tudo aqui atrasa a abertura da HOME.
 */
class LauncherApp : Application() {
    /** No :player, ajustes pelo dono do processo principal (CrossProcessPrefs.kt); no principal, o de sempre. */
    override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences =
        CrossProcessPrefs.prefs(this, name ?: "null", { super.getSharedPreferences(name, mode) })
    companion object {
        /** O app, para as frases traduzidas de quem não tem Context (Textos.kt). Vale desde o onCreate do processo. */
        lateinit var instancia: LauncherApp
            private set
    }

    /**
     * O Android avisa quando falta memória (12/11/2026, Sala: ZRAM 100% cheia e travadinhas até nas Configurações do
     * Android). Antes o launcher não devolvia nada. Agora solta os caches de imagem que se refazem do disco.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        ObserverTv.publish(ObserverCore.Signal.MEMORY_PRESSURE, level.toLong())   // o Observador anota o aperto (só no processo principal)
        if (Memoria.soltarCaches(level)) {
            PlayerLogos.soltarMemoria()
            br.paulo.tvlauncher.home.IconSetArt.soltar()   // arte dos conjuntos de ícones: volta do disco no próximo desenho
            android.util.Log.i("TvLauncher.Memoria", "onTrimMemory($level): caches soltos")
        }
    }

    override fun onCreate() {
        super.onCreate()
        instancia = this
        AppContext.app = this   // txt/txtN e os módulos de área (refatoração G)
        // Módulos de área: a assinatura do relay (cota do dia por TV) usa a chave da TV, que mora no app.
        RelaySign.headers = { m, p, b -> br.paulo.tvlauncher.relay.RelayTv.assinatura(m, p, b) }
        // Módulos de área: o aviso na tela (TvNotice, no :core) é desenhado pelo AvisoTv, que mora no app.
        TvNotice.display = AvisoTv.comoTvNotice
        // Módulo :weather: a fonte pelo plano mora no app.
        br.paulo.tvlauncher.weather.WeatherHost.kind = { c -> br.paulo.tvlauncher.weather.DataSources.weather(c) }
        // Módulo :media: fichas pelo TMDB conforme contrato, plano e chave (DataSources, no app).
        br.paulo.tvlauncher.player.MediaHost.tmdb = { c -> br.paulo.tvlauncher.weather.DataSources.tmdb(c) }
        br.paulo.tvlauncher.player.MediaHost.simklSemLogin = { c -> br.paulo.tvlauncher.weather.DataSources.simklSemLogin(c) }
        br.paulo.tvlauncher.player.MediaHost.guideWords = { c -> LembretesTv.palavrasParaGuia(c) }
        br.paulo.tvlauncher.player.MediaHost.onGuideUpdated = { c -> LembretesTv.varrerDepois(c) }
        br.paulo.tvlauncher.player.MediaHost.simklLibraryIds = { c -> br.paulo.tvlauncher.player.ServicosSync.idsSimklDaBiblioteca(c) }
        // O processo ":dhizuku_usuario" roda código de OUTRO app (UserService do Dhizuku): uma exceção dele não pode
        // contar como queda do launcher (RegistroErros/VigiaAtualizacao marcariam a versão como ruim), e nada da tela
        // do launcher precisa existir lá.
        if (android.os.Build.VERSION.SDK_INT >= 28 && getProcessName().endsWith(":dhizuku_usuario")) return
        // Troca de pacote (PackageMigration.kt): o processo ":migration" só copia dados. Nada pode subir nele, e nada pode
        // ler preferências antes da cópia (elas ficariam velhas na memória).
        if (android.os.Build.VERSION.SDK_INT >= 28 && getProcessName().endsWith(":migration")) return
        RegistroErros.instalar(this)
        if (android.os.Build.VERSION.SDK_INT < 28 || getProcessName() == packageName) PackageMigration.retomarSeParou(this)
        // Atualização vigiada: anota a versão que abriu (versão nova = começa a vigiar) e liga o modo seguro, que não
        // deixa abrir a tela que derrubou uma versão ruim. Logo depois do RegistroErros: uma queda já conta.
        VigiaAtualizacao.aoIniciar(this)
        VigiaAtualizacao.vigiarTelas(this)
        br.paulo.tvlauncher.home.GpuTrim.vigiar(this)   // etapa 4b da G: tela apagada há 30 s = GPU da HOME solta
        br.paulo.tvlauncher.parts.PartsInstall.install(this)   // refatoração G: parte que falta é pedida quando a função precisa
        Moldura.instalar(this)   // a hora da HOME no mesmo lugar nas telas de lista (Moldura.kt)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: android.app.Activity) = Estilo.tirarCorDaJanelaSeCoberta(a)   // Estilo.kt
            // Ideia 47 (TamanhoTexto.kt): o tamanho do texto escolhido, antes do onCreate de cada tela (Android 10+).
            override fun onActivityPreCreated(a: android.app.Activity, salvo: android.os.Bundle?) {
                CrossProcessPrefs.refresh()   // no :player, antes de ler qualquer ajuste: busca de novo no dono (CrossProcessPrefs.kt)
                TamanhoTexto.aplicar(a)
                AltoContraste.aplicar(a)   // elevação 16.3
                br.paulo.tvlauncher.settings.CardStyle.aplicar(a)   // cor de destaque e cantos (Ajuste fino)
                br.paulo.tvlauncher.settings.FocusStyle.ler(a)   // forma do foco: as cores "no foco" do Estilo dependem dela
            }
            override fun onActivityCreated(a: android.app.Activity, salvo: android.os.Bundle?) {
                if (salvo == null) UsoLocal.contarTela(a)   // ideia 67
                br.paulo.tvlauncher.settings.FontChoice.aplicarNaTela(a)   // fonte escolhida também nos textos que só herdam a do tema
            }
            override fun onActivityResumed(a: android.app.Activity) {
                VozFoco.aoRetomar(a)   // ideia 48 (VozFoco.kt)
                if (a is MainActivity) SugestoesTv.aoRetomarHome(a)   // ideia 44 (SugestoesTv.kt): no máximo 1 vez por dia
            }
            override fun onActivityPaused(a: android.app.Activity) {}
            override fun onActivityStopped(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, s: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) = LeakWatch.aoDestruir(a)   // adb: EXTRAS --es o vazamentos
        })
        // A IA na nuvem saiu em 05/11/2026: apaga a chave cifrada que ela tenha deixado (fora da thread da tela).
        Fundo.rodar("limpa-ia") { PedidoIa.apagarRestosDaNuvem(this) }
        // Celular como parceiro (24/11/2026): a Sofy percebe a troca de saída de som e passa a usar o atraso medido nela.
        // Só no processo principal: no :player o mesmo aviso sairia em dobro.
        if (android.os.Build.VERSION.SDK_INT < 28 || getProcessName() == packageName)
            runCatching { br.paulo.tvlauncher.companion.AudioSyncTv.watch(this) }
        // Backup noturno: reagenda a cada processo novo (uma atualização do app apaga os alarmes dele). Só no processo
        // principal (o player roda em ":player") e fora da thread da tela (lê preferências do disco).
        if (android.os.Build.VERSION.SDK_INT < 28 || getProcessName() == packageName) {
            Fundo.rodar("backup-noturno") { runCatching { BackupNoturno.agendar(this) } }
            // Versão nova ainda sem AOT: compila quando a tela apagar (CompilarAot.kt). Só registra um receiver.
            CompilarAot.iniciar(this)
            CobrancaPlay.atualizar(this)   // build da Play: confere com a loja o plano comprado (no-op no APK do site)
            ObserverTv.start(this)   // Observador (ObserverCore.kt): só neste processo os sensores publicam
            CompatibilidadeTv.vigiar(this)   // mede o "acordar" do relatório de compatibilidade (CompatibilidadeTv.kt)
            // Ideias de 12/11/2026: lembretes reagendados (atualizar o app apaga os alarmes), aviso no celular de versão
            // nova e de erro novo, e a guarda de desempenho (anota a versão para comparar os quadros com a anterior).
            Fundo.rodar("ideias-12-11") { runCatching { IdeiasInicio.aoIniciar(this) } }
            // Modo hóspede (degrau 2): atualizar o app apaga os alarmes; reagenda a hora da saída.
            Fundo.rodar("vitais") { runCatching { VitaisTv.aoIniciar(this) } }   // degrau 2: quedas e travadas por dia
            Fundo.rodar("hospede") { runCatching { ModoHospede.agendar(this) }; runCatching { AgendaPerfisTv.agendar(this) } }
        }
        BuildInfo.versao = BuildConfig.VERSION_NAME   // o MCP informa a versão no "initialize"
    }
}
