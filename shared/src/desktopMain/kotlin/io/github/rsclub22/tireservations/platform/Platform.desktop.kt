package io.github.rsclub22.tireservations.platform

import io.github.rsclub22.tireservations.data.Tagesblatt
import java.awt.Desktop
import java.awt.print.PageFormat
import java.awt.print.PrinterJob
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
 * Zeichnet das Blatt selbst und zeigt den Druckdialog des Systems.
 *
 * Kein Browser und kein Desktop.print(): Letzteres schickt eine Datei ohne Vorschau
 * und ohne Druckerauswahl an den Standarddrucker, Ersteres holt fuer einen Druck ein
 * ganzes Programm dazu. PrinterJob.printDialog() gibt dagegen genau das, was man am
 * Telefon braucht - Drucker waehlen, Seiten waehlen, drucken.
 *
 * Ein Abbruch im Dialog meldet true: nicht drucken zu wollen ist kein Fehler, und
 * eine Fehlermeldung dafuer waere nur laestig.
 */
actual fun drucke(blatt: Tagesblatt, standort: String, gedrucktAm: String, titel: String): Boolean = runCatching {
    val job = PrinterJob.getPrinterJob()
    job.setJobName(titel)

    val druck = Tagesblattdruck(blatt, standort, gedrucktAm)
    // Hochformat wie das Blatt auf dem Pi. Die sieben Spalten werden dadurch
    // enger, passen aber - lange Namen bekommen ein Auslassungszeichen.
    val format = job.defaultPage().apply { orientation = PageFormat.PORTRAIT }
    job.setPrintable(druck, format)

    if (!job.printDialog()) return true

    job.print()
    true
}.getOrDefault(false)

/**
 * Das Symbol in der System-Ablage. Wird beim ersten Melden angelegt und bleibt
 * dann stehen - jedes Mal ein neues anzulegen fuellt die Leiste.
 */
private var ablageSymbol: java.awt.TrayIcon? = null

actual fun melde(titel: String, text: String, kennung: Int): Boolean = runCatching {
    if (!java.awt.SystemTray.isSupported()) return false

    val ablage = java.awt.SystemTray.getSystemTray()
    val symbol = ablageSymbol ?: java.awt.TrayIcon(symbolbild(), "Reservierungen").also {
        it.isImageAutoSize = true
        ablage.add(it)
        ablageSymbol = it
    }

    symbol.displayMessage(titel, text, java.awt.TrayIcon.MessageType.INFO)
    true
}.getOrDefault(false)

/**
 * Ein kleines Symbol, gezeichnet statt mitgeliefert: eine Bilddatei waere die
 * einzige Ressource des Desktop-Ziels und muesste durch die Paketierung getragen
 * werden, nur fuer sechzehn Pixel.
 */
private fun symbolbild(): java.awt.Image {
    val groesse = 16
    val bild = java.awt.image.BufferedImage(groesse, groesse, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val stift = bild.createGraphics()
    stift.setRenderingHint(
        java.awt.RenderingHints.KEY_ANTIALIASING,
        java.awt.RenderingHints.VALUE_ANTIALIAS_ON,
    )
    // Das Braun des Hauses, wie im Theme.
    stift.color = java.awt.Color(0x60, 0x21, 0x0F)
    stift.fillRoundRect(1, 1, groesse - 2, groesse - 2, 5, 5)
    stift.color = java.awt.Color.WHITE
    stift.fillRect(4, 6, groesse - 8, 2)
    stift.fillRect(4, 10, groesse - 8, 2)
    stift.dispose()

    return bild
}
