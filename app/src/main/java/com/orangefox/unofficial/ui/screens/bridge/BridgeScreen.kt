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
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orangefox.unofficial.FoxApp
import com.orangefox.unofficial.data.api.DeviceParser
import com.orangefox.unofficial.data.api.FoxApiClient
import com.orangefox.unofficial.data.model.BridgeUptime
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

/**
 * Real connectivity checks, verified against the live infrastructure:
 *  - The REST API lives on the host ROOT (no /v3 prefix): /replies, /releases, /uptime...
 *  - Release files download through mirrors.DL -> /release/{id}/dl (a bare GET
 *    with a Range header returns HTTP 206 for 1 KiB — used to prove the
 *    download pipeline without pulling a whole 55 MB zip).
 *  - dl.orangefox.download is legacy: its root answers HTTP 502, so the app
 *    never probes it again.
 */
object OrangeEndpoints {
    val all = listOf(
        BridgeEndpoint("API Server", "Devices, releases & uptime (REST, no /v3 prefix)", "https://api.orangefox.download/releases?limit=1"),
        BridgeEndpoint("Main Website", "Device pages & web downloads", "https://orangefox.download/"),
        BridgeEndpoint("Wiki", "Installation guides & documentation", "https://wiki.orangefox.download/"),
        BridgeEndpoint("GitLab", "Source code & build pipelines", "https://gitlab.com/OrangeFox")
    )
}

data class BridgeState(
    val uptime: BridgeUptime? = null,
    val uptimeError: String? = null,
    val uptimeLatencyMs: Long? = null,
    val endpointHealth: Map<String, EndpointHealth> = emptyMap(),
    val downloadCheck: EndpointHealth? = null
)

