package com.orangefox.unofficial.ui.screens.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.orangefox.unofficial.FoxApp
import com.orangefox.unofficial.data.model.Device
import com.orangefox.unofficial.data.repo.FoxRepository
import com.orangefox.unofficial.data.repo.RefreshResult
import com.orangefox.unofficial.ui.components.DeviceCard
import com.orangefox.unofficial.ui.components.EmptyState
import com.orangefox.unofficial.ui.components.ErrorBanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DevicesViewModel(private val repo: FoxRepository) : ViewModel() {

    val devices: StateFlow<List<Device>> = repo.cachedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _refresh = MutableStateFlow<RefreshResult?>(null)
    val refresh: StateFlow<RefreshResult?> = _refresh.asStateFlow()

    init {
        viewModelScope.launch {
            repo.seedOfflineIfEmpty()
            refresh()
        }
    }

    fun refresh() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _refresh.value = repo.refreshDevices()
            _loading.value = false
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FoxApp
                DevicesViewModel(app.repository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DevicesScreen(onDeviceClick: (String) -> Unit) {
    val vm: DevicesViewModel = viewModel(factory = DevicesViewModel.Factory)
    val devices by vm.devices.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val refresh by vm.refresh.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var query by remember { mutableStateOf("") }
    var selectedOem by remember { mutableStateOf<String?>(null) }

    val oems = remember(devices) {
        devices.map { it.oem }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sorted()
            .take(8)
    }
    val filtered = remember(devices, query, selectedOem) {
        devices.filter { device ->
            (selectedOem == null || device.oem.equals(selectedOem, ignoreCase = true)) &&
                (query.isBlank() ||
                    device.name.contains(query, ignoreCase = true) ||
                    device.codename.contains(query, ignoreCase = true) ||
                    device.oem.contains(query, ignoreCase = true))
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Devices") },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (refresh is RefreshResult.Failure) {
                    ErrorBanner(
                        "Offline — showing the cached catalog. Pull to retry.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search device or codename (e.g. ginkgo)") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedOem == null,
                        onClick = { selectedOem = null },
                        label = { Text("All") }
                    )
                    oems.forEach { oem ->
                        FilterChip(
                            selected = selectedOem == oem,
                            onClick = { selectedOem = if (selectedOem == oem) null else oem },
                            label = { Text(oem) }
                        )
                    }
                }
                when {
                    filtered.isEmpty() && loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    filtered.isEmpty() -> EmptyState(
                        icon = Icons.Rounded.Smartphone,
                        title = "No devices found",
                        message = "Try a different search, clear the filters, or pull down to refresh from the OrangeFox API."
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered, key = { it.codename }) { device ->
                            DeviceCard(device) { onDeviceClick(device.codename) }
                        }
                    }
                }
            }
        }
    }
}
