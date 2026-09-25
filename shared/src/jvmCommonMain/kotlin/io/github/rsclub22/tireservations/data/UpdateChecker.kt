package io.github.rsclub22.tireservations.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** A newer release published on GitHub. */
data class AppUpdate(
    val version: String,
    val notes: String,
    /** Direct link to the release APK, or the release page if no APK is attached. */
    val downloadUrl: String,
)

/**
 * Looks for a newer signed release on GitHub (`/releases/latest`, which skips drafts and
 * pre-releases). Installs from the Play Store are updated by the store instead, see [isFromPlayStore].
 *
 * @param repo `owner/name` of the GitHub repository; blank disables the check.
 */
class UpdateChecker(
    private val client: OkHttpClient,
    private val repo: String,
    val currentVersion: String,
    // Neue Parameter bewusst hinter apiRoot: der Test uebergibt es positional als
    // viertes Argument, und davor eingeschoben wandert es stillschweigend in den
    // falschen Parameter.
    private val apiRoot: String = "https://api.github.com",
    /** Wird als User-Agent an GitHub geschickt; jede Plattform nennt sich selbst. */
    private val userAgent: String = "TIReservations",
    /**
     * Endung des Release-Anhangs, der zu dieser Plattform passt - `.apk` am Telefon,
     * `.deb` auf dem Desktop. Ohne Treffer verweist die Meldung auf die
     * Release-Seite, statt einem Desktop eine APK anzubieten.
     */
    private val assetSuffix: String = ".apk",
) {
    private val json = Json { ignoreUnknownKeys = true }

    val isEnabled: Boolean get() = repo.isNotBlank()

    /** Returns the newer release, or `null` if the app is up to date or nothing is published yet. */
    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext null
        val request = Request.Builder()
            .url("$apiRoot/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", userAgent)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                // 404: no release published yet.
                if (response.code == 404) return@withContext null
                if (!response.isSuccessful) {
                    throw ApiException(response.code, "Update-Prüfung fehlgeschlagen (HTTP ${response.code}).")
                }
                val release = json.parseToJsonElement(response.body.string()) as? JsonObject
                    ?: throw ApiException(0, "Unerwartete Antwort von GitHub.")
                val tag = release.string("tag_name") ?: return@withContext null
                if (!isNewer(tag, currentVersion)) return@withContext null
                val anhang = (release["assets"] as? JsonArray)
                    ?.mapNotNull { it as? JsonObject }
                    ?.firstOrNull { it.string("name")?.endsWith(assetSuffix, ignoreCase = true) == true }
                AppUpdate(
                    version = tag.removePrefix("v"),
                    notes = release.string("body").orEmpty().trim(),
                    downloadUrl = anhang?.string("browser_download_url") ?: release.string("html_url").orEmpty(),
                )
            }
        } catch (e: IOException) {
            throw ApiException(0, "Update-Prüfung nicht möglich: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    companion object {
        /** Compares versions like `v1.2.10` and `1.2.9-debug` numerically, ignoring prefixes and suffixes. */
        fun isNewer(latest: String, current: String): Boolean {
            val a = parts(latest)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        private fun parts(version: String): List<Int> =
            version.trim().removePrefix("v").removePrefix("V")
                .substringBefore('-').substringBefore('+')
                .split('.')
                .map { it.toIntOrNull() ?: 0 }

    }
}
