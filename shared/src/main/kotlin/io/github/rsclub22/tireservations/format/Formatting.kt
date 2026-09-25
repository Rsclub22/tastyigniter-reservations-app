package io.github.rsclub22.tireservations.format

import io.github.rsclub22.tireservations.data.ReservationStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val GERMAN = Locale.GERMANY
val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", GERMAN)
val LongDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", GERMAN)
val ShortDateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(GERMAN)

fun LocalTime?.display(): String = this?.format(TimeFormat) ?: "--:--"
fun LocalDate?.display(): String = this?.format(ShortDateFormat) ?: "–"

fun relativeDayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Heute"
    today.plusDays(1) -> "Morgen"
    today.minusDays(1) -> "Gestern"
    else -> date.format(DateTimeFormatter.ofPattern("EEE, d. MMM", GERMAN))
}

/** German labels for TastyIgniter's default status names; custom names are shown unchanged. */
fun statusLabel(name: String?): String = when (name?.trim()?.lowercase()) {
    null, "" -> "Ohne Status"
    "pending" -> "Ausstehend"
    "confirmed" -> "Bestätigt"
    "canceled", "cancelled" -> "Storniert"
    "completed" -> "Abgeschlossen"
    "no show", "no-show" -> "Nicht erschienen"
    else -> name
}

fun statusLabel(status: ReservationStatus): String = statusLabel(status.name)

/** Parses `#rgb` / `#rrggbb` into an opaque ARGB value, or `null` if it is not a color. */
fun parseHexArgb(hex: String?): Long? {
    val h = hex?.trim()?.removePrefix("#") ?: return null
    val full = when (h.length) {
        3 -> h.map { "$it$it" }.joinToString("")
        6 -> h
        else -> return null
    }
    return full.toLongOrNull(16)?.let { 0xFF000000 or it }
}
