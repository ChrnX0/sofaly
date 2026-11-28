package br.paulo.tvlauncher.observer

/**
 * Versão do consentimento de "Ajudar outras casas" (#541, 28/11/2026). Pura: SofyConsentTest.
 *
 * Por quê: o "Sim" vale para o texto que a pessoa leu. O que a TV manda mudou várias vezes em 27/11 (lentos, regras de
 * conferir telas, o pacote da rede de Sofys) e um "1" guardado não diz sob qual texto foi dado. Para quê: nada novo sai
 * sem um "Sim" novo. Como: em vez de 1, a chave guarda a versão do texto aceito. Só a versão atual liga o envio; uma
 * versão mais velha vale como desligado e a TV pergunta de novo uma vez ([reask]).
 *
 * Mudou o que vai (um campo do pacote, um tipo de lição)? Suba a versão daquela metade, junto com os textos da TV e do
 * painel. SofyConsentTest e SofyFleetTest param o build se o pacote mudar sem isso.
 */
object SofyConsent {
    /** Fichas: nome, versão e tipo (travou ou ficou lento) dos apps + regras de conferir telas. Texto de 27/11 16:39. */
    const val SHARE_VERSION = 2
    /** Rede de Sofys: modelo, o que quebrou em categorias, o que consertou, memória e rede em faixas. Texto de 27/11. */
    const val FLEET_VERSION = 2

    fun shareOn(share: Int) = share == SHARE_VERSION
    fun fleetOn(share: Int, fleet: Int) = shareOn(share) && fleet == FLEET_VERSION

    /** Já disse "Sim" a um texto mais velho e ainda não respondeu ao atual: perguntar de novo (uma vez). */
    fun reask(share: Int, fleet: Int) = share in 1 until SHARE_VERSION || (fleet in 1 until FLEET_VERSION && !fleetOn(share, fleet))

    /** Os valores a gravar. Desligar zera os dois; o painel (fleet=false) mexe só nas fichas. */
    fun values(on: Boolean, fleet: Boolean, oldFleet: Int): Pair<Int, Int> = when {
        !on -> 0 to 0
        fleet -> SHARE_VERSION to FLEET_VERSION
        else -> SHARE_VERSION to (if (oldFleet == FLEET_VERSION) FLEET_VERSION else 0)
    }
}
