package io.github.rsclub22.tireservations

import android.app.Application
import io.github.rsclub22.tireservations.data.isFromPlayStore
import io.github.rsclub22.tireservations.platform.initAndroidPlatform

/**
 * Setzt den Anwendungskontext und baut die gemeinsamen Abhängigkeiten.
 *
 * Die Objekte selbst liegen jetzt in [AppGraph] und werden vom Desktop genauso
 * aufgebaut; hier bleibt nur, was Android eigen ist - der Kontext und die Werte aus
 * `BuildConfig`.
 */
class ReservationsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initAndroidPlatform(this)

        AppGraph.init(
            debug = BuildConfig.DEBUG,
            // Leer schaltet die Update-Prüfung ab: im Debug-Build (eigene App mit
            // Suffix ".debug") und wenn der Play Store die Updates ohnehin macht.
            updateRepo = if (BuildConfig.DEBUG || isFromPlayStore(this)) "" else BuildConfig.UPDATE_REPO,
            versionName = BuildConfig.VERSION_NAME,
            userAgent = "TIReservations-Android",
            assetSuffix = ".apk",
        )
    }

    /** Wird unter Einstellungen angezeigt. */
    val versionLabel: String
        get() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
}
