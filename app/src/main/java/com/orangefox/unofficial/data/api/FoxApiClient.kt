package com.orangefox.unofficial.data.api

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

/**
 * Bridge to the public OrangeFox servers. Every call returns the raw body so
 * the tolerant parser can normalise whichever schema the server returns.
 *
 * All endpoints used by the app live here and in [com.orangefox.unofficial.data.repo.FoxRepository]:
 *   - REST API   : https://api.orangefox.download/  (device catalog + releases)
 *   - Downloads  : https://dl.orangefox.download/   (recovery image files)
 *   - Website    : https://orangefox.download/      (device pages)
 *   - Wiki       : https://wiki.orangefox.download/ (installation guides)
 *   - Source     : https://gitlab.com/OrangeFox
 */
interface FoxApiService {

    @GET
    suspend fun fetch(@Url url: String): ResponseBody
}

object FoxApiClient {

    const val DEFAULT_BASE_URL = "https://api.orangefox.download/"
    const val USER_AGENT = "OFRecovery/1.0 (Android; unofficial OrangeFox companion app)"

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
