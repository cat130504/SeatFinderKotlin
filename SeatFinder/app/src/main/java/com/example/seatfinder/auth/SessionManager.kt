package com.example.seatfinder.auth


//Memoria condivisa della sessione

// Dati dell'utente utili dopo il login
data class UserLogin(
    val id: Int,
    val iniziali: String
)

object SessionManager {
    // -1 = nessun utente loggato
    var userId: Int = -1


    var nome: String? = null
    var iniziali: String? = null

    // true se c'è un utente in memoria
    fun isLoggedIn(): Boolean = userId != -1

    fun clear() {
        userId = -1
        nome = null
        iniziali = null
    }
}