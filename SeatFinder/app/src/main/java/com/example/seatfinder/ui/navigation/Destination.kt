package com.example.seatfinder.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Person

enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
) {
    MAP("map", "Mappa", Icons.Default.Map, "Schermata Mappa"),
    STATS("stats", "Statistiche", Icons.Default.QueryStats, "Schermata Statistiche"),
    QR("qr", "Scansione", Icons.Default.QrCode, "Schermata Scansione"),
    PROFILE_STATS("profile_stats", "Profilo", Icons.Default.Person, "Statistiche del profilo")
}