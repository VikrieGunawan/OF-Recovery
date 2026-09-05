package com.orangefox.unofficial.data.repo

import android.content.Context
import com.orangefox.unofficial.data.api.DeviceParser
import com.orangefox.unofficial.data.api.FoxApiService
import com.orangefox.unofficial.data.local.DeviceDao
import com.orangefox.unofficial.data.local.DeviceEntity
import com.orangefox.unofficial.data.local.OfflineCatalog
import com.orangefox.unofficial.data.local.SettingsRepository
import com.orangefox.unofficial.data.model.Device
import com.orangefox.unofficial.data.model.FoxBuild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

sealed interface RefreshResult {
    data class Success(val count: Int) : RefreshResult
    data class Failure(val message: String?) : RefreshResult
}

/**
 * Single source of truth for everything that talks to the OrangeFox servers:
 * refresh from the API bridge -> persist into Room -> UI always observes the
 * cache, so the app keeps working when a server is down (offline fallback).
 */
class FoxRepository(
    private val api: FoxApiService,
    private val dao: DeviceDao,
    private val settings: SettingsRepository,
    private val context: Context
) {

    val cachedDevices: Flow<List<Device>> = dao.observeAll().map { list ->
        list.map { it.toModel() }
    }

    suspend fun seedOfflineIfEmpty() = withContext(Dispatchers.IO) {
        if (dao.count() == 0) {
            dao.upsertAll(OfflineCatalog.load(context).map { it.toEntity("offline") })
        }
    }

    suspend fun refreshDevices(): RefreshResult = withContext(Dispatchers.IO) {
        try {
            val base = currentBaseUrl()
            val raw = api.fetch("$base/v3/device/").use { it.string() }
            val parsed = DeviceParser.parseDeviceList(raw)
            when {
                parsed.isEmpty() -> RefreshResult.Failure(
                    "The server responded, but no devices could be parsed (API schema may have changed)."
                )
                else -> {
                    dao.upsertAll(parsed.map { it.toEntity("api") })
                    RefreshResult.Success(parsed.size)
                }
            }
        } catch (e: Exception) {
            RefreshResult.Failure(e.message)
        }
    }

    suspend fun deviceByCodename(codename: String): Device? = withContext(Dispatchers.IO) {
        dao.byCodename(codename)?.toModel() ?: run {
            seedOfflineIfEmpty()
            dao.byCodename(codename)?.toModel()
        }
    }

    suspend fun buildsForDevice(codename: String): List<FoxBuild> = withContext(Dispatchers.IO) {
        val base = currentBaseUrl()
        val candidates = listOf(
            "$base/v3/device/$codename",
            "$base/v3/device/$codename/releases"
        )
        val raw = candidates.firstNotNullOfOrNull { url ->
            runCatching { api.fetch(url).use { it.string() } }.getOrNull()
        } ?: return@withContext emptyList()
        DeviceParser.parseBuilds(raw)
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        dao.clear()
        seedOfflineIfEmpty()
    }

    private suspend fun currentBaseUrl(): String =
        runBlocking { settings.prefs.first().apiBaseUrl }.trimEnd('/')

    private fun DeviceEntity.toModel() = Device(
        codename = codename,
        name = name,
        oem = oem,
        imageUrl = imageUrl,
        fromOfflineSeed = source == "offline"
    )

    private fun Device.toEntity(source: String) = DeviceEntity(
        codename = codename,
        name = name,
        oem = oem,
        imageUrl = imageUrl,
        source = source,
        updatedAt = System.currentTimeMillis()
    )
}
