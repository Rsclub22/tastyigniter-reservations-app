package io.github.rsclub22.tireservations.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Der Link, der die Reservierung in den Google Kalender traegt. Reine
 * Zeichenkettenarbeit, deshalb ohne Netz pruefbar.
 */
class KalenderTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    private fun reservierung(
        duration: Int? = 90,
        comment: String = "Fensterplatz",
        locationName: String? = "Zum braunen Ross",
        locationAddress: String? = "Hauptstraße 1, 12345 Musterstadt",
        time: LocalTime? = LocalTime.of(19, 30),
        // 25.09.2026 19:30 in Berlin (Sommerzeit) = 17:30 UTC, wie der Server es liefert.
        beginn: Instant? = Instant.parse("2026-09-25T17:30:00Z"),
    ) = Reservation(
        id = 12,
        locationId = 1,
        locationName = locationName,
        locationAddress = locationAddress,
        guestNum = 4,
        firstName = "Erika",
        lastName = "Mustermann",
        email = "erika@example.com",
        telephone = "+49 30 123456",
        comment = comment,
        date = LocalDate.of(2026, 9, 25),
        time = time,
        beginn = beginn,
        duration = duration,
        statusId = 6,
        statusName = "Confirmed",
        statusColor = null,
        tables = emptyList(),
        tableNames = null,
        createdAt = null,
    )

    private fun parameter(link: String?): Map<String, String> =
        link!!.substringAfter('?').split('&').associate { teil ->
            teil.substringBefore('=') to URLDecoder.decode(teil.substringAfter('='), "UTF-8")
        }

    @Test
    fun `baut den Vorlagen-Link mit Zeitraum in UTC`() {
        val link = Kalender.googleLink(reservierung(), berlin)!!

        assertTrue(link.startsWith("https://calendar.google.com/calendar/render?"))
        val p = parameter(link)
        assertEquals("TEMPLATE", p["action"])
        // Mit Z: Google legt den Zeitpunkt in die Kalenderzone des Gastes.
        assertEquals("20260925T173000Z/20260925T190000Z", p["dates"])
        assertEquals(null, p["ctz"])
        assertEquals("Tisch für 4 Personen · Zum braunen Ross", p["text"])
        assertEquals("Zum braunen Ross, Hauptstraße 1, 12345 Musterstadt", p["location"])
        assertTrue(p["details"]!!.contains("Reservierung Nr. 12"))
        assertTrue(p["details"]!!.contains("Fensterplatz"))
    }

    @Test
    fun `ohne Dauer nimmt der Eintrag zwei Stunden an`() {
        val p = parameter(Kalender.googleLink(reservierung(duration = null), berlin))
        assertEquals("20260925T173000Z/20260925T193000Z", p["dates"])
    }

    @Test
    fun `ohne Standort steht nur der Tisch im Titel`() {
        val p = parameter(Kalender.googleLink(reservierung(locationName = null, locationAddress = null), berlin))
        assertEquals("Tisch für 4 Personen", p["text"])
        assertEquals(null, p["location"])
    }

    @Test
    fun `ohne Uhrzeit gibt es keinen Eintrag`() {
        assertEquals(null, Kalender.eintrag(reservierung(time = null), berlin))
    }

    @Test
    fun `Zeitpunkte haengen nicht an der Zone des Geraets`() {
        // 25.09.2026 19:30 Berlin ist Sommerzeit, also 17:30 UTC.
        val erwartet = 1_790_357_400_000L
        assertEquals(erwartet, Kalender.eintrag(reservierung(), berlin)!!.beginnMillis)
        // Ein Gast in New York bekommt denselben Zeitpunkt, nicht 19:30 Ortszeit.
        val unterwegs = Kalender.eintrag(reservierung(), ZoneId.of("America/New_York"))!!
        assertEquals(erwartet, unterwegs.beginnMillis)
        assertEquals(erwartet + 90 * 60_000L, unterwegs.endeMillis)
    }

    @Test
    fun `ohne Zeitpunkt vom Server gilt die Wanduhrzeit in der Zone des Geraets`() {
        val e = Kalender.eintrag(reservierung(beginn = null), berlin)!!
        assertEquals(1_790_357_400_000L, e.beginnMillis)
    }
}
