package io.github.rsclub22.tireservations.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) : SettingsStorage {

    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val email = stringPreferencesKey("email")
        val isAdmin = booleanPreferencesKey("is_admin")
        val token = stringPreferencesKey("token")
        val userName = stringPreferencesKey("user_name")
        val defaultLocation = longPreferencesKey("default_location")
        val skippedUpdate = stringPreferencesKey("skipped_update")
    }

    override val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        baseUrl = this[Keys.baseUrl].orEmpty(),
        email = this[Keys.email].orEmpty(),
        isAdmin = this[Keys.isAdmin] ?: true,
        token = this[Keys.token],
        userName = this[Keys.userName],
        defaultLocationId = this[Keys.defaultLocation],
        skippedUpdateVersion = this[Keys.skippedUpdate],
    )

    override suspend fun saveLogin(baseUrl: String, email: String, isAdmin: Boolean, token: String, userName: String?) {
        context.dataStore.edit {
            it[Keys.baseUrl] = baseUrl
            it[Keys.email] = email
            it[Keys.isAdmin] = isAdmin
            it[Keys.token] = token
            if (userName != null) it[Keys.userName] = userName else it.remove(Keys.userName)
        }
    }

    override suspend fun setDefaultLocation(id: Long?) {
        context.dataStore.edit {
            if (id != null) it[Keys.defaultLocation] = id else it.remove(Keys.defaultLocation)
        }
    }

    override suspend fun skipUpdate(version: String) {
        context.dataStore.edit { it[Keys.skippedUpdate] = version }
    }

    override suspend fun logout() {
        context.dataStore.edit {
            it.remove(Keys.token)
            it.remove(Keys.userName)
        }
    }
}
