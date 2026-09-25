package io.github.rsclub22.tireservations.ui.annahme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rsclub22.tireservations.data.Annahmeentwurf
import io.github.rsclub22.tireservations.data.ApiException
import io.github.rsclub22.tireservations.data.InternReservierung
import io.github.rsclub22.tireservations.data.ReservationRepository
import io.github.rsclub22.tireservations.data.Tagesdaten
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class AnnahmeState(
    val datum: LocalDate = LocalDate.now(),
    val gaeste: Int = 2,
    val raumId: Long? = null,
    val ohneTisch: Boolean = false,
    val zeit: LocalTime? = null,
    val nachname: String = "",
    val telefon: String = "",
    val notiz: String = "",
    val tag: Tagesdaten? = null,
    val laden: Boolean = true,
    val speichern: Boolean = false,
    val fehler: String? = null,
    val feldFehler: Map<String, List<String>> = emptyMap(),
    /** Die zuletzt angenommene Reservierung - bleibt stehen, bis der naechste Anruf beginnt. */
    val angelegt: InternReservierung? = null,
    val unauthorized: Boolean = false,
) {
    val bereit: Boolean
        get() = zeit != null && nachname.isNotBlank() && telefon.isNotBlank() && !speichern

    /** Fehlermeldung des Servers zu einem Feld, etwa die Hoechstzahl an `gaeste`. */
    fun fehlerZu(feld: String): String? = feldFehler[feld]?.firstOrNull()
}

/**
 * Die Telefonannahme: schnelles Eintragen, waehrend der Gast am Telefon ist.
 *
 * Bewusst anders als der Bearbeiten-Bildschirm: Nachname und Telefonnummer
 * genuegen, der Status ist sofort bestaetigt, es wird nichts verschickt. Nach dem
 * Speichern bleibt der Tag stehen und die Eingabefelder sind leer - der naechste
 * Anruf beginnt ohne einen weiteren Klick.
 */
class TelefonannahmeViewModel(
    private val repository: ReservationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AnnahmeState())
    val state = _state.asStateFlow()

    private var ladeJob: Job? = null

    init {
        ladeTag()
    }

    // --- Tag und Rahmen ---------------------------------------------------------------

    fun setDatum(datum: LocalDate) {
        if (datum == _state.value.datum) return
        // Die gewaehlte Zeit gilt nur fuer den Tag, an dem sie gewaehlt wurde.
        _state.update { it.copy(datum = datum, zeit = null) }
        ladeTag()
    }

    fun tagWeiter(tage: Long) = setDatum(_state.value.datum.plusDays(tage))

    fun setGaeste(gaeste: Int) {
        val neu = gaeste.coerceIn(1, 200)
        if (neu == _state.value.gaeste) return
        // Die Gaestezahl entscheidet mit, welche Zeitfenster passen - der Server
        // rechnet das, also neu holen.
        _state.update { it.copy(gaeste = neu, feldFehler = emptyMap()) }
        ladeTag()
    }

    fun setRaum(raumId: Long?) {
        if (raumId == _state.value.raumId) return
        // Ein Raum haengt nicht an den Tischen, damit aendert sich die Belegung.
        _state.update { it.copy(raumId = raumId, ohneTisch = false, zeit = null) }
        ladeTag()
    }

    /**
     * Ausdruecklich ohne Tisch und ohne Raum. Loescht die Raumauswahl, weil beides
     * zusammen keine eindeutige Angabe waere - der Server wuerde den Raum ohnehin
     * verwerfen.
     */
    fun setOhneTisch(an: Boolean) {
        _state.update { it.copy(ohneTisch = an, raumId = if (an) null else it.raumId) }
        if (an && _state.value.raumId != null) ladeTag()
    }

    fun setZeit(zeit: LocalTime) = _state.update { it.copy(zeit = zeit, feldFehler = emptyMap()) }

    fun setNachname(wert: String) = _state.update { it.copy(nachname = wert, feldFehler = emptyMap()) }

    fun setTelefon(wert: String) = _state.update { it.copy(telefon = wert, feldFehler = emptyMap()) }

    fun setNotiz(wert: String) = _state.update { it.copy(notiz = wert) }

    fun neuLaden() = ladeTag()

    fun fehlerGesehen() = _state.update { it.copy(fehler = null) }

    /** Bestaetigung wegraeumen und fuer den naechsten Anruf freimachen. */
    fun weiter() = _state.update {
        it.copy(angelegt = null, nachname = "", telefon = "", notiz = "", zeit = null, feldFehler = emptyMap())
    }

    private fun ladeTag() {
        ladeJob?.cancel()
        val s = _state.value
        ladeJob = viewModelScope.launch {
            _state.update { it.copy(laden = true, fehler = null) }
            try {
                val tag = repository.internTag(s.datum, s.gaeste, s.raumId)
                _state.update { jetzt ->
                    jetzt.copy(
                        tag = tag,
                        laden = false,
                        // Passt die gewaehlte Zeit nicht mehr in den neuen Tag, fallen
                        // lassen statt eine Zeit anzubieten, die es nicht mehr gibt.
                        zeit = jetzt.zeit?.takeIf { z -> tag.belegung.any { it.zeit == z } },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _state.update {
                    it.copy(laden = false, fehler = e.message, unauthorized = e.isUnauthorized)
                }
            }
        }
    }

    // --- Annehmen ---------------------------------------------------------------------

    fun annehmen() {
        val s = _state.value
        val zeit = s.zeit ?: return
        if (s.speichern) return

        viewModelScope.launch {
            _state.update { it.copy(speichern = true, fehler = null, feldFehler = emptyMap()) }
            try {
                val angelegt = repository.internAnnehmen(
                    Annahmeentwurf(
                        datum = s.datum,
                        zeit = zeit,
                        gaeste = s.gaeste,
                        nachname = s.nachname,
                        telefon = s.telefon,
                        notiz = s.notiz,
                        raumId = s.raumId,
                        ohneTisch = s.ohneTisch,
                    ),
                )
                _state.update { it.copy(speichern = false, angelegt = angelegt) }
                // Den Tag neu holen: die eigene Annahme veraendert die Belegung, und am
                // Telefon wird gleich die naechste eingetragen.
                ladeTag()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _state.update {
                    it.copy(
                        speichern = false,
                        // Bei einer Hoechstzahl aus einem Sperrvermerk kommt die Meldung
                        // am Feld gaeste; dann gehoert sie dorthin und nicht in eine
                        // allgemeine Fehlerzeile.
                        fehler = if (e.fieldErrors.isEmpty()) e.message else null,
                        feldFehler = e.fieldErrors,
                        unauthorized = e.isUnauthorized,
                    )
                }
            }
        }
    }
}
