package io.github.rsclub22.tireservations.ui.annahme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rsclub22.tireservations.data.ReservationRepository
import io.github.rsclub22.tireservations.data.Tagesdaten
import io.github.rsclub22.tireservations.data.Zeitfenster
import io.github.rsclub22.tireservations.ui.components.ErrorCard
import io.github.rsclub22.tireservations.ui.components.LoadingBox
import io.github.rsclub22.tireservations.ui.components.display
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Telefonannahme: eintragen, waehrend der Gast am Telefon ist.
 *
 * Aufbau wie die interne Weboberflaeche, die dieser Schirm ersetzt - drei Karten
 * untereinander: der Tag, die Belegung, der Gast. Der Ablauf ist derselbe: Name
 * und Nummer eintragen, dann auf die Uhrzeit klicken, fertig. Der Klick nimmt an,
 * das ist der eigentliche Tempogewinn.
 *
 * Zeitfenster, die rechnerisch nicht mehr passen, bleiben sichtbar und waehlbar:
 * genau dafuer ruft jemand an. Ob es wirklich geht, entscheidet der Server.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TelefonannahmeScreen(
    repository: ReservationRepository,
    onBack: () -> Unit,
    onUnauthorized: () -> Unit,
    startdatum: LocalDate = LocalDate.now(),
) {
    // Der Schluessel haengt am Startdatum: kommt man aus dem Monatskalender mit
    // einem anderen Tag herein, soll der Schirm dort stehen und nicht den
    // gemerkten Zustand des letzten Aufrufs zeigen.
    val vm: TelefonannahmeViewModel = viewModel(key = "telefonannahme-$startdatum") {
        TelefonannahmeViewModel(repository, startdatum)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    var kalender by remember { mutableStateOf(false) }

    LaunchedEffect(state.unauthorized) { if (state.unauthorized) onUnauthorized() }

    if (kalender) {
        val zustand = rememberDatePickerState(
            initialSelectedDateMillis = state.datum.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { kalender = false },
            confirmButton = {
                TextButton(onClick = {
                    zustand.selectedDateMillis?.let {
                        vm.setDatum(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    kalender = false
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { kalender = false }) { Text("Abbrechen") } },
        ) { DatePicker(state = zustand) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Telefonannahme") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = vm::neuLaden) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Neu laden")
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            // Am Telefon stehen die Gastfelder untereinander, auf dem Desktop
            // nebeneinander wie auf der Weboberflaeche.
            val breit = maxWidth >= 720.dp

            Column(
                Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.fehler?.let { ErrorCard(it, onRetry = vm::neuLaden) }

                TagKarte(state, vm, breit) { kalender = true }

                val tag = state.tag
                when {
                    state.laden && tag == null -> LoadingBox()
                    tag != null -> {
                        BelegungKarte(state, tag, vm)
                        GastKarte(state, vm, breit)
                    }
                }
            }
        }
    }
}

// --- Karte 1: Tag -------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TagKarte(
    state: AnnahmeState,
    vm: TelefonannahmeViewModel,
    breit: Boolean,
    onKalender: () -> Unit,
) {
    Karte("TAG") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            schnellwahl(LocalDate.now()).forEach { (datum, beschriftung) ->
                FilterChip(
                    selected = state.datum == datum,
                    onClick = { vm.setDatum(datum) },
                    label = { Text(beschriftung) },
                )
            }
            OutlinedButton(onClick = onKalender) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(state.datum.display())
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = { vm.tagWeiter(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Tag zurück")
            }
            IconButton(onClick = { vm.tagWeiter(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Tag vor")
            }

            Text("Personen", style = MaterialTheme.typography.labelLarge)
            FilledTonalIconButton(onClick = { vm.setGaeste(state.gaeste - 1) }, enabled = state.gaeste > 1) {
                Icon(Icons.Outlined.Remove, contentDescription = "weniger")
            }
            Text(
                state.gaeste.toString(),
                Modifier.widthIn(min = 34.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            FilledTonalIconButton(onClick = { vm.setGaeste(state.gaeste + 1) }) {
                Icon(Icons.Outlined.Add, contentDescription = "mehr")
            }

            if (breit) {
                Spacer(Modifier.width(8.dp))
                Raumwahl(state, vm, Modifier.widthIn(min = 220.dp))
            }
        }

        state.fehlerZu("gaeste")?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        if (!breit) Raumwahl(state, vm, Modifier.fillMaxWidth())

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ohne Tisch und ohne Raum", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Wenn die automatische Vergabe nicht passt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.ohneTisch, onCheckedChange = vm::setOhneTisch)
        }

        HorizontalDivider()

        Sperrbereich(state, vm, breit)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Raumwahl(state: AnnahmeState, vm: TelefonannahmeViewModel, modifier: Modifier = Modifier) {
    var offen by remember { mutableStateOf(false) }
    val raeume = state.tag?.raeume.orEmpty()
    val gewaehlt = raeume.firstOrNull { it.id == state.raumId }

    ExposedDropdownMenuBox(expanded = offen, onExpandedChange = { offen = !offen }, modifier = modifier) {
        OutlinedTextField(
            value = gewaehlt?.let { "${it.name} (bis ${it.maxPlaetze})" } ?: "Tisch (automatisch)",
            onValueChange = {},
            readOnly = true,
            label = { Text("Raum") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = offen) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            DropdownMenuItem(
                text = { Text("Tisch (automatisch)") },
                onClick = { vm.setRaum(null); offen = false },
            )
            raeume.forEach { raum ->
                DropdownMenuItem(
                    text = { Text("${raum.name} (bis ${raum.maxPlaetze})") },
                    onClick = { vm.setRaum(raum.id); offen = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Sperrbereich(state: AnnahmeState, vm: TelefonannahmeViewModel, breit: Boolean) {
    val gesperrt = state.tag?.gesperrt == true

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!gesperrt) {
            OutlinedTextField(
                value = state.grund,
                onValueChange = vm::setGrund,
                placeholder = { Text("Grund (optional), z. B. Betriebsferien") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                "Dieser Tag ist gesperrt" + state.tag.grund.takeIf { it.isNotBlank() }
                    ?.let { ": $it" }.orEmpty(),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        OutlinedButton(onClick = vm::sperreUmschalten, enabled = !state.sperrtLaeuft) {
            Text(if (gesperrt) "Freigeben" else "Diesen Tag sperren")
        }
    }

    val sperren = state.tag?.sperren.orEmpty()
    if (sperren.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Gesperrt:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            sperren.keys.sorted().forEach { text ->
                val datum = runCatching { LocalDate.parse(text) }.getOrNull()
                Text(
                    datum?.format(KURZ) ?: text,
                    Modifier.clickable(enabled = datum != null) { datum?.let(vm::setDatum) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// --- Karte 2: Belegung --------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BelegungKarte(state: AnnahmeState, tag: Tagesdaten, vm: TelefonannahmeViewModel) {
    Karte("BELEGUNG AM ${tag.datum.format(LANG).uppercase()}") {
        // Der Text eines Sperrvermerks ist am Telefon oft die wichtigste Angabe des
        // Tages - dort stehen die Essenszeiten und die Hoechstzahl.
        tag.vermerke.forEach { vermerk ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Text(
                    vermerk.kommentar.ifBlank { "Sperrvermerk ohne Text" },
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        Text(
            buildString {
                if (tag.maxPax != null) {
                    append("Höchstens ${tag.maxPax} Plätze")
                } else {
                    append("${tag.tischeGesamt} Tische · ${tag.plaetzeGesamt} Plätze gesamt")
                }
                append(" · ${tag.reservierungen.count { !it.istVermerk }} Reservierungen an diesem Tag")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (tag.belegung.isEmpty()) {
            Text(
                "Für diesen Tag sind keine Zeiten vorgesehen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Karte
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tag.belegung.forEach { fenster ->
                Zeitfeld(
                    fenster = fenster,
                    gewaehlt = fenster.zeit == state.zeit,
                    laeuft = state.speichern && fenster.zeit == state.zeit,
                    onClick = { vm.zeitGeklickt(fenster.zeit) },
                )
            }
        }

        Text(
            "Ein Klick auf die Uhrzeit nimmt die Reservierung mit den unten eingetragenen Daten an. " +
                "Grau bedeutet: für ${state.gaeste} " +
                (if (state.gaeste == 1) "Person" else "Personen") + " ist kein Tisch mehr frei.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Zeitfeld(
    fenster: Zeitfenster,
    gewaehlt: Boolean,
    laeuft: Boolean,
    onClick: () -> Unit,
) {
    val farben = MaterialTheme.colorScheme

    // Die farbige Kante links bedeutet "wird knapp" - auf der Weboberflaeche bei
    // ein oder zwei freien Tischen. Nicht dasselbe wie grau: grau heisst, dass fuer
    // diese Personenzahl gar keiner mehr passt.
    val kante = when {
        gewaehlt -> farben.primary
        fenster.knapp -> farben.tertiary
        else -> farben.outlineVariant
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = when {
                gewaehlt -> farben.primaryContainer
                fenster.passt -> farben.surface
                else -> farben.surfaceVariant.copy(alpha = 0.6f)
            },
        ),
        border = BorderStroke(if (fenster.knapp || gewaehlt) 2.dp else 1.dp, kante),
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    fenster.zeit.display(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (fenster.passt) farben.onSurface else farben.onSurfaceVariant,
                )
                Text(
                    fenster.anzeige,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (fenster.passt) farben.onSurfaceVariant else farben.error,
                )
            }
            if (laeuft) {
                Spacer(Modifier.width(10.dp))
                CircularProgressIndicator(Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
            }
        }
    }
}

// --- Karte 3: Gast ------------------------------------------------------------------

@Composable
private fun GastKarte(state: AnnahmeState, vm: TelefonannahmeViewModel, breit: Boolean) {
    Karte("GAST") {
        if (breit) {
            // Nebeneinander wie auf der Weboberflaeche. weight() gibt es nur
            // innerhalb der Row, deshalb stehen die Aufrufe zweimal da statt
            // einmal in einer Hilfsfunktion.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Feld(
                    state.nachname, vm::setNachname, "Nachname *", state.fehlerZu("nachname"),
                    KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    Modifier.weight(1f),
                )
                Feld(
                    state.telefon, vm::setTelefon, "Telefon *", state.fehlerZu("telefon"),
                    KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    Modifier.weight(1f),
                )
                Feld(
                    state.email, vm::setEmail, "E-Mail (optional)", state.fehlerZu("email"),
                    KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    Modifier.weight(1f),
                )
            }
        } else {
            Feld(
                state.nachname, vm::setNachname, "Nachname *", state.fehlerZu("nachname"),
                KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                Modifier.fillMaxWidth(),
            )
            Feld(
                state.telefon, vm::setTelefon, "Telefon *", state.fehlerZu("telefon"),
                KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                Modifier.fillMaxWidth(),
            )
            Feld(
                state.email, vm::setEmail, "E-Mail (optional)", state.fehlerZu("email"),
                KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = state.notiz,
            onValueChange = vm::setNotiz,
            label = { Text("Notiz (optional)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = vm::annehmen,
            enabled = state.bereit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.speichern) {
                CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(
                if (state.zeit == null) {
                    "Reservierung annehmen"
                } else {
                    "Reservierung annehmen für ${state.zeit.display()} Uhr"
                },
            )
        }

        Text(
            "Wird sofort als bestätigt gespeichert. Der Tisch wird automatisch zugewiesen. " +
                "Es wird keine E-Mail verschickt – weder an den Gast noch ans Haus. " +
                "Ohne Klick auf eine Uhrzeit oben fehlt die Zeit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.angelegt?.let { angelegt ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Angenommen",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "#${angelegt.id} · ${angelegt.zeit.display()} Uhr · ${angelegt.gaeste} Personen · " +
                            "${angelegt.name} · ${angelegt.tischeText}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = vm::weiter) { Text("Nächster Anruf") }
                }
            }
        }
    }
}

@Composable
private fun Feld(
    wert: String,
    onWert: (String) -> Unit,
    beschriftung: String,
    fehler: String?,
    optionen: KeyboardOptions,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = wert,
        onValueChange = onWert,
        label = { Text(beschriftung) },
        singleLine = true,
        isError = fehler != null,
        supportingText = fehler?.let { { Text(it) } },
        keyboardOptions = optionen,
        modifier = modifier,
    )
}

// --- Bausteine ----------------------------------------------------------------------

@Composable
private fun Karte(titel: String, inhalt: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                titel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            inhalt()
        }
    }
}

/** Heute, Morgen und die drei folgenden Tage - die Spanne, in der am Telefon gebucht wird. */
private fun schnellwahl(heute: LocalDate): List<Pair<LocalDate, String>> = listOf(
    heute to "Heute",
    heute.plusDays(1) to "Morgen",
) + (2L..4L).map { heute.plusDays(it) to heute.plusDays(it).format(KURZ) }

private val KURZ = DateTimeFormatter.ofPattern("EE d.M.", Locale.GERMAN)
private val LANG = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
