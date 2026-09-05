package com.orangefox.unofficial.ui.screens.downloads

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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
import com.orangefox.unofficial.data.model.Device
import com.orangefox.unofficial.data.model.FoxBuild
import com.orangefox.unofficial.data.repo.FoxRepository
import com.orangefox.unofficial.ui.components.BuildCard
import com.orangefox.unofficial.ui.components.DeviceCard
import com.orangefox.unofficial.ui.components.ErrorBanner
import com.orangefox.unofficial.ui.components.SectionTitle
import com.orangefox.unofficial.util.DlState
import com.orangefox.unofficial.util.DownloadHelper
import com.orangefox.unofficial.util.humanBytes
import com.orangefox.unofficial.util.openInBrowser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(private val repo: FoxRepository) : ViewModel() {

    val devices: StateFlow<List<Device>> = repo.cachedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selected = MutableStateFlow<Device?>(null)
    val selected: StateFlow<Device?> = _selected.asStateFlow()

    private val _builds = MutableStateFlow<List<FoxBuild>>(emptyList())
    val builds: StateFlow<List<FoxBuild>> = _builds.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _note = MutableStateFlow<String?>(null)
    val note: StateFlow<String?> = _note.asStateFlow()

    init {
        viewModelScope.launch { repo.seedOfflineIfEmpty() }
    }

    fun select(device: Device) {
        _selected.value = device
        loadBuilds(device.codename)
    }

    fun loadBuilds(codename: String) {
        viewModelScope.launch {
            _loading.value = true
            _note.value = null
            val result = repo.buildsForDevice(codename)
            _builds.value = result
            if (result.isEmpty()) {
                _note.value = "Builds could not be loaded from the API — use the device page on the website instead."
            }
            _loading.value = false
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FoxApp
                DownloadsViewModel(app.repository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen() {
    val app = LocalContext.current.applicationContext as FoxApp
    val vm: DownloadsViewModel = viewModel(factory = DownloadsViewModel.Factory)
    val context = LocalContext.current

    val devices by vm.devices.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val builds by vm.builds.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var tracked by remember { mutableStateOf(setOf<Long>()) }
    var states by remember { mutableStateOf(mapOf<Long, DlState>()) }

    // Poll DownloadManager while the screen is visible
    LaunchedEffect(Unit) {
        while (true) {
            delay(600)
            if (tracked.isNotEmpty()) {
                val snap = DownloadHelper.snapshot(context, tracked)
                states = states + snap
                tracked = tracked.filterNot { id ->
                    val status = snap[id]?.status
                    status != null &&
                        status != DownloadManager.STATUS_RUNNING &&
                        status != DownloadManager.STATUS_PENDING
                }.toSet()
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val notifGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED

    val suggestions = remember(devices, query) {
        if (query.isBlank()) emptyList()
        else devices.filter {
            it.name.contains(query, ignoreCase = true) || it.codename.contains(query, ignoreCase = true)
        }.take(6)
    }

    fun startDownload(build: FoxBuild) {
        val url = build.fileUrl ?: return
        val name = url.substringAfterLast('/').ifBlank { "orangefox-${System.currentTimeMillis()}.zip" }
        runCatching {
            val id = DownloadHelper.enqueue(context, url, name)
            tracked = tracked + id
            Toast.makeText(context, "Download started: $name", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Could not start the download", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Downloads") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (states.isNotEmpty()) {
                item { SectionTitle("Active & recent downloads") }
                items(states.entries.toList(), key = { it.key }) { entry ->
                    DownloadStateCard(entry.value)
                }
            }

            item { SectionTitle("Find your device") }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search device or codename (e.g. vayu)") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(suggestions, key = { it.codename }) { device ->
                DeviceCard(device) {
                    query = ""
                    vm.select(device)
                }
            }

            selected?.let { device ->
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(device.name, style = MaterialTheme.typography.headlineSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(onClick = {}, label = { Text(device.codename) })
                                AssistChip(onClick = {}, label = { Text(device.oem) })
                            }
                            OutlinedButton(
                                onClick = {
                                    openInBrowser(
                                        context,
                                        "https://orangefox.download/device/${device.codename}"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("  Open device page on orangefox.download")
                            }
                        }
                    }
                }

                if (loading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Loading builds…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                note?.let { n -> item { ErrorBanner(n) } }

                items(builds) { build ->
                    BuildCard(
                        build = build,
                        onDownload = { startDownload(build) },
                        onOpen = { build.fileUrl?.let { openInBrowser(context, it) } }
                    )
                }

                if (builds.isEmpty() && !loading && note == null) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Smartphone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Select a device above to see its OrangeFox builds.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= 33 && !notifGranted) {
                item { NotificationPermissionCard(onAllow = { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun DownloadStateCard(state: DlState) {
    val (label, done, failed) = when (state.status) {
        DownloadManager.STATUS_SUCCESSFUL -> Triple("Completed", true, false)
        DownloadManager.STATUS_FAILED -> Triple("Failed", false, true)
        DownloadManager.STATUS_PAUSED -> Triple("Paused", false, false)
        DownloadManager.STATUS_PENDING -> Triple("Waiting", false, false)
        else -> Triple("Downloading", false, false)
    }
    val target = if (state.total > 0) {
        (state.bytes.toFloat() / state.total).coerceIn(0f, 1f)
    } else 0f
    val progress by animateFloatAsState(targetValue = target, animationSpec = tween(300), label = "dl_progress")

    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        done -> Icons.Rounded.CheckCircle
                        failed -> Icons.Rounded.Cancel
                        else -> Icons.Rounded.Download
                    },
                    contentDescription = label,
                    tint = when {
                        done -> MaterialTheme.colorScheme.primary
                        failed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    state.title.ifBlank { "Download" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(
                progress = { if (failed) 0f else if (done) 1f else progress },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "${humanBytes(state.bytes)} / ${humanBytes(state.total)} — saved to Downloads/OrangeFox",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NotificationPermissionCard(onAllow: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Rounded.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Download notifications", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Allow notifications to see download progress and completion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onAllow) { Text("Allow") }
        }
    }
}
