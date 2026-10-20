package com.example.seatfinder.domain.stats

//Costanti condivise da tutta la logica statistiche.
object StatsConfig {
    //Quanti giorni di storico caricare
    const val GIORNI_STORICO = 30

    //Ore considerate "di apertura"
    val ORE_APERTURA = 8..18

    //Giorni feriali (lunedì=1 ... venerdì=5).
    val GIORNI_FERIALI = listOf(1, 2, 3, 4, 5)

    //Sotto questa soglia di occupazioni totali, StatsScreen mostra un avviso
    //"pochi dati": le statistiche non sono ancora affidabili
    const val SOGLIA_POCHI_DATI = 100

    //Soglia oltre la quale una fascia è considerata "affollata"
    const val SOGLIA_POCO_AFFOLLATA = 10
}