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
import io.github.rsclub22.tireservations.ui.components.Meldungen
import io.github.rsclub22.tireservations.ui.components.MeldungsHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.rsclub22.tireservations.platform.Selbsterneuerung
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
        // Aus dem Entwicklungsstart heraus bleibt die Pruefung aus.
        updateRepo = if (appVersion == "dev") "" else "Rsclub22/tastyigniter-reservations-app",
        versionName = appVersion,
        userAgent = "TIReservations-Desktop",
        // Die Release-Strecke haengt neben dem .deb ein .tar.gz mit dem
        // ausgepackten Programm an. Nur das laesst sich ohne Paketverwaltung und
        // ohne Root einspielen - siehe Selbsterneuerung.
        assetSuffix = ".tar.gz",
    )

    // Was ein frueherer Tausch stehen liess: die alte Fassung lief damals noch
    // aus ihrem Verzeichnis und konnte es nicht selbst loeschen.
    Selbsterneuerung.raeumeAuf()

    starteWache()

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

/**
 * Sieht regelmaessig nach neuen unbestaetigten Reservierungen.
 *
 * Auf dem Desktop reicht eine Schleife: das Programm laeuft auf dem Tresenrechner
 * ohnehin den ganzen Tag. Eine Minute ist nah genug am Geschehen und belastet den
 * Pi nicht - der Endpunkt liefert nur Zahlen und die wenigen offenen Eintraege.
 *
 * Findet das System keine Ablage fuer Meldungen - unter Wayland ist die AWT-Ablage
 * oft nicht da -, wird die Meldung stattdessen in der App gezeigt.
 */
private fun starteWache() {
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        while (true) {
            val ergebnis = AppGraph.wachdienst.nachsehen()

            if (ergebnis != null && ergebnis.neue > 0 && !ergebnis.gemeldet) {
                Meldungen.zeige("${ergebnis.titel}: ${ergebnis.text.lineSequence().first()}")
            }

            delay(60_000)
        }
    }
}
