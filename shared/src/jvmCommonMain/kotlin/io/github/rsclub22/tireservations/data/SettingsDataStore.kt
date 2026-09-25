package io.github.rsclub22.tireservations.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.rsclub22.tireservations.platform.settingsFilePath
import okio.Path.Companion.toPath

/**
 * Der DataStore mit den Einstellungen.
 *
 * Vorher hing er über `preferencesDataStore(name = "settings")` an einem
 * `android.content.Context`. Die multiplatform-fähige Fassung bekommt stattdessen
 * den Dateipfad, den jede Plattform selbst kennt - siehe [settingsFilePath].
 */
fun createSettingsDataStore(): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath { settingsFilePath().toPath() }
