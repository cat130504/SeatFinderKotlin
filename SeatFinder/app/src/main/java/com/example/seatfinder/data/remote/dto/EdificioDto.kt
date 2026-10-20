package com.example.seatfinder.data.remote.dto

import kotlinx.serialization.Serializable

//DTO dell'edificio: rispecchia la tabella "edificio" su Supabase
//Usato dal MainViewModel per popolare il FAB degli edifici
@Serializable
data class Edificio(val id: Int, val nome: String)

//Gestione dei join annidati (serve per TavoloInfoResponse su TavoloDto)
@Serializable
data class EdificioJoinDto(
    val nome: String
)