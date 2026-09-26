package io.github.rsclub22.tireservations.ui.monat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rsclub22.tireservations.data.Monatstag
import io.github.rsclub22.tireservations.data.Monatsuebersicht
import io.github.rsclub22.tireservations.data.ReservationRepository
import io.github.rsclub22.tireservations.ui.components.ErrorCard
import io.github.rsclub22.tireservations.ui.components.LoadingBox
import io.github.rsclub22.tireservations.ui.components.display
import io.github.rsclub22.tireservations.ui.components.senkrechtSchiebbar
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Der Monat als Kalender: je Tag, wie voll es ist.
 *
 * Eingefaerbt wird gegen den vollsten Tag des Monats, nicht gegen eine feste
 * Kapazitaet. Die Raeume fassen ein Vielfaches der Tische - eine feste Obergrenze
 * waere an den meisten Tagen irrefuehrend und an Weihnachten falsch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonatsansichtScreen(
    repository: ReservationRepository,
    onBack: () -> Unit,
    onTagOeffnen: (LocalDate) -> Unit,
    onUnauthorized: () -> Unit,
) {
    val vm: MonatsansichtViewModel = viewModel(key = "monat") { MonatsansichtViewModel(repository) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.unauthorized) { if (state.unauthorized) onUnauthorized() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.monat.month.getDisplayName(TextStyle.FULL, Locale.GERMAN) +
                            " " + state.monat.year,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.weiter(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Monat zurück")
                    }
                    IconButton(onClick = vm::heute) {
                        Icon(Icons.Outlined.Today, contentDescription = "Heute")
                    }
                    IconButton(onClick = { vm.weiter(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Monat vor")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).senkrechtSchiebbar().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.fehler?.let { ErrorCard(it, onRetry = vm::neuLaden) }

            val uebersicht = state.uebersicht
            when {
                state.laden && uebersicht == null -> LoadingBox()
                uebersicht != null -> {
                    Text(
                        "${uebersicht.reservierungenGesamt} Reservierungen · " +
                            "${uebersicht.gaesteGesamt} Gäste · vollster Tag ${uebersicht.hoechstwert}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Wochenkopf()
                    Kalenderraster(uebersicht, state.gewaehlt, vm::waehle)
                    Legende()

                    state.gewaehlterTag?.let { tag ->
                        Tagesdetail(tag, onOeffnen = { onTagOeffnen(tag.datum) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Wochenkopf() {
    Row(Modifier.fillMaxWidth()) {
        listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach { tag ->
            Text(
                tag,
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Kalenderraster(
    uebersicht: Monatsuebersicht,
    gewaehlt: LocalDate?,
    onWaehlen: (LocalDate) -> Unit,
) {
    // Der Erste faellt selten auf einen Montag; davor bleiben Zellen leer.
    val vorlauf = uebersicht.von.dayOfWeek.value - 1
    val zellen: List<Monatstag?> = List(vorlauf) { null } + uebersicht.tage

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        zellen.chunked(7).forEach { woche ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                woche.forEach { tag ->
                    if (tag == null) {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        Tageszelle(
                            tag = tag,
                            fuellung = uebersicht.fuellung(tag),
                            gewaehlt = tag.datum == gewaehlt,
                            heute = tag.datum == LocalDate.now(),
                            modifier = Modifier.weight(1f),
                            onClick = { onWaehlen(tag.datum) },
                        )
                    }
                }
                // Die letzte Woche fuellt selten sieben Spalten.
                repeat(7 - woche.size) { Box(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
    }
}

@Composable
private fun Tageszelle(
    tag: Monatstag,
    fuellung: Float,
    gewaehlt: Boolean,
    heute: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val farbschema = MaterialTheme.colorScheme

    // Gesperrte Tage sind kein "voll", sondern "zu" - andere Farbe, damit die
    // beiden Zustaende sich nicht verwechseln lassen.
    val hintergrund = when {
        tag.gesperrt -> farbschema.errorContainer
        tag.gaeste == 0 -> farbschema.surfaceVariant.copy(alpha = 0.4f)
        else -> farbschema.primary.copy(alpha = 0.12f + 0.55f * fuellung)
    }

    val rand = when {
        gewaehlt -> farbschema.primary
        heute -> farbschema.tertiary
        tag.vermerk -> farbschema.tertiary.copy(alpha = 0.7f)
        else -> Color.Transparent
    }

    Box(
        modifier
            .aspectRatio(1f)
            .background(hintergrund, RoundedCornerShape(8.dp))
            .border(if (gewaehlt) 2.dp else 1.dp, rand, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                tag.datum.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (heute) FontWeight.Bold else FontWeight.Normal,
            )
            if (tag.gaeste > 0) {
                Text(
                    "${tag.gaeste}",
                    style = MaterialTheme.typography.labelSmall,
                    color = farbschema.onSurfaceVariant,
                )
            }
        }

        // Der Punkt sitzt in der Ecke und nicht als dritte Zeile unter der Zahl:
        // die Zelle ist quadratisch, und als Zeile wurde er vom Rand abgeschnitten.
        if (tag.vermerk) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(6.dp)
                    .background(farbschema.tertiary, CircleShape),
            )
        }
    }
}

@Composable
private fun Legende() {
    Text(
        "Zahl unter dem Datum: Gäste. Je kräftiger, desto voller — gemessen am vollsten Tag " +
            "des Monats. Rot: gesperrt. ● : Sperrvermerk.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Tagesdetail(tag: Monatstag, onOeffnen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                tag.datum.display(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (tag.leer) {
                    "Keine Reservierungen."
                } else {
                    "${tag.reservierungen} Reservierungen · ${tag.gaeste} Gäste" +
                        (tag.maxPax?.let { " · Höchstzahl $it" } ?: "")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (tag.gesperrt) {
                Text(
                    "Gesperrt" + tag.grund.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (tag.vermerkText.isNotBlank()) {
                Text(tag.vermerkText, style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalButton(onClick = onOeffnen) { Text("Annahme für diesen Tag") }
        }
    }
}
