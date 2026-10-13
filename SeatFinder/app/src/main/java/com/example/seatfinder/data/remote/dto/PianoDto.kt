package com.example.seatfinder.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PianoQuota(val quota: String)

@Serializable
data class PianoRowDto(
    val quota: String,
    val edificio: Int
)

@Serializable
data class PianoJoinDto(
    val edificio: EdificioJoinDto
)
