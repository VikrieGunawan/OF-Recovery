package com.orangefox.unofficial.ui.screens.home

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
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
import com.orangefox.unofficial.ui.components.SectionTitle
import com.orangefox.unofficial.util.DownloadHelper
import com.orangefox.unofficial.util.openInBrowser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PhoneMatch {
    data object Checking : PhoneMatch
    data object NotFound : PhoneMatch
    data class Found(val device: Device) : PhoneMatch
}

class HomeViewModel(private val repo: FoxRepository) : ViewModel() {

    private val _match = MutableStateFlow<PhoneMatch>(PhoneMatch.Checking)
    val match: StateFlow<PhoneMatch> = _match.asStateFlow()

    private val _latest = MutableStateFlow<List<FoxBuild>>(emptyList())
    val latest: StateFlow<List<FoxBuild>> = _latest.asStateFlow()

    private val _latestNote = MutableStateFlow<String?>(null)
    val latestNote: StateFlow<String?> = _latestNote.asStateFlow()

    private val _loadingLatest = MutableStateFlow(false)
    val loadingLatest: StateFlow<Boolean> = _loadingLatest.asStateFlow()

    init {
        viewModelScope.launch { repo.seedOfflineIfEmpty() }
        loadLatest()
        resolvePhone()
    }

    fun loadLatest() {
        if (_loadingLatest.value) return
        viewModelScope.launch {
            _loadingLatest.value = true
            _latest.value = repo.latestReleases(limit = 8)
            _latestNote.value =
                if (_latest.value.isEmpty()) "The bridge returned no releases right now — the app keeps working from its cache." else null
            _loadingLatest.value = false
        }
    }

    private fun resolvePhone() {
        viewModelScope.launch {
            _match.value = repo.matchThisPhone()
                ?.let { PhoneMatch.Found(it) }
                ?: PhoneMatch.NotFound
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FoxApp
                HomeViewModel(app.repository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDevices: () -> Unit,
    onOpenChecker: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenBridge: () -> Unit,
    onOpenSettings: () -> Unit,
    onDeviceClick: (String) -> Unit
) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val match by vm.match.collectAsStateWithLifecycle()
    val latest by vm.latest.collectAsStateWithLifecycle()
    val latestNote by vm.latestNote.collectAsStateWithLifecycle()
    val loadingLatest by vm.loadingLatest.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as FoxApp
    val devices by app.repository.cachedDevices.collectAsStateWithLifecycle(initialValue = emptyList())

    // Custom collapsing header: the fraction drives every part of the animation
    // continuously (no pinned title, no abrupt jump to the left edge).
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val density = LocalDensity.current
    LaunchedEffect(density) {
        val expanded = with(density) { 148.dp.toPx() }
        val collapsed = with(density) { 64.dp.toPx() }
        scrollBehavior.state.heightOffsetLimit = -(expanded - collapsed)
    }
    val fraction = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FoxHomeHeader(
                fraction = fraction,
                onOpenBridge = onOpenBridge,
                onOpenSettings = onOpenSettings
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { HeroCard() }

            item { YourDeviceCard(match, onOpenDevices, onDeviceClick) }

            item {
                QuickActionsGrid(
                    onChecker = onOpenChecker,
                    onDevices = onOpenDevices,
                    onDownloads = onOpenDownloads,
                    onBridge = onOpenBridge
                )
            }

            item { SectionTitle("Latest from the bridge") }

            if (loadingLatest && latest.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            "Fetching the newest OrangeFox releases…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            latestNote?.let { note ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            note,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(latest, key = { it.fileUrl ?: it.displayName }) { build ->
                BuildCard(
                    build = build,
                    onDownload = {
                        val context = app
                        val url = build.fileUrl ?: return@BuildCard
                        val name = build.displayName.ifBlank {
                            "orangefox-${System.currentTimeMillis()}.zip"
                        }
                        runCatching {
                            DownloadHelper.enqueue(context, url, name)
                            Toast.makeText(
                                context,
                                "Download started — see the Downloads tab",
                                Toast.LENGTH_SHORT
                            ).show()
                        }.onFailure {
                            Toast.makeText(context, "Could not start the download", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpen = { build.fileUrl?.let { openInBrowser(app, it) } }
                )
            }

            if (devices.isNotEmpty()) {
                item { SectionTitle("Featured devices") }
                item { FeaturedCarousel(devices, onDeviceClick) }
            }

            item { LinksCard() }
            item { DisclaimerCard() }
        }
    }
}

/**
 * Home-grown collapsing header. The large title slides up and fades out while
 * the compact title fades in — one continuous motion driven by the scroll
 * fraction, so nothing ever "teleports" to the left edge.
 */
@Composable
private fun FoxHomeHeader(
    fraction: Float,
    onOpenBridge: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val bg = MaterialTheme.colorScheme.surface
    val hairline = MaterialTheme.colorScheme.outlineVariant
    val largeAlpha = (1f - fraction * 1.5f).coerceIn(0f, 1f)
    val smallAlpha = (fraction * 1.7f - 0.4f).coerceIn(0f, 1f)
    val density = LocalDensity.current
    val hairlinePx = with(density) { 1.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .drawBehind {
                if (fraction > 0.08f) {
                    drawLine(
                        color = hairline,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = hairlinePx
                    )
                }
            }
            .statusBarsPadding()
            .height(lerp(148.dp, 64.dp, fraction))
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "OF Recovery",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.graphicsLayer { alpha = smallAlpha }
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenBridge) {
                Icon(Icons.Rounded.NetworkCheck, contentDescription = "Bridge Health")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings")
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.BottomStart
        ) {
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = largeAlpha
                        translationY = -fraction * with(density) { 40.dp.toPx() }
                    }
                    .padding(bottom = 14.dp)
            ) {
                Text(
                    "OF Recovery",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Unofficial OrangeFox Recovery companion",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HeroCard() {
    val transition = rememberInfiniteTransition(label = "hero")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(1600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "hero_scale"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(20.dp)
            ) {
                Text(
                    "OrangeFox Recovery",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Browse supported devices, download builds, check your recovery and watch the bridge — all live from the official servers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                AssistChip(onClick = {}, label = { Text("UNOFFICIAL APP") })
            }
            painterResource(R.drawable.ic_launcher_foreground).let { fox ->
                Icon(
                    painter = fox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 24.dp)
                        .size(92.dp)
                        .scale(scale)
                )
            }
        }
    }
}

@Composable
private fun YourDeviceCard(
    match: PhoneMatch,
    onOpenDevices: () -> Unit,
    onDeviceClick: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        when (match) {
            is PhoneMatch.Checking -> Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Identifying your phone…", style = MaterialTheme.typography.bodyMedium)
            }

            is PhoneMatch.Found -> Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "This phone is supported: ${match.device.name}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "${match.device.oem} · ${match.device.codename} — tap to see its OrangeFox releases",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.TextButton(onClick = { onDeviceClick(match.device.codename) }) {
                    Text("Open")
                }
            }

            is PhoneMatch.NotFound -> Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Your phone wasn't matched automatically",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Search the full catalog by codename — there may still be a build for it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.TextButton(onClick = onOpenDevices) {
                    Text("Search")
                }
            }
        }
    }
}

