package com.example.seatfinder.auth

import android.content.Context
import android.content.Intent
import com.example.seatfinder.service.PingServiceLauncher

object AuthManager {

    private const val PREFS_NAME = "LoginPrefs"
    private const val KEY_USER_ID = "userId"
    private const val KEY_INIZIALI = "iniziali"
    private const val KEY_EXPIRE_TIME = "expireTime"

    // Durata della sessione: 7 giorni
    private const val SESSION_DURATION_MS = 7L * 24 * 60 * 60 * 1000

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    fun restoreSession(context: Context): Boolean {
        val p = prefs(context)
        val userId = p.getInt(KEY_USER_ID, -1)
        val iniziali = p.getString(KEY_INIZIALI, null)
        val expire = p.getLong(KEY_EXPIRE_TIME, 0L)

        if (userId == -1 || iniziali == null || System.currentTimeMillis() >= expire) {
            return false
        }
        SessionManager.userId = userId
        SessionManager.iniziali = iniziali
        return true
    }

    fun saveSession(context: Context, user: UserLogin) {
        prefs(context).edit()
            .putInt(KEY_USER_ID, user.id)
            .putString(KEY_INIZIALI, user.iniziali)
            .putLong(KEY_EXPIRE_TIME, System.currentTimeMillis() + SESSION_DURATION_MS)
            .apply()
        SessionManager.userId = user.id
        SessionManager.iniziali = user.iniziali
    }

    fun logout(context: Context) {
        SessionManager.clear()
        prefs(context).edit().clear().apply()
        PingServiceLauncher.stop(context)

        context.startActivity(
            Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }
}
