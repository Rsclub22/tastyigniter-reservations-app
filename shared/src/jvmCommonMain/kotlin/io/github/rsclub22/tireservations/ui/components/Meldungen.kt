package io.github.rsclub22.tireservations.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Kurze Rückmeldungen, die kein Bildschirm besitzt.
 *
 * Auf Android waren das `Toast`-Aufrufe. Die gibt es auf dem Desktop nicht, und ein
 * `expect`/`actual` je Plattform wäre hier zu viel Aufwand für zu wenig: die drei
 * betroffenen Meldungen erscheinen, während der Bildschirm gerade verlassen wird
 * ("Reservierung gelöscht") oder wenn eine Aktion nicht möglich war ("keine
 * passende App"). Ein Kanal auf App-Ebene, den ein Host in der Wurzel beider
 * Einstiegspunkte anzeigt, löst das ohne Plattformcode - und die Meldung
 * übersteht dabei auch den Wechsel des Bildschirms.
 */
object Meldungen {

    private val kanal = MutableSharedFlow<String>(extraBufferCapacity = 4)

    val texte: SharedFlow<String> = kanal.asSharedFlow()

    /**
     * Meldung anzeigen. Verwirft stillschweigend, wenn der Puffer voll ist - vier
     * ungesehene Kurzmeldungen sind schon mehr, als jemand lesen würde.
     */
    fun zeige(text: String) {
        kanal.tryEmit(text)
    }
}

/**
 * Zeigt die Meldungen aus [Meldungen] an. Gehört in die Wurzel der App, über den
 * Inhalt gelegt. Fängt keine Klicks ab: die Box hat keinen Zeiger-Modifier, nur
 * der Snackbar am unteren Rand reagiert.
 */
@Composable
fun MeldungsHost(modifier: Modifier = Modifier) {
    val state = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        Meldungen.texte.collect { state.showSnackbar(it) }
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(state)
    }
}
