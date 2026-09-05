package com.orangefox.unofficial

import android.app.Application
import androidx.room.Room
import com.orangefox.unofficial.data.api.FoxApiClient
import com.orangefox.unofficial.data.api.FoxApiService
import com.orangefox.unofficial.data.local.FoxDatabase
import com.orangefox.unofficial.data.local.SettingsRepository
import com.orangefox.unofficial.data.repo.FoxRepository

/**
 * Manual dependency container. Keeps the graph tiny on purpose — no DI
 * framework, so the build stays simple and CI-friendly.
 */
class FoxApp : Application() {

    val database: FoxDatabase by lazy {
        Room.databaseBuilder(this, FoxDatabase::class.java, "of_recovery.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val apiService: FoxApiService by lazy { FoxApiClient.create() }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    val repository: FoxRepository by lazy {
        FoxRepository(apiService, database.deviceDao(), settingsRepository, this)
    }
}
