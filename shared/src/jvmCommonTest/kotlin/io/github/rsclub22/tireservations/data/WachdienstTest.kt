package io.github.rsclub22.tireservations.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Die Entscheidung, ob gemeldet wird. Ohne Netz und ohne Betriebssystem - genau
 * deshalb ist sie aus [Wachdienst.nachsehen] herausgezogen.
 */
class WachdienstTest {

    private fun reservierung(id: Long, name: String, gaeste: Int) = InternReservierung(
        id = id,
        datum = LocalDate.of(2027, 5, 1),
        zeit = LocalTime.of(19, 0),
        dauer = 60,
        gaeste = gaeste,
        name = name,
        telefon = "0170 1",
        kommentar = "",
        statusId = 8,
        status = "Ausstehend",
        tische = emptyList(),
        istVermerk = false,
    )

    @Test
    fun `erster Lauf meldet nichts, setzt aber den Merker`() {
        val offen = OffeneReservierungen(
            seit = 0,
            hoechsteId = 178,
            anzahl = 3,
            offenGesamt = 3,
            reservierungen = listOf(
                reservierung(150, "Alt", 2),
                reservierung(160, "Auch alt", 4),
                reservierung(178, "Ebenfalls alt", 6),
            ),
        )

        val e = Wachdienst.entscheide(merker = 0, offen = offen)

        // Sonst kuendigt der erste Start jede laengst bekannte an.
        assertFalse(e.melden)
        assertEquals(178, e.neuerMerker)
    }

    @Test
    fun `meldet eine neue mit Angaben`() {
        val offen = OffeneReservierungen(
            seit = 173,
            hoechsteId = 178,
            anzahl = 1,
            offenGesamt = 1,
            reservierungen = listOf(reservierung(178, "Testgast", 4)),
        )

        val e = Wachdienst.entscheide(merker = 173, offen = offen)

        assertTrue(e.melden)
        assertEquals("Neue Reservierung", e.titel)
        assertTrue(e.text.contains("Testgast"))
        assertTrue(e.text.contains("4 Pers."))
        assertTrue(e.text.contains("19:00"))
        assertTrue(e.text.contains("Noch zu bestätigen: 1"))
        assertEquals(178, e.neuerMerker)
    }

    @Test
    fun `zaehlt mehrere und kuerzt die Aufzaehlung`() {
        val offen = OffeneReservierungen(
            seit = 100,
            hoechsteId = 205,
            anzahl = 5,
            offenGesamt = 5,
            reservierungen = (1..5).map { reservierung(200L + it, "Gast $it", it) },
        )

        val e = Wachdienst.entscheide(merker = 100, offen = offen)

        assertEquals("5 neue Reservierungen", e.titel)
        assertTrue(e.text.contains("Gast 1"))
        assertTrue(e.text.contains("Gast 3"))
        // Nur drei stehen da, der Rest wird gezaehlt - eine Meldung mit zwanzig
        // Zeilen liest niemand.
        assertFalse(e.text.contains("Gast 4"))
        assertTrue(e.text.contains("und 2 weitere"))
    }

    @Test
    fun `der Merker rueckt auch vor, wenn nichts Neues da ist`() {
        val offen = OffeneReservierungen(
            seit = 173,
            // Es kamen bestaetigte Reservierungen dazu, aber keine unbestaetigte.
            hoechsteId = 190,
            anzahl = 0,
            offenGesamt = 2,
            reservierungen = emptyList(),
        )

        val e = Wachdienst.entscheide(merker = 173, offen = offen)

        assertFalse(e.melden)
        // Bliebe der Merker stehen, kaeme beim naechsten Blick dieselbe Meldung.
        assertEquals(190, e.neuerMerker)
    }
}
