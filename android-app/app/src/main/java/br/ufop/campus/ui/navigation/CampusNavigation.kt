package br.ufop.campus.ui.navigation

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.core.ffi.IncomingGradePreview
import br.ufop.campus.core.map.CampusMapController
import br.ufop.campus.core.map.MapMeetingFocusRequest
import br.ufop.campus.core.update.AppUpdateManager
import br.ufop.campus.ui.agenda.AgendaScreen
import br.ufop.campus.ui.map.CampusMapScreen
import br.ufop.campus.ui.profile.ProfileViewModel
import br.ufop.campus.ui.profile.ReleaseNotesDialog
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusAdaptiveLayout
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.campusAccentTextColor
import br.ufop.campus.ui.theme.campusCardBorder
import br.ufop.campus.ui.theme.rememberCampusAdaptiveLayout
import br.ufop.campus.ui.update.AppUpdateDialog
import br.ufop.campus.ui.update.InAppUpdateBanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class CampusTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    AGENDA(
        label = "Agenda",
        icon = Icons.Rounded.CalendarToday,
        selectedIcon = Icons.Rounded.CalendarMonth
    ),
    DISCIPLINES(
        label = "Disciplinas",
        icon = Icons.Outlined.AutoStories,
        selectedIcon = Icons.Rounded.AutoStories
    ),
    ACTIVITIES(
        label = "Atividades",
        icon = Icons.Rounded.TaskAlt,
        selectedIcon = Icons.Rounded.CheckCircle
    ),
    PROFILE(
        label = "Perfil",
        icon = Icons.Rounded.Person,
        selectedIcon = Icons.Rounded.Person
    ),
    MAP(
        label = "Mapa",
        icon = Icons.Outlined.Map,
        selectedIcon = Icons.Rounded.Map
    );

    companion object {
        val primaryTabs: List<CampusTab> = listOf(AGENDA, DISCIPLINES, ACTIVITIES, PROFILE)
    }
}

