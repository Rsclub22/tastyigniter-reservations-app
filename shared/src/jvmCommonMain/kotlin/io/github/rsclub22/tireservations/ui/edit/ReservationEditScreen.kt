package io.github.rsclub22.tireservations.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rsclub22.tireservations.ui.components.Meldungen
import io.github.rsclub22.tireservations.data.DiningTable
import io.github.rsclub22.tireservations.data.ReservationRepository
import io.github.rsclub22.tireservations.data.SettingsStore
import io.github.rsclub22.tireservations.ui.components.ErrorCard
import io.github.rsclub22.tireservations.ui.components.LoadingBox
import io.github.rsclub22.tireservations.ui.components.LongDateFormat
import io.github.rsclub22.tireservations.ui.components.display
import io.github.rsclub22.tireservations.ui.components.senkrechtSchiebbar
import io.github.rsclub22.tireservations.ui.components.statusLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReservationEditScreen(
    repository: ReservationRepository,
    settingsStore: SettingsStore,
    reservationId: Long?,
    initialDate: LocalDate?,
    onBack: () -> Unit,
    onSaved: (id: Long, isNew: Boolean) -> Unit,
    onUnauthorized: () -> Unit,
) {
    val vm: ReservationEditViewModel = viewModel(key = "edit-$reservationId-$initialDate") {
        ReservationEditViewModel(repository, settingsStore, reservationId, initialDate)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val d = state.draft
    var showDate by remember { mutableStateOf(false) }
    // Which time the picker dialog edits: start or end of the stay.
    var timePicker by remember { mutableStateOf<TimeTarget?>(null) }

    LaunchedEffect(state.savedId) {
        state.savedId?.let { id ->
            state.warning?.let { Meldungen.zeige(it) }
            onSaved(id, state.isNew)
        }
    }
    LaunchedEffect(state.unauthorized) { if (state.unauthorized) onUnauthorized() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Neue Reservierung" else "Reservierung bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    TextButton(onClick = vm::save, enabled = !state.loading && !state.saving) { Text("Speichern") }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .senkrechtSchiebbar()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val err = state.fieldErrors

            SectionTitle("Wann & wo")
            if (state.locations.size > 1 || d.locationId == null) {
                LocationDropdown(state, err["location_id"]) { id -> vm.edit { it.copy(locationId = id) } }
            }
            PickerField(
                label = "Datum",
                value = d.date.format(LongDateFormat),
                icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                error = err["reserve_date"],
                onClick = { showDate = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerField(
                    label = "Beginn",
                    value = d.time.display(),
                    icon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                    error = err["reserve_time"],
                    onClick = { timePicker = TimeTarget.START },
                    modifier = Modifier.weight(1f),
                )
                PickerField(
                    label = "Ende",
                    value = d.duration?.let { endLabel(d.time, it) } ?: "Standard",
                    icon = { Icon(Icons.Outlined.TimerOff, contentDescription = null) },
                    error = err["duration"],
                    onClick = { timePicker = TimeTarget.END },
                    modifier = Modifier.weight(1f),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Personen", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(
                            onClick = { vm.edit { it.copy(guestNum = (it.guestNum - 1).coerceAtLeast(1)) } },
                        ) { Icon(Icons.Outlined.Remove, contentDescription = "Weniger") }
                        Text(
                            "${d.guestNum}",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(48.dp),
                        )
                        FilledTonalIconButton(
                            onClick = { vm.edit { it.copy(guestNum = (it.guestNum + 1).coerceAtMost(999)) } },
                        ) { Icon(Icons.Outlined.Add, contentDescription = "Mehr") }
                    }
                    err["guest_num"]?.let { ErrorText(it) }
                }
                OutlinedTextField(
                    value = state.durationText,
                    onValueChange = vm::setDuration,
                    label = { Text("Dauer (Min.)") },
                    placeholder = { Text("Standard") },
                    supportingText = { Text("oder Ende wählen") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            SectionTitle("Gast")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField("Vorname", d.firstName, err["first_name"], Modifier.weight(1f), capitalize = true) { v ->
                    vm.edit { it.copy(firstName = v) }
                }
                FormField("Nachname", d.lastName, err["last_name"], Modifier.weight(1f), capitalize = true) { v ->
                    vm.edit { it.copy(lastName = v) }
                }
            }
            FormField("Telefon", d.telephone, err["telephone"], Modifier.fillMaxWidth(), keyboardType = KeyboardType.Phone) { v ->
                vm.edit { it.copy(telephone = v) }
            }
            FormField("E-Mail (optional)", d.email, err["email"], Modifier.fillMaxWidth(), keyboardType = KeyboardType.Email) { v ->
                vm.edit { it.copy(email = v) }
            }
            OutlinedTextField(
                value = d.comment,
                onValueChange = { v -> vm.edit { it.copy(comment = v.take(520)) } },
                label = { Text("Anmerkungen") },
                placeholder = { Text("z. B. Kinderstuhl, Allergien, Anlass") },
                minLines = 2,
                isError = err["comment"] != null,
                supportingText = { Text(err["comment"] ?: "${d.comment.length}/520") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.tables.isNotEmpty()) {
                SectionTitle("Tisch")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.tables.forEach { t ->
                        FilterChip(
                            selected = t.id in d.tableIds,
                            onClick = { vm.toggleTable(t.id) },
                            label = { Text(tableLabel(t)) },
                            enabled = fits(t, d.guestNum) || t.id in d.tableIds,
                        )
                    }
                }
                Text(
                    "Ohne Auswahl weist TastyIgniter je nach Einstellung automatisch einen Tisch zu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.isNew && state.statuses.isNotEmpty()) {
                SectionTitle("Status")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.statuses.forEach { s ->
                        FilterChip(
                            selected = d.statusId == s.id,
                            onClick = { vm.edit { it.copy(statusId = if (it.statusId == s.id) null else s.id) } },
                            label = { Text(statusLabel(s)) },
                        )
                    }
                }
            }

            state.error?.let { ErrorCard(it) }

            Button(
                onClick = vm::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(if (state.isNew) "Reservierung anlegen" else "Änderungen speichern")
            }
        }
    }

    if (showDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = d.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        vm.edit { it.copy(date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()) }
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Abbrechen") } },
        ) { DatePicker(state = pickerState) }
    }

    timePicker?.let { target ->
        val initial = when (target) {
            TimeTarget.START -> d.time
            // Without a duration, suggest two hours after the start.
            TimeTarget.END -> d.time.plusMinutes((d.duration ?: 120).toLong())
        }
        // Keyed by target so the picker restarts from the right time for start and end.
        val timeState = key(target) {
            rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        }
        AlertDialog(
            onDismissRequest = { timePicker = null },
            title = { Text(if (target == TimeTarget.START) "Beginn wählen" else "Ende wählen") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val picked = LocalTime.of(timeState.hour, timeState.minute)
                    // Moving the start keeps the duration, so the end moves along.
                    if (target == TimeTarget.START) vm.edit { it.copy(time = picked) } else vm.setEnd(picked)
                    timePicker = null
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (target == TimeTarget.END && d.duration != null) {
                        TextButton(onClick = { vm.setEnd(null); timePicker = null }) { Text("Standard") }
                    }
                    TextButton(onClick = { timePicker = null }) { Text("Abbrechen") }
                }
            },
        )
    }
}

