package com.example.seatfinder.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.seatfinder.domain.model.OccupazioneUtente
import com.example.seatfinder.domain.stats.StatsConfig
import com.example.seatfinder.domain.stats.giorniDaOggi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.collectAsState

//Schermata "Profilo": statistiche personali dell'utente loggato.
@Composable
fun ProfileStatsScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileStatsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    //Caricamento automatico al primo ingresso in schermata
    LaunchedEffect(Unit) {
        viewModel.carica()
    }

    when {
        //Spinner a tutto schermo al primo caricamento
        state.loading -> Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

        //Errore con dati assenti: messaggio + bottone Riprova
        state.errore -> Box(modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Impossibile caricare le statistiche")
                Spacer(Modifier.height(8.dp))
                Button(onClick = viewModel::carica) { Text("Riprova") }
            }
        }

        //Nessuna occupazione: messaggio "vuoto"
        state.occupazioni.isEmpty() -> Box(modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
            Text(
                "Non hai ancora occupato nessun tavolo",
                textAlign = TextAlign.Center
            )
        }

        //Caso normale: le quattro sezioni in colonna scrollabile
        else -> Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SezioneRiepilogo(state.occupazioni)
            SezioneHeatmapProfilo(state.occupazioni)
            SezioneLuoghi(state.occupazioni)
            SezioneUltime(state.occupazioni)
        }
    }
}


//Giorno della settimana
private fun OccupazioneUtente.dow(): Int =
    Calendar.getInstance().apply { timeInMillis = inizio }.get(Calendar.DAY_OF_WEEK) - 1

//Ora del giorno di un'occupazione
private fun OccupazioneUtente.ora(): Int =
    Calendar.getInstance().apply { timeInMillis = inizio }.get(Calendar.HOUR_OF_DAY)

//Sezione con quattro "tessere": occupazioni totali, giorno preferito, fascia preferita, piano preferito.
//I "preferiti" sono il valore con più occorrenze
@Composable
private fun SezioneRiepilogo(occ: List<OccupazioneUtente>) {
    val giornoTop = remember(occ) {
        occ.groupingBy { it.dow() }.eachCount().maxByOrNull { it.value }?.key
    }
    val oraTop = remember(occ) {
        occ.groupingBy { it.ora() }.eachCount().maxByOrNull { it.value }?.key
    }
    val pianoTop = remember(occ) {
        occ.groupingBy { it.edificioNome to it.quota }.eachCount().maxByOrNull { it.value }?.key
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Le tue statistiche", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tessera("Occupazioni", "${occ.size}", Modifier.weight(1f))
                Tessera("Giorno preferito", giornoTop?.let { LABEL_GIORNI[it] } ?: "-", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tessera("Fascia preferita", oraTop?.let { "$it:00" } ?: "-", Modifier.weight(1f))
                Tessera("Piano preferito", pianoTop?.let { "${it.first} · ${it.second}" } ?: "-", Modifier.weight(1f))
            }
        }
    }
}

//Singola "tessera" del riepilogo: etichetta piccola sopra, valore grande sotto.
@Composable
private fun Tessera(label: String, valore: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(valore, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------- 2. Heatmap personale ----------

//Heatmap giorni × ore delle OCCUPAZIONI dell'utente
@Composable
private fun SezioneHeatmapProfilo(occ: List<OccupazioneUtente>) {
    val todayDow = remember { Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1 }
    val ordine = remember(todayDow) { giorniDaOggi(todayDow) }

    //(giorno, ora) → quante volte l'utente ha occupato
    val mappa = remember(occ) { occ.groupingBy { it.dow() to it.ora() }.eachCount() }
    //Massimo per normalizzare i colori
    val maxN = mappa.values.maxOrNull()?.coerceAtLeast(1) ?: 1

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Le tue fasce di occupazione", style = MaterialTheme.typography.titleMedium)

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                //Intestazione giorni (oggi in grassetto e blu)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(26.dp))
                    ordine.forEach { dow ->
                        val isOggi = dow == todayDow
                        Text(
                            LABEL_GIORNI[dow],
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontWeight = if (isOggi) FontWeight.ExtraBold else FontWeight.Bold,
                            color = if (isOggi) COL_ADESSO else Color.Unspecified
                        )
                    }
                }
                //Una riga per ogni ora di apertura
                StatsConfig.ORE_APERTURA.forEach { h ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$h",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(26.dp)
                        )
                        ordine.forEach { dow ->
                            val n = mappa[dow to h] ?: 0

                            //Celle senza occupazioni: grigio neutro con bordo sottile
                            //Celle con occupazioni: scala cromatica, senza bordo
                            val colore = if (n == 0) COL_VUOTA.copy(alpha = 0.35f)
                            else coloreOccupazione(n.toFloat() / maxN)

                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(18.dp)
                                    .padding(1.dp)
                                    .background(colore, RoundedCornerShape(3.dp))
                                    .border(
                                        width = 0.5.dp,
                                        color = if (n == 0) COL_VUOTA.copy(alpha = 0.7f)
                                        else Color.Transparent,
                                        shape = RoundedCornerShape(3.dp)
                                    )
                            )
                        }
                    }
                }
            }

            LegendaHeatmapProfilo()
        }
    }
}

//Legenda della heatmap personale: prima cella grigia ("Nessuna")
@Composable
private fun LegendaHeatmapProfilo() {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Scala occupazioni", style = MaterialTheme.typography.labelSmall)

        Row(
            Modifier
                .fillMaxWidth()
                .height(12.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            //Cella grigia = nessuna occupazione
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(COL_VUOTA.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                    .border(
                        width = 0.5.dp,
                        color = COL_VUOTA.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
            //Gradiente continuo
            (0..10).forEach { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(coloreOccupazione(i / 10f), RoundedCornerShape(2.dp))
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Nessuna", style = MaterialTheme.typography.labelSmall)
            Text("Poche", style = MaterialTheme.typography.labelSmall)
            Text("Molte", style = MaterialTheme.typography.labelSmall)
        }
    }
}

//Classifica (edificio, quota) per numero di occupazioni.
//La barra orizzontale è proporzionale al conteggio rispetto al primo classificato.
@Composable
private fun SezioneLuoghi(occ: List<OccupazioneUtente>) {
    val luoghi = remember(occ) {
        occ.groupingBy { it.edificioNome to it.quota }.eachCount()
            .entries.sortedByDescending { it.value }
    }
    //Massimo = il primo
    val maxN = luoghi.firstOrNull()?.value ?: 1

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Dove occupi i tavoli", style = MaterialTheme.typography.titleMedium)
            luoghi.forEach { (luogo, n) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${luogo.first} · ${luogo.second}", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "$n volte",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    //Barra di sfondo + barra colorata
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(n.toFloat() / maxN)
                                .height(10.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp))
                        )
                    }
                }
            }
        }
    }
}

//Lista delle 10 occupazioni più recenti, con data/ora e nome del luogo.
@Composable
private fun SezioneUltime(occ: List<OccupazioneUtente>) {
    //Formato data italiano, riusato per tutte le righe
    val formato = remember { SimpleDateFormat("dd/MM · HH:mm", Locale.ITALY) }
    val ultime = remember(occ) { occ.sortedByDescending { it.inizio }.take(10) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ultime occupazioni", style = MaterialTheme.typography.titleMedium)
            ultime.forEach { o ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("${o.edificioNome} · ${o.quota}", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Tavolo ${o.tavoloId} · ${formato.format(Date(o.inizio))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                //Divisorio fra una riga e l'altra
                HorizontalDivider()
            }
        }
    }
}