package io.github.rsclub22.tireservations.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Betrieb ohne TastyIgniter: die Reservierungen liegen in einer JSON-Datei auf
 * diesem Geraet. Ein zweites Geraet sieht sie nicht - das ist gewollt, es gibt
 * keinen Abgleich.
 *
 * Status und Standort sind fest: die drei Status, die TastyIgniter frisch
 * anlegt, mit denselben Nummern, und ein einziger Standort. So verhalten sich
 * Filter, Farben und Beschriftungen wie gewohnt, ohne dass jemand Stammdaten
 * pflegen muss, bevor die erste Reservierung eingetragen werden kann.
 *
 * Die Funktionen der Server-Erweiterung (Telefonannahme, Tagesblatt, Monat,
 * Sperrtage) kommen in einem spaeteren Schritt; bis dahin melden sie sich wie
 * ein Server, der sie nicht kennt.
 */
class LokalBackend(
    private val datei: File,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val jetzt: () -> Instant = Instant::now,
) : Backend {

    private val mutex = Mutex()

    /** Erst beim ersten Zugriff gelesen; danach ist der Speicher die Quelle. */
    private var stand: LokalStand? = null

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    // --- Reservierungen ---------------------------------------------------------------

    override suspend fun reservations(query: ReservationQuery): ReservationList = lesen { s ->
        val suche = query.search?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val items = s.reservierungen
            .asSequence()
            .filter { query.date == null || it.datum == query.date.toString() }
            .filter { query.locationId == null || query.locationId == STANDORT.id }
            .filter { query.statusId == null || it.statusId == query.statusId }
            .filter { suche == null || it.trifft(suche) }
            .map { it.alsReservierung() }
            .sortedWith(compareBy(nullsLast()) { r: Reservation -> r.date }.thenBy(nullsLast()) { it.time }.thenBy { it.id })
            .toList()
        ReservationList(items)
    }

    override suspend fun reservation(id: Long): LoadedReservation = lesen { s ->
        LoadedReservation(s.finde(id).alsReservierung())
    }

    override suspend fun createReservation(draft: ReservationDraft): Long = aendern { s ->
        pruefe(draft)
        val id = s.naechsteId
        val neu = draft.alsEintrag(id, statusId = draft.statusId ?: AUSSTEHEND, angelegt = jetzt().toString())
        s.copy(naechsteId = id + 1, reservierungen = s.reservierungen + neu) to id
    }

    override suspend fun updateReservation(id: Long, draft: ReservationDraft) = aendern { s ->
        pruefe(draft)
        val alt = s.finde(id)
        // Der Status aendert sich nur ueber updateStatus, wie beim Server.
        val neu = draft.alsEintrag(id, statusId = alt.statusId, angelegt = alt.angelegt)
        s.copy(reservierungen = s.reservierungen.map { if (it.id == id) neu else it }) to Unit
    }

    override suspend fun deleteReservation(id: Long) = aendern { s ->
        s.finde(id)
        s.copy(reservierungen = s.reservierungen.filterNot { it.id == id }) to Unit
    }

    /** [comment] und [notify] haben ohne Server keinen Empfaenger: keine Historie, keine Mail. */
    override suspend fun updateStatus(id: Long, statusId: Long, comment: String?, notify: Boolean) = aendern { s ->
        s.finde(id)
        if (ReservationStatus.DEFAULTS.none { it.id == statusId }) {
            throw ApiException(422, "Unbekannter Status $statusId.")
        }
        s.copy(reservierungen = s.reservierungen.map { if (it.id == id) it.copy(statusId = statusId) else it }) to Unit
    }

    // --- Stammdaten ---------------------------------------------------------------------

    override suspend fun locations(): List<Location> = listOf(STANDORT)

    override suspend fun tables(): List<DiningTable> = emptyList()

    override suspend fun reservationStatuses(): List<ReservationStatus> = ReservationStatus.DEFAULTS

    // --- Funktionen der Server-Erweiterung ------------------------------------------------

    override suspend fun internTag(datum: LocalDate, gaeste: Int, raumId: Long?): Tagesdaten = fehlt()

    override suspend fun internAnnehmen(entwurf: Annahmeentwurf): InternReservierung = fehlt()

    override suspend fun internTagesblatt(von: LocalDate, bis: LocalDate, trennzeit: String?): Tagesblatt = fehlt()

    override suspend fun internMonat(jahr: Int, monat: Int): Monatsuebersicht = fehlt()

    /**
     * Ohne Webformular kommt nichts von aussen herein; was das Haus selbst
     * eintraegt, muss es sich nicht melden. Deshalb eine leere Antwort und kein
     * Fehler - der Wachdienst soll schweigen, nicht scheitern.
     */
    override suspend fun internOffen(seit: Long): OffeneReservierungen = lesen { s ->
        OffeneReservierungen(
            seit = seit,
            hoechsteId = s.naechsteId - 1,
            anzahl = 0,
            offenGesamt = 0,
            hausgroesse = 0,
            reservierungen = emptyList(),
        )
    }

    override suspend fun internSperren(datum: LocalDate, grund: String): Map<String, String> = fehlt()

    override suspend fun internFreigeben(datum: LocalDate): Map<String, String> = fehlt()

    private fun fehlt(): Nothing =
        throw ApiException(404, "Im lokalen Betrieb noch nicht verfügbar.")

    // --- Speicher -------------------------------------------------------------------------

    private suspend fun <T> lesen(block: (LokalStand) -> T): T = mutex.withLock { block(geladen()) }

    /** Schreibt erst die Datei und uebernimmt den neuen Stand nur, wenn das geklappt hat. */
    private suspend fun <T> aendern(block: (LokalStand) -> Pair<LokalStand, T>): T = mutex.withLock {
        val (neu, ergebnis) = block(geladen())
        schreibe(neu)
        stand = neu
        ergebnis
    }

    private suspend fun geladen(): LokalStand = stand ?: withContext(Dispatchers.IO) {
        if (!datei.exists()) return@withContext LokalStand()
        val text = try {
            datei.readText()
        } catch (e: IOException) {
            throw ApiException(0, "Lokale Daten nicht lesbar: ${e.message ?: datei.path}")
        }
        // Eine unlesbare Datei wird NICHT durch eine leere ersetzt, anders als bei
        // den Einstellungen: dort kostet das eine Anmeldung, hier die Reservierungen.
        // Lieber steht die App still und sagt, wo die Datei liegt.
        runCatching { json.decodeFromString(LokalStand.serializer(), text) }.getOrElse {
            throw ApiException(0, "Lokale Daten beschädigt, bitte die Datei prüfen: ${datei.path}")
        }
    }.also { stand = it }

    /**
     * Ueber eine Nachbardatei und Umbenennen: ein abgebrochener Schreibvorgang
     * (Akku leer, App abgeraeumt) laesst so die alte Datei stehen statt einer halben.
     */
    private suspend fun schreibe(s: LokalStand) = withContext(Dispatchers.IO) {
        try {
            datei.parentFile?.mkdirs()
            val tmp = File(datei.path + ".tmp")
            tmp.writeText(json.encodeToString(LokalStand.serializer(), s))
            try {
                Files.move(tmp.toPath(), datei.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), datei.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            throw ApiException(0, "Speichern fehlgeschlagen: ${e.message ?: datei.path}")
        }
    }

    // --- Umwandlung -----------------------------------------------------------------------

    private fun LokalStand.finde(id: Long): LokalReservierung =
        reservierungen.firstOrNull { it.id == id } ?: throw ApiException(404, "Reservierung $id nicht gefunden.")

    /** Dieselben Grenzen wie das Formular; hier nur, was die Datei sonst verderben wuerde. */
    private fun pruefe(d: ReservationDraft) {
        if (d.guestNum < 1) throw ApiException(422, "Mindestens 1 Gast.", mapOf("guest_num" to listOf("Mindestens 1 Gast.")))
        if (d.firstName.isBlank() && d.lastName.isBlank()) {
            val text = "Bitte mindestens Vor- oder Nachname angeben."
            throw ApiException(422, text, mapOf("last_name" to listOf(text)))
        }
    }

    private fun ReservationDraft.alsEintrag(id: Long, statusId: Long, angelegt: String?) = LokalReservierung(
        id = id,
        datum = date.toString(),
        zeit = time.truncatedTo(ChronoUnit.MINUTES).format(HHMM),
        gaeste = guestNum,
        dauer = duration,
        vorname = firstName.trim(),
        nachname = lastName.trim(),
        email = email.trim(),
        telefon = telephone.trim(),
        kommentar = comment.trim(),
        statusId = statusId,
        angelegt = angelegt,
    )

    private fun LokalReservierung.alsReservierung(): Reservation {
        val tag = runCatching { LocalDate.parse(datum) }.getOrNull()
        val uhr = runCatching { LocalTime.parse(zeit) }.getOrNull()
        val status = ReservationStatus.DEFAULTS.firstOrNull { it.id == statusId }
        return Reservation(
            id = id,
            locationId = STANDORT.id,
            locationName = STANDORT.name,
            locationAddress = null,
            guestNum = gaeste,
            firstName = vorname,
            lastName = nachname,
            email = email,
            telephone = telefon,
            comment = kommentar,
            date = tag,
            time = uhr,
            beginn = if (tag != null && uhr != null) LocalDateTime.of(tag, uhr).atZone(zone).toInstant() else null,
            duration = dauer,
            statusId = statusId,
            statusName = status?.name,
            statusColor = status?.color,
            tables = emptyList(),
            tableNames = null,
            // Die Detailansicht zeigt die ersten 16 Zeichen, also Datum und Uhrzeit.
            createdAt = angelegt?.let { a ->
                runCatching { LocalDateTime.ofInstant(Instant.parse(a), zone).toString() }.getOrNull()
            },
        )
    }

    private fun LokalReservierung.trifft(suche: String): Boolean =
        id.toString() == suche ||
            listOf("$vorname $nachname", email, telefon).any { it.lowercase().contains(suche) }

    companion object {
        /** Dateiname neben den Einstellungen. */
        const val DATEINAME = "lokal-reservierungen.json"

        val STANDORT = Location(1, "Restaurant")

        private val AUSSTEHEND = ReservationStatus.DEFAULTS.first { it.name == "Pending" }.id

        private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

/**
 * Inhalt der Datei. Datum und Uhrzeit als Text, damit die Datei sich mit jedem
 * Editor lesen und zur Not von Hand richten laesst.
 */
@Serializable
internal data class LokalStand(
    val version: Int = 1,
    val naechsteId: Long = 1,
    val reservierungen: List<LokalReservierung> = emptyList(),
)

@Serializable
internal data class LokalReservierung(
    val id: Long,
    val datum: String,
    val zeit: String,
    val gaeste: Int,
    val dauer: Int? = null,
    val vorname: String = "",
    val nachname: String = "",
    val email: String = "",
    val telefon: String = "",
    val kommentar: String = "",
    val statusId: Long,
    /** Zeitpunkt als ISO-Instant; null bei Eintraegen, die jemand von Hand angelegt hat. */
    val angelegt: String? = null,
)
