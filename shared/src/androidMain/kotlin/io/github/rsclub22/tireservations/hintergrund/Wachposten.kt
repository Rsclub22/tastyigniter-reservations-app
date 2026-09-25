package io.github.rsclub22.tireservations.hintergrund

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.Constraints
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.rsclub22.tireservations.AppGraph
import java.util.concurrent.TimeUnit

/**
 * Sieht auch dann nach neuen unbestaetigten Reservierungen, wenn die App zu ist.
 *
 * Android laesst periodische Arbeit nicht haeufiger als alle 15 Minuten zu und
 * verschiebt sie zusaetzlich nach Lage von Akku und Doze. Eine Meldung kommt also
 * nicht in dem Moment, in dem jemand bucht, sondern innerhalb der naechsten
 * Viertelstunde. Wirklich sofort ginge nur mit Firebase oder einem offenen
 * WebSocket zum Server - beides waere ein eigenes Vorhaben.
 */
class Wachposten(
    kontext: Context,
    parameter: WorkerParameters,
) : CoroutineWorker(kontext, parameter) {

    override suspend fun doWork(): Result {
        // Der Prozess kann zwischendurch beendet worden sein; dann steht AppGraph
        // noch nicht. Application.onCreate laeuft aber vor jedem Worker, also ist
        // das hier nur die Absicherung gegen eine Reihenfolge, auf die man sich
        // nicht verlassen sollte.
        if (!AppGraph.bereit) return Result.retry()

        // Fehler sind kein Grund zur Wiederholung: der naechste Durchlauf kommt
        // ohnehin, und ein Retry-Sturm bei ausgefallenem Server hilft niemandem.
        AppGraph.wachdienst.nachsehen()

        return Result.success()
    }

    companion object {
        private const val NAME = "wachposten-offene-reservierungen"

        fun einplanen(kontext: Context) {
            val auftrag = PeriodicWorkRequestBuilder<Wachposten>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()

            // KEEP: ein bereits laufender Zeitplan wird nicht bei jedem App-Start
            // zurueckgesetzt, sonst verschoebe sich der naechste Lauf immer weiter.
            WorkManager.getInstance(kontext).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                auftrag,
            )
        }
    }
}
