package com.example.seatfinder.domain.stats

import com.example.seatfinder.data.remote.dto.StatsFlussoRow

enum class LivelloAffluenza { LIBERA, POCO_AFFOLLATA, AFFOLLATA }

/**
 * Restituisce l'ora (es. 14) con meno occupazioni storiche per il giorno indicato.
 * Considera solo le ore da [oraCorrente] in poi; se non ce ne sono, guarda tutta la giornata.
 */
fun calcolaFasciaConsigliata(
    rows: List<StatsFlussoRow>,
    todayDow: Int,
    oraCorrente: Int
): Int? {
    val oggiPerOra = rows
        .filter { it.giorno_settimana == todayDow }
        .groupBy { it.ora }
        .mapValues { (_, list) -> list.sumOf { it.occupazioni_totali } }
        .filterKeys { it in StatsConfig.ORE_APERTURA }

    val oreFuture = oggiPerOra.filterKeys { it >= oraCorrente }
    val candidati = oreFuture.ifEmpty { oggiPerOra }

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
    occupazioni / StatsConfig.GIORNI_STORICO.toFloat()
