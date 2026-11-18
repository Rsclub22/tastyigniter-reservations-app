package io.github.rsclub22.tireservations.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Die Telefonannahme im lokalen Betrieb: Zeitfenster, Tischvergabe, Raeume,
 * Sperrvermerke, Sperrtage. Das Haus ist ein kleines Ross: Di-Sa 11:30-15:00,
 * drei Tische und eine Kombination, ein Saal.
 */
class LokalAnnahmeTest {

    @get:Rule
    val ordner = TemporaryFolder()

    private val zone = ZoneId.of("Europe/Berlin")

    /** Ein Mittwoch. "Jetzt" liegt am Tag davor, damit kein Zeitfenster schon vorbei ist. */
    private val tag = LocalDate.of(2026, 11, 25)
    private var jetzt = Instant.parse("2026-11-24T09:00:00Z")

    private val stamm = LokalStammdaten(
        tische = listOf(
            LokalTisch(1, "1", 2, 4),
            LokalTisch(2, "2", 2, 4),
            LokalTisch(3, "3", 1, 2),
            LokalTisch(4, "1/2", 5, 8, aus = listOf(1, 2)),
        ),
        raeume = listOf(LokalRaum(10, "Saal", 10, 200)),
    )

    private suspend fun backend() = LokalBackend(ordner.root.resolve("lokal.json"), zone) { jetzt }
        .also { it.speichereStammdaten(stamm) }

    private fun anruf(gaeste: Int, zeit: String = "12:00", raum: Long? = null, ohneTisch: Boolean = false, datum: LocalDate = tag) =
        Annahmeentwurf(datum, LocalTime.parse(zeit), gaeste, "Gast", "0170", raumId = raum, ohneTisch = ohneTisch)

    private fun vermerk(text: String, zeit: String = "10:00", dauer: Int = 360, datum: LocalDate = tag) = ReservationDraft(
        locationId = 1, date = datum, time = LocalTime.parse(zeit), guestNum = 999, duration = dauer,
        lastName = "Sperrvermerk", comment = text,
    )

    @Test
    fun `Zeitfenster im Raster von Oeffnung bis vor Schliessung`() = runTest {
        val zeiten = backend().internTag(tag, 2).belegung.map { it.zeit }

        assertEquals(LocalTime.of(11, 30), zeiten.first())
        assertEquals(LocalTime.of(14, 45), zeiten.last())
        assertEquals(14, zeiten.size)
    }

    @Test
    fun `heute ohne die schon vergangenen Zeiten`() = runTest {
        jetzt = Instant.parse("2026-11-25T12:10:00Z") // 13:10 in Berlin

        assertEquals(LocalTime.of(13, 15), backend().internTag(tag, 2).belegung.first().zeit)
    }

    @Test
    fun `geschlossener Wochentag hat keine Zeiten, Gesellschaften auch nicht`() = runTest {
        val montag = LocalDate.of(2026, 11, 23)

        assertTrue(backend().internTag(montag, 2).belegung.isEmpty())
        assertTrue(backend().internTag(montag, 30).belegung.isEmpty())
    }

    @Test
    fun `Gesellschaft bekommt das grosse Fenster`() = runTest {
        val zeiten = backend().internTag(tag, 25).belegung.map { it.zeit }

        assertEquals(LocalTime.of(10, 0), zeiten.first())
        assertEquals(LocalTime.of(21, 45), zeiten.last())
    }

    @Test
    fun `kleinster passender Einzeltisch, die Kombination erst wenn keiner reicht`() = runTest {
        val b = backend()

        assertEquals(listOf("3"), b.internAnnehmen(anruf(2)).tische.map { it.name })
        assertEquals(listOf("1"), b.internAnnehmen(anruf(2)).tische.map { it.name })
        assertEquals(listOf("2"), b.internAnnehmen(anruf(3)).tische.map { it.name })
        // Tische 1 und 2 sind belegt, also auch ihre Kombination.
        assertEquals("ohne Tisch", b.internAnnehmen(anruf(6)).tischeText)
    }

    @Test
    fun `wer die Kombination belegt, belegt die Einzeltische mit`() = runTest {
        val b = backend()
        assertEquals(listOf("1/2"), b.internAnnehmen(anruf(6)).tische.map { it.name })

        val zwoelf = b.internTag(tag, 4).belegung.first { it.zeit == LocalTime.NOON }
        assertEquals(1, zwoelf.frei)
        assertFalse("nur noch Tisch 3 mit 2 Plaetzen", zwoelf.passt)

        // Nach dem Aufenthalt ist alles wieder frei.
        val danach = b.internTag(tag, 4).belegung.first { it.zeit == LocalTime.of(13, 30) }
        assertEquals(3, danach.frei)
        assertTrue(danach.passt)
    }

    @Test
    fun `Raum haengt nicht an den Tischen`() = runTest {
        val b = backend()
        val saal = b.internAnnehmen(anruf(40, raum = 10))
        assertEquals(listOf("Saal"), saal.tische.map { it.name })

        val mitSaal = b.internTag(tag, 40, raumId = 10).belegung
        assertFalse(mitSaal.first { it.zeit == LocalTime.NOON }.passt)
        assertTrue(mitSaal.first { it.zeit == LocalTime.of(13, 30) }.passt)
        // Die Tische sind davon unberuehrt.
        assertEquals(3, b.internTag(tag, 2).belegung.first { it.zeit == LocalTime.NOON }.frei)
    }

    @Test
    fun `ohne Tisch gewinnt gegen den Raum`() = runTest {
        assertEquals("ohne Tisch", backend().internAnnehmen(anruf(4, raum = 10, ohneTisch = true)).tischeText)
    }

