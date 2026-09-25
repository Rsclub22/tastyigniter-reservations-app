package io.github.rsclub22.tireservations.platform

import io.github.rsclub22.tireservations.data.Blatt
import io.github.rsclub22.tireservations.data.BlattTag
import io.github.rsclub22.tireservations.data.InternReservierung
import io.github.rsclub22.tireservations.data.Tagesblatt
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Das Tagesblatt auf Papier, mit Java2D gezeichnet.
 *
 * Gezeichnet statt ueber HTML, weil die JVM keine Anzeige-Maschine hat, die CSS
 * versteht - der Weg ueber den Browser waere der einzige gewesen, der das Blatt so
 * darstellt wie gemeint. So gibt es stattdessen den Druckdialog des Systems mit
 * Druckerauswahl, und es oeffnet sich nichts nebenher.
 *
 * Spalten und Reihenfolge wie in der Druckansicht unter /intern/druck und wie im
 * HTML fuer Android: Da, Zeit, Name, Pers., Tisch/Raum, Telefon, Nr. Wer das Blatt
 * seit Jahren in der Hand hat, soll nicht umlernen muessen - Hochformat deshalb
 * ebenso, auch wenn sieben Spalten darauf eng stehen.
 *
 * Gesetzt wird kraeftig: das Blatt liegt neben dem Telefon und wird im Vorbeigehen
 * gelesen, nicht am Schreibtisch studiert.
 */
