package io.github.rsclub22.tireservations.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Tresenrechner hat einen Touchscreen, und AWT kennt keine Beruehrungen: X11
 * liefert sie als Mausereignisse. Compose-Foundation laesst aber gerade eine Maus
 * den Inhalt nicht ziehen - Tippen ging also, Wischen tat gar nichts.
 *
 * Gewischt wird hier mit `performMouseInput`, weil genau das der Zeigertyp ist,
 * der vom Touchscreen herein kommt.
 *
 * Der erste Test haelt den Mangel der Bibliothek fest und ist damit die
 * Begruendung fuer [mitFingerSchiebbar]. Faellt er eines Tages um, weil Compose
 * die Maus ziehen laesst, kann der Modifier weg.
 */
@OptIn(ExperimentalTestApi::class)
class FingerschubTest {

    /** Wischhoehe klar ueber der Losbrechschwelle (touch slop), sonst gilt es als Tippen. */
    private val wischweg = 120f

    @Test
    fun `ohne Fingerschub laesst sich mit der Maus nichts ziehen`() = runComposeUiTest {
        val zustand = ScrollState(0)
        setContent { Liste(zustand) { Modifier } }

        wischeNachOben()

        assertEquals(
            "Compose zieht bei PointerType.Mouse nicht - dieser Test ist die Begruendung fuer mitFingerSchiebbar()",
            0,
            zustand.value,
        )
    }

    @Test
    fun `mit Fingerschub schiebt ein Mauszug den Inhalt`() = runComposeUiTest {
        val zustand = ScrollState(0)
        setContent { Liste(zustand) { Modifier.mitFingerSchiebbar(zustand) } }

        wischeNachOben()

        assertTrue(
            "Nach oben gewischt soll der Inhalt weiterlaufen, war aber bei ${zustand.value}",
            zustand.value > 0,
        )
    }

    @Test
    fun `nach oben gewischt laeuft der Inhalt nicht ueber den Anfang hinaus zurueck`() = runComposeUiTest {
        val zustand = ScrollState(0)
        setContent { Liste(zustand) { Modifier.mitFingerSchiebbar(zustand) } }

        // Am Anfang nach unten: es gibt nichts davor, der Zustand muss bei 0 bleiben.
        onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(60f, 60f))
            press()
            moveTo(Offset(60f, 60f + wischweg))
            release()
        }
        waitForIdle()

        assertEquals(0, zustand.value)
    }

    @Test
    fun `wischen loest kein Tippen auf der Zeile aus`() = runComposeUiTest {
        val zustand = ScrollState(0)
        var getippt = 0
        setContent { Liste(zustand, aufZeileGetippt = { getippt++ }) { Modifier.mitFingerSchiebbar(zustand) } }

        wischeNachOben()

        // Der Finger landet beim Loslassen auf einer Zeile. Zaehlte das als
        // Tippen, wuerde jedes Wischen in der Tagesliste eine Reservierung
        // aufmachen - genau das darf nicht passieren.
        assertEquals("Wischen darf nicht als Tippen zaehlen", 0, getippt)
        assertTrue("und geschoben werden soll es trotzdem", zustand.value > 0)
    }

    @Test
    fun `tippen auf die Zeile geht weiterhin`() = runComposeUiTest {
        val zustand = ScrollState(0)
        var getippt = 0
        setContent { Liste(zustand, aufZeileGetippt = { getippt++ }) { Modifier.mitFingerSchiebbar(zustand) } }

        onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(60f, 60f))
            press()
            release()
        }
        waitForIdle()

        assertEquals(1, getippt)
        assertEquals(0, zustand.value)
    }

    private fun ComposeUiTest.wischeNachOben() {
        onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(60f, 60f + wischweg))
            press()
            // In Schritten, damit die Losbrechschwelle sauber ueberschritten wird
            // und eine Geschwindigkeit fuer den Nachlauf zusammenkommt.
            moveTo(Offset(60f, 60f + wischweg * 2 / 3))
            moveTo(Offset(60f, 60f + wischweg / 3))
            moveTo(Offset(60f, 60f))
            release()
        }
        waitForIdle()
    }

    @Composable
    private fun Liste(
        zustand: ScrollState,
        aufZeileGetippt: () -> Unit = {},
        schub: @Composable () -> Modifier,
    ) {
        Column(
            Modifier
                .size(200.dp)
                .testTag(TAG)
                .verticalScroll(zustand)
                .then(schub()),
        ) {
            // Deutlich hoeher als der Bereich, es gibt also etwas zu schieben.
            repeat(40) {
                Box(Modifier.fillMaxWidth().height(40.dp).clickable { aufZeileGetippt() })
            }
        }
    }

    private companion object {
        const val TAG = "liste"
    }
}
