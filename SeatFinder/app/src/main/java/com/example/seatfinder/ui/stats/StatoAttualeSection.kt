package com.example.seatfinder.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.seatfinder.data.remote.dto.Edificio
import com.example.seatfinder.domain.model.StatoPiano
import kotlinx.coroutines.delay

@Composable
internal fun SezioneStatoAttuale(
    stato: List<StatoPiano>,
    edifici: List<Edificio>,
    edificioSel: Int?,
    onEdificioChange: (Int?) -> Unit,
    ultimoAggiornamento: Long
) {
    var secondiFa by remember { mutableIntStateOf(0) }

    LaunchedEffect(ultimoAggiornamento) {
        while (true) {
            secondiFa = if (ultimoAggiornamento == 0L) 0
            else ((System.currentTimeMillis() - ultimoAggiornamento) / 1000).toInt()
            delay(1000)
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("Stato attuale", style = MaterialTheme.typography.titleMedium)
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = edificioSel == null,
                        onClick  = { onEdificioChange(null) },
                        label    = { Text("Tutti") }
                    )
                }
                items(edifici) { ed ->
                    FilterChip(
                        selected = edificioSel == ed.id,
                        onClick  = { onEdificioChange(ed.id) },
                        label    = { Text(ed.nome) }
                    )
                }
            }

            if (stato.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(60.dp), Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                stato.forEach { s -> RigaStatoPiano(s) }
            }
        }
    }
}

@Composable
private fun RigaStatoPiano(s: StatoPiano) {
    val coloreBarra = coloreOccupazione(s.percentualeOccupazione)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${s.edificioNome} · ${s.quota}",
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                "${s.tavoliLiberi}/${s.tavoliTotali} tavoli liberi · ${s.postiLiberi}/${s.postiTotali} posti",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(s.disponibilita.coerceIn(0f, 1f))
                    .height(10.dp)
                    .background(coloreBarra, RoundedCornerShape(5.dp))
            )
        }
    }
}
