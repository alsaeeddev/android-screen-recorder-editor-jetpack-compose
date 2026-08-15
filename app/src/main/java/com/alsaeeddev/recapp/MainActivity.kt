package com.alsaeeddev.recapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.alsaeeddev.recapp.data.model.MediaType
import com.alsaeeddev.recapp.data.model.RecordItem
import com.alsaeeddev.recapp.data.model.RecordingRegionOption
import com.alsaeeddev.recapp.data.model.RecordingState
import com.alsaeeddev.recapp.service.FloatingOverlayService
import com.alsaeeddev.recapp.service.ScreenRecordService
import com.alsaeeddev.recapp.service.SelectiveAreaOverlayService
import com.alsaeeddev.recapp.ui.MainViewModel
import com.alsaeeddev.recapp.ui.components.ScreenshotPreviewDialog
import com.alsaeeddev.recapp.ui.components.SelectiveAreaDialog
import com.alsaeeddev.recapp.ui.components.VideoPlayerDialog
import com.alsaeeddev.recapp.ui.screens.HomeScreen
import com.alsaeeddev.recapp.ui.screens.LibraryScreen
import com.alsaeeddev.recapp.ui.screens.SettingsScreen
import com.alsaeeddev.recapp.ui.theme.BentoCardSurface
import com.alsaeeddev.recapp.ui.theme.BentoPrimary
import com.alsaeeddev.recapp.ui.theme.ScreenRecorderTheme
import com.alsaeeddev.recapp.util.NotificationHelper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.net.toUri

