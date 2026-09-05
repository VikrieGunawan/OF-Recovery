package com.orangefox.unofficial.data.api

import com.orangefox.unofficial.data.model.Device
import com.orangefox.unofficial.data.model.FoxBuild
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tolerant parser for the OrangeFox bridge.
 *
 * The public API is schema-flexible (fields have been renamed between server
 * versions), so instead of hard-coding one strict DTO we walk the JSON tree
 * and extract values from a list of candidate keys. Anything unrecognised is
 * ignored — worst case we return an empty list and the UI falls back to the
 * Room cache / offline catalog.
 */
object DeviceParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseDeviceList(raw: String): List<Device> = runCatching {
        val element = json.parseToJsonElement(raw)
        element.toArrayOrNull()
            ?.filterIsInstance<JsonObject>()
            ?.mapNotNull { it.toDeviceOrNull() }
            .orEmpty()
    }.getOrDefault(emptyList())

    fun parseBuilds(raw: String): List<FoxBuild> = runCatching {
        val element = json.parseToJsonElement(raw)
        element.collectArrays()
            .asSequence()
            .flatMap { it.filterIsInstance<JsonObject>() }
            .mapNotNull { it.toBuildOrNull() }
            .distinctBy { it.displayName to it.fileUrl }
            .toList()
    }.getOrDefault(emptyList())

    // ---- device extraction -------------------------------------------------

    private fun JsonObject.toDeviceOrNull(): Device? {
        val codename = stringOf("codename", "device", "slug", "id", "_id") ?: return null
        val name = stringOf("full_name", "name", "model", "device_name") ?: codename
        val oem = stringOf("oem", "brand", "manufacturer", "vendor") ?: "Unknown"
        var img = stringOf("img", "image", "photo", "preview", "image_url", "img_url")
        if (img != null && img.startsWith("/")) img = "https://dl.orangefox.download$img"
        return Device(codename = codename, name = name, oem = oem, imageUrl = img)
    }

    private fun JsonObject.toBuildOrNull(): FoxBuild? {
        var url = stringOf("url", "download", "file", "link", "download_url", "file_url", "path") ?: return null
        if (url.startsWith("/")) url = "https://dl.orangefox.download$url"
        if (!url.startsWith("http")) return null
        val name = stringOf("file_name", "filename", "name", "title", "version", "id") ?: url.substringAfterLast('/')
        val version = stringOf("version", "fox_version", "release")
        val size = longOf("size", "bytes", "file_size", "filesize")
        val date = dateLabel()
        val channel = stringOf("type", "channel", "stability", "branch")?.lowercase()
        return FoxBuild(
            displayName = name,
            fileUrl = url,
            version = version,
            sizeBytes = size,
            dateLabel = date,
            channel = channel
        )
    }

    private fun JsonObject.dateLabel(): String? {
        val ms = longOf("date", "datetime", "created_at", "time")
        if (ms != null && ms > 0) {
            val millis = if (ms > 1_000_000_000_000L) ms else ms * 1000
            return runCatching {
                SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(millis))
            }.getOrNull()
        }
        return stringOf("date", "datetime", "created_at")
    }

    // ---- generic tolerant helpers -------------------------------------------

    private fun JsonElement.toArrayOrNull(): JsonArray? = when (this) {
        is JsonArray -> this
        is JsonObject -> {
            val keyed = listOf("data", "devices", "items", "results")
                .firstOrNull { this[it] is JsonArray }
                ?.let { this[it] } as? JsonArray
            keyed ?: values.filterIsInstance<JsonArray>().firstOrNull()
        }
        else -> null
    }

    private fun JsonElement.collectArrays(): List<JsonArray> = when (this) {
        is JsonArray -> listOf(this) + flatMap { it.collectArrays() }
        is JsonObject -> values.flatMap { it.collectArrays() }
        else -> emptyList()
    }

    private fun JsonObject.stringOf(vararg keys: String): String? {
        for (key in keys) {
            val value = this[key]
            if (value is JsonPrimitive && value !is JsonNull) {
                val text = value.content
                if (text.isNotBlank() && text != "null") return text
            }
        }
        return null
    }

    private fun JsonObject.longOf(vararg keys: String): Long? {
        for (key in keys) {
            val value = this[key]
            if (value is JsonPrimitive && value !is JsonNull) {
                value.content.toLongOrNull()?.let { return it }
            }
        }
        return null
    }
}
