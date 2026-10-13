package com.example.seatfinder.auth

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

sealed interface LoginResult {
    data class Success(val user: UserLogin) : LoginResult
    data object InvalidCredentials : LoginResult
    data class Error(val cause: Throwable?) : LoginResult
}

object EsseTreAuthApi {

    private const val LOGIN_URL = "https://univpm.esse3.cineca.it/e3rest/api/login"
    private val client = OkHttpClient()   // una sola istanza, riusata

    suspend fun login(matricola: String, password: String): LoginResult =
        withContext(Dispatchers.IO) {
            try {
                val credentials = Base64.encodeToString(
                    "$matricola:$password".toByteArray(), Base64.NO_WRAP
                )
                val request = Request.Builder()
                    .url(LOGIN_URL)
                    .addHeader("Authorization", "Basic $credentials")
                    .build()

                client.newCall(request).execute().use { response ->
                    when {
                        response.code == 401 -> LoginResult.InvalidCredentials
                        !response.isSuccessful -> LoginResult.Error(null)
                        else -> {
                            val body = response.body?.string()
                                ?: return@use LoginResult.Error(null)
                            val user = JSONObject(body).getJSONObject("user")
                            LoginResult.Success(
                                UserLogin(
                                    id = user.getString("id").toInt(),
                                    iniziali = "${user.getString("firstName").first()}" +
                                            "${user.getString("lastName").first()}"
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                LoginResult.Error(e)
            }
        }
}
