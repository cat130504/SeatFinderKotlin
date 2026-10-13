package com.example.seatfinder.domain.model

data class OccupazioneUtente(
    val tavoloId: Int,
    val edificioNome: String,
    val quota: String,
    val inizio: Long,        // epoch millis
    val lat: Double?,
    val lng: Double?
)