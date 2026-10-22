package com.example.seatfinder.service

import android.content.Context
import android.content.Intent
import android.os.Build

//Accende e spegne il PingService
object PingServiceLauncher {

    //Avvia il servizio passandogli id del tavolo e coordinate dell'ancora.
    //Le coordinate servono a PingService per calcolare la distanza dal tavolo.
    fun start(context: Context, idTavolo: Int, userId: Int, lat: Double?, lng: Double?) {
        val intent = Intent(context, PingService::class.java).apply {
            putExtra(PingService.EXTRA_ID_TAVOLO, idTavolo)
            putExtra(PingService.EXTRA_USER_ID, userId)
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