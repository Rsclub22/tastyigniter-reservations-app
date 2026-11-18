package io.github.rsclub22.tireservations.data

import java.time.LocalDate

/**
 * Wohin die App ihre Reservierungen schreibt: an TastyIgniter ([TastyIgniterApi])
 * oder in eine Datei auf dem Geraet ([LokalBackend]).
 *
 * Die Methoden sind genau die, die [ReservationRepository] vorher am API-Client
 * aufgerufen hat. Die Oberflaeche merkt so nicht, welche Seite antwortet - und
 * eine Funktion, die der lokale Betrieb (noch) nicht kann, meldet sich mit einer
 * [ApiException] wie ein Server, der sie nicht anbietet.
 */
interface Backend {
    suspend fun reservations(query: ReservationQuery): ReservationList
    suspend fun reservation(id: Long): LoadedReservation
    suspend fun createReservation(draft: ReservationDraft): Long
    suspend fun updateReservation(id: Long, draft: ReservationDraft)
    suspend fun deleteReservation(id: Long)
    suspend fun updateStatus(id: Long, statusId: Long, comment: String?, notify: Boolean)

    suspend fun locations(): List<Location>
    suspend fun tables(): List<DiningTable>
    suspend fun reservationStatuses(): List<ReservationStatus>

    suspend fun internTag(datum: LocalDate, gaeste: Int, raumId: Long? = null): Tagesdaten
    suspend fun internAnnehmen(entwurf: Annahmeentwurf): InternReservierung
    suspend fun internTagesblatt(von: LocalDate, bis: LocalDate = von, trennzeit: String? = null): Tagesblatt
    suspend fun internMonat(jahr: Int, monat: Int): Monatsuebersicht
    suspend fun internOffen(seit: Long): OffeneReservierungen
    suspend fun internSperren(datum: LocalDate, grund: String = ""): Map<String, String>
    suspend fun internFreigeben(datum: LocalDate): Map<String, String>
}
