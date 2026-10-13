package com.example.seatfinder.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.seatfinder.R
import com.example.seatfinder.data.repository.TavoloRepository
import com.example.seatfinder.domain.model.Tavolo
import kotlinx.coroutines.delay

/**
 * Schermata principale della mappa (Composable).
 */
@Composable
fun MapScreen(
    drawableRes: Int = R.drawable.q155,
    quota: String? = null,
    modifier: Modifier = Modifier
) {
    var selectedTable by remember { mutableStateOf<Tavolo?>(null) }

    val refreshTick by TavoloRepository.refreshTick.collectAsState()

    var mapViewRef by remember { mutableStateOf<InteractiveMapView?>(null) }

    Box(modifier = modifier.fillMaxSize()) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                InteractiveMapView(context).apply {
                    cambiaImmagine(drawableRes)
                    caricaTavoli(quota)
                    onTableClick = { tavolo -> selectedTable = tavolo }
                    mapViewRef = this
                }
            },
            update = { mapView ->
                mapView.onTableClick = { tavolo -> selectedTable = tavolo }
                mapView.cambiaImmagine(drawableRes)

            }
        )

        LaunchedEffect(quota, refreshTick, mapViewRef) {
            val mapView = mapViewRef ?: return@LaunchedEffect
            while (true) {
                mapView.caricaTavoli(quota, force = true)
                delay(30_000L)
            }
        }

        selectedTable?.let { tavolo ->
            TavoloDialog(
                tavolo = tavolo,
                onDismissRequest = { selectedTable = null }
            )
        }
    }
}
