package io.github.rsclub22.tireservations.platform

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import io.github.rsclub22.tireservations.data.Druckblatt
import io.github.rsclub22.tireservations.data.Tagesblatt
import androidx.core.app.NotificationManagerCompat
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
/**
 * Am Telefon nie: das Einspielen eines Pakets verlangt REQUEST_INSTALL_PACKAGES
 * und fuehrt durch einen Systemdialog. Die App bleibt deshalb beim Download im
 * Browser, den der Benutzer selbst oeffnet.
 */
actual val kannSelbstErneuern: Boolean = false

actual suspend fun erneuereSelbst(url: String): String? =
    "Auf Android muss das Paket von Hand installiert werden."

actual fun drucke(blatt: Tagesblatt, standort: String, gedrucktAm: String, titel: String): Boolean {
    val activity = aktiveActivity?.get() ?: return false
    val html = Druckblatt.html(blatt, standort, gedrucktAm)

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

/** Ein eigener Kanal, damit sich die Meldungen im System einzeln abschalten lassen. */
private const val KANAL = "neue-reservierungen"

actual fun melde(titel: String, text: String, kennung: Int): Boolean = runCatching {
    val verwalter = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        ?: return false

    if (verwalter.getNotificationChannel(KANAL) == null) {
        verwalter.createNotificationChannel(
            NotificationChannel(KANAL, "Neue Reservierungen", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Meldet Reservierungen, die über das Formular hereinkommen und noch zu bestätigen sind."
            },
        )
    }

    // Ein Tippen oeffnet die App. Ohne das fuehrt die Meldung ins Leere.
    val oeffnen = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
        ?.let {
            PendingIntent.getActivity(
                appContext, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

    val meldung = NotificationCompat.Builder(appContext, KANAL)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(titel)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setAutoCancel(true)
        .apply { oeffnen?.let { setContentIntent(it) } }
        .build()

    // Ab Android 13 braucht es die Erlaubnis POST_NOTIFICATIONS. Fehlt sie, verwirft
    // das System die Meldung stillschweigend - deshalb hier nachsehen und dem
    // Aufrufer die Wahrheit sagen, damit er auf eine Meldung in der App ausweicht.
    if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
        return false
    }

    NotificationManagerCompat.from(appContext).notify(kennung, meldung)
    true
}.getOrDefault(false)

actual val beruehrungKommtAlsMaus: Boolean = false
