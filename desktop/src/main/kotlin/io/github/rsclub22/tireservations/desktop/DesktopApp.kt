package io.github.rsclub22.tireservations.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rsclub22.tireservations.data.ApiException
import io.github.rsclub22.tireservations.data.AppSettings
import io.github.rsclub22.tireservations.data.Reservation
import io.github.rsclub22.tireservations.data.ReservationQuery
import io.github.rsclub22.tireservations.data.ReservationRepository
import io.github.rsclub22.tireservations.format.LongDateFormat
import io.github.rsclub22.tireservations.format.display
import io.github.rsclub22.tireservations.format.parseHexArgb
import io.github.rsclub22.tireservations.format.relativeDayLabel
import io.github.rsclub22.tireservations.format.statusLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

private val Brand = Color(0xFFC8472B)

@Composable
fun DesktopApp(repository: ReservationRepository, settingsStore: DesktopSettingsStore) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFFFFB4A3), primaryContainer = Color(0xFF7F2A15))
    } else {
        lightColorScheme(primary = Brand, onPrimary = Color.White, primaryContainer = Color(0xFFFFDAD2))
    }
    val settings by settingsStore.settings.collectAsState()

    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (settings.isLoggedIn) {
                DayListScreen(repository, settings)
            } else {
                LoginScreen(repository, settings)
            }
        }
    }
}

@Composable
private fun LoginScreen(repository: ReservationRepository, settings: AppSettings) {
    var server by remember { mutableStateOf(settings.baseUrl.removeSuffix("/api")) }
    var email by remember { mutableStateOf(settings.email) }
    var password by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(settings.isAdmin) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun login() {
        if (server.isBlank() || email.isBlank() || password.isBlank()) {
            error = "Bitte Server-Adresse, E-Mail und Passwort ausfüllen."
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                // On success the settings flow switches to the reservation list.
                repository.login(server, email, password, isAdmin)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Anmeldung fehlgeschlagen."
            } finally {
                loading = false
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("TastyIgniter Reservierungen", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Mit dem Admin-Zugang der TastyIgniter-Installation anmelden.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = server, onValueChange = { server = it; error = null },
                label = { Text("Server-Adresse") }, placeholder = { Text("https://mein-restaurant.de") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = email, onValueChange = { email = it; error = null },
                label = { Text("E-Mail") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it; error = null },
                label = { Text("Passwort") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { login() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Als Mitarbeiter anmelden", modifier = Modifier.weight(1f))
                Switch(checked = isAdmin, onCheckedChange = { isAdmin = it })
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = ::login, enabled = !loading, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Anmelden")
            }
        }
    }
}

@Composable
private fun DayListScreen(repository: ReservationRepository, settings: AppSettings) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var reservations by remember { mutableStateOf<List<Reservation>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(date, reloadKey) {
        loading = true
        error = null
        try {
            reservations = repository.reservations(
                ReservationQuery(date = date, locationId = settings.defaultLocationId),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is ApiException && e.isUnauthorized) repository.logout()
            error = e.message ?: "Unbekannter Fehler"
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { date = date.minusDays(1) }) { Text("‹") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(relativeDayLabel(date), style = MaterialTheme.typography.titleLarge)
                Text(date.format(LongDateFormat), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { date = date.plusDays(1) }) { Text("›") }
            if (date != LocalDate.now()) TextButton(onClick = { date = LocalDate.now() }) { Text("Heute") }
            TextButton(onClick = { reloadKey++ }) { Text("Aktualisieren") }
            TextButton(onClick = { scope.launch { repository.logout() } }) { Text("Abmelden") }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    val guests = reservations.sumOf { it.guestNum }
                    Text(
                        "${reservations.size} Reservierungen · $guests Gäste",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(reservations, key = { it.id }) { ReservationRow(it) }
            }
        }
    }
}

@Composable
private fun ReservationRow(r: Reservation) {
    val statusColor = parseHexArgb(r.statusColor)?.let { Color(it) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(4.dp).height(44.dp)
                    .background(statusColor ?: MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Text(r.time.display(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(64.dp))
            Column(Modifier.weight(1f)) {
                Text(r.customerName.ifBlank { "Reservierung #${r.id}" }, style = MaterialTheme.typography.titleMedium)
                val details = listOfNotNull(
                    "${r.guestNum} Pers.",
                    r.tableNames,
                    r.telephone.takeIf { it.isNotBlank() },
                    r.comment.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                Text(details, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            val bg = statusColor ?: MaterialTheme.colorScheme.secondaryContainer
            Surface(color = bg, contentColor = if (bg.luminance() > 0.5f) Color.Black else Color.White, shape = RoundedCornerShape(50)) {
                Text(statusLabel(r.statusName), modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
            }
        }
    }
}
