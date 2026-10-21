package com.example.seatfinder.auth

import android.content.Context
import android.content.Intent
import com.example.seatfinder.service.PingServiceLauncher
import androidx.core.content.edit

//Salva e ripristina la sessione, riaprendo l'app non serve rifare il login

object AuthManager {

    private const val PREFS_NAME = "LoginPrefs"
    private const val KEY_USER_ID = "userId"
    private const val KEY_NOME = "iniziali"

    private const val KEY_INIZIALI = "iniziali"
    private const val KEY_EXPIRE_TIME = "expireTime"

    //Durata della sessione: 7 giorni
    private const val SESSION_DURATION_MS = 7L * 24 * 60 * 60 * 1000

    //Apre le SharedPreferences della sessione
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    //Prova a ripristinare la sessione salvata: true solo se userId valido, iniziali presenti e non scaduta
    fun restoreSession(context: Context): Boolean {
        val p = prefs(context)
        val userId = p.getInt(KEY_USER_ID, -1)
        val iniziali = p.getString(KEY_INIZIALI, null)
        val nome = p.getString(KEY_NOME, null)
        val expire = p.getLong(KEY_EXPIRE_TIME, 0L)

        if (userId == -1 || iniziali == null || System.currentTimeMillis() >= expire) {
            return false
        }
        SessionManager.userId = userId
        SessionManager.iniziali = iniziali
        SessionManager.nome = nome
        return true
    }

    //Salva la sessione su disco e in memoria, con scadenza a 7 giorni da adesso.
    fun saveSession(context: Context, user: UserLogin) {
        prefs(context).edit()
            .putInt(KEY_USER_ID, user.id)
            .putString(KEY_INIZIALI, user.iniziali)
            .putString(KEY_NOME, user.nome)
            .putLong(KEY_EXPIRE_TIME, System.currentTimeMillis() + SESSION_DURATION_MS)
            .apply()
        SessionManager.userId = user.id
        SessionManager.iniziali = user.iniziali
        SessionManager.nome=user.nome
    }

    //Logout: pulisce memoria e disco, ferma il PingService e riapre il LoginActivity
    fun logout(context: Context) {
        SessionManager.clear()
        prefs(context).edit().clear().apply()
        PingServiceLauncher.stop(context)

        context.startActivity(
            Intent(context, LoginActivity::class.java).apply {
                //Il tasto "indietro" non torna all'app
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }
}