package com.orangefox.unofficial.data.model

/**
 * Domain models shared by the UI, the Room cache and the OrangeFox API bridge.
 *
 * The app intentionally keeps these models small and tolerant: the public
 * OrangeFox API schema can change, so the parser (see [com.orangefox.unofficial.data.api.DeviceParser])
 * normalises raw JSON into these shapes before anything touches the UI.
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
