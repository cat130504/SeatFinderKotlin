package com.example.seatfinder.service

import android.content.Context
import android.content.Intent
import android.os.Build

object PingServiceLauncher {

    fun start(context: Context, idTavolo: Int, lat: Double?, lng: Double?) {
        val intent = Intent(context, PingService::class.java).apply {
            putExtra(PingService.EXTRA_ID_TAVOLO, idTavolo)
            lat?.let { putExtra(PingService.EXTRA_LAT, it) }
            lng?.let { putExtra(PingService.EXTRA_LNG, it) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, PingService::class.java))
    }
}
