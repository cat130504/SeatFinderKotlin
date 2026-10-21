package com.example.seatfinder.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TavoloTest {

    private fun tavolo(posti: Int, disponibili: Int) = Tavolo(
        id = 1, x = 0f, y = 0f, rotazione = 0f, quota = "1",
        isOccupato = false, presa = true, posti = posti, postiDisponibili = disponibili
    )

    @Test
    fun tavoloConZeroPostiDisponibiliEPieno() {
        assertTrue(tavolo(4, 0).isPieno)
    }

    @Test
    fun tavoloConTuttiIPostiDisponibiliELibero() {
        assertTrue(tavolo(4, 4).isLibero)
    }

    @Test
    fun tavoloConPostiParzialmenteOccupatiNonENePienoNeLibero() {
        val t = tavolo(4, 2)
        assertFalse(t.isPieno)
        assertFalse(t.isLibero)
    }
}