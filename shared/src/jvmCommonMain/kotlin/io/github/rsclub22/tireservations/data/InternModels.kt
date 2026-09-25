package io.github.rsclub22.tireservations.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.LocalTime

/**
 * Die Telefonannahme, wie sie `/api/intern/...` liefert.
 *
 * Die Namen sind hier deutsch, anders als bei den übrigen Modellen dieser App.
 * Das ist Absicht: diese Klassen spiegeln eine Schnittstelle, die wir selbst
 * entworfen haben und deren Felder auf dem Server `belegung`, `passt`,
 * `ohne_tisch` heißen. Eins-zu-eins benannt lässt sich jede Stelle ohne
 * Übersetzungstabelle beim Server nachlesen - und das ist die Schicht, die am
 * ehesten auseinanderläuft, wenn man es nicht tut.
 */

/**
 * Ein Zeitfenster des Tages.
 *
 * Die Zählungen bedeuten je nach Lage etwas anderes, und das ist keine
 * Schlampigkeit des Servers, sondern die Sache selbst:
 * - normaler Tag: [frei] von [gesamt] Tischen, [freiePlaetze] Plätze darauf
 * - Raum gewählt: [frei] ist 1 oder 0, die Personenzahl begrenzt nichts mehr
 * - Tag mit Sperrvermerk: [ohneTisch] ist wahr, gezählt wird in [paxBelegt]
 *   von [paxMax]
 *
 * [passt] ist die Antwort auf die einzige Frage, die am Telefon zählt: geht die
 * gewünschte Gesellschaft zu dieser Zeit noch hinein?
 */
data class Zeitfenster(
    val zeit: LocalTime,
    val frei: Int,
    val gesamt: Int,
    val freiePlaetze: Int,
    val passt: Boolean,
    val groesster: Int,
    val raum: String?,
    val ohneTisch: Boolean,
    val paxMax: Int?,
    val paxBelegt: Int,
) {
    /**
     * Die Zeile unter der Uhrzeit, wortgleich mit der internen Weboberflaeche -
     * die Formulierungen sind im Haus eingefuehrt, und zwei verschiedene waeren
     * eine Stolperfalle beim Umstieg.
     */
    val anzeige: String
        get() = when {
            paxMax != null -> "$paxBelegt/$paxMax Pers." + if (!passt) " · voll" else ""
            !passt -> "belegt"
            ohneTisch -> "ohne Tisch"
            raum != null -> "$raum frei"
            else -> "$frei/$gesamt Tische · max. $groesster Pl."
        }

    /**
     * Nur noch ein oder zwei Tische frei. Die Weboberflaeche setzt dafuer eine
     * farbige Kante - „wird knapp", nicht „geht nicht mehr".
     */
    val knapp: Boolean get() = frei in 1..2 && paxMax == null
}

/** Scheune, Keller, Saal, Gaststube - von Hand vergeben, hängen nicht an den Tischen. */
data class Raum(
    val id: Long,
    val name: String,
    val minPlaetze: Int,
    val maxPlaetze: Int,
)

/**
 * Ein Sperrvermerk: eine Pseudo-Reservierung, die einen Tag gegen die
 * Online-Buchung verriegelt. Der [kommentar] ist am Telefon oft die wichtigste
 * Angabe des Tages - dort stehen die Essenszeiten und die Höchstzahl.
 */
data class Sperrvermerk(
    val id: Long,
    val zeit: LocalTime?,
    val gaeste: Int,
    val kommentar: String,
)

/** Eine Reservierung, wie die Telefonannahme sie auflistet. */
data class InternReservierung(
    val id: Long,
    /** Nur bei Meldungen gesetzt - im Tages-Zusammenhang kennt man den Tag schon. */
    val datum: LocalDate? = null,
    val zeit: LocalTime?,
    val dauer: Int,
    val gaeste: Int,
    val name: String,
    val telefon: String,
    val kommentar: String,
    val statusId: Long,
    val status: String,
    val tische: List<DiningTable>,
    val istVermerk: Boolean,
) {
    val tischeText: String get() = tische.joinToString(" + ") { it.name }.ifBlank { "ohne Tisch" }
}

