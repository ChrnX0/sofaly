package br.paulo.tvlauncher.observer

/**
 * A Sofy guardando a casa (24/11/2026, ideias 42, 43, 46, 47 e 48 do estudo "extrapolar a Sofy").
 *
 * Por quê: algumas coisas só a TV vê e ninguém conta: alguém apertando VOLTAR sem parar porque se perdeu, uma tecla
 * presa (controle caiu no sofá), um app recém-instalado que pede poder de mais, alguém errando o PIN do painel, uma
 * atualização do Google que quebrou a busca por voz. Para quê: ela percebe, ajuda na hora quando dá (a dica de como
 * voltar) e conta o resto pela Ilha e pelo Diário. Ela nunca desinstala nem bloqueia nada sozinha.
 * Como: regras pequenas e puras. Pura: SofyGuardTest.
 */
object SofyGuard {
    // ---------------------------------------------------------------- 42: percebe quem se perdeu
    const val LOST_BACKS = 6
    const val LOST_BACK_WINDOW_MS = 15_000L
    const val LOST_HOMES = 3
    const val LOST_HOME_WINDOW_MS = 20_000L
    const val KEY_BACK = 4
    const val KEY_HOME = 3

    /** Conta VOLTAR e INÍCIO seguidos (só o "desce" de cada toque). [key] devolve true quando o padrão de "perdido" aparece. */
    class LostMeter {
        private val backs = ArrayDeque<Long>()
        private val homes = ArrayDeque<Long>()

        @Synchronized fun key(keyCode: Int, now: Long): Boolean {
            val (q, n, w) = when (keyCode) {
                KEY_BACK -> Triple(backs, LOST_BACKS, LOST_BACK_WINDOW_MS)
                KEY_HOME -> Triple(homes, LOST_HOMES, LOST_HOME_WINDOW_MS)
                else -> { backs.clear(); homes.clear(); return false }   // navegou (setas, OK): não está perdido
            }
            q.addLast(now)
            while (q.isNotEmpty() && now - q.first() > w) q.removeFirst()
            if (q.size < n) return false
            q.clear()
            return true
        }
    }

    // ---------------------------------------------------------------- 43: tecla presa
    /** Volume segurado é normal; o resto segurado por ~8 s (0,5 s + 150 repetições de 50 ms) é tecla presa ou controle apertado no sofá. */
    const val STUCK_REPEATS = 150
    val VOLUME_KEYS = setOf(24, 25, 164)

    fun stuck(keyCode: Int, repeatCount: Int): Boolean = repeatCount >= STUCK_REPEATS && keyCode !in VOLUME_KEYS

    /**
     * Os recados da tecla presa (43) e do PIN errado (47) vão à Ilha: são sugestão, não "só observou". Com OBSERVED (até
     * 25/11/2026) a SofyPolicy mandava os dois só ao Diário e o ObserverTv.notes ainda os escondia (tipo nunca "ativo"):
     * o aviso prometido nunca aparecia (teste da noite na Sala: tecla segurada 9,5 s, recado nenhum).
     */
    fun keyStuckNote(keyName: String, at: Long): ObserverCore.Note =
        ObserverCore.sofyNote(ObserverCore.Kind.KEY_STUCK, at, ObserverCore.Step.SUGGESTS, listOf(keyName))

    fun pinWrongNote(count: Int, at: Long): ObserverCore.Note =
        ObserverCore.sofyNote(ObserverCore.Kind.PANEL_PIN_WRONG, at, ObserverCore.Step.SUGGESTS, listOf(count.toString()), ObserverCore.Severity.CRITICAL)

    // ---------------------------------------------------------------- 46: app novo com poder demais
    /** Poderes que, sozinhos, já merecem um aviso: ler a tela inteira, administrar o aparelho, instalar apps, mexer em ajustes protegidos, desviar a rede. */
    val CRITICAL = mapOf(
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to "accessibility",
        "android.permission.BIND_DEVICE_ADMIN" to "device_admin",
        "android.permission.REQUEST_INSTALL_PACKAGES" to "install_apps",
        "android.permission.WRITE_SECURE_SETTINGS" to "secure_settings",
        "android.permission.BIND_VPN_SERVICE" to "vpn",
    )
    /** Poderes fortes: três juntos num app novo pedem atenção. */
    val STRONG = mapOf(
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" to "notifications",
        "android.permission.SYSTEM_ALERT_WINDOW" to "draw_over",
        "android.permission.READ_LOGS" to "read_logs",
        "android.permission.PACKAGE_USAGE_STATS" to "usage",
        "android.permission.RECORD_AUDIO" to "microphone",
        "android.permission.CAMERA" to "camera",
        "android.permission.ACCESS_FINE_LOCATION" to "location",
        "android.permission.READ_CONTACTS" to "contacts",
    )

    /** Os poderes de um app: as permissões que ele pede e as que os serviços dele exigem (acessibilidade, VPN...). */
    fun powers(requested: Collection<String>, servicePerms: Collection<String>): List<String> {
        val all = (requested + servicePerms).toSet()
        return CRITICAL.filterKeys { it in all }.values.toList() + STRONG.filterKeys { it in all }.values.toList()
    }

    fun tooPowerful(p: List<String>): Boolean = p.any { it in CRITICAL.values } || p.count { it in STRONG.values } >= 3

    /** Pacotes novos desde a última olhada (a primeira olhada não acusa ninguém: tudo o que já estava fica). */
    fun newPackages(before: Set<String>?, now: Set<String>): Set<String> = if (before == null) emptySet() else now - before

    // ---------------------------------------------------------------- 47: PIN errado no painel
    const val PIN_FAILS = 5
    const val PIN_WINDOW_MS = 3_600_000L

    fun pinAlarm(events: List<SofyBus.Event>, now: Long): Int? =
        events.count { it.source == SofyBus.Source.HOUSE && it.kind == "panel_pin_wrong" && now - it.at in 0..PIN_WINDOW_MS }
            .takeIf { it >= PIN_FAILS }

    // ---------------------------------------------------------------- 48: atualização da Play quebrou algo
    /** Os pacotes do Google cuja atualização pode quebrar o que a casa usa (busca por voz, loja, launcher, Chromecast). */
    val WATCHED = setOf("com.android.vending", "com.google.android.gms", "com.google.android.apps.tv.launcherx",
        "com.google.android.katniss", "com.google.android.apps.mediashell", "com.android.tv.settings")

    /** O que funcionava antes e parou agora. Check que não existia antes não conta (não sabemos se já falhava). */
    fun regressions(before: Map<String, Boolean>, now: Map<String, Boolean>): List<String> =
        now.filter { (k, ok) -> !ok && before[k] == true }.keys.sorted()

    /** Algum pacote vigiado mudou de versão? [before]/[now] = pacote → versionCode. */
    fun watchedUpdated(before: Map<String, Long>, now: Map<String, Long>): List<String> =
        WATCHED.filter { p -> now[p] != null && before[p] != null && now[p] != before[p] }.sorted()
}
