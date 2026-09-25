package io.github.rsclub22.tireservations.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TastyIgniterApiTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun api(token: String? = "secret") =
        TastyIgniterApi(client, server.url("/api").toString().trimEnd('/'), token)

    @Test
    fun `normalizes user entered server urls`() {
        assertEquals("https://restaurant.de/api", TastyIgniterApi.normalizeBaseUrl("restaurant.de"))
        assertEquals("https://restaurant.de/api", TastyIgniterApi.normalizeBaseUrl(" https://restaurant.de/ "))
        assertEquals("http://10.0.0.5/api", TastyIgniterApi.normalizeBaseUrl("http://10.0.0.5/api/"))
        assertEquals("https://x.de/shop/api", TastyIgniterApi.normalizeBaseUrl("https://x.de/shop"))
    }

    @Test
    fun `creates admin token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"status_code":201,"token":"1|abc"}"""))

        val token = api(null).createToken("chef@example.com", "pw", isAdmin = true, deviceName = "Pixel")

        assertEquals("1|abc", token)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/token", request.path)
        val body = Json.parseToJsonElement(request.body.readUtf8()) as JsonObject
        assertEquals("chef@example.com", body["email"]!!.jsonPrimitive.content)
        assertEquals("true", body["is_admin"]!!.jsonPrimitive.content)
        assertEquals("Pixel", body["device_name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `lists reservations of a day with filters and follows pagination`() = runTest {
        val page1 = MappersTest.SAMPLE_LIST.replace("\"current_page\": 2", "\"current_page\": 1").replace("\"total_pages\": 3", "\"total_pages\": 2")
        val page2 = """{"data":[],"meta":{"pagination":{"current_page":2,"total_pages":2}}}"""
        server.enqueue(MockResponse().setBody(page1))
        server.enqueue(MockResponse().setBody(page2))

        val list = api().reservations(
            ReservationQuery(date = null, locationId = 1, statusId = 6),
        )

        assertEquals(1, list.size)
        val first = server.takeRequest()
        assertEquals("Bearer secret", first.getHeader("Authorization"))
        val url = first.requestUrl!!
        assertEquals("/api/reservations", url.encodedPath)
        assertEquals("1", url.queryParameter("location"))
        assertEquals("6", url.queryParameter("status"))
        assertEquals("1", url.queryParameter("page"))
        assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("page"))
    }

    @Test
    fun `sends date range filter`() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))

        api().reservations(ReservationQuery(date = LocalDate.of(2026, 9, 25)))

        val url = server.takeRequest().requestUrl!!
        assertEquals("2026-09-25 00:00:00", url.queryParameter("dateTimeFilter[startAt]"))
        assertEquals("2026-09-25 23:59:59", url.queryParameter("dateTimeFilter[endAt]"))
    }

    @Test
    fun `posts new reservation in api format`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(MappersTest.SAMPLE_LIST))

        val id = api().createReservation(
            ReservationDraft(
                locationId = 1, date = LocalDate.of(2026, 9, 25), time = LocalTime.of(19, 30), guestNum = 4,
                firstName = " Erika ", lastName = "Mustermann", email = "erika@example.com",
                telephone = "0301234", comment = "", tableIds = listOf(3, 4),
            ),
        )

        assertEquals(12L, id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        val body = Json.parseToJsonElement(request.body.readUtf8()) as JsonObject
        assertEquals("2026-09-25", body["reserve_date"]!!.jsonPrimitive.content)
        assertEquals("19:30", body["reserve_time"]!!.jsonPrimitive.content)
        assertEquals("Erika", body["first_name"]!!.jsonPrimitive.content)
        assertEquals("4", body["guest_num"]!!.jsonPrimitive.content)
        assertEquals("[3,4]", body["tables"].toString())
    }

    @Test
    fun `updates status via dedicated endpoint`() = runTest {
        server.enqueue(MockResponse().setBody(MappersTest.SAMPLE_LIST))

        api().updateStatus(12, 7, "Gast hat abgesagt", notify = true)

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/reservations/12/status", request.path)
        val body = Json.parseToJsonElement(request.body.readUtf8()) as JsonObject
        assertEquals("7", body["status_id"]!!.jsonPrimitive.content)
        assertEquals("true", body["notify"]!!.jsonPrimitive.content)
    }

    @Test
    fun `maps validation and auth errors`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(422)
                .setBody("""{"message":"The given data was invalid.","errors":{"email":["Die E-Mail ist ungültig."]}}"""),
        )
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Unauthenticated."}"""))

        try {
            api().createReservation(ReservationDraft(locationId = 1))
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(422, e.statusCode)
            assertEquals(listOf("Die E-Mail ist ungültig."), e.fieldErrors["email"])
            assertTrue(e.message.contains("E-Mail"))
        }

        try {
            api().reservation(1)
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertTrue(e.isUnauthorized)
        }
    }

    @Test
    fun `reports non json responses clearly`() = runTest {
        server.enqueue(MockResponse().setBody("<html>Wartungsmodus</html>"))
        try {
            api().locations()
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertTrue(e.message, e.message.contains("kein JSON"))
        }
    }

    @Test
    fun `filters statuses to reservation statuses`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"data":[
                  {"type":"statuses","id":"1","attributes":{"status_name":"Received","status_for":"order"}},
                  {"type":"statuses","id":"8","attributes":{"status_name":"Pending","status_for":"reservation","status_color":"#f0ad4e"}}
                ]}""",
            ),
        )
        val statuses = api().reservationStatuses()
        assertEquals(listOf(8L), statuses.map { it.id })
    }
}