@Composable
fun CampusNavigation(
    repository: CampusCoreRepository,
    modifier: Modifier = Modifier,
    pendingIncomingGrade: IncomingGradePreview? = null,
    incomingGradeError: String? = null,
    pendingTargetTab: CampusTab? = null,
    pendingTargetTabNonce: Int = 0,
    pendingMapFocus: MapMeetingFocusRequest? = null,
    pendingMapFocusNonce: Int = 0,
    onConsumeTargetTab: () -> Unit = {},
    onConsumeMapFocus: () -> Unit = {},
    onConsumeIncomingGrade: () -> Unit = {},
    onDismissIncomingGradeError: () -> Unit = {},
    onAppSecurityChanged: () -> Unit = {},
    onExitApp: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(CampusTab.AGENDA) }
    var showTimetableModal by rememberSaveable { mutableStateOf(false) }
    var showDynamicGridModal by rememberSaveable { mutableStateOf(false) }
    var showReleaseNotesDialog by rememberSaveable { mutableStateOf(false) }
    var checkedAutoReleaseNotes by rememberSaveable { mutableStateOf(false) }
    var pendingTimetableFriendId by rememberSaveable { mutableStateOf<String?>(null) }
    var incomingImportErrorBanner by remember { mutableStateOf<String?>(null) }

    val isDark = LocalCampusThemeController.current.isDark
    val a11y = LocalCampusAccessibility.current.settings
    val adaptive = rememberCampusAdaptiveLayout()
    val mutationVersion by repository.mutationVersion.collectAsState()
    val appUpdateState by AppUpdateManager.state.collectAsState()
    val isUpdateDialogOpen by AppUpdateManager.isDialogOpen.collectAsState()
    var onboardingRefreshTick by remember { mutableIntStateOf(0) }
    var tourReplayTrigger by remember { mutableIntStateOf(0) }
    var tourDismissTick by remember { mutableIntStateOf(0) }

    var mapController by remember { mutableStateOf<CampusMapController?>(null) }
    val profileViewModel = remember(repository) {
        ProfileViewModel(repository = repository)
    }

    DisposableEffect(profileViewModel) {
        onDispose {
            profileViewModel.close()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapController?.close()
            mapController = null
        }
    }

    val ensureMapController: () -> CampusMapController = {
        mapController ?: CampusMapController().also { mapController = it }
    }

    val studentProfile = remember(mutationVersion, onboardingRefreshTick) {
        repository.getStudentProfile()
    }
    val tourCompleted = remember(
        mutationVersion,
        onboardingRefreshTick,
        tourReplayTrigger,
        tourDismissTick
    ) {
        CampusRepositoryHolder.isGuidedTourCompleted(context, repository)
    }
    val shouldShowGuidedTour = studentProfile.onboardingCompleted && !tourCompleted

    val tabBounds = remember { mutableStateMapOf<CampusTab, Rect>() }

    val handleSelectTab: (CampusTab) -> Unit = { tab ->
        showTimetableModal = false
        showDynamicGridModal = false
        if (tab == CampusTab.MAP) {
            ensureMapController()
        }
        selectedTab = tab
    }

    val openMeetingInMap: (MapMeetingFocusRequest) -> Unit = { request ->
        showTimetableModal = false
        showDynamicGridModal = false
        val controller = ensureMapController()
        controller.resolveMeetingAsync(
            courseCode = request.courseCode,
            section = request.section,
            weekday = request.weekday,
            startTime = request.startTime,
            fallbackBuildingRaw = request.fallbackBuildingRaw
        )
        selectedTab = CampusTab.MAP
    }

    val renderActiveTabContent: @Composable () -> Unit = {
        when (selectedTab) {
            CampusTab.AGENDA -> {
                AgendaScreen(
                    repository = repository,
                    onOpenLocationInMap = openMeetingInMap
                )
            }
            CampusTab.DISCIPLINES -> {
                br.ufop.campus.ui.disciplines.DisciplinesScreen(
                    repository = repository,
                    onOpenTimetable = { showTimetableModal = true },
                    onOpenLocationInMap = openMeetingInMap
                )
            }
            CampusTab.ACTIVITIES -> {
                br.ufop.campus.ui.activities.ActivitiesPlaceholderScreen(
                    repository = repository,
                    onOpenLocationInMap = openMeetingInMap
                )
            }
            CampusTab.PROFILE -> {
                br.ufop.campus.ui.profile.ProfilePlaceholderScreen(
                    repository = repository,
                    onOpenDynamicGrid = { showDynamicGridModal = true },
                    onReplayGuidedTour = {
                        CampusRepositoryHolder.setGuidedTourCompleted(context, repository, false)
                        selectedTab = CampusTab.AGENDA
                        tourReplayTrigger += 1
                    },
                    onCheckForUpdates = {
                        AppUpdateManager.checkForUpdates(context, force = true)
                        AppUpdateManager.openUpdateDialog()
                    },
                    onAppSecurityChanged = onAppSecurityChanged,
                    onExitApp = onExitApp,
                    viewModel = profileViewModel
                )
            }
            CampusTab.MAP -> {
                val activeMapController = mapController
                if (activeMapController != null) {
                    CampusMapScreen(
                        controller = activeMapController,
                        bottomBarClearanceDp = if (adaptive.useNavigationRail) 16.dp else 82.dp,
                        startBarClearanceDp = adaptive.verticalIslandClearanceDp.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                }
            }
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == CampusTab.MAP && mapController == null) {
            ensureMapController()
        }
        mapController?.onMapTabActiveChanged(selectedTab == CampusTab.MAP)
        if (selectedTab != CampusTab.DISCIPLINES && showTimetableModal) {
            showTimetableModal = false
        }
    }

    LaunchedEffect(pendingTargetTab, pendingTargetTabNonce) {
        if (pendingTargetTab != null) {
            handleSelectTab(pendingTargetTab)
            onConsumeTargetTab()
        }
    }

    LaunchedEffect(pendingMapFocus, pendingMapFocusNonce) {
        if (pendingMapFocus != null) {
            openMeetingInMap(pendingMapFocus)
            onConsumeMapFocus()
        }
    }

    LaunchedEffect(studentProfile.onboardingCompleted, shouldShowGuidedTour, mutationVersion) {
        if (studentProfile.onboardingCompleted && !shouldShowGuidedTour && !checkedAutoReleaseNotes) {
            checkedAutoReleaseNotes = true
            val lastSeenVersion = CampusRepositoryHolder.getLastSeenReleaseVersion(context, repository)
            if (lastSeenVersion != CampusRepositoryHolder.CURRENT_APP_VERSION) {
                showReleaseNotesDialog = true
            }
        }
    }

    CompositionLocalProvider(LocalCampusAdaptiveLayout provides adaptive) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .onPreviewKeyEvent { event ->
                    if (!studentProfile.onboardingCompleted) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown && event.isCtrlPressed) {
                        val targetTab = when (event.key) {
                            Key.One, Key.NumPad1 -> CampusTab.AGENDA
                            Key.Two, Key.NumPad2 -> CampusTab.DISCIPLINES
                            Key.Three, Key.NumPad3 -> CampusTab.ACTIVITIES
                            Key.Four, Key.NumPad4 -> CampusTab.PROFILE
                            Key.Five, Key.NumPad5 -> CampusTab.MAP
                            else -> null
                        }
                        if (targetTab != null) {
                            handleSelectTab(targetTab)
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
        ) {
            if (adaptive.useNavigationRail) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (selectedTab == CampusTab.MAP) {
                                    Modifier
                                } else {
                                    Modifier
                                        .padding(start = adaptive.verticalIslandClearanceDp.dp)
                                        .statusBarsPadding()
                                }
                            )
                    ) {
                        renderActiveTabContent()
                    }

                    FloatingVerticalCampusNavigationIsland(
                        selectedTab = selectedTab,
                        isDark = isDark,
                        reduceMotion = a11y.reduceMotion,
                        avatarPath = studentProfile.avatarPath,
                        studentName = studentProfile.fullName,
                        avatarRefreshKey = mutationVersion,
                        trackTabBounds = shouldShowGuidedTour,
                        onSelectTab = handleSelectTab,
                        onTabPositioned = { tab, rect ->
                            if (shouldShowGuidedTour && tabBounds[tab] != rect) {
                                tabBounds[tab] = rect
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(start = 12.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (selectedTab == CampusTab.MAP) Modifier else Modifier.statusBarsPadding()
                        )
                ) {
                    renderActiveTabContent()
                }

                if (selectedTab != CampusTab.MAP) {
                    // Scrim Inferior contínuo na cor exata do Canvas para evitar vazamento de texto sob a barra flutuante
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(124.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                                        MaterialTheme.colorScheme.background,
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )
                }

                FloatingCampusNavigationBar(
                    selectedTab = selectedTab,
                    isDark = isDark,
                    reduceMotion = a11y.reduceMotion,
                    avatarPath = studentProfile.avatarPath,
                    studentName = studentProfile.fullName,
                    avatarRefreshKey = mutationVersion,
                    trackTabBounds = shouldShowGuidedTour,
                    onSelectTab = handleSelectTab,
                    onTabPositioned = { tab, rect ->
                        if (shouldShowGuidedTour && tabBounds[tab] != rect) {
                            tabBounds[tab] = rect
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }

            if (showTimetableModal) {
                br.ufop.campus.ui.timetable.TimetableModalScreen(
                    repository = repository,
                    onDismiss = { showTimetableModal = false },
                    onOpenLocationInMap = openMeetingInMap,
                    initialFriendId = pendingTimetableFriendId,
                    onInitialFriendConsumed = { pendingTimetableFriendId = null }
                )
            }

            if (showDynamicGridModal) {
                br.ufop.campus.ui.curriculum.DynamicCurriculumGridScreen(
                    repository = repository,
                    onDismiss = { showDynamicGridModal = false }
                )
            }

            if (shouldShowGuidedTour) {
                GuidedTourOverlay(
                    tabBounds = tabBounds,
                    onSelectTab = handleSelectTab,
                    onFinish = {
                        CampusRepositoryHolder.setGuidedTourCompleted(context, repository, true)
                        tourDismissTick += 1
                    }
                )
            }

            if (!studentProfile.onboardingCompleted) {
                br.ufop.campus.ui.profile.StudentOnboardingOverlay(
                    repository = repository,
                    onCompleted = { onboardingRefreshTick += 1 }
                )
            }

            if (showReleaseNotesDialog) {
                ReleaseNotesDialog(
                    onDismiss = {
                        showReleaseNotesDialog = false
                        CampusRepositoryHolder.setLastSeenReleaseVersion(
                            context = context,
                            repository = repository,
                            version = CampusRepositoryHolder.CURRENT_APP_VERSION
                        )
                    }
                )
            }

            if (studentProfile.onboardingCompleted && !shouldShowGuidedTour) {
                InAppUpdateBanner(
                    state = appUpdateState,
                    onOpenDetails = { AppUpdateManager.openUpdateDialog() },
                    onDismissBanner = { AppUpdateManager.dismissBannerForCurrentRelease(context) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            if (isUpdateDialogOpen) {
                AppUpdateDialog(
                    state = appUpdateState,
                    onDismiss = { AppUpdateManager.closeUpdateDialog() }
                )
            }

            if (studentProfile.onboardingCompleted && pendingIncomingGrade != null) {
                val incomingTouchTarget = if (a11y.largeTouchTargets) 56.dp else 48.dp
                br.ufop.campus.ui.timetable.IncomingGradeConfirmSheet(
                    preview = pendingIncomingGrade,
                    minTouchTarget = incomingTouchTarget,
                    onConfirmImport = {
                        runCatching {
                            repository.importFriendScheduleJson("", pendingIncomingGrade.rawJsonPayload)
                        }.onSuccess { imported ->
                            pendingTimetableFriendId = imported.id
                            selectedTab = CampusTab.DISCIPLINES
                            showTimetableModal = true
                            onConsumeIncomingGrade()
                        }.onFailure { err ->
                            incomingImportErrorBanner = err.message
                                ?: "Não foi possível importar a grade recebida."
                            onConsumeIncomingGrade()
                        }
                    },
                    onDismiss = onConsumeIncomingGrade
                )
            }

            val activeIncomingError = incomingGradeError ?: incomingImportErrorBanner
            if (studentProfile.onboardingCompleted && activeIncomingError != null) {
                val errorCloseTouchTarget = if (a11y.largeTouchTargets) 56.dp else 48.dp
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Não foi possível abrir a grade",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeIncomingError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                incomingImportErrorBanner = null
                                onDismissIncomingGradeError()
                            },
                            modifier = Modifier
                                .defaultMinSize(minWidth = errorCloseTouchTarget, minHeight = errorCloseTouchTarget)
                                .semantics { contentDescription = "Fechar aviso de erro" }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Fechar",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private val navAvatarBitmapCache: LruCache<String, ImageBitmap> by lazy {
    LruCache<String, ImageBitmap>(6)
}

private val navAvatarLatestByPath = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

private val NAV_SPRING_SPEC = spring<Float>(dampingRatio = 0.80f, stiffness = 420f)

@Composable
private fun rememberCachedNavAvatarBitmap(
    avatarPath: String?,
    avatarRefreshKey: Any
): ImageBitmap? {
    val initialBitmap = remember(avatarPath) {
        avatarPath?.takeIf { it.isNotBlank() }?.let { navAvatarLatestByPath[it] }
    }
    val avatarBitmap by produceState<ImageBitmap?>(
        initialValue = initialBitmap,
        key1 = avatarPath,
        key2 = avatarRefreshKey
    ) {
        value = if (avatarPath.isNullOrBlank()) {
            navAvatarLatestByPath.clear()
            null
        } else {
            withContext(Dispatchers.IO) {
                decodeNavAvatarBitmapCached(avatarPath, maxSizePx = 96)
            }
        }
    }
    return avatarBitmap
}

@Composable
private fun FloatingVerticalCampusNavigationIsland(
    selectedTab: CampusTab,
    isDark: Boolean,
    reduceMotion: Boolean,
    avatarPath: String?,
    studentName: String,
    avatarRefreshKey: Any,
    trackTabBounds: Boolean,
    onSelectTab: (CampusTab) -> Unit,
    onTabPositioned: (CampusTab, Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val avatarBitmap = rememberCachedNavAvatarBitmap(avatarPath, avatarRefreshKey)
    val density = LocalDensity.current
    val highContrast = LocalCampusAccessibility.current.settings.highContrastMode
    val capsuleBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    val capsuleBorder = campusCardBorder(strokeWidth = 2.dp)
    val pillBg = if (highContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val activeAccentText = campusAccentTextColor()
    val selectedContentColor = when {
        highContrast -> MaterialTheme.colorScheme.onPrimary
        isDark -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> activeAccentText
    }

    var lastPrimaryIndex by rememberSaveable {
        mutableIntStateOf(CampusTab.primaryTabs.indexOf(selectedTab).coerceAtLeast(0))
    }
    val primaryIndex = CampusTab.primaryTabs.indexOf(selectedTab)
    val isPrimarySelected = primaryIndex >= 0
    val targetPrimaryIndex = if (isPrimarySelected) primaryIndex else lastPrimaryIndex
    LaunchedEffect(primaryIndex) {
        if (primaryIndex >= 0) {
            lastPrimaryIndex = primaryIndex
        }
    }

    val animatedIndexState = animateFloatAsState(
        targetValue = targetPrimaryIndex.toFloat(),
        animationSpec = if (reduceMotion) snap() else NAV_SPRING_SPEC,
        label = "VerticalNavPillSlideIndex"
    )
    val pillAlphaState = animateFloatAsState(
        targetValue = if (isPrimarySelected) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else NAV_SPRING_SPEC,
        label = "VerticalNavPillAlpha"
    )
    val pillScaleState = animateFloatAsState(
        targetValue = if (isPrimarySelected) 1f else 0.86f,
        animationSpec = if (reduceMotion) snap() else NAV_SPRING_SPEC,
        label = "VerticalNavPillScale"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.width(74.dp),
            shape = RoundedCornerShape(999.dp),
            color = capsuleBg,
            shadowElevation = if (isDark) 14.dp else 8.dp,
            tonalElevation = 0.dp,
            border = capsuleBorder
        ) {
            val itemHeightDp = 58.dp
            val gapDp = 4.dp
            val itemHeightPx = with(density) { itemHeightDp.toPx() }
            val gapPx = with(density) { gapDp.toPx() }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 5.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeightDp)
                        .graphicsLayer {
                            translationY = animatedIndexState.value * (itemHeightPx + gapPx)
                            scaleX = pillScaleState.value
                            scaleY = pillScaleState.value
                            alpha = pillAlphaState.value
                        }
                        .clip(RoundedCornerShape(999.dp))
                        .background(pillBg)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(gapDp)
                ) {
                    CampusTab.primaryTabs.forEach { tab ->
                        val isSelected = selectedTab == tab
                        val itemColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                selectedContentColor
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = if (reduceMotion) snap() else spring(),
                            label = "VerticalIslandContent_${tab.name}"
                        )
                        val iconScaleState = animateFloatAsState(
                            targetValue = if (isSelected) 1.08f else 1.0f,
                            animationSpec = if (reduceMotion) snap() else NAV_SPRING_SPEC,
                            label = "VerticalIslandIconScale_${tab.name}"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(itemHeightDp)
                                .then(
                                    if (trackTabBounds) {
                                        Modifier.onGloballyPositioned { coords ->
                                            onTabPositioned(tab, coords.boundsInRoot())
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                                .clip(RoundedCornerShape(999.dp))
                                .semantics {
                                    selected = isSelected
                                    contentDescription = "Aba ${tab.label}"
                                }
                                .clickable(
                                    role = Role.Tab,
                                    onClick = { onSelectTab(tab) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (tab == CampusTab.PROFILE) {
                                    ProfileTabAvatarIcon(
                                        avatarBitmap = avatarBitmap,
                                        studentName = studentName,
                                        isSelected = isSelected,
                                        highContrast = highContrast,
                                        contentColor = itemColor
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                                        contentDescription = tab.label,
                                        tint = itemColor,
                                        modifier = Modifier
                                            .size(21.dp)
                                            .graphicsLayer {
                                                val s = iconScaleState.value
                                                scaleX = s
                                                scaleY = s
                                            }
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = tab.label,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    letterSpacing = (-0.2).sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = itemColor,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        val isMapSelected = selectedTab == CampusTab.MAP
        val mapPillAlphaState = animateFloatAsState(
            targetValue = if (isMapSelected) 1f else 0f,
            animationSpec = if (reduceMotion) snap() else NAV_SPRING_SPEC,
            label = "VerticalIslandMapBtnAlpha"
        )
        val mapPillScaleState = animateFloatAsState(
            targetValue = if (isMapSelected) 1f else 0.84f,
            animationSpec = if (reduceMotion) snap() else NAV_SPRING_SPEC,
            label = "VerticalIslandMapBtnScale"
        )
        val mapIconScaleState = animateFloatAsState(
            targetValue = if (isMapSelected) 1.08f else 1.0f,
            animationSpec = if (reduceMotion) snap() else NAV_SPRING_SPEC,
            label = "VerticalIslandMapIconScale"
        )
        val mapIconColor by animateColorAsState(
            targetValue = if (isMapSelected) {
                selectedContentColor
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            animationSpec = if (reduceMotion) snap() else spring(),
            label = "VerticalIslandMapBtnIcon"
        )

        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = capsuleBg,
            shadowElevation = if (isDark) 14.dp else 8.dp,
            tonalElevation = 0.dp,
            border = capsuleBorder
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
                    .then(
                        if (trackTabBounds) {
                            Modifier.onGloballyPositioned { coords ->
                                onTabPositioned(CampusTab.MAP, coords.boundsInRoot())
                            }
                        } else {
                            Modifier
                        }
                    )
                    .clip(CircleShape)
                    .semantics {
                        selected = isMapSelected
                        contentDescription = "Aba Mapa"
                    }
                    .clickable(
                        role = Role.Tab,
                        onClick = { onSelectTab(CampusTab.MAP) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val s = mapPillScaleState.value
                            scaleX = s
                            scaleY = s
                            alpha = mapPillAlphaState.value
                        }
                        .clip(CircleShape)
                        .background(pillBg)
                )
                Icon(
                    imageVector = if (isMapSelected) CampusTab.MAP.selectedIcon else CampusTab.MAP.icon,
                    contentDescription = "Mapa",
                    tint = mapIconColor,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            val s = mapIconScaleState.value
                            scaleX = s
                            scaleY = s
                        }
                )
            }
        }
    }
}

@Composable
private fun FloatingCampusNavigationBar(
    selectedTab: CampusTab,
    isDark: Boolean,
    reduceMotion: Boolean,
    avatarPath: String?,
    studentName: String,
    avatarRefreshKey: Any,
    trackTabBounds: Boolean,
    onSelectTab: (CampusTab) -> Unit,
    onTabPositioned: (CampusTab, Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val avatarBitmap = rememberCachedNavAvatarBitmap(avatarPath, avatarRefreshKey)
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val highContrast = LocalCampusAccessibility.current.settings.highContrastMode
    val capsuleBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    val capsuleBorder = campusCardBorder(strokeWidth = 2.dp)
    val pillBg = if (highContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val activeAccentText = campusAccentTextColor()
    val selectedContentColor = when {
        highContrast -> MaterialTheme.colorScheme.onPrimary
        isDark -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> activeAccentText
    }

    var lastPrimaryIndex by rememberSaveable {
        mutableIntStateOf(CampusTab.primaryTabs.indexOf(selectedTab).coerceAtLeast(0))
    }
    val primaryIndex = CampusTab.primaryTabs.indexOf(selectedTab)
    val isPrimarySelected = primaryIndex >= 0
    val targetPrimaryIndex = if (isPrimarySelected) primaryIndex else lastPrimaryIndex
    LaunchedEffect(primaryIndex) {
        if (primaryIndex >= 0) {
            lastPrimaryIndex = primaryIndex
        }
    }

    val animatedIndexState = animateFloatAsState(
        targetValue = targetPrimaryIndex.toFloat(),
        animationSpec = if (reduceMotion) snap() else NAV_SPRING_SPEC,
        label = "FloatingNavPillSlideIndex"
    )
    val pillAlphaState = animateFloatAsState(
        targetValue = if (isPrimarySelected) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else NAV_SPRING_SPEC,
        label = "FloatingNavPillAlpha"
    )
    val pillScaleState = animateFloatAsState(
        targetValue = if (isPrimarySelected) 1f else 0.86f,
        animationSpec = if (reduceMotion) snap() else NAV_SPRING_SPEC,
        label = "FloatingNavPillScale"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(64.dp),
            shape = RoundedCornerShape(999.dp),
            color = capsuleBg,
            shadowElevation = if (isDark) 14.dp else 8.dp,
            tonalElevation = 0.dp,
            border = capsuleBorder
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 5.dp)
            ) {
                val tabCount = CampusTab.primaryTabs.size
                val gapDp = 2.dp
                val gapPx = with(density) { gapDp.toPx() }
                val totalWidthPx = constraints.maxWidth.toFloat()
                val slotWidthPx = ((totalWidthPx - gapPx * (tabCount - 1)) / tabCount).coerceAtLeast(0f)
                val slotWidthDp = with(density) { slotWidthPx.toDp() }
                val rtlSign = if (isRtl) -1f else 1f

                Box(
                    modifier = Modifier
                        .width(slotWidthDp)
                        .fillMaxHeight()
                        .graphicsLayer {
                            translationX = rtlSign * animatedIndexState.value * (slotWidthPx + gapPx)
                            val s = pillScaleState.value
                            scaleX = s
                            scaleY = s
                            alpha = pillAlphaState.value
                        }
                        .clip(RoundedCornerShape(999.dp))
                        .background(pillBg)
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(gapDp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CampusTab.primaryTabs.forEach { tab ->
                        val isSelected = selectedTab == tab
                        val itemColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                selectedContentColor
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = if (reduceMotion) snap() else spring(),
                            label = "FloatingNavContent_${tab.name}"
                        )
                        val iconScaleState = animateFloatAsState(
                            targetValue = if (isSelected) 1.08f else 1.0f,
                            animationSpec = if (reduceMotion) snap() else NAV_SPRING_SPEC,
                            label = "FloatingNavIconScale_${tab.name}"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(
                                    if (trackTabBounds) {
                                        Modifier.onGloballyPositioned { coords ->
                                            onTabPositioned(tab, coords.boundsInRoot())
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                                .clip(RoundedCornerShape(999.dp))
                                .semantics {
                                    selected = isSelected
                                    contentDescription = "Aba ${tab.label}"
                                }
                                .clickable(
                                    role = Role.Tab,
                                    onClick = { onSelectTab(tab) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (tab == CampusTab.PROFILE) {
                                    ProfileTabAvatarIcon(
                                        avatarBitmap = avatarBitmap,
                                        studentName = studentName,
                                        isSelected = isSelected,
                                        highContrast = highContrast,
                                        contentColor = itemColor
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                                        contentDescription = tab.label,
                                        tint = itemColor,
                                        modifier = Modifier
                                            .size(21.dp)
                                            .graphicsLayer {
                                                val s = iconScaleState.value
                                                scaleX = s
                                                scaleY = s
                                            }
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = tab.label,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    letterSpacing = (-0.2).sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = itemColor,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        val isMapSelected = selectedTab == CampusTab.MAP
        val mapPillAlphaState = animateFloatAsState(
            targetValue = if (isMapSelected) 1f else 0f,
            animationSpec = if (reduceMotion) snap() else NAV_SPRING_SPEC,
            label = "FloatingMapBtnAlpha"
        )
        val mapPillScaleState = animateFloatAsState(
            targetValue = if (isMapSelected) 1f else 0.84f,
            animationSpec = if (reduceMotion) snap() else NAV_SPRING_SPEC,
            label = "FloatingMapBtnScale"
        )
        val mapIconScaleState = animateFloatAsState(
            targetValue = if (isMapSelected) 1.08f else 1.0f,
            animationSpec = if (reduceMotion) snap() else NAV_SPRING_SPEC,
            label = "FloatingMapIconScale"
        )
        val mapIconColor by animateColorAsState(
            targetValue = if (isMapSelected) {
                selectedContentColor
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            animationSpec = if (reduceMotion) snap() else spring(),
            label = "FloatingMapBtnIcon"
        )

        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = capsuleBg,
            shadowElevation = if (isDark) 14.dp else 8.dp,
            tonalElevation = 0.dp,
            border = capsuleBorder
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
                    .then(
                        if (trackTabBounds) {
                            Modifier.onGloballyPositioned { coords ->
                                onTabPositioned(CampusTab.MAP, coords.boundsInRoot())
                            }
                        } else {
                            Modifier
                        }
                    )
                    .clip(CircleShape)
                    .semantics {
                        selected = isMapSelected
                        contentDescription = "Aba Mapa"
                    }
                    .clickable(
                        role = Role.Tab,
                        onClick = { onSelectTab(CampusTab.MAP) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val s = mapPillScaleState.value
                            scaleX = s
                            scaleY = s
                            alpha = mapPillAlphaState.value
                        }
                        .clip(CircleShape)
                        .background(pillBg)
                )
                Icon(
                    imageVector = if (isMapSelected) CampusTab.MAP.selectedIcon else CampusTab.MAP.icon,
                    contentDescription = "Mapa",
                    tint = mapIconColor,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            val s = mapIconScaleState.value
                            scaleX = s
                            scaleY = s
                        }
                )
            }
        }
    }
}

@Composable
private fun ProfileTabAvatarIcon(
    avatarBitmap: ImageBitmap?,
    studentName: String,
    isSelected: Boolean,
    highContrast: Boolean,
    contentColor: Color
) {
    val activeAccentText = campusAccentTextColor()
    val ringWidth = if (isSelected) 2.dp else 1.dp
    val ringColor = when {
        isSelected && highContrast -> contentColor
        isSelected -> activeAccentText
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val initials = remember(studentName) { extractNavInitials(studentName) }

    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) {
                    if (highContrast) {
                        contentColor.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    }
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .border(width = ringWidth, color = ringColor, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        when {
            avatarBitmap != null -> {
                Image(
                    bitmap = avatarBitmap,
                    contentDescription = "Perfil",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
            initials.isNotEmpty() -> {
                Text(
                    text = initials,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected && !highContrast) activeAccentText else contentColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Perfil",
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun extractNavInitials(fullName: String): String {
    val parts = fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> ""
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> "${parts.first().first()}${parts.last().first()}".uppercase()
    }
}

private fun decodeNavAvatarBitmapCached(path: String, maxSizePx: Int = 96): ImageBitmap? {
    val file = File(path)
    if (!file.exists() || !file.isFile) {
        navAvatarLatestByPath.remove(path)
        return null
    }
    val key = "$path:${file.lastModified()}:${file.length()}:$maxSizePx"
    navAvatarBitmapCache.get(key)?.let { hit ->
        navAvatarLatestByPath[path] = hit
        return hit
    }
    val decoded = runCatching {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sampleSize = 1
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDim / sampleSize > maxSizePx) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
        }
        BitmapFactory.decodeFile(file.absolutePath, decodeOptions)?.asImageBitmap()
    }.getOrNull()
    if (decoded != null) {
        navAvatarBitmapCache.put(key, decoded)
        navAvatarLatestByPath[path] = decoded
    }
    return decoded
}