internal class Tagesblattdruck(
    private val blatt: Tagesblatt,
    private val standort: String,
    private val gedrucktAm: String,
) : Printable {

    /**
     * Eine Druckseite. Die Hinweise stehen nur auf der ersten Seite eines
     * Abschnitts - auf Seite drei desselben Abschnitts noch einmal der komplette
     * Sperrvermerk waere Papierverschwendung.
     */
    private data class Seite(
        val tag: BlattTag,
        val abschnitt: Blatt,
        val blattNr: Int,
        val blattGesamt: Int,
        val fortsetzung: Int,
        val hinweise: List<String>,
        val zeilen: List<InternReservierung>,
    )

    /** Die Aufteilung haengt am Papierformat, das erst beim Drucken feststeht. */
    private var format: PageFormat? = null
    private var seiten: List<Seite> = emptyList()

    fun seitenzahl(format: PageFormat): Int = aufteilen(format).size

    override fun print(g: Graphics, pf: PageFormat, seitenIndex: Int): Int {
        val alle = aufteilen(pf)
        if (seitenIndex >= alle.size) return Printable.NO_SUCH_PAGE

        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.translate(pf.imageableX, pf.imageableY)

        zeichne(g2, alle[seitenIndex], pf.imageableWidth.toFloat(), pf.imageableHeight.toFloat())

        return Printable.PAGE_EXISTS
    }

    // --- Aufteilung -------------------------------------------------------------------

    private fun aufteilen(pf: PageFormat): List<Seite> {
        // Dasselbe Format kommt bei jeder Seite erneut herein; einmal rechnen genuegt.
        if (format?.let { gleich(it, pf) } == true) return seiten

        val breite = pf.imageableWidth.toFloat()
        val hoehe = pf.imageableHeight.toFloat()

        // Die Blaetter sind die Abschnitte aller Tage - so wie im HTML, wo jeder
        // Abschnitt eine eigene Seite bekommt.
        val abschnitte = blatt.tage.flatMap { tag ->
            tag.blaetter.ifEmpty { listOf(Blatt("Keine Reservierungen", 0, emptyList())) }
                .map { tag to it }
        }

        val ergebnis = mutableListOf<Seite>()

        abschnitte.forEachIndexed { i, (tag, abschnitt) ->
            val hinweise = hinweise(tag)
            val hinweisHoehe = hinweise.fold(0f) { summe, hinweis ->
                summe + umbrechen(hinweis, breite - 2 * RAND, ZEICHENBREITE).size * ZEILE + 10f
            }

            var rest = abschnitt.reservierungen
            var teil = 0

            do {
                // Zeilen sind verschieden hoch: unter dem Namen kann eine Notiz
                // haengen. Deshalb wird gefuellt und nicht geteilt.
                val platz = hoehe - KOPF - TABELLENKOPF - FUSS -
                    (if (teil == 0) hinweisHoehe else 0f)

                val genommen = mutableListOf<InternReservierung>()
                var verbraucht = 0f

                for (r in rest) {
                    val h = zeilenhoehe(r, breite)
                    // Mindestens eine Zeile je Seite, sonst laeuft das Fuellen leer,
                    // wenn eine einzelne Notiz laenger ist als eine ganze Seite.
                    if (verbraucht + h > platz && genommen.isNotEmpty()) break
                    genommen += r
                    verbraucht += h
                }

                ergebnis += Seite(
                    tag = tag,
                    abschnitt = abschnitt,
                    blattNr = i + 1,
                    blattGesamt = abschnitte.size,
                    fortsetzung = teil,
                    hinweise = if (teil == 0) hinweise else emptyList(),
                    zeilen = genommen,
                )

                rest = rest.drop(genommen.size)
                teil++
            } while (rest.isNotEmpty())
        }

        format = pf
        seiten = ergebnis
        return ergebnis
    }

    private fun gleich(a: PageFormat, b: PageFormat): Boolean =
        a.imageableWidth == b.imageableWidth &&
            a.imageableHeight == b.imageableHeight &&
            a.orientation == b.orientation

    private fun zeilenhoehe(r: InternReservierung, breite: Float): Float =
        ZEILE + notizzeilen(r, breite).size * NOTIZ_ZEILE

    /**
     * Die Notiz einer Reservierung, umbrochen. Sie steht unter dem Namen und laeuft
     * bis zur Nummernspalte - das ist eine eigene Zeile, dort steht sonst nichts.
     */
    private fun notizzeilen(r: InternReservierung, breite: Float): List<String> {
        if (r.kommentar.isBlank()) return emptyList()

        val bis = SPALTENBREITEN.drop(2).dropLast(1).sum() * breite

        return umbrechen(r.kommentar, bis, ZEICHENBREITE_KLEIN)
    }

    private fun hinweise(tag: BlattTag): List<String> = buildList {
        if (tag.gesperrt) {
            add("Für die Online-Buchung gesperrt." + tag.grund.takeIf { it.isNotBlank() }?.let { " Grund: $it" }.orEmpty())
        }
        tag.sperrvermerke.forEach { vermerk ->
            val zeit = vermerk.zeit?.let { " (${it.format(ZEIT)} Uhr)" }.orEmpty()
            add("Sperrvermerk$zeit: " + vermerk.kommentar.ifBlank { "ohne Text" })
        }
        if (tag.paxJeZeit.isNotEmpty()) {
            add(tag.paxJeZeit.entries.joinToString(", ") { "${it.key} Uhr: ${it.value}" } + " Plätze")
        } else if (tag.maxPax != null) {
            add("Höchstens ${tag.maxPax} Plätze")
        }
    }

    // --- Zeichnen ---------------------------------------------------------------------

    private fun zeichne(g: Graphics2D, seite: Seite, breite: Float, hoehe: Float) {
        var y = 0f

        // Kopf: links der Tag, rechts Abschnitt, Zahlen und Blattnummer.
        g.color = SCHWARZ
        g.font = FONT_TAG
        g.drawString(seite.tag.datum.format(TAG_LANG), 0f, y + 16f)
        g.font = FONT_KLEIN
        g.color = GRAU
        g.drawString(standort, 0f, y + 31f)

        g.color = SCHWARZ
        g.font = FONT_FETT
        rechts(g, seite.abschnitt.titel, breite, y + 14f)
        g.font = FONT_KLEIN
        g.color = GRAU
        rechts(
            g,
            "${seite.abschnitt.reservierungen.size} Reservierungen · ${seite.abschnitt.gaeste} Gäste",
            breite, y + 28f,
        )
        val blattText = "Blatt ${seite.blattNr} von ${seite.blattGesamt}" +
            if (seite.fortsetzung > 0) " · Seite ${seite.fortsetzung + 1}" else ""
        rechts(g, blattText, breite, y + 41f)

        y += KOPF - 8f
        g.color = BRAUN
        g.stroke = BasicStroke(2f)
        g.drawLine(0, y.toInt(), breite.toInt(), y.toInt())
        y += 13f

        // Hinweise
        g.font = FONT_NORMAL
        seite.hinweise.forEach { hinweis ->
            val zeilen = umbrechen(hinweis, breite - 2 * RAND, ZEICHENBREITE)
            val kastenHoehe = zeilen.size * ZEILE + 8f

            g.color = SAND
            g.fillRect(0, y.toInt(), breite.toInt(), kastenHoehe.toInt())
            g.color = GOLD
            g.fillRect(0, y.toInt(), 4, kastenHoehe.toInt())

            g.color = SCHWARZ
            zeilen.forEachIndexed { i, text ->
                g.drawString(text, RAND, y + 13f + i * ZEILE)
            }
            y += kastenHoehe + 6f
        }

        if (seite.zeilen.isEmpty()) {
            g.color = GRAU
            g.font = FONT_NORMAL
            g.drawString("Keine Reservierungen.", 0f, y + 13f)
            fuss(g, hoehe)
            return
        }

        // Tabellenkopf
        val spalten = spalten(breite)
        g.font = FONT_KLEIN
        g.color = GRAU
        UEBERSCHRIFTEN.forEachIndexed { i, text ->
            if (i == 3) rechts(g, text, spalten[i] + SPALTENBREITEN[i] * breite - ABSTAND, y + 10f)
            else g.drawString(text, spalten[i], y + 10f)
        }
        y += 15f
        g.color = LINIE
        g.stroke = BasicStroke(0.8f)
        g.drawLine(0, y.toInt(), breite.toInt(), y.toInt())

        // Zeilen
        seite.zeilen.forEach { r ->
            y += ZEILE

            // Kaestchen zum Abhaken, wenn die Gesellschaft da ist.
            g.color = GRAU
            g.stroke = BasicStroke(0.8f)
            g.drawRect(spalten[0].toInt(), (y - 10f).toInt(), 11, 11)

            g.color = SCHWARZ
            g.font = FONT_FETT
            g.drawString(r.zeit?.format(ZEIT) ?: "—", spalten[1], y)

            // Der Name ist das, wonach beim Durchgehen gesucht wird.
            g.font = FONT_NAME
            g.drawString(kuerzen(g, r.name.ifBlank { "—" }, SPALTENBREITEN[2] * breite - 6f), spalten[2], y)

            g.font = FONT_NORMAL
            rechts(g, r.gaeste.toString(), spalten[3] + SPALTENBREITEN[3] * breite - ABSTAND, y)
            g.drawString(kuerzen(g, r.tischeText, SPALTENBREITEN[4] * breite - 6f), spalten[4], y)
            g.drawString(kuerzen(g, r.telefon, SPALTENBREITEN[5] * breite - 6f), spalten[5], y)

            g.color = GRAU
            g.font = FONT_KLEIN
            rechts(g, r.id.toString(), breite, y)

            // Die Notiz unter dem Namen. Dort steht, was am Telefon vereinbart
            // wurde - Kinderstuhl, Kuchen um drei, kommt spaeter. Ohne sie ist
            // das Blatt nur die halbe Auskunft.
            val notiz = notizzeilen(r, breite)
            notiz.forEachIndexed { i, text ->
                g.drawString(text, spalten[2], y + (i + 1) * NOTIZ_ZEILE)
            }
            y += notiz.size * NOTIZ_ZEILE

            g.color = LINIE
            g.drawLine(0, (y + 5f).toInt(), breite.toInt(), (y + 5f).toInt())
        }

        fuss(g, hoehe)
    }

    private fun fuss(g: Graphics2D, hoehe: Float) {
        g.font = FONT_KLEIN
        g.color = GRAU
        val text = "Gedruckt $gedrucktAm" +
            (blatt.trennzeit?.let { " · Trennung ${it.format(ZEIT)} Uhr" } ?: "")
        g.drawString(text, 0f, hoehe - 4f)
    }

    /** Linke Kanten der Spalten, aus den Anteilen der Seitenbreite. */
    private fun spalten(breite: Float): FloatArray {
        val x = FloatArray(SPALTENBREITEN.size)
        var laufend = 0f
        SPALTENBREITEN.forEachIndexed { i, anteil ->
            x[i] = laufend
            laufend += anteil * breite
        }
        return x
    }

    private fun rechts(g: Graphics2D, text: String, rechteKante: Float, y: Float) {
        g.drawString(text, rechteKante - g.fontMetrics.stringWidth(text), y)
    }

    /** Schneidet ab und haengt ein Auslassungszeichen an, statt in die Nachbarspalte zu laufen. */
    private fun kuerzen(g: Graphics2D, text: String, breite: Float): String {
        if (g.fontMetrics.stringWidth(text) <= breite) return text
        var gekuerzt = text
        while (gekuerzt.isNotEmpty() && g.fontMetrics.stringWidth("$gekuerzt…") > breite) {
            gekuerzt = gekuerzt.dropLast(1)
        }
        return "$gekuerzt…"
    }

    /**
     * Umbruch an Wortgrenzen. Ohne FontMetrics gerechnet, weil die Aufteilung
     * feststehen muss, bevor die erste Seite gezeichnet wird - daher die grobe
     * Schaetzung ueber die mittlere Zeichenbreite. Gezeichnet wird spaeter mit
     * demselben Aufruf, dann stimmen Rechnung und Papier ueberein.
     */
    private fun umbrechen(text: String, breite: Float, zeichenbreite: Float): List<String> {
        val proZeile = maxOf(20, (breite / zeichenbreite).toInt())
        val zeilen = mutableListOf<String>()
        var aktuell = StringBuilder()

        text.split(" ").forEach { wort ->
            if (aktuell.isEmpty()) {
                aktuell.append(wort)
            } else if (aktuell.length + 1 + wort.length <= proZeile) {
                aktuell.append(' ').append(wort)
            } else {
                zeilen += aktuell.toString()
                aktuell = StringBuilder(wort)
            }
        }
        if (aktuell.isNotEmpty()) zeilen += aktuell.toString()

        return zeilen.ifEmpty { listOf("") }
    }

    private companion object {
        val TAG_LANG: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)
        val ZEIT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        // Java2D rechnet in Punkten: 72 pro Zoll.
        const val ZEILE = 18f
        const val NOTIZ_ZEILE = 13f
        const val KOPF = 56f
        const val TABELLENKOPF = 18f
        const val FUSS = 16f
        const val RAND = 7f
        const val ZEICHENBREITE = 6.3f
        const val ZEICHENBREITE_KLEIN = 4.9f

        /** Luft zwischen zwei Spalten, damit "4" und "Tisch 2" nicht aneinanderkleben. */
        const val ABSTAND = 8f

        val UEBERSCHRIFTEN = listOf("DA", "ZEIT", "NAME", "PERS.", "TISCH / RAUM", "TELEFON", "NR.")
        val SPALTENBREITEN = floatArrayOf(0.045f, 0.085f, 0.33f, 0.065f, 0.19f, 0.20f, 0.085f)

        val SCHWARZ = Color(0x2A, 0x1C, 0x16)
        val GRAU = Color(0x6B, 0x5B, 0x53)
        val LINIE = Color(0xE4, 0xDA, 0xD3)
        val BRAUN = Color(0x60, 0x21, 0x0F)
        val GOLD = Color(0xB8, 0x86, 0x0B)
        val SAND = Color(0xFA, 0xF5, 0xEC)

        val FONT_TAG = Font(Font.SANS_SERIF, Font.BOLD, 16)
        val FONT_FETT = Font(Font.SANS_SERIF, Font.BOLD, 12)
        val FONT_NAME = Font(Font.SANS_SERIF, Font.BOLD, 12)
        val FONT_NORMAL = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        val FONT_KLEIN = Font(Font.SANS_SERIF, Font.PLAIN, 9)
    }
}
