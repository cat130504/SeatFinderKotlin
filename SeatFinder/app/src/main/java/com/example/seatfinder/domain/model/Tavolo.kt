package com.example.seatfinder.domain.model

data class Tavolo(
    val id: Int,
    val x: Float,                 // percentuale 0 a 100 sulla planimetria
    val y: Float,
    val rotazione: Float = 0f,
    val quota: String,
    val isOccupato: Boolean = false,
    val presa: Boolean = false,
    val posti: Int = 0,
    val postiDisponibili: Int
) {
    val isPieno: Boolean get() = postiDisponibili == 0
    val isLibero: Boolean get() = postiDisponibili == posti
}
