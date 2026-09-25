package io.github.rsclub22.tireservations.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Was am Ende auf Papier landet. Reine Zeichenkettenarbeit, also gut pruefbar -
 * und die einzige Stelle, an der ein Fehler erst auffaellt, wenn jemand vor dem
 * Drucker steht.
 */
class DruckblattTest {

    private fun reservierung(
        id: Long,
        zeit: String,
        name: String,
        gaeste: Int,
        tische: List<String> = emptyList(),
        telefon: String = "",
        kommentar: String = "",
    ) = InternReservierung(
        id = id,
        zeit = LocalTime.parse(zeit),
        dauer = 60,
        gaeste = gaeste,
        name = name,
        telefon = telefon,
        kommentar = kommentar,
        statusId = 6,
        status = "Bestätigt",
        tische = tische.mapIndexed { i, n -> DiningTable(i.toLong(), n, null, null) },
        istVermerk = false,
    )

    private fun blatt(tage: List<BlattTag>, trennzeit: LocalTime? = LocalTime.of(15, 0)) = Tagesblatt(
        von = tage.first().datum,
        bis = tage.last().datum,
        zeitraum = tage.size > 1,
        trennzeit = trennzeit,
        tage = tage,
    )

    private fun tag(
        datum: LocalDate,
        blaetter: List<Blatt>,
        gesperrt: Boolean = false,
        grund: String = "",
        sperrvermerke: List<Sperrvermerk> = emptyList(),
        maxPax: Int? = null,
        paxJeZeit: Map<String, Int> = emptyMap(),
    ) = BlattTag(datum, gesperrt, grund, maxPax, paxJeZeit, sperrvermerke, blaetter)

    @Test
    fun `ein Tag mit zwei Abschnitten ergibt zwei Blaetter`() {
        val html = Druckblatt.html(
            blatt(
                listOf(
                    tag(
                        LocalDate.of(2026, 12, 25),
                        listOf(
                            Blatt("Bis 15:00 Uhr", 12, listOf(reservierung(1, "11:00", "Bornkessel", 12))),
                            Blatt("Ab 15:00 Uhr", 4, listOf(reservierung(2, "18:00", "Winter", 4))),
                        ),
                    ),
                ),
            ),
            standort = "Gasthaus Zum braunen Roß",
            gedrucktAm = "25.09.2026",
        )

        assertEquals(2, Regex("<section class=\"blatt\">").findAll(html).count())
        assertTrue(html.contains("Blatt 1 von 2"))
        assertTrue(html.contains("Blatt 2 von 2"))
        assertTrue(html.contains("Freitag, 25. Dezember 2026"))
        assertTrue(html.contains("Gasthaus Zum braunen Roß"))
        // Die Spalten der Druckansicht, in derselben Reihenfolge.
        assertTrue(html.indexOf("Zeit") < html.indexOf("Pers."))
        assertTrue(html.indexOf("Pers.") < html.indexOf("Tisch / Raum"))
        assertTrue(html.contains("Trennung 15:00 Uhr"))
    }

    @Test
    fun `ohne Tisch steht es auch so da`() {
        val html = Druckblatt.html(
            blatt(
                listOf(
                    tag(
                        LocalDate.of(2027, 3, 16),
                        listOf(
                            Blatt(
                                "Ganzer Tag", 6,
                                listOf(
                                    reservierung(1, "18:00", "Mit", 2, tische = listOf("Tisch 4")),
                                    reservierung(2, "18:30", "Ohne", 4),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            standort = "Gasthaus",
            gedrucktAm = "25.09.2026",
        )

        assertTrue(html.contains("Tisch 4"))
        assertTrue(html.contains("ohne Tisch"))
    }

    @Test
    fun `Sperrtag und Sperrvermerk stehen ueber der Tabelle`() {
        val html = Druckblatt.html(
            blatt(
                listOf(
                    tag(
                        LocalDate.of(2026, 12, 25),
                        listOf(Blatt("Bis 15:00 Uhr", 10, listOf(reservierung(1, "11:00", "Gast", 10)))),
                        gesperrt = true,
                        grund = "Nur nach Absprache",
                        sperrvermerke = listOf(
                            Sperrvermerk(161, LocalTime.of(10, 0), 1234567, "WEIHNACHTEN: MAX 120 PAX.", ganztags = true),
                        ),
                        maxPax = 120,
                        paxJeZeit = mapOf("11:00" to 120, "13:00" to 120),
                    ),
                ),
            ),
            standort = "Gasthaus",
            gedrucktAm = "25.09.2026",
        )

        assertTrue(html.contains("Nur nach Absprache"))
        assertTrue(html.contains("WEIHNACHTEN: MAX 120 PAX."))
        assertTrue(html.contains("11:00 Uhr: 120"))
        // Der Hinweis steht vor der Tabelle, sonst liest ihn niemand.
        assertTrue(html.indexOf("WEIHNACHTEN") < html.indexOf("<table>"))
    }

    @Test
    fun `ein Tag ohne Reservierungen bekommt trotzdem ein Blatt`() {
        val html = Druckblatt.html(
            blatt(
                listOf(
                    tag(
                        LocalDate.of(2026, 12, 24),
                        emptyList(),
                        gesperrt = true,
                        grund = "Heiligabend",
                    ),
                ),
            ),
            standort = "Gasthaus",
            gedrucktAm = "25.09.2026",
        )

        assertEquals(1, Regex("<section class=\"blatt\">").findAll(html).count())
        assertTrue(html.contains("Keine Reservierungen"))
        assertTrue(html.contains("Heiligabend"))
    }

    @Test
    fun `Sonderzeichen in Namen werden maskiert`() {
        val html = Druckblatt.html(
            blatt(
                listOf(
                    tag(
                        LocalDate.of(2027, 3, 16),
                        listOf(
                            Blatt(
                                "Ganzer Tag", 2,
                                listOf(
                                    reservierung(
                                        1, "18:00", "Müller & Sohn <GmbH>", 2,
                                        kommentar = "Tisch \"am Fenster\"",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            standort = "Gasthaus",
            gedrucktAm = "25.09.2026",
        )

        assertTrue(html.contains("Müller &amp; Sohn &lt;GmbH&gt;"))
        assertTrue(html.contains("&quot;am Fenster&quot;"))
        // Kein unmaskiertes Tag, das die Tabelle zerreissen wuerde.
        assertFalse(html.contains("<GmbH>"))
    }
}
