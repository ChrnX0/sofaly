package br.paulo.tvlauncher.backup

import android.content.Context
import android.util.Log
import br.paulo.tvlauncher.home.AppStorage
import br.paulo.tvlauncher.home.abrir
import br.paulo.tvlauncher.panel.Smb
import br.paulo.tvlauncher.player.ContaMidia
import br.paulo.tvlauncher.player.MidiaArmazem
import br.paulo.tvlauncher.player.MidiaContas
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumSet
import java.util.Locale

/**
 * Cópia de segurança do pen drive ANTES de formatar (proposta C, 20/11/2026) e o "desfazer".
 *
 * Porquê: formatar apaga tudo e o pen drive costuma ter fotos e gravações. Para quê: dá para formatar sem medo. Como:
 * copia a parte comum, arquivo por arquivo, para uma pasta do Na rede (SMB, a mesma conta que os players usam para
 * gravar), em "Sofaly USB backup/<data>". Sem pasta de rede, a tela ensina o caminho pelo PC (o pen drive aparece no
 * Windows como "Drive USB" dentro da TV, MtpNaRede).
 */
object StorageBackup {
    private const val TAG = "TvLauncher.Storage"
    const val PASTA = "Sofaly USB backup"

    /** Contas SMB do Na rede (as que aceitam gravar). */
    fun destinos(ctx: Context): List<ContaMidia> = MidiaArmazem.contas(ctx).filter { it.tipo == ContaMidia.SMB }

    /**
     * Pastas que não deu para ler (#556). Sem isto, walkTopDown pulava a pasta calado e a cópia dizia "deu certo":
     * formatar depois apagava o que ficou de fora. Quem recebe esta exceção NÃO pode oferecer a formatação.
     */
    class PastaIlegivel(val pastas: List<String>) : Exception("sem ler: " + pastas.take(3).joinToString())

    /** Percorre [raiz] e junta em [ruins] (caminho relativo) toda pasta cujo conteúdo não deu para listar. */
    private fun percorrer(raiz: File, ruins: MutableList<String>): Sequence<File> =
        raiz.walkTopDown().onEnter { !it.name.startsWith(".android_secure") }
            .onFail { f, _ -> ruins += f.relativeTo(raiz).invariantSeparatorsPath.ifEmpty { "/" } }

    /**
     * Bytes e arquivos da parte comum (para a tela dizer o tamanho antes de copiar). Lança [PastaIlegivel] se alguma
     * pasta não abriu: aí o tamanho não vale e "vazio" seria mentira.
     */
    fun medir(raiz: File): Pair<Long, Int> {
        var bytes = 0L; var n = 0
        val ruins = mutableListOf<String>()
        percorrer(raiz, ruins).filter { it.isFile }.forEach { bytes += it.length(); n++ }
        if (ruins.isNotEmpty()) throw PastaIlegivel(ruins)
        return bytes to n
    }

    /**
     * Copia [raiz] inteira para a conta [conta]. [progresso] recebe (bytes copiados, total). null = deu certo; senão o
     * motivo. Arquivo que falha para a cópia toda: backup pela metade não pode liberar a formatação.
     */
    fun copiar(ctx: Context, raiz: File, conta: ContaMidia, progresso: (Long, Long) -> Unit): String? {
        val total = try { medir(raiz).first } catch (e: PastaIlegivel) { return e.message ?: "sem ler" }
        val dia = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.ROOT).format(Date())
        val base = MidiaContas.juntar(MidiaContas.juntar(Smb.inicio(conta), PASTA), dia)
        var feito = 0L
        return try {
            Smb.abrir(conta).use { a ->
                fun mkdirs(caminho: String) {
                    var c = ""
                    for (p in caminho.split('/').filter { it.isNotEmpty() }) {
                        c = MidiaContas.juntar(c, p)
                        val w = c.replace('/', '\\')
                        if (!a.pasta.folderExists(w)) a.pasta.mkdir(w)
                    }
                }
                mkdirs(base)
                val buf = ByteArray(256 * 1024)
                val ruins = mutableListOf<String>()
                for (f in percorrer(raiz, ruins)) {
                    val rel = f.relativeTo(raiz).invariantSeparatorsPath
                    if (rel.isEmpty()) continue
                    val destino = MidiaContas.juntar(base, rel)
                    if (f.isDirectory) { mkdirs(destino); continue }
                    a.pasta.openFile(destino.replace('/', '\\'), EnumSet.of(AccessMask.GENERIC_WRITE), null, SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OVERWRITE_IF, null).use { sf ->
                        sf.outputStream.use { out ->
                            f.inputStream().use { inp ->
                                while (true) {
                                    val n = inp.read(buf); if (n < 0) break
                                    out.write(buf, 0, n); feito += n; progresso(feito, total)
                                }
                            }
                        }
                    }
                }
            }
            // Uma pasta que sumiu de leitura no meio da cópia também segura a formatação.
            if (ruins.isNotEmpty()) throw PastaIlegivel(ruins)
            Log.i(TAG, "backup: ${feito} bytes -> ${conta.nome}/$base")
            null
        } catch (e: Exception) {
            Log.w(TAG, "backup falhou: $e")
            e.message?.take(120) ?: e.javaClass.simpleName
        }
    }

    /**
     * Desfazer: traz de volta à TV todos os apps que estão no pen drive e a área compartilhada; só então o pen drive
     * pode virar comum de novo (senão os apps sumiriam). Devolve null ou o motivo do 1º que falhou.
     */
    fun trazerTudoDeVolta(ctx: Context, primarioNoPen: Boolean, aoApp: (String) -> Unit): String? {
        if (primarioNoPen) StorageMode.moverCompartilhado(ctx, null)?.let { return it }
        for (a in StorageMode.appsMoveis(ctx).filter { it.noExterno }) {
            aoApp(a.nome)
            AppStorage.mover(ctx, a.pacote, paraExterno = false)?.let { return "${a.nome}: $it" }
        }
        return null
    }
}