private enum class TimeTarget { START, END }

/** End of the stay, with the number of days it runs past the start day. */
private fun endLabel(start: LocalTime, durationMinutes: Int): String {
    val end = start.plusMinutes(durationMinutes.toLong())
    return end.display() + when (val days = ReservationEditViewModel.daysAfterStart(start, durationMinutes)) {
        0 -> ""
        1 -> " (+1 Tag)"
        else -> " (+$days Tage)"
    }
}

private fun fits(t: DiningTable, guests: Int): Boolean =
    (t.maxCapacity == null || guests <= t.maxCapacity) && (t.minCapacity == null || guests >= t.minCapacity)

private fun tableLabel(t: DiningTable): String {
    val cap = when {
        t.minCapacity != null && t.maxCapacity != null -> " (${t.minCapacity}–${t.maxCapacity})"
        t.maxCapacity != null -> " (bis ${t.maxCapacity})"
        else -> ""
    }
    return t.name + cap
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun FormField(
    label: String,
    value: String,
    error: String?,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalize: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None,
            imeAction = ImeAction.Next,
        ),
        modifier = modifier,
    )
}

@Composable
private fun PickerField(
    label: String,
    value: String,
    icon: @Composable () -> Unit,
    error: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    icon()
                    Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        error?.let { ErrorText(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationDropdown(state: EditState, error: String?, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = state.locations.firstOrNull { it.id == state.draft.locationId }?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Standort") },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.locations.forEach { loc ->
                DropdownMenuItem(text = { Text(loc.name) }, onClick = { onSelect(loc.id); expanded = false })
            }
        }
    }
}

