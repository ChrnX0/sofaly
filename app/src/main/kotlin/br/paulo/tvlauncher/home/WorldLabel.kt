package br.paulo.tvlauncher.home

import android.content.Context
import br.paulo.tvlauncher.settings.Extras

/**
 * Onde mora o nome do mundo da cruz (22/11/2026, pedido do Paulo vendo a Sala v468606).
 *
 * Porquê: o nome acima da coluna disputava espaço com o item de cima e já tinha "andado" pela tela.
 * Para quê: um lugar fixo e calmo, que não depende de qual item está em foco.
 * Como: [Place.PILL] = o nome, com o ícone do mundo à esquerda, entra na pílula do topo (a da hora), antes do ponto;
 * a coluna fica sem nome. [Place.COLUMN] = como era. Trocável no Ajuste fino (`world_label`: 0 coluna, 1 pílula).
 * Só vale com a barra "Ilha" (a única que tem a pílula); nas outras barras o nome continua na coluna.
 */
object WorldLabel {
    const val KEY = "world_label"

    enum class Place { COLUMN, PILL }

    fun place(ctx: Context): Place = Place.entries.getOrElse(Extras.numero(ctx, KEY, Place.PILL.ordinal)) { Place.PILL }

    /** O desenho do mundo, o mesmo da categoria na linha da cruz. */
    fun tipo(c: CruzLogica.Categoria): IconeCruz.Tipo = when (c) {
        CruzLogica.Categoria.FERRAMENTAS -> IconeCruz.Tipo.FERRAMENTAS
        CruzLogica.Categoria.CONTINUAR -> IconeCruz.Tipo.CONTINUAR
        CruzLogica.Categoria.ASSISTIR -> IconeCruz.Tipo.ASSISTIR
        CruzLogica.Categoria.OUVIR -> IconeCruz.Tipo.OUVIR
        CruzLogica.Categoria.JOGAR -> IconeCruz.Tipo.JOGAR
        CruzLogica.Categoria.APLICATIVOS -> IconeCruz.Tipo.APLICATIVOS
    }
}
