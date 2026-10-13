package com.example.seatfinder.auth

data class UserLogin(
    val id: Int,
    val iniziali: String
)

object SessionManager {
    var userId: Int = -1
    var nome: String? = null
    var iniziali: String? = null

    fun isLoggedIn(): Boolean = userId != -1

    fun clear() {
        userId = -1
        nome = null
        iniziali = null
    }
}
