package io.github.rsclub22.tireservations.data

import io.github.rsclub22.tireservations.platform.deviceName
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.time.LocalDate

/**
 * Single entry point for the UI. Builds an API client from the stored settings and caches
 * lookup data (locations, tables, statuses) which rarely change.
 */
class ReservationRepository(
    private val settingsStore: SettingsStore,
    private val httpClient: OkHttpClient,
) {
    private val mutex = Mutex()
    private var cachedApi: TastyIgniterApi? = null
    private var cachedKey: Pair<String, String?>? = null

    private var locations: List<Location>? = null
    private var tables: List<DiningTable>? = null
    private var statuses: List<ReservationStatus>? = null

    private suspend fun api(): TastyIgniterApi = mutex.withLock {
        val s = settingsStore.settings.first()
        val key = s.baseUrl to s.token
        if (cachedApi == null || cachedKey != key) {
            cachedApi = TastyIgniterApi(httpClient, s.baseUrl, s.token)
            cachedKey = key
            clearCache()
        }
        cachedApi!!
    }

    suspend fun login(serverUrl: String, email: String, password: String, isAdmin: Boolean) {
        val baseUrl = TastyIgniterApi.normalizeBaseUrl(serverUrl)
        val tokenName = deviceName.take(255)
        val token = TastyIgniterApi(httpClient, baseUrl, null)
            .createToken(email.trim(), password, isAdmin, tokenName)
        val userName = runCatching { TastyIgniterApi(httpClient, baseUrl, token).currentUserName() }.getOrNull()
        settingsStore.saveLogin(baseUrl, email.trim(), isAdmin, token, userName)
        clearCache()
    }

    suspend fun logout() {
        settingsStore.logout()
        clearCache()
    }

    fun clearCache() {
        locations = null
        tables = null
        statuses = null
    }

    suspend fun reservations(query: ReservationQuery) = api().reservations(query)
    suspend fun reservation(id: Long) = api().reservation(id)
    /**
     * Creates the reservation. The initial status is sent with the payload and then applied again
     * through the status endpoint, which also writes the status history. If that second step fails
     * the reservation already exists, so the error is returned as [CreateResult.warning] instead of
     * thrown (a retry would create a duplicate).
     */
    suspend fun create(draft: ReservationDraft): CreateResult {
        val id = api().createReservation(draft)
        val statusId = draft.statusId ?: return CreateResult(id)
        return try {
            api().updateStatus(id, statusId, null, false)
            CreateResult(id)
        } catch (e: ApiException) {
            CreateResult(id, "Reservierung angelegt, aber der Status konnte nicht gesetzt werden: ${e.message}")
        }
    }
    suspend fun update(id: Long, draft: ReservationDraft) = api().updateReservation(id, draft)
    suspend fun delete(id: Long) = api().deleteReservation(id)
    suspend fun updateStatus(id: Long, statusId: Long, comment: String?, notify: Boolean) =
        api().updateStatus(id, statusId, comment, notify)

    // --- Telefonannahme --------------------------------------------------------------
    // Eigene Endpunkte der Server-Erweiterung. Absichtlich ohne Zwischenspeicher: die
    // Belegung eines Tages aendert sich genau dann, wenn jemand anruft.

    suspend fun internTag(datum: LocalDate, gaeste: Int, raumId: Long? = null) =
        api().internTag(datum, gaeste, raumId)

    suspend fun internAnnehmen(entwurf: Annahmeentwurf) = api().internAnnehmen(entwurf)

    suspend fun internTagesblatt(von: LocalDate, bis: LocalDate = von, trennzeit: String? = null) =
        api().internTagesblatt(von, bis, trennzeit)

    suspend fun internMonat(jahr: Int, monat: Int) = api().internMonat(jahr, monat)

    suspend fun internOffen(seit: Long) = api().internOffen(seit)

    suspend fun internSperren(datum: LocalDate, grund: String = "") = api().internSperren(datum, grund)

    suspend fun internFreigeben(datum: LocalDate) = api().internFreigeben(datum)

    suspend fun locations(): List<Location> =
        locations ?: api().locations().also { locations = it }

    /** Tables are optional: a token without the `tables` ability simply gets an empty list. */
    suspend fun tables(): List<DiningTable> =
        tables ?: runCatching { api().tables() }
            .getOrElse { if (it is ApiException && it.isUnauthorized) throw it else emptyList() }
            .also { tables = it }

    /** Falls back to TastyIgniter's default statuses if `/status` is not accessible. */
    suspend fun statuses(): List<ReservationStatus> =
        statuses ?: runCatching { api().reservationStatuses() }
            .getOrElse { if (it is ApiException && it.isUnauthorized) throw it else emptyList() }
            .ifEmpty { ReservationStatus.DEFAULTS }
            .also { statuses = it }
}
