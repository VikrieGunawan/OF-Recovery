package com.orangefox.unofficial.ui.screens.bridge

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orangefox.unofficial.data.api.FoxApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class BridgeEndpoint(
    val name: String,
    val role: String,
    val url: String
)

data class EndpointHealth(
    val endpoint: BridgeEndpoint,
    val online: Boolean,
    val latencyMs: Long,
    val httpCode: Int?,
    val error: String?
)

/** Every server the app can bridge with — keep in sync with the README. */
object OrangeEndpoints {
    val all = listOf(
        BridgeEndpoint("API Server", "Device catalog & release data (REST)", "https://api.orangefox.download/v3/device/"),
        BridgeEndpoint("Download Server", "Hosts the recovery image files", "https://dl.orangefox.download/"),
        BridgeEndpoint("Main Website", "Device pages & web downloads", "https://orangefox.download/"),
        BridgeEndpoint("Wiki", "Installation guides & documentation", "https://wiki.orangefox.download/"),
        BridgeEndpoint("GitLab", "Source code & build pipelines", "https://gitlab.com/OrangeFox")
    )
}

class BridgeViewModel(application: Application) : AndroidViewModel(application) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private val _health = MutableStateFlow<Map<String, EndpointHealth>>(emptyMap())
    val health: StateFlow<Map<String, EndpointHealth>> = _health.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun checkAll() {
        if (_running.value) return
        viewModelScope.launch {
            _running.value = true
            _health.value = emptyMap()
            for (endpoint in OrangeEndpoints.all) {
                val result = withContext(Dispatchers.IO) { probe(endpoint) }
                _health.value = _health.value + (endpoint.name to result)
            }
            _running.value = false
        }
    }

    private fun probe(endpoint: BridgeEndpoint): EndpointHealth {
        val startedAt = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url(endpoint.url)
                .header("User-Agent", FoxApiClient.USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                EndpointHealth(
                    endpoint = endpoint,
                    online = response.isSuccessful,
                    latencyMs = System.currentTimeMillis() - startedAt,
                    httpCode = response.code,
                    error = if (response.isSuccessful) null else "HTTP ${response.code}"
                )
            }
        } catch (e: Exception) {
            EndpointHealth(
                endpoint = endpoint,
                online = false,
                latencyMs = System.currentTimeMillis() - startedAt,
                httpCode = null,
                error = e.message ?: "unreachable"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BridgeScreen(onBack: () -> Unit) {
    val vm: BridgeViewModel = viewModel()
    val health by vm.health.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.checkAll() }

    val onlineCount = health.values.count { it.online }
    val knownCount = health.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bridge Health") },
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
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("OrangeFox bridge status", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (running) "Probing every OrangeFox endpoint…" else "$onlineCount / ${OrangeEndpoints.all.size} endpoints online",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (running) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        TextButton(onClick = { vm.checkAll() }, enabled = !running) {
                            Text("Re-run checks")
                        }
                    }
                }
            }

            items(OrangeEndpoints.all, key = { it.name }) { endpoint ->
                EndpointCard(endpoint, health[endpoint.name])
            }

            item { RequirementsCard() }
        }
    }
}

@Composable
private fun EndpointCard(endpoint: BridgeEndpoint, health: EndpointHealth?) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon: ImageVector
            val tint: androidx.compose.ui.graphics.Color
            when {
                health == null -> {
                    icon = Icons.Rounded.HourglassTop
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                }
                health.online -> {
                    icon = Icons.Rounded.CloudDone
                    tint = MaterialTheme.colorScheme.primary
                }
                else -> {
                    icon = Icons.Rounded.CloudOff
                    tint = MaterialTheme.colorScheme.error
                }
            }
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(endpoint.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    endpoint.role,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    endpoint.url,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                health?.let { h ->
                    if (!h.online && h.error != null) {
                        Text(
                            "Error: ${h.error}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = when {
                    health == null -> MaterialTheme.colorScheme.surfaceVariant
                    health.online -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.errorContainer
                }
            ) {
                Text(
                    text = when {
                        health == null -> "…"
                        health.online -> "${health.latencyMs} ms"
                        else -> "down"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RequirementsCard() {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("What does the bridge need?", style = MaterialTheme.typography.titleMedium)
            Requirement("HTTPS connectivity to api.orangefox.download, dl.orangefox.download, orangefox.download, wiki.orangefox.download and gitlab.com/OrangeFox")
            Requirement("INTERNET and ACCESS_NETWORK_STATE permissions in the manifest")
            Requirement("An HTTP client with timeouts, a clear User-Agent and graceful failure handling (no API key needed — the OrangeFox API is public)")
            Requirement("Tolerant JSON parsing so schema changes on the server never crash the app")
            Requirement("A local Room cache plus a bundled offline catalog, so the app still works when a server is down")
            Requirement("A configurable base URL (Settings) in case OrangeFox ever moves its API")
        }
    }
}

@Composable
private fun Requirement(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
