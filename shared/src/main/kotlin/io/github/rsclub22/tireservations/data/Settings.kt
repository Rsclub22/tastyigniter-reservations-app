package io.github.rsclub22.tireservations.data

import kotlinx.coroutines.flow.Flow

data class AppSettings(
    val baseUrl: String = "",
    val email: String = "",
    val isAdmin: Boolean = true,
    val token: String? = null,
    val userName: String? = null,
    val defaultLocationId: Long? = null,
    /** Update the user chose "Später" for; not offered again automatically. */
    val skippedUpdateVersion: String? = null,
) {
    val isLoggedIn: Boolean get() = !token.isNullOrBlank() && baseUrl.isNotBlank()
}

/** Persistent app settings; implemented with DataStore on Android and a config file on desktop. */
interface SettingsStorage {
    val settings: Flow<AppSettings>

    suspend fun saveLogin(baseUrl: String, email: String, isAdmin: Boolean, token: String, userName: String?)

    suspend fun setDefaultLocation(id: Long?)

    suspend fun skipUpdate(version: String)

    suspend fun logout()
}
