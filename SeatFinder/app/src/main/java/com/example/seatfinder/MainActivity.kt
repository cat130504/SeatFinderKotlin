package com.example.seatfinder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.seatfinder.ui.main.MainAppScreen
import com.example.seatfinder.ui.theme.SeatFinderTheme

class MainActivity : ComponentActivity() {

    // Stato per mostrare il dialogo che guida l'utente a "Consenti sempre"
    private var mostraDialogoBackground by mutableStateOf(false)

    // Stato per quando l'utente nega definitivamente e dobbiamo mandarlo nelle impostazioni manuali
    private var mostraDialogoImpostazioniManuali by mutableStateOf(false)

    // 1. Permesso notifiche -> una volta finito, controlla la posizione
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Log.w("PERMESSI", "Permesso notifiche negato")
        }
        controllaPermessiPosizione()
    }

    // 2. Posizione in foreground -> se concessa, mostra il dialogo per il background
    private val requestForegroundLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { risultati ->
        val concesso = risultati[Manifest.permission.ACCESS_FINE_LOCATION] == true

        if (concesso) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !haPermessoBackground()) {
                mostraDialogoBackground = true
            }
        } else {
            Log.w("PERMESSI", "Permesso posizione precisa negato")
        }
    }

    // 3. Posizione in background (su Android 11+ apre direttamente la schermata di sistema)
    private val requestBackgroundLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Log.w("PERMESSI", "Permesso posizione in background negato")
            // Se l'utente torna indietro senza mettere "Consenti sempre", mostriamo il pulsante per le impostazioni
            mostraDialogoImpostazioniManuali = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        avviaCatenaPermessi()

        setContent {
            SeatFinderTheme {
                MainAppScreen()

                // Dialogo preventivo prima di mandarlo alla schermata di sistema
                if (mostraDialogoBackground) {
                    AlertDialog(
                        onDismissRequest = { mostraDialogoBackground = false },
                        title = { Text("Posizione in background necessaria") },
                        text = {
                            Text(
                                "Per permettere a SeatFinder di rilevare se ti allontani dal tavolo anche quando l'app è chiusa o lo schermo è spento, seleziona \"Consenti sempre\" nella schermata successiva."
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    mostraDialogoBackground = false
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        requestBackgroundLocationPermission.launch(
                                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                        )
                                    }
                                }
                            ) {
                                Text("Vai alle impostazioni")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { mostraDialogoBackground = false }) {
                                Text("Più tardi")
                            }
                        }
                    )
                }

                // Dialogo di fallback se l'utente ha già negato il permesso o il sistema blocca il launcher
                if (mostraDialogoImpostazioniManuali) {
                    AlertDialog(
                        onDismissRequest = { mostraDialogoImpostazioniManuali = false },
                        title = { Text("Imposta \"Consenti sempre\"") },
                        text = {
                            Text(
                                "Hai concesso la posizione solo mentre l'app è in uso. " +
                                        "Per evitare che la tua postazione venga liberata per errore in background, " +
                                        "apri le Impostazioni -> Autorizzazioni -> Posizione e seleziona \"Consenti sempre\"."
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    mostraDialogoImpostazioniManuali = false
                                    apriImpostazioniApp()
                                }
                            ) {
                                Text("Apri Impostazioni")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { mostraDialogoImpostazioniManuali = false }) {
                                Text("Annulla")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun avviaCatenaPermessi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            controllaPermessiPosizione()
        }
    }

    private fun controllaPermessiPosizione() {
        val haFineLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!haFineLocation) {
            requestForegroundLocationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !haPermessoBackground()) {
            mostraDialogoBackground = true //dialogo per posizione sempre concessa nelle impostazioni
        }
    }

    private fun haPermessoBackground(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // Apre direttamente la scheda "Informazioni app" di SeatFinder nelle impostazioni di Android
    private fun apriImpostazioniApp() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}