package io.github.rsclub22.tireservations

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.rsclub22.tireservations.ui.AppNavigation
import io.github.rsclub22.tireservations.ui.components.MeldungsHost
import io.github.rsclub22.tireservations.ui.theme.ReservationsTheme
import io.github.rsclub22.tireservations.ui.update.AutoUpdatePrompt

/**
 * Wird vom Gradle-Block `compose.desktop.application` gesetzt (`-Dapp.version`), damit
 * die Version nur an einer Stelle steht - in `packageVersion`. Beim Start aus der IDE
 * ohne dieses Argument steht "dev".
 */
private val appVersion: String = System.getProperty("app.version") ?: "dev"

fun main() {
    AppGraph.init(
        debug = appVersion == "dev",
        // Die Release-Strecke haengt .deb-Pakete an; die Update-Pruefung sucht daher
        // nach dieser Endung und nicht nach der APK. Aus dem Entwicklungsstart
        // heraus bleibt sie aus.
        updateRepo = if (appVersion == "dev") "" else "Rsclub22/tastyigniter-reservations-app",
        versionName = appVersion,
        userAgent = "TIReservations-Desktop",
        assetSuffix = ".deb",
    )

    application {
        val state = rememberWindowState(
            // Die Bildschirme sind fuers Telefon gebaut. Mit weniger Breite sieht das
            // gestaucht aus, mit deutlich mehr gestreckt - ein eigenes Layout fuer
            // breite Fenster ist ein eigener Schritt.
            size = DpSize(1100.dp, 800.dp),
        )

        Window(
            onCloseRequest = ::exitApplication,
            state = state,
            title = "Reservierungen",
        ) {
            ReservationsTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation(
                        repository = AppGraph.repository,
                        settingsStore = AppGraph.settingsStore,
                        updateChecker = AppGraph.updateChecker,
                        versionLabel = appVersion,
                    )
                    AutoUpdatePrompt(
                        checker = AppGraph.updateChecker,
                        settingsStore = AppGraph.settingsStore,
                        alreadyChecked = { AppGraph.updateCheckedThisSession },
                        markChecked = { AppGraph.updateCheckedThisSession = true },
                    )
                    MeldungsHost()
                }
            }
        }
    }
}
