package io.github.rsclub22.tireservations.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import java.lang.ref.WeakReference

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

/**
 * Die gerade sichtbare Activity.
 *
 * Nur fuers Drucken: PrintManager zeigt eine Oberflaeche und verlangt deshalb eine
 * Activity, der Anwendungskontext genuegt ihm nicht. Als WeakReference gehalten,
 * damit eine beendete Activity nicht am Leben bleibt.
 */
private var aktiveActivity: WeakReference<Activity>? = null

fun setzeAktiveActivity(activity: Activity?) {
    aktiveActivity = activity?.let { WeakReference(it) }
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

/**
 * Druckt ueber die Systemfunktion von Android: eine WebView laedt das Blatt, der
 * PrintManager macht daraus ein Dokument mit Vorschau und Druckerauswahl.
 *
 * Laeuft auf dem Hauptfaden - WebView laesst sich nirgendwo sonst erzeugen - und
 * braucht eine Activity, siehe [setzeAktiveActivity].
 */
actual fun drucke(html: String, titel: String): Boolean {
    val activity = aktiveActivity?.get() ?: return false

    Handler(Looper.getMainLooper()).post {
        val web = WebView(activity)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val dienst = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
                dienst.print(
                    titel,
                    view.createPrintDocumentAdapter(titel),
                    PrintAttributes.Builder().build(),
                )
            }
        }
        // Ohne Basis-URL: das Blatt ist in sich geschlossen, es wird nichts nachgeladen.
        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    return true
}
