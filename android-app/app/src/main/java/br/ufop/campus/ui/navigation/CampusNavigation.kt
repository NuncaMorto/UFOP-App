package br.ufop.campus.ui.navigation

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
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
import br.ufop.campus.ui.theme.rememberCampusAdaptiveLayout
import br.ufop.campus.ui.update.AppUpdateDialog
import br.ufop.campus.ui.update.InAppUpdateBanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class CampusTab(
    val label: String,
    val icon: ImageVector
) {
    AGENDA(
        label = "Agenda",
        icon = Icons.Default.DateRange
    ),
    DISCIPLINES(
        label = "Disciplinas",
        icon = Icons.AutoMirrored.Filled.List
    ),
    ACTIVITIES(
        label = "Atividades",
        icon = Icons.Default.CheckCircle
    ),
    PROFILE(
        label = "Perfil",
        icon = Icons.Default.Person
    ),
    MAP(
        label = "Mapa",
        icon = Icons.Outlined.Map
    );

    companion object {
        val primaryTabs: List<CampusTab> = listOf(AGENDA, DISCIPLINES, ACTIVITIES, PROFILE)
    }
}

@Composable
fun CampusNavigation(
    repository: CampusCoreRepository,
    modifier: Modifier = Modifier,
    onAppSecurityChanged: () -> Unit = {},
    onExitApp: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(CampusTab.AGENDA) }
    var showTimetableModal by rememberSaveable { mutableStateOf(false) }
    var showDynamicGridModal by rememberSaveable { mutableStateOf(false) }
    var showReleaseNotesDialog by rememberSaveable { mutableStateOf(false) }
    var checkedAutoReleaseNotes by rememberSaveable { mutableStateOf(false) }

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
        controller.resolveMeeting(
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
                    onOpenReleaseNotes = { showReleaseNotesDialog = true },
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
                        bottomBarClearanceDp = if (adaptive.useNavigationRail) 16.dp else 88.dp,
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
        if (selectedTab != CampusTab.DISCIPLINES && showTimetableModal) {
            showTimetableModal = false
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
                Row(modifier = Modifier.fillMaxSize()) {
                    CampusNavigationRail(
                        selectedTab = selectedTab,
                        expanded = adaptive.useExpandedRail,
                        isDark = isDark,
                        reduceMotion = a11y.reduceMotion,
                        avatarPath = studentProfile.avatarPath,
                        studentName = studentProfile.fullName,
                        avatarRefreshKey = mutationVersion,
                        onSelectTab = handleSelectTab,
                        onTabPositioned = { tab, rect ->
                            tabBounds[tab] = rect
                        }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (selectedTab == CampusTab.MAP) Modifier else Modifier.statusBarsPadding()
                            )
                    ) {
                        renderActiveTabContent()
                    }
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
                    // IHC-03 Scrim Inferior sob a Barra Flutuante para transição suave de rolagem
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(108.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.80f),
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
                    onSelectTab = handleSelectTab,
                    onTabPositioned = { tab, rect ->
                        tabBounds[tab] = rect
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
                    onOpenLocationInMap = openMeetingInMap
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
        }
    }
}

