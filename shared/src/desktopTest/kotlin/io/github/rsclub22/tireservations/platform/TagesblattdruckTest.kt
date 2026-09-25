package io.github.rsclub22.tireservations.platform

import io.github.rsclub22.tireservations.data.Blatt
import io.github.rsclub22.tireservations.data.BlattTag
import io.github.rsclub22.tireservations.data.InternReservierung
import io.github.rsclub22.tireservations.data.Sperrvermerk
import io.github.rsclub22.tireservations.data.Tagesblatt
import java.awt.image.BufferedImage
import java.awt.print.PageFormat
import java.awt.print.Paper
import java.awt.print.Printable
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Das Blatt wird auf dem Desktop selbst gezeichnet, also gibt es hier auch keinen
 * Browser, der einen Rechenfehler beim Umbruch noch gerade zieht: zu viele Zeilen
 * auf einer Seite laufen unten einfach aus dem Papier heraus und fehlen.
 *
 * Gezeichnet wird in ein Bild statt auf einen Drucker - das laeuft ohne Geraet und
 * faellt auf, wenn beim Zeichnen etwas fliegt.
 */
class TagesblattdruckTest {

    private fun reservierung(i: Int) = InternReservierung(
        id = 1000L + i,
        datum = LocalDate.of(2026, 12, 24),
        zeit = LocalTime.of(11, 30),
        dauer = 120,
        gaeste = 4,
        name = "Familie Mustermann $i",
        telefon = "036841 12345$i",
        kommentar = "",
        statusId = 1,
        status = "Bestätigt",
        tische = emptyList(),
        istVermerk = false,
    )

    private fun blatt(anzahl: Int, vermerke: List<Sperrvermerk> = emptyList()) = Tagesblatt(
        von = LocalDate.of(2026, 12, 24),
        bis = LocalDate.of(2026, 12, 24),
        zeitraum = false,
        trennzeit = LocalTime.of(15, 0),
        tage = listOf(
            BlattTag(
                datum = LocalDate.of(2026, 12, 24),
                gesperrt = false,
                grund = "",
                maxPax = 120,
                paxJeZeit = mapOf("11:00" to 35),
                sperrvermerke = vermerke,
                blaetter = listOf(
                    Blatt("Mittag", anzahl * 4, (1..anzahl).map(::reservierung)),
                ),
            ),
        ),
    )

    /** A4 hoch, wie es der Druck vorgibt. */
    private fun a4Hoch() = PageFormat().apply {
        orientation = PageFormat.PORTRAIT
        paper = Paper().apply {
            setSize(595.0, 842.0)
            setImageableArea(40.0, 40.0, 515.0, 762.0)
        }
    }

    private fun zeichneAlle(druck: Tagesblattdruck, format: PageFormat): Int {
        val bild = BufferedImage(595, 842, BufferedImage.TYPE_INT_RGB)
        val g = bild.createGraphics()
        var seiten = 0
        while (druck.print(g, format, seiten) == Printable.PAGE_EXISTS) {
            seiten++
            if (seiten > 50) break
        }
        g.dispose()
        return seiten
    }

    @Test
    fun `wenige Reservierungen passen auf ein Blatt`() {
        val druck = Tagesblattdruck(blatt(8), "Gasthaus Zum braunen Roß", "24.12.2026")
        assertEquals(1, zeichneAlle(druck, a4Hoch()))
    }

    @Test
    fun `lange Liste wird auf mehrere Seiten verteilt`() {
        val format = a4Hoch()
        val druck = Tagesblattdruck(blatt(90), "Gasthaus Zum braunen Roß", "24.12.2026")

        val seiten = druck.seitenzahl(format)
        assertTrue("90 Reservierungen brauchen mehr als eine Seite, waren $seiten", seiten > 1)
        assertEquals(seiten, zeichneAlle(druck, format))
    }

    @Test
    fun `hinter der letzten Seite kommt nichts mehr`() {
        val format = a4Hoch()
        val druck = Tagesblattdruck(blatt(8), "Gasthaus Zum braunen Roß", "24.12.2026")
        val bild = BufferedImage(595, 842, BufferedImage.TYPE_INT_RGB)

        assertEquals(
            Printable.NO_SUCH_PAGE,
            druck.print(bild.createGraphics(), format, druck.seitenzahl(format)),
        )
    }

    @Test
    fun `Sperrvermerk kostet Platz auf der ersten Seite`() {
        val format = a4Hoch()
        val vermerk = Sperrvermerk(
            id = 9000,
            zeit = LocalTime.of(11, 0),
            gaeste = 999,
            kommentar = "2 Gänge: 11 Uhr und 13 Uhr, Anmeldung bis 20.12., MAX 120 PAX",
            ganztags = true,
        )

        val ohne = Tagesblattdruck(blatt(40), "Gasthaus", "24.12.2026").seitenzahl(format)
        val mit = Tagesblattdruck(blatt(40, listOf(vermerk)), "Gasthaus", "24.12.2026").seitenzahl(format)

        assertTrue("Der Vermerk darf keine Seiten einsparen: $mit gegen $ohne", mit >= ohne)
        assertEquals(1, zeichneAlle(Tagesblattdruck(blatt(3, listOf(vermerk)), "Gasthaus", "24.12.2026"), format))
    }

    @Test
    fun `ein Tag ohne Reservierungen ergibt trotzdem ein Blatt`() {
        val leer = Tagesblatt(
            von = LocalDate.of(2026, 12, 24),
            bis = LocalDate.of(2026, 12, 24),
            zeitraum = false,
            trennzeit = null,
            tage = listOf(
                BlattTag(
                    datum = LocalDate.of(2026, 12, 24),
                    gesperrt = true,
                    grund = "Betriebsferien",
                    maxPax = null,
                    paxJeZeit = emptyMap(),
                    sperrvermerke = emptyList(),
                    blaetter = emptyList(),
                ),
            ),
        )

        assertEquals(1, zeichneAlle(Tagesblattdruck(leer, "Gasthaus", "24.12.2026"), a4Hoch()))
    }
}
