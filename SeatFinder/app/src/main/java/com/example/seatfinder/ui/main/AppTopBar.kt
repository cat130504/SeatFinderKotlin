package com.example.seatfinder.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

//Top bar della schermata principale: cerchio con le iniziali, info del tavolo occupato e bottone "Libera Tavolo"
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    iniziali: String?,
    infoTavolo: Pair<String, String>?,   //(nome edificio, quota)
    tavoloAttuale: Int?,
    onProfileClick: () -> Unit,
    onLiberaClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                //Cerchio cliccabile con le iniziali: apre ProfileDialog per il logout
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClick = onProfileClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = iniziali ?: "?",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                //Info del tavolo occupato (edificio, piano, id). Visibili solo se tavoloAttuale non è null.
                infoTavolo?.let { (edificio, quota) ->
                    Column {
                        Text(
                            text = edificio,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Piano: $quota",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tavolo: $tavoloAttuale",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        actions = {
            //Bottone rosso per liberare subito il tavolo
            if (tavoloAttuale != null) {
                Button(
                    onClick = onLiberaClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Libera Tavolo")
                }
            }
        }
    )
}