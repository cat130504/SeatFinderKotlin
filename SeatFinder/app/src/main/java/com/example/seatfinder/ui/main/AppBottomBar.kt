package com.example.seatfinder.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.seatfinder.ui.navigation.Destination

@Composable
fun AppBottomBar(
    currentRoute: String?,
    scanEnabled: Boolean,
    onDestinationClick: (Destination) -> Unit
) {
    Column {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Destination.entries.forEach { destination ->
                val isEnabled = if (destination == Destination.QR) scanEnabled else true
                val isSelected = currentRoute == destination.route

                NavigationBarItem(
                    selected = isSelected,
                    enabled = isEnabled,
                    onClick = { onDestinationClick(destination) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.contentDescription
                        )
                    },
                    label = { Text(destination.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor   = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor   = MaterialTheme.colorScheme.primary,
                        indicatorColor      = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledIconColor   = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        disabledTextColor   = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    )
                )
            }
        }
    }
}