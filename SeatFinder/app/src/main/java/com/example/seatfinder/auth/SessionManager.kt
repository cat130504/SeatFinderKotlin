package com.example.seatfinder.auth


//Memoria condivisa della sessione

// Dati dell'utente utili dopo il login
data class UserLogin(
    val id: Int,
    val iniziali: String,
    val nome: String
)

object SessionManager {
    // -1 = nessun utente loggato
    var userId: Int = -1

    var nome: String? = null
    var iniziali: String? = null
    fun clear() {
        userId = -1
        nome = null
        iniziali = null
    }
}