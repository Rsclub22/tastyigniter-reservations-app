package io.github.rsclub22.tireservations.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rsclub22.tireservations.data.ReservationRepository
import io.github.rsclub22.tireservations.data.SettingsStore
import io.github.rsclub22.tireservations.ui.components.ErrorCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginState(
    val serverUrl: String = "",
    val email: String = "",
    val password: String = "",
    val isAdmin: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(
    private val repository: ReservationRepository,
    settingsStore: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settingsStore.settings.first()
            _state.update {
                it.copy(
                    serverUrl = s.baseUrl.removeSuffix("/api"),
                    email = s.email,
                    isAdmin = s.isAdmin,
                )
            }
        }
    }

    fun onServerUrl(v: String) = _state.update { it.copy(serverUrl = v, error = null) }
    fun onEmail(v: String) = _state.update { it.copy(email = v, error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onAdmin(v: Boolean) = _state.update { it.copy(isAdmin = v) }

    fun login(onSuccess: () -> Unit) {
        val s = _state.value
        if (s.serverUrl.isBlank() || s.email.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = "Bitte Server-Adresse, E-Mail und Passwort ausfüllen.") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.login(s.serverUrl, s.email, s.password, s.isAdmin) }
                .onSuccess {
                    _state.update { it.copy(loading = false, password = "") }
                    onSuccess()
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Anmeldung fehlgeschlagen.") } }
        }
    }
}

@Composable
fun LoginScreen(
    repository: ReservationRepository,
    settingsStore: SettingsStore,
    onLoggedIn: () -> Unit,
) {
    val vm: LoginViewModel = viewModel { LoginViewModel(repository, settingsStore) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showPassword by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(
                Icons.Outlined.EventAvailable,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Text("TastyIgniter Reservierungen", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Mit dem Admin-Zugang deiner TastyIgniter-Installation anmelden. " +
                    "Die API-Erweiterung (igniter.api) muss installiert sein.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val fieldModifier = Modifier.fillMaxWidth().widthIn(max = 480.dp)
            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = vm::onServerUrl,
                label = { Text("Server-Adresse") },
                placeholder = { Text("https://mein-restaurant.de") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                supportingText = {
                    if (state.serverUrl.trim().startsWith("http://", ignoreCase = true)) {
                        Text("Achtung: unverschlüsselte Verbindung (http).", color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("„/api“ wird automatisch ergänzt.")
                    }
                },
                modifier = fieldModifier,
            )
            OutlinedTextField(
                value = state.email,
                onValueChange = vm::onEmail,
                label = { Text("E-Mail") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = fieldModifier,
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = vm::onPassword,
                label = { Text("Passwort") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (showPassword) "Passwort verbergen" else "Passwort anzeigen",
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.login(onLoggedIn) }),
                modifier = fieldModifier,
            )
            Row(fieldModifier, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Als Mitarbeiter anmelden", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Aus: Anmeldung als Kunde (nur eigene Reservierungen)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.isAdmin, onCheckedChange = vm::onAdmin)
            }

            state.error?.let { ErrorCard(it, modifier = fieldModifier) }

            Button(
                onClick = { vm.login(onLoggedIn) },
                enabled = !state.loading,
                modifier = fieldModifier.height(48.dp),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Anmelden")
                }
            }
        }
    }
}
