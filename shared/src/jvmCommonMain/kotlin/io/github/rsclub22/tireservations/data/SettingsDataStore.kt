package io.github.rsclub22.tireservations.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.rsclub22.tireservations.platform.settingsFilePath
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Der DataStore mit den Einstellungen.
 *
 * Vorher hing er über `preferencesDataStore(name = "settings")` an einem
 * `android.content.Context`. Die multiplatform-fähige Fassung bekommt stattdessen
 * den Dateipfad, den jede Plattform selbst kennt - siehe [settingsFilePath].
 *
 * @param beiVerlust wird gerufen, wenn der gespeicherte Stand unlesbar war und
 *   verworfen werden musste. Der Aufrufer kann es der Oberflaeche sagen.
 */
fun createSettingsDataStore(beiVerlust: () -> Unit = {}): DataStore<Preferences> {
    val pfad = settingsFilePath()

    // Eine vorhandene, leere Datei ist der stille Fall: DataStore liest sie
    // klaglos als "nichts gespeichert", und die Anwendung steht am Anmeldeschirm,
    // als haette sie nie jemand benutzt - keine Server-Adresse, keine Ausnahme,
    // kein Hinweis.
    //
    // Sie kann dabei nicht harmlos sein: reines Lesen legt die Datei nicht an,
    // erst ein Schreiben tut das und hinterlaesst dann Inhalt. Null Bytes heisst
    // also, dass ein Schreibvorgang abgebrochen ist - etwa weil ein Update den
    // Prozess abgeraeumt hat, waehrend der Wachdienst seinen Merker ablegte.
    if (FileSystem.SYSTEM.metadataOrNull(pfad.toPath())?.size == 0L) beiVerlust()

    return PreferenceDataStoreFactory.createWithPath(
        // Ohne diesen Handler wirft eine beschaedigte Datei bei *jedem* Lesen.
        // Die Anwendung entscheidet ihren Startschirm aber aus den Einstellungen,
        // und die Ausnahme laesst diese Entscheidung nie fallen: sie bleibt beim
        // Ladekringel stehen, bei jedem Start erneut. Herauszukommen waere nur
        // ueber "App-Daten loeschen" in den Systemeinstellungen - das kann man
        // niemandem zumuten, der gerade das Telefon abnehmen will.
        //
        // Der Preis ist eine Abmeldung. Die ist aergerlich, aber in einer Minute
        // behoben, und [beiVerlust] sorgt dafuer, dass sie erklaert wird statt
        // wie ein Gespenst auszusehen.
        corruptionHandler = ReplaceFileCorruptionHandler {
            beiVerlust()
            emptyPreferences()
        },
    ) { pfad.toPath() }
}
