package io.github.rsclub22.tireservations.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class LokalBackendTest {

    @get:Rule
    val ordner = TemporaryFolder()

    private val zone = ZoneId.of("Europe/Berlin")
    private val datei: File get() = ordner.root.resolve("lokal.json")

    private fun backend() = LokalBackend(datei, zone) { Instant.parse("2026-11-18T09:30:00Z") }

    private val tag = LocalDate.of(2026, 11, 27)

    private fun entwurf(name: String = "Müller", zeit: LocalTime = LocalTime.of(12, 0)) = ReservationDraft(
        locationId = LokalBackend.STANDORT.id,
        date = tag,
        time = zeit,
        guestNum = 4,
        firstName = "Anna",
        lastName = name,
        telephone = "089 555",
    )

    @Test
    fun `angelegte Reservierung uebersteht einen Neustart`() = runTest {
        val id = backend().createReservation(entwurf())

        // Frisch aus der Datei, nicht aus dem Speicher des ersten Objekts.
        val r = backend().reservation(id).reservation
        assertEquals(1L, id)
        assertEquals("Anna Müller", r.customerName)
        assertEquals(tag, r.date)
        assertEquals(LocalTime.of(12, 0), r.time)
        assertEquals("Pending", r.statusName)
        assertEquals(Instant.parse("2026-11-27T11:00:00Z"), r.beginn)
        assertEquals("2026-11-18T10:30", r.createdAt)
    }

    @Test
    fun `Nummern laufen auch nach dem Loeschen weiter`() = runTest {
        val b = backend()
        val eins = b.createReservation(entwurf())
        b.deleteReservation(eins)
        val zwei = b.createReservation(entwurf())

        // Eine wiederverwendete Nummer wuerde auf einem alten Zettel auf die falsche Reservierung zeigen.
        assertEquals(2L, zwei)
    }

    @Test
    fun `Tagesliste filtert nach Tag, Status und Suche und sortiert nach Uhrzeit`() = runTest {
        val b = backend()
        b.createReservation(entwurf("Spät", LocalTime.of(13, 30)))
        b.createReservation(entwurf("Früh", LocalTime.of(11, 30)).copy(statusId = 6))
        b.createReservation(entwurf("Anderer Tag").copy(date = tag.plusDays(1)))

        val amTag = b.reservations(ReservationQuery(date = tag)).items
        assertEquals(listOf("Früh", "Spät"), amTag.map { it.lastName })

        val bestaetigt = b.reservations(ReservationQuery(statusId = 6)).items
        assertEquals(listOf("Früh"), bestaetigt.map { it.lastName })

        assertEquals(listOf("Anderer Tag"), b.reservations(ReservationQuery(search = "anderer")).items.map { it.lastName })
        assertEquals(listOf("Früh"), b.reservations(ReservationQuery(search = "2")).items.map { it.lastName })
    }

    @Test
    fun `Aendern laesst Status und Anlagezeit stehen`() = runTest {
        val b = backend()
        val id = b.createReservation(entwurf())
        b.updateStatus(id, 6, "egal", notify = true)

        b.updateReservation(id, entwurf("Meier").copy(statusId = 7, email = ""))

        val r = b.reservation(id).reservation
        assertEquals("Meier", r.lastName)
        assertEquals(6L, r.statusId)
        assertEquals("2026-11-18T10:30", r.createdAt)
    }

    @Test
    fun `unbekannte Nummer meldet sich als 404`() = runTest {
        try {
            backend().reservation(99)
            fail("erwartet: ApiException")
        } catch (e: ApiException) {
            assertEquals(404, e.statusCode)
        }
    }

    @Test
    fun `ohne jeden Namen gibt es einen Feldfehler`() = runTest {
        try {
            backend().createReservation(entwurf().copy(firstName = " ", lastName = ""))
            fail("erwartet: ApiException")
        } catch (e: ApiException) {
            assertEquals(422, e.statusCode)
            assertTrue("last_name" in e.fieldErrors)
        }
        assertFalse(datei.exists())
    }

    @Test
    fun `beschaedigte Datei wird nicht ueberschrieben`() = runTest {
        datei.writeText("{ halb")

        try {
            backend().createReservation(entwurf())
            fail("erwartet: ApiException")
        } catch (e: ApiException) {
            assertTrue(e.message.contains(datei.path))
        }
        assertEquals("{ halb", datei.readText())
    }

    @Test
    fun `offene Reservierungen gibt es lokal nicht`() = runTest {
        val b = backend()
        b.createReservation(entwurf())

        val offen = b.internOffen(0)
        assertEquals(0, offen.anzahl)
        assertEquals(1L, offen.hoechsteId)
    }

    @Test
    fun `unmoeglicher Monat meldet sich als 422`() = runTest {
        try {
            backend().internMonat(2026, 13)
            fail("erwartet: ApiException")
        } catch (e: ApiException) {
            assertEquals(422, e.statusCode)
        }
    }

    @Test
    fun `von Hand angelegter Eintrag ohne Anlagezeit wird gelesen`() = runTest {
        datei.writeText(
            """{"naechsteId": 5, "reservierungen": [
                {"id": 4, "datum": "2026-11-27", "zeit": "12:15", "gaeste": 2, "nachname": "Hand", "statusId": 6}
            ]}""",
        )

        val r = backend().reservation(4).reservation
        assertEquals("Hand", r.lastName)
        assertNull(r.createdAt)
        assertEquals(5L, backend().createReservation(entwurf()))
    }
}