enum class NavItem(val label: String, val icon: ImageVector) {
    HOME("Recorder", Icons.Default.Videocam),
    LIBRARY("Library", Icons.Default.Folder),
    SETTINGS("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var selectedCropX = 0
    private var selectedCropY = 0
    private var selectedCropW = 0
    private var selectedCropH = 0

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenRecordService::class.java).apply {
                action = NotificationHelper.ACTION_START
                putExtra(ScreenRecordService.Companion.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenRecordService.Companion.EXTRA_DATA, result.data)
                putExtra(ScreenRecordService.Companion.EXTRA_CROP_X, selectedCropX)
                putExtra(ScreenRecordService.Companion.EXTRA_CROP_Y, selectedCropY)
                putExtra(ScreenRecordService.Companion.EXTRA_CROP_W, selectedCropW)
                putExtra(ScreenRecordService.Companion.EXTRA_CROP_H, selectedCropH)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchMediaProjectionPrompt()
        } else {
            Toast.makeText(this, "Microphone permission required for audio", Toast.LENGTH_SHORT)
                .show()
            launchMediaProjectionPrompt()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onResume() {
        super.onResume()
        viewModel.validateLibraryFiles()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCropRecordingIntent(intent)
    }

    private fun handleCropRecordingIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(
                SelectiveAreaOverlayService.Companion.EXTRA_START_CROP_RECORDING,
                false
            ) == true
        ) {
            selectedCropX = intent.getIntExtra(ScreenRecordService.Companion.EXTRA_CROP_X, 0)
            selectedCropY = intent.getIntExtra(ScreenRecordService.Companion.EXTRA_CROP_Y, 0)
            selectedCropW = intent.getIntExtra(ScreenRecordService.Companion.EXTRA_CROP_W, 0)
            selectedCropH = intent.getIntExtra(ScreenRecordService.Companion.EXTRA_CROP_H, 0)
            intent.removeExtra(SelectiveAreaOverlayService.Companion.EXTRA_START_CROP_RECORDING)
            requestRecordingFlow()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkRequiredPermissions()
        handleCropRecordingIntent(intent)

        setContent {
            val settings by viewModel.recordingSettings.collectAsState()
            val recState by viewModel.recordingState.collectAsState()

            val videoList by viewModel.videoRecords.collectAsState()
            val screenshotList by viewModel.screenshotRecords.collectAsState()
            val favoriteList by viewModel.favoriteRecords.collectAsState()
            val recycledList by viewModel.recycledRecords.collectAsState()
            val activeList by viewModel.allActiveRecords.collectAsState()

            var selectedNav by remember { mutableStateOf(NavItem.HOME) }
            var selectedItemForPreview by remember { mutableStateOf<RecordItem?>(null) }
            var showSelectiveAreaDialog by remember { mutableStateOf(false) }

            // Sync floating bubble overlay service with settings
            LaunchedEffect(settings.showFloatingBubble) {
                if (settings.showFloatingBubble) {
                    if (Settings.canDrawOverlays(
                            this@MainActivity
                        )
                    ) {
                        startService(Intent(this@MainActivity, FloatingOverlayService::class.java))
                    }
                } else {
                    stopService(Intent(this@MainActivity, FloatingOverlayService::class.java))
                }
            }

            ScreenRecorderTheme(darkTheme = settings.isDarkMode) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        BentoBottomNavigationBar(
                            selectedItem = selectedNav,
                            onSelect = { selectedNav = it }
                        )
                    }
                ) { innerPadding ->
                      Box(
                           modifier = Modifier
                               .fillMaxSize()
                               .padding(innerPadding)
                               .background(MaterialTheme.colorScheme.background)
                       ) {
                           AnimatedContent(
                               targetState = selectedNav,
                               transitionSpec = {
                                   val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                   (slideInHorizontally(animationSpec = tween(250)) { width -> direction * width } + fadeIn(animationSpec = tween(250))) togetherWith
                                           (slideOutHorizontally(animationSpec = tween(250)) { width -> -direction * width } + fadeOut(animationSpec = tween(250)))
                               },
                               label = "nav_transition"
                           ) { targetNav ->
                               when (targetNav) {
                                   NavItem.HOME -> HomeScreen(
                                       recordingState = recState,
                                       settings = settings,
                                       recentRecords = activeList,
                                       onStartRecording = {
                                           if (settings.recordingRegion == RecordingRegionOption.CUSTOM_AREA) {
                                               if (Settings.canDrawOverlays(
                                                       this@MainActivity
                                                   )
                                               ) {
                                                   try {
                                                       val serviceIntent = Intent(
                                                           this@MainActivity,
                                                           SelectiveAreaOverlayService::class.java
                                                       )
                                                       startService(serviceIntent)
                                                       moveTaskToBack(true)
                                                   } catch (e: Exception) {
                                                       Log.e(
                                                           "MainActivity",
                                                           "Failed starting overlay service, using fallback dialog",
                                                           e
                                                       )
                                                       showSelectiveAreaDialog = true
                                                   }
                                               } else {
                                                   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                       Toast.makeText(
                                                           this@MainActivity,
                                                           "Grant Overlay permission to select area over phone screen",
                                                           Toast.LENGTH_SHORT
                                                       ).show()
                                                       val overlayIntent = Intent(
                                                           Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                           "package:$packageName".toUri()
                                                       )
                                                       startActivity(overlayIntent)
                                                   }
                                                   showSelectiveAreaDialog = true
                                               }
                                           } else {
                                               selectedCropX = 0
                                               selectedCropY = 0
                                               selectedCropW = 0
                                               selectedCropH = 0
                                               requestRecordingFlow()
                                           }
                                       },
                                       onPauseRecording = {
                                           val intent = Intent(
                                               this@MainActivity,
                                               ScreenRecordService::class.java
                                           ).apply {
                                               action = NotificationHelper.ACTION_PAUSE
                                           }
                                           startService(intent)
                                       },
                                       onResumeRecording = {
                                           val intent = Intent(
                                               this@MainActivity,
                                               ScreenRecordService::class.java
                                           ).apply {
                                               action = NotificationHelper.ACTION_RESUME
                                           }
                                           startService(intent)
                                       },
                                       onStopRecording = {
                                           val intent = Intent(
                                               this@MainActivity,
                                               ScreenRecordService::class.java
                                           ).apply {
                                               action = NotificationHelper.ACTION_STOP
                                           }
                                           startService(intent)
                                       },
                                       onToggleFloatingBubble = { enabled ->
                                           if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(
                                                   this@MainActivity
                                               )
                                           ) {
                                               val overlayIntent = Intent(
                                                   Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                   Uri.parse("package:$packageName")
                                               )
                                               startActivity(overlayIntent)
                                           }
                                           viewModel.updateSettings(settings.copy(showFloatingBubble = enabled))
                                       },
                                       onOpenSettings = { selectedNav = NavItem.SETTINGS },
                                       onSelectRecordItem = { item -> selectedItemForPreview = item },
                                       onTakeScreenshot = {
                                           if (recState is RecordingState.Recording) {
                                               val intent = Intent(
                                                   this@MainActivity,
                                                   ScreenRecordService::class.java
                                               ).apply {
                                                   action = NotificationHelper.ACTION_SCREENSHOT
                                               }
                                               startService(intent)
                                           } else {
                                               Toast.makeText(
                                                   this@MainActivity,
                                                   "Start recording to capture screenshot frame",
                                                   Toast.LENGTH_SHORT
                                               ).show()
                                           }
                                       }
                                   )

                                   NavItem.LIBRARY -> LibraryScreen(
                                       videoItems = videoList,
                                       screenshotItems = screenshotList,
                                       favoriteItems = favoriteList,
                                       recycledItems = recycledList,
                                       onSelectItem = { item -> selectedItemForPreview = item },
                                       onToggleFavorite = { item -> viewModel.toggleFavorite(item) },
                                       onMoveToRecycleBin = { item -> viewModel.moveToRecycleBin(item) },
                                       onRestoreItem = { item -> viewModel.restoreItem(item) },
                                       onDeletePermanently = { item -> viewModel.deletePermanently(item) },
                                       onEmptyRecycleBin = { viewModel.emptyRecycleBin() },
                                       onRenameItem = { item, title -> viewModel.renameItem(item, title) }
                                   )

                                   NavItem.SETTINGS -> SettingsScreen(
                                       settings = settings,
                                       onUpdateSettings = { updated -> viewModel.updateSettings(updated) }
                                   )
                               }
                           }

