package com.example.seatfinder.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class OccupazioneInsert(
    val id_utente: Int,
    val id_tavolo: Int,
    val data_occupazione: String,
    val lat_occupazione: Double? = null,
    val lng_occupazione: Double? = null
)

@Serializable
data class OccupazioneIdDto(
    val id: Int
)

@Serializable
data class OccupazioneTavoloDto(
    val id_tavolo: Int
)
