package io.github.rsclub22.tireservations.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class MappersTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    private fun doc(json: String) = JsonApiDocument(Json.parseToJsonElement(json) as JsonObject)

    @Test
    fun `parses reservation with included status and tables`() {
        val d = doc(SAMPLE_LIST)
        val r = Mappers.reservation(d, d.data.first(), berlin)

        assertEquals(12L, r.id)
        assertEquals(1L, r.locationId)
        assertEquals("Hauptstraße", r.locationName)
        assertEquals(4, r.guestNum)
        assertEquals("Erika Mustermann", r.customerName)
        assertEquals(LocalDate.of(2026, 9, 25), r.date)
        assertEquals(LocalTime.of(19, 30), r.time)
        assertEquals(90, r.duration)
        assertEquals(6L, r.statusId)
        assertEquals("Confirmed", r.statusName)
        assertEquals("#00a65a", r.statusColor)
        assertEquals(listOf(3L, 4L), r.tables.map { it.id })
        assertEquals("T3, T4", r.tableNames)
        assertEquals(2, d.currentPage)
        assertEquals(3, d.totalPages)
    }

    @Test
    fun `utc midnight date is shifted back to local day`() {
        // Laravel's date cast serializes local midnight as the previous day in UTC.
        assertEquals(LocalDate.of(2026, 9, 25), Mappers.parseDate("2026-09-24T22:00:00.000000Z", berlin))
        assertEquals(LocalDate.of(2026, 9, 25), Mappers.parseDate("2026-09-25", berlin))
        assertEquals(LocalDate.of(2026, 9, 25), Mappers.parseDate("2026-09-25 00:00:00", berlin))
        assertNull(Mappers.parseDate("", berlin))
    }

    @Test
    fun `parses time in several formats`() {
        assertEquals(LocalTime.of(19, 30), Mappers.parseTime("19:30"))
        assertEquals(LocalTime.of(19, 30), Mappers.parseTime("19:30:00"))
        assertNull(Mappers.parseTime("abc"))
    }

    @Test
    fun `extracts laravel validation errors`() {
        val body = Json.parseToJsonElement(
            """{"message":"invalid","errors":{"email":["E-Mail ungültig"],"guest_num":["zu klein","muss Zahl sein"]}}""",
        ) as JsonObject
        val errors = Mappers.errors(body)
        assertEquals(listOf("E-Mail ungültig"), errors["email"])
        assertEquals(2, errors["guest_num"]?.size)
    }

    companion object {
        val SAMPLE_LIST = """
        {
          "data": [{
            "type": "reservations",
            "id": "12",
            "attributes": {
              "reservation_id": 12, "location_id": 1, "table_id": 3, "guest_num": 4,
              "first_name": "Erika", "last_name": "Mustermann", "email": "erika@example.com",
              "telephone": "+49 30 123456", "comment": "Fensterplatz",
              "reserve_date": "2026-09-24T22:00:00.000000Z", "reserve_time": "19:30:00",
              "duration": 90, "status_id": 6, "table_name": null,
              "created_at": "2026-09-20T10:00:00.000000Z"
            },
            "relationships": {
              "status": {"data": {"type": "statuses", "id": "6"}},
              "tables": {"data": [{"type": "tables", "id": "3"}, {"type": "tables", "id": "4"}]},
              "location": {"data": {"type": "locations", "id": "1"}}
            }
          }],
          "included": [
            {"type": "statuses", "id": "6", "attributes": {"status_name": "Confirmed", "status_color": "#00a65a", "status_for": "reservation"}},
            {"type": "tables", "id": "3", "attributes": {"id": 3, "name": "T3", "min_capacity": 2, "max_capacity": 4}},
            {"type": "tables", "id": "4", "attributes": {"id": 4, "name": "T4", "min_capacity": 2, "max_capacity": 6}},
            {"type": "locations", "id": "1", "attributes": {"location_name": "Hauptstraße"}}
          ],
          "meta": {"pagination": {"total": 45, "count": 20, "per_page": 20, "current_page": 2, "total_pages": 3}}
        }
        """.trimIndent()
    }
}
