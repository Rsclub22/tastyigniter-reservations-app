package io.github.rsclub22.tireservations.data

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Was im lokalen Betrieb an die Stelle der TastyIgniter-Einstellungen tritt:
 * Oeffnungszeiten, Raster, Tische, Raeume.
 *
 * Die Vorgaben sind die des braunen Rosses (Mittag 11:30-15:00, So/Mo zu,
 * 15-Minuten-Raster, 90 Minuten Aufenthalt) - irgendetwas muss beim ersten
 * Start drinstehen, und eine Uhrzeit, die man aendert, ist schneller als
 * sieben leere Felder.
 *
 * Uhrzeiten als "HH:MM"-Text wie in [LokalReservierung], damit die Datei von
 * Hand lesbar bleibt.
 */
@Serializable
data class LokalStammdaten(
    val name: String = "Restaurant",
    /** Je Wochentag (1 = Montag … 7 = Sonntag) eine Oeffnungszeit; fehlt der Tag, ist zu. */
    val oeffnungszeiten: List<LokalOeffnung> = (2..6).map { LokalOeffnung(it, "11:30", "15:00") },
    val rasterMinuten: Int = 15,
    val aufenthaltMinuten: Int = 90,
    /** Wo das Tagesblatt teilt; null fuer ein einziges Blatt. */
    val trennzeit: String? = "15:00",
    /**
     * Ab dieser Gaestezahl gilt statt der Oeffnungszeit das Fenster
     * [grossVon]-[grossBis], an Tagen, an denen ueberhaupt geoeffnet ist -
     * Gesellschaften kommen nach Absprache auch ausserhalb.
     */
    val grossAb: Int = 20,
    val grossVon: String = "10:00",
    val grossBis: String = "22:00",
    val tische: List<LokalTisch> = emptyList(),
    val raeume: List<LokalRaum> = emptyList(),
) {
    fun oeffnung(tag: DayOfWeek): Pair<LocalTime, LocalTime>? =
        oeffnungszeiten.firstOrNull { it.wochentag == tag.value }?.let { o ->
            val von = zeitOderNull(o.von) ?: return null
            val bis = zeitOderNull(o.bis) ?: return null
            von to bis
        }

    /** Nur die echten Tische: eine Kombination sind dieselben Plaetze noch einmal. */
    val einzeltische: List<LokalTisch> get() = tische.filter { it.aus.isEmpty() }

    /**
     * Plaetze des ganzen Hauses. Wer mehr Gaeste eintraegt, legt keinen Tisch an,
     * sondern einen Sperrvermerk - dieselbe Regel wie auf dem Server.
     *
     * Die Raeume zaehlen mit: auf dem Server sind sie Tische ohne Kombination.
     * Ohne sie waere eine Gesellschaft von 100 im Saal ein Sperrvermerk.
     */
    val hausgroesse: Int get() = einzeltische.sumOf { it.max } + raeume.sumOf { it.max }

    /** Tische und Raeume teilen sich die Nummern, wie in TastyIgniter. */
    val naechsteNummer: Long get() = ((tische.map { it.id } + raeume.map { it.id }).maxOrNull() ?: 0) + 1

    companion object {
        fun zeitOderNull(text: String?): LocalTime? =
            text?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it.padStart(5, '0')) }.getOrNull() }
    }
}

@Serializable
data class LokalOeffnung(val wochentag: Int, val von: String, val bis: String)

/**
 * Ein Tisch; [aus] nicht leer heisst Kombination aus diesen Tischen. Wer die
 * Kombination belegt, belegt die Einzeltische mit und umgekehrt.
 */
@Serializable
data class LokalTisch(
    val id: Long,
    val name: String,
    val min: Int = 1,
    val max: Int,
    val aus: List<Long> = emptyList(),
)

/** Scheune, Keller, Saal - nur von Hand vergeben, haengen nicht an den Tischen. */
@Serializable
data class LokalRaum(
    val id: Long,
    val name: String,
    val min: Int = 1,
    val max: Int,
)
