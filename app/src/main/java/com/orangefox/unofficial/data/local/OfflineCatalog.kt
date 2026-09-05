package com.orangefox.unofficial.data.local

import android.content.Context
import com.orangefox.unofficial.data.model.Device
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
internal data class SeedEntry(
    val codename: String,
    val name: String,
    val oem: String,
    val image: String? = null
)

/**
 * Bundled fallback catalog (assets/catalog_offline.json). It is seeded into
 * Room on first launch so the Devices/Downloads screens always have data,
 * even before the API bridge succeeds for the first time.
 */
object OfflineCatalog {

    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): List<Device> = runCatching {
        val text = context.assets.open("catalog_offline.json").bufferedReader().use { it.readText() }
        json.decodeFromString(ListSerializer(SeedEntry.serializer()), text).map { entry ->
            Device(
                codename = entry.codename,
                name = entry.name,
                oem = entry.oem,
                imageUrl = entry.image,
                fromOfflineSeed = true
            )
        }
    }.getOrDefault(emptyList())
}
