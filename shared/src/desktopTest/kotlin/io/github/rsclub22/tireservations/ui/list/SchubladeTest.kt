package io.github.rsclub22.tireservations.ui.list

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.launch
import org.junit.Test

/**
 * Zweimal aufs Hamburger-Menue getippt hat die App am Tresen mit weissem Fenster
 * stehengelassen. Diese Tests stellen die vier Tippmuster nach, die dafuer in
 * Frage kamen - und **keines** von ihnen bringt die Oberflaeche um. Die Ursache
 * des Weissschirms liegt also nicht in der Schublade selbst.
 *
 * Sie bleiben trotzdem stehen: sie halten fest, was geprueft und ausgeschlossen
 * wurde, damit niemand dieselben vier Vermutungen noch einmal durchgeht. Wer den
 * Weissschirm sucht, muss weiter - Navigation, ViewModel, echte Liste -, und
 * braucht dafuer den Stapel aus dem Log, nicht eine fuenfte Vermutung.
 */
@OptIn(ExperimentalTestApi::class)
class SchubladeTest {

    @Test
    fun `zweimal aufs Menue getippt bringt die Oberflaeche nicht um`() = runComposeUiTest {
        setContent {
            val schublade = rememberDrawerState(DrawerValue.Closed)
            val bereich = rememberCoroutineScope()
            ModalNavigationDrawer(
                drawerState = schublade,
                drawerContent = {
                    ModalDrawerSheet {
                        NavigationDrawerItem(
                            label = { Text("Tagesliste") },
                            selected = true,
                            onClick = { bereich.launch { schublade.close() } },
                        )
                    }
                },
            ) {
                IconButton(
                    onClick = { bereich.launch { schublade.open() } },
                    modifier = Modifier.testTag("menue"),
                ) { Icon(Icons.Filled.Menu, contentDescription = "Menü") }
            }
        }

        // Ohne waitForIdle dazwischen - das ist der schnelle Doppeltipp.
        onNodeWithTag("menue").performClick()
        onNodeWithTag("menue").performClick()
        waitForIdle()

        // Kommt die Oberflaeche noch zum Zeichnen, lebt sie.
        onNodeWithTag("menue").assertExists()
    }

    @Test
    fun `Menue auf und sofort daneben getippt`() = runComposeUiTest {
        setContent { Geruest() }

        // Erster Tipp oeffnet. Der zweite landet auf dem Schleier bzw. der
        // einfahrenden Schublade - auf dem Touchscreen trifft der Finger beim
        // zweiten Mal gar nicht mehr den Hamburger, weil die Schublade genau
        // dort hereinfaehrt.
        onNodeWithTag("menue").performClick()
        onNodeWithTag("wurzel").performTouchInput { click(Offset(400f, 300f)) }
        waitForIdle()

        onNodeWithTag("wurzel").assertExists()
    }

    @Test
    fun `Menue auf und sofort auf einen Eintrag getippt`() = runComposeUiTest {
        var navigiert = 0
        setContent { Geruest(onAnnahme = { navigiert++ }) }

        onNodeWithTag("menue").performClick()
        // Ohne waitForIdle: die Schublade faehrt noch, und es wird schon getippt.
        onNodeWithTag("annahme").performClick()
        waitForIdle()

        onNodeWithTag("wurzel").assertExists()
    }

    @Test
    fun `dreimal aufs Menue getippt`() = runComposeUiTest {
        setContent { Geruest() }

        repeat(3) { onNodeWithTag("menue").performClick() }
        waitForIdle()

        onNodeWithTag("wurzel").assertExists()
    }

    @Composable
    private fun Geruest(onAnnahme: () -> Unit = {}) {
        val schublade = rememberDrawerState(DrawerValue.Closed)
        val bereich = rememberCoroutineScope()
        ModalNavigationDrawer(
            drawerState = schublade,
            drawerContent = {
                ModalDrawerSheet {
                    NavigationDrawerItem(
                        label = { Text("Tagesliste") },
                        selected = true,
                        onClick = { bereich.launch { schublade.close() } },
                    )
                    NavigationDrawerItem(
                        label = { Text("Telefonannahme") },
                        selected = false,
                        onClick = {
                            bereich.launch { schublade.close() }
                            onAnnahme()
                        },
                        modifier = Modifier.testTag("annahme"),
                    )
                }
            },
            modifier = Modifier.testTag("wurzel"),
        ) {
            IconButton(
                onClick = { bereich.launch { schublade.open() } },
                modifier = Modifier.testTag("menue"),
            ) { Icon(Icons.Filled.Menu, contentDescription = "Men\u00fc") }
        }
    }

}
