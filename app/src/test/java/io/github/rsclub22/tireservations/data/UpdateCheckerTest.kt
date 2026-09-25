package io.github.rsclub22.tireservations.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateCheckerTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun checker(current: String, repo: String = "owner/app") =
        UpdateChecker(OkHttpClient(), repo, current, server.url("/").toString().trimEnd('/'))

    @Test
    fun `compares versions numerically`() {
        assertTrue(UpdateChecker.isNewer("v1.2.10", "1.2.9"))
        assertTrue(UpdateChecker.isNewer("v2.0", "1.9.9"))
        assertFalse(UpdateChecker.isNewer("v1.0.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("v1.0.0", "1.0.0-debug"))
        assertFalse(UpdateChecker.isNewer("v1.0.0", "1.1.0"))
    }

    @Test
    fun `offers newer release with apk download`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"tag_name":"v1.3.0","body":"Neu: Ende wählen","html_url":"https://github.com/owner/app/releases/tag/v1.3.0",
                "assets":[{"name":"ti-reservierungen-1.3.0.aab","browser_download_url":"https://x/app.aab"},
                          {"name":"ti-reservierungen-1.3.0.apk","browser_download_url":"https://x/app.apk"}]}""",
            ),
        )

        val update = checker("1.2.0").check()!!

        assertEquals("1.3.0", update.version)
        assertEquals("https://x/app.apk", update.downloadUrl)
        assertEquals("Neu: Ende wählen", update.notes)
        assertEquals("/repos/owner/app/releases/latest", server.takeRequest().path)
    }

    @Test
    fun `no update when current or nothing published`() = runTest {
        server.enqueue(MockResponse().setBody("""{"tag_name":"v1.2.0","assets":[]}"""))
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))

        assertNull(checker("1.2.0").check())
        assertNull(checker("1.2.0").check())
    }

    @Test
    fun `disabled without repository`() = runTest {
        val disabled = checker("1.0.0", repo = "")
        assertFalse(disabled.isEnabled)
        assertNull(disabled.check())
        assertEquals(0, server.requestCount)
    }
}
