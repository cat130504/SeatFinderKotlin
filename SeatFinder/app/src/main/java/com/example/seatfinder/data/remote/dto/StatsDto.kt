package com.example.seatfinder.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class StatsFlussoRow(
    val quota: String,
    val giorno_settimana: Int,       // 0 = domenica ... 6 = sabato
    val ora: Int,                    // 0..23 (Europe/Rome)
    val occupazioni_totali: Int,
    val posti_totali: Int
)
