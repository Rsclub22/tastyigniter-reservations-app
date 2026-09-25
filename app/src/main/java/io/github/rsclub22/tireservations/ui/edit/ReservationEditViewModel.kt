package io.github.rsclub22.tireservations.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rsclub22.tireservations.data.ApiException
import io.github.rsclub22.tireservations.data.CreateResult
import io.github.rsclub22.tireservations.data.DiningTable
import io.github.rsclub22.tireservations.data.Location
import io.github.rsclub22.tireservations.data.ReservationDraft
import io.github.rsclub22.tireservations.data.ReservationRepository
import io.github.rsclub22.tireservations.data.ReservationRules
import io.github.rsclub22.tireservations.data.ReservationStatus
import io.github.rsclub22.tireservations.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
        val minutes = end?.let { ReservationRules.durationForEnd(draft.time, it, draft.duration) }
        _state.update {
            it.copy(durationText = minutes?.toString().orEmpty(), draft = it.draft.copy(duration = minutes))
        }
    }

    fun toggleTable(id: Long) = edit { d ->
        d.copy(tableIds = if (id in d.tableIds) d.tableIds - id else d.tableIds + id)
    }

    fun save() {
        val s = _state.value
        val errors = ReservationRules.validate(s.draft)
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
}
