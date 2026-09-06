package com.orangefox.unofficial.data.api

import com.orangefox.unofficial.data.model.BridgeUptime
import com.orangefox.unofficial.data.model.Device
import com.orangefox.unofficial.data.model.DeviceDto
import com.orangefox.unofficial.data.model.FoxBuild
import com.orangefox.unofficial.data.model.ListDto
import com.orangefox.unofficial.data.model.ReleaseDto
import com.orangefox.unofficial.data.model.UptimeDto
import kotlinx.serialization.decodeFromString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Parser for the OrangeFox bridge.
 *
 * Wire payloads are decoded into the DTOs defined in
 * [com.orangefox.unofficial.data.model] with `ignoreUnknownKeys`, then mapped
 * to the small domain models used by the UI. Any decode failure returns an
 * empty result so the app falls back to the Room cache / offline catalog
 * instead of crashing when the server schema evolves.
 */
object DeviceParser {

    fun parseDeviceList(raw: String): List<Device> = runCatching {
        FoxApiClient.json
            .decodeFromString<ListDto<DeviceDto>>(raw)
            .data.orEmpty()
            .mapNotNull { it.toDomain() }
    }.getOrDefault(emptyList())

    fun parseSingleDevice(raw: String): Device? {
        val json = FoxApiClient.json
        return runCatching { json.decodeFromString<DeviceDto>(raw).toDomain() }
            .recoverCatching {
                json.decodeFromString<ListDto<DeviceDto>>(raw)
                    .data.orEmpty()
                    .firstNotNullOfOrNull { it.toDomain() }
            }
            .getOrNull()
    }

    fun parseBuilds(raw: String): List<FoxBuild> = runCatching {
        FoxApiClient.json
            .decodeFromString<ListDto<ReleaseDto>>(raw)
            .data.orEmpty()
            .map { it.toDomain() }
    }.getOrDefault(emptyList())

    /** True when the paginated list response reports more pages (`has_more`). */
    fun parseHasMore(raw: String): Boolean = runCatching {
        FoxApiClient.json
            .decodeFromString<ListDto<DeviceDto>>(raw)
            .has_more == true
    }.getOrDefault(false)

    fun parseUptime(raw: String): BridgeUptime? = runCatching {
        val dto = FoxApiClient.json.decodeFromString<UptimeDto>(raw)
        val health = dto.health ?: return@runCatching null
        BridgeUptime(
            status = dto.status ?: health.status,
            role = health.role,
            allUp = health.allUp,
            requiredUp = health.requiredUp,
            hosts = health.hosts.orEmpty().mapNotNull { host ->
                val nickname = host.nickname ?: return@mapNotNull null
                com.orangefox.unofficial.data.model.BridgeHost(
                    nickname = nickname,
                    isOk = host.isOk,
                    isOptional = host.isOptional,
                    errorText = host.errorText
                )
            }
        )
    }.getOrNull()

    // ---- mapping -----------------------------------------------------------

    private fun DeviceDto.toDomain(): Device? {
        val codename = codename?.takeIf { it.isNotBlank() }
            ?: id?.takeIf { it.isNotBlank() }
            ?: return null
        var img = img
        if (img != null && img.startsWith("/")) img = "https://dl.orangefox.download$img"
        return Device(
            codename = codename,
            name = full_name?.takeIf { it.isNotBlank() }
                ?: model_name?.takeIf { it.isNotBlank() }
                ?: codename,
            oem = oem_name?.takeIf { it.isNotBlank() } ?: "Unknown",
            imageUrl = img
        )
    }

    private fun ReleaseDto.toDomain(): FoxBuild {
        val directUrl = mirrors?.get("DL")
            ?: url
            ?: id?.takeIf { it.isNotBlank() }?.let { "https://api.orangefox.download/release/$it/dl" }
        val dateLabel = date?.takeIf { it > 0 }?.let { seconds ->
            runCatching {
                SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date((seconds * 1000).toLong()))
            }.getOrNull()
        }
        return FoxBuild(
            displayName = filename?.takeIf { it.isNotBlank() }
                ?: version
                ?: id
                ?: "OrangeFox build",
            fileUrl = directUrl,
            version = version,
            sizeBytes = size,
            dateLabel = dateLabel,
            channel = type?.lowercase(Locale.US)
        )
    }
}
