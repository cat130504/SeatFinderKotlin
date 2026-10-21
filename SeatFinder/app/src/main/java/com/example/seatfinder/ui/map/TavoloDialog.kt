package com.example.seatfinder.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.seatfinder.domain.model.Tavolo

//Dialog informativo che appare quando si tappa un tavolo sulla mappa
//Mostra i dati base del tavolo (id, posti, presa, posti disponibili)
@Composable
fun TavoloDialog(tavolo: Tavolo, onDismissRequest: () -> Unit) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Tavolo N°${tavolo.id}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "Posti Totali: ${tavolo.posti}")
                Text(text = "Posti Disponibili: ${tavolo.postiDisponibili}")
                Text(text = "Presa elettrica: ${if (tavolo.presa) "Presente" else "Assente"}")
            }
        }
    }
}