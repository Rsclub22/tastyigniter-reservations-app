package io.github.rsclub22.tireservations.data

import java.time.Duration
import java.time.LocalTime

/** Validation and time calculations for reservations, shared by all clients. */
object ReservationRules {

    private const val MINUTES_PER_DAY = 24 * 60

    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /** Mirrors the validation rules of the TastyIgniter API (`ReservationRequest`). */
    fun validate(d: ReservationDraft): Map<String, String> = buildMap {
        if (d.locationId == null) put("location_id", "Bitte einen Standort wählen.")
        if (d.guestNum < 1) put("guest_num", "Mindestens 1 Gast.")
        if (d.firstName.isBlank()) put("first_name", "Vorname fehlt.")
        if (d.firstName.trim().length > 48) put("first_name", "Maximal 48 Zeichen.")
        if (d.lastName.isBlank()) put("last_name", "Nachname fehlt.")
        if (d.lastName.trim().length > 48) put("last_name", "Maximal 48 Zeichen.")
        // E-mail is optional (not every installation requires it), but must be valid if given.
        if (d.email.isNotBlank() && !EMAIL.matches(d.email.trim())) put("email", "Keine gültige E-Mail-Adresse.")
        if (d.email.trim().length > 96) put("email", "Maximal 96 Zeichen.")
        if (d.telephone.isBlank()) put("telephone", "Telefonnummer fehlt.")
        if (d.comment.length > 520) put("comment", "Maximal 520 Zeichen.")
    }

    /** Minutes from [start] to [end]; an end at or before the start is taken as the next day. */
    fun durationBetween(start: LocalTime, end: LocalTime): Int {
        val minutes = Duration.between(start, end).toMinutes().toInt()
        return if (minutes <= 0) minutes + MINUTES_PER_DAY else minutes
    }

    /**
     * Duration for a newly picked end time. The picker only knows a clock time:
     * - stays under 24 hours end at the next occurrence of that time after the start;
     * - longer stays keep the day they currently end on, so only the clock time changes.
     */
    fun durationForEnd(start: LocalTime, end: LocalTime, currentDuration: Int?): Int {
        if (currentDuration == null || currentDuration < MINUTES_PER_DAY) return durationBetween(start, end)
        val endDay = daysAfterStart(start, currentDuration)
        val minutes = endDay * MINUTES_PER_DAY + (end.toSecondOfDay() - start.toSecondOfDay()) / 60
        return if (minutes <= 0) minutes + MINUTES_PER_DAY else minutes
    }

    /** Number of days the stay ends after the start day (0 = same day). */
    fun daysAfterStart(start: LocalTime, durationMinutes: Int): Int =
        (start.toSecondOfDay() / 60 + durationMinutes) / MINUTES_PER_DAY
}
