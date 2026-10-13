package com.example.seatfinder.ui.main

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.seatfinder.ui.navigation.Destination

@Composable
fun AppBottomBar(
    currentRoute: String?,
    scanEnabled: Boolean,
    onDestinationClick: (Destination) -> Unit
) {
    NavigationBar {
        Destination.entries.forEach { destination ->
            val isEnabled = if (destination == Destination.QR) scanEnabled else true

            NavigationBarItem(
                selected = currentRoute == destination.route,
                enabled = isEnabled,
                onClick = { onDestinationClick(destination) },
                icon = { Icon(destination.icon, contentDescription = destination.contentDescription) },
                label = { Text(destination.label) }
            )
        }
    }
}
