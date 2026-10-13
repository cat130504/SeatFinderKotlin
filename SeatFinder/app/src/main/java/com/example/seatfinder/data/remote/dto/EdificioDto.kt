package com.example.seatfinder.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class Edificio(val id: Int, val nome: String)

@Serializable
data class EdificioJoinDto(
    val nome: String
)
