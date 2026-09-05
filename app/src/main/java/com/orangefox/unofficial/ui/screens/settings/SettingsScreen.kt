package com.orangefox.unofficial.ui.screens.settings

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.orangefox.unofficial.FoxApp
import com.orangefox.unofficial.data.local.FoxPrefs
import com.orangefox.unofficial.data.local.SettingsRepository
import com.orangefox.unofficial.data.local.ThemeMode
import com.orangefox.unofficial.data.repo.FoxRepository
import com.orangefox.unofficial.ui.components.SectionTitle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val repo: FoxRepository
) : ViewModel() {

    val prefs: StateFlow<FoxPrefs> = settings.prefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoxPrefs())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }
    fun setApiBaseUrl(url: String) = viewModelScope.launch {
        if (url.isNotBlank()) settings.setApiBaseUrl(url)
    }

    fun clearCache() = viewModelScope.launch { repo.clearCache() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FoxApp
                SettingsViewModel(app.settingsRepository, app.repository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenAbout: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var apiBaseText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(prefs.apiBaseUrl) {
        if (apiBaseText.isBlank()) apiBaseText = prefs.apiBaseUrl
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val notifGranted = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.POST_NOTIFICATIONS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionTitle("Appearance") }

            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Theme", style = MaterialTheme.typography.titleMedium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = prefs.themeMode == mode,
                                    onClick = { vm.setTheme(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ThemeMode.entries.size
                                    )
                                ) {
                                    Text(mode.label)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    ListItem(
                        headlineContent = { Text("Dynamic color (Material You)") },
                        supportingContent = {
                            Text(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                    "Use wallpaper-based colors on Android 12+. The Fox Orange palette is used when off."
                                else
                                    "Requires Android 12 or newer. The Fox Orange palette is used on this device."
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = prefs.useDynamicColor,
                                onCheckedChange = { vm.setDynamicColor(it) },
                                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            )
                        }
                    )
                }
            }

            item { SectionTitle("Connection") }

            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("API base URL", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = apiBaseText,
                            onValueChange = { apiBaseText = it },
                            singleLine = true,
                            placeholder = { Text(FoxPrefs.DEFAULT_API_BASE) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { vm.setApiBaseUrl(apiBaseText) }) {
                                Text("Save")
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Applies after the app restarts. No API key is required — the OrangeFox API is public.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    ListItem(
                        headlineContent = { Text("Clear device cache") },
                        supportingContent = { Text("Removes cached devices and re-seeds the offline catalog.") },
                        trailingContent = {
                            IconButton(onClick = { vm.clearCache() }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Clear cache")
                            }
                        }
                    )
                }
            }

            item { SectionTitle("Notifications") }

            if (Build.VERSION.SDK_INT >= 33 && !notifGranted) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        ListItem(
                            headlineContent = { Text("Download notifications") },
                            supportingContent = { Text("Allow progress notifications for recovery downloads.") },
                            leadingContent = { Icon(Icons.Rounded.Notifications, contentDescription = null) },
                            trailingContent = {
                                TextButton(onClick = { permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) {
                                    Text("Allow")
                                }
                            }
                        )
                    }
                }
            }

            item { SectionTitle("More") }

            item {
                Card(
                    onClick = onOpenAbout,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    ListItem(
                        headlineContent = { Text("About OF Recovery") },
                        supportingContent = { Text("Version, credits & open-source libraries") },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) }
                    )
                }
            }
        }
    }
}import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.orangefox.unofficial.FoxApp
import com.orangefox.unofficial.data.local.FoxPrefs
import com.orangefox.unofficial.data.local.SettingsRepository
import com.orangefox.unofficial.data.local.ThemeMode
import com.orangefox.unofficial.data.repo.FoxRepository
import com.orangefox.unofficial.ui.components.SectionTitle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val repo: FoxRepository
) : ViewModel() {

    val prefs: StateFlow<FoxPrefs> = settings.prefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoxPrefs())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }
    fun setApiBaseUrl(url: String) = viewModelScope.launch {
        if (url.isNotBlank()) settings.setApiBaseUrl(url)
    }

    fun clearCache() = viewModelScope.launch { repo.clearCache() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FoxApp
                SettingsViewModel(app.settingsRepository, app.repository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenAbout: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var apiBaseText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(prefs.apiBaseUrl) {
        if (apiBaseText.isBlank()) apiBaseText = prefs.apiBaseUrl
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val notifGranted = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.POST_NOTIFICATIONS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionTitle("Appearance") }

            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Theme", style = MaterialTheme.typography.titleMedium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = prefs.themeMode == mode,
                                    onClick = { vm.setTheme(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ThemeMode.entries.size
                                    )
                                ) {
                                    Text(mode.label)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    ListItem(
                        headlineContent = { Text("Dynamic color (Material You)") },
                        supportingContent = {
                            Text(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                    "Use wallpaper-based colors on Android 12+. The Fox Orange palette is used when off."
                                else
                                    "Requires Android 12 or newer. The Fox Orange palette is used on this device."
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = prefs.useDynamicColor,
                                onCheckedChange = { vm.setDynamicColor(it) },
                                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            )
                        }
                    )
                }
            }

            item { SectionTitle("Connection") }

            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("API base URL", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = apiBaseText,
                            onValueChange = { apiBaseText = it },
                            singleLine = true,
                            placeholder = { Text(FoxPrefs.DEFAULT_API_BASE) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { vm.setApiBaseUrl(apiBaseText) }) {
                                Text("Save")
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Applies after the app restarts. No API key is required — the OrangeFox API is public.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    ListItem(
                        headlineContent = { Text("Clear device cache") },
                        supportingContent = { Text("Removes cached devices and re-seeds the offline catalog.") },
                        trailingContent = {
                            IconButton(onClick = { vm.clearCache() }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Clear cache")
                            }
                        }
                    )
                }
            }

            item { SectionTitle("Notifications") }

            if (Build.VERSION.SDK_INT >= 33 && !notifGranted) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        ListItem(
                            headlineContent = { Text("Download notifications") },
                            supportingContent = { Text("Allow progress notifications for recovery downloads.") },
                            leadingContent = { Icon(Icons.Rounded.Notifications, contentDescription = null) },
                            trailingContent = {
                                TextButton(onClick = { permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) {
                                    Text("Allow")
                                }
                            }
                        )
                    }
                }
            }

            item { SectionTitle("More") }

            item {
                Card(
                    onClick = onOpenAbout,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    ListItem(
                        headlineContent = { Text("About OF Recovery") },
                        supportingContent = { Text("Version, credits & open-source libraries") },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) }
                    )
                }
            }
        }
    }
}import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.orangefox.unofficial.FoxApp
import com.orangefox.unofficial.data.local.FoxPrefs
import com.orangefox.unofficial.data.local.SettingsRepository
import com.orangefox.unofficial.data.local.ThemeMode
import com.orangefox.unofficial.data.repo.FoxRepository
import com.orangefox.unofficial.ui.components.SectionTitle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val repo: FoxRepository
) : ViewModel() {

    val prefs: StateFlow<FoxPrefs> = settings.prefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoxPrefs())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }
    fun setApiBaseUrl(url: String) = viewModelScope.launch {
        if (url.isNotBlank()) settings.setApiBaseUrl(url)
    }

    fun clearCache() = viewModelScope.launch { repo.clearCache() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FoxApp
                SettingsViewModel(app.settingsRepository, app.repository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenAbout: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var apiBaseText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(prefs.apiBaseUrl) {
        if (apiBaseText.isBlank()) apiBaseText = prefs.apiBaseUrl
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val notifGranted = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.POST_NOTIFICATIONS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionTitle("Appearance") }

            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Theme", style = MaterialTheme.typography.titleMedium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = prefs.themeMode == mode,
                                    onClick = { vm.setTheme(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ThemeMode.entries.size
                                    )
                                ) {
                                    Text(mode.label)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    ListItem(
                        headlineContent = { Text("Dynamic color (Material You)") },
                        supportingContent = {
                            Text(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                    "Use wallpaper-based colors on Android 12+. The Fox Orange palette is used when off."
                                else
                                    "Requires Android 12 or newer. The Fox Orange palette is used on this device."
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = prefs.useDynamicColor,
                                onCheckedChange = { vm.setDynamicColor(it) },
                                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            )
                        }
                    )
                }
            }

            item { SectionTitle("Connection") }

            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("API base URL", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = apiBaseText,
                            onValueChange = { apiBaseText = it },
                            singleLine = true,
                            placeholder = { Text(FoxPrefs.DEFAULT_API_BASE) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { vm.setApiBaseUrl(apiBaseText) }) {
                                Text("Save")
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Applies after the app restarts. No API key is required — the OrangeFox API is public.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    ListItem(
                        headlineContent = { Text("Clear device cache") },
                        supportingContent = { Text("Removes cached devices and re-seeds the offline catalog.") },
                        trailingContent = {
                            IconButton(onClick = { vm.clearCache() }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Clear cache")
                            }
                        }
                    )
                }
            }

            item { SectionTitle("Notifications") }

            if (Build.VERSION.SDK_INT >= 33 && !notifGranted) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        ListItem(
                            headlineContent = { Text("Download notifications") },
                            supportingContent = { Text("Allow progress notifications for recovery downloads.") },
                            leadingContent = { Icon(Icons.Rounded.Notifications, contentDescription = null) },
                            trailingContent = {
                                TextButton(onClick = { permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) {
                                    Text("Allow")
                                }
                            }
                        )
                    }
                }
            }

            item { SectionTitle("More") }

            item {
                Card(
                    onClick = onOpenAbout,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    ListItem(
                        headlineContent = { Text("About OF Recovery") },
                        supportingContent = { Text("Version, credits & open-source libraries") },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) }
                    )
                }
            }
        }
    }
}import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.orangefox.unofficial.FoxApp
import com.orangefox.unofficial.data.local.FoxPrefs
import com.orangefox.unofficial.data.local.SettingsRepository
import com.orangefox.unofficial.data.local.ThemeMode
import com.orangefox.unofficial.data.repo.FoxRepository
import com.orangefox.unofficial.ui.components.SectionTitle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val repo: FoxRepository
) : ViewModel() {

    val prefs: StateFlow<FoxPrefs> = settings.prefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoxPrefs())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }
    fun setApiBaseUrl(url: String) = viewModelScope.launch {
        if (url.isNotBlank()) settings.setApiBaseUrl(url)
    }

    fun clearCache() = viewModelScope.launch { repo.clearCache() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FoxApp
                SettingsViewModel(app.settingsRepository, app.repository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenAbout: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var apiBaseText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(prefs.apiBaseUrl) {
        if (apiBaseText.isBlank()) apiBaseText = prefs.apiBaseUrl
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val notifGranted = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.POST_NOTIFICATIONS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionTitle("Appearance") }

            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Theme", style = MaterialTheme.typography.titleMedium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = prefs.themeMode == mode,
                                    onClick = { vm.setTheme(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ThemeMode.entries.size
                                    )
                                ) {
                                    Text(mode.label)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    ListItem(
                        headlineContent = { Text("Dynamic color (Material You)") },
                        supportingContent = {
                            Text(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                    "Use wallpaper-based colors on Android 12+. The Fox Orange palette is used when off."
                                else
                                    "Requires Android 12 or newer. The Fox Orange palette is used on this device."
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = prefs.useDynamicColor,
                                onCheckedChange = { vm.setDynamicColor(it) },
                                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            )
                        }
                    )
                }
            }

            item { SectionTitle("Connection") }

            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("API base URL", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = apiBaseText,
                            onValueChange = { apiBaseText = it },
                            singleLine = true,
                            placeholder = { Text(FoxPrefs.DEFAULT_API_BASE) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { vm.setApiBaseUrl(apiBaseText) }) {
                                Text("Save")
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Applies after the app restarts. No API key is required — the OrangeFox API is public.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    ListItem(
                        headlineContent = { Text("Clear device cache") },
                        supportingContent = { Text("Removes cached devices and re-seeds the offline catalog.") },
                        trailingContent = {
                            IconButton(onClick = { vm.clearCache() }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Clear cache")
                            }
                        }
                    )
                }
            }

            item { SectionTitle("Notifications") }

            if (Build.VERSION.SDK_INT >= 33 && !notifGranted) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        ListItem(
                            headlineContent = { Text("Download notifications") },
                            supportingContent = { Text("Allow progress notifications for recovery downloads.") },
                            leadingContent = { Icon(Icons.Rounded.Notifications, contentDescription = null) },
                            trailingContent = {
                                TextButton(onClick = { permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) {
                                    Text("Allow")
                                }
                            }
                        )
                    }
                }
            }

            item { SectionTitle("More") }

            item {
                Card(
                    onClick = onOpenAbout,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    ListItem(
                        headlineContent = { Text("About OF Recovery") },
                        supportingContent = { Text("Version, credits & open-source libraries") },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.ChevronRight, contentDescription = null) }
                    )
                }
            }
        }
    }
}
