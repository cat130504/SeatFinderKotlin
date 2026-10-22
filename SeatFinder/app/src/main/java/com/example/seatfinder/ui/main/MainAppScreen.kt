package com.example.seatfinder.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.seatfinder.R
import com.example.seatfinder.auth.AuthManager
import com.example.seatfinder.auth.SessionManager
import com.example.seatfinder.service.PingServiceLauncher
import com.example.seatfinder.ui.components.ExpandableFabMenu
import com.example.seatfinder.ui.components.PlanBadge
import com.example.seatfinder.ui.map.MapScreen
import com.example.seatfinder.ui.map.drawableFromQuota
import com.example.seatfinder.ui.map.generaOpzioniEdifici
import com.example.seatfinder.ui.map.generaOpzioniPiani
import com.example.seatfinder.ui.navigation.Destination
import com.example.seatfinder.ui.qr.QrScreen
import com.example.seatfinder.ui.stats.ProfileStatsScreen
import com.example.seatfinder.ui.stats.StatsScreen

//Collega UI con MainViewModel, costruisce la schermata "principale".Punto in cui si incontrano tutte le schermate delle fasi successive
@Composable
fun MainAppScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val navController = rememberNavController()

    //Stato osservato dal MainViewModel
    val tavoloAttuale by viewModel.tavoloAttuale.collectAsState()
    val infoTavolo by viewModel.infoTavolo.collectAsState()
    val edifici by viewModel.edifici.collectAsState()
    val selezione by viewModel.selezione.collectAsState()
    val ready by viewModel.ready.collectAsState()

    //Route corrente: serve a bottom bar e a capire quando mostrare i FAB della mappa
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var showProfileDialog by remember { mutableStateOf(false) }

    //Immagine della planimetria in base a edificio+quota scelti
    val mapDrawable = remember(selezione.edificio, selezione.quota) {
        val edificio = selezione.edificio
        val quota = selezione.quota
        if (edificio != null && quota != null) drawableFromQuota(context, edificio.nome, quota)
        else R.drawable.q155
    }

    //Voci dei due FAB (edifici a sinistra, piani a destra)
    val opzioniEdifici = remember(edifici) {
        generaOpzioniEdifici(edifici, viewModel::selezionaEdificio)
    }
    val opzioniPiani = remember(selezione.piani) {
        generaOpzioniPiani(selezione.piani, viewModel::selezionaPiano)
    }

    //Accende/spegne il PingService in base al tavolo attuale.
    LaunchedEffect(tavoloAttuale) {
        val id = tavoloAttuale
        if (id != null) {
            PingServiceLauncher.start(context, id, SessionManager.userId, viewModel.tavoloLat, viewModel.tavoloLng)        } else {
            PingServiceLauncher.stop(context)
        }
    }

    //Dialog di conferma logout
    if (showProfileDialog) {
        ProfileDialog(
            nome = SessionManager.nome ?: SessionManager.iniziali ?: "Utente",
            onConfirmLogout = {
                showProfileDialog = false
                viewModel.logout { AuthManager.logout(context) }
            },
            onDismiss = { showProfileDialog = false }
        )
    }

    //Finché il caricamento iniziale non è finito, solo spinner
    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            AppTopBar(
                iniziali = SessionManager.iniziali,
                infoTavolo = infoTavolo,
                tavoloAttuale = tavoloAttuale,
                onProfileClick = { showProfileDialog = true },
                onLiberaClick = viewModel::liberaTavolo
            )
        },
        bottomBar = {
            AppBottomBar(
                currentRoute = currentRoute,
                //QR disabilitato se hai già un tavolo occupato
                scanEnabled = tavoloAttuale == null,
                onDestinationClick = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            NavHost(
                navController = navController,
                startDestination = Destination.MAP.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Destination.MAP.route) {
                    //GATE LOCALE: la mappa si disegna solo con edificio+quota noti
                    val edificio = selezione.edificio
                    val quota = selezione.quota
                    if (edificio != null && quota != null) {
                        MapScreen(
                            drawableRes = mapDrawable,
                            quota = quota
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                composable(Destination.PROFILE_STATS.route) {
                    ProfileStatsScreen()
                }
                composable(Destination.STATS.route) {
                    StatsScreen()
                }
                composable(Destination.QR.route) {
                    QrScreen(
                        onSuccessOccupazione = { idTavolo, lat, lng ->
                            //QR letto: salva tavolo e coordinate, poi torna alla mappa
                            viewModel.onTavoloOccupato(idTavolo, lat, lng)
                            navController.navigate(Destination.MAP.route) {
                                popUpTo(Destination.MAP.route) { inclusive = true }
                            }
                        }
                    )
                }
            }

            //Solo sulla mappa: badge edificio/piano in alto e i due FAB in basso
            if (currentRoute == Destination.MAP.route) {
                val edificio = selezione.edificio
                val quota = selezione.quota

                if (edificio != null && quota != null) {
                    PlanBadge(
                        planName = "Edificio: ${edificio.nome}   Piano: $quota",
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                    )
                }
                if (opzioniPiani.isNotEmpty()) {
                    ExpandableFabMenu(
                        options = opzioniPiani,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    )
                }
                if (opzioniEdifici.isNotEmpty()) {
                    ExpandableFabMenu(
                        options = opzioniEdifici,
                        alignment = Alignment.Start,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}