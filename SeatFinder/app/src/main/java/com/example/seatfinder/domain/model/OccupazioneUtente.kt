package com.example.seatfinder.domain.model

//Modello di dominio che rappresenta una singola occupazione dell'utente.
data class OccupazioneUtente(
    val tavoloId: Int,
    val edificioNome: String,
    val quota: String,
    val inizio: Long,
    val lat: Double?,
    val lng: Double?
)