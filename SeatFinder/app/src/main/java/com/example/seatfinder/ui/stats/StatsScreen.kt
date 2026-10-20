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
import com.example.seatfinder.domain.stats.giorniDaOggi
import kotlinx.coroutines.delay
import java.util.Calendar

//Schermata Statistiche generali.
//"Regista" delle statistiche: legge lo stato dal cassetto StatsViewModel, gestisce i filtri
@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    //Giorno e ora correnti
    val todayDow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
    val oraCorrente = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    //Filtri locali dello schermo: edificio e quota selezionati
    var edificioSel by remember { mutableStateOf<Int?>(null) }
    var quotaSel    by remember { mutableStateOf<String?>(null) }

    //Giorno scelto per heatmap + miglior orario.
    //remember(todayDow) azzera la selezione se cambia il giorno (es. mezzanotte).
    var giornoSel by remember(todayDow) { mutableIntStateOf(giorniDaOggi(todayDow).first()) }

    //Refresh periodico dello stato attuale: ogni 30 secondi.
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.aggiornaStatoAttuale()
            delay(30_000)
        }
    }

    //Piani disponibili in base all'edificio selezionato, senza duplicati
    val quoteDisponibili = remember(state.pianoRows, edificioSel) {
        val base = if (edificioSel == null) state.pianoRows
        else state.pianoRows.filter { it.edificio == edificioSel }
        base.map { it.quota }.distinct().sorted()
    }

    //Se il piano selezionato non è più fra quelli disponibili (es. cambio edificio),
    //ricado sul primo disponibile
    LaunchedEffect(quoteDisponibili) {
        if (quotaSel == null || quotaSel !in quoteDisponibili) {
            quotaSel = quoteDisponibili.firstOrNull()
        }
    }

    //Storico filtrato per il piano scelto
    val rowsFiltrate = remember(state.rows, quotaSel) {
        state.rows.filter { r -> r.quota == quotaSel }
    }

    //Stato attuale filtrato per edificio scelto
    val statoFiltrato = remember(state.statoAttuale, edificioSel) {
        if (edificioSel == null) state.statoAttuale
        else state.statoAttuale.filter { it.edificioId == edificioSel }
    }

    //Fascia consigliata per (piano, giorno scelto)
    val fasciaConsigliata: Int? = remember(rowsFiltrate, giornoSel, todayDow, oraCorrente) {
        val oraMinima = if (giornoSel == todayDow) oraCorrente else 0
        calcolaFasciaConsigliata(rowsFiltrate, giornoSel, oraMinima)
    }

    //Primo caricamento: storico non ancora arrivato → spinner a tutto schermo
    if (state.loading && state.rows.isEmpty()) {
        Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        //Sezione "Stato attuale"
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

        //Filtro piano: chip orizzontali
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

        //Nessun dato storico per il piano scelto: messaggio e stop
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

        //Avviso "pochi dati": sotto la soglia, le statistiche non sono affidabili
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

        //Heatmap giorni × ore
        SezioneHeatmap(
            rows = rowsFiltrate,
            todayDow = todayDow,
            oraCorrente = oraCorrente,
            giornoSel = giornoSel,
            fasciaConsigliata = fasciaConsigliata
        )

        //Card "Miglior orario" con chip per cambiare giorno
        SezioneMigliorOrario(
            rows = rowsFiltrate,
            todayDow = todayDow,
            giornoSel = giornoSel,
            onGiornoChange = { giornoSel = it },
            fasciaConsigliata = fasciaConsigliata
        )
    }
}