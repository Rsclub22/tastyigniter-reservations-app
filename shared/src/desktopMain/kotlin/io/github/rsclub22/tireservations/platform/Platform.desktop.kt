package io.github.rsclub22.tireservations.platform

import java.awt.Desktop
import java.io.File
import java.net.URI
import java.net.URLEncoder

actual val deviceName: String
    get() = "Desktop ${System.getProperty("os.name")} (${System.getProperty("user.name")})"

/**
 * Ablage nach der Konvention des jeweiligen Systems. Windows und macOS sind noch
 * nicht als Ziel gebaut (s. CI-Matrix), stehen hier aber schon richtig - sonst
 * landet die Datei beim ersten Windows-Build im Benutzerordner.
 */
actual fun settingsFilePath(): String {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val home = System.getProperty("user.home").orEmpty()

    val basis = when {
        os.contains("win") ->
            System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: File(home, "AppData/Roaming").path

        os.contains("mac") || os.contains("darwin") ->
            File(home, "Library/Application Support").path

        else ->
            System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: File(home, ".config").path
    }

    return File(File(basis, "tastyigniter-reservierungen"), "settings.preferences_pb").absolutePath
}

/**
 * `tel:` kann auf dem Desktop fast nie jemand - dafür bräuchte es ein eingerichtetes
 * Softphone. Schlägt es fehl, meldet der Aufrufer das, und die Nummer steht ja auf
 * dem Bildschirm.
 */
actual fun dialNumber(number: String, letUserChooseApp: Boolean): Boolean =
    browse(URI("tel:" + number.filter { !it.isWhitespace() }))

actual fun composeMail(address: String, subject: String): Boolean {
    val uri = URI("mailto:$address?subject=" + encode(subject))

    return runCatching {
        val desktop = Desktop.getDesktop()
        if (!Desktop.isDesktopSupported() || !desktop.isSupported(Desktop.Action.MAIL)) {
            return browse(uri)
        }
        desktop.mail(uri)
        true
    }.getOrDefault(false)
}

actual fun openUrl(url: String): Boolean = browse(URI(url))

private fun browse(uri: URI): Boolean = runCatching {
    val desktop = Desktop.getDesktop()
    if (!Desktop.isDesktopSupported() || !desktop.isSupported(Desktop.Action.BROWSE)) return false
    desktop.browse(uri)
    true
}.getOrDefault(false)

/**
 * `mailto:` verlangt Prozent-Kodierung. URLEncoder macht daraus Formular-Kodierung
 * und schreibt "+" für das Leerzeichen - das erscheint im Betreff sonst wörtlich.
 */
private fun encode(text: String): String =
    URLEncoder.encode(text, "UTF-8").replace("+", "%20")

/**
 * Schreibt das Blatt in eine Datei und oeffnet sie im Browser - von dort geht es
 * mit Strg+P auf den Drucker.
 *
 * Bewusst nicht Desktop.print(): das schickt die Datei ohne Vorschau und ohne
 * Auswahl des Druckers direkt an den Standarddrucker. Beim Tagesblatt will man
 * vorher sehen, was kommt, und oft nur einen der Abschnitte.
 */
actual fun drucke(html: String, titel: String): Boolean = runCatching {
    val name = titel.replace(Regex("[^A-Za-z0-9_-]"), "-").take(40).ifBlank { "tagesblatt" }
    val datei = File.createTempFile("$name-", ".html")
    datei.deleteOnExit()
    datei.writeText(html, Charsets.UTF_8)

    browse(datei.toURI())
}.getOrDefault(false)
