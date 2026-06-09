package com.example.carvideo

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.example.carvideo.extractor.SearchResultItem
import com.example.carvideo.logging.CrashLogger
import com.example.carvideo.player.PlaybackService
import com.example.carvideo.update.AppUpdater
import com.example.carvideo.update.UpdateInfo
import com.example.carvideo.ui.CarVideoTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.logEvent(this, "MainActivity onCreate")
        enableEdgeToEdge()
        startService(Intent(this, PlaybackService::class.java))
        setContent {
            val vm: SearchViewModel = viewModel()
            val state by vm.state.collectAsState()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* handle result */ }
                LaunchedEffect(Unit) {
                    launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            CarVideoTheme(themeMode = state.themeMode) {
                val backgroundColor = MaterialTheme.colorScheme.background
                val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(backgroundColor, surfaceColor.copy(alpha = 0.5f))
                            )
                        )
                ) {
                    HomeScreen(vm)
                    val currentStream by com.example.carvideo.player.PlaybackState.current.collectAsState()
                    if (currentStream != null) {
                        Text(
                            "AA Status: Service Active. Check 'Unknown Sources' in AA settings!",
                            color = Color.Green.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.TopStart).padding(top = 120.dp, start = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: SearchViewModel) {
    val state by vm.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var checkingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var copiedLogMessage by remember { mutableStateOf<String?>(null) }

    if (showLogs) {
        CrashLogDialog(
            logText = CrashLogger.readFullLog(context),
            onCopy = {
                copiedLogMessage = if (CrashLogger.copyLogsToClipboard(context)) {
                    "Log gekopieerd. Plak hem hier in ChatGPT."
                } else {
                    "Kopiëren mislukt."
                }
            },
            onClear = {
                CrashLogger.clearLogs(context)
                copiedLogMessage = "Logs gewist."
            },
            onDismiss = { showLogs = false }
        )
    }

    if (showSettings) {
        SettingsDialog(
            currentTheme = state.themeMode,
            checkingUpdate = checkingUpdate,
            updateInfo = updateInfo,
            updateMessage = updateMessage,
            logMessage = copiedLogMessage,
            onThemeChange = { vm.setThemeMode(it) },
            onCheckUpdate = {
                checkingUpdate = true
                updateMessage = null
                CrashLogger.logEvent(context, "Update check gestart")
                scope.launch {
                    try {
                        val info = AppUpdater.checkForUpdate()
                        updateInfo = info
                        updateMessage = if (info.isUpdateAvailable) {
                            "Nieuwe versie gevonden: ${info.releaseName}"
                        } else {
                            "Je hebt al de nieuwste versie."
                        }
                        CrashLogger.logEvent(context, "Update check OK: ${info.tagName}, update=${info.isUpdateAvailable}")
                    } catch (e: Exception) {
                        updateMessage = "Update check mislukt: ${e.message}"
                        CrashLogger.logEvent(context, "Update check fout: ${e.message}")
                    } finally {
                        checkingUpdate = false
                    }
                }
            },
            onDownloadUpdate = {
                val info = updateInfo ?: return@SettingsDialog
                try {
                    AppUpdater.downloadWithDownloadManager(context, info)
                    Toast.makeText(context, "Update wordt gedownload. Open daarna Installeren.", Toast.LENGTH_LONG).show()
                    updateMessage = "Download gestart. Wacht tot Android meldt dat de download klaar is en tik daarna op Installeren."
                    CrashLogger.logEvent(context, "Update download gestart: ${info.assetName}")
                } catch (e: Exception) {
                    updateMessage = "Download mislukt: ${e.message}"
                    CrashLogger.logEvent(context, "Update download fout: ${e.message}")
                }
            },
            onInstallUpdate = {
                try {
                    CrashLogger.logEvent(context, "Update installatie gestart")
                    AppUpdater.installDownloadedApk(context)
                } catch (e: Exception) {
                    updateMessage = "Installatie kon niet starten: ${e.message}"
                    CrashLogger.logEvent(context, "Update installatie fout: ${e.message}")
                }
            },
            onShowLogs = { showLogs = true },
            onCopyLogs = {
                copiedLogMessage = if (CrashLogger.copyLogsToClipboard(context)) {
                    "Log gekopieerd. Plak hem hier in ChatGPT."
                } else {
                    "Kopiëren mislukt."
                }
            },
            onClearLogs = {
                CrashLogger.clearLogs(context)
                copiedLogMessage = "Logs gewist."
            },
            onDismiss = { showSettings = false }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column(modifier = Modifier.background(Color.Transparent)) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Car Video",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("For You", modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("Trending", modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                        Text("Recent", modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }) {
                        Text("Search", modifier = Modifier.padding(12.dp))
                    }
                }
            }
        },
        bottomBar = {
            val nextItem = vm.getNextItem()
            val state by vm.state.collectAsState()
            val currentStream by com.example.carvideo.player.PlaybackState.current.collectAsState()
            val videoMode by vm.videoMode.collectAsState()
            
            com.example.carvideo.ui.NowPlayingBar(
                nextUpTitle = nextItem?.title,
                isLiked = currentStream?.let { vm.isLiked(it.originalUrl ?: "") } ?: false,
                isFailover = state.isFailover,
                onLikeClick = {
                    currentStream?.let { stream ->
                        vm.toggleLike(
                            com.example.carvideo.extractor.SearchResultItem(
                                title = stream.title,
                                url = stream.originalUrl ?: "",
                                uploader = stream.uploader,
                                thumbnailUrl = stream.thumbnailUrl,
                                durationSeconds = stream.durationSeconds
                            )
                        )
                    }
                },
                onNextClick = { vm.skipNext() },
                onPreviousClick = { vm.skipPrevious() },
                onSeek = { vm.seekTo(it) },
                videoMode = videoMode,
                onToggleVideo = { vm.toggleVideoMode() }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> ResultsList(state.forYou, vm) { vm.play(it, state.forYou) }
                1 -> ResultsList(state.trending, vm) { vm.play(it, state.trending) }
                2 -> ResultsList(state.history, vm) { vm.play(it, state.history) }
                3 -> SearchContent(query, { query = it }, { vm.search(query) }, state, vm)
            }
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SearchContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    state: UiState,
    vm: SearchViewModel
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        SearchBar(query, onQueryChange, onSearch)
        Spacer(Modifier.height(8.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.selectedService == 0,
                onClick = { vm.setService(0) },
                label = { Text("YouTube") }
            )
            FilterChip(
                selected = state.selectedService == 1,
                onClick = { vm.setService(1) },
                label = { Text("SoundCloud") }
            )
        }

        Spacer(Modifier.height(8.dp))

        if (state.loading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.error != null) {
            Text(state.error, color = MaterialTheme.colorScheme.error)
        } else {
            ResultsList(state.results, vm) { vm.play(it, state.results) }
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ResultsList(items: List<SearchResultItem>, vm: SearchViewModel, onItemClick: (SearchResultItem) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items) { item ->
            ResultCard(item, vm) { onItemClick(item) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("YouTube zoeken of URL") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                focusedBorderColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = onSearch,
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Icon(Icons.Default.Search, "Zoek")
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ResultCard(item: SearchResultItem, vm: SearchViewModel, onClick: () -> Unit) {
    val isLiked = vm.isLiked(item.url)
    val currentStream by com.example.carvideo.player.PlaybackState.current.collectAsState()
    val isPlayingThis = currentStream?.originalUrl == item.url
    
    val cornerSize by animateDpAsState(
        targetValue = if (isPlayingThis) 32.dp else if (isLiked) 28.dp else 24.dp,
        animationSpec = tween(500),
        label = "shape"
    )

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerSize),
        colors = CardDefaults.elevatedCardColors(
            containerColor = when {
                isPlayingThis -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                isLiked -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            }
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isPlayingThis) 8.dp else 2.dp)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 120.dp, height = 68.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                if (isLiked) {
                    Icon(
                        Icons.Default.Favorite,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    item.uploader ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Default.PlayArrow,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
fun SettingsDialog(
    currentTheme: Int,
    checkingUpdate: Boolean,
    updateInfo: UpdateInfo?,
    updateMessage: String?,
    logMessage: String?,
    onThemeChange: (Int) -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onShowLogs: () -> Unit,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Instellingen", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Thema Modus", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = currentTheme == 0, onClick = { onThemeChange(0) }, label = { Text("Systeem") })
                    FilterChip(selected = currentTheme == 1, onClick = { onThemeChange(1) }, label = { Text("Licht") })
                    FilterChip(selected = currentTheme == 2, onClick = { onThemeChange(2) }, label = { Text("Donker") })
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                Text("Updates", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Huidige versie: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (updateMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(updateMessage, style = MaterialTheme.typography.bodySmall)
                }
                if (updateInfo != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Laatste release: ${updateInfo.tagName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCheckUpdate, enabled = !checkingUpdate) {
                        if (checkingUpdate) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Check update")
                    }
                    Button(onClick = onDownloadUpdate, enabled = updateInfo?.isUpdateAvailable == true) {
                        Text("Download")
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onInstallUpdate) { Text("Installeren na download") }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                Text("Crash log", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Als de app crasht, open hier de log en kopieer die naar ChatGPT.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (logMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(logMessage, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onShowLogs) { Text("Bekijk log") }
                    Button(onClick = onCopyLogs) { Text("Kopieer log") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onClearLogs) { Text("Wis logs") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Sluiten") }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun CrashLogDialog(
    logText: String,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crash log") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(logText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onCopy) { Text("Kopieer") } },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Wis") }
                TextButton(onClick = onDismiss) { Text("Sluiten") }
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}
