package io.github.rsclub22.tireservations.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rsclub22.tireservations.data.ReservationStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val GERMAN = Locale.GERMANY
val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", GERMAN)
val LongDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", GERMAN)
val ShortDateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(GERMAN)

fun LocalTime?.display(): String = this?.format(TimeFormat) ?: "--:--"
fun LocalDate?.display(): String = this?.format(ShortDateFormat) ?: "–"

fun relativeDayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Heute"
    today.plusDays(1) -> "Morgen"
    today.minusDays(1) -> "Gestern"
    else -> date.format(DateTimeFormatter.ofPattern("EEE, d. MMM", GERMAN))
}

/** German labels for TastyIgniter's default status names; custom names are shown unchanged. */
fun statusLabel(name: String?): String = when (name?.trim()?.lowercase()) {
    null, "" -> "Ohne Status"
    "pending" -> "Ausstehend"
    "confirmed" -> "Bestätigt"
    "canceled", "cancelled" -> "Storniert"
    "completed" -> "Abgeschlossen"
    "no show", "no-show" -> "Nicht erschienen"
    else -> name
}

fun statusLabel(status: ReservationStatus): String = statusLabel(status.name)

fun parseHexColor(hex: String?): Color? {
    val h = hex?.trim()?.removePrefix("#") ?: return null
    val full = when (h.length) {
        3 -> h.map { "$it$it" }.joinToString("")
        6 -> h
        else -> return null
    }
    return full.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}

@Composable
fun StatusBadge(name: String?, color: String?, modifier: Modifier = Modifier) {
    val bg = parseHexColor(color) ?: MaterialTheme.colorScheme.secondaryContainer
    val fg = if (parseHexColor(color) == null) MaterialTheme.colorScheme.onSecondaryContainer
    else if (bg.luminance() > 0.5f) Color.Black else Color.White
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            statusLabel(name),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorCard(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (onRetry != null) {
                    TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
                        Text("Erneut versuchen")
                    }
                }
            }
        }
    }
}
