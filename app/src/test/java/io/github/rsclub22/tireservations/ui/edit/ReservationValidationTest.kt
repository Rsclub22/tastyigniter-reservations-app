package io.github.rsclub22.tireservations.ui.edit

import io.github.rsclub22.tireservations.data.ReservationDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservationValidationTest {

    private val valid = ReservationDraft(
        locationId = 1, guestNum = 2, firstName = "Max", lastName = "Muster",
        email = "max@example.com", telephone = "0151 123",
    )

    @Test
    fun `valid draft has no errors`() {
        assertTrue(ReservationEditViewModel.validate(valid).isEmpty())
    }

    @Test
    fun `reports missing required fields`() {
        val errors = ReservationEditViewModel.validate(ReservationDraft(guestNum = 0))
        assertEquals(
            setOf("location_id", "guest_num", "first_name", "last_name", "email", "telephone"),
            errors.keys,
        )
    }

    @Test
    fun `rejects invalid email`() {
        assertTrue("email" in ReservationEditViewModel.validate(valid.copy(email = "max@")))
    }
}
