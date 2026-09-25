package io.github.rsclub22.tireservations.ui.blatt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rsclub22.tireservations.data.ApiException
import io.github.rsclub22.tireservations.data.Druckblatt
import io.github.rsclub22.tireservations.data.ReservationRepository
import io.github.rsclub22.tireservations.data.Tagesblatt
import io.github.rsclub22.tireservations.platform.drucke
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Wie der Tag auf die Blaetter verteilt wird. */
enum class Trennung(val wert: String?, val beschriftung: String) {
    /** Die im Haus eingestellte Zeit, meist 15:00. */
    VORGABE(null, "Mittag / Abend"),

    /** Ein einziges Blatt fuer den ganzen Tag. */
    KEINE("aus", "Ganzer Tag"),
}

data class BlattState(
    val von: LocalDate = LocalDate.now(),
    val bis: LocalDate = LocalDate.now(),
    val trennung: Trennung = Trennung.VORGABE,
    val blatt: Tagesblatt? = null,
    val laden: Boolean = true,
    val fehler: String? = null,
    val meldung: String? = null,
    val unauthorized: Boolean = false,
) {
    val zeitraum: Boolean get() = von != bis
}

/**
 * Das Tagesblatt: was am Abend auf Papier neben dem Telefon liegt.
 *
 * Gedruckt wird ueber ein erzeugtes HTML, nicht ueber Zeichenbefehle - siehe
 * [Druckblatt]. Der Weg auf den Drucker ist das Einzige, was sich je Plattform
 * unterscheidet.
 */
class TagesblattViewModel(
    private val repository: ReservationRepository,
    startdatum: LocalDate = LocalDate.now(),
) : ViewModel() {

    private val _state = MutableStateFlow(BlattState(von = startdatum, bis = startdatum))
    val state = _state.asStateFlow()

    private var ladeJob: Job? = null

    init {
        laden()
    }

    fun setVon(datum: LocalDate) {
        _state.update { it.copy(von = datum, bis = if (it.bis < datum) datum else it.bis) }
        laden()
    }

    fun setBis(datum: LocalDate) {
        _state.update { it.copy(bis = datum, von = if (it.von > datum) datum else it.von) }
        laden()
    }

    /** Einzelner Tag, vor oder zurueck. */
    fun tagWeiter(tage: Long) {
        val neu = _state.value.von.plusDays(tage)
        _state.update { it.copy(von = neu, bis = neu) }
        laden()
    }

    fun setTrennung(trennung: Trennung) {
        if (trennung == _state.value.trennung) return
        _state.update { it.copy(trennung = trennung) }
        laden()
    }

    fun neuLaden() = laden()

    fun meldungGesehen() = _state.update { it.copy(meldung = null) }

    private fun laden() {
        ladeJob?.cancel()
        val s = _state.value
        ladeJob = viewModelScope.launch {
            _state.update { it.copy(laden = true, fehler = null) }
            try {
                val blatt = repository.internTagesblatt(s.von, s.bis, s.trennung.wert)
                _state.update { it.copy(blatt = blatt, laden = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _state.update { it.copy(laden = false, fehler = e.message, unauthorized = e.isUnauthorized) }
            }
        }
    }

    fun drucken() {
        val blatt = _state.value.blatt ?: return
        viewModelScope.launch {
            // Der Standortname steht im Kopf jedes Blattes. Faellt der Abruf aus,
            // ist das kein Grund, den Druck zu verweigern - dann bleibt die Zeile leer.
            val standort = runCatching { repository.locations().firstOrNull()?.name }.getOrNull().orEmpty()
            val gedruckt = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

            val erfolg = drucke(
                html = Druckblatt.html(blatt, standort, gedruckt),
                titel = "Tagesblatt ${blatt.von}",
            )

            _state.update {
                it.copy(
                    meldung = if (erfolg) {
                        null
                    } else {
                        "Zum Drucken wird ein Browser bzw. ein Druckdienst gebraucht – hier ist keiner eingerichtet."
                    },
                )
            }
        }
    }
}
