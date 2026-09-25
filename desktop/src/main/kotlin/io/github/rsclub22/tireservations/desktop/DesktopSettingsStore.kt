package io.github.rsclub22.tireservations.desktop

import io.github.rsclub22.tireservations.data.AppSettings
import io.github.rsclub22.tireservations.data.SettingsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

/**
 * Settings in `$XDG_CONFIG_HOME/ti-reservierungen/settings.json` (default `~/.config/…`).
 * The file holds the API token, so it is only readable by the current user.
 */
class DesktopSettingsStore(private val file: Path = defaultFile()) : SettingsStorage {

    private val json = Json { prettyPrint = true }
    private val mutex = Mutex()
    private val state = MutableStateFlow(read())

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override suspend fun saveLogin(baseUrl: String, email: String, isAdmin: Boolean, token: String, userName: String?) =
        update { it.copy(baseUrl = baseUrl, email = email, isAdmin = isAdmin, token = token, userName = userName) }

    override suspend fun setDefaultLocation(id: Long?) = update { it.copy(defaultLocationId = id) }

    override suspend fun skipUpdate(version: String) = update { it.copy(skippedUpdateVersion = version) }

    override suspend fun logout() = update { it.copy(token = null, userName = null) }

    private suspend fun update(transform: (AppSettings) -> AppSettings) = mutex.withLock {
        val next = transform(state.value)
        withContext(Dispatchers.IO) { write(next) }
        state.value = next
    }

    private fun read(): AppSettings {
        val obj = runCatching { json.parseToJsonElement(Files.readString(file)) as? JsonObject }.getOrNull()
            ?: return AppSettings()
        fun str(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull
        return AppSettings(
            baseUrl = str("baseUrl").orEmpty(),
            email = str("email").orEmpty(),
            isAdmin = (obj["isAdmin"] as? JsonPrimitive)?.booleanOrNull ?: true,
            token = str("token"),
            userName = str("userName"),
            defaultLocationId = (obj["defaultLocationId"] as? JsonPrimitive)?.longOrNull,
            skippedUpdateVersion = str("skippedUpdateVersion"),
        )
    }

    private fun write(s: AppSettings) {
        val content = buildJsonObject {
            put("baseUrl", s.baseUrl)
            put("email", s.email)
            put("isAdmin", s.isAdmin)
            s.token?.let { put("token", it) }
            s.userName?.let { put("userName", it) }
            s.defaultLocationId?.let { put("defaultLocationId", it) }
            s.skippedUpdateVersion?.let { put("skippedUpdateVersion", it) }
        }
        Files.createDirectories(file.parent)
        val tmp = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(tmp, json.encodeToString(JsonObject.serializer(), content))
        runCatching { Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------")) }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        fun defaultFile(): Path {
            val base = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
                ?.let { Path.of(it) }
                ?: Path.of(System.getProperty("user.home"), ".config")
            return base.resolve("ti-reservierungen").resolve("settings.json")
        }
    }
}
