package com.example.seatfinder.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

//Una voce del FAB espandibile: icona, etichetta e azione da eseguire al click
data class FabOption(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

//FAB espandibile con più opzioni (una per ogni edificio o piano).
@Composable
fun ExpandableFabMenu(
    options: List<FabOption>,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.End
) {
    var isExpanded by remember { mutableStateOf(false) }

    val labelDopoBottone = alignment == Alignment.Start

    Box(modifier = modifier) {
        Column(
            horizontalAlignment = alignment,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            //Le opzioni appaiono/scompaiono con fade + slide verticale
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Column(
                    horizontalAlignment = alignment,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    options.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!labelDopoBottone) OptionLabel(option.label)

                            //Al click: esegue l'azione e richiude il menu
                            SmallFloatingActionButton(
                                onClick = {
                                    option.onClick()
                                    isExpanded = false
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(option.icon, contentDescription = option.label)
                            }

                            if (labelDopoBottone) OptionLabel(option.label)
                        }
                    }
                }
            }

            //FAB principale: cambia icona in base allo stato
            FloatingActionButton(
                onClick = { isExpanded = !isExpanded }
            ) {
                Icon(
                    if (isExpanded) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = null
                )
            }
        }
    }
}

//Etichetta dell'opzione: piccola Surface con ombra, così resta leggibile anche sopra la planimetria
@Composable
private fun OptionLabel(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}