package com.example.seatfinder.domain.model

//Modello di dominio del tavolo: la versione "pulita" del TavoloDto,
data class Tavolo(
    val id: Int,
    val x: Float,                 //Percentuale (non pixel)
    val y: Float,
    val rotazione: Float = 0f,
    val quota: String,
    val isOccupato: Boolean = false,
    val presa: Boolean = false,
    val posti: Int = 0,
    val postiDisponibili: Int
) {
    //Tutti i posti sono occupati
    val isPieno: Boolean get() = postiDisponibili == 0
    //Nessun posto occupato
    val isLibero: Boolean get() = postiDisponibili == posti
}