package io.github.rsclub22.tireservations

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.github.rsclub22.tireservations.platform.setzeAktiveActivity
import io.github.rsclub22.tireservations.ui.AppNavigation
import io.github.rsclub22.tireservations.ui.components.MeldungsHost
import io.github.rsclub22.tireservations.ui.theme.ReservationsTheme
import io.github.rsclub22.tireservations.ui.update.AutoUpdatePrompt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Das Drucken braucht eine Activity, nicht den Anwendungskontext.
        setzeAktiveActivity(this)
        frageMeldeerlaubnis()
        val app = application as ReservationsApp
        setContent {
            ReservationsTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation(
                        repository = AppGraph.repository,
                        settingsStore = AppGraph.settingsStore,
                        updateChecker = AppGraph.updateChecker,
                        versionLabel = app.versionLabel,
                    )
                    AutoUpdatePrompt(
                        checker = AppGraph.updateChecker,
                        settingsStore = AppGraph.settingsStore,
                        alreadyChecked = { AppGraph.updateCheckedThisSession },
                        markChecked = { AppGraph.updateCheckedThisSession = true },
                    )
                    // Ueber dem Inhalt: zeigt die Kurzmeldungen, die kein Bildschirm
                    // besitzt - fruehere Toasts.
                    MeldungsHost()
                }
            }
        }
    }

    override fun onDestroy() {
        setzeAktiveActivity(null)
        super.onDestroy()
    }

    /**
     * Ab Android 13 muss der Benutzer Meldungen erlauben. Ohne die Erlaubnis
     * verwirft das System sie stillschweigend - dann bliebe unklar, warum nie
     * etwas kommt. Einmal fragen genuegt; lehnt jemand ab, weicht die App auf
     * eine Meldung im Programm aus.
     */
    private fun frageMeldeerlaubnis() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val erteilt = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        if (!erteilt) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