class BridgeViewModel(application: Application) : AndroidViewModel(application) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(BridgeState())
    val state: StateFlow<BridgeState> = _state.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun checkAll() {
        if (_running.value) return
        viewModelScope.launch {
            _running.value = true
            _state.value = BridgeState()
            // +1 accounts for the download-pipeline probe.
            val pending = java.util.concurrent.atomic.AtomicInteger(OrangeEndpoints.all.size + 1)

            // 1) Official aggregated status (GET /uptime).
            withContext(Dispatchers.IO) {
                val startedAt = System.currentTimeMillis()
                try {
                    val app = getApplication<FoxApp>()
                    val raw = app.apiService.getUptime().use { it.string() }
                    val parsed = DeviceParser.parseUptime(raw)
                    _state.value = _state.value.copy(
                        uptime = parsed,
                        uptimeLatencyMs = System.currentTimeMillis() - startedAt,
                        uptimeError = if (parsed == null) "Could not parse the /uptime response" else null
                    )
                } catch (e: Exception) {
                    _state.value = _state.value.copy(
                        uptimeError = e.message ?: "unreachable"
                    )
                }
            }

            // 2) Probe the reachable endpoints in parallel.
            OrangeEndpoints.all.forEach { endpoint ->
                viewModelScope.launch {
                    val result = withContext(Dispatchers.IO) { probe(endpoint) }
                    _state.value = _state.value.copy(
                        endpointHealth = _state.value.endpointHealth + (endpoint.name to result)
                    )
                    if (pending.decrementAndGet() == 0) _running.value = false
                }
            }

            // 3) Prove the download pipeline: resolve one real release mirror
            //    and pull 1 KiB of it (HTTP 206 = download server works).
            viewModelScope.launch {
                val result = withContext(Dispatchers.IO) { probeDownloadPipeline() }
                _state.value = _state.value.copy(downloadCheck = result)
                if (pending.decrementAndGet() == 0) _running.value = false
            }
        }
    }

    private fun probe(endpoint: BridgeEndpoint): EndpointHealth {
        val startedAt = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url(endpoint.url)
                .header("User-Agent", FoxApiClient.USER_AGENT)
                .header("Accept", "application/json, text/html;q=0.8, */*;q=0.5")
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

    private fun probeDownloadPipeline(): EndpointHealth {
        val endpoint = BridgeEndpoint(
            "Download pipeline",
            "1 KiB range fetch of a real release mirror (mirrors.DL)",
            "https://api.orangefox.download/release/{id}/dl"
        )
        val startedAt = System.currentTimeMillis()
        return try {
            // Resolve the newest release mirror first.
            val listRequest = Request.Builder()
                .url("https://api.orangefox.download/releases?limit=1")
                .header("User-Agent", FoxApiClient.USER_AGENT)
                .build()
            val mirror = client.newCall(listRequest).execute().use { response ->
                if (!response.isSuccessful) return EndpointHealth(
                    endpoint, false, System.currentTimeMillis() - startedAt, response.code, "mirror lookup HTTP ${response.code}"
                )
                val body = response.body?.string().orEmpty()
                Regex("\"mirrors\"\\s*:\\s*\\{\\s*\"DL\"\\s*:\\s*\"([^\"]+)\"")
                    .find(body)?.groupValues?.get(1)
                    ?: return EndpointHealth(
                        endpoint, false, System.currentTimeMillis() - startedAt, response.code, "no DL mirror in response"
                    )
            }
            val downloadRequest = Request.Builder()
                .url(mirror)
                .header("User-Agent", FoxApiClient.USER_AGENT)
                .header("Range", "bytes=0-1023")
                .build()
            client.newCall(downloadRequest).execute().use { response ->
                val ok = response.code == 206 || response.code == 200
                EndpointHealth(
                    endpoint = endpoint,
                    online = ok,
                    latencyMs = System.currentTimeMillis() - startedAt,
                    httpCode = response.code,
                    error = if (ok) null else "HTTP ${response.code}"
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
    val state by vm.state.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.checkAll() }

    val downloadCheck = state.downloadCheck
    val probes = OrangeEndpoints.all + listOfNotNull(downloadCheck?.endpoint)
    val healthMap = remember(state) {
        state.endpointHealth + (downloadCheck?.let { it.endpoint.name to it }?.let { mapOf(it) } ?: emptyMap())
    }
    val onlineCount = probes.count { healthMap[it.name]?.online == true }

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
                        val uptime = state.uptime
                        Text(
                            when {
                                running && uptime == null -> "Asking the official /uptime endpoint…"
                                uptime != null -> buildString {
                                    append("Official status: ${uptime.status ?: "unknown"}")
                                    append(uptime.role?.let { " ($it)" } ?: "")
                                    append(" — ${uptime.hosts.count { it.isOk == true }}/${uptime.hosts.size} hosts up")
                                    state.uptimeLatencyMs?.let { append(" · ${it} ms") }
                                }
                                else -> "Official status unavailable: ${state.uptimeError ?: "unknown error"}"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "$onlineCount / ${probes.size} app checks online",
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

            // Official infrastructure hosts reported by the API itself.
            if (state.uptime != null) {
                item { SectionLabel("Official infrastructure (GET /uptime)") }
                items(state.uptime!!.hosts, key = { "host_${it.nickname}" }) { host ->
                    HostCard(host)
                }
            }

            // App-side probes.
            item { SectionLabel("Connectivity checks from this device") }
            items(probes, key = { it.name }) { endpoint ->
                EndpointCard(endpoint, healthMap[endpoint.name])
            }

            item { RequirementsCard() }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun HostCard(host: com.orangefox.unofficial.data.model.BridgeHost) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when {
                    host.isOk == true -> Icons.Rounded.CloudDone
                    host.isOk == false && host.isOptional != true -> Icons.Rounded.CloudOff
                    host.isOk == false -> Icons.Rounded.CloudOff
                    else -> Icons.Rounded.HourglassTop
                },
                contentDescription = null,
                tint = when {
                    host.isOk == true -> MaterialTheme.colorScheme.primary
                    host.isOk == false && host.isOptional == true -> MaterialTheme.colorScheme.tertiary
                    host.isOk == false -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(host.nickname, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                host.errorText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (host.isOptional == true) {
                AssistChip(onClick = {}, label = { Text("optional") })
            }
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
            Requirement("HTTPS connectivity to api.orangefox.download (REST + release downloads), orangefox.download, wiki.orangefox.download and gitlab.com/OrangeFox")
            Requirement("INTERNET and ACCESS_NETWORK_STATE permissions in the manifest")
            Requirement("An HTTP client with timeouts, a clear User-Agent and graceful failure handling (no API key needed — the OrangeFox API is public)")
            Requirement("The official /uptime endpoint for authoritative infrastructure status instead of guessing from root URLs")
            Requirement("Tolerant JSON parsing so schema changes on the server never crash the app")
            Requirement("A local Room cache plus a bundled offline catalog, so the app still works when a server is down")
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
