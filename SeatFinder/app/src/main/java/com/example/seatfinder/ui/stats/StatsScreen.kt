package com.example.seatfinder.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.seatfinder.domain.stats.StatsConfig
import com.example.seatfinder.domain.stats.calcolaFasciaConsigliata
import kotlinx.coroutines.delay
import java.util.Calendar

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    var edificioSel by remember { mutableStateOf<Int?>(null) }
    var quotaSel    by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.aggiornaStatoAttuale()
            delay(30_000)
        }
    }

    val quoteDisponibili = remember(state.pianoRows, edificioSel) {
        val base = if (edificioSel == null) state.pianoRows
        else state.pianoRows.filter { it.edificio == edificioSel }
        base.map { it.quota }.distinct().sorted()
    }

    LaunchedEffect(quoteDisponibili) {
        if (quotaSel == null || quotaSel !in quoteDisponibili) {
            quotaSel = quoteDisponibili.firstOrNull()
        }
    }

    val rowsFiltrate = remember(state.rows, quotaSel) {
        state.rows.filter { r -> r.quota == quotaSel }
    }

    val statoFiltrato = remember(state.statoAttuale, edificioSel) {
        if (edificioSel == null) state.statoAttuale
        else state.statoAttuale.filter { it.edificioId == edificioSel }
    }

    if (state.loading && state.rows.isEmpty()) {
        Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }

    // Calcola una volta sola "oggi" e "adesso"
    val todayDow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
    val oraCorrente = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    // Fascia consigliata condivisa tra heatmap e card
    val fasciaConsigliata: Int? = remember(rowsFiltrate, todayDow, oraCorrente) {
        calcolaFasciaConsigliata(rowsFiltrate, todayDow, oraCorrente)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        SezioneStatoAttuale(
            stato               = statoFiltrato,
            edifici             = state.edifici,
            edificioSel         = edificioSel,
            onEdificioChange    = { edificioSel = it },
            ultimoAggiornamento = state.ultimoAggiornamento
        )

        Text(
            "Analisi ultimi ${StatsConfig.GIORNI_STORICO} giorni",
            style = MaterialTheme.typography.titleMedium
        )

        if (quoteDisponibili.isNotEmpty()) {
            Text("Piano", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quoteDisponibili) { q ->
                    FilterChip(
                        selected = q == quotaSel,
                        onClick  = { quotaSel = q },
                        label    = { Text(q) }
                    )
                }
            }
        }

        if (rowsFiltrate.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                    Text(
                        "Nessun dato storico disponibile per la selezione",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
            return@Column
        }

        val totaleOcc = rowsFiltrate.sumOf { it.occupazioni_totali }
        if (totaleOcc < StatsConfig.SOGLIA_POCHI_DATI) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Pochi dati ($totaleOcc occupazioni). Le statistiche diventano più affidabili con l'uso.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        SezioneHeatmap(rowsFiltrate, todayDow, oraCorrente, fasciaConsigliata)
        SezioneMigliorOrario(rowsFiltrate, todayDow, fasciaConsigliata)
    }
}
