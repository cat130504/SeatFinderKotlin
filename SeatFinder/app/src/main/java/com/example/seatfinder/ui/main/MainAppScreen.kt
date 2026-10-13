package com.example.seatfinder.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.example.seatfinder.ui.stats.StatsScreen

@Composable
fun MainAppScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val navController = rememberNavController()

    val tavoloAttuale by viewModel.tavoloAttuale.collectAsState()
    val infoTavolo by viewModel.infoTavolo.collectAsState()
    val edifici by viewModel.edifici.collectAsState()
    val selezione by viewModel.selezione.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var showProfileDialog by remember { mutableStateOf(false) }

    val mapDrawable = remember(selezione.edificio, selezione.quota) {
        val edificio = selezione.edificio
        val quota = selezione.quota
        if (edificio != null && quota != null) drawableFromQuota(context, edificio.nome, quota)
        else R.drawable.q155
    }

    val opzioniEdifici = remember(edifici) {
        generaOpzioniEdifici(edifici, viewModel::selezionaEdificio)
    }
    val opzioniPiani = remember(selezione.piani) {
        generaOpzioniPiani(selezione.piani, viewModel::selezionaPiano)
    }

    LaunchedEffect(tavoloAttuale) {
        val id = tavoloAttuale
        if (id != null) {
            PingServiceLauncher.start(context, id, viewModel.tavoloLat, viewModel.tavoloLng)
        } else {
            PingServiceLauncher.stop(context)
        }
    }

    if (showProfileDialog) {
        ProfileDialog(
            nome = SessionManager.nome ?: SessionManager.iniziali ?: "Utente",
            onConfirmLogout = {
                showProfileDialog = false
                AuthManager.logout(context)
            },
            onDismiss = { showProfileDialog = false }
        )
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
                    MapScreen(
                        drawableRes = mapDrawable,
                        quota = selezione.quota
                    )
                }
                composable(Destination.STATS.route) {
                    StatsScreen()
                }
                composable(Destination.QR.route) {
                    QrScreen(
                        onSuccessOccupazione = { idTavolo, lat, lng ->
                            viewModel.onTavoloOccupato(idTavolo, lat, lng)
                            navController.navigate(Destination.MAP.route) {
                                popUpTo(Destination.MAP.route) { inclusive = true }
                            }
                        }
                    )
                }
            }

            if (currentRoute == Destination.MAP.route) {
                selezione.quota?.let { quota ->
                    PlanBadge(
                        planName = "Piano: $quota",
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
