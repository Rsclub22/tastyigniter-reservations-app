package io.github.rsclub22.tireservations.platform

/**
 * Das Wenige, was Android und Desktop unterschiedlich machen müssen.
 *
 * Bewusst flach gehalten: fünf Deklarationen, keine Abstraktionsschicht. Alles
 * andere - Datenlayer, Bildschirme, Navigation, ViewModels - ist bei beiden Zielen
 * derselbe Code, weil beide auf der JVM laufen.
 */

/** Name, unter dem das Gerät beim Anmelden als Token-Name auf dem Server steht. */
expect val deviceName: String

/** Ablageort der DataStore-Datei mit den Einstellungen. */
expect fun settingsFilePath(): String

/**
 * Wählt eine Rufnummer.
 *
 * @param letUserChooseApp erzwingt die Auswahl der App - am Telefon etwa zwischen
 *   der Telefon-App und einem Softphone wie 3CX. Auf dem Desktop ohne Bedeutung.
 * @return false, wenn nichts dafür eingerichtet ist; der Aufrufer meldet es dann.
 */
expect fun dialNumber(number: String, letUserChooseApp: Boolean = false): Boolean

/** Öffnet den Mail-Verfasser. Gibt false zurück, wenn kein Mail-Programm da ist. */
expect fun composeMail(address: String, subject: String): Boolean

/** Öffnet eine Adresse im Browser. Gibt false zurück, wenn keiner gefunden wurde. */
expect fun openUrl(url: String): Boolean
