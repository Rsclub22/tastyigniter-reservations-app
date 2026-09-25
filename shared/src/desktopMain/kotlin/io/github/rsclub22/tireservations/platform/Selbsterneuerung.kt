package io.github.rsclub22.tireservations.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Die Desktop-Fassung erneuert sich selbst: Paket laden, Pruefsumme pruefen,
 * Verzeichnis tauschen, neu starten.
 *
 * Das geht nur bei der ausgepackten Fassung. Wer sie ueber das .deb installiert
 * hat, bekommt sie von der Paketverwaltung - dort hat die App nichts zu suchen,
 * und ohne Schreibrecht in /opt koennte sie es ohnehin nicht.
 *
 * Getauscht wird ueber Umbenennen und nicht durch Ueberschreiben: unter Linux
 * laesst sich ein laufendes Verzeichnis umbenennen, und ein Umbenennen ist
 * entweder ganz passiert oder gar nicht. Ein halb ueberschriebenes Verzeichnis
 * waere dagegen eine Fassung, die nicht mehr startet.
 */
internal object Selbsterneuerung {

    /** Dateiendung der alten Fassung, die nach dem Tausch stehen bleibt. */
    private const val ALT = ".alt"

    /** So lange wird dem Nachfolger zugesehen, bevor dieser Prozess geht. */
    private const val START_WARTEZEIT_S = 4L

    /**
     * Das Verzeichnis der Installation - also das mit bin/ und lib/ darin.
     *
     * jpackage setzt `jpackage.app-path` auf den Starter. Fehlt die Angabe (Start
     * aus der Entwicklungsumgebung), wird ueber den Ablageort der eigenen Klassen
     * gesucht: die liegen als jar-Dateien im Verzeichnis lib/app.
     */
    fun wurzel(): File? {
        System.getProperty("jpackage.app-path")?.let { pfad ->
            // …/tastyigniter-reservierungen/bin/tastyigniter-reservierungen
            return File(pfad).parentFile?.parentFile
        }

        val quelle = runCatching {
            File(javaClass.protectionDomain.codeSource.location.toURI())
        }.getOrNull() ?: return null

        // …/wurzel/lib/app/beliebige.jar
        val app = quelle.parentFile ?: return null
        if (app.name != "app") return null

        return app.parentFile?.parentFile
    }

    /** Der Starter, der nach dem Tausch wieder hochgefahren wird. */
    private fun starter(wurzel: File): File? =
        File(wurzel, "bin").listFiles()?.firstOrNull { it.canExecute() }

    val moeglich: Boolean
        get() {
            val wurzel = wurzel() ?: return false

            // Gebraucht wird Schreibrecht im Elternverzeichnis (dort wird das Neue
            // ausgepackt und umbenannt), nicht im Verzeichnis selbst.
            return File(wurzel, "bin").isDirectory &&
                wurzel.parentFile?.canWrite() == true &&
                starter(wurzel) != null
        }

    /**
     * Fuehrt die Erneuerung aus. Kehrt nur im Fehlerfall zurueck - sonst laeuft
     * bereits die neue Fassung und dieser Prozess ist beendet.
     */
    suspend fun fuehreAus(url: String): String? = withContext(Dispatchers.IO) {
        val wurzel = wurzel() ?: return@withContext "Das Programmverzeichnis ließ sich nicht bestimmen."

        tauscheAus(url, wurzel)?.let { return@withContext it }

        val starter = starter(wurzel)
            ?: return@withContext "Nach dem Tausch war kein Starter zu finden."

        starteNeu(starter, wurzel)?.let { return@withContext it }

        exitProcess(0)
    }

