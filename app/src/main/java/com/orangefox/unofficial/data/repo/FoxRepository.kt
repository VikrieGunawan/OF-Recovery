package com.orangefox.unofficial.data.repo

import android.content.Context
import android.os.Build
import com.orangefox.unofficial.data.api.DeviceParser
import com.orangefox.unofficial.data.api.FoxApiClient
import com.orangefox.unofficial.data.api.FoxApiService
import com.orangefox.unofficial.data.local.DeviceDao
import com.orangefox.unofficial.data.local.DeviceEntity
import com.orangefox.unofficial.data.local.OfflineCatalog
import com.orangefox.unofficial.data.local.SettingsRepository
import com.orangefox.unofficial.data.model.BridgeUptime
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
 *
 * Endpoints verified against the official OpenAPI spec (Fox API 6.1.0):
 *   GET /devices?limit&skip&oem_name&codename&model_name&has_releases
 *   GET /devices/get?codename
 *   GET /releases?codename&device_id&type&limit&skip   (newest first)
 *   GET /uptime
 *   Download mirror: mirrors.DL -> https://api.orangefox.download/release/{id}/dl
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
            val seen = LinkedHashMap<String, Device>()
            val pageSize = 100
            var skip = 0
            var pages = 0
            // The catalog is large; walk the pages until the server reports
            // no more data, capped for safety.
            while (pages < 6) {
                pages++
                val raw = service().getDevices(limit = pageSize, skip = skip).use { it.string() }
                val page = DeviceParser.parseDeviceList(raw)
                page.forEach { seen.putIfAbsent(it.codename, it) }
                val hasMore = DeviceParser.parseHasMore(raw)
                if (page.isEmpty() || !hasMore) break
                skip += pageSize
            }
            if (seen.isEmpty()) {
                RefreshResult.Failure(
                    "The server responded, but no devices could be parsed (API schema may have changed)."
                )
            } else {
                dao.upsertAll(seen.values.map { it.toEntity("api") })
                RefreshResult.Success(seen.size)
            }
        } catch (e: Exception) {
            RefreshResult.Failure(e.message)
        }
    }

    suspend fun deviceByCodename(codename: String): Device? = withContext(Dispatchers.IO) {
        dao.byCodename(codename)?.toModel()?.let { return@withContext it }
        seedOfflineIfEmpty()
        dao.byCodename(codename)?.toModel()?.let { return@withContext it }
        // Cache miss — ask the bridge directly (GET /devices/get?codename=...).
        runCatching {
            DeviceParser.parseSingleDevice(
                service().getDeviceByCodename(codename).use { it.string() }
            )
        }.getOrNull()?.also { dao.upsertAll(listOf(it.toEntity("api"))) }
    }

    /** Releases for one device, newest first (GET /releases?codename=...). */
    suspend fun buildsForDevice(codename: String): List<FoxBuild> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = service().getReleases(codename = codename, limit = 50).use { it.string() }
            DeviceParser.parseBuilds(raw)
        }.getOrDefault(emptyList())
    }

    /** Most recent releases across every device — powers the Home feed. */
    suspend fun latestReleases(limit: Int = 10): List<FoxBuild> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = service().getReleases(limit = limit).use { it.string() }
            DeviceParser.parseBuilds(raw)
        }.getOrDefault(emptyList())
    }

    /**
     * Try to recognise THIS phone inside the OrangeFox catalog so Home can show
     * a direct link. Matches on Build.DEVICE (usually the codename) first, then
     * on the marketing model (Build.MODEL).
     */
    suspend fun matchThisPhone(): Device? = withContext(Dispatchers.IO) {
        val devNeedle = (Build.DEVICE ?: "").trim().lowercase()
        val modelNeedle = (Build.MODEL ?: "").trim()
        if (devNeedle.isBlank() && modelNeedle.isBlank()) return@withContext null

        // 1) Cheap local match from whatever is cached (offline-friendly).
        val localHit = cachedDevices.first().firstOrNull { d ->
            d.codename.lowercase() == devNeedle ||
                (modelNeedle.isNotBlank() && d.name.equals(modelNeedle, ignoreCase = true))
        }
        if (localHit != null) return@withContext localHit

        // 2) Ask the bridge: codename lookup, then model lookup.
        runCatching {
            if (devNeedle.isNotBlank()) {
                DeviceParser.parseSingleDevice(
                    service().getDeviceByCodename(devNeedle).use { it.string() }
                )
            } else null
        }.getOrNull()?.let { hit ->
            dao.upsertAll(listOf(hit.toEntity("api")))
            return@withContext hit
        }
        runCatching {
            val raw = service().getDevices(model = modelNeedle, limit = 10).use { it.string() }
            DeviceParser.parseDeviceList(raw).firstOrNull()
        }.getOrNull()?.also { hit -> dao.upsertAll(listOf(hit.toEntity("api"))) }
    }

    /** Official aggregated infrastructure status (GET /uptime). */
    suspend fun uptime(): BridgeUptime? = withContext(Dispatchers.IO) {
        runCatching {
            DeviceParser.parseUptime(service().getUptime().use { it.string() })
        }.getOrNull()
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        dao.clear()
        seedOfflineIfEmpty()
    }

    private suspend fun currentBaseUrl(): String =
        runBlocking { settings.prefs.first().apiBaseUrl }.trimEnd('/')

    @Volatile
    private var customService: Pair<String, FoxApiService>? = null

    /**
     * Typed service against the effective base URL: the default official host,
     * or the custom one set in Settings (kept as an escape hatch if OrangeFox
     * ever moves its API).
     */
    private suspend fun service(): FoxApiService {
        val normalized = currentBaseUrl().trimEnd('/') + "/"
        if (normalized == FoxApiClient.DEFAULT_BASE_URL) return api
        customService?.let { (base, svc) -> if (base == normalized) return svc }
        val created = FoxApiClient.create(normalized)
        customService = normalized to created
        return created
    }

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
} */
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
            val seen = LinkedHashMap<String, Device>()
            val pageSize = 100
            var skip = 0
            var pages = 0
            // The catalog is large; walk the pages until the server reports
            // no more data, capped for safety.
            while (pages < 6) {
                pages++
                val raw = service().getDevices(limit = pageSize, skip = skip).use { it.string() }
                val page = DeviceParser.parseDeviceList(raw)
                page.forEach { seen.putIfAbsent(it.codename, it) }
                val hasMore = DeviceParser.parseHasMore(raw)
                if (page.isEmpty() || !hasMore) break
                skip += pageSize
            }
            if (seen.isEmpty()) {
                RefreshResult.Failure(
                    "The server responded, but no devices could be parsed (API schema may have changed)."
                )
            } else {
                dao.upsertAll(seen.values.map { it.toEntity("api") })
                RefreshResult.Success(seen.size)
            }
        } catch (e: Exception) {
            RefreshResult.Failure(e.message)
        }
    }

    suspend fun deviceByCodename(codename: String): Device? = withContext(Dispatchers.IO) {
        dao.byCodename(codename)?.toModel()?.let { return@withContext it }
        seedOfflineIfEmpty()
        dao.byCodename(codename)?.toModel()?.let { return@withContext it }
        // Cache miss — ask the bridge directly (GET /devices/get?codename=...).
        runCatching {
            DeviceParser.parseSingleDevice(
                service().getDeviceByCodename(codename).use { it.string() }
            )
        }.getOrNull()?.also { dao.upsertAll(listOf(it.toEntity("api"))) }
    }

    /** Releases for one device, newest first (GET /releases?codename=...). */
    suspend fun buildsForDevice(codename: String): List<FoxBuild> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = service().getReleases(codename = codename, limit = 50).use { it.string() }
            DeviceParser.parseBuilds(raw)
        }.getOrDefault(emptyList())
    }

    /** Most recent releases across every device — powers the Home feed. */
    suspend fun latestReleases(limit: Int = 10): List<FoxBuild> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = service().getReleases(limit = limit).use { it.string() }
            DeviceParser.parseBuilds(raw)
        }.getOrDefault(emptyList())
    }

    /**
     * Try to recognise THIS phone inside the OrangeFox catalog so Home can show
     * a direct link. Matches on Build.DEVICE (usually the codename) first, then
     * on the marketing model (Build.MODEL).
     */
    suspend fun matchThisPhone(): Device? = withContext(Dispatchers.IO) {
        val devNeedle = (Build.DEVICE ?: "").trim().lowercase()
        val modelNeedle = (Build.MODEL ?: "").trim()
        if (devNeedle.isBlank() && modelNeedle.isBlank()) return@withContext null

        // 1) Cheap local match from whatever is cached (offline-friendly).
        val localHit = cachedDevices.first().firstOrNull { d ->
            d.codename.lowercase() == devNeedle ||
                (modelNeedle.isNotBlank() && d.name.equals(modelNeedle, ignoreCase = true))
        }
        if (localHit != null) return@withContext localHit

        // 2) Ask the bridge: codename lookup, then model lookup.
        runCatching {
            if (devNeedle.isNotBlank()) {
                DeviceParser.parseSingleDevice(
                    service().getDeviceByCodename(devNeedle).use { it.string() }
                )
            } else null
        }.getOrNull()?.let { hit ->
            dao.upsertAll(listOf(hit.toEntity("api")))
            return@withContext hit
        }
        runCatching {
            val raw = service().getDevices(model = modelNeedle, limit = 10).use { it.string() }
            DeviceParser.parseDeviceList(raw).firstOrNull()
        }.getOrNull()?.also { hit -> dao.upsertAll(listOf(hit.toEntity("api"))) }
    }

    /** Official aggregated infrastructure status (GET /uptime). */
    suspend fun uptime(): BridgeUptime? = withContext(Dispatchers.IO) {
        runCatching {
            DeviceParser.parseUptime(service().getUptime().use { it.string() })
        }.getOrNull()
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        dao.clear()
        seedOfflineIfEmpty()
    }

    private suspend fun currentBaseUrl(): String =
        runBlocking { settings.prefs.first().apiBaseUrl }.trimEnd('/')

    @Volatile
    private var customService: Pair<String, FoxApiService>? = null

    /**
     * Typed service against the effective base URL: the default official host,
     * or the custom one set in Settings (kept as an escape hatch if OrangeFox
     * ever moves its API).
     */
    private suspend fun service(): FoxApiService {
        val normalized = currentBaseUrl().trimEnd('/') + "/"
        if (normalized == FoxApiClient.DEFAULT_BASE_URL) return api
        customService?.let { (base, svc) -> if (base == normalized) return svc }
        val created = FoxApiClient.create(normalized)
        customService = normalized to created
        return created
    }

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
