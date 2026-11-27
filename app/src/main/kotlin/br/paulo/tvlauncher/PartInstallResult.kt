package br.paulo.tvlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import br.paulo.tvlauncher.mcp.TunelTailscale
import br.paulo.tvlauncher.parts.Parts
import br.paulo.tvlauncher.parts.PartsInstall

/**
 * Resposta do PackageInstaller para uma parte instalada sob demanda (refatoração G, PartsInstall). Fica no pacote raiz
 * como os outros componentes do manifesto. O launcher continua aberto (setDontKillApp): o túnel sobe aqui mesmo, e a
 * tradução vale na próxima vez que o player abrir (ele roda no processo dele).
 */
class PartInstallResult : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val part = intent.getStringExtra(PartsInstall.EXTRA_PART) ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Sem Device Owner o Android pede confirmação: mostra a tela dele (como no Atualizador).
            @Suppress("DEPRECATION")
            val confirmar = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            ctx.startActivity(confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val app = ctx.applicationContext
        if (status != PackageInstaller.STATUS_SUCCESS) {
            PartsInstall.note(app, part, "falhou: $status ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
            return
        }
        PartsInstall.note(app, part, "instalada")
        if (part == Parts.TUNNEL) TunelTailscale.ajustar(app)
    }
}