    /**
     * Startet die neue Fassung und kehrt erst zurueck, wenn sie wirklich laeuft.
     *
     * Zwei Dinge gehen hier sonst schief, und beide sahen in der Probe gleich aus:
     * die Anwendung war weg und kam nicht wieder.
     *
     * Erstens die Sitzung. Ein einfach abgespaltener Nachfolger bleibt in der
     * Sitzung des Elternprozesses und geht mit ihm unter - je nachdem, woraus die
     * Anwendung gestartet wurde. `setsid` gibt ihm eine eigene.
     *
     * Zweitens die Eile. Die Uebergabe an das neue Programm laeuft ueber eine
     * Pipe zum Elternprozess; verschwindet der im selben Atemzug, kommt der
     * Nachfolger nicht mehr dazu, sich zu ersetzen. Deshalb wird gewartet, bis er
     * steht - und wenn er es nicht tut, wird das gesagt, statt sich wortlos zu
     * beenden.
     *
     * @return Fehlermeldung, oder null wenn der Nachfolger laeuft
     */
    private fun starteNeu(starter: File, wurzel: File): String? {
        // Erst mit eigener Sitzung; gibt es setsid nicht, eben ohne.
        val versuche = listOf(
            listOf("setsid", starter.absolutePath),
            listOf(starter.absolutePath),
        )

        for (befehl in versuche) {
            val kind = runCatching {
                ProcessBuilder(befehl)
                    .directory(wurzel)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            }.getOrNull() ?: continue

            // Laeuft nach der Wartezeit noch: gut, das ist die neue Fassung.
            if (!kind.waitFor(START_WARTEZEIT_S, TimeUnit.SECONDS)) return null

            // setsid spaltet ab und beendet sich dann selbst - auch das ist Erfolg.
            if (befehl.first() == "setsid" && kind.exitValue() == 0) return null
        }

        return "Die neue Fassung ist eingespielt, ließ sich aber nicht starten. " +
            "Bitte das Programm von Hand starten."
    }

    /**
     * Alles bis zum Tausch: laden, pruefen, auspacken, umbenennen. Ohne Neustart,
     * damit sich der riskante Teil pruefen laesst, ohne die eigene JVM zu beenden.
     *
     * @return Fehlermeldung, oder null wenn [wurzel] jetzt die neue Fassung ist
     */
    internal fun tauscheAus(url: String, wurzel: File): String? {
        val eltern = wurzel.parentFile ?: return "Das Programmverzeichnis hat kein Elternverzeichnis."
        if (!eltern.canWrite()) {
            return "Kein Schreibrecht in ${eltern.path} – diese Fassung muss über die Paketverwaltung erneuert werden."
        }

        val arbeit = File(eltern, ".erneuerung-${System.currentTimeMillis()}")
        if (!arbeit.mkdirs()) return "Das Arbeitsverzeichnis ließ sich nicht anlegen."

        return try {
            val paket = File(arbeit, "paket.tar.gz")
            lade(url, paket)?.let { return it }

            // Die Pruefsumme liegt als eigener Anhang neben dem Paket. Fehlt sie,
            // wird nicht ausgepackt: hier wird Programmcode eingespielt, und ein
            // abgebrochener Download sieht sonst aus wie eine neue Fassung.
            val summe = File(arbeit, "paket.sha256")
            if (lade("$url.sha256", summe) != null) {
                return "Zu diesem Paket gibt es keine Prüfsumme – es wird nicht eingespielt."
            }
            pruefe(paket, summe)?.let { return it }

            val ziel = File(arbeit, "neu").apply { mkdirs() }
            packeAus(paket, ziel)?.let { return it }

            val neu = ziel.listFiles()?.singleOrNull { it.isDirectory }
                ?: return "Das Paket hat einen unerwarteten Aufbau."
            if (!File(neu, "bin").isDirectory) {
                return "Im Paket fehlt das Verzeichnis bin/."
            }

            tausche(wurzel, neu)
        } catch (e: Exception) {
            "Die Erneuerung ist fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            arbeit.deleteRecursively()
        }
    }

