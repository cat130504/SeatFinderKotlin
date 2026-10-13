package com.example.seatfinder.data.remote.dto

import com.example.seatfinder.domain.model.Tavolo
import kotlinx.serialization.Serializable

@Serializable
data class TavoloDto(
    val id: Int,
    val x: Float,
    val y: Float,
    val rotazione: Float = 0f,
    val quota: String,
    val is_occupato: Boolean = false,
    val presa: Boolean = false,
    val posti: Int = 0,
    val postiDisponibili: Int = 4
)

fun TavoloDto.toDomain() = Tavolo(
    id = id,
    x = x,
    y = y,
    rotazione = rotazione,
    quota = quota,
    isOccupato = is_occupato,
    presa = presa,
    posti = posti,
    postiDisponibili = postiDisponibili
)

@Serializable
data class TavoloUpdate(
    val is_occupato: Boolean
)

@Serializable
data class TavoloInfoResponse(
    val quota: String,
    val piano: PianoJoinDto
)
