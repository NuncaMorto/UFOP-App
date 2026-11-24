package br.ufop.campus.ui.timetable

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.core.ffi.FriendScheduleInfo
import br.ufop.campus.core.map.MapMeetingFocusRequest
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusAdaptiveLayout
import br.ufop.campus.ui.theme.LocalCampusThemeController

@Composable
fun TimetableModalScreen(
    repository: CampusCoreRepository,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLocationInMap: ((MapMeetingFocusRequest) -> Unit)? = null
) {
    val context = LocalContext.current
    val isDark = LocalCampusThemeController.current.isDark
    val a11y = LocalCampusAccessibility.current.settings
    val adaptive = LocalCampusAdaptiveLayout.current
    val minTouchTarget = if (a11y.largeTouchTargets) 56.dp else 48.dp
    val toolbarVisualHeight = if (a11y.largeTouchTargets) 52.dp else 36.dp

    var isLandscape by rememberSaveable {
        mutableStateOf(CampusRepositoryHolder.loadTimetableIsLandscape(context))
    }
    var isReducedMode by rememberSaveable { mutableStateOf(false) }
    var showFullDayGrid by rememberSaveable { mutableStateOf(false) }

    // Em smartphones compactos, aplica orientação persistida (Horizontal por padrão, alternável para Vertical)
    // e restaura ao sair; em tablets, dobráveis abertos e Split Screen, mantém rotação livre.
    LaunchedEffect(context, isLandscape, adaptive.shouldForceScreenOrientation) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        if (adaptive.shouldForceScreenOrientation) {
            activity.requestedOrientation = if (isLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        } else if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(context) {
        val activity = context.findActivity()
        onDispose {
            if (activity != null && activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    val handleDismiss: () -> Unit = {
        val activity = context.findActivity()
        if (activity != null && activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDismiss()
    }

    BackHandler(onBack = handleDismiss)

    val mutationVersion by repository.mutationVersion.collectAsState()
    val studentProfile = remember(mutationVersion) {
        repository.getStudentProfile()
    }
    val availableSemesters = remember(mutationVersion) {
        repository.listAvailableSemesters().ifEmpty { listOf("2026.2") }
    }
    var selectedSemester by rememberSaveable {
        mutableStateOf(
            studentProfile.activeSemester.ifBlank { availableSemesters.first() }
        )
    }
    val effectiveSemester = if (selectedSemester in availableSemesters) {
        selectedSemester
    } else {
        availableSemesters.first()
    }

    val friendSchedules = remember(mutationVersion) {
        repository.listFriendSchedules()
    }
    var selectedFriendId by rememberSaveable { mutableStateOf<String?>(null) }
    val activeFriend: FriendScheduleInfo? = remember(selectedFriendId, friendSchedules) {
        selectedFriendId?.let { id -> friendSchedules.find { it.id == id } }
    }

    val timetable = remember(mutationVersion, effectiveSemester, activeFriend) {
        if (activeFriend != null) {
            repository.getFriendTimetable(activeFriend.id) ?: activeFriend.timetable
        } else {
            repository.getWeeklyTimetable(semester = effectiveSemester)
        }
    }

    val displayDays = FULL_WEEK_DAYS
    val displayRows = remember(timetable.rows, displayDays, showFullDayGrid) {
        buildDisplayedTimetableRows(timetable.rows, displayDays, showFullDayGrid)
    }

    var showImportFriendDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var friendPendingRemoval by remember { mutableStateOf<FriendScheduleInfo?>(null) }
    var selectedCellDetail by remember { mutableStateOf<SelectedTimetableCellDetail?>(null) }
    var pendingExportJson by remember { mutableStateOf("") }
    var pendingSuggestedFilename by remember { mutableStateOf("grade_ufop.ufopgrade") }
    var feedbackBannerMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Launcher para importar arquivo .ufopgrade (OpenDocument)
    val openUfopGradeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val jsonText = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?.trim()
                    .orEmpty()
                require(jsonText.isNotEmpty()) { "O arquivo selecionado está vazio." }
                repository.importFriendScheduleJson("", jsonText)
            }.onSuccess { imported ->
                selectedFriendId = imported.id
                val courseInfo = imported.courseName.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                feedbackBannerMessage =
                    "Grade de ${imported.friendName}$courseInfo (${imported.semester}) importada!"
            }.onFailure { err ->
                feedbackBannerMessage = err.message
                    ?: "Não foi possível importar o arquivo .ufopgrade selecionado."
            }
        }
    }

    val handleStartShareGrade: () -> Unit = {
        val rawName = studentProfile.fullName.ifBlank { "estudante_ufop" }
        val sanitizedName = sanitizeFilenameToken(rawName)
        val suggestedFilename = "grade_${sanitizedName}.ufopgrade"
        val bundleJson = repository.exportMyGradeBundleJson(effectiveSemester)
        pendingSuggestedFilename = suggestedFilename
        pendingExportJson = bundleJson
        showShareDialog = true
    }

    val selectedGradeLabel = activeFriend?.let { "Grade · ${it.friendName}" } ?: "Minha grade"
    val verticalScrollState = rememberScrollState()
    val horizontalGridScrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (isReducedMode) 12.dp else 16.dp,
                        vertical = if (isReducedMode) 6.dp else 10.dp
                    )
            ) {
                TimetableTopBar(
                    effectiveSemester = effectiveSemester,
                    timetableSemester = timetable.semester,
                    activeFriend = activeFriend,
                    studentProfile = studentProfile,
                    friendSchedules = friendSchedules,
                    availableSemesters = availableSemesters,
                    selectedGradeLabel = selectedGradeLabel,
                    isDark = isDark,
                    largeTouchTargets = a11y.largeTouchTargets,
                    minTouchTarget = minTouchTarget,
                    toolbarVisualHeight = toolbarVisualHeight,
                    isLandscape = isLandscape,
                    isReducedMode = isReducedMode,
                    showFullDayGrid = showFullDayGrid,
                    onSelectMyGrade = { selectedFriendId = null },
                    onSelectFriend = { friendId -> selectedFriendId = friendId },
                    onRequestRemoveFriend = { friend -> friendPendingRemoval = friend },
                    onImportFile = {
                        openUfopGradeLauncher.launch(
                            arrayOf("*/*", "application/json", "application/octet-stream")
                        )
                    },
                    onOpenImportTextDialog = { showImportFriendDialog = true },
                    onSelectSemester = { semesterOption ->
                        selectedSemester = semesterOption
                        selectedFriendId = null
                    },
                    onShareGrade = handleStartShareGrade,
                    onToggleLandscape = {
                        val nextLandscape = !isLandscape
                        isLandscape = nextLandscape
                        CampusRepositoryHolder.saveTimetableIsLandscape(context, nextLandscape)
                    },
                    onToggleReducedMode = { isReducedMode = !isReducedMode },
                    onToggleFullDayGrid = { showFullDayGrid = !showFullDayGrid },
                    onDismiss = handleDismiss
                )

                if (showShareDialog) {
                    ShareGradeDialog(
                        repository = repository,
                        effectiveSemester = effectiveSemester,
                        pendingExportJson = pendingExportJson,
                        pendingSuggestedFilename = pendingSuggestedFilename,
                        minTouchTarget = minTouchTarget,
                        coroutineScope = coroutineScope,
                        onDismiss = { showShareDialog = false },
                        onFeedback = { message -> feedbackBannerMessage = message }
                    )
                }

                if (showImportFriendDialog) {
                    ImportFriendGradeDialog(
                        repository = repository,
                        isDark = isDark,
                        minTouchTarget = minTouchTarget,
                        onDismiss = { showImportFriendDialog = false },
                        onImported = { imported ->
                            selectedFriendId = imported.id
                            feedbackBannerMessage =
                                "Grade de ${imported.friendName} (${imported.semester}) importada!"
                        }
                    )
                }

                friendPendingRemoval?.let { friendToDelete ->
                    RemoveFriendConfirmDialog(
                        friendToDelete = friendToDelete,
                        minTouchTarget = minTouchTarget,
                        onDismiss = { friendPendingRemoval = null },
                        onConfirmRemove = {
                            if (selectedFriendId == friendToDelete.id) {
                                selectedFriendId = null
                            }
                            repository.removeFriendSchedule(friendToDelete.id)
                            feedbackBannerMessage = "Grade de ${friendToDelete.friendName} removida."
                            friendPendingRemoval = null
                        }
                    )
                }

                selectedCellDetail?.let { detailInfo ->
                    TimetableCellDetailDialog(
                        detailInfo = detailInfo,
                        isDark = isDark,
                        minTouchTarget = minTouchTarget,
                        onDismiss = { selectedCellDetail = null },
                        onOpenLocationInMap = onOpenLocationInMap?.let { openInMap ->
                            { request ->
                                selectedCellDetail = null
                                handleDismiss()
                                openInMap(request)
                            }
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(verticalScrollState)
                ) {
                    feedbackBannerMessage?.let { message ->
                        TimetableFeedbackBanner(
                            message = message,
                            onDismiss = { feedbackBannerMessage = null }
                        )
                    }

                    TimetableWeeklyGrid(
                        displayDays = displayDays,
                        displayRows = displayRows,
                        isReducedMode = isReducedMode,
                        largeTouchTargets = a11y.largeTouchTargets,
                        isLandscape = isLandscape,
                        isDark = isDark,
                        horizontalGridScrollState = horizontalGridScrollState,
                        onCellClick = { cell, dayOfWeek, startTime, endTime ->
                            selectedCellDetail = resolveSelectedTimetableCellDetail(
                                repository = repository,
                                activeFriend = activeFriend,
                                cell = cell,
                                dayOfWeek = dayOfWeek,
                                startTime = startTime,
                                endTime = endTime
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(if (isReducedMode) 10.dp else 14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    TimetableDepartmentLegend(
                        legend = timetable.legend,
                        semester = timetable.semester,
                        isReducedMode = isReducedMode
                    )

                    // Card inferior do amigo ao rolar a grade para baixo (Foto circular + Nome + Curso)
                    if (activeFriend != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        FriendScheduleFooterCard(
                            friend = activeFriend,
                            semester = timetable.semester,
                            minTouchTarget = minTouchTarget,
                            onBackToMyGrade = { selectedFriendId = null }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TimetableFeedbackBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable(onClick = onDismiss),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "✕",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
