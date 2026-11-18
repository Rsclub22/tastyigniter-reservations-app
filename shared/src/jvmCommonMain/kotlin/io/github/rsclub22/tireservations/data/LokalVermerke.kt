package io.github.rsclub22.tireservations.data

import java.time.LocalTime

/**
 * Sperrvermerke im lokalen Betrieb, nachgebaut aus `ClosureNotes` der
 * Server-Erweiterung reservationcontrol.
 *
 * Ein Vermerk ist eine Reservierung mit mehr Gaesten, als das Haus Plaetze hat.
 * Was er bedeutet, steht in seinem Freitext - der Betreiber will das so, ein
 * Formular waere am Telefon langsamer (siehe Memory "Freitext statt Formular").
 *
 * Uebernommen sind die Stichwoerter, die ohne Website etwas bedeuten:
 * `ganztägig` (und Verwandte), `max 30 PAX`, `17 Uhr` bzw. `17:30`. Was die
 * Online-Buchung oeffnet oder bewirbt (`ONLINE BUCHBAR:`, `GÄSTEHINWEIS:`),
 * hat hier keinen Empfaenger; der Gaestehinweis wird nur herausgeschnitten,
 * damit eine Uhrzeit darin nicht zur Buchungszeit wird.
 *
 * Die Muster sind die des Servers, Zeichen fuer Zeichen. Wer eines aendert,
 * aendert es dort zuerst - sonst liest derselbe Text auf zwei Geraeten
 * verschieden.
 */
internal object LokalVermerke {

    /** Ein Vermerk, wie ihn die Rechnung braucht. */
    data class Vermerk(val id: Long, val zeit: LocalTime, val dauer: Int, val kommentar: String)

    private val ZEIT = muster("""(?<![\d:.])(\d{1,2})(?:[:.](\d{2}))?\s*Uhr\b""")

    private val PAX = muster(
        """(?:max(?:imal)?\.?\s*(\d{1,4})\s*(?:pax|personen|pers\.?|g[äa]ste|pl[äa]tze)?|(\d{1,4})\s*pax)\b""",
    )

    private val GANZTAGS = muster(
        """ganzt[äa]gig|ganze[rn]\s+tag|online[^.!]{0,40}nicht\s*(?:mehr\s*)?(?:verf[üu]gbar|m[öo]glich|buchbar)|keine\s+online""",
    )

    private val HINWEIS = muster(
        """\bg(?:ä|ae)stehinweis[ \t]*:[ \t]*((?:(?!\bonline\s+(?:wieder\s+)?buchbar\b[ \t]*:)[^\r\n])*)""",
    )

    private val EVENTZEIT = muster(
        """(?<![\d:.])(\d{1,2})(?:[:.](\d{2}))?\s*Uhr\b|(?<![\d:.])(\d{1,2}):(\d{2})(?![\d:.])""",
    )

    /** Der Text ohne Gaestehinweis - nur der Rest steuert etwas. */
    private fun operativ(v: Vermerk): String = HINWEIS.replace(v.kommentar, "")

    /**
     * Nimmt der Vermerk den ganzen Tag ein? Ja, wenn sein Zeitfenster in die
     * Oeffnungszeit reicht, oder wenn der Text es sagt. Ein Abend um 17 Uhr
     * neben einem Mittagsbetrieb nimmt ihn nicht ein.
     */
    fun ganztags(v: Vermerk, oeffnung: Pair<LocalTime, LocalTime>?): Boolean {
        if (GANZTAGS.containsMatchIn(operativ(v))) return true
        val (auf, zu) = oeffnung ?: return false
        val start = v.zeit
        var ende = v.zeit.plusMinutes(maxOf(0, v.dauer).toLong())
        // Ueber Mitternacht: bis zum Tagesende zaehlen.
        if (!ende.isAfter(start)) ende = LocalTime.of(23, 59)
        return start < zu && ende > auf
    }

    /** Im Text genannte Uhrzeiten, aufsteigend. */
    fun zeiten(vermerke: List<Vermerk>): List<LocalTime> =
        vermerke.flatMap { v ->
            ZEIT.findAll(operativ(v).trim()).mapNotNull { uhrzeit(it.groupValues[1], it.groupValues[2]) }.toList()
        }.distinct().sorted()

