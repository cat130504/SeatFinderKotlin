package com.example.seatfinder.ui.stats

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.seatfinder.domain.stats.LivelloAffluenza

//Colori condivisi da heatmap, legenda, barre di stato e fascia consigliata

//Estremi della scala cromatica continua
internal val COL_BASSA  = Color(0xFF4CAF50)   // verde
internal val COL_MEDIA  = Color(0xFFFFEB3B)   // giallo
internal val COL_ALTA   = Color(0xFFFF9800)   // arancio
internal val COL_PIENA  = Color(0xFFE53935)   // rosso

//Blu usato per "adesso/oggi"
internal val COL_ADESSO = Color(0xFF1976D2)

//Grigio delle celle senza dati (usato solo nella heatmap del profilo)
internal val COL_VUOTA = Color(0xFFB8BEC9)

//Dato un valore 0..1 (percentuale di occupazione), restituisce il colore in base a quanto occupato
internal fun coloreOccupazione(v: Float): Color {
    val x = v.coerceIn(0f, 1f)
    return when {
        x < 0.33f -> lerp(COL_BASSA, COL_MEDIA, x / 0.33f)
        x < 0.66f -> lerp(COL_MEDIA, COL_ALTA, (x - 0.33f) / 0.33f)
        else      -> lerp(COL_ALTA, COL_PIENA, (x - 0.66f) / 0.34f)
    }
}

//Colore "di significato" di un livello di affluenza
internal fun LivelloAffluenza.colore(): Color = when (this) {
    LivelloAffluenza.LIBERA         -> COL_BASSA
    LivelloAffluenza.POCO_AFFOLLATA -> COL_ALTA
    LivelloAffluenza.AFFOLLATA      -> COL_PIENA
}