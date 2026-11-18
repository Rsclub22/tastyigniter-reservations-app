package io.github.rsclub22.tireservations.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Tagesblatt und Monatsuebersicht im lokalen Betrieb, wie `DailySheet` und `month()` des Servers. */
class LokalBlattTest {

    @get:Rule
    val ordner = TemporaryFolder()

    private val tag = LocalDate.of(2026, 11, 25)

    private val stamm = LokalStammdaten(
        tische = listOf(LokalTisch(1, "1", 1, 6), LokalTisch(2, "2", 1, 6)),
        trennzeit = "13:00",
    )

    private suspend fun backend() =
        LokalBackend(ordner.root.resolve("lokal.json"), ZoneId.of("Europe/Berlin")) { Instant.parse("2026-11-24T09:00:00Z") }
            .also { it.speichereStammdaten(stamm) }

    private suspend fun LokalBackend.eintrag(
        name: String,
        zeit: String,
        gaeste: Int = 2,
        datum: LocalDate = tag,
        kommentar: String = "",
        status: Long? = null,
    ): Long = createReservation(
        ReservationDraft(
            locationId = 1, date = datum, time = LocalTime.parse(zeit), guestNum = gaeste,
            lastName = name, comment = kommentar, statusId = status,
        ),
    )

    @Test
    fun `Tag in zwei Blaetter geteilt, Vermerk als Banner, Storniertes fehlt`() = runTest {
        val b = backend()
        b.eintrag("Früh", "12:00", gaeste = 4)
        b.eintrag("Spät", "13:00", gaeste = 3)
        b.eintrag("Abgesagt", "12:30", status = 7)
        b.eintrag("Sperrvermerk", "10:00", gaeste = 999, kommentar = "WEIHNACHTEN: MAX 120 PAX.")

        val blatt = b.internTagesblatt(tag, tag, null)

        assertFalse(blatt.zeitraum)
        assertEquals(LocalTime.of(13, 0), blatt.trennzeit)
        val t = blatt.tage.single()
        assertEquals(listOf("Bis 13:00 Uhr", "Ab 13:00 Uhr"), t.blaetter.map { it.titel })
        assertEquals(listOf(4, 3), t.blaetter.map { it.gaeste })
        assertEquals(listOf("Früh"), t.blaetter[0].reservierungen.map { it.name })
        assertEquals("WEIHNACHTEN: MAX 120 PAX.", t.sperrvermerke.single().kommentar)
        assertEquals(120, t.maxPax)
    }

    @Test
    fun `aus heisst ein Blatt, Unlesbares die Vorgabe 15 Uhr`() = runTest {
        val b = backend()
        b.eintrag("A", "12:00")
        b.eintrag("B", "16:00")

        assertEquals(listOf("Ganzer Tag"), b.internTagesblatt(tag, tag, "aus").tage.single().blaetter.map { it.titel })
        val vorgabe = b.internTagesblatt(tag, tag, "quatsch")
        assertEquals(LocalTime.of(15, 0), vorgabe.trennzeit)
        assertEquals(listOf("Bis 15:00 Uhr", "Ab 15:00 Uhr"), vorgabe.tage.single().blaetter.map { it.titel })
    }

    @Test
    fun `Zeitraum laesst leere Tage weg, verdreht und zu lang wird verstanden`() = runTest {
        val b = backend()
        b.eintrag("A", "12:00")
        b.eintrag("B", "12:00", datum = tag.plusDays(2))

        val zeitraum = b.internTagesblatt(tag.plusDays(3), tag, null)
        assertTrue(zeitraum.zeitraum)
        assertEquals(tag, zeitraum.von)
        assertEquals(listOf(tag, tag.plusDays(2)), zeitraum.tage.map { it.datum })

        val lang = b.internTagesblatt(tag, tag.plusYears(2), null)
        assertEquals(tag.plusDays(91), lang.bis)
    }

    @Test
    fun `ein leerer einzelner Tag kommt trotzdem`() = runTest {
        val t = backend().internTagesblatt(tag, tag, null).tage.single()

        assertTrue(t.blaetter.isEmpty())
    }

    @Test
    fun `Monat mit jedem Tag, Vermerke nicht als Gaeste`() = runTest {
        val b = backend()
        b.eintrag("A", "12:00", gaeste = 4)
        b.eintrag("B", "12:30", gaeste = 6)
        b.eintrag("C", "12:00", gaeste = 3, datum = tag.plusDays(1))
        b.eintrag("Abgesagt", "12:00", gaeste = 20, status = 7)
        b.eintrag("Sperrvermerk", "16:00", gaeste = 999, datum = LocalDate.of(2026, 11, 27), kommentar = "Märchenabend 17 Uhr, max 10 PAX")
        b.internSperren(LocalDate.of(2026, 11, 30), "Betriebsferien")

        val m = b.internMonat(2026, 11)

        assertEquals(30, m.tage.size)
        assertEquals(10, m.hoechstwert)
        val mittwoch = m.tag(tag)!!
        assertEquals(2, mittwoch.reservierungen)
        assertEquals(10, mittwoch.gaeste)
        val maerchen = m.tag(LocalDate.of(2026, 11, 27))!!
        assertTrue(maerchen.vermerk)
        assertEquals(0, maerchen.gaeste)
        assertEquals(10, maerchen.maxPax)
        assertEquals("Märchenabend 17 Uhr, max 10 PAX", maerchen.vermerkText)
        val ferien = m.tag(LocalDate.of(2026, 11, 30))!!
        assertTrue(ferien.gesperrt)
        assertEquals("Betriebsferien", ferien.grund)
        assertTrue(m.tag(LocalDate.of(2026, 11, 1))!!.leer)
        assertNull(mittwoch.maxPax)
    }
}
