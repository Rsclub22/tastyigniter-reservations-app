package io.github.rsclub22.tireservations.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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

    private fun reservationJson(id: Int, date: String, time: String = "19:00:00") =
        """{"type":"reservations","id":"$id","attributes":{"reservation_id":$id,"guest_num":2,""" +
            """"first_name":"G$id","last_name":"X","reserve_date":"$date","reserve_time":"$time"}}"""

    private fun page(current: Int, total: Int, vararg items: String) =
        """{"data":[${items.joinToString(",")}],"meta":{"pagination":{"current_page":$current,"total_pages":$total}}}"""

    @Test
    fun `day view pages newest first and filters on the client`() = runTest {
        val pages = mapOf(
            1 to page(1, 4, reservationJson(1, "2026-09-27"), reservationJson(2, "2026-09-25", "20:00:00"), reservationJson(3, "2026-09-25", "18:00:00")),
            2 to page(2, 4, reservationJson(4, "2026-09-25", "12:00:00"), reservationJson(5, "2026-09-24")),
            3 to page(3, 4, reservationJson(6, "2026-09-23")),
            4 to page(4, 4, reservationJson(7, "2026-09-20")),
        )
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                MockResponse().setBody(pages.getValue(request.requestUrl!!.queryParameter("page")!!.toInt()))
        }

        val list = api().reservations(ReservationQuery(date = LocalDate.of(2026, 9, 25)))

        assertEquals(listOf(4L, 3L, 2L), list.map { it.id })
        val first = server.takeRequest().requestUrl!!
        assertEquals("reserve_date desc", first.queryParameter("sort"))
        // The server-side dateTimeFilter is broken in TastyIgniter and must not be sent.
        assertEquals(null, first.queryParameter("dateTimeFilter[startAt]"))
        // Pages 3 and 4 lie entirely before the requested day and are never loaded.
        val requested = (2..server.requestCount).map { server.takeRequest().requestUrl!!.queryParameter("page") }
        assertEquals(listOf("2"), requested)
    }

    @Test
    fun `finds an old day on a busy installation with few requests`() = runTest {
        val newest = LocalDate.of(2026, 12, 31)
        // 400 pages, one day per page, newest first: far beyond any fixed page cap.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.requestUrl!!.queryParameter("page")!!.toInt()
                val day = newest.minusDays((p - 1).toLong()).toString()
                return MockResponse().setBody(page(p, 400, reservationJson(p * 10, day), reservationJson(p * 10 + 1, day, "12:00:00")))
            }
        }

        val target = newest.minusDays(300)
        val list = api().reservations(ReservationQuery(date = target))

        assertEquals(listOf(3011L, 3010L), list.map { it.id })
        assertTrue("requests: ${server.requestCount}", server.requestCount <= 12)
    }

    @Test
    fun `posts new reservation in api format`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(MappersTest.SAMPLE_LIST))

        val id = api().createReservation(
            ReservationDraft(
                locationId = 1, date = LocalDate.of(2026, 9, 25), time = LocalTime.of(19, 30), guestNum = 4,
                firstName = " Erika ", lastName = "Mustermann", email = "erika@example.com",
                telephone = "0301234", comment = "", tableIds = listOf(3, 4), statusId = 6,
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
        assertEquals("6", body["status_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `omits blank email on create but sends it on update`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(MappersTest.SAMPLE_LIST))
        server.enqueue(MockResponse().setBody(MappersTest.SAMPLE_LIST))
        val draft = ReservationDraft(locationId = 1, firstName = "Max", lastName = "Muster", telephone = "0151", email = " ", statusId = 6)

        api().createReservation(draft)
        api().updateReservation(12, draft)

        val created = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertTrue("email" !in created)
        val updated = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertEquals("", updated["email"]!!.jsonPrimitive.content)
        assertTrue("status_id" !in updated)
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
