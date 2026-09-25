package io.github.rsclub22.tireservations.data

import io.github.rsclub22.tireservations.platform.melde
import kotlinx.coroutines.flow.first
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Sieht nach, ob ueber das oeffentliche Formular etwas hereingekommen ist.
 *
 * Gemeldet wird nur Unbestaetigtes: was das Haus selbst ueber App oder /intern
 * eintraegt, ist sofort bestaetigt und braucht niemanden zu stoeren. Die Abgrenzung
 * laeuft ueber den Status, nicht ueber den User-Agent - eine Annahme am Tresen
 * traegt den Browser der Person davor und saehe sonst aus wie eine Online-Buchung.
 *
 * Der Merker ist eine Reservierungsnummer und keine Uhrzeit: der Server kuerzt
 * created_at auf das Datum, nach der Zeit liesse sich "neu seit zuletzt" nicht
 * beantworten.
 */
class Wachdienst(
    private val repository: ReservationRepository,
    private val settingsStore: SettingsStore,
) {

    data class Ergebnis(
        /** Wie viele seit dem letzten Blick dazugekommen sind. */
        val neue: Int,
        /** Wie viele insgesamt unbestaetigt sind - dafuer steht die Markierung. */
        val offenGesamt: Int,
        /** Wurde beim Betriebssystem gemeldet? Sonst weicht der Aufrufer aus. */
        val gemeldet: Boolean,
        val titel: String,
        val text: String,
    )

    suspend fun nachsehen(): Ergebnis? {
        val einstellungen = settingsStore.settings.first()
        if (!einstellungen.isLoggedIn) return null

        val merker = einstellungen.letzteGesehene
        val offen = runCatching { repository.internOffen(merker) }.getOrNull() ?: return null

        val entscheidung = entscheide(merker, offen)
        settingsStore.merkeGesehen(entscheidung.neuerMerker)

        if (!entscheidung.melden) {
            return Ergebnis(0, offen.offenGesamt, false, "", "")
        }

        return Ergebnis(
            neue = offen.anzahl,
            offenGesamt = offen.offenGesamt,
            gemeldet = melde(entscheidung.titel, entscheidung.text, KENNUNG),
            titel = entscheidung.titel,
            text = entscheidung.text,
        )
    }

    /** Was ein Blick ergibt - ohne Netz und ohne Betriebssystem, damit pruefbar. */
    data class Entscheidung(
        val melden: Boolean,
        val neuerMerker: Long,
        val titel: String,
        val text: String,
    )

    internal companion object {

        internal fun entscheide(merker: Long, offen: OffeneReservierungen): Entscheidung {
            // Erster Lauf: nur den Merker setzen. Sonst kuendigt die App beim ersten
            // Start jede laengst bekannte unbestaetigte Reservierung als neu an - beim
            // Haus mit einem halben Jahr Vorlauf waeren das Dutzende auf einmal.
            if (merker == 0L) {
                return Entscheidung(melden = false, neuerMerker = offen.hoechsteId, titel = "", text = "")
            }

            if (offen.anzahl == 0) {
                return Entscheidung(melden = false, neuerMerker = offen.hoechsteId, titel = "", text = "")
            }

            val titel = if (offen.anzahl == 1) {
                "Neue Reservierung"
            } else {
                "${offen.anzahl} neue Reservierungen"
            }

            return Entscheidung(
                melden = true,
                neuerMerker = offen.hoechsteId,
                titel = titel,
                text = beschreibung(offen),
            )
        }

        private fun beschreibung(offen: OffeneReservierungen): String {
            val zeilen = offen.reservierungen.take(3).map { r ->
                buildString {
                    r.datum?.let { append(it.format(TAG)).append(" ") }
                    r.zeit?.let { append(it.format(ZEIT)).append(" Uhr · ") }
                    append(r.gaeste).append(" Pers. · ")
                    append(r.name.ifBlank { "ohne Namen" })
                }
            }

            val rest = offen.reservierungen.size - zeilen.size

            return buildString {
                append(zeilen.joinToString("\n"))
                if (rest > 0) append("\n… und $rest weitere")
                append("\nNoch zu bestätigen: ${offen.offenGesamt}")
            }
        }

        /** Gleiche Kennung: eine neue Meldung ersetzt die alte, statt sich zu stapeln. */
        const val KENNUNG = 4711

        val TAG: DateTimeFormatter = DateTimeFormatter.ofPattern("EE d.M.", Locale.GERMAN)
        val ZEIT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
