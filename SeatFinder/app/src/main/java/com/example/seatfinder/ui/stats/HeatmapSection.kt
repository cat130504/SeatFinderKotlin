package com.example.seatfinder.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.seatfinder.data.remote.dto.StatsFlussoRow
import com.example.seatfinder.domain.stats.StatsConfig
import com.example.seatfinder.domain.stats.giorniDaOggi

internal val LABEL_GIORNI = listOf("Dom", "Lun", "Mar", "Mer", "Gio", "Ven", "Sab")

@Composable
internal fun SezioneHeatmap(
    rows: List<StatsFlussoRow>,
    todayDow: Int,
    oraCorrente: Int,
    giornoSel: Int,
    fasciaConsigliata: Int?
) {
    val ordine = giorniDaOggi(todayDow)
    val labels = ordine.map { LABEL_GIORNI[it] }

    val mappaAggregata = remember(rows) {
        rows.groupBy { it.giorno_settimana to it.ora }
            .mapValues { (_, list) -> list.sumOf { it.occupazioni_totali } }
    }
    val maxTot = mappaAggregata.values.maxOrNull()?.coerceAtLeast(1) ?: 1

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Affluenza settimanale", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            HeatmapGrid(
                mappaAggregata = mappaAggregata,
                ordine = ordine,
                labels = labels,
                maxTot = maxTot,
                oraCorrente = oraCorrente,
                fasciaConsigliata = fasciaConsigliata,
                todayDow = todayDow,
                giornoSel = giornoSel
            )
            LegendaHeatmap()
        }
    }
}

@Composable
private fun HeatmapGrid(
    mappaAggregata: Map<Pair<Int, Int>, Int>,
    ordine: List<Int>,
    labels: List<String>,
    maxTot: Int,
    oraCorrente: Int,
    fasciaConsigliata: Int?,
    todayDow: Int,
    giornoSel: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(26.dp))
            labels.forEachIndexed { idx, l ->
                val isOggi = ordine[idx] == todayDow
                Text(
                    l,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = if (isOggi) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (isOggi) COL_ADESSO else Color.Unspecified
                )
            }
        }

        StatsConfig.ORE_APERTURA.forEach { h ->
            val isOraCorrente = (h == oraCorrente)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isOraCorrente)
                            Modifier
                                .background(COL_ADESSO.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
                                .padding(vertical = 1.dp)
                        else Modifier
                    )
            ) {
                Text(
                    "$h",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(26.dp),
                    fontWeight = if (isOraCorrente) FontWeight.ExtraBold else FontWeight.Normal,
                    color = if (isOraCorrente) COL_ADESSO else Color.Unspecified
                )
                ordine.forEach { dow ->
                    val n = mappaAggregata[dow to h] ?: 0
                    val occ = (n.toFloat() / maxTot).coerceIn(0f, 1f)

                    // Bordo sulla fascia consigliata del giorno scelto (non più solo di oggi)
                    val isConsigliata = dow == giornoSel && fasciaConsigliata == h

                    val coloreBordo = MaterialTheme.colorScheme.primary

                    Box(
                        Modifier
                            .weight(1f)
                            .height(18.dp)
                            .padding(1.dp)
                    ) {
                        if (isConsigliata) {
                            // Bordo spesso + gap interno per staccare dal colore della cella
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .border(2.5.dp, coloreBordo, RoundedCornerShape(4.dp))
                                    .padding(2.dp)
                                    .background(coloreOccupazione(occ), RoundedCornerShape(2.dp))
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(coloreOccupazione(occ), RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendaHeatmap() {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Scala affluenza", style = MaterialTheme.typography.labelSmall)
        Row(
            Modifier.fillMaxWidth().height(12.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            (0..10).forEach { i ->
                val v = i / 10f
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(coloreOccupazione(v), RoundedCornerShape(2.dp))
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Bassa", style = MaterialTheme.typography.labelSmall)
            Text("Media", style = MaterialTheme.typography.labelSmall)
            Text("Alta", style = MaterialTheme.typography.labelSmall)
        }
    }
}