                           // Selective Area Dialog
                           if (showSelectiveAreaDialog) {
                               SelectiveAreaDialog(
                                   onDismiss = { showSelectiveAreaDialog = false },
                                   onConfirmRecording = { w, h, x, y ->
                                       selectedCropW = w
                                       selectedCropH = h
                                       selectedCropX = x
                                       selectedCropY = y
                                       showSelectiveAreaDialog = false
                                       requestRecordingFlow()
                                   }
                               )
                           }

                           // Preview Dialogs
                           selectedItemForPreview?.let { item ->
                               if (item.mediaType == MediaType.VIDEO) {
                                   VideoPlayerDialog(
                                       item = item,
                                       onDismiss = { selectedItemForPreview = null },
                                       onDelete = { delItem -> viewModel.moveToRecycleBin(delItem) },
                                       onRename = { renItem, title ->
                                           viewModel.renameItem(
                                               renItem,
                                               title
                                           )
                                       },
                                       onSaveEditedVideo = { newItem -> viewModel.saveRecord(newItem) }
                                   )
                               } else {
                                   ScreenshotPreviewDialog(
                                       item = item,
                                       onDismiss = { selectedItemForPreview = null },
                                       onFavoriteToggle = { favItem -> viewModel.toggleFavorite(favItem) },
                                       onDelete = { delItem -> viewModel.moveToRecycleBin(delItem) }
                                   )
                               }
                           }
                       }

                    // Without animated transition
                 /*   Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        when (selectedNav) {
                            NavItem.HOME -> HomeScreen(
                                recordingState = recState,
                                settings = settings,
                                recentRecords = activeList,
                                onStartRecording = {
                                    if (settings.recordingRegion == RecordingRegionOption.CUSTOM_AREA) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(
                                                this@MainActivity
                                            )
                                        ) {
                                            try {
                                                val serviceIntent = Intent(
                                                    this@MainActivity,
                                                    SelectiveAreaOverlayService::class.java
                                                )
                                                startService(serviceIntent)
                                                moveTaskToBack(true)
                                            } catch (e: Exception) {
                                                Log.e(
                                                    "MainActivity",
                                                    "Failed starting overlay service, using fallback dialog",
                                                    e
                                                )
                                                showSelectiveAreaDialog = true
                                            }
                                        } else {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Grant Overlay permission to select area over phone screen",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                val overlayIntent = Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:$packageName")
                                                )
                                                startActivity(overlayIntent)
                                            }
                                            showSelectiveAreaDialog = true
                                        }
                                    } else {
                                        selectedCropX = 0
                                        selectedCropY = 0
                                        selectedCropW = 0
                                        selectedCropH = 0
                                        requestRecordingFlow()
                                    }
                                },
                                onPauseRecording = {
                                    val intent = Intent(
                                        this@MainActivity,
                                        ScreenRecordService::class.java
                                    ).apply {
                                        action = NotificationHelper.ACTION_PAUSE
                                    }
                                    startService(intent)
                                },
                                onResumeRecording = {
                                    val intent = Intent(
                                        this@MainActivity,
                                        ScreenRecordService::class.java
                                    ).apply {
                                        action = NotificationHelper.ACTION_RESUME
                                    }
                                    startService(intent)
                                },
                                onStopRecording = {
                                    val intent = Intent(
                                        this@MainActivity,
                                        ScreenRecordService::class.java
                                    ).apply {
                                        action = NotificationHelper.ACTION_STOP
                                    }
                                    startService(intent)
                                },
                                onToggleFloatingBubble = { enabled ->
                                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(
                                            this@MainActivity
                                        )
                                    ) {
                                        val overlayIntent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            "package:$packageName".toUri()
                                        )
                                        startActivity(overlayIntent)
                                    }
                                    viewModel.updateSettings(settings.copy(showFloatingBubble = enabled))
                                },
                                onOpenSettings = { selectedNav = NavItem.SETTINGS },
                                onSelectRecordItem = { item -> selectedItemForPreview = item },
                                onTakeScreenshot = {
                                    if (recState is RecordingState.Recording) {
                                        val intent = Intent(
                                            this@MainActivity,
                                            ScreenRecordService::class.java
                                        ).apply {
                                            action = NotificationHelper.ACTION_SCREENSHOT
                                        }
                                        startService(intent)
                                    } else {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Start recording to capture screenshot frame",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )

                            NavItem.LIBRARY -> LibraryScreen(
                                videoItems = videoList,
                                screenshotItems = screenshotList,
                                favoriteItems = favoriteList,
                                recycledItems = recycledList,
                                onSelectItem = { item -> selectedItemForPreview = item },
                                onToggleFavorite = { item -> viewModel.toggleFavorite(item) },
                                onMoveToRecycleBin = { item -> viewModel.moveToRecycleBin(item) },
                                onRestoreItem = { item -> viewModel.restoreItem(item) },
                                onDeletePermanently = { item -> viewModel.deletePermanently(item) },
                                onEmptyRecycleBin = { viewModel.emptyRecycleBin() },
                                onRenameItem = { item, title -> viewModel.renameItem(item, title) }
                            )

                            NavItem.SETTINGS -> SettingsScreen(
                                settings = settings,
                                onUpdateSettings = { updated -> viewModel.updateSettings(updated) }
                            )
                        }

                        // Selective Area Dialog
                        if (showSelectiveAreaDialog) {
                            SelectiveAreaDialog(
                                onDismiss = { showSelectiveAreaDialog = false },
                                onConfirmRecording = { w, h, x, y ->
                                    selectedCropW = w
                                    selectedCropH = h
                                    selectedCropX = x
                                    selectedCropY = y
                                    showSelectiveAreaDialog = false
                                    requestRecordingFlow()
                                }
                            )
                        }

                        // Preview Dialogs
                        selectedItemForPreview?.let { item ->
                            if (item.mediaType == MediaType.VIDEO) {
                                VideoPlayerDialog(
                                    item = item,
                                    onDismiss = { selectedItemForPreview = null },
                                    onDelete = { delItem -> viewModel.moveToRecycleBin(delItem) },
                                    onRename = { renItem, title ->
                                        viewModel.renameItem(
                                            renItem,
                                            title
                                        )
                                    },
                                    onSaveEditedVideo = { newItem -> viewModel.saveRecord(newItem) }
                                )
                            } else {
                                ScreenshotPreviewDialog(
                                    item = item,
                                    onDismiss = { selectedItemForPreview = null },
                                    onFavoriteToggle = { favItem -> viewModel.toggleFavorite(favItem) },
                                    onDelete = { delItem -> viewModel.moveToRecycleBin(delItem) }
                                )
                            }
                        }
                    }*/
                }
            }
        }
    }

    private fun checkRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestRecordingFlow() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            launchMediaProjectionPrompt()
        }
    }

    private fun launchMediaProjectionPrompt() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}


