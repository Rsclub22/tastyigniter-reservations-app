package io.github.rsclub22.tireservations.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rsclub22.tireservations.data.ApiException
import io.github.rsclub22.tireservations.data.CreateResult
import io.github.rsclub22.tireservations.data.DiningTable
import io.github.rsclub22.tireservations.data.Location
import io.github.rsclub22.tireservations.data.ReservationDraft
import io.github.rsclub22.tireservations.data.ReservationRepository
import io.github.rsclub22.tireservations.data.ReservationStatus
import io.github.rsclub22.tireservations.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

data class EditState(
    val isNew: Boolean,
    val draft: ReservationDraft = ReservationDraft(),
    val locations: List<Location> = emptyList(),
    val tables: List<DiningTable> = emptyList(),
    val statuses: List<ReservationStatus> = emptyList(),
    val durationText: String = "",
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val savedId: Long? = null,
    /** Shown after saving when the reservation was stored but a follow-up step failed. */
    val warning: String? = null,
    val unauthorized: Boolean = false,
)

class ReservationEditViewModel(
    private val repository: ReservationRepository,
    private val settingsStore: SettingsStore,
    private val reservationId: Long?,
    initialDate: LocalDate?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        EditState(isNew = reservationId == null, draft = ReservationDraft(date = initialDate ?: LocalDate.now())),
    )
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val locations = repository.locations()
                val tables = repository.tables()
                val statuses = repository.statuses()
                val draft = if (reservationId != null) {
                    repository.reservation(reservationId).toDraft()
                } else {
                    val defaultLocation = settingsStore.settings.first().defaultLocationId
                    _state.value.draft.copy(
                        locationId = locations.firstOrNull { it.id == defaultLocation }?.id ?: locations.firstOrNull()?.id,
                        // Staff entering a reservation by phone usually confirms it right away.
                        statusId = statuses.firstOrNull { it.name.equals("Confirmed", ignoreCase = true) }?.id,
                    )
                }
                _state.update {
                    it.copy(
                        draft = draft,
                        durationText = draft.duration?.toString().orEmpty(),
                        locations = locations,
                        tables = tables,
                        statuses = statuses,
                        loading = false,
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "Laden fehlgeschlagen",
                        unauthorized = e is ApiException && e.isUnauthorized,
                    )
                }
            }
        }
    }

    fun edit(transform: (ReservationDraft) -> ReservationDraft) =
        _state.update { it.copy(draft = transform(it.draft), fieldErrors = emptyMap(), error = null) }

    fun setDuration(text: String) {
        val digits = text.filter(Char::isDigit).take(4)
        _state.update {
            it.copy(durationText = digits, draft = it.draft.copy(duration = digits.toIntOrNull()?.takeIf { d -> d > 0 }))
        }
    }

    /** Sets the end time; the stay is stored as a duration, so it is converted into minutes. */
    fun setEnd(end: LocalTime?) {
        val draft = _state.value.draft
        val minutes = end?.let { durationForEnd(draft.time, it, draft.duration) }
        _state.update {
            it.copy(durationText = minutes?.toString().orEmpty(), draft = it.draft.copy(duration = minutes))
        }
    }

    fun toggleTable(id: Long) = edit { d ->
        d.copy(tableIds = if (id in d.tableIds) d.tableIds - id else d.tableIds + id)
    }

    fun save() {
        val s = _state.value
        val errors = validate(s.draft)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors, error = "Bitte die markierten Felder prüfen.") }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                if (reservationId == null) repository.create(s.draft)
                else repository.update(reservationId, s.draft).let { CreateResult(reservationId) }
            }.onSuccess { result ->
                _state.update { it.copy(saving = false, savedId = result.id, warning = result.warning) }
            }.onFailure { e ->
                val fieldErrors = (e as? ApiException)?.fieldErrors.orEmpty()
                    .mapValues { (_, v) -> v.joinToString(" ") }
                _state.update {
                    it.copy(
                        saving = false,
                        error = e.message ?: "Speichern fehlgeschlagen",
                        fieldErrors = fieldErrors,
                        unauthorized = e is ApiException && e.isUnauthorized,
                    )
                }
            }
        }
    }

    companion object {
        /** Minutes from [start] to [end]; an end at or before the start is taken as the next day. */
        fun durationBetween(start: LocalTime, end: LocalTime): Int {
            val minutes = Duration.between(start, end).toMinutes().toInt()
            return if (minutes <= 0) minutes + MINUTES_PER_DAY else minutes
        }

        /**
         * Duration for a newly picked end time. The picker only knows a clock time, so whole
         * extra days of a stay longer than 24 hours are kept from [currentDuration].
         */
        fun durationForEnd(start: LocalTime, end: LocalTime, currentDuration: Int?): Int {
            val extraDays = ((currentDuration ?: 1) - 1).coerceAtLeast(0) / MINUTES_PER_DAY
            return durationBetween(start, end) + extraDays * MINUTES_PER_DAY
        }

        /** Number of days the stay ends after the start day (0 = same day). */
        fun daysAfterStart(start: LocalTime, durationMinutes: Int): Int =
            (start.toSecondOfDay() / 60 + durationMinutes) / MINUTES_PER_DAY

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
    }
}
