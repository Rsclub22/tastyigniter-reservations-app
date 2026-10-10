package io.github.rsclub22.tireservations

import android.app.Application
import io.github.rsclub22.tireservations.data.isFromPlayStore
import io.github.rsclub22.tireservations.hintergrund.Wachposten
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

        val ausDemPlayStore = isFromPlayStore(this)
        AppGraph.init(
            debug = BuildConfig.DEBUG,
            // Leer schaltet die Update-Prüfung ab: im Debug-Build (eigene App mit
            // Suffix ".debug") und wenn der Play Store die Updates ohnehin macht.
            updateRepo = if (BuildConfig.DEBUG || ausDemPlayStore) "" else BuildConfig.UPDATE_REPO,
            versionName = BuildConfig.VERSION_NAME,
            userAgent = "TIReservations-Android",
            assetSuffix = ".apk",
            // Die Einstellungen verweisen dann auf die Store-Seite statt auf GitHub.
            storeUrl = if (ausDemPlayStore) "https://play.google.com/store/apps/details?id=$packageName" else null,
        )

        // Auch bei geschlossener App nach neuen unbestaetigten Reservierungen sehen.
        Wachposten.einplanen(this)
    }

    /** Wird unter Einstellungen angezeigt. */
    val versionLabel: String
        get() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
}