    /** @return Fehlermeldung oder null */
    private fun lade(url: String, ziel: File): String? = runCatching {
        val verbindung = URI(url).toURL().openConnection().apply {
            setRequestProperty("User-Agent", "TIReservations-Desktop")
            connectTimeout = 30_000
            readTimeout = 120_000
        }

        verbindung.getInputStream().use { quelle ->
            ziel.outputStream().use { quelle.copyTo(it) }
        }

        if (ziel.length() == 0L) "Die geladene Datei ist leer." else null
    }.getOrElse { "Der Download ist fehlgeschlagen: ${it.message ?: it.javaClass.simpleName}" }

    /** @return Fehlermeldung oder null */
    private fun pruefe(paket: File, summendatei: File): String? {
        // Die Datei enthaelt "<hex>  <dateiname>", wie sha256sum sie schreibt.
        val erwartet = summendatei.readText().trim().substringBefore(' ').lowercase()
        if (erwartet.length != 64) return "Die Prüfsumme ist unlesbar."

        val digest = MessageDigest.getInstance("SHA-256")
        paket.inputStream().use { strom ->
            val puffer = ByteArray(1 shl 16)
            while (true) {
                val gelesen = strom.read(puffer)
                if (gelesen <= 0) break
                digest.update(puffer, 0, gelesen)
            }
        }

        val tatsaechlich = digest.digest().joinToString("") { "%02x".format(it) }

        return if (tatsaechlich == erwartet) {
            null
        } else {
            "Die Prüfsumme stimmt nicht – das Paket wird nicht eingespielt."
        }
    }

    /**
     * Auspacken ueber tar. Das JDK bringt keinen tar-Leser mit, und tar gibt es
     * auf jedem System, auf dem diese Fassung laeuft.
     */
    private fun packeAus(paket: File, ziel: File): String? = runCatching {
        val prozess = ProcessBuilder("tar", "xzf", paket.absolutePath, "-C", ziel.absolutePath)
            .redirectErrorStream(true)
            .start()

        if (!prozess.waitFor(5, TimeUnit.MINUTES)) {
            prozess.destroyForcibly()
            return "Das Auspacken hat zu lange gedauert."
        }

        val ausgabe = prozess.inputStream.bufferedReader().readText().trim()

        if (prozess.exitValue() == 0) null else "Das Auspacken ist fehlgeschlagen: $ausgabe"
    }.getOrElse { "tar ließ sich nicht aufrufen: ${it.message ?: it.javaClass.simpleName}" }

    /**
     * Alt beiseite, neu an seine Stelle. Geht der zweite Schritt schief, wird der
     * erste zurueckgenommen - sonst stuende die Anwendung ohne Verzeichnis da.
     */
    private fun tausche(wurzel: File, neu: File): String? {
        val beiseite = File(wurzel.parentFile, wurzel.name + ALT + "-" + System.currentTimeMillis())

        if (!wurzel.renameTo(beiseite)) {
            return "Die bisherige Fassung ließ sich nicht beiseiteschieben."
        }

        if (!neu.renameTo(wurzel)) {
            beiseite.renameTo(wurzel)
            return "Die neue Fassung ließ sich nicht an ihren Platz bringen."
        }

        return null
    }

    /**
     * Raeumt auf, was ein frueherer Tausch stehen gelassen hat.
     *
     * Beim Tausch selbst laeuft die alte Fassung noch aus diesem Verzeichnis; sie
     * kann es nicht loeschen, ohne sich den Boden unter den Fuessen wegzuziehen.
     * Also erledigt es der naechste Start.
     */
    fun raeumeAuf() {
        val wurzel = wurzel() ?: return
        val eltern = wurzel.parentFile ?: return

        eltern.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(wurzel.name + ALT) }
            ?.forEach { runCatching { it.deleteRecursively() } }
    }
}

actual val kannSelbstErneuern: Boolean get() = Selbsterneuerung.moeglich

actual suspend fun erneuereSelbst(url: String): String? = Selbsterneuerung.fuehreAus(url)
