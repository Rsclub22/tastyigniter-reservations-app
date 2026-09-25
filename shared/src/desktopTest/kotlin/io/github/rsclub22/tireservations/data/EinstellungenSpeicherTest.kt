package io.github.rsclub22.tireservations.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Was passiert, wenn die Einstellungsdatei beschaedigt ist.
 *
 * Das ist keine erfundene Sorge: der Wachdienst schreibt alle 15 Minuten in
 * dieselbe Datei, und ein Update killt den Prozess, wann es ihm passt. Ohne
 * Vorkehrung wirft eine unlesbare Datei bei *jedem* Lesen - und die Anwendung
 * entscheidet ihren Startschirm aus den Einstellungen, kommt also nie ueber den
 * Ladekringel hinaus. Herauszukommen waere nur ueber "App-Daten loeschen".
 *
 * Geprueft wird deshalb beides: dass ein beschaedigter Stand die Anwendung nicht
 * lahmlegt, und dass der Verlust gemeldet wird statt stillschweigend zu
 * passieren.
 */
class EinstellungenSpeicherTest {

    @get:Rule
    val ordner = TemporaryFolder()

    /**
     * DataStore laesst je Datei nur eine aktive Instanz im Prozess zu; jede
     * bekommt daher einen eigenen Gueltigkeitsbereich, der danach endet.
     */
    private fun <T> mitSpeicher(datei: File, verloren: () -> Unit = {}, block: suspend (DataStore<Preferences>) -> T): T {
        val bereich = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val speicher = PreferenceDataStoreFactory.createWithPath(
                corruptionHandler = ReplaceFileCorruptionHandler { verloren(); emptyPreferences() },
                scope = bereich,
            ) { datei.absolutePath.toPath() }

            return runBlocking { block(speicher) }
        } finally {
            bereich.cancel()
        }
    }

    private fun angemeldet(datei: File) = mitSpeicher(datei) { speicher ->
        SettingsStore(speicher).saveLogin(
            baseUrl = "https://beispiel.test/api",
            email = "wirt@beispiel.test",
            isAdmin = true,
            token = "geheim",
            userName = "Wirt",
        )
    }

    @Test
    fun `ein gespeicherter Zugang wird wiedergefunden`() {
        val datei = File(ordner.root, "settings.preferences_pb")
        angemeldet(datei)

        val gelesen = mitSpeicher(datei) { SettingsStore(it).settings.first() }

        assertTrue(gelesen.isLoggedIn)
        assertEquals("https://beispiel.test/api", gelesen.baseUrl)
        assertEquals("Wirt", gelesen.userName)
    }

    @Test
    fun `eine beschaedigte Datei legt die Anwendung nicht lahm`() {
        val datei = File(ordner.root, "settings.preferences_pb")
        angemeldet(datei)
        // Halb geschriebene Datei, wie sie ein abgewuergter Prozess hinterlassen kann.
        datei.writeBytes(datei.readBytes().let { it.copyOf(it.size / 2) })

        var verloren = false
        val gelesen = mitSpeicher(datei, verloren = { verloren = true }) {
            SettingsStore(it).settings.first()
        }

        assertFalse("Abgemeldet, wie es nach einem Verlust sein muss", gelesen.isLoggedIn)
        assertTrue("Der Verlust muss gemeldet werden, nicht stillschweigend passieren", verloren)
    }

    @Test
    fun `nach einem Verlust laesst sich wieder anmelden`() {
        val datei = File(ordner.root, "settings.preferences_pb")
        angemeldet(datei)
        datei.writeBytes(ByteArray(64) { 0x41 })

        mitSpeicher(datei, verloren = {}) { SettingsStore(it).settings.first() }
        angemeldet(datei)

        val gelesen = mitSpeicher(datei) { SettingsStore(it).settings.first() }
        assertTrue(gelesen.isLoggedIn)
    }

    /**
     * Der stille Fall: DataStore liest eine leere Datei klaglos als "nichts
     * gespeichert" - keine Ausnahme, kein Hinweis, nur ein Anmeldeschirm mit
     * leeren Feldern. Erkannt wird er deshalb an der Datei selbst.
     */
    @Test
    fun `eine leere Datei wird als Verlust erkannt`() {
        val datei = File(ordner.root, "settings.preferences_pb")
        angemeldet(datei)
        datei.writeBytes(ByteArray(0))

        val gelesen = mitSpeicher(datei) { SettingsStore(it).settings.first() }

        assertFalse(gelesen.isLoggedIn)
        assertEquals("", gelesen.baseUrl)
        assertTrue("Null Bytes heißt: ein Schreibvorgang ist abgebrochen", istVerlust(datei))
    }

    /**
     * Reines Lesen legt die Datei nicht an - eine fehlende Datei ist deshalb der
     * normale Zustand vor der ersten Anmeldung und kein Verlust.
     */
    @Test
    fun `eine fehlende Datei ist kein Verlust`() {
        val datei = File(ordner.root, "settings.preferences_pb")

        val gelesen = mitSpeicher(datei) { SettingsStore(it).settings.first() }

        assertFalse(gelesen.isLoggedIn)
        assertFalse("Vor der ersten Anmeldung gibt es nichts zu verlieren", istVerlust(datei))
        assertFalse("Lesen darf die Datei nicht anlegen", datei.exists())
    }

    @Test
    fun `ein gefuellter Stand gilt nicht als Verlust`() {
        val datei = File(ordner.root, "settings.preferences_pb")
        angemeldet(datei)

        assertFalse(istVerlust(datei))
    }

    /** Dieselbe Bedingung wie in [createSettingsDataStore]. */
    private fun istVerlust(datei: File) = datei.exists() && datei.length() == 0L
}
