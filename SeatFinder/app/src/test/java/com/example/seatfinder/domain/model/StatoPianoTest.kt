package com.example.seatfinder.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StatoPianoTest {

    private fun piano(
        occupati: Int, totali: Int,
        postiLiberi: Int = 0, postiTotali: Int = 0
    ) = StatoPiano(
        quota = "1", edificioId = 1, edificioNome = "Edificio A",
        tavoliOccupati = occupati, tavoliTotali = totali,
        postiLiberi = postiLiberi, postiTotali = postiTotali
    )

    @Test
    fun tavoliLiberiELaDifferenzaFraTotaliEOccupati() {
        assertEquals(7, piano(3, 10).tavoliLiberi)
    }

    @Test
    fun percentualeOccupazioneCalcolaIlRapportoCorretto() {
        assertEquals(0.5f, piano(5, 10).percentualeOccupazione, 0.001f)
    }

    @Test
    fun percentualeOccupazioneEZeroSeNonCiSonoTavoli() {
        assertEquals(0f, piano(0, 0).percentualeOccupazione, 0.001f)
    }

    @Test
    fun disponibilitaEIlComplementoDellaPercentualeDiOccupazione() {
        assertEquals(0.7f, piano(3, 10).disponibilita, 0.001f)
    }

    @Test
    fun pianoCompletamenteOccupatoHaDisponibilitaZero() {
        val s = piano(10, 10)
        assertEquals(1f, s.percentualeOccupazione, 0.001f)
        assertEquals(0f, s.disponibilita, 0.001f)
    }

    @Test
    fun pianoCompletamenteLiberoHaDisponibilitaUno() {
        val s = piano(0, 10)
        assertEquals(0f, s.percentualeOccupazione, 0.001f)
        assertEquals(1f, s.disponibilita, 0.001f)
    }
}