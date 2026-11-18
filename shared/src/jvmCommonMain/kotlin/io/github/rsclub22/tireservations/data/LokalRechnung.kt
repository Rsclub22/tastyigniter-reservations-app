package io.github.rsclub22.tireservations.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Die Rechnungen der Telefonannahme im lokalen Betrieb, nachgebaut aus
 * `DayData`, `TableAllocator`, `Rooms` und `Intake` der Server-Erweiterung.
 *
 * Bewusst ohne Speicher und ohne Uhr: der Stand kommt herein, das Ergebnis geht
 * hinaus. So laesst sich jeder Fall aus dem Betrieb als Test nachstellen.
 *
 * Abweichungen vom Server, damit niemand sie fuer Versehen haelt:
 * - Keine Mindestvorlaufzeit und kein Buchungshorizont - die gelten dort nur
 *   online, und online gibt es hier nicht.
 * - Ein gesperrter Tag hat keine Zeitfenster, wie auf dem Server. Er ist hier
 *   also schlicht "zu", nicht nur "online zu".
 * - Kein Wechselpuffer zwischen zwei Belegungen (Vorgabe des Servers ist 0).
 */
internal class LokalRechnung(
    private val stamm: LokalStammdaten,
    /** Alle gespeicherten Reservierungen; was nicht zaehlt, filtert die Rechnung selbst. */
    private val alle: List<LokalReservierung>,
    private val sperrtage: Map<String, String>,
) {

    // --- Tag ------------------------------------------------------------------------------

    /** Was `GET intern/tag` liefert. [jetzt] nur, um vergangene Zeiten von heute wegzulassen. */
    fun tag(datum: LocalDate, gaeste: Int, raumId: Long?, jetzt: LocalDateTime): Tagesdaten {
        val raum = stamm.raeume.firstOrNull { it.id == raumId }
        val oeffnung = stamm.oeffnung(datum.dayOfWeek)
        val vermerke = vermerke(datum)
        val vermerkZeiten = LokalVermerke.zeiten(vermerke)
        val maxPax = LokalVermerke.maxPax(vermerke)
        val paxJeZeit = LokalVermerke.maxPaxJeZeit(vermerke)
        val ganztags = vermerke.filter { LokalVermerke.ganztags(it, oeffnung) }
        val eventzeiten = LokalVermerke.eventzeiten(vermerke)
        val zeitfenster = zeitfenster(datum, gaeste, jetzt)
        val reservierungen = belegend(datum)

        val belegung: List<Zeitfenster> = if (raum == null && ganztags.isNotEmpty()) {
            // Ein Vermerk liegt ueber dem Tag: alle Tische sind belegt, am Telefon wird
            // trotzdem angenommen, nur ohne Tisch. Nennt er Zeiten, gelten nur die.
            (vermerkZeiten.ifEmpty { zeitfenster }).map { personenZeile(it, maxPax, paxJeZeit, datum, gaeste) }
        } else {
            val amTelefon = if (raum == null) eventzeiten else emptyList()
            val zeilen = zeitfenster.map { zeit ->
                when {
                    zeit in amTelefon -> personenZeile(zeit, maxPax, paxJeZeit, datum, gaeste)
                    raum != null -> {
                        val frei = raumFrei(raum.id, datum.atTime(zeit), reservierungen)
                        Zeitfenster(zeit, if (frei) 1 else 0, 1, 0, frei, 0, raum.name, false, null, 0)
                    }
                    else -> {
                        val frei = freieTische(datum.atTime(zeit), stamm.aufenthaltMinuten, reservierungen)
                        val freiEinzeln = frei.filter { it.aus.isEmpty() }
                        Zeitfenster(
                            zeit = zeit,
                            frei = freiEinzeln.size,
                            gesamt = stamm.einzeltische.size,
                            freiePlaetze = freiEinzeln.sumOf { it.max },
                            passt = waehle(frei, gaeste) != null,
                            groesster = frei.maxOfOrNull { it.max } ?: 0,
                            raum = null,
                            ohneTisch = false,
                            paxMax = null,
                            paxBelegt = 0,
                        )
                    }
                }
            }
            // Eventzeiten, die der Wochenplan nicht selbst anbietet: der Maerchenabend
            // um 17 Uhr neben dem Mittagstisch.
            val dazu = amTelefon.filter { z -> zeilen.none { it.zeit == z } }
                .map { personenZeile(it, maxPax, paxJeZeit, datum, gaeste) }
            (zeilen + dazu).sortedBy { it.zeit }
        }

        return Tagesdaten(
            datum = datum,
            gaeste = gaeste,
            raum = raum?.alsRaum(),
            gesperrt = datum.toString() in sperrtage,
            grund = sperrtage[datum.toString()].orEmpty(),
            sperren = kommendeSperren(jetzt.toLocalDate()),
            trennzeit = LokalStammdaten.zeitOderNull(stamm.trennzeit),
            tischeGesamt = stamm.einzeltische.size,
            plaetzeGesamt = stamm.einzeltische.sumOf { it.max },
            belegung = belegung,
            raeume = stamm.raeume.sortedBy { it.name }.map { it.alsRaum() },
            vermerke = vermerke.map { v ->
                Sperrvermerk(v.id, v.zeit, vermerkGaeste(v.id), v.kommentar, LokalVermerke.ganztags(v, oeffnung))
            },
            vermerkZeiten = if (ganztags.isNotEmpty()) vermerkZeiten else eventzeiten,
            maxPax = maxPax,
            paxJeZeit = paxJeZeit.mapKeys { (z, _) -> z.toString() },
            hausgroesse = stamm.hausgroesse,
            // Mit den Vermerken: die Liste zeigt sie, gezaehlt werden sie nicht.
            reservierungen = alle.filter { it.datum == datum.toString() && it.statusId != STORNIERT }
                .sortedWith(compareBy({ it.zeit }, { it.id }))
                .map { alsIntern(it) },
        )
    }

    // --- Tagesblatt und Monat -------------------------------------------------------------

    /**
     * Was `GET intern/tagesblatt` liefert, wie `DailySheet`. Ein Zeitraum laesst
     * leere Tage weg; ein Tag mit Sperrvermerk kommt mit, der Vermerk ist dann
     * genau die Nachricht.
     *
     * @param trennzeit wie der Server: null fuer die eigene Vorgabe, "aus" fuer ein Blatt.
     */
    fun tagesblatt(von: LocalDate, bis: LocalDate, trennzeit: String?): Tagesblatt {
        val teilung = teilung(trennzeit)
        // Verdreht eingegeben: verstehen statt ablehnen.
        val (anfang, ende0) = if (bis < von) bis to von else von to bis
        val ende = minOf(ende0, anfang.plusDays(MAX_TAGE - 1))
        val zeitraum = anfang != ende
        val tage = generateSequence(anfang) { it.plusDays(1) }.takeWhile { it <= ende }
            .map { blattTag(it, teilung) }
            .filter { !zeitraum || it.blaetter.isNotEmpty() || it.sperrvermerke.isNotEmpty() }
            .toList()
        return Tagesblatt(anfang, ende, zeitraum, teilung, tage)
    }

    private fun blattTag(datum: LocalDate, teilung: LocalTime?): BlattTag {
        // Anders als die Belegung mit allem, was nicht storniert ist: eine
        // Reservierung, die niemand sieht, ist genau der Fall, den das Blatt
        // verhindern soll.
        val alleDesTages = alle.filter { it.datum == datum.toString() && it.statusId != STORNIERT }
            .sortedWith(compareBy({ it.zeit }, { it.id }))
        val (vermerkEintraege, gaeste) = alleDesTages.partition { istVermerk(it) }
        val vermerke = vermerke(datum)
        val oeffnung = stamm.oeffnung(datum.dayOfWeek)
        return BlattTag(
            datum = datum,
            gesperrt = datum.toString() in sperrtage,
            grund = sperrtage[datum.toString()].orEmpty(),
            maxPax = LokalVermerke.maxPax(vermerke),
            paxJeZeit = LokalVermerke.maxPaxJeZeit(vermerke).mapKeys { (z, _) -> z.toString() },
            sperrvermerke = vermerkEintraege.mapNotNull { r ->
                vermerke.firstOrNull { it.id == r.id }?.let { v ->
                    Sperrvermerk(v.id, v.zeit, r.gaeste, v.kommentar, LokalVermerke.ganztags(v, oeffnung))
                }
            },
            blaetter = blaetter(gaeste, teilung),
        )
    }

    private fun blaetter(gaeste: List<LokalReservierung>, teilung: LocalTime?): List<Blatt> {
        fun blatt(titel: String, r: List<LokalReservierung>) = Blatt(titel, r.sumOf { it.gaeste }, r.map(::alsIntern))
        if (teilung == null) return if (gaeste.isEmpty()) emptyList() else listOf(blatt("Ganzer Tag", gaeste))
        val (vorher, nachher) = gaeste.partition { (LokalStammdaten.zeitOderNull(it.zeit) ?: LocalTime.MIDNIGHT) < teilung }
        val z = teilung.toString()
        return listOf(blatt("Bis $z Uhr", vorher), blatt("Ab $z Uhr", nachher)).filter { it.reservierungen.isNotEmpty() }
    }

    /** Wie `DayData::resolveSplitTime`: leer oder "aus" heisst ein Blatt, Unlesbares die Vorgabe. */
    private fun teilung(eingabe: String?): LocalTime? {
        val text = (eingabe ?: stamm.trennzeit ?: "").trim()
        if (text.isEmpty() || text.lowercase() in setOf("aus", "off")) return null
        val m = Regex("""^(\d{1,2}):(\d{2})$""").find(text) ?: return STANDARD_TRENNZEIT
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h <= 23 && min <= 59) LocalTime.of(h, min) else STANDARD_TRENNZEIT
    }

    /**
     * Was `GET intern/monat` liefert: jeden Tag des Monats, auch die leeren, damit
     * das Raster ohne Luecken steht. Vermerke zaehlen nicht als Gaeste.
     */
    fun monat(jahr: Int, monat: Int): Monatsuebersicht {
        val von = LocalDate.of(jahr, monat, 1)
        val bis = von.withDayOfMonth(von.lengthOfMonth())
        val tage = generateSequence(von) { it.plusDays(1) }.takeWhile { it <= bis }.map { datum ->
            val desTages = alle.filter { it.datum == datum.toString() && it.statusId != STORNIERT }
            val (vermerkEintraege, gaeste) = desTages.partition { istVermerk(it) }
            Monatstag(
                datum = datum,
                reservierungen = gaeste.size,
                gaeste = gaeste.sumOf { it.gaeste },
                gesperrt = datum.toString() in sperrtage,
                grund = sperrtage[datum.toString()].orEmpty(),
                vermerk = vermerkEintraege.isNotEmpty(),
                vermerkText = vermerkEintraege.minByOrNull { it.id }?.kommentar.orEmpty(),
                maxPax = LokalVermerke.maxPax(vermerke(datum)),
            )
        }.toList()
        return Monatsuebersicht(jahr, monat, von, bis, tage.maxOfOrNull { it.gaeste } ?: 0, tage)
    }

    /** Sperrtage ab heute, wie `BlockedDates::upcoming()`. */
    fun kommendeSperren(heute: LocalDate): Map<String, String> =
        sperrtage.filterKeys { it >= heute.toString() }.toSortedMap()

    // --- Annahme --------------------------------------------------------------------------

    /**
     * Steht eine Hoechstzahl aus einem Sperrvermerk der Annahme im Weg? Dann die
     * Meldung in Worten, sonst null. Wortgleich mit dem Server.
     */
    fun hoechstzahlVerletzt(datum: LocalDate, zeit: LocalTime, gaeste: Int): String? {
        val gelten = LokalVermerke.fuerZeit(vermerke(datum), stamm.oeffnung(datum.dayOfWeek), zeit)
        val max = LokalVermerke.maxPaxJeZeit(gelten)[zeit] ?: LokalVermerke.maxPax(gelten) ?: return null
        val schon = personenJeZeit(datum)[zeit] ?: 0
        if (schon + gaeste <= max) return null
        val z = zeit.toString()
        return "Um $z Uhr sind bereits $schon von $max Plätzen vergeben – $gaeste weitere passen nicht mehr. " +
            "Frei sind noch ${maxOf(0, max - schon)}."
    }

    /**
     * Tische fuer eine Telefonannahme, wie `Intake::create`: ausdruecklich ohne
     * Tisch, oder ohne Tisch, wenn zu dieser Zeit ein Vermerk gilt (dann verteilt
     * der Tischplan auf Papier), sonst der Raum, sonst die automatische Wahl.
     */
    fun tischeFuerAnnahme(e: Annahmeentwurf): List<Long> {
        val raum = stamm.raeume.firstOrNull { it.id == e.raumId }
        val gelten = LokalVermerke.fuerZeit(vermerke(e.datum), stamm.oeffnung(e.datum.dayOfWeek), e.zeit)
        return when {
            e.ohneTisch -> emptyList()
            raum == null && gelten.isNotEmpty() -> emptyList()
            raum != null -> listOf(raum.id)
            else -> automatisch(e.datum, e.zeit, stamm.aufenthaltMinuten, e.gaeste, ignoriere = null)
        }
    }

    /**
     * Die automatische Tischwahl beim Anlegen ueber das Formular, wie der
     * Beobachter in `Extension.php`: nicht fuer Sperrvermerke, nicht zu Zeiten
     * eines Vermerks (dort verteilt der Tischplan) und nicht fuer Gesellschaften.
     */
    fun tischeFuerFormular(d: ReservationDraft, ignoriere: Long?): List<Long> {
        if (d.tableIds.isNotEmpty()) return d.tableIds
        val hausgroesse = stamm.hausgroesse
        if (hausgroesse > 0 && d.guestNum > hausgroesse) return emptyList()
        if (d.guestNum >= stamm.grossAb) return emptyList()
        val zeit = d.time.withSecond(0).withNano(0)
        if (LokalVermerke.fuerZeit(vermerke(d.date), stamm.oeffnung(d.date.dayOfWeek), zeit).isNotEmpty()) return emptyList()
        return automatisch(d.date, zeit, d.duration ?: stamm.aufenthaltMinuten, d.guestNum, ignoriere)
    }

    private fun automatisch(datum: LocalDate, zeit: LocalTime, dauer: Int, gaeste: Int, ignoriere: Long?): List<Long> {
        val andere = belegend(datum).filter { it.id != ignoriere }
        return listOfNotNull(waehle(freieTische(datum.atTime(zeit), dauer, andere), maxOf(1, gaeste))?.id)
    }

    // --- Zeitfenster ----------------------------------------------------------------------

    /**
     * Die Uhrzeiten des Tages im Raster: ab Oeffnung, vor Schliessung. Gesellschaften
     * ab [LokalStammdaten.grossAb] bekommen das grosse Fenster, aber nur an Tagen,
     * an denen ueberhaupt geoeffnet ist. Heute ohne die schon vergangenen.
     */
    fun zeitfenster(datum: LocalDate, gaeste: Int, jetzt: LocalDateTime): List<LocalTime> {
        if (datum.toString() in sperrtage) return emptyList()
        val normal = stamm.oeffnung(datum.dayOfWeek) ?: return emptyList()
        val (von, bis) = if (gaeste >= stamm.grossAb) {
            val gv = LokalStammdaten.zeitOderNull(stamm.grossVon)
            val gb = LokalStammdaten.zeitOderNull(stamm.grossBis)
            if (gv != null && gb != null) gv to gb else normal
        } else {
            normal
        }
        val raster = stamm.rasterMinuten.coerceIn(5, 240).toLong()
        // Schliessung nach Mitternacht: bis Tagesende.
        val ende = if (bis.isAfter(von)) von.toSecondOfDay().toLong() to bis.toSecondOfDay().toLong()
        else von.toSecondOfDay().toLong() to 86_400L
        return generateSequence(ende.first) { it + raster * 60 }
            .takeWhile { it < ende.second }
            .map { LocalTime.ofSecondOfDay(it) }
            .filter { datum != jetzt.toLocalDate() || it.isAfter(jetzt.toLocalTime()) }
            .toList()
    }

    // --- Tische ---------------------------------------------------------------------------

    /** Freie Tische zu einem Zeitpunkt, Kombinationen eingeschlossen. */
    fun freieTische(ab: LocalDateTime, dauer: Int, reservierungen: List<LokalReservierung>): List<LokalTisch> {
        val bis = ab.plusMinutes(maxOf(1, dauer).toLong())
        // Auf dem Server haengen am Vermerk alle Tische, und so sperrt er sein
        // Fenster. Hier traegt er keine; dieselbe Wirkung also von Hand.
        val vermerkDazwischen = alle.any { v ->
            v.datum == ab.toLocalDate().toString() && v.statusId != STORNIERT && istVermerk(v) &&
                ab < ende(v) && bis > beginn(v)
        }
        if (vermerkDazwischen) return emptyList()
        val belegt = reservierungen
            .filter { r -> ab < ende(r) && bis > beginn(r) }
            .flatMap { it.tische }
            .toSet()
        val mitVerwandten = verwandte(belegt)
        return stamm.tische.filter { it.id !in mitVerwandten }
    }

    /**
     * Wer die Kombination belegt, belegt ihre Tische; wer einen Tisch belegt, jede
     * Kombination, in der er steckt. Auf dem Server sind das Eltern und Kinder.
     */
    private fun verwandte(ids: Set<Long>): Set<Long> {
        val mehr = ids.toMutableSet()
        for (t in stamm.tische) {
            if (t.id in ids) mehr += t.aus
            if (t.aus.any { it in ids }) mehr += t.id
        }
        return mehr
    }

    /** Kleinster passender Einzeltisch, erst wenn keiner reicht, die kleinste passende Kombination. */
    fun waehle(frei: List<LokalTisch>, gaeste: Int): LokalTisch? {
        val passend = frei.filter { it.min <= gaeste && it.max >= gaeste }
        return passend.filter { it.aus.isEmpty() }.minByOrNull { it.max }
            ?: passend.filter { it.aus.isNotEmpty() }.minByOrNull { it.max }
    }

    private fun raumFrei(raumId: Long, um: LocalDateTime, reservierungen: List<LokalReservierung>): Boolean =
        reservierungen.none { r -> raumId in r.tische && um >= beginn(r) && um < ende(r) }

    // --- Reservierungen des Tages ---------------------------------------------------------

    /** Was zur Belegung zaehlt: nicht storniert, kein Sperrvermerk. */
    private fun belegend(datum: LocalDate): List<LokalReservierung> =
        alle.filter { it.datum == datum.toString() && it.statusId != STORNIERT && !istVermerk(it) }
            .sortedWith(compareBy({ it.zeit }, { it.id }))

    fun vermerke(datum: LocalDate): List<LokalVermerke.Vermerk> =
        alle.filter { it.datum == datum.toString() && it.statusId != STORNIERT && istVermerk(it) }
            .sortedBy { it.zeit }
            .mapNotNull { r ->
                LokalStammdaten.zeitOderNull(r.zeit)?.let { LokalVermerke.Vermerk(r.id, it, dauer(r), r.kommentar) }
            }

    fun istVermerk(r: LokalReservierung): Boolean = stamm.hausgroesse > 0 && r.gaeste > stamm.hausgroesse

    /** Personen je Uhrzeit, Raeume eingeschlossen: die Kueche unterscheidet nicht, wo jemand sitzt. */
    private fun personenJeZeit(datum: LocalDate): Map<LocalTime, Int> =
        belegend(datum).mapNotNull { r -> LokalStammdaten.zeitOderNull(r.zeit)?.let { it to r.gaeste } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, g) -> g.sum() }

    private fun personenZeile(
        zeit: LocalTime,
        maxPax: Int?,
        paxJeZeit: Map<LocalTime, Int>,
        datum: LocalDate,
        gaeste: Int,
    ): Zeitfenster {
        val schon = personenJeZeit(datum)[zeit] ?: 0
        val grenze = paxJeZeit[zeit] ?: maxPax
        val frei = if (grenze == null) 0 else maxOf(0, grenze - schon)
        return Zeitfenster(
            zeit = zeit,
            frei = frei,
            gesamt = grenze ?: 0,
            freiePlaetze = frei,
            passt = grenze == null || schon + gaeste <= grenze,
            groesster = 0,
            raum = null,
            ohneTisch = true,
            paxMax = grenze,
            paxBelegt = schon,
        )
    }

    private fun vermerkGaeste(id: Long): Int = alle.firstOrNull { it.id == id }?.gaeste ?: 0

    private fun dauer(r: LokalReservierung): Int = r.dauer ?: stamm.aufenthaltMinuten

    private fun beginn(r: LokalReservierung): LocalDateTime =
        LocalDate.parse(r.datum).atTime(LokalStammdaten.zeitOderNull(r.zeit) ?: LocalTime.MIDNIGHT)

    private fun ende(r: LokalReservierung): LocalDateTime = beginn(r).plusMinutes(dauer(r).toLong())

    fun alsIntern(r: LokalReservierung): InternReservierung {
        val status = ReservationStatus.DEFAULTS.firstOrNull { it.id == r.statusId }
        return InternReservierung(
            id = r.id,
            datum = runCatching { LocalDate.parse(r.datum) }.getOrNull(),
            zeit = LokalStammdaten.zeitOderNull(r.zeit),
            dauer = dauer(r),
            gaeste = r.gaeste,
            name = "${r.vorname} ${r.nachname}".trim(),
            telefon = r.telefon,
            kommentar = r.kommentar,
            statusId = r.statusId,
            status = status?.name.orEmpty(),
            tische = r.tische.mapNotNull { id -> tischName(id)?.let { DiningTable(id, it, null, null) } },
            istVermerk = istVermerk(r),
        )
    }

    fun tischName(id: Long): String? =
        stamm.tische.firstOrNull { it.id == id }?.name ?: stamm.raeume.firstOrNull { it.id == id }?.name

    private fun LokalRaum.alsRaum() = Raum(id, name, min, max)

    companion object {
        val STORNIERT: Long = ReservationStatus.DEFAULTS.first { it.name == "Canceled" }.id
        val BESTAETIGT: Long = ReservationStatus.DEFAULTS.first { it.name == "Confirmed" }.id

        /** Obergrenze fuer den Sammeldruck; sonst waere ein vertipptes Jahr ein Auftrag ueber Jahre. */
        const val MAX_TAGE = 92L

        private val STANDARD_TRENNZEIT: LocalTime = LocalTime.of(15, 0)
    }
}
