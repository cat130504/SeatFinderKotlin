package com.example.seatfinder.service

import android.content.Context
import android.content.Intent
import android.os.Build

//Accende e spegne il PingService
object PingServiceLauncher {

    //Avvia il servizio passandogli id del tavolo e coordinate dell'ancora.
    //Le coordinate servono a PingService per calcolare la distanza dal tavolo.
    fun start(context: Context, idTavolo: Int, lat: Double?, lng: Double?) {
        val intent = Intent(context, PingService::class.java).apply {
            putExtra(PingService.EXTRA_ID_TAVOLO, idTavolo)
            //Aggiunge lat/lng solo se presenti: se l'utente ha negato la posizione
            //il service partirà comunque ma non potrà controllare la distanza
            lat?.let { putExtra(PingService.EXTRA_LAT, it) }
            lng?.let { putExtra(PingService.EXTRA_LNG, it) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    //Ferma il servizio
    fun stop(context: Context) {
        context.stopService(Intent(context, PingService::class.java))
    }
}