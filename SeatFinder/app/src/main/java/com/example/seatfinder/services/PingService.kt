package com.example.seatfinder.service

import android.Manifest
import android.app.*
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.example.seatfinder.data.repository.TavoloRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class PingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var pingJob: Job? = null
    private var liberazioneJob: Job? = null
    private var timeoutAllontanamentoJob: Job? = null
    private var pausaProrogaJob: Job? = null

    private var idTavoloAttuale: Int? = null
    private var lat: Double? = null
    private var lng: Double? = null

    // Stato gestione allontanamento
    private var notificaAllontanamentoInCorso = false
    private var inPausaAllontanamento = false

    companion object {
        const val CHANNEL_ID = "ping_tavolo_channel"
        const val CHANNEL_ID_ALERT = "ping_tavolo_alert_channel"

        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_ID_LIBERATO = 2
        const val NOTIFICATION_ID_CONFERMA = 3

        const val EXTRA_ID_TAVOLO = "id_tavolo"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"

        const val ACTION_MANTIENI = "com.example.seatfinder.ACTION_MANTIENI"
        const val ACTION_LIBERA = "com.example.seatfinder.ACTION_LIBERA"

        const val SECONDI_GRAZIA = 15L
        const val DISTANZA_MASSIMA_METRI = 50f
        const val TIMEOUT_ALLONTANAMENTO_MS_NOTIFICA = 10 * 60 * 1000L // 10 minuti per rispondere prima di liberare
        const val TIMEOUT_PAUSA_MS_NOTIFICA = 10 * 60 * 1000L          // 10 minuti di proroga prima di riverificare
    }

    override fun onCreate() {
        super.onCreate()
        creaCanaliNotifica()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MANTIENI -> {
                gestisciMantieni()
                return START_STICKY
            }
            ACTION_LIBERA -> {
                gestisciLiberaSubito()
                return START_NOT_STICKY
            }
        }

        val idTavolo = intent?.getIntExtra(EXTRA_ID_TAVOLO, -1) ?: -1

        startForeground(NOTIFICATION_ID, creaNotifica())

        liberazioneJob?.cancel()
        liberazioneJob = null

        if (idTavolo != -1) {
            idTavoloAttuale = idTavolo
            if (intent!!.hasExtra(EXTRA_LAT)) lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
            if (intent.hasExtra(EXTRA_LNG)) lng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
            avviaPing(idTavolo)
        }

        return START_STICKY
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun avviaPing(idTavolo: Int) {
        pingJob?.cancel()
        pingJob = serviceScope.launch {
            while (isActive) {

                // Controlla la distanza solo se non c'è una notifica attiva o non siamo nei 10 min di proroga
                if (!notificaAllontanamentoInCorso && !inPausaAllontanamento) {
                    controllaDistanza(idTavolo)
                }

                delay(5_000) //controllo distanza ogni 5 min
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private suspend fun controllaDistanza(idTavolo: Int) {
        val latAncora = lat ?: return
        val lngAncora = lng ?: return

        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)
            val cancellationTokenSource = CancellationTokenSource()

            val location = fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).await() ?: run {
                Log.d("DEBUG_LOC", "getCurrentLocation ha restituito null")
                return
            }

            val risultato = FloatArray(1)
            Location.distanceBetween(latAncora, lngAncora, location.latitude, location.longitude, risultato)
            val distanza = risultato[0]

            Log.d("DEBUG_LOC", "Distanza dal tavolo: $distanza metri")

            if (distanza > DISTANZA_MASSIMA_METRI) {
                avviaProceduraAllontanamento(idTavolo)
            }
        } catch (e: Exception) {
            Log.e("DB_ERROR", "Errore controllo posizione", e)
        }
    }

    private fun avviaProceduraAllontanamento(idTavolo: Int) {
        notificaAllontanamentoInCorso = true
        mostraNotificaConferma()

        timeoutAllontanamentoJob?.cancel()
        timeoutAllontanamentoJob = serviceScope.launch {
            delay(TIMEOUT_ALLONTANAMENTO_MS_NOTIFICA)
            Log.d("DEBUG_LOC", "Nessuna risposta entro 10 minuti, libero il tavolo")
            nascondiNotificaConferma()
            TavoloRepository.liberaTavolo(idTavolo)
            TavoloRepository.setTavolo(null)
            mostraNotificaLiberato(0)
            stopSelf()
        }
    }

    private fun gestisciMantieni() {
        Log.d("DEBUG_LOC", "L'utente ha scelto di mantenere il tavolo: pausa controlli per 10 minuti")

        // Cancella il timer di liberazione automatica
        timeoutAllontanamentoJob?.cancel()
        timeoutAllontanamentoJob = null

        nascondiNotificaConferma()
        notificaAllontanamentoInCorso = false

        // Entra in pausa per 10 minuti mantenendo le coordinate originali del tavolo
        inPausaAllontanamento = true
        pausaProrogaJob?.cancel()
        pausaProrogaJob = serviceScope.launch {
            delay(TIMEOUT_PAUSA_MS_NOTIFICA)
            inPausaAllontanamento = false
            Log.d("DEBUG_LOC", "Fine pausa 10 minuti: i controlli sulla distanza dal tavolo sono di nuovo attivi")
        }
    }

    private fun gestisciLiberaSubito() {
        Log.d("DEBUG_LOC", "L'utente ha scelto di liberare il tavolo")
        timeoutAllontanamentoJob?.cancel()
        pausaProrogaJob?.cancel()
        nascondiNotificaConferma()

        val idTavolo = idTavoloAttuale
        serviceScope.launch {
            if (idTavolo != null) {
                TavoloRepository.liberaTavolo(idTavolo)
                TavoloRepository.setTavolo(null)
            }
            mostraNotificaLiberato(0)
            stopSelf()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val idTavolo = idTavoloAttuale
        if (idTavolo != null) {
            liberazioneJob?.cancel()
            liberazioneJob = serviceScope.launch {
                delay(SECONDI_GRAZIA * 1000)
                TavoloRepository.liberaTavolo(idTavolo)
                TavoloRepository.setTavolo(null)
                mostraNotificaLiberato(1)
                stopSelf()
            }
        } else {
            stopSelf()
        }
    }

    private fun creaCanaliNotifica() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val channelLow = NotificationChannel(
                CHANNEL_ID,
                "Stato Tavolo",
                NotificationManager.IMPORTANCE_LOW
            )

            val channelAlert = NotificationChannel(
                CHANNEL_ID_ALERT,
                "Avvisi Tavolo",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifiche di allontanamento e richiesta conferma"
            }

            manager.createNotificationChannel(channelLow)
            manager.createNotificationChannel(channelAlert)
        }
    }

    private fun creaNotifica(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tavolo occupato")
            .setContentText("Stai mantenendo la prenotazione del tavolo")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
    }

    private fun mostraNotificaConferma() {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val mantieniIntent = Intent(this, PingService::class.java).apply {
            action = ACTION_MANTIENI
        }
        val mantieniPendingIntent = PendingIntent.getService(this, 101, mantieniIntent, flags)

        val liberaIntent = Intent(this, PingService::class.java).apply {
            action = ACTION_LIBERA
        }
        val liberaPendingIntent = PendingIntent.getService(this, 102, liberaIntent, flags)

        val notifica = NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
            .setContentTitle("Ti sei allontanato dal tavolo!")
            .setContentText("Vuoi mantenere il posto? Verrà liberato tra 10 minuti se non rispondi.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_save, "Mantieni (+10 min)", mantieniPendingIntent)
            .addAction(android.R.drawable.ic_menu_delete, "Libera", liberaPendingIntent)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_CONFERMA, notifica)
    }

    private fun nascondiNotificaConferma() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID_CONFERMA)
    }

    private fun mostraNotificaLiberato(a: Int) {
        val messaggio = if (a == 0) {
            "Ti sei allontanato, il tavolo è stato liberato."
        } else {
            "App chiusa, il tavolo è stato liberato."
        }

        val notifica = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tavolo liberato")
            .setContentText(messaggio)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_LIBERATO, notifica)
    }

    override fun onDestroy() {
        super.onDestroy()
        pingJob?.cancel()
        liberazioneJob?.cancel()
        timeoutAllontanamentoJob?.cancel()
        pausaProrogaJob?.cancel()
        nascondiNotificaConferma()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
