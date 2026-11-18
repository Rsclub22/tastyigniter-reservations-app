package io.github.rsclub22.tireservations.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * Die Freitext-Auswertung der Sperrvermerke. Die Texte sind die echten aus dem
 * Betrieb (Weihnachten, Maerchenabend) und die Faelle aus den Tests der
 * Server-Erweiterung - beide Seiten muessen denselben Text gleich lesen.
 */
class LokalVermerkeTest {

    private val mittag = LocalTime.of(11, 30) to LocalTime.of(15, 0)

    private fun v(text: String, zeit: String = "10:00", dauer: Int = 360) =
        LokalVermerke.Vermerk(1, LocalTime.parse(zeit), dauer, text)

    private fun t(text: String) = LocalTime.parse(text)

    @Test
    fun `Weihnachten - zwei Gaenge und eine Hoechstzahl fuer beide`() {
        val vermerk = listOf(v("WEIHNACHTEN: 2 Gänge: 11 Uhr und 13 Uhr. MAX 120 PAX."))

        assertEquals(listOf(t("11:00"), t("13:00")), LokalVermerke.zeiten(vermerk))
        assertEquals(120, LokalVermerke.maxPax(vermerk))
        // Die Zahl steht hinter beiden Zeiten, also gilt sie fuer beide und nicht nur fuer 13 Uhr.
        assertEquals(emptyMap<LocalTime, Int>(), LokalVermerke.maxPaxJeZeit(vermerk))
        assertTrue(LokalVermerke.ganztags(vermerk.first(), mittag))
    }

    @Test
    fun `je Gang eine eigene Zahl`() {
        val vermerk = listOf(v("11 Uhr max 60 PAX, 13 Uhr max 80 PAX"))

        assertEquals(mapOf(t("11:00") to 60, t("13:00") to 80), LokalVermerke.maxPaxJeZeit(vermerk))
        assertEquals(60, LokalVermerke.maxPax(vermerk))
    }

    @Test
    fun `Maerchenabend neben dem Mittag nimmt den Tag nicht ein`() {
        val abend = v("Märchenabend 17 Uhr, max 10 PAX", zeit = "16:00", dauer = 240)

        assertFalse(LokalVermerke.ganztags(abend, mittag))
        assertEquals(listOf(t("17:00")), LokalVermerke.eventzeiten(listOf(abend)))
        assertEquals(listOf(abend), LokalVermerke.fuerZeit(listOf(abend), mittag, t("17:00")))
        assertEquals(emptyList<LokalVermerke.Vermerk>(), LokalVermerke.fuerZeit(listOf(abend), mittag, t("12:00")))
    }

    @Test
    fun `ganztaegig im Text gewinnt gegen die Uhr`() {
        assertTrue(LokalVermerke.ganztags(v("Betriebsfeier, ganztägig", zeit = "18:00", dauer = 60), mittag))
        assertTrue(LokalVermerke.ganztags(v("GANZTAGIG", zeit = "18:00", dauer = 60), mittag))
    }

    @Test
    fun `Eventzeit ausserhalb des Fensters zaehlt nicht`() {
        val abend = v("Einlass 15:30, Beginn 17 Uhr", zeit = "16:00", dauer = 240)

        assertEquals(listOf(t("17:00")), LokalVermerke.eventzeiten(listOf(abend)))
    }

    @Test
    fun `keine Personenzahl aus beilaeufiger Prosa`() {
        assertNull(LokalVermerke.maxPax(listOf(v("Tisch 5 Personen am Fenster"))))
        assertNull(LokalVermerke.maxPax(listOf(v("Buchung Nummer 1611 Uhr"))))
        assertEquals(emptyList<LocalTime>(), LokalVermerke.zeiten(listOf(v("Buchung Nummer 1611 Uhr"))))
    }

    @Test
    fun `Gaestehinweis steuert nichts`() {
        val vermerk = listOf(v("Feier. GÄSTEHINWEIS: Ab 18 Uhr, max 5 PAX"))

        assertEquals(emptyList<LocalTime>(), LokalVermerke.zeiten(vermerk))
        assertNull(LokalVermerke.maxPax(vermerk))
    }
}
