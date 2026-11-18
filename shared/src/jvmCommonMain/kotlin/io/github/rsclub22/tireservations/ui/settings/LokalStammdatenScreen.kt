package io.github.rsclub22.tireservations.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rsclub22.tireservations.data.LokalOeffnung
import io.github.rsclub22.tireservations.data.LokalRaum
import io.github.rsclub22.tireservations.data.LokalStammdaten
import io.github.rsclub22.tireservations.data.LokalTisch
import io.github.rsclub22.tireservations.data.ReservationRepository
import io.github.rsclub22.tireservations.ui.components.ErrorCard
import io.github.rsclub22.tireservations.ui.components.LoadingBox
import io.github.rsclub22.tireservations.ui.components.senkrechtSchiebbar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Was im Formular steht, alles als Text - erst [StammdatenEingabe.alsStammdaten]
 * entscheidet, ob es taugt. So verliert niemand eine halb getippte Uhrzeit.
 */
data class StammdatenEingabe(
    val name: String = "",
    /** Index 0 = Montag. */
    val tage: List<TagEingabe> = DayOfWeek.entries.map { TagEingabe() },
    val raster: String = "",
    val aufenthalt: String = "",
    val trennzeit: String = "",
    val grossAb: String = "",
    val grossVon: String = "",
    val grossBis: String = "",
    /** Nur Einzeltische; Kombinationen stehen in [kombis]. */
    val tische: List<PlatzEingabe> = emptyList(),
    val kombis: List<KombiEingabe> = emptyList(),
    val raeume: List<PlatzEingabe> = emptyList(),
) {
    data class TagEingabe(val offen: Boolean = false, val von: String = "", val bis: String = "")

    data class PlatzEingabe(val id: Long, val name: String, val min: String, val max: String)

    /**
     * Eine Kombination: angetippte Tische statt getippter Namen. Name und Plaetze
     * fuellen sich aus der Auswahl, bis jemand sie von Hand aendert - dann
     * bleiben sie, wie sie geschrieben wurden.
     */
    data class KombiEingabe(
        val id: Long,
        val aus: List<Long> = emptyList(),
        val name: String = "",
        val nameVonHand: Boolean = false,
        val min: String = "1",
        val max: String = "",
        val maxVonHand: Boolean = false,
    )

    /** Name und Plaetze neu aus der Auswahl, wo sie nicht von Hand stehen. */
    fun nachgezogen(k: KombiEingabe): KombiEingabe {
        val teile = tische.filter { it.id in k.aus }
        return k.copy(
            name = if (k.nameVonHand) k.name else teile.joinToString(" + ") { it.name.trim().ifEmpty { "?" } },
            max = if (k.maxVonHand) k.max else teile.sumOf { it.max.trim().toIntOrNull() ?: 0 }.takeIf { it > 0 }?.toString().orEmpty(),
        )
    }

    /** Nach jeder Aenderung an den Tischen: weggefallene aus den Kombinationen nehmen, Rest nachziehen. */
    fun kombisAbgeglichen(): StammdatenEingabe {
        val ids = tische.map { it.id }.toSet()
        return copy(kombis = kombis.map { nachgezogen(it.copy(aus = it.aus.filter { id -> id in ids })) })
    }

    /** Die Stammdaten oder die Liste dessen, was nicht stimmt. */
    fun alsStammdaten(): Pair<LokalStammdaten?, List<String>> {
        val fehler = mutableListOf<String>()
        fun zeit(text: String, wo: String): String? {
            val z = LokalStammdaten.zeitOderNull(text)
            if (z == null) fehler += "$wo: Uhrzeit als HH:MM angeben." else return "%02d:%02d".format(z.hour, z.minute)
            return null
        }
        fun zahl(text: String, wo: String, min: Int, max: Int): Int {
            val z = text.trim().toIntOrNull()
            if (z == null || z !in min..max) fehler += "$wo: Zahl von $min bis $max."
            return z ?: min
        }
        fun platz(p: PlatzEingabe, art: String, grenze: Int): Triple<String, Int, Int> {
            val wo = "$art „${p.name.ifBlank { "?" }}“"
            if (p.name.isBlank()) fehler += "Ein $art hat keinen Namen."
            val mi = zahl(p.min, "$wo, von", 1, grenze)
            val ma = zahl(p.max, "$wo, bis", 1, grenze)
            if (ma < mi) fehler += "$wo: „bis“ ist kleiner als „von“."
            return Triple(p.name.trim(), mi, ma)
        }

        val oeffnung = tage.mapIndexedNotNull { i, t ->
            if (!t.offen) return@mapIndexedNotNull null
            val wo = DayOfWeek.of(i + 1).getDisplayName(TextStyle.FULL, Locale.GERMAN)
            val von = zeit(t.von, wo)
            val bis = zeit(t.bis, wo)
            if (von != null && bis != null) LokalOeffnung(i + 1, von, bis) else null
        }
        val trenn = trennzeit.trim().takeIf { it.isNotEmpty() }?.let { zeit(it, "Trennzeit") }

        val einzeln = tische.map { t -> platz(t, "Tisch", 500).let { (n, mi, ma) -> LokalTisch(t.id, n, mi, ma) } }
        val kombiniert = kombis.map { k ->
            val (n, mi, ma) = platz(PlatzEingabe(k.id, k.name, k.min, k.max), "Kombination", 2000)
            if (k.aus.size < 2) fehler += "Kombination „${n.ifBlank { "?" }}“: mindestens zwei Tische antippen."
            LokalTisch(k.id, n, mi, ma, aus = k.aus)
        }
        val raumListe = raeume.map { r -> platz(r, "Raum", 2000).let { (n, mi, ma) -> LokalRaum(r.id, n, mi, ma) } }

        // Gleiche Namen machen die Listen am Telefon und auf dem Tagesblatt mehrdeutig.
        (einzeln.map { it.name } + kombiniert.map { it.name } + raumListe.map { it.name })
            .filter { it.isNotEmpty() }
            .groupBy { it.lowercase() }
            .filter { it.value.size > 1 }
            .forEach { (_, gleich) -> fehler += "Der Name „${gleich.first()}“ steht mehrfach da." }
        kombiniert.groupBy { it.aus.toSet() }.filter { it.key.size >= 2 && it.value.size > 1 }.forEach { (_, gleich) ->
            fehler += "„${gleich.first().name}“ und „${gleich[1].name}“ sind dieselbe Kombination."
        }

        val stamm = LokalStammdaten(
            name = name.trim().ifEmpty { "Restaurant" },
            oeffnungszeiten = oeffnung,
            rasterMinuten = zahl(raster, "Raster", 5, 240),
            aufenthaltMinuten = zahl(aufenthalt, "Aufenthalt", 15, 1440),
            trennzeit = trenn,
            grossAb = zahl(grossAb, "Gesellschaft ab", 1, 2000),
            grossVon = zeit(grossVon, "Gesellschaften von") ?: "10:00",
            grossBis = zeit(grossBis, "Gesellschaften bis") ?: "22:00",
            tische = einzeln + kombiniert,
            raeume = raumListe,
        )
        return if (fehler.isEmpty()) stamm to emptyList() else null to fehler.distinct()
    }

    companion object {
        fun aus(s: LokalStammdaten): StammdatenEingabe {
            val einzeln = s.tische.filter { it.aus.isEmpty() }
            val eingabe = StammdatenEingabe(
                name = s.name,
                tage = DayOfWeek.entries.map { tag ->
                    s.oeffnungszeiten.firstOrNull { it.wochentag == tag.value }
                        ?.let { TagEingabe(true, it.von, it.bis) } ?: TagEingabe()
                },
                raster = s.rasterMinuten.toString(),
                aufenthalt = s.aufenthaltMinuten.toString(),
                trennzeit = s.trennzeit.orEmpty(),
                grossAb = s.grossAb.toString(),
                grossVon = s.grossVon,
                grossBis = s.grossBis,
                tische = einzeln.map { PlatzEingabe(it.id, it.name, it.min.toString(), it.max.toString()) },
                raeume = s.raeume.map { PlatzEingabe(it.id, it.name, it.min.toString(), it.max.toString()) },
            )
            // Gespeicherte Werte gelten als von Hand, wenn sie nicht dem entsprechen,
            // was sich aus der Auswahl ergaebe - sonst ueberschriebe das naechste
            // Antippen einen bewusst vergebenen Namen.
            return eingabe.copy(
                kombis = s.tische.filter { it.aus.isNotEmpty() }.map { t ->
                    val auto = eingabe.nachgezogen(KombiEingabe(t.id, t.aus))
                    KombiEingabe(
                        id = t.id,
                        aus = t.aus,
                        name = t.name,
                        nameVonHand = t.name != auto.name,
                        min = t.min.toString(),
                        max = t.max.toString(),
                        maxVonHand = t.max.toString() != auto.max,
                    )
                },
            )
        }
    }
}