    @Test
    fun `Weihnachten - nur die genannten Gaenge, nach Personen gezaehlt`() = runTest {
        val b = backend()
        b.createReservation(vermerk("WEIHNACHTEN: 2 Gänge: 11 Uhr und 13 Uhr. MAX 120 PAX."))

        val leer = b.internTag(tag, 4)
        assertEquals(listOf(LocalTime.of(11, 0), LocalTime.of(13, 0)), leer.belegung.map { it.zeit })
        assertTrue(leer.belegung.all { it.ohneTisch && it.paxMax == 120 && it.passt })
        assertEquals(120, leer.maxPax)
        assertTrue(leer.vermerke.single().ganztags)

        // Angenommen wird ohne Tisch, der Tischplan auf Papier verteilt.
        assertEquals("ohne Tisch", b.internAnnehmen(anruf(100, zeit = "11:00")).tischeText)
        val elf = b.internTag(tag, 30).belegung.first()
        assertEquals(100, elf.paxBelegt)
        assertFalse(elf.passt)
    }

    @Test
    fun `Hoechstzahl wird beim Annehmen am Feld gaeste gemeldet`() = runTest {
        val b = backend()
        b.createReservation(vermerk("WEIHNACHTEN: 2 Gänge: 11 Uhr und 13 Uhr. MAX 120 PAX."))
        b.internAnnehmen(anruf(100, zeit = "11:00"))

        try {
            b.internAnnehmen(anruf(30, zeit = "11:00"))
            fail("erwartet: ApiException")
        } catch (e: ApiException) {
            assertEquals(422, e.statusCode)
            assertEquals(
                "Um 11:00 Uhr sind bereits 100 von 120 Plätzen vergeben – 30 weitere passen nicht mehr. Frei sind noch 20.",
                e.fieldErrors["gaeste"]?.single(),
            )
        }
        // 13 Uhr ist ein eigener Gang mit eigener Zaehlung.
        b.internAnnehmen(anruf(30, zeit = "13:00"))
    }

    @Test
    fun `Maerchenabend - Mittag wie immer, 17 Uhr kommt dazu`() = runTest {
        val b = backend()
        b.createReservation(vermerk("Märchenabend 17 Uhr, max 10 PAX", zeit = "16:00", dauer = 240))

        val belegung = b.internTag(tag, 2).belegung
        val mittag = belegung.first { it.zeit == LocalTime.NOON }
        assertFalse(mittag.ohneTisch)
        assertEquals(3, mittag.frei)
        val abend = belegung.last()
        assertEquals(LocalTime.of(17, 0), abend.zeit)
        assertTrue(abend.ohneTisch)
        assertEquals(10, abend.paxMax)
        assertFalse(b.internTag(tag, 2).vermerke.single().ganztags)

        assertEquals("ohne Tisch", b.internAnnehmen(anruf(4, zeit = "17:00")).tischeText)
    }

    @Test
    fun `Vermerk zaehlt nicht als Gast, steht aber in der Liste`() = runTest {
        val b = backend()
        b.createReservation(vermerk("WEIHNACHTEN: MAX 120 PAX."))
        b.internAnnehmen(anruf(4, zeit = "12:00"))

        val t = b.internTag(tag, 2)
        assertEquals(2, t.reservierungen.size)
        assertEquals(4, t.gaesteGesamt)
        assertTrue(t.reservierungen.first().istVermerk)
        // Tische und Saal: 4 + 4 + 2 + 200.
        assertEquals(210, t.hausgroesse)
    }

    @Test
    fun `gesperrter Tag ist zu, freigegeben wieder offen`() = runTest {
        val b = backend()
        assertEquals(mapOf(tag.toString() to "Betriebsferien"), b.internSperren(tag, " Betriebsferien "))

        val gesperrt = b.internTag(tag, 2)
        assertTrue(gesperrt.gesperrt)
        assertEquals("Betriebsferien", gesperrt.grund)
        assertTrue(gesperrt.belegung.isEmpty())

        assertEquals(emptyMap<String, String>(), b.internFreigeben(tag))
        assertFalse(b.internTag(tag, 2).gesperrt)
    }

    @Test
    fun `vergangene Sperrtage fallen beim naechsten Sperren heraus`() = runTest {
        val b = backend()
        b.internSperren(tag.minusDays(1))
        jetzt = Instant.parse("2026-11-25T09:00:00Z")

        assertEquals(setOf(tag.toString()), b.internSperren(tag).keys)
    }

    @Test
    fun `Formular vergibt den Tisch selbst, Aendern verteilt nicht neu`() = runTest {
        val b = backend()
        val id = b.createReservation(
            ReservationDraft(locationId = 1, date = tag, time = LocalTime.NOON, guestNum = 4, firstName = "A", lastName = "B", telephone = "1"),
        )
        val r = b.reservation(id).reservation
        assertEquals(listOf("1"), r.tables.map { it.name })
        assertEquals("1", r.tableNames)

        b.updateReservation(id, r.toDraft().copy(tableIds = emptyList()))
        assertTrue(b.reservation(id).reservation.tables.isEmpty())
    }

    @Test
    fun `Stammdaten ueberstehen den Neustart, Tische kommen ins Formular`() = runTest {
        backend()
        val neu = LokalBackend(ordner.root.resolve("lokal.json"), zone) { jetzt }

        assertEquals(stamm, neu.stammdaten())
        assertEquals(listOf("1", "1/2", "2", "3"), neu.tables().map { it.name })
    }
}
