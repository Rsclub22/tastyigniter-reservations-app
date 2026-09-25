package io.github.rsclub22.tireservations.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rsclub22.tireservations.data.Reservation
import io.github.rsclub22.tireservations.data.ReservationRepository
import io.github.rsclub22.tireservations.data.SettingsStore
import io.github.rsclub22.tireservations.ui.components.ErrorCard
import io.github.rsclub22.tireservations.ui.components.LoadingBox
import io.github.rsclub22.tireservations.ui.components.LongDateFormat
import io.github.rsclub22.tireservations.ui.components.StatusBadge
import io.github.rsclub22.tireservations.ui.components.display
import io.github.rsclub22.tireservations.ui.components.parseHexColor
import io.github.rsclub22.tireservations.ui.components.relativeDayLabel
import io.github.rsclub22.tireservations.ui.components.statusLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationListScreen(
    repository: ReservationRepository,
    settingsStore: SettingsStore,
    onOpen: (Long) -> Unit,
    onCreate: (LocalDate) -> Unit,
    onSettings: () -> Unit,
    onUnauthorized: () -> Unit,
) {
    val vm: ReservationListViewModel = viewModel { ReservationListViewModel(repository, settingsStore) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    // Returning from detail/edit screens re-enters composition: refresh the list then.
    LaunchedEffect(Unit) { vm.onScreenShown() }
    LaunchedEffect(state.unauthorized) { if (state.unauthorized) onUnauthorized() }

    Scaffold(
        topBar = {
            if (state.searchActive) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { vm.setSearchActive(false) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Suche schließen")
                        }
                    },
                    title = {
                        TextField(
                            value = state.search,
                            onValueChange = vm::setSearch,
                            placeholder = { Text("Name, E-Mail, Telefon, Nr.") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            trailingIcon = {
                                if (state.search.isNotEmpty()) {
                                    IconButton(onClick = { vm.setSearch("") }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Leeren")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Reservierungen") },
                    actions = {
                        IconButton(onClick = { vm.setSearchActive(true) }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Suchen")
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Einstellungen")
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onCreate(state.date) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Neue Reservierung") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!state.isSearching) {
                DateBar(
                    date = state.date,
                    onPrev = { vm.shiftDate(-1) },
                    onNext = { vm.shiftDate(1) },
                    onToday = { vm.setDate(LocalDate.now()) },
                    onPick = { showDatePicker = true },
                )
            }
            FilterRow(state, vm)

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading -> LoadingBox()
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        state.error?.let { msg ->
                            item { ErrorCard(msg, onRetry = vm::refresh) }
                        }
                        if (state.error == null) {
                            item {
                                Text(
                                    summary(state),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }
                        items(state.reservations, key = { it.id }) { r ->
                            ReservationCard(r, showDate = state.isSearching, onClick = { onOpen(r.id) })
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        vm.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") } },
        ) { DatePicker(state = pickerState) }
    }
}

private fun summary(state: ListState): String {
    val n = state.reservations.size
    val count = if (n == 1) "1 Reservierung" else "$n Reservierungen"
    val guests = if (state.guestTotal == 1) "1 Gast" else "${state.guestTotal} Gäste"
    return if (state.isSearching) "$count gefunden" else "$count · $guests"
}

@Composable
private fun DateBar(date: LocalDate, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Vorheriger Tag")
        }
        Column(
            Modifier.weight(1f).clickable(onClick = onPick).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(relativeDayLabel(date), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                date.format(LongDateFormat),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (date != LocalDate.now()) {
            TextButton(onClick = onToday) { Text("Heute") }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Nächster Tag")
        }
    }
}

@Composable
private fun FilterRow(state: ListState, vm: ReservationListViewModel) {
    var locationMenu by remember { mutableStateOf(false) }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.locations.size > 1) {
            item {
                Box {
                    FilterChip(
                        selected = state.locationId != null,
                        onClick = { locationMenu = true },
                        leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = {
                            Text(state.locations.firstOrNull { it.id == state.locationId }?.name ?: "Alle Standorte")
                        },
                    )
                    DropdownMenu(expanded = locationMenu, onDismissRequest = { locationMenu = false }) {
                        DropdownMenuItem(text = { Text("Alle Standorte") }, onClick = {
                            vm.setLocation(null); locationMenu = false
                        })
                        state.locations.forEach { loc ->
                            DropdownMenuItem(text = { Text(loc.name) }, onClick = {
                                vm.setLocation(loc.id); locationMenu = false
                            })
                        }
                    }
                }
            }
        }
        item {
            FilterChip(selected = state.statusId == null, onClick = { vm.setStatus(null) }, label = { Text("Alle") })
        }
        items(state.statuses, key = { it.id }) { status ->
            FilterChip(
                selected = state.statusId == status.id,
                onClick = { vm.setStatus(if (state.statusId == status.id) null else status.id) },
                label = { Text(statusLabel(status)) },
            )
        }
    }
}

@Composable
private fun ReservationCard(r: Reservation, showDate: Boolean, onClick: () -> Unit) {
    val accent = parseHexColor(r.statusColor) ?: MaterialTheme.colorScheme.primary
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(48.dp).background(accent, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                Text(r.time.display(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (showDate) {
                    Text(r.date.display(), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    r.customerName.ifBlank { "Reservierung #${r.id}" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${r.guestNum}", style = MaterialTheme.typography.bodyMedium)
                    r.tableNames?.let {
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Outlined.TableRestaurant, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (r.comment.isNotBlank()) {
                    Text(
                        r.comment,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusBadge(r.statusName, r.statusColor)
        }
    }
}
