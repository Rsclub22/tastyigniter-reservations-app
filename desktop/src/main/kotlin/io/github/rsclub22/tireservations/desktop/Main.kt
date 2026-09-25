package io.github.rsclub22.tireservations.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.rsclub22.tireservations.data.ReservationRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

fun main() {
    val settingsStore = DesktopSettingsStore()
    val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    val host = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()
    val repository = ReservationRepository(
        settingsStore,
        httpClient,
        listOfNotNull("Linux Desktop", host).joinToString(" "),
    )

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "TI Reservierungen",
            state = rememberWindowState(width = 1000.dp, height = 720.dp),
        ) {
            DesktopApp(repository, settingsStore)
        }
    }
}