@Composable
private fun CampusNavigationRail(
    selectedTab: CampusTab,
    expanded: Boolean,
    isDark: Boolean,
    reduceMotion: Boolean,
    avatarPath: String?,
    studentName: String,
    avatarRefreshKey: Any,
    onSelectTab: (CampusTab) -> Unit,
    onTabPositioned: (CampusTab, Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val avatarBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = avatarPath,
        key2 = avatarRefreshKey
    ) {
        value = if (avatarPath.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                decodeNavAvatarBitmap(avatarPath, maxSizePx = 96)
            }
        }
    }

    val railWidth = if (expanded) 196.dp else 84.dp
    val railBg = MaterialTheme.colorScheme.surface.copy(
        alpha = if (isDark) 0.92f else 0.96f
    )

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(railWidth),
        color = railBg,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = if (expanded) 12.dp else 8.dp, vertical = 16.dp),
            horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (expanded) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "VIDA ACADÊMICA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "UFOP",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            CampusTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                val pillColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = if (reduceMotion) snap() else spring(),
                    label = "RailNavPillBg_${tab.name}"
                )
                val itemColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = if (reduceMotion) snap() else spring(),
                    label = "RailNavContent_${tab.name}"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minWidth = 48.dp, minHeight = 56.dp)
                        .onGloballyPositioned { coords ->
                            onTabPositioned(tab, coords.boundsInRoot())
                        }
                        .clip(RoundedCornerShape(18.dp))
                        .background(pillColor)
                        .semantics {
                            selected = isSelected
                            contentDescription = "Aba ${tab.label}"
                        }
                        .clickable(
                            role = Role.Tab,
                            onClick = { onSelectTab(tab) }
                        )
                        .padding(
                            horizontal = if (expanded) 14.dp else 6.dp,
                            vertical = if (expanded) 12.dp else 8.dp
                        ),
                    contentAlignment = if (expanded) Alignment.CenterStart else Alignment.Center
                ) {
                    if (expanded) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (tab == CampusTab.PROFILE) {
                                ProfileTabAvatarIcon(
                                    avatarBitmap = avatarBitmap,
                                    studentName = studentName,
                                    isSelected = isSelected,
                                    contentColor = itemColor
                                )
                            } else {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    tint = itemColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = itemColor,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (tab == CampusTab.PROFILE) {
                                ProfileTabAvatarIcon(
                                    avatarBitmap = avatarBitmap,
                                    studentName = studentName,
                                    isSelected = isSelected,
                                    contentColor = itemColor
                                )
                            } else {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    tint = itemColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = tab.label,
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
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
}

@Composable
private fun FloatingCampusNavigationBar(
    selectedTab: CampusTab,
    isDark: Boolean,
    reduceMotion: Boolean,
    avatarPath: String?,
    studentName: String,
    avatarRefreshKey: Any,
    onSelectTab: (CampusTab) -> Unit,
    onTabPositioned: (CampusTab, Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val avatarBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = avatarPath,
        key2 = avatarRefreshKey
    ) {
        value = if (avatarPath.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                decodeNavAvatarBitmap(avatarPath, maxSizePx = 96)
            }
        }
    }

    val capsuleBg = MaterialTheme.colorScheme.surface.copy(
        alpha = if (isDark) 0.78f else 0.88f
    )
    val capsuleBorder = if (isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
    }

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
            shadowElevation = 12.dp,
            tonalElevation = 3.dp,
            border = BorderStroke(1.dp, capsuleBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CampusTab.primaryTabs.forEach { tab ->
                    val isSelected = selectedTab == tab
                    val pillColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.90f)
                        } else {
                            Color.Transparent
                        },
                        animationSpec = if (reduceMotion) snap() else spring(),
                        label = "FloatingNavPillBg_${tab.name}"
                    )
                    val itemColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = if (reduceMotion) snap() else spring(),
                        label = "FloatingNavContent_${tab.name}"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onGloballyPositioned { coords ->
                                onTabPositioned(tab, coords.boundsInRoot())
                            }
                            .clip(RoundedCornerShape(999.dp))
                            .background(pillColor)
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
                                    contentColor = itemColor
                                )
                            } else {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    tint = itemColor,
                                    modifier = Modifier.size(21.dp)
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

        val isMapSelected = selectedTab == CampusTab.MAP
        val mapPillColor by animateColorAsState(
            targetValue = if (isMapSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.90f)
            } else {
                Color.Transparent
            },
            animationSpec = if (reduceMotion) snap() else spring(),
            label = "FloatingMapBtnBg"
        )
        val mapIconColor by animateColorAsState(
            targetValue = if (isMapSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
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
            shadowElevation = 12.dp,
            tonalElevation = 3.dp,
            border = BorderStroke(1.dp, capsuleBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
                    .onGloballyPositioned { coords ->
                        onTabPositioned(CampusTab.MAP, coords.boundsInRoot())
                    }
                    .clip(CircleShape)
                    .background(mapPillColor)
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
                Icon(
                    imageVector = if (isMapSelected) Icons.Filled.Map else CampusTab.MAP.icon,
                    contentDescription = "Mapa",
                    tint = mapIconColor,
                    modifier = Modifier.size(24.dp)
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
    contentColor: Color
) {
    val ringWidth = if (isSelected) 2.dp else 1.dp
    val ringColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val initials = remember(studentName) { extractNavInitials(studentName) }

    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
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
                    color = if (isSelected) MaterialTheme.colorScheme.primary else contentColor,
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

private fun decodeNavAvatarBitmap(path: String, maxSizePx: Int = 96): ImageBitmap? {
    val file = File(path)
    if (!file.exists() || !file.isFile) return null
    return runCatching {
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
}
