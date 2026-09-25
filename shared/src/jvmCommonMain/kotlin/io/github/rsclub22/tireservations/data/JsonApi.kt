package io.github.rsclub22.tireservations.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** A single JSON:API resource object (`{type, id, attributes, relationships}`). */
data class Resource(
    val type: String,
    val id: String,
    val attributes: JsonObject,
    val relationships: JsonObject,
)

/**
 * A JSON:API document as produced by the TastyIgniter API extension
 * (Fractal `JsonApiSerializer`).
 */
class JsonApiDocument(root: JsonObject) {
    val data: List<Resource>
    private val included: Map<Pair<String, String>, Resource>
    val currentPage: Int
    val totalPages: Int

    init {
        data = when (val d = root["data"]) {
            is JsonArray -> d.mapNotNull { it.asResource() }
            is JsonObject -> listOfNotNull(d.asResource())
            else -> emptyList()
        }
        included = (root["included"] as? JsonArray)
            ?.mapNotNull { it.asResource() }
            ?.associateBy { it.type to it.id }
            .orEmpty()
        val pagination = (root["meta"] as? JsonObject)?.get("pagination") as? JsonObject
        currentPage = pagination?.int("current_page") ?: 1
        totalPages = pagination?.int("total_pages") ?: 1
    }

    /** Resolves the related resources of [resource] for relationship [name] from `included`. */
    fun related(resource: Resource, name: String): List<Resource> {
        val rel = (resource.relationships[name] as? JsonObject)?.get("data") ?: return emptyList()
        val refs = when (rel) {
            is JsonArray -> rel.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(rel)
            else -> emptyList()
        }
        return refs.mapNotNull { ref ->
            val type = ref.string("type") ?: return@mapNotNull null
            val id = ref.string("id") ?: return@mapNotNull null
            included[type to id]
        }
    }

    private fun JsonElement.asResource(): Resource? {
        val obj = this as? JsonObject ?: return null
        return Resource(
            type = obj.string("type").orEmpty(),
            id = obj.string("id").orEmpty(),
            attributes = obj["attributes"] as? JsonObject ?: JsonObject(emptyMap()),
            relationships = obj["relationships"] as? JsonObject ?: JsonObject(emptyMap()),
        )
    }
}

internal fun JsonObject.primitive(key: String): JsonPrimitive? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }

internal fun JsonObject.string(key: String): String? = primitive(key)?.contentOrNull
internal fun JsonObject.long(key: String): Long? = primitive(key)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
internal fun JsonObject.int(key: String): Int? = primitive(key)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
internal fun JsonObject.bool(key: String): Boolean? = primitive(key)?.let {
    it.booleanOrNull ?: it.intOrNull?.let { n -> n != 0 } ?: it.contentOrNull?.let { s -> s == "1" || s == "true" }
}

/** Converts API resources into app models. Lenient: TastyIgniter versions differ in how they serialize fields. */
object Mappers {

    fun reservation(doc: JsonApiDocument, res: Resource, zone: ZoneId = ZoneId.systemDefault()): Reservation {
        val a = res.attributes
        val status = doc.related(res, "status").firstOrNull()?.let { status(it) }
            ?: (a["status"] as? JsonObject)?.let { statusFromAttributes(it) }
        val location = doc.related(res, "location").firstOrNull()
        val tables = doc.related(res, "tables").map { table(it) }
        val dateTime = parseDateTime(a.string("reservation_datetime"), zone)
        return Reservation(
            id = a.long("reservation_id") ?: res.id.toLongOrNull() ?: a.long("id") ?: 0,
            locationId = a.long("location_id"),
            locationName = location?.attributes?.string("location_name"),
            guestNum = a.int("guest_num") ?: 0,
            firstName = a.string("first_name").orEmpty(),
            lastName = a.string("last_name").orEmpty(),
            email = a.string("email").orEmpty(),
            telephone = a.string("telephone").orEmpty(),
            comment = a.string("comment").orEmpty(),
            date = parseDate(a.string("reserve_date"), zone) ?: dateTime?.first,
            time = parseTime(a.string("reserve_time")) ?: dateTime?.second,
            duration = a.int("duration")?.takeIf { it > 0 },
            statusId = a.long("status_id")?.takeIf { it > 0 } ?: status?.id,
            statusName = status?.name ?: a.string("status_name"),
            statusColor = status?.color,
            tables = tables,
            tableNames = a.string("table_name")?.takeIf { it.isNotBlank() }
                ?: tables.takeIf { it.isNotEmpty() }?.joinToString { it.name },
            createdAt = a.string("created_at") ?: a.string("date_added"),
        )
    }

    fun location(res: Resource) = Location(
        id = res.attributes.long("location_id") ?: res.id.toLong(),
        name = res.attributes.string("location_name") ?: "Standort ${res.id}",
    )

    fun table(res: Resource) = DiningTable(
        id = res.attributes.long("id") ?: res.attributes.long("table_id") ?: res.id.toLong(),
        name = res.attributes.string("name") ?: res.attributes.string("table_name") ?: "Tisch ${res.id}",
        minCapacity = res.attributes.int("min_capacity"),
        maxCapacity = res.attributes.int("max_capacity"),
        enabled = res.attributes.bool("is_enabled") ?: res.attributes.bool("table_status") ?: true,
    )

    fun status(res: Resource) = statusFromAttributes(res.attributes, res.id.toLongOrNull())

    fun isReservationStatus(res: Resource) = res.attributes.string("status_for") == "reservation"

    private fun statusFromAttributes(a: JsonObject, fallbackId: Long? = null) = ReservationStatus(
        id = a.long("status_id") ?: a.long("id") ?: fallbackId ?: 0,
        name = a.string("status_name").orEmpty(),
        color = a.string("status_color")?.takeIf { it.isNotBlank() },
    )

    /**
     * `reserve_date` is either a plain `2026-09-25`, a local `2026-09-25 00:00:00`
     * or an ISO timestamp in UTC (Laravel date cast), which must be shifted back to local time.
     */
    fun parseDate(value: String?, zone: ZoneId = ZoneId.systemDefault()): LocalDate? {
        if (value.isNullOrBlank()) return null
        parseDateTime(value, zone)?.let { return it.first }
        return runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
    }

    fun parseTime(value: String?): LocalTime? {
        if (value.isNullOrBlank()) return null
        val v = value.trim()
        return try {
            LocalTime.parse(if (v.length == 5) v else v.take(8))
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun parseDateTime(value: String?, zone: ZoneId): Pair<LocalDate, LocalTime>? {
        if (value.isNullOrBlank() || !value.contains('T')) return null
        val instant = runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: return null
        val local = instant.atZone(zone).toLocalDateTime()
        return local.toLocalDate() to local.toLocalTime()
    }

    /** Extracts Laravel style validation errors: `{"message": "...", "errors": {"field": ["..."]}}`. */
    fun errors(root: JsonObject?): Map<String, List<String>> {
        val errors = root?.get("errors") as? JsonObject ?: return emptyMap()
        return errors.mapValues { (_, v) ->
            when (v) {
                is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                is JsonPrimitive -> listOfNotNull(v.contentOrNull)
                else -> emptyList()
            }
        }
    }
}