    /** Hoechstzahl je Zeitfenster; bei mehreren gilt die kleinste. */
    fun maxPax(vermerke: List<Vermerk>): Int? =
        vermerke.flatMap { v ->
            PAX.findAll(operativ(v).trim()).mapNotNull { m ->
                (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull()?.takeIf { it >= 1 }
            }.toList()
        }.minOrNull()

    /**
     * Hoechstzahlen je Uhrzeit, wenn der Text sie einzeln nennt:
     * "11 Uhr max 60 PAX, 13 Uhr max 80 PAX". Eine Zahl gehoert zur Uhrzeit vor
     * ihr, und es muss aufgehen - sonst leer, und es gilt [maxPax] fuer alle.
     */
    fun maxPaxJeZeit(vermerke: List<Vermerk>): Map<LocalTime, Int> {
        val plan = sortedMapOf<LocalTime, Int>()
        for (v in vermerke) {
            val text = operativ(v).trim().takeIf { it.isNotEmpty() } ?: continue
            val zeiten = ZEIT.findAll(text).mapNotNull { m ->
                uhrzeit(m.groupValues[1], m.groupValues[2])?.let { it to m.range.first }
            }.toList()
            val zahlen = PAX.findAll(text).toList()
            if (zeiten.isEmpty() || zahlen.isEmpty()) continue

            val paare = mutableMapOf<LocalTime, Int>()
            for (m in zahlen) {
                val zahl = m.groupValues[1].ifEmpty { m.groupValues[2] }.toIntOrNull() ?: 0
                if (zahl < 1) continue
                val davor = zeiten.lastOrNull { it.second < m.range.first }?.first
                // Zahl vor der ersten Uhrzeit oder zwei an derselben: nicht zuzuordnen.
                if (davor == null || davor in paare) return emptyMap()
                paare[davor] = zahl
            }
            if (paare.size != zeiten.map { it.first }.distinct().size) return emptyMap()
            paare.forEach { (zeit, zahl) -> plan[zeit] = plan[zeit]?.let { minOf(it, zahl) } ?: zahl }
        }
        return plan
    }

    /**
     * Die Eventzeiten: im Text genannte Uhrzeiten, die im gespeicherten Fenster
     * des Vermerks liegen. Am Telefon werden sie angeboten, ohne Tisch und nach
     * Personen gezaehlt.
     */
    fun eventzeiten(vermerke: List<Vermerk>): List<LocalTime> =
        vermerke.flatMap { v ->
            val start = v.zeit.toSecondOfDay() / 60
            val ende = start + maxOf(0, v.dauer)
            // Leer oder ueber Mitternacht: daraus wird keine Zeit abgeleitet.
            if (ende <= start || ende >= 1440) return@flatMap emptyList()
            EVENTZEIT.findAll(operativ(v)).mapNotNull { m ->
                val g = m.groupValues
                if (g[1].isNotEmpty()) uhrzeit(g[1], g[2]) else uhrzeit(g[3], g[4])
            }.filter { (it.toSecondOfDay() / 60) in start until ende }.toList()
        }.distinct().sorted()

    /** Die Vermerke, die fuer eine Uhrzeit gelten: ganztaegige immer, die anderen zu ihren Zeiten. */
    fun fuerZeit(vermerke: List<Vermerk>, oeffnung: Pair<LocalTime, LocalTime>?, zeit: LocalTime): List<Vermerk> =
        vermerke.filter { v ->
            ganztags(v, oeffnung) || zeit in zeiten(listOf(v)) || zeit in eventzeiten(listOf(v))
        }

    private fun uhrzeit(stunde: String, minute: String): LocalTime? {
        val h = stunde.toIntOrNull() ?: return null
        val m = minute.ifEmpty { "0" }.toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
    }
}

/**
 * Wie `/iu` in PHP: ohne Ruecksicht auf Gross- und Kleinschreibung, Umlaute
 * eingeschlossen. Als Inline-Schalter, weil RegexOption kein UNICODE_CASE kennt
 * und "ÄSTEHINWEIS" sonst nicht auf "ästehinweis" passt.
 */
private fun muster(p: String) = Regex("(?iu)$p")
