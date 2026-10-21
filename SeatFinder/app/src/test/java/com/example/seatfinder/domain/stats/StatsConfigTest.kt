package com.example.seatfinder.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsConfigTest {

    @Test
    fun leCostantiHannoIValoriAttesi() {
        assertEquals(30, StatsConfig.GIORNI_STORICO)
        assertEquals(8..18, StatsConfig.ORE_APERTURA)
        assertEquals(listOf(1, 2, 3, 4, 5), StatsConfig.GIORNI_FERIALI)
        assertEquals(100, StatsConfig.SOGLIA_POCHI_DATI)
        assertEquals(10, StatsConfig.SOGLIA_POCO_AFFOLLATA)
    }

    @Test
    fun oreAperturaCopreUndiciOre() {
        assertEquals(11, StatsConfig.ORE_APERTURA.count())
    }

    @Test
    fun giorniFerialeContieneCinqueGiorni() {
        assertEquals(5, StatsConfig.GIORNI_FERIALI.size)
    }
}