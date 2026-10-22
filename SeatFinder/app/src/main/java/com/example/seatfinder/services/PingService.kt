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
import com.example.seatfinder.auth.SessionManager

//"Guardiano" del tavolo occupato: controlla periodicamente se l'utente è ancora vicino al tavolo.
//Se si allontana troppo, mostra una notifica con due scelte (Mantieni / Libera)
//e dopo 10 minuti senza rispostalibera il tavolo da solo.
//Si spegne quando il tavolo viene liberato o quando l'app viene rimossa dai recenti.
class PingService : Service() {


    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private var pingJob: Job? = null
    private var gpsJob: Job? = null

    private var liberazioneJob: Job? = null
    private var timeoutAllontanamentoJob: Job? = null
    private var pausaProrogaJob: Job? = null

    //Dati del tavolo attuale (ricevuti via Intent all'avvio)
    private var idTavoloAttuale: Int? = null

    private var userId: Int = -1
    private var lat: Double? = null
    private var lng: Double? = null

    //Stato della gestione allontanamento:
    //notificaAllontanamentoInCorso: c'è già una notifica "torna al tavolo" aperta
    //inPausaAllontanamento: l'utente ha scelto "Mantieni", controlli sospesi per 10 min
    private var notificaAllontanamentoInCorso = false
    private var inPausaAllontanamento = false

    companion object {
        //Canali notifica: uno "silenzioso" per lo stato, uno "urgente" per gli avvisi
        const val CHANNEL_ID = "ping_tavolo_channel"
        const val CHANNEL_ID_ALERT = "ping_tavolo_alert_channel"

        //Id delle tre notifiche (stato, "tavolo liberato", richiesta di conferma)
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_ID_LIBERATO = 2
        const val NOTIFICATION_ID_CONFERMA = 3
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_ID_TAVOLO = "id_tavolo"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"

        //Azioni dei bottoni della notifica di conferma
        const val ACTION_MANTIENI = "com.example.seatfinder.ACTION_MANTIENI"
        const val ACTION_LIBERA = "com.example.seatfinder.ACTION_LIBERA"

        //Tempi e soglie
        const val SECONDI_GRAZIA = 15L                                 //Attesa dopo rimozione app prima di liberare
        const val DISTANZA_MASSIMA_METRI = 50f                         //Soglia "allontanamento"
        const val TIMEOUT_ALLONTANAMENTO_MS_NOTIFICA = 10 * 60 * 1000L //10 min per rispondere alla notifica
        const val TIMEOUT_PAUSA_MS_NOTIFICA = 10 * 60 * 1000L          //10 min di proroga dopo "Mantieni"
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
        userId = intent?.getIntExtra(EXTRA_USER_ID, -1) ?: -1        //Notifica persistente: senza questo il sistema ucciderebbe il servizio
        startForeground(NOTIFICATION_ID, creaNotifica())

        liberazioneJob?.cancel()
        liberazioneJob = null

        if (idTavolo != -1) {
            idTavoloAttuale = idTavolo
            //Le coordinate sono opzionali: se mancano, il controllo distanza non parte
            if (intent!!.hasExtra(EXTRA_LAT)) lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
            if (intent.hasExtra(EXTRA_LNG)) lng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
            avviaPing(idTavolo)
            avviaGps(idTavolo)
        }

        return START_STICKY
    }

    //Loop di aggiornamento ping, ogni 60 secondi
    private fun avviaPing(idTavolo: Int) {
        pingJob?.cancel()
        pingJob = serviceScope.launch {
            while (isActive) {

                TavoloRepository.pingOccupazione(idTavolo, SessionManager.userId)
                delay(60_000) //aggiornamento ping ogni minuto
            }
        }
    }

    //Loop di controllo posizione: ogni ciclo legge la posizione e la confronta con l'ancora.
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun avviaGps(idTavolo: Int) {
        gpsJob?.cancel()
        gpsJob = serviceScope.launch {
            while (isActive) {

                //Controlla solo se non c'è già una notifica aperta o una pausa in corso
                if (!notificaAllontanamentoInCorso && !inPausaAllontanamento) {
                    controllaDistanza(idTavolo)
                }

                delay(60_000) //controllo distanza ogni minuto
            }
        }
    }

    //Legge la posizione attuale e calcola la distanza dall'ancora.
    //Se supera DISTANZA_MASSIMA_METRI, avvia la procedura di allontanamento.
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private suspend fun controllaDistanza(idTavolo: Int) {
        val latAncora = lat ?: return
        val lngAncora = lng ?: return

        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)
            val cancellationTokenSource = CancellationTokenSource()

            //getCurrentLocation: prende la posizione "adesso"
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

    //Mostra la notifica "ti sei allontanato" e avvia il timeout di 10 minuti.
    //Se l'utente non risponde entro il timeout, il tavolo viene liberato.
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

    //Bottone "Mantieni": cancella il timeout di liberazione e sospende i controlli
    //per 10 minuti (l'utente sta tornando al tavolo).
    private fun gestisciMantieni() {
        Log.d("DEBUG_LOC", "L'utente ha scelto di mantenere il tavolo: pausa controlli per 10 minuti")

        //Annulla la liberazione automatica
        timeoutAllontanamentoJob?.cancel()
        timeoutAllontanamentoJob = null

        nascondiNotificaConferma()
        notificaAllontanamentoInCorso = false

        //Pausa di 10 min: dopo, il loop riprende a controllare la distanza
        inPausaAllontanamento = true
        pausaProrogaJob?.cancel()
        pausaProrogaJob = serviceScope.launch {
            delay(TIMEOUT_PAUSA_MS_NOTIFICA)
            inPausaAllontanamento = false
            Log.d("DEBUG_LOC", "Fine pausa 10 minuti: i controlli sulla distanza dal tavolo sono di nuovo attivi")
        }
    }

    //Bottone "Libera": libera subito e ferma il servizio.
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

    //Chiamato quando l'app viene rimossa dalle app recenti.
    //Diamo SECONDI_GRAZIA per riaprirla; se l'utente non torna, il tavolo viene liberato.
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val idTavolo = idTavoloAttuale
        if (idTavolo != null) {
            liberazioneJob?.cancel()
            liberazioneJob = serviceScope.launch {
                delay(SECONDI_GRAZIA * 1000) //s in ms
                TavoloRepository.liberaTavolo(idTavolo)
                TavoloRepository.setTavolo(null)
                mostraNotificaLiberato(1)
                stopSelf()
            }
        } else {
            stopSelf()
        }
    }

    //Crea i due canali notifica.
    //CHANNEL_ID: importanza bassa, silenzioso, per la notifica "sto mantenendo il tavolo"
    //CHANNEL_ID_ALERT: importanza alta, per l'avviso di allontanamento (deve attirare l'attenzione)
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

    //Notifica persistente (foreground): comunica che stai mantenendo il tavolo.
    private fun creaNotifica(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tavolo occupato")
            .setContentText("Stai mantenendo la prenotazione del tavolo")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
    }

    //Notifica "ti sei allontanato" con due bottoni: Mantieni (+10 min) e Libera.
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

    //Notifica "tavolo liberato". Due messaggi:
    //l'utente si è allontanato (o ha premuto Libera)
    //l'app è stata chiusa dai recenti
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

    //Pulizia
    override fun onDestroy() {
        super.onDestroy()
        pingJob?.cancel()
        gpsJob?.cancel()
        liberazioneJob?.cancel()
        timeoutAllontanamentoJob?.cancel()
        pausaProrogaJob?.cancel()
        nascondiNotificaConferma()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}