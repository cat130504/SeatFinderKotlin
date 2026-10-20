package com.example.seatfinder.domain.stats

import com.example.seatfinder.data.remote.dto.StatsFlussoRow

//Livello di affluenza di una fascia oraria, calcolato dalle occupazioni storiche.
enum class LivelloAffluenza { LIBERA, POCO_AFFOLLATA, AFFOLLATA }

//Costruisce l'elenco dei 5 giorni feriali da mostrare a partire da oggi
fun giorniDaOggi(todayDow: Int): List<Int> = buildList {
    var d = if (todayDow in StatsConfig.GIORNI_FERIALI) todayDow else 1
    repeat(5) {
        add(d)
        d = if (d == 5) 1 else d + 1
    }
}

//"Cervello matematico" della fascia consigliata: fra le ore di apertura del giorno
//scelto, trova quella con meno occupazioni storiche.
fun calcolaFasciaConsigliata(
    rows: List<StatsFlussoRow>,
    giornoSettimana: Int,
    oraMinima: Int
): Int? {
    //Totale occupazioni per ogni ora del giorno, filtrando solo le ore di apertura
    val perOra = rows
        .filter { it.giorno_settimana == giornoSettimana }
        .groupBy { it.ora }
        .mapValues { (_, list) -> list.sumOf { it.occupazioni_totali } }
        .filterKeys { it in StatsConfig.ORE_APERTURA }

    val oreFuture = perOra.filterKeys { it >= oraMinima }
    val candidati = oreFuture.ifEmpty { perOra }

    //L'ora con il minimo di occupazioni
    return candidati.minByOrNull { it.value }?.key
}

//Occupazioni storiche totali in una specifica (giorno, ora): usata per decidere
//il livello di affluenza della fascia consigliata.
fun occupazioniFascia(
    rows: List<StatsFlussoRow>,
    giornoSettimana: Int,
    ora: Int
): Int = rows
    .filter { it.giorno_settimana == giornoSettimana && it.ora == ora }
    .sumOf { it.occupazioni_totali }

//Classifica una fascia in base al numero di occupazioni totali
fun livelloAffluenza(occupazioni: Int): LivelloAffluenza = when {
    occupazioni == 0 -> LivelloAffluenza.LIBERA
    occupazioni <= StatsConfig.SOGLIA_POCO_AFFOLLATA -> LivelloAffluenza.POCO_AFFOLLATA
    else -> LivelloAffluenza.AFFOLLATA
}

//Media giornaliera di occupazioni in una fascia
fun mediaGiornaliera(occupazioni: Int): Float =
    occupazioni / (StatsConfig.GIORNI_STORICO / 7f)