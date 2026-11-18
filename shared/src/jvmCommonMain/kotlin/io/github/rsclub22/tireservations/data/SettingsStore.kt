package io.github.rsclub22.tireservations.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val baseUrl: String = "",
    val email: String = "",
    val isAdmin: Boolean = true,
    val token: String? = null,
    val userName: String? = null,
    val defaultLocationId: Long? = null,
    /** Update the user chose "Später" for; not offered again automatically. */
    val skippedUpdateVersion: String? = null,
    /**
     * Hoechste Reservierungsnummer, die beim Pruefen auf neue unbestaetigte schon
     * gesehen wurde. Eine Nummer und keine Uhrzeit, weil der Server created_at auf
     * das Datum kuerzt.
     */
    val letzteGesehene: Long = 0,
    /** Betrieb ohne TastyIgniter, die Reservierungen liegen in einer Datei auf dem Geraet. */
    val lokal: Boolean = false,
) {
    val isLoggedIn: Boolean get() = lokal || (!token.isNullOrBlank() && baseUrl.isNotBlank())
}

class SettingsStore(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val email = stringPreferencesKey("email")
        val isAdmin = booleanPreferencesKey("is_admin")
        val token = stringPreferencesKey("token")
        val userName = stringPreferencesKey("user_name")
        val defaultLocation = longPreferencesKey("default_location")
        val skippedUpdate = stringPreferencesKey("skipped_update")
        val letzteGesehene = longPreferencesKey("letzte_gesehene")
        val lokal = booleanPreferencesKey("lokal")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        baseUrl = this[Keys.baseUrl].orEmpty(),
        email = this[Keys.email].orEmpty(),
        isAdmin = this[Keys.isAdmin] ?: true,
        token = this[Keys.token],
        userName = this[Keys.userName],
        defaultLocationId = this[Keys.defaultLocation],
        skippedUpdateVersion = this[Keys.skippedUpdate],
        letzteGesehene = this[Keys.letzteGesehene] ?: 0,
        lokal = this[Keys.lokal] ?: false,
        // Im lokalen Betrieb ist niemand Kunde; ein gemerkter Kundenzugang vom
        // Server darf dort nicht die Kundenansicht ausloesen.
    ).let { if (it.lokal) it.copy(isAdmin = true) else it }

    suspend fun saveLogin(baseUrl: String, email: String, isAdmin: Boolean, token: String, userName: String?) {
        dataStore.edit {
            it[Keys.baseUrl] = baseUrl
            it[Keys.email] = email
            it[Keys.isAdmin] = isAdmin
            it[Keys.token] = token
            if (userName != null) it[Keys.userName] = userName else it.remove(Keys.userName)
            it.remove(Keys.lokal)
        }
    }

    /**
     * Schaltet auf den Betrieb ohne Server. Adresse und E-Mail bleiben stehen,
     * damit der Rueckweg zum Server nicht mit Abtippen beginnt; das Token nicht,
     * sonst waere man nach dem Beenden des lokalen Betriebs still wieder angemeldet.
     */
    suspend fun startLokal() {
        dataStore.edit {
            it[Keys.lokal] = true
            it.remove(Keys.token)
            it.remove(Keys.userName)
        }
    }

    suspend fun setDefaultLocation(id: Long?) {
        dataStore.edit {
            if (id != null) it[Keys.defaultLocation] = id else it.remove(Keys.defaultLocation)
        }
    }

    suspend fun skipUpdate(version: String) {
        dataStore.edit { it[Keys.skippedUpdate] = version }
    }

    suspend fun merkeGesehen(id: Long) {
        dataStore.edit { it[Keys.letzteGesehene] = id }
    }

    suspend fun logout() {
        dataStore.edit {
            it.remove(Keys.token)
            it.remove(Keys.userName)
            it.remove(Keys.lokal)
        }
    }
}
