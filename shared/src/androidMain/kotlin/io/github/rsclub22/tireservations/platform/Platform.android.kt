package io.github.rsclub22.tireservations.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.io.File

/**
 * Der Anwendungskontext. Wird in `ReservationsApp.onCreate` gesetzt, bevor irgendetwas
 * davon gebraucht wird.
 *
 * Die Alternative wäre, jede der Funktionen hier einen Context durchzureichen zu
 * lassen - und damit im geteilten Code eine Android-Eigenheit abzubilden, die den
 * Desktop nichts angeht.
 */
internal lateinit var appContext: Context

/**
 * Muss aus `Application.onCreate` aufgerufen werden, bevor irgendetwas hier
 * gebraucht wird. Liegt in :shared, gesetzt wird es aus :app - daher oeffentlich,
 * waehrend [appContext] selbst modulintern bleibt.
 */
fun initAndroidPlatform(context: Context) {
    appContext = context.applicationContext
}

actual val deviceName: String
    get() = "Android ${Build.MANUFACTURER} ${Build.MODEL}"

/**
 * Derselbe Pfad, den `preferencesDataStore(name = "settings")` vorher benutzt hat:
 * `filesDir/datastore/settings.preferences_pb`. Weicht er ab, findet die App die
 * gespeicherte Anmeldung nicht mehr und jedes Gerät steht nach dem Update wieder
 * am Anmeldebildschirm.
 */
actual fun settingsFilePath(): String =
    File(appContext.filesDir, "datastore/settings.preferences_pb").absolutePath

actual fun dialNumber(number: String, letUserChooseApp: Boolean): Boolean {
    val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))

    // Mit Auswahl: fragt jedes Mal, womit angerufen wird - Telefon-App oder ein
    // Softphone wie 3CX. Ohne: die Vorgabe des Systems, sonst dessen eigene Auswahl.
    val intent = if (letUserChooseApp) Intent.createChooser(dial, "Anrufen mit …") else dial

    return start(intent)
}

actual fun composeMail(address: String, subject: String): Boolean = start(
    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
        putExtra(Intent.EXTRA_SUBJECT, subject)
    },
)

actual fun openUrl(url: String): Boolean =
    start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

private fun start(intent: Intent): Boolean = try {
    // FLAG_ACTIVITY_NEW_TASK ist Pflicht, weil hier der Anwendungskontext startet und
    // nicht die Activity. Vorher lief das über LocalContext.current, also die
    // Activity - ohne das Flag fliegt eine AndroidRuntimeException.
    appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}
