package com.orangefox.unofficial.data.api

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

/**
 * Bridge to the public OrangeFox servers.
 *
 * Verified against the official OpenAPI spec (https://api.orangefox.download/openapi.json,
 * Fox API 6.1.0). The API has NO version prefix — endpoints live directly on the
 * root path. Calls return the raw body so the tolerant parser can normalise the
 * schema before anything touches the UI.
 *
 * Servers used by the app:
 *   - REST API + downloads : https://api.orangefox.download/   (devices, releases, uptime, /release/{id}/dl)
 *   - Website              : https://orangefox.download/       (device & release pages)
 *   - Wiki                 : https://wiki.orangefox.download/  (installation guides)
 *   - Source               : https://gitlab.com/OrangeFox
 */
interface FoxApiService {

    /** GET /devices — list devices with filters. */
    @GET("devices")
    suspend fun getDevices(
        @Query("limit") limit: Int? = null,
        @Query("skip") skip: Int? = null,
        @Query("oem_name") oem: String? = null,
        @Query("codename") codename: String? = null,
        @Query("model_name") model: String? = null,
        @Query("has_releases") hasReleases: Boolean? = null
    ): ResponseBody

    /** GET /devices/get — one device by codename. */
    @GET("devices/get")
    suspend fun getDeviceByCodename(@Query("codename") codename: String): ResponseBody

    /** GET /oems — unique OEM names. */
    @GET("oems")
    suspend fun getOems(): ResponseBody

    /** GET /releases — releases with filters (newest first). */
    @GET("releases")
    suspend fun getReleases(
        @Query("codename") codename: String? = null,
        @Query("device_id") deviceId: String? = null,
        @Query("type") type: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("skip") skip: Int? = null
    ): ResponseBody

    /** GET /releases/get — one release by build_id. */
    @GET("releases/get")
    suspend fun getRelease(@Query("build_id") buildId: String): ResponseBody

    /** GET /uptime — official aggregated infrastructure status. */
    @GET("uptime")
    suspend fun getUptime(): ResponseBody

    /** Free-form GET for the Bridge Health probes and Settings-configured hosts. */
    @GET
    suspend fun fetch(@Url url: String): ResponseBody
}

object FoxApiClient {

    const val DEFAULT_BASE_URL = "https://api.orangefox.download/"
    const val USER_AGENT = "OFRecovery/1.0 (Android; unofficial OrangeFox companion app)"

    /** Shared tolerant JSON config — unknown fields are ignored, nulls fall back to defaults. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun create(baseUrl: String = DEFAULT_BASE_URL): FoxApiService {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .build()
                )
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .build()
            .create(FoxApiService::class.java)
    }
}
