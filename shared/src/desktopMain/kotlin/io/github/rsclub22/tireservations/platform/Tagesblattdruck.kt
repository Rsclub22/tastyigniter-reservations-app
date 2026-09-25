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

        zeichne(g2, alle[seitenIndex], pf.imageableWidth.toFloat())

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
            val hinweisHoehe = hinweise.sumOf { (umbrechen(it, breite - 2 * RAND).size * ZEILE + 10).toInt() }

            val platzErsteSeite = hoehe - KOPF - hinweisHoehe - TABELLENKOPF
            val platzWeitere = hoehe - KOPF - TABELLENKOPF

            val ersteSeite = maxOf(1, (platzErsteSeite / ZEILE).toInt())
            val weitere = maxOf(1, (platzWeitere / ZEILE).toInt())

            var rest = abschnitt.reservierungen
            var teil = 0

            do {
                val nimm = if (teil == 0) ersteSeite else weitere
                ergebnis += Seite(
                    tag = tag,
                    abschnitt = abschnitt,
                    blattNr = i + 1,
                    blattGesamt = abschnitte.size,
                    fortsetzung = teil,
                    hinweise = if (teil == 0) hinweise else emptyList(),
                    zeilen = rest.take(nimm),
                )
                rest = rest.drop(nimm)
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

    private fun zeichne(g: Graphics2D, seite: Seite, breite: Float) {
        var y = 0f

        // Kopf: links der Tag, rechts Abschnitt, Zahlen und Blattnummer.
        g.color = SCHWARZ
        g.font = FONT_TAG
        g.drawString(seite.tag.datum.format(TAG_LANG), 0f, y + 14f)
        g.font = FONT_KLEIN
        g.color = GRAU
        g.drawString(standort, 0f, y + 28f)

        g.color = SCHWARZ
        g.font = FONT_FETT
        rechts(g, seite.abschnitt.titel, breite, y + 12f)
        g.font = FONT_KLEIN
        g.color = GRAU
        rechts(
            g,
            "${seite.abschnitt.reservierungen.size} Reservierungen · ${seite.abschnitt.gaeste} Gäste",
            breite, y + 25f,
        )
        val blattText = "Blatt ${seite.blattNr} von ${seite.blattGesamt}" +
            if (seite.fortsetzung > 0) " · Seite ${seite.fortsetzung + 1}" else ""
        rechts(g, blattText, breite, y + 37f)

        y += KOPF - 8f
        g.color = BRAUN
        g.stroke = BasicStroke(1.5f)
        g.drawLine(0, y.toInt(), breite.toInt(), y.toInt())
        y += 12f

        // Hinweise
        g.font = FONT_NORMAL
        seite.hinweise.forEach { hinweis ->
            val zeilen = umbrechen(hinweis, breite - 2 * RAND)
            val kastenHoehe = zeilen.size * ZEILE + 8f

            g.color = SAND
            g.fillRect(0, y.toInt(), breite.toInt(), kastenHoehe.toInt())
            g.color = GOLD
            g.fillRect(0, y.toInt(), 3, kastenHoehe.toInt())

            g.color = SCHWARZ
            zeilen.forEachIndexed { i, text ->
                g.drawString(text, RAND, y + 11f + i * ZEILE)
            }
            y += kastenHoehe + 6f
        }

        if (seite.zeilen.isEmpty()) {
            g.color = GRAU
            g.font = FONT_NORMAL
            g.drawString("Keine Reservierungen.", 0f, y + 12f)
            fuss(g, breite)
            return
        }

        // Tabellenkopf
        val spalten = spalten(breite)
        g.font = FONT_KLEIN
        g.color = GRAU
        UEBERSCHRIFTEN.forEachIndexed { i, text ->
            if (i == 3) rechts(g, text, spalten[i] + SPALTENBREITEN[i] * breite - ABSTAND, y + 9f)
            else g.drawString(text, spalten[i], y + 9f)
        }
        y += 13f
        g.color = LINIE
        g.stroke = BasicStroke(0.6f)
        g.drawLine(0, y.toInt(), breite.toInt(), y.toInt())

        // Zeilen
        seite.zeilen.forEach { r ->
            y += ZEILE

            // Kaestchen zum Abhaken, wenn die Gesellschaft da ist.
            g.color = GRAU
            g.stroke = BasicStroke(0.6f)
            g.drawRect(spalten[0].toInt(), (y - 9f).toInt(), 9, 9)

            g.color = SCHWARZ
            g.font = FONT_FETT
            g.drawString(r.zeit?.format(ZEIT) ?: "—", spalten[1], y)

            g.font = FONT_NORMAL
            g.drawString(kuerzen(g, r.name.ifBlank { "—" }, SPALTENBREITEN[2] * breite - 6f), spalten[2], y)
            rechts(g, r.gaeste.toString(), spalten[3] + SPALTENBREITEN[3] * breite - ABSTAND, y)
            g.drawString(kuerzen(g, r.tischeText, SPALTENBREITEN[4] * breite - 6f), spalten[4], y)
            g.drawString(kuerzen(g, r.telefon, SPALTENBREITEN[5] * breite - 6f), spalten[5], y)

            g.color = GRAU
            g.font = FONT_KLEIN
            rechts(g, r.id.toString(), breite, y)

            g.color = LINIE
            g.drawLine(0, (y + 4f).toInt(), breite.toInt(), (y + 4f).toInt())
        }

        fuss(g, breite)
    }

    private fun fuss(g: Graphics2D, breite: Float) {
        g.font = FONT_KLEIN
        g.color = GRAU
        val text = "Gedruckt $gedrucktAm" +
            (blatt.trennzeit?.let { " · Trennung ${it.format(ZEIT)} Uhr" } ?: "")
        g.drawString(text, 0f, (format?.imageableHeight?.toFloat() ?: 700f) - 4f)
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
     * Schaetzung ueber die mittlere Zeichenbreite.
     */
    private fun umbrechen(text: String, breite: Float): List<String> {
        val proZeile = maxOf(20, (breite / ZEICHENBREITE).toInt())
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
        const val ZEILE = 15f
        const val KOPF = 52f
        const val TABELLENKOPF = 16f
        const val RAND = 6f
        const val ZEICHENBREITE = 5.2f

        /** Luft zwischen zwei Spalten, damit "4" und "Tisch 2" nicht aneinanderkleben. */
        const val ABSTAND = 8f

        val UEBERSCHRIFTEN = listOf("DA", "ZEIT", "NAME", "PERS.", "TISCH / RAUM", "TELEFON", "NR.")
        val SPALTENBREITEN = floatArrayOf(0.045f, 0.085f, 0.33f, 0.065f, 0.19f, 0.20f, 0.085f)

        val SCHWARZ = Color(0x2A, 0x1C, 0x16)
        val GRAU = Color(0x7A, 0x6A, 0x62)
        val LINIE = Color(0xE4, 0xDA, 0xD3)
        val BRAUN = Color(0x60, 0x21, 0x0F)
        val GOLD = Color(0xB8, 0x86, 0x0B)
        val SAND = Color(0xFA, 0xF5, 0xEC)

        val FONT_TAG = Font(Font.SANS_SERIF, Font.BOLD, 14)
        val FONT_FETT = Font(Font.SANS_SERIF, Font.BOLD, 10)
        val FONT_NORMAL = Font(Font.SANS_SERIF, Font.PLAIN, 10)
        val FONT_KLEIN = Font(Font.SANS_SERIF, Font.PLAIN, 8)
    }
}
