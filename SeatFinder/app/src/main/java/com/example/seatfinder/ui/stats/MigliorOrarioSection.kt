package com.example.seatfinder.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import com.example.seatfinder.domain.stats.giorniDaOggi
import com.example.seatfinder.domain.stats.livelloAffluenza
import com.example.seatfinder.domain.stats.mediaGiornaliera
import com.example.seatfinder.domain.stats.occupazioniFascia

//Nomi per esteso dei giorni
private val NOMI_GIORNI = listOf(
    "domenica", "lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato"
)

//Sezione "Miglior orario": mostra la fascia consigliata per il giorno scelto

@Composable
internal fun SezioneMigliorOrario(
    rows: List<StatsFlussoRow>,
    todayDow: Int,
    giornoSel: Int,
    onGiornoChange: (Int) -> Unit,
    fasciaConsigliata: Int?
) {
    //Ordine dei giorni da mostrare nei chip: stessi 5 feriali della heatmap
    val giorni = remember(todayDow) { giorniDaOggi(todayDow) }

    //Occupazioni storiche nella fascia consigliata del giorno scelto.
    val occConsigliata = remember(rows, giornoSel, fasciaConsigliata) {
        fasciaConsigliata?.let { h -> occupazioniFascia(rows, giornoSel, h) } ?: 0
    }

    //Titolo dinamico: "oggi" se il giorno scelto è oggi, altrimenti il nome del giorno
    val titolo = if (giornoSel == todayDow) "Miglior orario per oggi"
    else "Miglior orario per ${NOMI_GIORNI[giornoSel]}"

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titolo, style = MaterialTheme.typography.titleMedium)
            Text(
                "Basato sugli ultimi ${StatsConfig.GIORNI_STORICO} giorni",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            //Chip orizzontali per scegliere il giorno (oggi, domani, ...)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(giorni) { g ->
                    FilterChip(
                        selected = g == giornoSel,
                        onClick = { onGiornoChange(g) },
                        label = { Text(if (g == todayDow) "Oggi" else LABEL_GIORNI[g]) }
                    )
                }
            }

            if (fasciaConsigliata != null) {
                //Card principale: orario consigliato in grande + descrizione affluenza
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
                            //Colore semantico del livello di affluenza (verde/arancio/rosso)
                            color = livelloAffluenza(occConsigliata).colore()
                        )
                    }
                }
            } else {
                Text(
                    "Nessun dato per questo giorno.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

//Costruisce il testo che descrive l'affluenza della fascia consigliata.
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