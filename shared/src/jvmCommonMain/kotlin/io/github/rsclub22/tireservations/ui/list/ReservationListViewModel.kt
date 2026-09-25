package io.github.rsclub22.tireservations.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rsclub22.tireservations.data.ApiException
import io.github.rsclub22.tireservations.data.InternReservierung
import io.github.rsclub22.tireservations.data.Location
import io.github.rsclub22.tireservations.data.Reservation
import io.github.rsclub22.tireservations.data.ReservationQuery
import io.github.rsclub22.tireservations.data.ReservationRepository
import io.github.rsclub22.tireservations.data.ReservationStatus
import io.github.rsclub22.tireservations.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ListState(
    val date: LocalDate = LocalDate.now(),
    val locationId: Long? = null,
    val statusId: Long? = null,
    val search: String = "",
    val searchActive: Boolean = false,
    val reservations: List<Reservation> = emptyList(),
    val locations: List<Location> = emptyList(),
    val statuses: List<ReservationStatus> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val unauthorized: Boolean = false,
    /**
     * Unbestaetigte Reservierungen - die ueber das oeffentliche Formular. Sie
     * liegen meist an anderen Tagen als dem gezeigten, deshalb stehen sie
     * ueber der Tagesliste und nicht darin.
     */
    val offene: List<InternReservierung> = emptyList(),
    val offeneGesamt: Int = 0,
) {
    val guestTotal: Int get() = reservations.sumOf { it.guestNum }
    val isSearching: Boolean get() = searchActive && search.isNotBlank()
}

@OptIn(FlowPreview::class)
class ReservationListViewModel(
    private val repository: ReservationRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ListState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            val defaultLocation = settingsStore.settings.first().defaultLocationId
            _state.update { it.copy(locationId = defaultLocation) }
            loadLookups()
            load()
            ladeOffene()
        }
        _state.map { it.search }
            .distinctUntilChanged()
            .drop(1)
            .debounce(400)
            .onEach { load() }
            .launchIn(viewModelScope)
    }

    /**
     * Holt die unbestaetigten Reservierungen fuer die Markierung.
     *
     * Rein lesend: den Merker fuer die Benachrichtigungen ruecken allein die
     * Wachdienste vor. Faellt der Abruf aus - etwa weil das Token die Ability
     * "intern" nicht hat -, bleibt die Markierung einfach weg; die Tagesliste
     * deswegen mit einem Fehler zu behelligen waere unverhaeltnismaessig.
     */
    private suspend fun ladeOffene() {
        // seit = 0 liefert alle unbestaetigten, nicht nur die seit dem letzten
        // Blick hinzugekommenen. Der Merker gehoert allein den Benachrichtigungen;
        // die Markierung soll zeigen, was insgesamt offen ist.
        val offen = runCatching { repository.internOffen(0) }.getOrNull() ?: return

        _state.update { it.copy(offene = offen.reservierungen, offeneGesamt = offen.offenGesamt) }
    }

    private suspend fun loadLookups() {
        runCatching {
            val locations = repository.locations()
            val statuses = repository.statuses()
            _state.update { s ->
                s.copy(
                    locations = locations,
                    statuses = statuses,
                    // Drop a stored default location that no longer exists.
                    locationId = s.locationId?.takeIf { id -> locations.any { it.id == id } },
                )
            }
        }.onFailure(::handleError)
    }

    fun refresh() {
        viewModelScope.launch {
            if (_state.value.locations.isEmpty()) loadLookups()
            load(pullToRefresh = true)
            ladeOffene()
        }
    }

    private var shownBefore = false

    /** Called whenever the screen becomes visible; reloads silently when coming back from another screen. */
    fun onScreenShown() {
        if (shownBefore) {
            load(quiet = true)
            viewModelScope.launch { ladeOffene() }
        }
        shownBefore = true
    }

    fun setDate(date: LocalDate) {
        _state.update { it.copy(date = date) }
        load()
    }

    fun shiftDate(days: Long) = setDate(_state.value.date.plusDays(days))

    fun setLocation(id: Long?) {
        _state.update { it.copy(locationId = id) }
        load()
    }

    fun setStatus(id: Long?) {
        _state.update { it.copy(statusId = id) }
        load()
    }

    fun setSearch(text: String) = _state.update { it.copy(search = text) }

    fun setSearchActive(active: Boolean) {
        val hadSearch = _state.value.isSearching
        _state.update { it.copy(searchActive = active, search = if (active) it.search else "") }
        if (!active && hadSearch) load()
    }

    private fun load(pullToRefresh: Boolean = false, quiet: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            _state.update {
                it.copy(
                    loading = !pullToRefresh && !quiet,
                    refreshing = pullToRefresh,
                    error = if (quiet) it.error else null,
                )
            }
            val query = ReservationQuery(
                date = if (s.isSearching) null else s.date,
                locationId = s.locationId,
                statusId = s.statusId,
                search = s.search.takeIf { s.isSearching },
            )
            try {
                val list = repository.reservations(query)
                _state.update { it.copy(reservations = list, loading = false, refreshing = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, refreshing = false) }
                handleError(e)
            }
        }
    }

    private fun handleError(e: Throwable) {
        if (e is CancellationException) throw e
        _state.update {
            it.copy(
                error = e.message ?: "Unbekannter Fehler",
                unauthorized = e is ApiException && e.isUnauthorized,
            )
        }
    }
}
