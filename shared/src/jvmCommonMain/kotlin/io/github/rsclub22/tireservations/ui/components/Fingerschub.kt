package io.github.rsclub22.tireservations.ui.components

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rsclub22.tireservations.platform.beruehrungKommtAlsMaus

/**
 * Macht einen scrollbaren Bereich mit dem Finger schiebbar.
 *
 * Auf dem Tresenrechner ist das der Unterschied zwischen benutzbar und nicht:
 * dort kommen Beruehrungen als Mausereignisse herein (siehe
 * [beruehrungKommtAlsMaus]), und `verticalScroll` bzw. `LazyColumn` lassen sich
 * von einer Maus grundsaetzlich nicht am Inhalt ziehen - Compose-Foundation
 * fragt vor jedem Zug den Zeigertyp ab und laesst alles ausser der Maus durch.
 * Tippen ging deshalb, Wischen tat gar nichts.
 *
 * `Modifier.draggable` fragt den Typ nicht ab. Es wird hier also von Hand an
 * denselben Scroll-Zustand gehaengt, den der Bereich schon benutzt. Das ist
 * bewusst kein eigener Zieh-Erkenner: Wurferkennung, Ausrutschweg und das
 * Abbrechen eines Tippens, sobald der Finger wandert, kommen alle aus der
 * Bibliothek und verhalten sich damit wie am Telefon.
 *
 * Am Telefon ein No-Op - dort kuemmert sich Compose selbst um Beruehrungen, und
 * ein zweiter Zieh-Erkenner wuerde jede Wischbewegung doppelt zaehlen.
 *
 * Gehoert hinter `verticalScroll`/`horizontalScroll` in die Kette, damit beim
 * Mausrad weiterhin der Bereich selbst zum Zug kommt.
 *
 * @param zustand derselbe [ScrollableState], den der Bereich benutzt -
 *   `ScrollState` und `LazyListState` sind beides einer.
 * @param richtung die Achse des Bereichs; quer gewischt wird nichts verschoben.
 */
@Composable
fun Modifier.mitFingerSchiebbar(
    zustand: ScrollableState,
    richtung: Orientation = Orientation.Vertical,
): Modifier {
    if (!beruehrungKommtAlsMaus) return this

    val nachlauf = ScrollableDefaults.flingBehavior()
    val zieher = rememberDraggableState { weg ->
        // Der Finger zieht den Inhalt mit sich: nach oben gewischt heisst
        // weiter nach unten im Inhalt, daher das umgekehrte Vorzeichen.
        zustand.dispatchRawDelta(-weg)
    }

    return draggable(
        state = zieher,
        orientation = richtung,
        // Faengt der Finger waehrend eines Nachlaufs wieder an, muss der
        // Nachlauf weichen - sonst schieben beide gleichzeitig. Ein leerer
        // Zug mit Vorrang "Benutzereingabe" bricht ihn ab.
        onDragStarted = { zustand.scroll(MutatePriority.UserInput) {} },
        onDragStopped = { geschwindigkeit ->
            zustand.scroll { with(nachlauf) { performFling(-geschwindigkeit) } }
        },
    )
}

/**
 * `verticalScroll`, aber auch mit dem Finger schiebbar.
 *
 * Steht als eigene Hausnummer da, damit die Bildschirme nicht jeder fuer sich an
 * [mitFingerSchiebbar] denken muessen - vergessen faellt auf dem Entwicklungs-
 * rechner naemlich nicht auf, sondern erst am Tresen.
 */
@Composable
fun Modifier.senkrechtSchiebbar(zustand: ScrollState = rememberScrollState()): Modifier =
    verticalScroll(zustand).mitFingerSchiebbar(zustand)
