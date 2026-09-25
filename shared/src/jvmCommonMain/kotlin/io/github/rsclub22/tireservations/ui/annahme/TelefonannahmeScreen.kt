package io.github.rsclub22.tireservations.ui.annahme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import io.github.rsclub22.tireservations.ui.components.relativeDayLabel
import java.time.Instant
import java.time.ZoneOffset

/**
 * Telefonannahme: eintragen, waehrend der Gast am Telefon ist.
 *
 * Absichtlich knapp gehalten. Nachname und Telefonnummer genuegen, der Status ist
 * sofort bestaetigt, es wird nichts verschickt. Die Zeitfenster kommen vom Server
 * samt Belegung - auch die, die rechnerisch nicht mehr passen: das Aussortieren
 * waere hier falsch, denn genau dafuer ruft jemand an. Ob es wirklich geht,
 * entscheidet der Server beim Annehmen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TelefonannahmeScreen(
    repository: ReservationRepository,
    onBack: () -> Unit,
    onUnauthorized: () -> Unit,
) {
    val vm: TelefonannahmeViewModel = viewModel(key = "telefonannahme") {
        TelefonannahmeViewModel(repository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    var showDate by remember { mutableStateOf(false) }

    LaunchedEffect(state.unauthorized) { if (state.unauthorized) onUnauthorized() }

    if (showDate) {
        val pickerState = rememberDatePickerStateFor(state.datum)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        vm.setDatum(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDate = false
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Abbrechen") } },
        ) { DatePicker(state = pickerState) }
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
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Datumszeile(
                datum = state.datum,
                onZurueck = { vm.tagWeiter(-1) },
                onVor = { vm.tagWeiter(1) },
                onKalender = { showDate = true },
            )

            Gaestezahl(
                gaeste = state.gaeste,
                fehler = state.fehlerZu("gaeste"),
                onAendern = vm::setGaeste,
            )

            state.fehler?.let { ErrorCard(it, onRetry = vm::neuLaden) }

            val tag = state.tag
            when {
                state.laden && tag == null -> LoadingBox()
                tag != null -> {
                    Tageskopf(tag)

                    Zeitfenstergitter(
                        belegung = tag.belegung,
                        gewaehlt = state.zeit,
                        onWaehlen = vm::setZeit,
                    )

                    if (tag.raeume.isNotEmpty()) {
                        Raumwahl(tag, state.raumId, state.ohneTisch, vm::setRaum)
                    }

                    OhneTischSchalter(state.ohneTisch, vm::setOhneTisch)

                    HorizontalDivider()

                    Gastfelder(
                        nachname = state.nachname,
                        telefon = state.telefon,
                        notiz = state.notiz,
                        nachnameFehler = state.fehlerZu("nachname"),
                        telefonFehler = state.fehlerZu("telefon"),
                        onNachname = vm::setNachname,
                        onTelefon = vm::setTelefon,
                        onNotiz = vm::setNotiz,
                    )

                    Button(
                        onClick = vm::annehmen,
                        enabled = state.bereit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.speichern) {
                            CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            when {
                                state.zeit == null -> "Zuerst eine Uhrzeit wählen"
                                else -> "Annehmen für ${state.zeit.display()} Uhr"
                            },
                        )
                    }

                    state.angelegt?.let { angelegt ->
                        Bestaetigung(
                            text = "#${angelegt.id} · ${angelegt.zeit.display()} · ${angelegt.gaeste} Personen · " +
                                "${angelegt.name} · ${angelegt.tischeText}",
                            onWeiter = vm::weiter,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Datumszeile(
    datum: java.time.LocalDate,
    onZurueck: () -> Unit,
    onVor: () -> Unit,
    onKalender: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onZurueck) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Tag zurück")
        }
        Column(Modifier.weight(1f)) {
            Text(datum.display(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                relativeDayLabel(datum),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onKalender) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = "Datum wählen")
        }
        IconButton(onClick = onVor) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Tag vor")
        }
    }
}

@Composable
private fun Gaestezahl(gaeste: Int, fehler: String?, onAendern: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Personen", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            FilledTonalIconButton(onClick = { onAendern(gaeste - 1) }, enabled = gaeste > 1) {
                Icon(Icons.Outlined.Remove, contentDescription = "weniger")
            }
            Text(
                gaeste.toString(),
                Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            FilledTonalIconButton(onClick = { onAendern(gaeste + 1) }) {
                Icon(Icons.Outlined.Add, contentDescription = "mehr")
            }
        }
        // Hier landet die Hoechstzahl aus einem Sperrvermerk, wenn der Server sie meldet.
        fehler?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Tageskopf(tag: Tagesdaten) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tag.gesperrt) {
            Hinweis(
                "Dieser Tag ist für die Online-Buchung gesperrt" +
                    tag.grund.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty(),
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        // Der Text eines Sperrvermerks ist am Telefon oft die wichtigste Angabe des
        // Tages - dort stehen die Essenszeiten und die Hoechstzahl.
        tag.vermerke.forEach { vermerk ->
            Hinweis(
                vermerk.kommentar.ifBlank { "Sperrvermerk ohne Text" },
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }

        Text(
            buildString {
                append("${tag.reservierungen.count { !it.istVermerk }} Reservierungen")
                append(" · ${tag.gaesteGesamt} Gäste")
                if (tag.maxPax != null) append(" · Höchstzahl ${tag.maxPax}")
                else append(" · ${tag.tischeGesamt} Tische, ${tag.plaetzeGesamt} Plätze")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Hinweis(text: String, hintergrund: androidx.compose.ui.graphics.Color, vordergrund: androidx.compose.ui.graphics.Color) {
    Card(colors = CardDefaults.cardColors(containerColor = hintergrund)) {
        Text(
            text,
            Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = vordergrund,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Zeitfenstergitter(
    belegung: List<Zeitfenster>,
    gewaehlt: java.time.LocalTime?,
    onWaehlen: (java.time.LocalTime) -> Unit,
) {
    Column {
        Text("Uhrzeit", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(8.dp))

        if (belegung.isEmpty()) {
            Text(
                "Für diesen Tag sind keine Zeiten vorgesehen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            belegung.forEach { fenster ->
                FilterChip(
                    selected = fenster.zeit == gewaehlt,
                    onClick = { onWaehlen(fenster.zeit) },
                    label = {
                        Column {
                            Text(fenster.zeit.display())
                            Text(
                                fenster.anzeige,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (fenster.passt) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Raumwahl(tag: Tagesdaten, raumId: Long?, ohneTisch: Boolean, onWaehlen: (Long?) -> Unit) {
    Column {
        Text("Raum (statt Tisch)", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = raumId == null && !ohneTisch,
                onClick = { onWaehlen(null) },
                label = { Text("Tisch automatisch") },
            )
            tag.raeume.forEach { raum ->
                FilterChip(
                    selected = raumId == raum.id,
                    onClick = { onWaehlen(raum.id) },
                    label = { Text("${raum.name} (bis ${raum.maxPlaetze})") },
                )
            }
        }
    }
}

@Composable
private fun OhneTischSchalter(an: Boolean, onAendern: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Ohne Tisch und ohne Raum", style = MaterialTheme.typography.titleSmall)
            Text(
                "Für Fälle, in denen die automatische Vergabe nicht passt. Die Zuordnung " +
                    "macht dann jemand von Hand.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = an, onCheckedChange = onAendern)
    }
}

@Composable
private fun Gastfelder(
    nachname: String,
    telefon: String,
    notiz: String,
    nachnameFehler: String?,
    telefonFehler: String?,
    onNachname: (String) -> Unit,
    onTelefon: (String) -> Unit,
    onNotiz: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = nachname,
            onValueChange = onNachname,
            label = { Text("Nachname") },
            singleLine = true,
            isError = nachnameFehler != null,
            supportingText = nachnameFehler?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = telefon,
            onValueChange = onTelefon,
            label = { Text("Telefon") },
            singleLine = true,
            isError = telefonFehler != null,
            supportingText = telefonFehler?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = notiz,
            onValueChange = onNotiz,
            label = { Text("Notiz (optional)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Bestaetigung(text: String, onWeiter: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Angenommen", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onWeiter) { Text("Nächster Anruf") }
        }
    }
}

/**
 * Der Zustand des Datumswählers haengt am Tag: wechselt der Tag, soll der Kalender
 * beim naechsten Oeffnen dort stehen und nicht beim alten Datum.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberDatePickerStateFor(datum: java.time.LocalDate) =
    androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = datum.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