/** Alles, was die Annahme für einen Tag braucht - `GET intern/tag`. */
data class Tagesdaten(
    val datum: LocalDate,
    val gaeste: Int,
    val raum: Raum?,
    val gesperrt: Boolean,
    val grund: String,
    /** Datum (ISO) auf Grund; gesperrte Tage sind online nicht buchbar. */
    val sperren: Map<String, String>,
    val trennzeit: LocalTime?,
    val tischeGesamt: Int,
    val plaetzeGesamt: Int,
    val belegung: List<Zeitfenster>,
    val raeume: List<Raum>,
    val vermerke: List<Sperrvermerk>,
    val vermerkZeiten: List<LocalTime>,
    val maxPax: Int?,
    val paxJeZeit: Map<String, Int>,
    val hausgroesse: Int,
    val reservierungen: List<InternReservierung>,
) {
    /** Zählt nur, was wirklich Gäste sind - Sperrvermerke sind keine Gesellschaft. */
    val gaesteGesamt: Int get() = reservierungen.filterNot { it.istVermerk }.sumOf { it.gaeste }
}

/** Was am Telefon eingegeben wird - `POST intern/reservierung`. */
data class Annahmeentwurf(
    val datum: LocalDate,
    val zeit: LocalTime,
    val gaeste: Int,
    val nachname: String,
    val telefon: String,
    val email: String = "",
    val notiz: String = "",
    val raumId: Long? = null,
    /**
     * Ausdrücklich ohne Tisch und ohne Raum. Notausgang für die Fälle, in denen
     * die automatische Vergabe nicht passt; gewinnt gegen [raumId].
     */
    val ohneTisch: Boolean = false,
)

/** Das Tagesblatt - `GET intern/tagesblatt`. */
data class Tagesblatt(
    val von: LocalDate,
    val bis: LocalDate,
    val zeitraum: Boolean,
    val trennzeit: LocalTime?,
    val tage: List<BlattTag>,
)

data class BlattTag(
    val datum: LocalDate,
    val gesperrt: Boolean,
    val grund: String,
    val maxPax: Int?,
    val paxJeZeit: Map<String, Int>,
    val sperrvermerke: List<Sperrvermerk>,
    val blaetter: List<Blatt>,
)

/** Ein Abschnitt des Tagesblatts, etwa „Bis 15:00 Uhr". */
data class Blatt(
    val titel: String,
    val gaeste: Int,
    val reservierungen: List<InternReservierung>,
)

/** Ein Tag in der Monatsuebersicht - nur Summen, keine Zeitfenster. */
data class Monatstag(
    val datum: LocalDate,
    val reservierungen: Int,
    val gaeste: Int,
    val gesperrt: Boolean,
    val grund: String,
    val vermerk: Boolean,
    val vermerkText: String,
    val maxPax: Int?,
) {
    val leer: Boolean get() = reservierungen == 0 && !gesperrt && !vermerk
}

/**
 * Der Monat auf einen Blick - `GET intern/monat`.
 *
 * Enthaelt jeden Tag des Monats, auch die leeren, damit das Kalenderraster ohne
 * Luecken gezeichnet werden kann.
 */
data class Monatsuebersicht(
    val jahr: Int,
    val monat: Int,
    val von: LocalDate,
    val bis: LocalDate,
    /**
     * Groesste Gaestezahl des Monats. Eingefaerbt wird daran und nicht an einer
     * festen Kapazitaet: die Raeume fassen ein Vielfaches der Tische, eine feste
     * Obergrenze waere an den meisten Tagen irrefuehrend.
     */
    val hoechstwert: Int,
    val tage: List<Monatstag>,
) {
    fun tag(datum: LocalDate): Monatstag? = tage.firstOrNull { it.datum == datum }

    /** Wie voll der Tag im Vergleich zum vollsten des Monats ist, 0f..1f. */
    fun fuellung(tag: Monatstag): Float =
        if (hoechstwert <= 0) 0f else (tag.gaeste.toFloat() / hoechstwert).coerceIn(0f, 1f)

    val gaesteGesamt: Int get() = tage.sumOf { it.gaeste }
    val reservierungenGesamt: Int get() = tage.sumOf { it.reservierungen }
}

