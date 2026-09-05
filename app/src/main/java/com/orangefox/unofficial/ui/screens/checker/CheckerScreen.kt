package com.orangefox.unofficial.ui.screens.checker

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orangefox.unofficial.util.CheckResult
import com.orangefox.unofficial.util.CheckStatus
import com.orangefox.unofficial.util.RecoveryDetector
import com.orangefox.unofficial.util.Verdict
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.platform.LocalContext
import android.app.Application
import kotlinx.coroutines.Dispatchers

class CheckerViewModel(application: Application) : AndroidViewModel(application) {

    private val _items = MutableStateFlow<List<CheckResult>>(emptyList())
    val items: StateFlow<List<CheckResult>> = _items.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _verdict = MutableStateFlow<Verdict?>(null)
    val verdict: StateFlow<Verdict?> = _verdict.asStateFlow()

    fun runChecks() {
        if (_running.value) return
        viewModelScope.launch {
            _running.value = true
            _items.value = emptyList()
            _verdict.value = null
            val results = mutableListOf<CheckResult>()
            for (check in RecoveryDetector.checks(getApplication())) {
                results += withContext(Dispatchers.IO) { check() }
                _items.value = results.toList()
                delay(280) // pacing so results appear one by one
            }
            _verdict.value = RecoveryDetector.verdictOf(results)
            _running.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckerScreen() {
    val vm: CheckerViewModel = viewModel()
    val items by vm.items.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val verdict by vm.verdict.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Recovery Checker") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.HealthAndSafety,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            "Scan this phone locally for signs of an unlocked bootloader, root access " +
                                "and a custom recovery such as TWRP or OrangeFox. Nothing is uploaded — " +
                                "every check runs on the device.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (!running && verdict == null) {
                item {
                    Button(
                        onClick = { vm.runChecks() },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("Start scan")
                    }
                }
            }

            if (running) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Text("Scanning device…", style = MaterialTheme.typography.titleMedium)
                            }
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            items(items, key = { it.id }) { result ->
                CheckCard(result)
            }

            verdict?.let { v ->
                item { VerdictCard(v) }
                item { TipsCard() }
                item {
                    Button(
                        onClick = { vm.runChecks() },
                        enabled = !running,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scan again")
                    }
                }
            }

            item {
                Text(
                    "Note: this scan is best-effort. Some vendors hide the verified-boot state and " +
                        "reading the recovery image requires root. If the result is inconclusive, " +
                        "boot into recovery manually to verify.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CheckCard(result: CheckResult) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, tint) = when (result.status) {
                CheckStatus.DETECTED -> Icons.Rounded.CheckCircle to MaterialTheme.colorScheme.primary
                CheckStatus.NOT_FOUND -> Icons.Rounded.Remove to MaterialTheme.colorScheme.onSurfaceVariant
                CheckStatus.UNKNOWN -> Icons.AutoMirrored.Rounded.Help to MaterialTheme.colorScheme.tertiary
            }
            Icon(icon, contentDescription = result.status.name, tint = tint, modifier = Modifier.size(24.dp))
            Column {
                Text(result.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    result.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VerdictCard(verdict: Verdict) {
    val (container, content, icon) = when (verdict) {
        Verdict.CUSTOM_LIKELY -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Rounded.CheckCircle
        )
        Verdict.STOCK_LIKELY -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Rounded.Smartphone
        )
        Verdict.UNCERTAIN -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.AutoMirrored.Rounded.Help
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(40.dp))
            Column {
                Text(verdict.title, style = MaterialTheme.typography.titleLarge, color = content)
                Spacer(Modifier.height(4.dp))
                Text(verdict.description, style = MaterialTheme.typography.bodyMedium, color = content)
            }
        }
    }
}

@Composable
private fun TipsCard() {
    var expanded by remember { mutableStateOf(false) }
    OutlinedCard(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Rounded.Help,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "How do I boot into recovery?",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null
                )
            }
            AnimatedVisibility(expanded) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "• Power off, then hold Power + Volume Down until the recovery menu appears " +
                            "(the combo varies between vendors).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "• Or from a PC with ADB: adb reboot recovery",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "• If OrangeFox is installed you will be greeted by its orange splash menu.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
