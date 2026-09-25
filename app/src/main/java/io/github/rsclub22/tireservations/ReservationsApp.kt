package io.github.rsclub22.tireservations

import android.app.Application
import io.github.rsclub22.tireservations.data.ReservationRepository
import io.github.rsclub22.tireservations.data.SettingsStore
import io.github.rsclub22.tireservations.data.UpdateChecker
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class ReservationsApp : Application() {

    val settingsStore by lazy { SettingsStore(this) }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .build()
    }

    val repository by lazy { ReservationRepository(settingsStore, httpClient) }

    val updateChecker by lazy {
        // Play Store installs are updated by the store; only sideloaded APKs check GitHub.
        val repo = if (UpdateChecker.isFromPlayStore(this)) "" else BuildConfig.UPDATE_REPO
        UpdateChecker(httpClient, repo, BuildConfig.VERSION_NAME)
    }

    /** The automatic update check runs once per app start. */
    var updateCheckedThisSession = false
}
