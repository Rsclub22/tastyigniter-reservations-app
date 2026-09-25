package io.github.rsclub22.tireservations.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Client for the TastyIgniter REST API (`igniter/api` extension).
 *
 * @param baseUrl API root, e.g. `https://restaurant.example/api`
 * @param token Sanctum token returned by `POST /token`
 */
class TastyIgniterApi(
    private val client: OkHttpClient,
    val baseUrl: String,
    private val token: String?,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val root: HttpUrl = baseUrl.toHttpUrlOrNull()
        ?: throw ApiException(0, "Ungültige Server-Adresse: $baseUrl")

    // --- Authentication -------------------------------------------------------------------

    suspend fun createToken(email: String, password: String, isAdmin: Boolean, deviceName: String): String {
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
            put("is_admin", isAdmin)
            put("device_name", deviceName)
        }
        val response = send("POST", url("token"), body)
        return (response as? JsonObject)?.string("token")
            ?: throw ApiException(0, "Der Server hat kein Token zurückgegeben.")
    }

    /** Returns the display name of the logged-in user. */
    suspend fun currentUserName(): String? {
        val response = send("GET", url("token/user")) as? JsonObject
        val user = response?.get("user") as? JsonObject ?: return null
        return user.string("name") ?: user.string("full_name")
            ?: listOfNotNull(user.string("first_name"), user.string("last_name")).joinToString(" ").ifBlank { null }
            ?: user.string("email")
    }

    // --- Reservations ---------------------------------------------------------------------

    /**
     * Loads reservations, newest date first, and filters by [ReservationQuery.date] on the client.
     *
     * The API's `dateTimeFilter` can't be used: TastyIgniter passes the filter through
     * Spatie's `FiltersScope`, which flattens `{startAt, endAt}` into positional arguments, so the
     * scope ends up filtering between "now" and "now" and returns nothing. Instead we page through
     * the list sorted by `reserve_date desc` and stop once a page lies entirely before the wanted day.
     */
    suspend fun reservations(query: ReservationQuery, pageLimit: Int = 100, maxPages: Int = 50): List<Reservation> {
        val result = mutableListOf<Reservation>()
        var page = 1
        while (page <= maxPages) {
            val url = url("reservations").newBuilder()
                .addQueryParameter("include", "status,tables,location")
                .addQueryParameter("pageLimit", pageLimit.toString())
                .addQueryParameter("page", page.toString())
                .addQueryParameter("sort", "reserve_date desc")
                .apply {
                    query.locationId?.let { addQueryParameter("location", it.toString()) }
                    query.statusId?.let { addQueryParameter("status", it.toString()) }
                    query.search?.takeIf { it.isNotBlank() }?.let { addQueryParameter("search", it.trim()) }
                }
                .build()
            val doc = document(send("GET", url))
            val items = doc.data.map { Mappers.reservation(doc, it) }
            result += items
            if (doc.currentPage >= doc.totalPages || items.isEmpty()) break
            val latestOnPage = items.mapNotNull { it.date }.maxOrNull()
            if (query.date != null && latestOnPage != null && latestOnPage < query.date) break
            page++
        }
        return result
            .filter { query.date == null || it.date == query.date }
            .sortedWith(compareBy(nullsLast()) { r: Reservation -> r.date }.thenBy(nullsLast()) { it.time })
    }

    suspend fun reservation(id: Long): Reservation {
        val url = url("reservations/$id").newBuilder()
            .addQueryParameter("include", "status,tables,location")
            .build()
        val doc = document(send("GET", url))
        val res = doc.data.firstOrNull() ?: throw ApiException(404, "Reservierung $id nicht gefunden.")
        return Mappers.reservation(doc, res)
    }

    suspend fun createReservation(draft: ReservationDraft): Long {
        val doc = document(send("POST", url("reservations"), draft.toJson(isUpdate = false)))
        val res = doc.data.firstOrNull() ?: throw ApiException(0, "Unerwartete Antwort vom Server.")
        return Mappers.reservation(doc, res).id
    }

    suspend fun updateReservation(id: Long, draft: ReservationDraft) {
        send("PATCH", url("reservations/$id"), draft.toJson(isUpdate = true))
    }

    suspend fun deleteReservation(id: Long) {
        send("DELETE", url("reservations/$id"))
    }

    suspend fun updateStatus(id: Long, statusId: Long, comment: String?, notify: Boolean) {
        val body = buildJsonObject {
            put("status_id", statusId)
            if (!comment.isNullOrBlank()) put("comment", comment)
            put("notify", notify)
        }
        send("PATCH", url("reservations/$id/status"), body)
    }

    // --- Lookups --------------------------------------------------------------------------

    suspend fun locations(): List<Location> =
        list("locations").map { Mappers.location(it) }

    suspend fun tables(): List<DiningTable> =
        list("tables").map { Mappers.table(it) }.filter { it.enabled }.sortedBy { it.name }

    suspend fun reservationStatuses(): List<ReservationStatus> =
        list("status").filter { Mappers.isReservationStatus(it) }.map { Mappers.status(it) }

    private suspend fun list(path: String): List<Resource> {
        val out = mutableListOf<Resource>()
        var page = 1
        while (page <= 20) {
            val url = url(path).newBuilder()
                .addQueryParameter("pageLimit", "100")
                .addQueryParameter("page", page.toString())
                .build()
            val doc = document(send("GET", url))
            out += doc.data
            if (doc.currentPage >= doc.totalPages) break
            page++
        }
        return out
    }

    // --- HTTP plumbing --------------------------------------------------------------------

    private fun url(path: String): HttpUrl = root.newBuilder().addPathSegments(path).build()

    private fun document(element: JsonElement?): JsonApiDocument =
        JsonApiDocument(element as? JsonObject ?: throw ApiException(0, "Unerwartete Antwort vom Server."))

    private suspend fun send(method: String, url: HttpUrl, body: JsonObject? = null): JsonElement? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .apply { token?.let { header("Authorization", "Bearer $it") } }
                .method(method, body?.toString()?.toRequestBody(jsonMedia))
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body.string()
                    val parsed = text.takeIf { it.isNotBlank() }
                        ?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
                        // The parser accepts bare literals, so an HTML page could slip through as a primitive.
                        ?.takeIf { it is JsonObject || it is JsonArray }
                    if (!response.isSuccessful) {
                        throw errorFor(response.code, parsed as? JsonObject, text)
                    }
                    if (parsed == null && text.isNotBlank()) {
                        throw ApiException(
                            response.code,
                            "Der Server hat kein JSON geliefert. Ist die API-Erweiterung installiert und die Adresse korrekt?",
                        )
                    }
                    parsed
                }
            } catch (e: IOException) {
                throw ApiException(0, "Keine Verbindung zum Server: ${e.message ?: e.javaClass.simpleName}")
            }
        }

    private fun errorFor(code: Int, body: JsonObject?, raw: String): ApiException {
        val fieldErrors = Mappers.errors(body)
        val serverMessage = body?.string("message")?.takeIf { it.isNotBlank() }
        val message = when {
            fieldErrors.isNotEmpty() -> fieldErrors.values.flatten().joinToString("\n")
            code == 401 -> "Anmeldung abgelaufen oder ungültig. Bitte erneut anmelden."
            code == 403 -> serverMessage ?: "Keine Berechtigung für diese Aktion."
            code == 404 -> serverMessage ?: "Nicht gefunden (404). Ist die API-Ressource in TastyIgniter aktiviert?"
            serverMessage != null -> serverMessage
            else -> "Serverfehler $code${raw.take(120).let { if (it.isBlank()) "" else ": $it" }}"
        }
        return ApiException(code, message, fieldErrors)
    }

    /**
     * A blank e-mail is left out when creating, so installations without the e-mail requirement
     * accept it. When updating it is sent empty, so a removed address is actually cleared.
     */
    private fun ReservationDraft.toJson(isUpdate: Boolean): JsonObject = buildJsonObject {
        put("location_id", locationId)
        put("reserve_date", date.format(ISO_DATE))
        put("reserve_time", time.format(TIME))
        put("guest_num", guestNum)
        put("first_name", firstName.trim())
        put("last_name", lastName.trim())
        if (email.isNotBlank() || isUpdate) put("email", email.trim())
        // Status changes of existing reservations go through the status endpoint only.
        if (!isUpdate) statusId?.let { put("status_id", it) }
        put("telephone", telephone.trim())
        put("comment", comment.trim())
        duration?.let { put("duration", it) }
        putJsonArray("tables") { tableIds.forEach { add(JsonPrimitive(it)) } }
        if (tableIds.isNotEmpty()) put("table_id", tableIds.first())
    }

    companion object {
        private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /**
         * Turns what the user typed into the API root:
         * `restaurant.de` → `https://restaurant.de/api`, `http://10.0.0.5/api/` → `http://10.0.0.5/api`.
         */
        fun normalizeBaseUrl(input: String): String {
            var url = input.trim().trimEnd('/')
            if (url.isEmpty()) return url
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                url = "https://$url"
            }
            if (!url.endsWith("/api", ignoreCase = true)) url += "/api"
            return url
        }
    }
}
