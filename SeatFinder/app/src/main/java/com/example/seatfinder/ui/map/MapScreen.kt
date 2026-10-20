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


//Disegna la planimetria, mostra TavoloDialog al tap, e ricarica i tavoli ogni 30s
@Composable
fun MapScreen(
    drawableRes: Int = R.drawable.q155,
    quota: String? = null,
    modifier: Modifier = Modifier
) {
    //Tavolo selezionato: se non null, apre TavoloDialog
    var selectedTable by remember { mutableStateOf<Tavolo?>(null) }

    //Contatore osservato: ogni incremento forza un refresh della mappa
    val refreshTick by TavoloRepository.refreshTick.collectAsState()

    //Riferimento alla view per poterla pilotare dal LaunchedEffect
    var mapViewRef by remember { mutableStateOf<InteractiveMapView?>(null) }

    Box(modifier = modifier.fillMaxSize()) {

        //AndroidView: incapsula la View Android classica dentro Compose
        //factory crea la view (una volta sola), update la aggiorna ad ogni ricomposizione
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                InteractiveMapView(context).apply {
                    cambiaImmagine(drawableRes)
                    caricaTavoli(quota)
                    //Tap su un tavolo → apre il dialog
                    onTableClick = { tavolo -> selectedTable = tavolo }
                    mapViewRef = this
                }
            },
            update = { mapView ->
                mapView.onTableClick = { tavolo -> selectedTable = tavolo }
                //Cambio edificio/piano → nuova planimetria
                mapView.cambiaImmagine(drawableRes)
            }
        )

        //Refresh periodico: ricarica i tavoli ogni 30s
        LaunchedEffect(quota, refreshTick, mapViewRef) {
            val mapView = mapViewRef ?: return@LaunchedEffect
            while (true) {
                mapView.caricaTavoli(quota, force = true)
                delay(30_000L)
            }
        }

        //Dialog del tavolo selezionato
        selectedTable?.let { tavolo ->
            TavoloDialog(
                tavolo = tavolo,
                onDismissRequest = { selectedTable = null }
            )
        }
    }
}