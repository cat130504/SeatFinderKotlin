package com.example.seatfinder.auth

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SessionManagerTest {

    @Before
    fun setUp() {
        SessionManager.clear()
    }

    @After
    fun tearDown() {
        SessionManager.clear()
    }

    @Test
    fun statoInizialeNonLoggato() {
        assertEquals(-1, SessionManager.userId)
        assertNull(SessionManager.nome)
        assertNull(SessionManager.iniziali)
    }

    @Test
    fun userIdDiventaValidoDopoLogin() {
        SessionManager.userId = 123
        assertEquals(123, SessionManager.userId)
    }

    @Test
    fun userIdMenoUnoRappresentaNonLoggato() {
        SessionManager.userId = -1
        assertEquals(-1, SessionManager.userId)
    }

    @Test
    fun clearAzzeraTuttiICampi() {
        SessionManager.userId = 42
        SessionManager.nome = "Mario Rossi"
        SessionManager.iniziali = "MR"

        SessionManager.clear()

        assertEquals(-1, SessionManager.userId)
        assertNull(SessionManager.nome)
        assertNull(SessionManager.iniziali)
    }

    @Test
    fun nomeEInizialiPossonoEssereImpostatiIndipendentemente() {
        SessionManager.userId = 99
        SessionManager.iniziali = "AB"
        assertNull(SessionManager.nome)
        assertEquals(99, SessionManager.userId)
        assertEquals("AB", SessionManager.iniziali)
    }
}