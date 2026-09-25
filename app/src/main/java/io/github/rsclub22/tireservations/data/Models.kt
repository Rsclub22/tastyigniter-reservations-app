package io.github.rsclub22.tireservations.data

import java.time.LocalDate
import java.time.LocalTime

data class Reservation(
    val id: Long,
    val locationId: Long?,
    val locationName: String?,
    val guestNum: Int,
    val firstName: String,
    val lastName: String,
    val email: String,
    val telephone: String,
    val comment: String,
    val date: LocalDate?,
    val time: LocalTime?,
    /** Stay time in minutes, `null` means "location default". */
    val duration: Int?,
    val statusId: Long?,
    val statusName: String?,
    val statusColor: String?,
    val tables: List<DiningTable>,
    val tableNames: String?,
    val createdAt: String?,
) {
    val customerName: String get() = "$firstName $lastName".trim()

    fun toDraft() = ReservationDraft(
        locationId = locationId,
        date = date ?: LocalDate.now(),
        time = time ?: LocalTime.of(18, 0),
        guestNum = guestNum,
        duration = duration,
        firstName = firstName,
        lastName = lastName,
        email = email,
        telephone = telephone,
        comment = comment,
        tableIds = tables.map { it.id },
        statusId = statusId,
    )
}

/** Everything the user can enter when creating or editing a reservation. */
data class ReservationDraft(
    val locationId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.of(18, 0),
    val guestNum: Int = 2,
    val duration: Int? = null,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val telephone: String = "",
    val comment: String = "",
    val tableIds: List<Long> = emptyList(),
    val statusId: Long? = null,
)

data class Location(val id: Long, val name: String)

data class DiningTable(
    val id: Long,
    val name: String,
    val minCapacity: Int?,
    val maxCapacity: Int?,
    val enabled: Boolean = true,
)

data class ReservationStatus(val id: Long, val name: String, val color: String?) {
    companion object {
        /** Status records TastyIgniter seeds on a fresh install (used if `/status` is not accessible). */
        val DEFAULTS = listOf(
            ReservationStatus(8, "Pending", "#f0ad4e"),
            ReservationStatus(6, "Confirmed", "#00a65a"),
            ReservationStatus(7, "Canceled", "#dd4b39"),
        )
    }
}

data class ReservationQuery(
    val date: LocalDate? = null,
    val locationId: Long? = null,
    val statusId: Long? = null,
    val search: String? = null,
)

class ApiException(
    val statusCode: Int,
    override val message: String,
    val fieldErrors: Map<String, List<String>> = emptyMap(),
) : Exception(message) {
    val isUnauthorized: Boolean get() = statusCode == 401
}
