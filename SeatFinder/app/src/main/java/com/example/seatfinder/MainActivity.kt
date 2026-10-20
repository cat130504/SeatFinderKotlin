package com.example.seatfinder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.example.seatfinder.ui.theme.SeatFinderTheme
import androidx.core.content.ContextCompat
import com.example.seatfinder.ui.main.MainAppScreen

//Activity dell'app dopo il login e monta il MainAppScreen
class MainActivity : ComponentActivity() {

    //Permesso notifiche, se negato, PingService non può mostrare avvisi
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Log.w("PERMESSI", "Permesso notifiche negato")
        }
    }

    //Posizione in background: serve al PingService per controllare la distanza anche con app chiusa
    private val requestBackgroundLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Log.w("PERMESSI", "Permesso posizione in background negato")
        }
    }

    // Posizione in foreground
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

        richiediPermessi()

        setContent {
            SeatFinderTheme  {
                MainAppScreen()
            }
        }
    }
    //Cosa chiediamo e in che ordine:
    //Posizione precisa, perché senza di essa niente PingService
    //Poi la posizione in background , che il sistema concede solo se hai già dato quella precisa
    //Le notifiche, per gli avvisi di allontanamento
    private fun richiediPermessi() {
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
    }
}