/**
 * Unbestaetigte Reservierungen - `GET intern/offen`.
 *
 * Zwei Zahlen mit verschiedenem Zweck: [anzahl] sind die seit dem Merker
 * hinzugekommenen, dafuer wird gemeldet. [offenGesamt] sind alle unbestaetigten,
 * dafuer steht die Markierung in der Liste.
 */
data class OffeneReservierungen(
    val seit: Long,
    /**
     * Die hoechste vergebene Nummer, nicht die der neuesten unbestaetigten. Der
     * Merker rueckt daran vor - sonst kaeme dieselbe Meldung wieder, sobald
     * zwischendurch nur bestaetigte Reservierungen dazukommen.
     */
    val hoechsteId: Long,
    val anzahl: Int,
    val offenGesamt: Int,
    /**
     * Plaetze des ganzen Hauses. Damit erkennt die App Sperrvermerke auch in
     * Listen, die ueber das gewoehnliche /api/reservations kommen - dort fehlt
     * das Kennzeichen, und die Regel ist allein "mehr Gaeste als Plaetze".
     */
    val hausgroesse: Int,
    val reservierungen: List<InternReservierung>,
)

/**
 * Wandelt die Antworten um. Von Hand wie im übrigen Datenlayer, damit ein
 * fehlendes oder unerwartetes Feld eine harmlose Vorgabe ergibt und nicht die
 * ganze Antwort verwirft.
 */
internal object InternMappers {

    fun tagesdaten(o: JsonObject): Tagesdaten = Tagesdaten(
        datum = datum(o.string("datum")) ?: LocalDate.now(),
        gaeste = o.int("gaeste") ?: 2,
        raum = o["raum"]?.let { if (it is JsonObject) raum(it) else null },
        gesperrt = o.bool("gesperrt") ?: false,
        grund = o.string("grund").orEmpty(),
        sperren = textmap(o["sperren"]),
        trennzeit = zeit(o.string("trennzeit")),
        tischeGesamt = o.int("tische_gesamt") ?: 0,
        plaetzeGesamt = o.int("plaetze_gesamt") ?: 0,
        belegung = objekte(o["belegung"]).map(::zeitfenster),
        raeume = objekte(o["raeume"]).map(::raum),
        vermerke = objekte(o["vermerke"]).map(::sperrvermerk),
        vermerkZeiten = texte(o["vermerk_zeiten"]).mapNotNull(::zeit),
        maxPax = o.int("max_pax"),
        paxJeZeit = zahlmap(o["pax_je_zeit"]),
        hausgroesse = o.int("hausgroesse") ?: 0,
        reservierungen = objekte(o["reservierungen"]).map(::reservierung),
    )

    fun offen(o: JsonObject): OffeneReservierungen = OffeneReservierungen(
        seit = o.long("seit") ?: 0,
        hoechsteId = o.long("hoechste_id") ?: 0,
        anzahl = o.int("anzahl") ?: 0,
        offenGesamt = o.int("offen_gesamt") ?: 0,
        hausgroesse = o.int("hausgroesse") ?: 0,
        reservierungen = objekte(o["reservierungen"]).map(::reservierung),
    )

    fun monat(o: JsonObject): Monatsuebersicht = Monatsuebersicht(
        jahr = o.int("jahr") ?: LocalDate.now().year,
        monat = o.int("monat") ?: LocalDate.now().monthValue,
        von = datum(o.string("von")) ?: LocalDate.now(),
        bis = datum(o.string("bis")) ?: LocalDate.now(),
        hoechstwert = o.int("hoechstwert") ?: 0,
        tage = objekte(o["tage"]).map(::monatstag),
    )

    private fun monatstag(o: JsonObject) = Monatstag(
        datum = datum(o.string("datum")) ?: LocalDate.now(),
        reservierungen = o.int("reservierungen") ?: 0,
        gaeste = o.int("gaeste") ?: 0,
        gesperrt = o.bool("gesperrt") ?: false,
        grund = o.string("grund").orEmpty(),
        vermerk = o.bool("vermerk") ?: false,
        vermerkText = o.string("vermerk_text").orEmpty(),
        maxPax = o.int("max_pax"),
    )

    fun tagesblatt(o: JsonObject): Tagesblatt = Tagesblatt(
        von = datum(o.string("von")) ?: LocalDate.now(),
        bis = datum(o.string("bis")) ?: LocalDate.now(),
        zeitraum = o.bool("zeitraum") ?: false,
        trennzeit = zeit(o.string("trennzeit")),
        tage = objekte(o["tage"]).map(::blattTag),
    )