@Composable
private fun QuickActionsGrid(
    onChecker: () -> Unit,
    onDevices: () -> Unit,
    onDownloads: () -> Unit,
    onBridge: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionCard(Icons.Rounded.HealthAndSafety, "Recovery Checker", "Is a custom recovery installed?", Modifier.weight(1f), onChecker)
            ActionCard(Icons.Rounded.Smartphone, "Devices", "Browse supported phones", Modifier.weight(1f), onDevices)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionCard(Icons.Rounded.Download, "Downloads", "Track your downloads", Modifier.weight(1f), onDownloads)
            ActionCard(Icons.Rounded.NetworkCheck, "Bridge Health", "Official server status", Modifier.weight(1f), onBridge)
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeaturedCarousel(devices: List<Device>, onDeviceClick: (String) -> Unit) {
    val featured = devices.take(12)
    val state = rememberCarouselState { featured.size }
    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = 220.dp,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) { index ->
        val device = featured[index]
        Card(
            onClick = { onDeviceClick(device.codename) },
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AsyncImage(
                    model = device.imageUrl,
                    contentDescription = device.name,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    placeholder = painterResource(R.drawable.img_placeholder),
                    error = painterResource(R.drawable.img_placeholder),
                    fallback = painterResource(R.drawable.img_placeholder)
                )
                Text(
                    device.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${device.oem} · ${device.codename}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LinksCard() {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Official resources", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { openInBrowser(context, "https://orangefox.download/") },
                    label = { Text("Website") },
                    leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = { openInBrowser(context, "https://wiki.orangefox.download/") },
                    label = { Text("Wiki") },
                    leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = { openInBrowser(context, "https://gitlab.com/OrangeFox") },
                    label = { Text("GitLab") },
                    leadingIcon = { Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }
    }
}

@Composable
private fun DisclaimerCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                "OF Recovery is an unofficial companion app. It is not affiliated with, " +
                    "nor endorsed by, the OrangeFox Recovery team. All trademarks belong to their owners.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
