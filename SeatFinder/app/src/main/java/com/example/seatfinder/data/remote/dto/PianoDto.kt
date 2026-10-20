package com.example.seatfinder.data.remote.dto

import kotlinx.serialization.Serializable

//DTO del piano: rispecchia la tabella "piano" su Supabase
//Solo la quota del piano: usato quando serve l'elenco dei piani di un edificio.
@Serializable
data class PianoQuota(val quota: String)

//Riga completa del piano: serve per costruire la mappa quota → edificio
@Serializable
data class PianoRowDto(
    val quota: String,
    val edificio: Int
)

//Gestione dei join annidati (serve per TavoloInfoResponse su TavoloDto)
@Serializable
data class PianoJoinDto(
    val edificio: EdificioJoinDto
)