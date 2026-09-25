package io.github.rsclub22.tireservations.platform

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Die Selbsterneuerung tauscht das Verzeichnis aus, aus dem das Programm gerade
 * laeuft. Geht dabei etwas schief, startet die Anwendung beim naechsten Mal nicht
 * mehr - und zwar auf dem Rechner, der neben dem Telefon steht. Deshalb wird hier
 * nicht nur der gute Fall geprueft, sondern vor allem, dass die schlechten Faelle
 * die vorhandene Fassung in Ruhe lassen.
 *
 * Geladen wird ueber einen echten HTTP-Server aus dem JDK statt ueber file:-URLs:
 * der Download ist der Teil, der in Wirklichkeit auch ueber HTTP laeuft.
 */
class SelbsterneuerungTest {

    @get:Rule
    val ordner = TemporaryFolder()

    private lateinit var server: HttpServer
    private lateinit var auslieferung: File

    private val basis get() = "http://127.0.0.1:${server.address.port}"

    @Before
    fun start() {
        auslieferung = ordner.newFolder("auslieferung")
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { austausch ->
            val datei = File(auslieferung, austausch.requestURI.path.trimStart('/'))
            if (datei.isFile) {
                val inhalt = datei.readBytes()
                austausch.sendResponseHeaders(200, inhalt.size.toLong())
                austausch.responseBody.use { it.write(inhalt) }
            } else {
                austausch.sendResponseHeaders(404, -1)
                austausch.responseBody.close()
            }
        }
        server.start()
    }

    @After
    fun stopp() = server.stop(0)

    /** Eine Installation, wie jpackage sie hinterlaesst: bin/ mit Starter, lib/. */
    private fun installation(name: String, inhalt: String): File {
        val wurzel = File(ordner.root, name).apply { mkdirs() }
        File(wurzel, "bin").mkdirs()
        File(wurzel, "bin/tastyigniter-reservierungen").apply {
            writeText("#!/bin/sh\necho $inhalt\n")
            setExecutable(true)
        }
        File(wurzel, "lib").mkdirs()
        File(wurzel, "lib/kennung.txt").writeText(inhalt)
        return wurzel
    }

    /** Packt eine Installation als Release-Anhang und legt die Pruefsumme daneben. */
    private fun veroeffentliche(quelle: File, pruefsumme: Boolean = true, verdreht: Boolean = false): String {
        val paket = File(auslieferung, "neu.tar.gz")
        ProcessBuilder("tar", "czf", paket.absolutePath, "-C", quelle.parentFile.path, quelle.name)
            .start().waitFor()

        if (pruefsumme) {
            val hex = MessageDigest.getInstance("SHA-256").digest(paket.readBytes())
                .joinToString("") { "%02x".format(it) }
            File(auslieferung, "neu.tar.gz.sha256").writeText(
                if (verdreht) "${"0".repeat(64)}  neu.tar.gz\n" else "$hex  neu.tar.gz\n",
            )
        }

        return "$basis/neu.tar.gz"
    }

    private fun altstaende(wurzel: File) =
        wurzel.parentFile.listFiles()!!.filter { it.name.startsWith(wurzel.name + ".alt") }

    @Test
    fun `die neue Fassung tritt an die Stelle der alten`() {
        val neu = installation("neu-quelle", "fassung-2")
        val url = veroeffentliche(neu)
        neu.deleteRecursively()

        // Der Name im Paket muss der der Installation sein - jpackage nennt beide
        // nach packageName.
        val wurzel = installation("neu-quelle", "fassung-1")

        assertNull(Selbsterneuerung.tauscheAus(url, wurzel))
        assertEquals("fassung-2", File(wurzel, "lib/kennung.txt").readText())
        assertTrue("Der Starter muss ausführbar bleiben", File(wurzel, "bin/tastyigniter-reservierungen").canExecute())
    }

    @Test
    fun `die alte Fassung bleibt zunaechst liegen und wird spaeter geraeumt`() {
        val neu = installation("app", "fassung-2")
        val url = veroeffentliche(neu)
        neu.deleteRecursively()
        val wurzel = installation("app", "fassung-1")

        assertNull(Selbsterneuerung.tauscheAus(url, wurzel))

        val alt = altstaende(wurzel)
        assertEquals("Genau ein beiseitegeschobener Stand", 1, alt.size)
        assertEquals("fassung-1", File(alt.single(), "lib/kennung.txt").readText())
    }

    @Test
    fun `eine falsche Pruefsumme laesst die Installation unberuehrt`() {
        val neu = installation("app", "fassung-2")
        val url = veroeffentliche(neu, verdreht = true)
        neu.deleteRecursively()
        val wurzel = installation("app", "fassung-1")

        val fehler = Selbsterneuerung.tauscheAus(url, wurzel)

        assertNotNull(fehler)
        assertTrue(fehler!!, fehler.contains("Prüfsumme"))
        assertEquals("fassung-1", File(wurzel, "lib/kennung.txt").readText())
        assertTrue("Nichts beiseitegeschoben", altstaende(wurzel).isEmpty())
    }

    @Test
    fun `ohne Pruefsumme wird nichts eingespielt`() {
        val neu = installation("app", "fassung-2")
        val url = veroeffentliche(neu, pruefsumme = false)
        neu.deleteRecursively()
        val wurzel = installation("app", "fassung-1")

        val fehler = Selbsterneuerung.tauscheAus(url, wurzel)

        assertNotNull(fehler)
        assertEquals("fassung-1", File(wurzel, "lib/kennung.txt").readText())
    }

    @Test
    fun `ein Paket ohne bin-Verzeichnis wird abgelehnt`() {
        val unbrauchbar = File(ordner.root, "app").apply { mkdirs() }
        File(unbrauchbar, "liesmich.txt").writeText("kein Programm")
        val url = veroeffentliche(unbrauchbar)
        unbrauchbar.deleteRecursively()
        val wurzel = installation("app", "fassung-1")

        val fehler = Selbsterneuerung.tauscheAus(url, wurzel)

        assertNotNull(fehler)
        assertTrue(fehler!!, fehler.contains("bin/"))
        assertEquals("fassung-1", File(wurzel, "lib/kennung.txt").readText())
    }

    @Test
    fun `ein fehlgeschlagener Download laesst die Installation unberuehrt`() {
        val wurzel = installation("app", "fassung-1")

        val fehler = Selbsterneuerung.tauscheAus("$basis/gibtsnicht.tar.gz", wurzel)

        assertNotNull(fehler)
        assertEquals("fassung-1", File(wurzel, "lib/kennung.txt").readText())
        assertTrue(altstaende(wurzel).isEmpty())
    }

    @Test
    fun `das Arbeitsverzeichnis bleibt nicht liegen`() {
        val neu = installation("app", "fassung-2")
        val url = veroeffentliche(neu)
        neu.deleteRecursively()
        val wurzel = installation("app", "fassung-1")

        Selbsterneuerung.tauscheAus(url, wurzel)

        val reste = ordner.root.listFiles()!!.filter { it.name.startsWith(".erneuerung-") }
        assertTrue("Übrig geblieben: $reste", reste.isEmpty())
    }
}