/*

@Composable
fun BentoBottomNavigationBar(
    selectedItem: NavItem,
    onSelect: (NavItem) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BentoCardSurface,
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(68.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem.values().forEach { nav ->
                val isSelected = selectedItem == nav
                val chipBg = if (isSelected) BentoPrimary.copy(alpha = 0.15f) else Color.Transparent

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(chipBg)
                        .clickable { onSelect(nav) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("nav_${nav.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = nav.icon,
                        contentDescription = nav.label,
                        tint = if (isSelected) BentoPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    if (isSelected) {
                        Text(
                            text = nav.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoPrimary
                        )
                    }
                }
            }
        }
    }
}
*/



@Composable
fun BentoBottomNavigationBar(
    selectedItem: NavItem,
    onSelect: (NavItem) -> Unit
) {
    val items = NavItem.values()
    val selectedIndex = items.indexOf(selectedItem)
    val density = LocalDensity.current

    var containerWidthPx by remember { mutableIntStateOf(0) }
    val itemWidth = with(density) { (containerWidthPx / items.size).toDp() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BentoCardSurface,
        tonalElevation = 6.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(68.dp)
                .padding(horizontal = 16.dp)
                .onSizeChanged { containerWidthPx = it.width }
        ) {
            // Sliding pill background
            val pillOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "pillOffset"
            )

            if (containerWidthPx > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = pillOffset)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .padding(vertical = 10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(BentoPrimary.copy(alpha = 0.15f))
                )
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { nav ->
                    val isSelected = selectedItem == nav

                    val iconColor by animateColorAsState(
                        targetValue = if (isSelected) BentoPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(250),
                        label = "iconColor"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.1f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "iconScale"
                    )

                    Row(
                        modifier = Modifier
                            .width(if (itemWidth > 0.dp) itemWidth else 0.dp)
                            .fillMaxHeight()
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(nav) }
                            .testTag("nav_${nav.name.lowercase()}"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = nav.icon,
                            contentDescription = nav.label,
                            tint = iconColor,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn(tween(200)) + expandHorizontally(tween(250)),
                            exit = fadeOut(tween(150)) + shrinkHorizontally(tween(200))
                        ) {
                            Row {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = nav.label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BentoPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}