package com.orangefox.unofficial.ui.screens.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.orangefox.unofficial.FoxApp
import com.orangefox.unofficial.R
import com.orangefox.unofficial.data.model.Device
import com.orangefox.unofficial.data.model.FoxBuild
import com.orangefox.unofficial.data.repo.FoxRepository
import com.orangefox.unofficial.ui.components.BuildCard
import com.orangefox.unofficial.ui.components.EmptyState
import com.orangefox.unofficial.ui.components.ErrorBanner
import com.orangefox.unofficial.ui.components.SectionTitle
import com.orangefox.unofficial.util.DownloadHelper
import com.orangefox.unofficial.util.openInBrowser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.widget.Toast

class DeviceDetailViewModel(
    private val repo: FoxRepository,
    private val codename: String
) : ViewModel() {

    private val _device = MutableStateFlow<Device?>(null)
    val device: StateFlow<Device?> = _device.asStateFlow()

    private val _builds = MutableStateFlow<List<FoxBuild>>(emptyList())
    val builds: StateFlow<List<FoxBuild>> = _builds.asStateFlow()

    private val _loadingBuilds = MutableStateFlow(false)
    val loadingBuilds: StateFlow<Boolean> = _loadingBuilds.asStateFlow()

    private val _buildsNote = MutableStateFlow<String?>(null)
    val buildsNote: StateFlow<String?> = _buildsNote.asStateFlow()

    init {
        viewModelScope.launch { _device.value = repo.deviceByCodename(codename) }
        loadBuilds()
    }

    fun loadBuilds() {
        if (_loadingBuilds.value) return
        viewModelScope.launch {
            _loadingBuilds.value = true
            _buildsNote.value = null
            val result = repo.buildsForDevice(codename)
            _builds.value = result
            if (result.isEmpty()) {
                _buildsNote.value =
                    "No builds could be parsed from the API for this device. Use the device page on the website instead."
            }
            _loadingBuilds.value = false
        }
    }

    companion object {
        fun factory(codename: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FoxApp
                DeviceDetailViewModel(app.repository, codename)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(codename: String, onBack: () -> Unit) {
    val vm: DeviceDetailViewModel = viewModel(
        key = "device_$codename",
        factory = DeviceDetailViewModel.factory(codename)
    )
    val device by vm.device.collectAsStateWithLifecycle()
    val builds by vm.builds.collectAsStateWithLifecycle()
    val loadingBuilds by vm.loadingBuilds.collectAsStateWithLifecycle()
    val buildsNote by vm.buildsNote.collectAsStateWithLifecycle()
    val context = LocalContext.current

    fun startDownload(build: FoxBuild) {
        val url = build.fileUrl ?: return
        val name = url.substringAfterLast('/').ifBlank { "orangefox-${System.currentTimeMillis()}.zip" }
        runCatching {
            DownloadHelper.enqueue(context, url, name)
            Toast.makeText(context, "Download started — see the Downloads tab", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Could not start the download", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        device?.name ?: codename,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = device?.imageUrl,
                                contentDescription = device?.name ?: codename,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                placeholder = painterResource(R.drawable.img_placeholder),
                                error = painterResource(R.drawable.img_placeholder),
                                fallback = painterResource(R.drawable.img_placeholder)
                            )
                        }
                        Text(
                            device?.name ?: codename,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = {}, label = { Text(device?.codename ?: codename) })
                            AssistChip(onClick = {}, label = { Text(device?.oem ?: "Unknown") })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    openInBrowser(context, "https://orangefox.download/device/$codename")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("  Device page")
                            }
                            OutlinedButton(onClick = { vm.loadBuilds() }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Releases") }

            if (loadingBuilds) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Loading builds from the OrangeFox bridge…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            buildsNote?.let { note ->
                item { ErrorBanner(note) }
                item {
                    Button(
                        onClick = { openInBrowser(context, "https://orangefox.download/device/$codename") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Open downloads on orangefox.download")
                    }
                }
            }

            if (builds.isEmpty() && !loadingBuilds && buildsNote == null) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Smartphone,
                        title = "No releases yet",
                        message = "This device has no parsed releases. Pull down on the Devices tab to refresh the catalog."
                    )
                }
            }

            items(builds) { build ->
                BuildCard(
                    build = build,
                    onDownload = { startDownload(build) },
                    onOpen = { build.fileUrl?.let { openInBrowser(context, it) } }
                )
            }
        }
    }
}
