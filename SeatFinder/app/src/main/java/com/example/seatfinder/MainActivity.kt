package com.example.appmobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.appmobile.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Log.w("PERMESSI", "Permesso notifiche negato")
        }
    }

    private val requestBackgroundLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Log.w("PERMESSI", "Permesso posizione in background negato")
        }
    }

    private val requestForegroundLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { risultati ->
        val concesso = risultati[Manifest.permission.ACCESS_FINE_LOCATION] == true

        if (concesso && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestBackgroundLocationPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestForegroundLocationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestBackgroundLocationPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        setContent {
            MaterialTheme {
                MainAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()

    // 1. STATI GLOBALI DELL'APP
    val tavoloAttuale by TavoloRepo.tavoloAttuale.collectAsState()
    var mapDrawable by remember { mutableIntStateOf(R.drawable.q155) }
    var mapQuota by remember { mutableStateOf<String?>(null) }

    // 2. STATI PER I MENU FAB (Edifici e Piani)
    var opzioniEdifici by remember { mutableStateOf<List<EdiOption>>(emptyList()) }
    var opzioniPiani by remember { mutableStateOf<List<FabOption>>(emptyList()) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current

    var tavoloLat by remember { mutableStateOf<Double?>(null) }
    var tavoloLng by remember { mutableStateOf<Double?>(null) }

    var infoTavolo by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Stato per mostrare/nascondere la finestra del profilo
    var showProfileDialog by remember { mutableStateOf(false) }

    // Recupero info tavolo occupato (Edificio e Quota)
    LaunchedEffect(tavoloAttuale) {
        if (tavoloAttuale != null) {
            infoTavolo = DB.getInfoTavolo(tavoloAttuale!!)
        } else {
            infoTavolo = null
        }
    }

    suspend fun caricaPianiEdificio(context: Context, edificioId: Int, nomeEdificio: String) {
        val piani = withContext(Dispatchers.IO) { DB.getPiani(edificioId) }
        opzioniPiani = generaOpzioniPiani(context, piani, nomeEdificio) { drawable, quota ->
            mapDrawable = drawable
            mapQuota = quota
        }

        piani.firstOrNull()?.let { primoPiano ->
            mapDrawable = drawableFromQuota(context, nomeEdificio, primoPiano.quota)
            mapQuota = primoPiano.quota
        }
    }

    LaunchedEffect(tavoloAttuale) {
        val intent = Intent(context, PingService::class.java)
        if (tavoloAttuale != null) {
            intent.putExtra(PingService.EXTRA_ID_TAVOLO, tavoloAttuale!!)
            tavoloLat?.let { intent.putExtra(PingService.EXTRA_LAT, it) }
            tavoloLng?.let { intent.putExtra(PingService.EXTRA_LNG, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
    }

    LaunchedEffect(Unit) {
        TavoloRepo.setTavolo(DB.getTavoloUtente(SessionManager.userId))
        try {
            val edificiDB = DB.getEdifici()

            if (edificiDB.isNotEmpty()) {
                val primoEdificio = edificiDB.first()
                caricaPianiEdificio(context, primoEdificio.id, primoEdificio.nome)
            }

            opzioniEdifici = generaOpzioniEdifici(edificiDB) { edificioId, nomeEdificio ->
                coroutineScope.launch {
                    caricaPianiEdificio(context, edificioId, nomeEdificio)
                }
            }
        } catch (e: Exception) {
            Log.e("SUPABASE_INIT", "Errore: ${e.message}")
        }
    }

    // Finestra popup Profilo / Logout
    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = {
                Text(
                    text = "Ciao ${SessionManager.nome ?: SessionManager.iniziali ?: "Utente"}!",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Vuoi effettuare il logout dall'applicazione?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showProfileDialog = false

                        // Pulisce la sessione in memoria
                        SessionManager.clear()

                        // Pulisce le SharedPreferences se presenti
                        val prefs = context.getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
                        prefs.edit().clear().apply()

                        // Ferma il servizio in background se attivo
                        context.stopService(Intent(context, PingService::class.java))

                        // Torna a LoginActivity ripulendo il backstack
                        val intent = Intent(context, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }

    // 4. STRUTTURA DELLA SCHERMATA
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cerchio con le Iniziali - ORA CLICCABILE
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { showProfileDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = SessionManager.iniziali ?: "?",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        // Testo Edificio, Piano e Tavolo (se occupato)
                        if (infoTavolo != null) {
                            Column {
                                Text(
                                    text = infoTavolo!!.first,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Piano: ${infoTavolo!!.second}",
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
                    if (tavoloAttuale != null) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val successo = tavoloAttuale?.let { DB.liberaTavolo(it) } ?: false
                                    if (successo) {
                                        TavoloRepo.setTavolo(null)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Libera Tavolo")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val isEnabled = if (destination == Destination.QR) tavoloAttuale == null else true

                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        enabled = isEnabled,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.contentDescription) },
                        label = { Text(destination.label) }
                    )
                }
            }
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
                        quota = mapQuota
                    )
                }
                composable(Destination.STATS.route) {
                    StatsScreen()
                }
                composable(Destination.QR.route) {
                    QrScreen(
                        onSuccessOccupazione = { idTavoloOccupato, lat, lng ->
                            TavoloRepo.setTavolo(idTavoloOccupato)
                            tavoloLat = lat
                            tavoloLng = lng
                            navController.navigate(Destination.MAP.route) {
                                popUpTo(Destination.MAP.route) { inclusive = true }
                            }
                        }
                    )
                }
            }
            if (currentRoute == Destination.MAP.route) {
                if (mapQuota != null) {
                    PlanBadge(
                        planName = "Piano: $mapQuota",
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
                    ExpandableFabMenuEdi(
                        options = opzioniEdifici,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}

private fun drawableFromQuota(context: Context, edificio: String, quota: String): Int {
    fun norm(s: String) = s.trim().lowercase().replace(" ", "_")

    val candidati = listOf(
        "${norm(edificio)}_${norm(quota)}",
        norm(quota)
    )
    for (nome in candidati) {
        val id = context.resources.getIdentifier(nome, "drawable", context.packageName)
        if (id != 0) return id
    }
    return R.drawable.login
}

private fun generaOpzioniPiani(
    context: Context,
    quote: List<PianoQuota>,
    nomeEdificio: String,
    onPianoSelected: (Int, String) -> Unit
): List<FabOption> {
    return quote.map { oggettoPiano ->
        FabOption(
            icon = Icons.Default.Description,
            label = oggettoPiano.quota,
            onClick = {
                onPianoSelected(
                    drawableFromQuota(context, nomeEdificio, oggettoPiano.quota),
                    oggettoPiano.quota
                )
            }
        )
    }
}

private fun generaOpzioniEdifici(
    edifici: List<Edificio>,
    onEdiSelected: (Int, String) -> Unit
): List<EdiOption> {
    return edifici.map { oggettoEdi ->
        EdiOption(
            icon = Icons.Default.Business,
            label = oggettoEdi.nome,
            onClick = {
                onEdiSelected(oggettoEdi.id, oggettoEdi.nome)
            }
        )
    }
}