    private fun blattTag(o: JsonObject) = BlattTag(
        datum = datum(o.string("datum")) ?: LocalDate.now(),
        gesperrt = o.bool("gesperrt") ?: false,
        grund = o.string("grund").orEmpty(),
        maxPax = o.int("max_pax"),
        paxJeZeit = zahlmap(o["pax_je_zeit"]),
        sperrvermerke = objekte(o["sperrvermerke"]).map(::sperrvermerk),
        blaetter = objekte(o["blaetter"]).map(::blatt),
    )

    private fun blatt(o: JsonObject) = Blatt(
        titel = o.string("titel").orEmpty(),
        gaeste = o.int("gaeste") ?: 0,
        reservierungen = objekte(o["reservierungen"]).map(::reservierung),
    )

    private fun zeitfenster(o: JsonObject) = Zeitfenster(
        zeit = zeit(o.string("zeit")) ?: LocalTime.MIDNIGHT,
        frei = o.int("frei") ?: 0,
        gesamt = o.int("gesamt") ?: 0,
        freiePlaetze = o.int("freie_plaetze") ?: 0,
        passt = o.bool("passt") ?: false,
        groesster = o.int("groesster") ?: 0,
        raum = o.string("raum"),
        ohneTisch = o.bool("ohne_tisch") ?: false,
        paxMax = o.int("pax_max"),
        paxBelegt = o.int("pax_belegt") ?: 0,
    )

    private fun raum(o: JsonObject) = Raum(
        id = o.long("id") ?: 0,
        name = o.string("name").orEmpty(),
        minPlaetze = o.int("min_plaetze") ?: 0,
        maxPlaetze = o.int("max_plaetze") ?: 0,
    )

    private fun sperrvermerk(o: JsonObject) = Sperrvermerk(
        id = o.long("id") ?: 0,
        zeit = zeit(o.string("zeit")),
        gaeste = o.int("gaeste") ?: 0,
        kommentar = o.string("kommentar").orEmpty(),
    )

    fun reservierung(o: JsonObject) = InternReservierung(
        id = o.long("id") ?: 0,
        datum = datum(o.string("datum")),
        zeit = zeit(o.string("zeit")),
        dauer = o.int("dauer") ?: 0,
        gaeste = o.int("gaeste") ?: 0,
        name = o.string("name").orEmpty(),
        telefon = o.string("telefon").orEmpty(),
        kommentar = o.string("kommentar").orEmpty(),
        statusId = o.long("status_id") ?: 0,
        status = o.string("status").orEmpty(),
        // Das API liefert hier nur Nummer und Name; Kapazitäten stehen in tag.raeume
        // bzw. brauchen am Telefon niemanden zu interessieren.
        tische = objekte(o["tische"]).map {
            DiningTable(it.long("id") ?: 0, it.string("name").orEmpty(), null, null)
        },
        istVermerk = o.bool("ist_vermerk") ?: false,
    )

    // --- Kleinkram ----------------------------------------------------------------

    private fun objekte(element: kotlinx.serialization.json.JsonElement?): List<JsonObject> =
        (element as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

    private fun texte(element: kotlinx.serialization.json.JsonElement?): List<String> =
        (element as? JsonArray)?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() } ?: emptyList()

    private fun textmap(element: kotlinx.serialization.json.JsonElement?): Map<String, String> =
        (element as? JsonObject)?.mapValues { (_, v) -> runCatching { v.jsonPrimitive.content }.getOrDefault("") }
            ?: emptyMap()

    private fun zahlmap(element: kotlinx.serialization.json.JsonElement?): Map<String, Int> =
        (element as? JsonObject)?.mapNotNull { (k, v) ->
            runCatching { v.jsonPrimitive.content.toInt() }.getOrNull()?.let { k to it }
        }?.toMap() ?: emptyMap()

    private fun datum(text: String?): LocalDate? =
        text?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    private fun zeit(text: String?): LocalTime? =
        text?.takeIf { it.isNotBlank() }?.let { runCatching { LocalTime.parse(it.take(5)) }.getOrNull() }
}
