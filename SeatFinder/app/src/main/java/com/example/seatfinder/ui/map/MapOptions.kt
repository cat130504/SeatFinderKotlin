package com.example.seatfinder.ui.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Description
import com.example.seatfinder.data.remote.dto.Edificio
import com.example.seatfinder.data.remote.dto.PianoQuota
import com.example.seatfinder.ui.components.FabOption

fun generaOpzioniPiani(
    piani: List<PianoQuota>,
    onPianoSelected: (quota: String) -> Unit
): List<FabOption> = piani.map { piano ->
    FabOption(
        icon = Icons.Default.Description,
        label = piano.quota,
        onClick = { onPianoSelected(piano.quota) }
    )
}

fun generaOpzioniEdifici(
    edifici: List<Edificio>,
    onEdificioSelected: (Edificio) -> Unit
): List<FabOption> = edifici.map { edificio ->
    FabOption(
        icon = Icons.Default.Business,
        label = edificio.nome,
        onClick = { onEdificioSelected(edificio) }
    )
}