data class StammdatenState(
    val eingabe: StammdatenEingabe = StammdatenEingabe(),
    /** Naechste freie Nummer fuer Tische und Raeume; sie teilen sich die Nummern. */
    val naechsteNummer: Long = 1,
    val laden: Boolean = true,
    val speichern: Boolean = false,
    val fehler: List<String> = emptyList(),
)

class LokalStammdatenViewModel(private val repository: ReservationRepository) : ViewModel() {
    private val _state = MutableStateFlow(StammdatenState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.lokalStammdaten() }
                .onSuccess { s ->
                    _state.update { it.copy(eingabe = StammdatenEingabe.aus(s), naechsteNummer = s.naechsteNummer, laden = false) }
                }
                .onFailure { e -> _state.update { it.copy(laden = false, fehler = listOf(e.message ?: "Laden fehlgeschlagen")) } }
        }
    }

    fun aendere(transform: (StammdatenEingabe) -> StammdatenEingabe) =
        _state.update { it.copy(eingabe = transform(it.eingabe), fehler = emptyList()) }

    /** Wie [aendere], fuer alles an den Einzeltischen: die Kombinationen ziehen mit. */
    fun aendereTische(transform: (List<StammdatenEingabe.PlatzEingabe>) -> List<StammdatenEingabe.PlatzEingabe>) =
        aendere { it.copy(tische = transform(it.tische)).kombisAbgeglichen() }

    fun aendereKombi(index: Int, transform: (StammdatenEingabe.KombiEingabe) -> StammdatenEingabe.KombiEingabe) =
        aendere { e -> e.copy(kombis = e.kombis.ersetze(index) { e.nachgezogen(transform(it)) }) }

    fun tischInKombi(index: Int, tischId: Long) = aendereKombi(index) { k ->
        // In der Reihenfolge der Tischliste, damit der Name "1 + 2" heisst und nicht "2 + 1".
        val neu = if (tischId in k.aus) k.aus - tischId else k.aus + tischId
        val reihenfolge = _state.value.eingabe.tische.map { it.id }
        k.copy(aus = neu.sortedBy { reihenfolge.indexOf(it) })
    }

    fun neuerTisch() = neu { e, id ->
        // Fortlaufend benannt: wer zehn Tische anlegt, muss nicht zehnmal tippen.
        val name = ((e.tische.mapNotNull { it.name.trim().toIntOrNull() }.maxOrNull() ?: 0) + 1).toString()
        e.copy(tische = e.tische + StammdatenEingabe.PlatzEingabe(id, name, "1", "4"))
    }

    fun neueKombi() = neu { e, id -> e.copy(kombis = e.kombis + StammdatenEingabe.KombiEingabe(id)) }

    fun neuerRaum() = neu { e, id -> e.copy(raeume = e.raeume + StammdatenEingabe.PlatzEingabe(id, "", "1", "40")) }

    private fun neu(transform: (StammdatenEingabe, Long) -> StammdatenEingabe) = _state.update {
        it.copy(eingabe = transform(it.eingabe, it.naechsteNummer), naechsteNummer = it.naechsteNummer + 1)
    }

    fun speichern(onFertig: () -> Unit) {
        val (stamm, fehler) = _state.value.eingabe.alsStammdaten()
        if (stamm == null) {
            _state.update { it.copy(fehler = fehler) }
            return
        }
        _state.update { it.copy(speichern = true, fehler = emptyList()) }
        viewModelScope.launch {
            runCatching { repository.speichereLokalStammdaten(stamm) }
                .onSuccess {
                    _state.update { it.copy(speichern = false) }
                    onFertig()
                }
                .onFailure { e -> _state.update { it.copy(speichern = false, fehler = listOf(e.message ?: "Speichern fehlgeschlagen")) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LokalStammdatenScreen(repository: ReservationRepository, onBack: () -> Unit) {
    val vm: LokalStammdatenViewModel = viewModel { LokalStammdatenViewModel(repository) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lokaler Betrieb") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
                actions = {
                    TextButton(onClick = { vm.speichern(onBack) }, enabled = !state.laden && !state.speichern) {
                        Text("Speichern")
                    }
                },
            )
        },
    ) { padding ->
        if (state.laden) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val e = state.eingabe
        val breite = Modifier.fillMaxWidth().widthIn(max = 640.dp)
        Column(
            Modifier.padding(padding).fillMaxSize().senkrechtSchiebbar().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.fehler.isNotEmpty()) ErrorCard(state.fehler.joinToString("\n"), modifier = breite)

            OutlinedTextField(
                value = e.name,
                onValueChange = { v -> vm.aendere { it.copy(name = v) } },
                label = { Text("Name des Hauses") },
                singleLine = true,
                modifier = breite,
            )

            Ueberschrift("Öffnungszeiten")
            e.tage.forEachIndexed { i, tag ->
                Row(breite, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = tag.offen, onCheckedChange = { an -> vm.aendere { it.copy(tage = it.tage.ersetze(i) { t -> t.copy(offen = an) }) } })
                    Text(
                        DayOfWeek.of(i + 1).getDisplayName(TextStyle.SHORT, Locale.GERMAN),
                        Modifier.width(36.dp),
                    )
                    if (tag.offen) {
                        Zeitfeld(tag.von, "von", Modifier.weight(1f)) { v -> vm.aendere { it.copy(tage = it.tage.ersetze(i) { t -> t.copy(von = v) }) } }
                        Zeitfeld(tag.bis, "bis", Modifier.weight(1f)) { v -> vm.aendere { it.copy(tage = it.tage.ersetze(i) { t -> t.copy(bis = v) }) } }
                    } else {
                        Text("geschlossen", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Ueberschrift("Zeiten")
            Row(breite, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Zahlfeld(e.raster, "Raster (Min.)", Modifier.weight(1f)) { v -> vm.aendere { it.copy(raster = v) } }
                Zahlfeld(e.aufenthalt, "Aufenthalt (Min.)", Modifier.weight(1f)) { v -> vm.aendere { it.copy(aufenthalt = v) } }
            }
            OutlinedTextField(
                value = e.trennzeit,
                onValueChange = { v -> vm.aendere { it.copy(trennzeit = v) } },
                label = { Text("Tagesblatt teilen um") },
                supportingText = { Text("Leer lassen für ein einziges Blatt.") },
                singleLine = true,
                modifier = breite,
            )
            Row(breite, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Zahlfeld(e.grossAb, "Gesellschaft ab", Modifier.weight(1f)) { v -> vm.aendere { it.copy(grossAb = v) } }
                Zeitfeld(e.grossVon, "von", Modifier.weight(1f)) { v -> vm.aendere { it.copy(grossVon = v) } }
                Zeitfeld(e.grossBis, "bis", Modifier.weight(1f)) { v -> vm.aendere { it.copy(grossBis = v) } }
            }
            Hinweis("Gesellschaften dürfen an geöffneten Tagen auch außerhalb der Öffnungszeit kommen.", breite)

            HorizontalDivider()
            Ueberschrift("Tische")
            Hinweis(
                "Plätze von–bis: wie viele Gäste der Tisch bekommt. Einen Sperrvermerk legt an, " +
                    "wer mehr Gäste einträgt, als Tische und Räume zusammen fassen.",
                breite,
            )
            e.tische.forEachIndexed { i, t ->
                Platzzeile(
                    t, modifier = breite,
                    onAendern = { neu -> vm.aendereTische { it.ersetze(i) { neu } } },
                    onLoeschen = { vm.aendereTische { it.filterIndexed { j, _ -> j != i } } },
                )
            }
            TextButton(onClick = vm::neuerTisch) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("Tisch hinzufügen")
            }

            HorizontalDivider()
            Ueberschrift("Zusammengestellte Tische")
            Hinweis(
                "Tische, die für größere Gruppen zusammengeschoben werden. Vergeben wird so eine " +
                    "Kombination erst, wenn kein einzelner Tisch reicht; ist sie belegt, sind es " +
                    "ihre Tische auch, und umgekehrt.",
                breite,
            )
            e.kombis.forEachIndexed { i, k ->
                Kombikarte(
                    k, e.tische, modifier = breite,
                    onTisch = { id -> vm.tischInKombi(i, id) },
                    onAendern = { neu -> vm.aendereKombi(i) { neu } },
                    onLoeschen = { vm.aendere { it.copy(kombis = it.kombis.filterIndexed { j, _ -> j != i }) } },
                )
            }
            TextButton(onClick = vm::neueKombi, enabled = e.tische.size >= 2) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(if (e.tische.size >= 2) "Tische zusammenstellen" else "Erst mindestens zwei Tische anlegen")
            }

            HorizontalDivider()
            Ueberschrift("Räume")
            Hinweis("Scheune, Saal, Keller: werden nur in der Telefonannahme von Hand vergeben.", breite)
            e.raeume.forEachIndexed { i, r ->
                Platzzeile(
                    r, modifier = breite,
                    onAendern = { neu -> vm.aendere { it.copy(raeume = it.raeume.ersetze(i) { neu }) } },
                    onLoeschen = { vm.aendere { it.copy(raeume = it.raeume.filterIndexed { j, _ -> j != i }) } },
                )
            }
            TextButton(onClick = vm::neuerRaum) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("Raum hinzufügen")
            }
        }
    }
}

@Composable
private fun Ueberschrift(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Hinweis(text: String, modifier: Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun Zeitfeld(wert: String, label: String, modifier: Modifier, onAendern: (String) -> Unit) {
    OutlinedTextField(
        value = wert,
        onValueChange = onAendern,
        label = { Text(label) },
        placeholder = { Text("HH:MM") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun Zahlfeld(wert: String, label: String, modifier: Modifier, onAendern: (String) -> Unit) {
    OutlinedTextField(
        value = wert,
        onValueChange = { v -> onAendern(v.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun Platzzeile(
    p: StammdatenEingabe.PlatzEingabe,
    modifier: Modifier,
    onAendern: (StammdatenEingabe.PlatzEingabe) -> Unit,
    onLoeschen: () -> Unit,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = p.name,
            onValueChange = { onAendern(p.copy(name = it)) },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.weight(2f),
        )
        Zahlfeld(p.min, "von", Modifier.weight(1f)) { onAendern(p.copy(min = it)) }
        Zahlfeld(p.max, "bis", Modifier.weight(1f)) { onAendern(p.copy(max = it)) }
        IconButton(onClick = onLoeschen) { Icon(Icons.Outlined.Delete, contentDescription = "Entfernen") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Kombikarte(
    k: StammdatenEingabe.KombiEingabe,
    tische: List<StammdatenEingabe.PlatzEingabe>,
    modifier: Modifier,
    onTisch: (Long) -> Unit,
    onAendern: (StammdatenEingabe.KombiEingabe) -> Unit,
    onLoeschen: () -> Unit,
) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Diese Tische antippen:", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = onLoeschen) { Icon(Icons.Outlined.Delete, contentDescription = "Zusammenstellung entfernen") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tische.forEach { t ->
                    FilterChip(
                        selected = t.id in k.aus,
                        onClick = { onTisch(t.id) },
                        label = { Text(t.name.ifBlank { "?" }) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = k.name,
                    onValueChange = { onAendern(k.copy(name = it, nameVonHand = it.isNotBlank())) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                Zahlfeld(k.min, "von", Modifier.weight(1f)) { onAendern(k.copy(min = it)) }
                Zahlfeld(k.max, "bis", Modifier.weight(1f)) { onAendern(k.copy(max = it, maxVonHand = it.isNotBlank())) }
            }
            // Ohne diesen Satz sieht niemand, dass sich die Felder selbst fuellen.
            if (!k.nameVonHand || !k.maxVonHand) {
                Text(
                    "Name und Plätze ergeben sich aus den Tischen; wer sie überschreibt, behält seinen Wert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun <T> List<T>.ersetze(index: Int, transform: (T) -> T): List<T> =
    mapIndexed { i, x -> if (i == index) transform(x) else x }
