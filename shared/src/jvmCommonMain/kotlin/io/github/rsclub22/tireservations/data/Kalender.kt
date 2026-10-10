package io.github.rsclub22.tireservations.data

import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Ein Kalendereintrag fuer eine Reservierung, so wie der Gast ihn in seinem
 * Kalender sehen soll. Plattformneutral; wie er in den Kalender kommt, regelt
 * `trageInKalenderEin` in `platform/`.
 */
data class Kalendereintrag(
    val titel: String,
    /** Echte Zeitpunkte, keine Wanduhrzeit: so stimmt die Stunde in jeder Zone. */
    val beginn: Instant,
    val ende: Instant,
    val details: String,
    val ort: String?,
) {
    val beginnMillis: Long get() = beginn.toEpochMilli()
    val endeMillis: Long get() = ende.toEpochMilli()
}

/**
 * Baut den Kalendereintrag aus einer Reservierung.
 *
 * Am Telefon oeffnet die Kalender-App den Eintrag zum Speichern. Auf dem Desktop
 * gibt es keine Kalender-App, dort fuehrt der Vorlagen-Link von Google Kalender
 * in den Browser - ohne Google-Anmeldung und ohne Berechtigung fuer die App.
 */
object Kalender {

    private val STEMPEL_UTC: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    /**
     * Ohne Dauer vom Server gilt diese Annahme. Lieber ein Eintrag mit
     * geschaetztem Ende als gar keiner - ein Kalender verlangt beide Zeitpunkte.
     */
    const val ANGENOMMENE_DAUER_MIN = 120L

    /**
     * Null, wenn Datum oder Uhrzeit fehlen; dann laesst sich kein Zeitraum bilden.
     *
     * @param zone nur fuer alte Server ohne [Reservation.beginn]: dann gilt die
     *   Wanduhrzeit in dieser Zone, und ein Geraet in einer anderen Zone liegt daneben.
     */
    fun eintrag(r: Reservation, zone: ZoneId = ZoneId.systemDefault()): Kalendereintrag? {
        val datum = r.date ?: return null
        val zeit = r.time ?: return null
        val beginn = r.beginn ?: datum.atTime(zeit).atZone(zone).toInstant()

        val titel = buildString {
            append("Tisch für ").append(r.guestNum).append(if (r.guestNum == 1) " Person" else " Personen")
            r.locationName?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
        val details = buildString {
            append("Reservierung Nr. ").append(r.id)
            r.tableNames?.takeIf { it.isNotBlank() }?.let { append("\nTisch: ").append(it) }
            r.comment.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
        }
        val ort = listOfNotNull(r.locationName, r.locationAddress)
            .filter { it.isNotBlank() }.joinToString(", ").ifEmpty { null }

        return Kalendereintrag(
            titel = titel,
            beginn = beginn,
            ende = beginn.plusSeconds(60 * (r.duration?.toLong() ?: ANGENOMMENE_DAUER_MIN)),
            details = details,
            ort = ort,
        )
    }

    fun googleLink(r: Reservation, zone: ZoneId = ZoneId.systemDefault()): String? =
        eintrag(r, zone)?.let(::googleLink)

    /** Der Vorlagen-Link, mit dem Google Kalender im Browser den Eintrag vorbelegt. */
    fun googleLink(e: Kalendereintrag): String {
        // Zeitstempel in UTC mit "Z": Google legt sie in die Kalenderzone des
        // Gastes, und die Stunde stimmt auch, wenn der gerade verreist ist.
        val parameter = listOfNotNull(
            "action" to "TEMPLATE",
            "text" to e.titel,
            "dates" to "${STEMPEL_UTC.format(e.beginn)}/${STEMPEL_UTC.format(e.ende)}",
            "details" to e.details,
            e.ort?.let { "location" to it },
        )
        return "https://calendar.google.com/calendar/render?" +
            parameter.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
    }
}
