package io.github.rsclub22.tireservations.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rsclub22.tireservations.data.AppSettings
import io.github.rsclub22.tireservations.data.AppUpdate
import io.github.rsclub22.tireservations.data.Location
import io.github.rsclub22.tireservations.data.ReservationRepository
import io.github.rsclub22.tireservations.data.SettingsStore
import io.github.rsclub22.tireservations.data.UpdateChecker
import io.github.rsclub22.tireservations.ui.update.UpdateDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: ReservationRepository,
    settingsStore: SettingsStore,
    updateChecker: UpdateChecker,
    versionLabel: String,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    var locations by remember { mutableStateOf<List<Location>>(emptyList()) }
    var cacheCleared by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var foundUpdate by remember { mutableStateOf<AppUpdate?>(null) }

    LaunchedEffect(Unit) { locations = runCatching { repository.locations() }.getOrDefault(emptyList()) }

    foundUpdate?.let {
        UpdateDialog(
            update = it,
            installedVersion = updateChecker.currentVersion,
            onDismiss = { foundUpdate = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text(settings.userName ?: settings.email) },
                supportingContent = { Text(if (settings.isAdmin) "Mitarbeiter · ${settings.email}" else "Kunde · ${settings.email}") },
                leadingContent = { Icon(Icons.Outlined.Person, contentDescription = null) },
            )
            ListItem(
                headlineContent = { Text("Server") },
                supportingContent = { Text(settings.baseUrl) },
                leadingContent = { Icon(Icons.Outlined.Dns, contentDescription = null) },
            )
            HorizontalDivider()

            if (locations.size > 1) {
                Text(
                    "Standard-Standort",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
                val options = listOf<Location?>(null) + locations
                options.forEach { loc ->
                    val selected = settings.defaultLocationId == loc?.id
                    ListItem(
                        headlineContent = { Text(loc?.name ?: "Alle Standorte") },
                        leadingContent = { RadioButton(selected = selected, onClick = null) },
                        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) {
                            scope.launch { settingsStore.setDefaultLocation(loc?.id) }
                        },
                    )
                }
                HorizontalDivider()
            }

            ListItem(
                headlineContent = { Text("Stammdaten neu laden") },
                supportingContent = {
                    Text(if (cacheCleared) "Erledigt – Standorte, Tische und Status werden neu geladen." else "Standorte, Tische und Status vom Server abrufen")
                },
                leadingContent = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                modifier = Modifier.selectable(selected = false) {
                    repository.clearCache()
                    cacheCleared = true
                },
            )
            ListItem(
                headlineContent = { Text("Abmelden", color = MaterialTheme.colorScheme.error) },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier.selectable(selected = false) {
                    scope.launch {
                        repository.logout()
                        onLoggedOut()
                    }
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text(versionLabel) },
                leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
            )
            ListItem(
                headlineContent = { Text("Nach Updates suchen") },
                supportingContent = {
                    Text(
                        when {
                            !updateChecker.isEnabled -> "Updates kommen über Google Play."
                            checkingUpdate -> "Suche …"
                            else -> updateStatus ?: "Neue Versionen von GitHub prüfen"
                        },
                    )
                },
                leadingContent = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
                modifier = Modifier.selectable(selected = false, enabled = updateChecker.isEnabled && !checkingUpdate) {
                    checkingUpdate = true
                    scope.launch {
                        runCatching { updateChecker.check() }
                            .onSuccess { update ->
                                foundUpdate = update
                                updateStatus = if (update == null) "Die App ist aktuell." else "Version ${update.version} verfügbar."
                            }
                            .onFailure { updateStatus = it.message ?: "Update-Prüfung fehlgeschlagen." }
                        checkingUpdate = false
                    }
                },
            )
        }
    }
}
