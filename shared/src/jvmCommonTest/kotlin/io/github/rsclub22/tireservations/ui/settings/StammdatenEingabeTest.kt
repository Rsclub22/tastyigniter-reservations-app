package io.github.rsclub22.tireservations.ui.settings

import io.github.rsclub22.tireservations.data.LokalRaum
import io.github.rsclub22.tireservations.data.LokalStammdaten
import io.github.rsclub22.tireservations.data.LokalTisch
import io.github.rsclub22.tireservations.ui.settings.StammdatenEingabe.KombiEingabe
import io.github.rsclub22.tireservations.ui.settings.StammdatenEingabe.PlatzEingabe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StammdatenEingabeTest {

    private val stamm = LokalStammdaten(
        tische = listOf(
            LokalTisch(1, "1", 2, 4),
            LokalTisch(2, "2", 2, 6),
            LokalTisch(3, "1 + 2", 5, 10, aus = listOf(1, 2)),
            LokalTisch(4, "Lange Tafel", 6, 12, aus = listOf(1, 2)),
        ),
        raeume = listOf(LokalRaum(5, "Saal", 10, 60)),
    )

    @Test
    fun `Name und Plaetze ergeben sich aus den angetippten Tischen`() {
        val e = StammdatenEingabe(tische = listOf(PlatzEingabe(1, "1", "2", "4"), PlatzEingabe(2, "2", "2", "6")))

        val k = e.nachgezogen(KombiEingabe(9, aus = listOf(1, 2)))

        assertEquals("1 + 2", k.name)
        assertEquals("10", k.max)
    }

    @Test
    fun `von Hand Geschriebenes bleibt stehen`() {
        val e = StammdatenEingabe(tische = listOf(PlatzEingabe(1, "1", "2", "4"), PlatzEingabe(2, "2", "2", "6")))

        val k = e.nachgezogen(KombiEingabe(9, aus = listOf(1, 2), name = "Fenster", nameVonHand = true, max = "8", maxVonHand = true))

        assertEquals("Fenster", k.name)
        assertEquals("8", k.max)
    }

    @Test
    fun `Laden und Speichern aendern nichts`() {
        val e = StammdatenEingabe.aus(stamm)

        assertEquals(2, e.tische.size)
        assertEquals(2, e.kombis.size)
        assertFalse("automatischer Name", e.kombis[0].nameVonHand)
        assertTrue("eigener Name", e.kombis[1].nameVonHand)
        // Zwei gleiche Zusammenstellungen sind ein Fehler; fuer den Rundlauf die zweite weg.
        val ohneDoppel = e.copy(kombis = e.kombis.take(1))
        val (gespeichert, fehler) = ohneDoppel.alsStammdaten()
        assertEquals(emptyList<String>(), fehler)
        assertEquals(stamm.tische.take(3), gespeichert!!.tische)
        assertEquals(stamm.raeume, gespeichert.raeume)
    }

    @Test
    fun `geloeschter Tisch faellt aus der Zusammenstellung`() {
        val e = StammdatenEingabe.aus(stamm)
        val ohneZwei = e.copy(tische = e.tische.filter { it.id != 2L }).kombisAbgeglichen()

        assertEquals(listOf(1L), ohneZwei.kombis[0].aus)
        assertEquals("1", ohneZwei.kombis[0].name)
        val (gespeichert, fehler) = ohneZwei.alsStammdaten()
        assertNull(gespeichert)
        assertTrue(fehler.any { "mindestens zwei Tische" in it })
    }

    @Test
    fun `dieselbe Zusammenstellung zweimal wird gemeldet`() {
        val (_, fehler) = StammdatenEingabe.aus(stamm).alsStammdaten()

        assertTrue(fehler.any { "dieselbe Kombination" in it })
    }
}
