package com.example.seatfinder.ui.stats


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.seatfinder.domain.model.OccupazioneUtente
import com.example.seatfinder.domain.stats.StatsConfig
import com.example.seatfinder.domain.stats.giorniDaOggi
import com.example.seatfinder.ui.stats.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*


@Composable
fun ProfileStatsScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileStatsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.carica()
    }

    when {
        state.loading -> Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

        state.errore -> Box(modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Impossibile caricare le statistiche")
                Spacer(Modifier.height(8.dp))
                Button(onClick = viewModel::carica) { Text("Riprova") }
            }
        }

        state.occupazioni.isEmpty() -> Box(modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
            Text(
                "Non hai ancora occupato nessun tavolo",
                textAlign = TextAlign.Center
            )
        }

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

// ---------- helper ----------

private fun OccupazioneUtente.dow(): Int =
    Calendar.getInstance().apply { timeInMillis = inizio }.get(Calendar.DAY_OF_WEEK) - 1

private fun OccupazioneUtente.ora(): Int =
    Calendar.getInstance().apply { timeInMillis = inizio }.get(Calendar.HOUR_OF_DAY)

// ---------- 1. Riepilogo ----------

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

@Composable
private fun SezioneHeatmapProfilo(occ: List<OccupazioneUtente>) {
    val todayDow = remember { Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1 }
    val ordine = remember(todayDow) { giorniDaOggi(todayDow) }

    val mappa = remember(occ) { occ.groupingBy { it.dow() to it.ora() }.eachCount() }
    val maxN = mappa.values.maxOrNull()?.coerceAtLeast(1) ?: 1

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Quando occupi i tavoli", style = MaterialTheme.typography.titleMedium)

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                StatsConfig.ORE_APERTURA.forEach { h ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$h",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(26.dp)
                        )
                        ordine.forEach { dow ->
                            val n = mappa[dow to h] ?: 0
                            // celle senza occupazioni neutre, le altre colorate in scala
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
                                        color = if (n == 0) COL_VUOTA.copy(alpha = 0.7f) else Color.Transparent,
                                        shape = RoundedCornerShape(3.dp)
                                    )
                            )
                        }
                    }
                }
            }
            Text(
                "Il colore indica quante volte hai occupato un tavolo in quella fascia",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------- 3. Luoghi più usati ----------

@Composable
private fun SezioneLuoghi(occ: List<OccupazioneUtente>) {
    val luoghi = remember(occ) {
        occ.groupingBy { it.edificioNome to it.quota }.eachCount()
            .entries.sortedByDescending { it.value }
    }
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

// ---------- 4. Ultime occupazioni con posizione ----------

@Composable
private fun SezioneUltime(occ: List<OccupazioneUtente>) {
    val context = LocalContext.current
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
                HorizontalDivider()
            }
        }
    }
}