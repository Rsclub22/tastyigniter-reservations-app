package io.github.rsclub22.tireservations.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Die Telefonannahme-Endpunkte der Server-Erweiterung.
 *
 * Die Antwortkörper sind die echten Formen, die der Server am 25.12.2026 liefert -
 * dem Tag mit dem Sperrvermerk „2 Gänge: 11 Uhr und 13 Uhr … MAX 120 PAX". Genau
 * dieser Fall ist der interessante: dort bedeutet die Belegung etwas anderes als an
 * einem normalen Tag, und das muss durch die Umwandlung hindurch erhalten bleiben.
 */
class InternApiTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun api() = TastyIgniterApi(client, server.url("/api").toString().trimEnd('/'), "1|token")

    @Test
    fun `liest den Tag mit Sperrvermerk`() = runTest {
        server.enqueue(MockResponse().setBody(WEIHNACHTEN))

        val tag = api().internTag(LocalDate.of(2026, 12, 25), gaeste = 4)

        assertEquals(LocalDate.of(2026, 12, 25), tag.datum)
        assertEquals(4, tag.gaeste)
        assertEquals(120, tag.maxPax)
        assertEquals(578, tag.hausgroesse)
        assertEquals(listOf(LocalTime.of(11, 0), LocalTime.of(13, 0)), tag.vermerkZeiten)
        assertEquals(LocalTime.of(15, 0), tag.trennzeit)
        assertEquals(1, tag.vermerke.size)
        assertTrue(tag.vermerke.first().kommentar.contains("MAX 120 PAX"))

        // An einem solchen Tag werden nur die genannten Zeiten angeboten, und die
        // Annahme laeuft ohne Tisch.
        assertEquals(2, tag.belegung.size)
        val elf = tag.belegung.first()
        assertEquals(LocalTime.of(11, 0), elf.zeit)
        assertTrue(elf.ohneTisch)
        assertEquals(35, elf.paxBelegt)
        assertEquals(120, elf.paxMax)
        assertTrue(elf.passt)
        assertEquals("35/120 Plätze", elf.anzeige)

        // Der Sperrvermerk selbst ist keine Gesellschaft und zaehlt nicht mit.
        assertEquals(10, tag.gaesteGesamt)
        assertEquals(2, tag.reservierungen.size)
        assertTrue(tag.reservierungen.last().istVermerk)
        assertEquals("ohne Tisch", tag.reservierungen.first().tischeText)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/intern/tag?datum=2026-12-25&gaeste=4", request.path)
        assertEquals("Bearer 1|token", request.getHeader("Authorization"))
    }

    @Test
    fun `zaehlt an einem normalen Tag Tische statt Plaetze`() = runTest {
        server.enqueue(MockResponse().setBody(NORMALER_TAG))

        val tag = api().internTag(LocalDate.of(2027, 3, 16), gaeste = 4)

        val fenster = tag.belegung.single()
        assertNull(fenster.paxMax)
        assertFalse(fenster.ohneTisch)
        assertEquals("6/8 Tische", fenster.anzeige)
        assertEquals(8, tag.tischeGesamt)
        assertEquals(44, tag.plaetzeGesamt)
        assertEquals(4, tag.raeume.size)
        assertEquals("Scheune", tag.raeume.first().name)
        assertEquals(90, tag.raeume.first().maxPlaetze)
    }

    @Test
    fun `schickt den Raum mit und liest die angelegte Reservierung`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(ANGENOMMEN))

        val angelegt = api().internAnnehmen(
            Annahmeentwurf(
                datum = LocalDate.of(2027, 3, 16),
                zeit = LocalTime.of(18, 0),
                gaeste = 40,
                nachname = "Feuerwehr",
                telefon = "03685 1",
                notiz = "Jahresversammlung",
                raumId = 25,
            ),
        )

        assertEquals(180L, angelegt.id)
        assertEquals(LocalTime.of(18, 0), angelegt.zeit)
        assertEquals("Scheune", angelegt.tischeText)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/intern/reservierung", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"zeit\":\"18:00\""))
        assertTrue(body.contains("\"raum\":25"))
        assertTrue(body.contains("\"notiz\":\"Jahresversammlung\""))
        // E-Mail war leer und wird gar nicht erst mitgeschickt.
        assertFalse(body.contains("email"))
    }

    @Test
    fun `ohne Tisch gewinnt gegen eine Raumauswahl`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(ANGENOMMEN))

        api().internAnnehmen(
            Annahmeentwurf(
                datum = LocalDate.of(2027, 3, 16),
                zeit = LocalTime.of(18, 0),
                gaeste = 4,
                nachname = "Unklar",
                telefon = "0170 1",
                // Beides gesetzt: der Raum darf nicht mitgeschickt werden, sonst
                // haette der Datensatz doch eine Zuordnung.
                raumId = 25,
                ohneTisch = true,
            ),
        )

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"ohne_tisch\":true"))
        assertFalse(body.contains("raum"))
    }

    @Test
    fun `meldet die Hoechstzahl am Feld gaeste`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(422).setBody(
                """
                {"message":"Um 11:00 Uhr sind bereits 35 von 120 Plätzen vergeben – 100 weitere passen nicht mehr. Frei sind noch 85.",
                 "errors":{"gaeste":["Um 11:00 Uhr sind bereits 35 von 120 Plätzen vergeben – 100 weitere passen nicht mehr. Frei sind noch 85."]}}
                """.trimIndent(),
            ),
        )

        try {
            api().internAnnehmen(
                Annahmeentwurf(
                    datum = LocalDate.of(2026, 12, 25),
                    zeit = LocalTime.of(11, 0),
                    gaeste = 100,
                    nachname = "Zuviel",
                    telefon = "0170 1",
                ),
            )
            fail("Die Höchstzahl hätte abgelehnt werden müssen")
        } catch (e: ApiException) {
            assertEquals(422, e.statusCode)
            // Entscheidend: die Meldung haengt am Feld, damit sie im Formular an der
            // Personenzahl erscheint und nicht als allgemeiner Serverfehler.
            assertTrue(e.fieldErrors.containsKey("gaeste"))
            assertTrue(e.fieldErrors.getValue("gaeste").first().contains("Frei sind noch 85"))
        }
    }

    @Test
    fun `liest das Tagesblatt mit Abschnitten`() = runTest {
        server.enqueue(MockResponse().setBody(TAGESBLATT))

        val blatt = api().internTagesblatt(LocalDate.of(2026, 12, 25))

        assertEquals(LocalDate.of(2026, 12, 25), blatt.von)
        assertFalse(blatt.zeitraum)
        assertEquals(LocalTime.of(15, 0), blatt.trennzeit)

        val tag = blatt.tage.single()
        assertEquals(120, tag.maxPax)
        assertEquals(1, tag.sperrvermerke.size)
        val abschnitt = tag.blaetter.single()
        assertEquals("Bis 15:00 Uhr", abschnitt.titel)
        assertEquals(61, abschnitt.gaeste)
        assertEquals(2, abschnitt.reservierungen.size)

        assertEquals("/api/intern/tagesblatt?von=2026-12-25&bis=2026-12-25", server.takeRequest().path)
    }

    @Test
    fun `sperrt einen Tag und gibt ihn wieder frei`() = runTest {
        server.enqueue(MockResponse().setBody("""{"gesperrt":"2027-04-01","alle":{"2027-04-01":"Betriebsferien"}}"""))
        server.enqueue(MockResponse().setBody("""{"freigegeben":"2027-04-01","alle":{}}"""))

        val nachSperren = api().internSperren(LocalDate.of(2027, 4, 1), "Betriebsferien")
        assertEquals(mapOf("2027-04-01" to "Betriebsferien"), nachSperren)

        val nachFreigeben = api().internFreigeben(LocalDate.of(2027, 4, 1))
        assertTrue(nachFreigeben.isEmpty())

        assertEquals("POST", server.takeRequest().method)
        assertEquals("DELETE", server.takeRequest().method)
    }

    private companion object {
        val WEIHNACHTEN = """
            {
              "datum":"2026-12-25","gaeste":4,"raum":null,
              "gesperrt":false,"grund":"","sperren":{"2026-10-03":""},
              "trennzeit":"15:00","tische_gesamt":8,"plaetze_gesamt":44,
              "belegung":[
                {"zeit":"11:00","frei":0,"gesamt":0,"freie_plaetze":85,"passt":true,"groesster":0,
                 "raum":null,"ohne_tisch":true,"pax_max":120,"pax_belegt":35},
                {"zeit":"13:00","frei":0,"gesamt":0,"freie_plaetze":94,"passt":true,"groesster":0,
                 "raum":null,"ohne_tisch":true,"pax_max":120,"pax_belegt":26}
              ],
              "raeume":[{"id":25,"name":"Scheune","min_plaetze":1,"max_plaetze":90}],
              "vermerke":[{"id":161,"zeit":"10:00","gaeste":1234567,
                 "kommentar":"WEIHNACHTEN: 2 Gänge: 11 Uhr und 13 Uhr. MAX 120 PAX."}],
              "vermerk_zeiten":["11:00","13:00"],
              "max_pax":120,"pax_je_zeit":{"11:00":"120","13:00":"120"},"hausgroesse":578,
              "reservierungen":[
                {"id":150,"zeit":"11:00","dauer":60,"gaeste":10,"name":"Bornkessel","telefon":"0170 1",
                 "kommentar":"","status_id":6,"status":"Bestätigt","tische":[],"ist_vermerk":false},
                {"id":161,"zeit":"10:00","dauer":60,"gaeste":1234567,"name":"BLOCKER","telefon":"",
                 "kommentar":"WEIHNACHTEN","status_id":6,"status":"Bestätigt","tische":[],"ist_vermerk":true}
              ]
            }
        """.trimIndent()

        val NORMALER_TAG = """
            {
              "datum":"2027-03-16","gaeste":4,"raum":null,
              "gesperrt":false,"grund":"","sperren":{},
              "trennzeit":"15:00","tische_gesamt":8,"plaetze_gesamt":44,
              "belegung":[
                {"zeit":"18:00","frei":6,"gesamt":8,"freie_plaetze":30,"passt":true,"groesster":10,
                 "raum":null,"ohne_tisch":false,"pax_max":null,"pax_belegt":0}
              ],
              "raeume":[
                {"id":25,"name":"Scheune","min_plaetze":1,"max_plaetze":90},
                {"id":26,"name":"Keller","min_plaetze":1,"max_plaetze":200},
                {"id":27,"name":"Saal","min_plaetze":1,"max_plaetze":200},
                {"id":28,"name":"Gaststube","min_plaetze":0,"max_plaetze":44}
              ],
              "vermerke":[],"vermerk_zeiten":[],
              "max_pax":null,"pax_je_zeit":{},"hausgroesse":578,
              "reservierungen":[]
            }
        """.trimIndent()

        val ANGENOMMEN = """
            {"reservierung":{"id":180,"zeit":"18:00","dauer":60,"gaeste":40,"name":"Feuerwehr",
             "telefon":"03685 1","kommentar":"Jahresversammlung","status_id":6,"status":"Bestätigt",
             "tische":[{"id":25,"name":"Scheune"}],"ist_vermerk":false}}
        """.trimIndent()

        val TAGESBLATT = """
            {
              "von":"2026-12-25","bis":"2026-12-25","zeitraum":false,"trennzeit":"15:00",
              "tage":[{
                "datum":"2026-12-25","gesperrt":false,"grund":"",
                "max_pax":120,"pax_je_zeit":{"11:00":"120"},
                "sperrvermerke":[{"id":161,"zeit":"10:00","kommentar":"WEIHNACHTEN: MAX 120 PAX."}],
                "blaetter":[{"titel":"Bis 15:00 Uhr","gaeste":61,"reservierungen":[
                  {"id":150,"zeit":"11:00","dauer":60,"gaeste":10,"name":"Bornkessel","telefon":"0170 1",
                   "kommentar":"","status_id":6,"status":"Bestätigt","tische":[],"ist_vermerk":false},
                  {"id":151,"zeit":"11:00","dauer":60,"gaeste":1,"name":"Fredi","telefon":"",
                   "kommentar":"","status_id":6,"status":"Bestätigt","tische":[],"ist_vermerk":false}
                ]}]
              }]
            }
        """.trimIndent()
    }
}
