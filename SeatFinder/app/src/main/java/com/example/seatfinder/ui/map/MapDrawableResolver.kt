package com.example.seatfinder.ui.map

import android.content.Context
import com.example.seatfinder.R


fun drawableFromQuota(context: Context, edificio: String, quota: String): Int {
    fun norm(s: String) = s.trim().lowercase().replace(" ", "_")

    val candidati = listOf(
        "${norm(edificio)}_${norm(quota)}",
        norm(quota)
    )
    for (nome in candidati) {
        val id = context.resources.getIdentifier(nome, "drawable", context.packageName)
        if (id != 0) return id
    }
    return R.drawable.q145
}
