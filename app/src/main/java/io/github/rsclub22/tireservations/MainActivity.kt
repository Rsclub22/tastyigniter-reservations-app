package io.github.rsclub22.tireservations

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.github.rsclub22.tireservations.ui.AppNavigation
import io.github.rsclub22.tireservations.ui.theme.ReservationsTheme
import io.github.rsclub22.tireservations.ui.update.AutoUpdatePrompt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as ReservationsApp
        setContent {
            ReservationsTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation(app.repository, app.settingsStore, app.updateChecker)
                    AutoUpdatePrompt(
                        checker = app.updateChecker,
                        settingsStore = app.settingsStore,
                        alreadyChecked = { app.updateCheckedThisSession },
                        markChecked = { app.updateCheckedThisSession = true },
                    )
                }
            }
        }
    }
}
