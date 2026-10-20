package com.example.seatfinder.data.remote.dto

import kotlinx.serialization.Serializable

//Riga restituita dalla funzione "stats_flusso" su Supabase.
//È il dato grezzo alla base delle statistiche: la heatmap, la fascia consigliata
//e il livello di affluenza si calcolano tutti a partire da queste righe.
@Serializable
data class StatsFlussoRow(
    val quota: String,
    val giorno_settimana: Int,
    val ora: Int,
    val occupazioni_totali: Int,
    val posti_totali: Int
)