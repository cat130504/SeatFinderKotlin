package com.example.seatfinder.auth

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

//Chiamata al server Esse3 per verificare matricola + password

// Esito del login: solo una di queste tre cose può tornare
sealed interface LoginResult {
    data class Success(val user: UserLogin) : LoginResult
    // Matricola o password sbagliate
    data object InvalidCredentials : LoginResult
    // Problema tecnico
    data class Error(val cause: Throwable?) : LoginResult
}

object EsseTreAuthApi {

    // Endpoint Esse3
    private const val LOGIN_URL = "https://univpm.esse3.cineca.it/e3rest/api/login"

    // Client unico, riusato
    private val client = OkHttpClient()

    //Esegue il login
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
                            // Esse3 risponde con un JSON tipo : { "user": { "id": 123, "firstName": "Mario", "lastName": "Rossi" } }
                            val user = JSONObject(body).getJSONObject("user")
                            LoginResult.Success(
                                UserLogin(
                                    //Id dell'utente
                                    id = user.getString("id").toInt(),
                                    //Iniziali: prima lettera nome + prima lettera cognome.
                                    iniziali = "${user.getString("firstName").first()}" +
                                            "${user.getString("lastName").first()}",
                                    nome = "${user.getString("firstName").first()}"
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