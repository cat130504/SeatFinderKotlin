package com.example.seatfinder.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.seatfinder.data.remote.dto.StatsFlussoRow
import com.example.seatfinder.domain.stats.LivelloAffluenza
import com.example.seatfinder.domain.stats.StatsConfig
import com.example.seatfinder.domain.stats.livelloAffluenza
import com.example.seatfinder.domain.stats.mediaGiornaliera
import com.example.seatfinder.domain.stats.occupazioniFascia

@Composable
internal fun SezioneMigliorOrario(
    rows: List<StatsFlussoRow>,
    todayDow: Int,
    fasciaConsigliata: Int?
) {
    val occConsigliata = remember(rows, todayDow, fasciaConsigliata) {
        fasciaConsigliata?.let { h -> occupazioniFascia(rows, todayDow, h) } ?: 0
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Miglior orario per oggi", style = MaterialTheme.typography.titleMedium)
            Text(
                "Basato sugli ultimi ${StatsConfig.GIORNI_STORICO} giorni",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (fasciaConsigliata != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Fascia consigliata", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${fasciaConsigliata}:00 – ${fasciaConsigliata + 1}:00",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = testoAffluenza(occConsigliata),
                            style = MaterialTheme.typography.bodySmall,
                            color = livelloAffluenza(occConsigliata).colore()
                        )
                    }
                }
            } else {
                Text(
                    "Nessun dato per la giornata odierna.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun testoAffluenza(n: Int): String {
    val media = "%.1f".format(mediaGiornaliera(n))
    return when (livelloAffluenza(n)) {
        LivelloAffluenza.LIBERA ->
            "Fascia storicamente libera · 0 occupazioni negli ultimi ${StatsConfig.GIORNI_STORICO} giorni"
        LivelloAffluenza.POCO_AFFOLLATA ->
            "Poco affollata · $n occupazioni (~$media/giorno)"
        LivelloAffluenza.AFFOLLATA ->
            "Fascia affollata · $n occupazioni (~$media/giorno)"
    }
}
