package io.github.rsclub22.tireservations.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rsclub22.tireservations.data.AppUpdate
import io.github.rsclub22.tireservations.data.SettingsStore
import io.github.rsclub22.tireservations.data.UpdateChecker
import io.github.rsclub22.tireservations.platform.openUrl
import io.github.rsclub22.tireservations.ui.components.Meldungen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Checks GitHub for a newer release once per app start and offers it in a dialog.
 *
 * Ob ueberhaupt geprueft wird, entscheidet die Verdrahtung im Einstiegspunkt: sie
 * uebergibt dem [UpdateChecker] ein leeres Repository, wenn nicht geprueft werden
 * soll - im Debug-Build und bei Installationen aus dem Play Store. Dann ist
 * [UpdateChecker.isEnabled] false und hier passiert nichts.
 */
@Composable
fun AutoUpdatePrompt(
    checker: UpdateChecker,
    settingsStore: SettingsStore,
    alreadyChecked: () -> Boolean,
    markChecked: () -> Unit,
) {
    var update by remember { mutableStateOf<AppUpdate?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!checker.isEnabled || alreadyChecked()) return@LaunchedEffect
        markChecked()
        val found = runCatching { checker.check() }.getOrNull() ?: return@LaunchedEffect
        if (settingsStore.settings.first().skippedUpdateVersion != found.version) update = found
    }

    update?.let { found ->
        UpdateDialog(
            update = found,
            installedVersion = checker.currentVersion,
            onDismiss = { update = null },
            onLater = {
                scope.launch { settingsStore.skipUpdate(found.version) }
                update = null
            },
        )
    }
}

@Composable
fun UpdateDialog(
    update: AppUpdate,
    installedVersion: String,
    onDismiss: () -> Unit,
    onLater: () -> Unit = onDismiss,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update verfügbar: ${update.version}") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text("Installiert ist $installedVersion. Die neue Version wird im Browser heruntergeladen; danach die APK öffnen und installieren.")
                if (update.notes.isNotBlank()) {
                    Text(
                        update.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!openUrl(update.downloadUrl)) {
                    Meldungen.zeige("Kein Browser gefunden")
                }
                onDismiss()
            }) { Text("Herunterladen") }
        },
        dismissButton = { TextButton(onClick = onLater) { Text("Später") } },
    )
}
