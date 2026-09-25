package io.github.rsclub22.tireservations.desktop

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class DesktopSettingsStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `persists login and logout across instances`() = runTest {
        val file = tmp.root.toPath().resolve("cfg/settings.json")
        DesktopSettingsStore(file).saveLogin("https://r.de/api", "chef@r.de", true, "1|abc", "Chef")

        val reloaded = DesktopSettingsStore(file)
        assertTrue(reloaded.settings.value.isLoggedIn)
        assertEquals("1|abc", reloaded.settings.value.token)
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(file))

        reloaded.logout()
        val afterLogout = DesktopSettingsStore(file).settings.value
        assertFalse(afterLogout.isLoggedIn)
        assertNull(afterLogout.token)
        assertEquals("chef@r.de", afterLogout.email)
    }

    @Test
    fun `missing or broken file gives defaults`() {
        val file = tmp.root.toPath().resolve("settings.json")
        assertFalse(DesktopSettingsStore(file).settings.value.isLoggedIn)
        Files.writeString(file, "{kaputt")
        assertFalse(DesktopSettingsStore(file).settings.value.isLoggedIn)
    }
}
