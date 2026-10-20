package com.example.seatfinder.ui.map

import android.content.Context
import com.example.seatfinder.R

//Risolve dinamicamente quale planimetria disegnare in base a edificio e quota.

fun drawableFromQuota(context: Context, edificio: String, quota: String): Int {
    //Normalizza per trasformare "Piano 1" in "piano_1" (minuscolo, spazi → underscore)
    fun norm(s: String) = s.trim().lowercase().replace(" ", "_")

    //Ordine dei tentativi: nome più specifico → nome più generico
    val candidati = listOf(
        "${norm(edificio)}_${norm(quota)}",
        norm(quota)
    )
    for (nome in candidati) {
        //getIdentifier cerca il drawable per nome a runtime (0 = non trovato)
        val id = context.resources.getIdentifier(nome, "drawable", context.packageName)
        if (id != 0) return id
    }
    //Fallback: planimetria di default, così la mappa non resta vuota
    return R.drawable.q145
}