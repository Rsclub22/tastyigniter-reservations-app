package io.github.rsclub22.tireservations.data

import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Das Tagesblatt als druckfertiges HTML.
 *
 * Warum HTML und nicht direkt auf den Drucker: beide Plattformen koennen HTML
 * drucken - der Desktop ueber den Browser, Android ueber PrintManager und WebView -
 * und die Seitenumbrueche, Kopfzeilen und Spaltenbreiten lassen sich damit an einer
 * Stelle festlegen statt zweimal in Zeichenbefehlen.
 *
 * Aufbau und Wortlaut folgen der Druckansicht unter /intern/druck: dieselben
 * Spalten in derselben Reihenfolge, dieselbe Kaestchenspalte zum Abhaken. Wer das
 * Blatt seit Jahren in der Hand hat, soll nicht umlernen muessen.
 */
object Druckblatt {

    private val TAG_LANG = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)
    private val ZEIT = DateTimeFormatter.ofPattern("HH:mm")

    fun html(blatt: Tagesblatt, standort: String, gedrucktAm: String): String = buildString {
        append("<!doctype html><html lang=\"de\"><head><meta charset=\"utf-8\">")
        append("<title>Tagesblatt</title><style>")
        append(STIL)
        append("</style></head><body>")

        val seiten = blatt.tage.flatMap { tag ->
            // Ein Tag ohne Abschnitte hat trotzdem ein Blatt, wenn ein Sperrvermerk
            // darauf liegt - der Vermerk ist ja gerade die Ansage fuer den Tag.
            tag.blaetter.ifEmpty { listOf(Blatt("Keine Reservierungen", 0, emptyList())) }
                .map { tag to it }
        }

        seiten.forEachIndexed { i, (tag, abschnitt) ->
            append("<section class=\"blatt\">")
            kopf(tag, abschnitt, i + 1, seiten.size, standort)

            if (tag.gesperrt) {
                append("<div class=\"sperrhinweis\"><strong>Für die Online-Buchung gesperrt.</strong>")
                if (tag.grund.isNotBlank()) append(" Grund: ").append(esc(tag.grund))
                append("</div>")
            }

            tag.sperrvermerke.forEach { vermerk ->
                append("<div class=\"vermerk\"><div class=\"vermerk-kopf\">Sperrvermerk")
                vermerk.zeit?.let { append(" · ").append(it.format(ZEIT)).append(" Uhr") }
                append("</div>")
                if (vermerk.kommentar.isNotBlank()) {
                    append("<div class=\"vermerk-text\">").append(esc(vermerk.kommentar)).append("</div>")
                }
                if (tag.paxJeZeit.isNotEmpty()) {
                    append("<div class=\"vermerk-fuss\">")
                    append(tag.paxJeZeit.entries.joinToString(", ") { "${esc(it.key)} Uhr: ${it.value}" })
                    append(" Plätze</div>")
                } else if (tag.maxPax != null) {
                    append("<div class=\"vermerk-fuss\">Höchstens ").append(tag.maxPax).append(" Plätze</div>")
                }
                append("</div>")
            }

            tabelle(abschnitt)
            append("</section>")
        }

        append("<div class=\"fuss\">Gedruckt ").append(esc(gedrucktAm))
        blatt.trennzeit?.let { append(" · Trennung ").append(it.format(ZEIT)).append(" Uhr") }
        append("</div></body></html>")
    }

    private fun StringBuilder.kopf(
        tag: BlattTag,
        abschnitt: Blatt,
        seite: Int,
        seiten: Int,
        standort: String,
    ) {
        append("<div class=\"kopf\"><div>")
        append("<div class=\"tag\">").append(esc(tag.datum.format(TAG_LANG))).append("</div>")
        append("<div class=\"ort\">").append(esc(standort)).append("</div>")
        append("</div><div class=\"rechts\">")
        append("<div class=\"abschnitt\">").append(esc(abschnitt.titel)).append("</div>")
        append("<div class=\"zahlen\">")
        append(abschnitt.reservierungen.size).append(" Reservierungen · ")
        append(abschnitt.gaeste).append(" Gäste")
        append("</div>")
        append("<div class=\"seite\">Blatt ").append(seite).append(" von ").append(seiten).append("</div>")
        append("</div></div>")
    }

    private fun StringBuilder.tabelle(abschnitt: Blatt) {
        if (abschnitt.reservierungen.isEmpty()) {
            append("<p class=\"leer\">Keine Reservierungen.</p>")
            return
        }

        append("<table><thead><tr>")
        append("<th class=\"s-haken\">Da</th><th class=\"s-zeit\">Zeit</th><th>Name</th>")
        append("<th class=\"s-pers\">Pers.</th><th class=\"s-tisch\">Tisch / Raum</th>")
        append("<th class=\"s-tel\">Telefon</th><th class=\"s-nr\">Nr.</th>")
        append("</tr></thead><tbody>")

        abschnitt.reservierungen.forEach { r ->
            append("<tr>")
            // Kaestchen zum Abhaken, wenn die Gesellschaft da ist.
            append("<td class=\"s-haken\"><span class=\"kasten\"></span></td>")
            append("<td class=\"s-zeit\">").append(r.zeit?.format(ZEIT) ?: "—").append("</td>")
            append("<td><span class=\"name\">").append(esc(r.name.ifBlank { "—" })).append("</span>")
            if (r.kommentar.isNotBlank()) {
                append("<div class=\"notiz\">").append(esc(r.kommentar)).append("</div>")
            }
            append("</td>")
            append("<td class=\"s-pers\">").append(r.gaeste).append("</td>")
            append("<td class=\"s-tisch\">").append(esc(r.tischeText)).append("</td>")
            append("<td class=\"s-tel\">").append(esc(r.telefon)).append("</td>")
            append("<td class=\"s-nr\">").append(r.id).append("</td>")
            append("</tr>")
        }

        append("</tbody></table>")
    }

    /** Nur die vier Zeichen, die in Text und Attributen wirklich stoeren. */
    private fun esc(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private val STIL = """
        @page { size: A4; margin: 12mm; }
        * { box-sizing: border-box; }
        body { font: 11pt/1.35 "DejaVu Sans", Arial, sans-serif; color: #2A1C16; margin: 0; }
        .blatt { page-break-after: always; }
        .blatt:last-of-type { page-break-after: auto; }
        .kopf { display: flex; justify-content: space-between; align-items: flex-start;
                border-bottom: 2px solid #60210F; padding-bottom: 6px; margin-bottom: 10px; }
        .tag { font-size: 15pt; font-weight: 700; }
        .ort { color: #7A6A62; font-size: 9pt; }
        .rechts { text-align: right; }
        .abschnitt { font-weight: 700; }
        .zahlen, .seite { color: #7A6A62; font-size: 9pt; }
        .sperrhinweis { background: #FBEAE7; border-left: 4px solid #A8321F;
                        padding: 6px 9px; margin-bottom: 8px; }
        .vermerk { border: 1px solid #E4DAD3; border-left: 4px solid #B8860B;
                   padding: 6px 9px; margin-bottom: 8px; }
        .vermerk-kopf { font-weight: 700; font-size: 9pt; text-transform: uppercase;
                        letter-spacing: .04em; color: #B8860B; }
        .vermerk-text { margin-top: 3px; }
        .vermerk-fuss { margin-top: 3px; color: #7A6A62; font-size: 9pt; }
        table { width: 100%; border-collapse: collapse; }
        th, td { border-bottom: 1px solid #E4DAD3; padding: 5px 4px; text-align: left;
                 vertical-align: top; }
        th { font-size: 9pt; text-transform: uppercase; letter-spacing: .04em; color: #7A6A62; }
        .s-haken { width: 26px; }
        .s-zeit { width: 52px; font-variant-numeric: tabular-nums; font-weight: 700; }
        .s-pers { width: 46px; text-align: right; font-variant-numeric: tabular-nums; }
        .s-tisch { width: 26%; }
        .s-tel { width: 22%; font-variant-numeric: tabular-nums; }
        .s-nr { width: 46px; text-align: right; color: #7A6A62; }
        .kasten { display: inline-block; width: 13px; height: 13px; border: 1px solid #7A6A62; }
        .name { font-weight: 600; }
        .notiz { font-size: 9pt; color: #7A6A62; }
        .leer { color: #7A6A62; font-style: italic; }
        .fuss { color: #7A6A62; font-size: 8pt; margin-top: 10px; }
        @media print { .fuss { position: fixed; bottom: 0; } }
    """.trimIndent()
}
