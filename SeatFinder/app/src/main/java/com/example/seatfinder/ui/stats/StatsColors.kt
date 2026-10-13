package com.example.seatfinder.ui.stats

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.seatfinder.domain.stats.LivelloAffluenza

// Scala cromatica continua
internal val COL_BASSA  = Color(0xFF4CAF50)   // verde
internal val COL_MEDIA  = Color(0xFFFFEB3B)   // giallo
internal val COL_ALTA   = Color(0xFFFF9800)   // arancio
internal val COL_PIENA  = Color(0xFFE53935)   // rosso
internal val COL_ADESSO = Color(0xFF1976D2)   // blu "adesso"/oggi

internal fun coloreOccupazione(v: Float): Color {
    val x = v.coerceIn(0f, 1f)
    return when {
        x < 0.33f -> lerp(COL_BASSA, COL_MEDIA, x / 0.33f)
        x < 0.66f -> lerp(COL_MEDIA, COL_ALTA, (x - 0.33f) / 0.33f)
        else      -> lerp(COL_ALTA, COL_PIENA, (x - 0.66f) / 0.34f)
    }
}

internal fun LivelloAffluenza.colore(): Color = when (this) {
    LivelloAffluenza.LIBERA         -> COL_BASSA
    LivelloAffluenza.POCO_AFFOLLATA -> COL_ALTA
    LivelloAffluenza.AFFOLLATA      -> COL_PIENA
}
