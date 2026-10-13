package com.example.seatfinder.domain.stats

import com.example.seatfinder.data.remote.dto.StatsFlussoRow

enum class LivelloAffluenza { LIBERA, POCO_AFFOLLATA, AFFOLLATA }


fun giorniDaOggi(todayDow: Int): List<Int> = buildList {
    var d = if (todayDow in StatsConfig.GIORNI_FERIALI) todayDow else 1
    repeat(5) {
        add(d)
        d = if (d == 5) 1 else d + 1
    }
}


fun calcolaFasciaConsigliata(
    rows: List<StatsFlussoRow>,
    giornoSettimana: Int,
    oraMinima: Int
): Int? {
    val perOra = rows
        .filter { it.giorno_settimana == giornoSettimana }
        .groupBy { it.ora }
        .mapValues { (_, list) -> list.sumOf { it.occupazioni_totali } }
        .filterKeys { it in StatsConfig.ORE_APERTURA }

    val oreFuture = perOra.filterKeys { it >= oraMinima }
    val candidati = oreFuture.ifEmpty { perOra }

    return candidati.minByOrNull { it.value }?.key
}

fun occupazioniFascia(
    rows: List<StatsFlussoRow>,
    giornoSettimana: Int,
    ora: Int
): Int = rows
    .filter { it.giorno_settimana == giornoSettimana && it.ora == ora }
    .sumOf { it.occupazioni_totali }

fun livelloAffluenza(occupazioni: Int): LivelloAffluenza = when {
    occupazioni == 0 -> LivelloAffluenza.LIBERA
    occupazioni <= StatsConfig.SOGLIA_POCO_AFFOLLATA -> LivelloAffluenza.POCO_AFFOLLATA
    else -> LivelloAffluenza.AFFOLLATA
}


fun mediaGiornaliera(occupazioni: Int): Float =
    occupazioni / (StatsConfig.GIORNI_STORICO / 7f)