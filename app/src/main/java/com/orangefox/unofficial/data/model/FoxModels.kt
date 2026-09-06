package com.orangefox.unofficial.data.model

import kotlinx.serialization.Serializable

/**
 * Domain models shared by the UI, the Room cache and the OrangeFox API bridge.
 *
 * The public OrangeFox API schema can change, so the parser
 * (see [com.orangefox.unofficial.data.api.DeviceParser]) normalises raw JSON
 * into these shapes before anything touches the UI.
 */
data class Device(
    val codename: String,
    val name: String,
    val oem: String,
    val imageUrl: String?,
    val fromOfflineSeed: Boolean = false
)

data class FoxBuild(
    val displayName: String,
    val fileUrl: String?,
    val version: String?,
    val sizeBytes: Long?,
    val dateLabel: String?,
    val channel: String?
)

/** Overall OrangeFox infrastructure status as reported by GET /uptime. */
data class BridgeUptime(
    val status: String?,
    val role: String?,
    val allUp: Boolean?,
    val requiredUp: Boolean?,
    val hosts: List<BridgeHost>
)

data class BridgeHost(
    val nickname: String,
    val isOk: Boolean?,
    val isOptional: Boolean?,
    val errorText: String?
)

// ---------------------------------------------------------------------------
// Wire DTOs — mirror the official Fox API 6.1.0 schema (openapi.json) but
// keep every field optional so a future schema change degrades gracefully
// instead of crashing the app.
// ---------------------------------------------------------------------------

@Serializable
data class ListDto<T>(
    val data: List<T>? = null,
    val count: Int? = null,
    val has_more: Boolean? = null
)

@Serializable
data class DeviceDto(
    val id: String? = null,
    val _id: String? = null,
    val codename: String? = null,
    val model_name: String? = null,
    val oem_name: String? = null,
    val codenames: List<String>? = null,
    val model_names: List<String>? = null,
    val supported: Boolean? = null,
    val full_name: String? = null,
    val img: String? = null,
    val url: String? = null,
    val notes: String? = null,
    val device_tree: String? = null
)

@Serializable
data class ReleaseDto(
    val id: String? = null,
    val _id: String? = null,
    val build_id: String? = null,
    val filename: String? = null,
    val version: String? = null,
    val type: String? = null,
    val date: Double? = null,
    val size: Long? = null,
    val md5: String? = null,
    val url: String? = null,
    val mirrors: Map<String, String>? = null
)

@Serializable
data class UptimeDto(
    val status: String? = null,
    val health: UptimeHealthDto? = null
)

@Serializable
data class UptimeHealthDto(
    val status: String? = null,
    val role: String? = null,
    val allUp: Boolean? = null,
    val requiredUp: Boolean? = null,
    val hosts: List<UptimeHostDto>? = null
)

@Serializable
data class UptimeHostDto(
    val nickname: String? = null,
    val isOk: Boolean? = null,
    val isOptional: Boolean? = null,
    val errorText: String? = null
)
