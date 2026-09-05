package com.orangefox.unofficial.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

data class FoxPrefs(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val apiBaseUrl: String = DEFAULT_API_BASE
) {
    companion object {
        const val DEFAULT_API_BASE = "https://api.orangefox.download/"
    }
}

private val Context.dataStore by preferencesDataStore(name = "of_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("use_dynamic_color")
        val API_BASE = stringPreferencesKey("api_base_url")
    }

    val prefs: Flow<FoxPrefs> = context.dataStore.data.map { p ->
        FoxPrefs(
            themeMode = p[Keys.THEME]?.let { stored ->
                runCatching { ThemeMode.valueOf(stored) }.getOrNull()
            } ?: ThemeMode.SYSTEM,
            useDynamicColor = p[Keys.DYNAMIC] ?: true,
            apiBaseUrl = p[Keys.API_BASE]?.takeIf { it.isNotBlank() } ?: FoxPrefs.DEFAULT_API_BASE
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC] = enabled }
    }

    suspend fun setApiBaseUrl(url: String) {
        context.dataStore.edit { it[Keys.API_BASE] = url.trim() }
    }
}
