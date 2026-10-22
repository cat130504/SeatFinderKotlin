package com.example.seatfinder.domain.stats

import com.example.seatfinder.data.remote.dto.StatsFlussoRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsCalcoloTest {

    private fun row(giorno: Int, ora: Int, occ: Int, quota: String = "1") =
        StatsFlussoRow(
            quota = quota,
            giorno_settimana = giorno,
            ora = ora,
            occupazioni_totali = occ,
            posti_totali = 4
        )


    @Test
    fun calcolaFasciaConsigliataRestituisceNullSenzaDati() {
        assertNull(calcolaFasciaConsigliata(emptyList(), 1, 0))
    }

    @Test
    fun calcolaFasciaConsigliataRestituisceNullSeNonCiSonoDatiPerIlGiorno() {
        val rows = listOf(row(2, 10, 5))
        assertNull(calcolaFasciaConsigliata(rows, 1, 0))
    }

    @Test
    fun calcolaFasciaConsigliataSelezionaOraConMenoOccupazioni() {
        val rows = listOf(
            row(1, 9, 10),
            row(1, 10, 2),
            row(1, 11, 7)
        )
        assertEquals(10, calcolaFasciaConsigliata(rows, 1, 0))
    }

    @Test
    fun calcolaFasciaConsigliataSommaLeOccupazioniPerOraSuPiuQuote() {
        val rows = listOf(
            row(1, 10, 3, quota = "1"),
            row(1, 10, 4, quota = "2"),
            row(1, 11, 5, quota = "1")
        )
        assertEquals(11, calcolaFasciaConsigliata(rows, 1, 0))
    }

    @Test
    fun calcolaFasciaConsigliataIgnoraLeOreFuoriDallOrarioDiApertura() {
        val rows = listOf(
            row(1, 6, 0),
            row(1, 22, 0),
            row(1, 10, 5)
        )
        assertEquals(10, calcolaFasciaConsigliata(rows, 1, 0))
    }

    @Test
    fun calcolaFasciaConsigliataEscludeLeOrePassateSeOraMinimaEImpostata() {
        val rows = listOf(
            row(1, 9, 0),
            row(1, 14, 5),
            row(1, 15, 8)
        )
        assertEquals(14, calcolaFasciaConsigliata(rows, 1, 12))
    }

    @Test
    fun calcolaFasciaConsigliataRipiegaSuTutteLeOreSeNessunaEFutura() {
        val rows = listOf(
            row(1, 9, 3),
            row(1, 10, 5)
        )
        assertEquals(9, calcolaFasciaConsigliata(rows, 1, 20))
    }


    @Test
    fun livelloAffluenzaZeroELibera() {
        assertEquals(LivelloAffluenza.LIBERA, livelloAffluenza(0))
    }

    @Test
    fun livelloAffluenzaDaUnoADieciEPocoAffollata() {
        assertEquals(LivelloAffluenza.POCO_AFFOLLATA, livelloAffluenza(1))
        assertEquals(LivelloAffluenza.POCO_AFFOLLATA, livelloAffluenza(10))
    }

    @Test
    fun livelloAffluenzaSopraSogliaEAffollata() {
        assertEquals(LivelloAffluenza.AFFOLLATA, livelloAffluenza(11))
        assertEquals(LivelloAffluenza.AFFOLLATA, livelloAffluenza(100))
    }


    @Test
    fun mediaGiornalieraCalcolaLaMediaSu30Giorni() {
        assertEquals(0f, mediaGiornaliera(0), 0.001f)
        assertEquals(7f, mediaGiornaliera(30), 0.001f)
        assertEquals(23.333f, mediaGiornaliera(100), 0.1f)
    }


    @Test
    fun occupazioniFasciaSommaSoloLeRigheCorrispondenti() {
        val rows = listOf(
            row(1, 10, 3),
            row(1, 10, 4, quota = "2"),
            row(1, 11, 100),
            row(2, 10, 50)
        )
        assertEquals(7, occupazioniFascia(rows, 1, 10))
    }

    @Test
    fun occupazioniFasciaRestituisceZeroSeNonTrovaCorrispondenze() {
        assertEquals(0, occupazioniFascia(emptyList(), 1, 10))
    }


    @Test
    fun giorniDaOggiDaLunediRestituisceLunVen() {
        assertEquals(listOf(1, 2, 3, 4, 5), giorniDaOggi(1))
    }

    @Test
    fun giorniDaOggiDaMercolediRuotaCorrettamente() {
        assertEquals(listOf(3, 4, 5, 1, 2), giorniDaOggi(3))
    }

    @Test
    fun giorniDaOggiDaVenerdiRuotaCorrettamente() {
        assertEquals(listOf(5, 1, 2, 3, 4), giorniDaOggi(5))
    }

    @Test
    fun giorniDaOggiDaDomenicaParteDaLunedi() {
        assertEquals(listOf(1, 2, 3, 4, 5), giorniDaOggi(0))
    }

    @Test
    fun giorniDaOggiDaSabatoParteDaLunedi() {
        assertEquals(listOf(1, 2, 3, 4, 5), giorniDaOggi(6))
    }

    @Test
    fun giorniDaOggiRestituisceSempreCinqueElementi() {
        for (d in 0..6) {
            assertEquals("Fallito con todayDow=$d", 5, giorniDaOggi(d).size)
        }
    }
}