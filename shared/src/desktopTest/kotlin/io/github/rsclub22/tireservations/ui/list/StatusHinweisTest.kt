package io.github.rsclub22.tireservations.ui.list

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.rsclub22.tireservations.data.ReservationRepository
import io.github.rsclub22.tireservations.data.SettingsStore
import io.github.rsclub22.tireservations.ui.detail.DetailState
import io.github.rsclub22.tireservations.ui.detail.ReservationDetailViewModel
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Der Hinweis "Status konnte nicht geladen werden" muss vom Netz bis in den
 * [ListState] durchkommen - sonst sieht eine bestaetigte Buchung aus wie eine
 * ausstehende. Hier laeuft die echte Kette Viewmodel -> Repository -> API gegen
 * einen MockWebServer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatusHinweisTest {

    @get:Rule
    val ordner = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val bereich = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
        bereich.cancel()
        Dispatchers.resetMain()
    }

    private fun zustandNach(statusIstKaputt: Boolean): ListState {
        val vm = aufbauen(statusIstKaputt) { repo, einst -> ReservationListViewModel(repo, einst) }
        return runBlocking {
            withTimeout(15_000) { vm.state.first { !it.loading && it.reservations.isNotEmpty() } }
        }
    }

    private fun detailNach(statusIstKaputt: Boolean): DetailState {
        val vm = aufbauen(statusIstKaputt) { repo, einst -> ReservationDetailViewModel(repo, einst, 1) }
        return runBlocking {
            withTimeout(15_000) { vm.state.first { !it.loading && (it.reservation != null || it.error != null) } }
        }
    }

    private fun <T> aufbauen(statusIstKaputt: Boolean, bau: (ReservationRepository, SettingsStore) -> T): T {
        val heute = LocalDate.now()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                val leer = """{"data":[],"meta":{"pagination":{"current_page":1,"total_pages":1}}}"""
                return when (url.encodedPath) {
                    "/api/reservations", "/api/reservations/1" -> {
                        val mitStatus = "status" in url.queryParameter("include")!!.split(',')
                        if (statusIstKaputt && mitStatus) {
                            MockResponse().setResponseCode(500)
                        } else {
                            MockResponse().setBody(
                                """{"data":[{"type":"reservations","id":"1","attributes":{"reservation_id":1,"guest_num":2,""" +
                                    """"first_name":"A","last_name":"B","reserve_date":"$heute","reserve_time":"19:00:00"}}],""" +
                                    """"meta":{"pagination":{"current_page":1,"total_pages":1}}}""",
                            )
                        }
                    }
                    "/api/locations", "/api/status" -> MockResponse().setBody(leer)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val speicher = PreferenceDataStoreFactory.createWithPath(scope = bereich) {
            ordner.newFile("einstellungen.preferences_pb").also { it.delete() }.absolutePath.toPath()
        }
        val einstellungen = SettingsStore(speicher)
        runBlocking {
            einstellungen.saveLogin(server.url("/api").toString().trimEnd('/'), "a@b.de", true, "tok", null)
        }
        return bau(ReservationRepository(einstellungen, OkHttpClient()), einstellungen)
    }

    @Test
    fun `flag is set in the state after a fallback`() {
        val zustand = zustandNach(statusIstKaputt = true)

        assertEquals(1, zustand.reservations.size)
        assertTrue(zustand.statusMissing)
        assertEquals(null, zustand.error)
    }

    @Test
    fun `flag stays clear when the status loaded`() {
        val zustand = zustandNach(statusIstKaputt = false)

        assertEquals(1, zustand.reservations.size)
        assertFalse(zustand.statusMissing)
    }

    @Test
    fun `detail flag is set after a fallback`() {
        val zustand = detailNach(statusIstKaputt = true)

        assertEquals(null, zustand.error)
        assertEquals(1L, zustand.reservation?.id)
        assertTrue(zustand.statusMissing)
    }

    @Test
    fun `detail flag stays clear when the status loaded`() {
        val zustand = detailNach(statusIstKaputt = false)

        assertEquals(1L, zustand.reservation?.id)
        assertFalse(zustand.statusMissing)
    }
}
