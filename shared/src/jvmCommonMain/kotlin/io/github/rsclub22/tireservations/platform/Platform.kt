package io.github.rsclub22.tireservations.platform

import io.github.rsclub22.tireservations.data.Tagesblatt

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

/**
 * Kann diese Fassung sich selbst erneuern?
 *
 * Wahr nur fuer die ausgepackte Desktop-Fassung, und auch dort nur, wenn das
 * Verzeichnis beschreibbar ist. Eine ueber die Paketverwaltung installierte
 * Fassung gehoert der Paketverwaltung - da faengt die App nicht an, in /opt zu
 * schreiben. Am Telefon ist es immer falsch: Android laesst eine App fremde
 * Pakete nur mit eigener Berechtigung einspielen.
 */
expect val kannSelbstErneuern: Boolean

/**
 * Laedt das Paket, prueft die Pruefsumme, tauscht die Installation aus und
 * startet neu.
 *
 * Kehrt nur zurueck, wenn es NICHT geklappt hat - dann mit der Meldung, die dem
 * Benutzer gezeigt wird. Im Erfolgsfall laeuft schon die neue Fassung.
 */
expect suspend fun erneuereSelbst(url: String): String?

/**
 * Druckt das Tagesblatt.
 *
 * Uebergeben werden die Daten und nicht fertiges HTML, weil beide Plattformen
 * verschieden drucken: Android macht ueber WebView und PrintManager aus dem HTML
 * ein Dokument, der Desktop zeichnet das Blatt selbst mit Java2D und zeigt den
 * Druckdialog des Systems. Ein Browser oeffnet sich dabei nicht.
 *
 * @return false, wenn kein Druckweg eingerichtet ist. Ein Abbruch im Dialog ist
 *   kein Fehler und meldet true.
 */
expect fun drucke(blatt: Tagesblatt, standort: String, gedrucktAm: String, titel: String): Boolean

/**
 * Meldet sich beim Betriebssystem - Benachrichtigungsleiste am Telefon,
 * System-Ablage auf dem Desktop.
 *
 * @param kennung gleiche Kennung ersetzt eine noch stehende Meldung, statt eine
 *   zweite danebenzulegen.
 * @return false, wenn das System keine Meldungen annimmt. Unter Wayland gibt es
 *   die AWT-Ablage oft nicht, und auf Android kann die Erlaubnis fehlen; der
 *   Aufrufer weicht dann auf eine Meldung in der App aus.
 */
expect fun melde(titel: String, text: String, kennung: Int): Boolean

/**
 * Kommen Beruehrungen des Bildschirms als Mausereignisse herein?
 *
 * Auf dem Desktop ja, und das ist keine Kleinigkeit: AWT kennt keine
 * Beruehrungen. X11 - auf dem Tresenrechner XWayland - liefert sie der JVM als
 * gewoehnliche Mausereignisse, und Compose traegt darum an jedem davon
 * PointerType.Mouse ein. Compose-Foundation laesst das Ziehen am Inhalt aber
 * genau fuer diesen Typ nicht zu: eine Maus soll am Rad drehen, nicht den Inhalt
 * schieben. Ergebnis ohne Gegenmittel: Tippen geht, Wischen tut gar nichts.
 *
 * Am Telefon falsch - dort sind es echte Beruehrungen, um die sich Compose
 * selbst kuemmert.
 *
 * Siehe [io.github.rsclub22.tireservations.ui.components.mitFingerSchiebbar].
 */
expect val beruehrungKommtAlsMaus: Boolean
