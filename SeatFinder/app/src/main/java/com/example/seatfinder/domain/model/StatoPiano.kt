package com.example.seatfinder.domain.model

data class StatoPiano(
    val quota: String,
    val edificioId: Int,
    val edificioNome: String,
    val tavoliOccupati: Int,
    val tavoliTotali: Int,
    val postiLiberi: Int,
    val postiTotali: Int
) {
    val tavoliLiberi: Int
        get() = tavoliTotali - tavoliOccupati

    val percentualeOccupazione: Float
        get() = if (tavoliTotali == 0) 0f
        else tavoliOccupati.toFloat() / tavoliTotali

    val disponibilita: Float
        get() = 1f - percentualeOccupazione
}
