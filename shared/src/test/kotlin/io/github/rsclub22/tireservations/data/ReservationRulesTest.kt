package io.github.rsclub22.tireservations.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ReservationRulesTest {

    private val valid = ReservationDraft(
        locationId = 1, guestNum = 2, firstName = "Max", lastName = "Muster",
        email = "max@example.com", telephone = "0151 123",
    )

    @Test
    fun `valid draft has no errors`() {
        assertTrue(ReservationRules.validate(valid).isEmpty())
    }

    @Test
    fun `reports missing required fields`() {
        val errors = ReservationRules.validate(ReservationDraft(guestNum = 0))
        assertEquals(
            setOf("location_id", "guest_num", "first_name", "last_name", "telephone"),
            errors.keys,
        )
    }

    @Test
    fun `duration from start and end`() {
        assertEquals(150, ReservationRules.durationBetween(LocalTime.of(18, 30), LocalTime.of(21, 0)))
        // An end before the start runs past midnight.
        assertEquals(180, ReservationRules.durationBetween(LocalTime.of(22, 0), LocalTime.of(1, 0)))
        assertEquals(24 * 60, ReservationRules.durationBetween(LocalTime.of(19, 0), LocalTime.of(19, 0)))
    }

    @Test
    fun `picking an end keeps whole extra days of long stays`() {
        val start = LocalTime.of(19, 0)
        assertEquals(150, ReservationRules.durationForEnd(start, LocalTime.of(21, 30), null))
        assertEquals(150, ReservationRules.durationForEnd(start, LocalTime.of(21, 30), 90))
        // Exactly 24 h stays 24 h when the same clock time is confirmed again.
        assertEquals(1440, ReservationRules.durationForEnd(start, start, 1440))
        // 48 h must not shrink to 24 h just because the picker shows 19:00.
        assertEquals(2880, ReservationRules.durationForEnd(start, start, 2880))
        assertEquals(1470 + 1440, ReservationRules.durationForEnd(start, LocalTime.of(19, 30), 2900))
        // An earlier clock time stays on the current end day instead of adding a day.
        assertEquals(2820, ReservationRules.durationForEnd(start, LocalTime.of(18, 0), 2900))
        // Short stays still pick the next occurrence of the time after the start.
        assertEquals(240, ReservationRules.durationForEnd(start, LocalTime.of(23, 0), 300))
        assertEquals(360, ReservationRules.durationForEnd(start, LocalTime.of(1, 0), 150))
    }

    @Test
    fun `days after start for end label`() {
        val start = LocalTime.of(19, 0)
        assertEquals(0, ReservationRules.daysAfterStart(start, 120))
        assertEquals(1, ReservationRules.daysAfterStart(start, 300))
        assertEquals(2, ReservationRules.daysAfterStart(start, 2880))
    }

    @Test
    fun `email is optional`() {
        assertTrue(ReservationRules.validate(valid.copy(email = "")).isEmpty())
    }

    @Test
    fun `rejects invalid email`() {
        assertTrue("email" in ReservationRules.validate(valid.copy(email = "max@")))
    }
}
