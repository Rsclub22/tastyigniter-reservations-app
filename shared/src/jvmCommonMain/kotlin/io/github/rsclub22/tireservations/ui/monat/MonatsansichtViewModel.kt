package io.github.rsclub22.tireservations.ui.monat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rsclub22.tireservations.data.ApiException
import io.github.rsclub22.tireservations.data.Monatstag
import io.github.rsclub22.tireservations.data.Monatsuebersicht
import io.github.rsclub22.tireservations.data.ReservationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class MonatState(
    val monat: YearMonth = YearMonth.now(),
    val uebersicht: Monatsuebersicht? = null,
    val gewaehlt: LocalDate? = null,
    val laden: Boolean = true,
    val fehler: String? = null,
    val unauthorized: Boolean = false,
) {
    val gewaehlterTag: Monatstag?
        get() = gewaehlt?.let { uebersicht?.tag(it) }
}

/**
 * Der Monat auf einen Blick: wo ist noch Platz.
 *
 * Holt nur Summen je Tag, nicht die Zeitfenster - das ist ein eigener Endpunkt und
 * nicht dreissig Aufrufe der Tagesansicht.
 */
class MonatsansichtViewModel(
    private val repository: ReservationRepository,
    startmonat: YearMonth = YearMonth.now(),
) : ViewModel() {

    private val _state = MutableStateFlow(MonatState(monat = startmonat))
    val state = _state.asStateFlow()

    private var ladeJob: Job? = null

    init {
        laden()
    }

    fun weiter(monate: Long) {
        _state.update { it.copy(monat = it.monat.plusMonths(monate), gewaehlt = null) }
        laden()
    }

    fun heute() {
        val jetzt = YearMonth.now()
        if (_state.value.monat == jetzt) {
            _state.update { it.copy(gewaehlt = LocalDate.now()) }
            return
        }
        _state.update { it.copy(monat = jetzt, gewaehlt = LocalDate.now()) }
        laden()
    }

    /** Erneutes Tippen hebt die Auswahl wieder auf. */
    fun waehle(datum: LocalDate) =
        _state.update { it.copy(gewaehlt = if (it.gewaehlt == datum) null else datum) }

    fun neuLaden() = laden()

    private fun laden() {
        ladeJob?.cancel()
        val monat = _state.value.monat
        ladeJob = viewModelScope.launch {
            _state.update { it.copy(laden = true, fehler = null) }
            try {
                val uebersicht = repository.internMonat(monat.year, monat.monthValue)
                _state.update { it.copy(uebersicht = uebersicht, laden = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _state.update {
                    it.copy(laden = false, fehler = e.message, unauthorized = e.isUnauthorized)
                }
            }
        }
    }
}
