package io.github.rsclub22.tireservations.ui.blatt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Card
import java.time.LocalTime
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rsclub22.tireservations.data.Blatt
import io.github.rsclub22.tireservations.data.BlattTag
import io.github.rsclub22.tireservations.data.ReservationRepository
import io.github.rsclub22.tireservations.ui.components.ErrorCard
import io.github.rsclub22.tireservations.ui.components.LoadingBox
import io.github.rsclub22.tireservations.ui.components.display
import io.github.rsclub22.tireservations.ui.components.senkrechtSchiebbar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Das Tagesblatt: Vorschau auf dem Schirm, Ausgabe auf Papier.
 *
 * Dieselben Spalten wie die Druckansicht unter /intern/druck, damit niemand
 * umlernen muss, wenn die App die Seite ersetzt.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagesblattScreen(
    repository: ReservationRepository,
    onBack: () -> Unit,
    onUnauthorized: () -> Unit,
    startdatum: LocalDate = LocalDate.now(),
) {
    val vm: TagesblattViewModel = viewModel(key = "tagesblatt-$startdatum") {
        TagesblattViewModel(repository, startdatum)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var waehleVon by remember { mutableStateOf(false) }
    var waehleBis by remember { mutableStateOf(false) }
    var waehleTrennzeit by remember { mutableStateOf(false) }

    LaunchedEffect(state.unauthorized) { if (state.unauthorized) onUnauthorized() }
    LaunchedEffect(state.meldung) {
        state.meldung?.let { snackbar.showSnackbar(it); vm.meldungGesehen() }
    }

    if (waehleTrennzeit) {
        val vorgabe = (state.trennung as? Trennung.Um)?.zeit
            ?: state.blatt?.trennzeit
            ?: LocalTime.of(15, 0)
        val zustand = rememberTimePickerState(
            initialHour = vorgabe.hour,
            initialMinute = vorgabe.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { waehleTrennzeit = false },
            title = { Text("Ab wann das zweite Blatt?") },
            text = { TimePicker(state = zustand) },
            confirmButton = {
                TextButton(onClick = {
                    vm.setTrennung(Trennung.Um(LocalTime.of(zustand.hour, zustand.minute)))
                    waehleTrennzeit = false
                }) { Text("Übernehmen") }
            },
            dismissButton = {
                TextButton(onClick = { waehleTrennzeit = false }) { Text("Abbrechen") }
            },
        )
    }

    if (waehleVon) {
        DatumsWahl(state.von, onAbbruch = { waehleVon = false }) { vm.setVon(it); waehleVon = false }
    }
    if (waehleBis) {
        DatumsWahl(state.bis, onAbbruch = { waehleBis = false }) { vm.setBis(it); waehleBis = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tagesblatt") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.tagWeiter(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Tag zurück")
                    }
                    IconButton(onClick = { vm.tagWeiter(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Tag vor")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = vm::drucken,
                icon = { Icon(Icons.Outlined.Print, contentDescription = null) },
                text = { Text("Drucken") },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).senkrechtSchiebbar().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Zeitraumwahl(
                von = state.von,
                bis = state.bis,
                onVon = { waehleVon = true },
                onBis = { waehleBis = true },
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(Trennung.Vorgabe, Trennung.Keine).forEach { trennung ->
                    FilterChip(
                        selected = state.trennung == trennung,
                        onClick = { vm.setTrennung(trennung) },
                        label = { Text(trennung.beschriftung) },
                    )
                }
                // Dritte Moeglichkeit: eine Zeit von Hand. An Tagen mit anderem
                // Ablauf - zwei Gaenge an Weihnachten etwa - liegt die Trennung
                // woanders als die 15 Uhr des Wochenbetriebs.
                val eigene = state.trennung as? Trennung.Um
                FilterChip(
                    selected = eigene != null,
                    onClick = { waehleTrennzeit = true },
                    label = { Text(eigene?.beschriftung ?: "Uhrzeit wählen …") },
                    leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                )
            }

            state.fehler?.let { ErrorCard(it, onRetry = vm::neuLaden) }

            val blatt = state.blatt
            when {
                state.laden && blatt == null -> LoadingBox()
                blatt != null && blatt.tage.isEmpty() -> Text(
                    "Für diesen Zeitraum gibt es nichts zu drucken.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                blatt != null -> blatt.tage.forEach { TagVorschau(it) }
            }
        }
    }
}

@Composable
private fun Zeitraumwahl(
    von: LocalDate,
    bis: LocalDate,
    onVon: () -> Unit,
    onBis: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onVon) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
            Text("  ${von.display()}")
        }
        Text("bis", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onBis) { Text(bis.display()) }
    }
}

@Composable
private fun TagVorschau(tag: BlattTag) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            tag.datum.display(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        if (tag.gesperrt) {
            Text(
                "Für die Online-Buchung gesperrt" +
                    tag.grund.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        tag.sperrvermerke.forEach { vermerk ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Sperrvermerk" + (tag.maxPax?.let { " · höchstens $it Plätze" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (vermerk.kommentar.isNotBlank()) {
                        Text(vermerk.kommentar, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (tag.blaetter.isEmpty()) {
            Text(
                "Keine Reservierungen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            tag.blaetter.forEach { AbschnittVorschau(it) }
        }

        HorizontalDivider()
    }
}

@Composable
private fun AbschnittVorschau(abschnitt: Blatt) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "${abschnitt.titel} · ${abschnitt.reservierungen.size} Reservierungen · ${abschnitt.gaeste} Gäste",
            style = MaterialTheme.typography.titleSmall,
        )
        abschnitt.reservierungen.forEach { r ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    r.zeit.display(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(Modifier.weight(1f)) {
                    Text(r.name.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${r.gaeste} Pers. · ${r.tischeText}" +
                            r.telefon.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "#${r.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatumsWahl(datum: LocalDate, onAbbruch: () -> Unit, onWahl: (LocalDate) -> Unit) {
    val zustand = rememberDatePickerState(
        initialSelectedDateMillis = datum.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onAbbruch,
        confirmButton = {
            TextButton(onClick = {
                zustand.selectedDateMillis?.let {
                    onWahl(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
            }) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onAbbruch) { Text("Abbrechen") } },
    ) { DatePicker(state = zustand) }
}
