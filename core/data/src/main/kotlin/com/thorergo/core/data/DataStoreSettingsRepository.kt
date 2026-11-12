package com.thorergo.core.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.thorergo.core.model.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

internal class DataStoreSettingsRepository @Inject constructor(
    private val store: DataStore<Settings>,
) : SettingsRepository {
    override val settings: Flow<Settings> = store.data.map { it.normalized() }.distinctUntilChanged()

    override suspend fun update(transform: (Settings) -> Settings): Result<Settings> = try {
        Result.success(store.updateData { transform(it.normalized()).normalized() })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** JSON on disk; unknown keys are ignored so older or newer files still load. */
internal object SettingsSerializer : Serializer<Settings> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    override val defaultValue: Settings = Settings()

    /** Fields that must be real JSON booleans: a string such as "true" never enables a feature. */
    private val booleanKeys: Set<String> = json.encodeToJsonElement(Settings.serializer(), defaultValue).jsonObject
        .filterValues { it is JsonPrimitive && !it.isString && it.booleanOrNull != null }.keys

    override suspend fun readFrom(input: InputStream): Settings = try {
        val raw = json.parseToJsonElement(input.readBytes().decodeToString()).jsonObject
        val strict = JsonObject(raw.filter { (key, value) -> key !in booleanKeys || (value is JsonPrimitive && !value.isString && value.booleanOrNull != null) })
        json.decodeFromJsonElement(Settings.serializer(), strict).normalized()
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable settings", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Invalid settings", e)
    }

    override suspend fun writeTo(t: Settings, output: OutputStream) {
        output.write(json.encodeToString(Settings.serializer(), t).encodeToByteArray())
    }
}
