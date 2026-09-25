package io.github.rsclub22.tireservations.ui.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rsclub22.tireservations.data.ApiException
import io.github.rsclub22.tireservations.data.Reservation
import io.github.rsclub22.tireservations.data.ReservationRepository
import io.github.rsclub22.tireservations.data.ReservationStatus
import io.github.rsclub22.tireservations.ui.components.ErrorCard
import io.github.rsclub22.tireservations.ui.components.LoadingBox
import io.github.rsclub22.tireservations.ui.components.LongDateFormat
import io.github.rsclub22.tireservations.ui.components.StatusBadge
import io.github.rsclub22.tireservations.ui.components.display
import io.github.rsclub22.tireservations.ui.components.statusLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailState(
    val reservation: Reservation? = null,
    val statuses: List<ReservationStatus> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val deleted: Boolean = false,
    val unauthorized: Boolean = false,
)

class ReservationDetailViewModel(
    private val repository: ReservationRepository,
    private val id: Long,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailState())
    val state = _state.asStateFlow()

    private var shownBefore = false

    init { load() }

    /** Reloads when coming back from the edit screen. */
    fun onScreenShown() {
        if (shownBefore) load()
        shownBefore = true
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.reservation == null, error = null) }
            runCatching {
                val r = repository.reservation(id)
                val statuses = repository.statuses()
                _state.update { it.copy(reservation = r, statuses = statuses, loading = false) }
            }.onFailure(::fail)
        }
    }

    fun changeStatus(statusId: Long, comment: String?, notify: Boolean) = action("Status geändert") {
        repository.updateStatus(id, statusId, comment, notify)
        _state.update { it.copy(reservation = repository.reservation(id)) }
    }

    fun delete() = action(null) {
        repository.delete(id)
        _state.update { it.copy(deleted = true) }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun action(successMessage: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching { block() }
                .onSuccess { _state.update { it.copy(busy = false, message = successMessage) } }
                .onFailure { e -> _state.update { it.copy(busy = false) }; fail(e, asMessage = true) }
        }
    }

    private fun fail(e: Throwable, asMessage: Boolean = false) {
        val msg = e.message ?: "Unbekannter Fehler"
        _state.update {
            it.copy(
                loading = false,
                error = if (asMessage) it.error else msg,
                message = if (asMessage) msg else it.message,
                unauthorized = e is ApiException && e.isUnauthorized,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReservationDetailScreen(
    repository: ReservationRepository,
    reservationId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onUnauthorized: () -> Unit,
) {
    val vm: ReservationDetailViewModel = viewModel(key = "detail-$reservationId") {
        ReservationDetailViewModel(repository, reservationId)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var pendingStatus by remember { mutableStateOf<ReservationStatus?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.onScreenShown() }
    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            Toast.makeText(context, "Reservierung gelöscht", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }
    LaunchedEffect(state.unauthorized) { if (state.unauthorized) onUnauthorized() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.messageShown() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reservierung #$reservationId") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (state.reservation != null) {
                        IconButton(onClick = { onEdit(reservationId) }, enabled = !state.busy) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Bearbeiten")
                        }
                        IconButton(onClick = { confirmDelete = true }, enabled = !state.busy) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Löschen")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val r = state.reservation
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            r == null -> Column(Modifier.padding(padding).padding(16.dp)) {
                ErrorCard(state.error ?: "Nicht gefunden", onRetry = vm::load)
            }
            else -> Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.customerName.ifBlank { "Ohne Namen" },
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    StatusBadge(r.statusName, r.statusColor)
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoRow(Icons.Outlined.Event, r.date?.format(LongDateFormat) ?: "–")
                        InfoRow(
                            Icons.Outlined.Schedule,
                            r.time.display() + " Uhr" +
                                (r.duration?.let { d -> " · $d Min. (bis ${r.time?.plusMinutes(d.toLong()).display()})" } ?: ""),
                        )
                        InfoRow(Icons.Outlined.Group, if (r.guestNum == 1) "1 Gast" else "${r.guestNum} Gäste")
                        InfoRow(Icons.Outlined.TableRestaurant, r.tableNames ?: "Kein Tisch zugewiesen")
                        r.locationName?.let { InfoRow(Icons.Outlined.Place, it) }
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoRow(Icons.Outlined.Call, r.telephone.ifBlank { "–" })
                        InfoRow(Icons.Outlined.Email, r.email.ifBlank { "–" })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (r.telephone.isNotBlank()) {
                                FilledTonalButton(onClick = { context.dial(r.telephone) }) {
                                    Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Anrufen")
                                }
                            }
                            if (r.email.isNotBlank()) {
                                OutlinedButton(onClick = { context.email(r.email, r) }) {
                                    Icon(Icons.Outlined.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("E-Mail")
                                }
                            }
                        }
                    }
                }

                if (r.comment.isNotBlank()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            InfoRow(Icons.AutoMirrored.Outlined.Notes, r.comment)
                        }
                    }
                }

                HorizontalDivider()
                Text("Status ändern", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.statuses.forEach { status ->
                        val current = status.id == r.statusId
                        OutlinedButton(
                            onClick = { pendingStatus = status },
                            enabled = !current && !state.busy,
                        ) { Text(statusLabel(status)) }
                    }
                }
                r.createdAt?.let {
                    Text(
                        "Erstellt: ${it.take(16).replace('T', ' ')}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    pendingStatus?.let { status ->
        StatusDialog(
            status = status,
            onDismiss = { pendingStatus = null },
            onConfirm = { comment, notify ->
                vm.changeStatus(status.id, comment, notify)
                pendingStatus = null
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Reservierung löschen?") },
            text = { Text("Die Reservierung wird endgültig aus TastyIgniter entfernt. Zum Absagen besser den Status „Storniert“ setzen.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete() }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun StatusDialog(status: ReservationStatus, onDismiss: () -> Unit, onConfirm: (String?, Boolean) -> Unit) {
    var comment by remember { mutableStateOf("") }
    var notify by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Status: ${statusLabel(status)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(500) },
                    label = { Text("Kommentar (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = notify, onCheckedChange = { notify = it })
                    Text("Gast per E-Mail benachrichtigen")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(comment.ifBlank { null }, notify) }) { Text("Übernehmen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun Context.dial(phone: String) =
    launch(Intent(Intent.ACTION_DIAL, "tel:${Uri.encode(phone)}".toUri()))

private fun Context.email(address: String, r: Reservation) = launch(
    Intent(Intent.ACTION_SENDTO, "mailto:".toUri()).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
        putExtra(Intent.EXTRA_SUBJECT, "Ihre Reservierung am ${r.date.display()} um ${r.time.display()} Uhr")
    },
)

private fun Context.launch(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "Keine passende App gefunden", Toast.LENGTH_SHORT).show()
    }
}
