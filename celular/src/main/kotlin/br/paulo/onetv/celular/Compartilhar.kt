package br.paulo.onetv.celular

/**
 * Regras puras do "Compartilhar foto/vídeo/arquivo com a TV" (Brainstorm 26/11/2026, item 16). Sem Android aqui, para
 * o teste JVM cobrir tudo: que tipo é, para que pasta da TV vai, que nome a TV aceita e como achar um nome livre.
 *
 * Por quê: até aqui o celular só mandava texto e link; para pôr o vídeo da viagem na TV era preciso abrir o painel,
 * achar Arquivos e arrastar. Agora é o "Compartilhar" de sempre da galeria, igual ao AirPlay/Google Fotos "Transmitir".
 */
object Compartilhar {
    enum class Tipo { IMAGEM, VIDEO, AUDIO, OUTRO }

    /** Pasta onde o painel já põe o que toca na TV (app.js, PASTA_NA_TV): o player do Sofaly acha lá. */
    const val PASTA_TOCAR = "Movies"
    const val PASTA_FOTOS = "Pictures"
    const val PASTA_OUTROS = "Download"

    /** Lado maior da foto que vira recado na tela (o mesmo 960 px do painel, extras.js reduzir). */
    const val FOTO_LADO = 960

    /** Extensões que a TV toca ou mostra (PainelHttp.tipoPrevia na TV), por tipo. */
    private val EXT = mapOf(
        Tipo.IMAGEM to setOf("jpg", "jpeg", "png", "gif", "webp", "bmp"),
        Tipo.VIDEO to setOf("mp4", "m4v", "webm", "mkv", "mov"),
        Tipo.AUDIO to setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac"),
    )

    /** Extensão para quando o app de origem manda um nome sem ela (a galeria às vezes manda só "IMG_2031"). */
    private val EXT_DO_MIME = mapOf(
        "image/jpeg" to "jpg", "image/png" to "png", "image/gif" to "gif", "image/webp" to "webp", "image/bmp" to "bmp",
        "video/mp4" to "mp4", "video/webm" to "webm", "video/x-matroska" to "mkv", "video/quicktime" to "mov",
        "video/3gpp" to "3gp", "audio/mpeg" to "mp3", "audio/mp4" to "m4a", "audio/aac" to "aac", "audio/ogg" to "ogg",
        "audio/opus" to "opus", "audio/wav" to "wav", "audio/x-wav" to "wav", "audio/flac" to "flac",
    )

    fun tipo(mime: String?, nome: String): Tipo {
        val m = mime?.lowercase().orEmpty()
        when {
            m.startsWith("image/") -> return Tipo.IMAGEM
            m.startsWith("video/") -> return Tipo.VIDEO
            m.startsWith("audio/") -> return Tipo.AUDIO
        }
        val ext = nome.substringAfterLast('.', "").lowercase()
        return EXT.entries.firstOrNull { ext in it.value }?.key ?: Tipo.OUTRO
    }

    /** A TV toca/mostra este arquivo? Se não, ele só é guardado (a TV responderia 415 ao "tocar"). */
    fun tocavel(tipo: Tipo, nome: String): Boolean = nome.substringAfterLast('.', "").lowercase() in (EXT[tipo] ?: emptySet())

    /** Para onde vai quando a pessoa escolhe "Guardar na TV". Vídeo e música na mesma pasta do painel. */
    fun pasta(tipo: Tipo): String = when (tipo) {
        Tipo.IMAGEM -> PASTA_FOTOS
        Tipo.VIDEO, Tipo.AUDIO -> PASTA_TOCAR
        Tipo.OUTRO -> PASTA_OUTROS
    }

    /**
     * Nome que a TV aceita (ChecagensPainel.nomeArquivoValido): sem barra, sem caractere de controle, até 200 letras.
     * Sem extensão, ganha a do tipo do arquivo. Nada sobrando vira "arquivo".
     */
    fun nomeSeguro(nome: String?, mime: String?): String {
        var n = (nome ?: "").map { if (it == '/' || it == '\\' || it.code < 0x20 || it.code == 0x7f) '_' else it }
            .joinToString("").trim().trimStart('.')
        if (n.isEmpty()) n = "arquivo"
        if (!n.contains('.')) EXT_DO_MIME[mime?.lowercase()]?.let { n = "$n.$it" }
        if (n.length > 200) {
            val ext = n.substringAfterLast('.', "").take(10)
            n = if (ext.isEmpty()) n.take(200) else n.take(199 - ext.length) + "." + ext
        }
        return n
    }

    /** "foto.jpg" → "foto (2).jpg", "foto (3).jpg"... para não apagar o que já está na TV (o envio dá 409). */
    fun alternativo(nome: String, n: Int): String {
        val ponto = nome.lastIndexOf('.')
        return if (ponto <= 0) "$nome ($n)" else nome.substring(0, ponto) + " ($n)" + nome.substring(ponto)
    }

    /**
     * Primeiro nome que ainda não está na pasta da TV. Conferido ANTES de mandar: com nome repetido a TV recusa fechando
     * a conexão no meio do envio (o celular veria "não chegou", não um 409), igual ao painel (app.js, verNaTv).
     */
    fun nomeLivre(nome: String, existentes: Set<String>): String {
        if (nome !in existentes) return nome
        for (n in 2..999) alternativo(nome, n).let { if (it !in existentes) return it }
        return alternativo(nome, System.currentTimeMillis().toInt() and 0x7fffffff)
    }

    /** Tamanho final da foto do recado: o lado maior fica em [FOTO_LADO], sem aumentar foto pequena. */
    fun reduzido(largura: Int, altura: Int, lado: Int = FOTO_LADO): Pair<Int, Int> {
        val maior = maxOf(largura, altura)
        if (maior <= lado || maior <= 0) return largura to altura
        val f = lado.toDouble() / maior
        return maxOf(1, Math.round(largura * f).toInt()) to maxOf(1, Math.round(altura * f).toInt())
    }

    /** Potência de 2 para o BitmapFactory já ler a foto pequena (uma foto de 50 MP não cabe inteira na memória). */
    fun amostra(largura: Int, altura: Int, lado: Int = FOTO_LADO): Int {
        var s = 1
        while (maxOf(largura, altura) / (s * 2) >= lado) s *= 2
        return s
    }
}
