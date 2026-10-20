package com.example.seatfinder.domain.model

//Modello che raffigura "stato attuale" di un piano: aggregato dei suoi tavoli in un dato momento.
data class StatoPiano(
    val quota: String,
    val edificioId: Int,
    val edificioNome: String,
    val tavoliOccupati: Int,
    val tavoliTotali: Int,
    val postiLiberi: Int,
    val postiTotali: Int
) {
    //Tavoli liberi
    val tavoliLiberi: Int
        get() = tavoliTotali - tavoliOccupati

    //Percentuale di occupazione
    val percentualeOccupazione: Float
        get() = if (tavoliTotali == 0) 0f
        else tavoliOccupati.toFloat() / tavoliTotali

    //Disponibilità
    val disponibilita: Float
        get() = 1f - percentualeOccupazione
}