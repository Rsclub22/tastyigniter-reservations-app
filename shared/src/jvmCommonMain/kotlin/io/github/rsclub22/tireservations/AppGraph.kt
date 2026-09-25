package io.github.rsclub22.tireservations

import io.github.rsclub22.tireservations.data.ReservationRepository
import io.github.rsclub22.tireservations.data.SettingsStore
import io.github.rsclub22.tireservations.data.UpdateChecker
import io.github.rsclub22.tireservations.data.Wachdienst
import io.github.rsclub22.tireservations.data.createSettingsDataStore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Die gemeinsam genutzten Abhängigkeiten der App.
 *
 * Vorher lagen sie in `ReservationsApp : Application` und waren damit an Android
 * gebunden. Kein DI-Framework: bei drei Objekten ist ein Halter mit [init] weniger
 * Aufwand als jede Bibliothek, und der Aufrufer bleibt sichtbar.
 *
 * Die Werte, die Android aus `BuildConfig` bezieht, kommen als Parameter herein -
 * `BuildConfig` gibt es nur im Android-Build, und drei `expect`-Deklarationen dafür
 * wären mehr Mechanik als Nutzen.
 */
object AppGraph {

    lateinit var settingsStore: SettingsStore
        private set

    lateinit var repository: ReservationRepository
        private set

    lateinit var updateChecker: UpdateChecker
        private set

    /** Sieht nach, ob ueber das oeffentliche Formular etwas hereingekommen ist. */
    lateinit var wachdienst: Wachdienst
        private set

    /** Die automatische Update-Prüfung läuft einmal je App-Start. */
    var updateCheckedThisSession = false

    private var initialised = false

    /** Stehen die Abhaengigkeiten schon? Fuer Aufrufer ausserhalb der Oberflaeche. */
    val bereit: Boolean get() = initialised

    /**
     * @param updateRepo `owner/name` des GitHub-Repositories; leer schaltet die
     *   Update-Prüfung ab (Play-Store-Installationen, s. `isFromPlayStore`).
     */
    fun init(
        debug: Boolean,
        updateRepo: String,
        versionName: String,
        userAgent: String,
        /** Endung des Release-Anhangs dieser Plattform: `.apk` bzw. `.deb`. */
        assetSuffix: String,
    ) {
        // Android ruft das aus Application.onCreate, der Desktop aus main(). Ein
        // zweiter Aufruf wuerde neue Clients erzeugen, an denen niemand haengt.
        if (initialised) return
        initialised = true

        val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .apply {
                if (debug) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    )
                }
            }
            .build()

        settingsStore = SettingsStore(createSettingsDataStore())
        repository = ReservationRepository(settingsStore, httpClient)
        wachdienst = Wachdienst(repository, settingsStore)
        updateChecker = UpdateChecker(
            httpClient,
            updateRepo,
            versionName,
            userAgent = userAgent,
            assetSuffix = assetSuffix,
        )
    }
}
