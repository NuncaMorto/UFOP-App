package br.ufop.campus.ui.curriculum

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.curriculum.DynamicCourseNode
import br.ufop.campus.core.curriculum.DynamicCurriculumCatalog
import br.ufop.campus.core.curriculum.DynamicGridEngine
import br.ufop.campus.core.curriculum.DynamicGridToggleAction
import br.ufop.campus.core.curriculum.UfopCurriculumCatalog
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusAdaptiveLayout
import br.ufop.campus.ui.theme.LocalCampusThemeController

internal data class DynamicGridFeedbackState(
    val message: String,
    val isBlockedWarning: Boolean
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun DynamicCurriculumGridScreen(
    repository: CampusCoreRepository,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = LocalCampusThemeController.current.isDark
    val a11y = LocalCampusAccessibility.current.settings
    val adaptive = LocalCampusAdaptiveLayout.current
    val mutationVersion by repository.mutationVersion.collectAsState()

    val studentProfile = remember(mutationVersion) { repository.getStudentProfile() }
    val creReport = remember(mutationVersion) { repository.getAcademicCreReport() }
    val catalogCourses = remember(context) { UfopCurriculumCatalog.getAllCourses(context) }
    val availableMatrices = remember(catalogCourses) {
        DynamicCurriculumCatalog.buildAllMatrices(catalogCourses)
    }
    val autoResolution = remember(
        studentProfile.courseName,
        studentProfile.registrationNumber,
        studentProfile.campusName,
        creReport.semesters,
        catalogCourses
    ) {
        val earliestSemester = creReport.semesters
            .mapNotNull { it.semester.trim().takeIf { s -> s.isNotEmpty() } }
            .minByOrNull { raw ->
                DynamicCurriculumCatalog.parseSemesterTerm(raw)
                    ?.let { (year, sem) -> year * 10 + sem }
                    ?: Int.MAX_VALUE
            }
        DynamicCurriculumCatalog.resolveMatrixForStudent(
            courseName = studentProfile.courseName,
            registrationNumber = studentProfile.registrationNumber,
            earliestTranscriptSemester = earliestSemester,
            catalogCourses = catalogCourses,
            campusHint = studentProfile.campusName
        )
    }

    var selectedMatrixId by rememberSaveable(autoResolution.matrix.id) {
        mutableStateOf(autoResolution.matrix.id)
    }
    val activeMatrix = remember(selectedMatrixId, catalogCourses, availableMatrices) {
        DynamicCurriculumCatalog.findById(selectedMatrixId, catalogCourses)
            ?: availableMatrices.firstOrNull()
            ?: DynamicCurriculumCatalog.defaultMatrix
    }

    var isHorizontal by rememberSaveable {
        mutableStateOf(CampusRepositoryHolder.loadDynamicGridIsHorizontal(context))
    }
    var isHeaderCollapsed by rememberSaveable { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showMatrixPickerDialog by remember { mutableStateOf(false) }
    var feedbackState by remember { mutableStateOf<DynamicGridFeedbackState?>(null) }

    // Em smartphones compactos, Horizontal rotaciona para paisagem e Vertical restaura retrato;
    // em tablets, dobráveis abertos e Split Screen, mantém rotação livre e alterna apenas o eixo da grade.
    LaunchedEffect(context, isHorizontal, adaptive.shouldForceScreenOrientation) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        if (adaptive.shouldForceScreenOrientation) {
            activity.requestedOrientation = if (isHorizontal) {
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

    val completedCodes = remember(mutationVersion, activeMatrix.id) {
        DynamicGridEngine.sanitizeCompleted(
            matrix = activeMatrix,
            completedCodes = DynamicCurriculumCatalog.loadCompletedCodesWithLegacyFallback(activeMatrix) { id ->
                repository.getDynamicGridCompletedCodes(id)
            }
        )
    }
    val summary = remember(activeMatrix, completedCodes) {
        DynamicGridEngine.summarize(activeMatrix, completedCodes)
    }
    val coursesByPeriod = remember(activeMatrix) {
        activeMatrix.courses.groupBy { it.period }
    }

    val handleCourseClick: (DynamicCourseNode) -> Unit = { node ->
        val result = DynamicGridEngine.toggleCourse(
            matrix = activeMatrix,
            courseCode = node.code,
            currentCompleted = completedCodes
        )
        when (result.action) {
            DynamicGridToggleAction.MARKED_COMPLETED -> {
                repository.saveDynamicGridCompletedCodes(activeMatrix.id, result.updatedCompletedCodes)
                feedbackState = null
            }
            DynamicGridToggleAction.UNMARKED_WITH_CASCADE -> {
                repository.saveDynamicGridCompletedCodes(activeMatrix.id, result.updatedCompletedCodes)
                feedbackState = if (result.cascadeRemovedCodes.isNotEmpty()) {
                    DynamicGridFeedbackState(
                        message = "Desmarcada com ${result.cascadeRemovedCodes.size} dependente(s): ${result.cascadeRemovedCodes.joinToString(", ")}",
                        isBlockedWarning = false
                    )
                } else {
                    null
                }
            }
            DynamicGridToggleAction.BLOCKED_BY_PREREQUISITES -> {
                val missingDetails = buildList {
                    if (result.missingPrerequisites.isNotEmpty()) {
                        add(result.missingPrerequisites.joinToString(", "))
                    }
                    if (result.missingHours > 0) {
                        add("${result.missingHours}h mínimas pendentes")
                    }
                }.joinToString(" e ").ifBlank { "pré-requisitos pendentes" }
                feedbackState = DynamicGridFeedbackState(
                    message = "Bloqueada · Conclua primeiro: $missingDetails",
                    isBlockedWarning = true
                )
            }
            DynamicGridToggleAction.NO_CHANGE -> Unit
        }
    }

    val handleToggleOrientation: (Boolean) -> Unit = { horizontal ->
        if (isHorizontal != horizontal) {
            isHorizontal = horizontal
            CampusRepositoryHolder.saveDynamicGridIsHorizontal(context, horizontal)
        }
    }

    val handleConfirmClear: () -> Unit = {
        repository.saveDynamicGridCompletedCodes(activeMatrix.id, emptySet())
        feedbackState = null
        showClearConfirmDialog = false
    }

    val controlHeight = if (a11y.largeTouchTargets) 48.dp else 40.dp
    val autoMatchNote = if (activeMatrix.id == autoResolution.matrix.id) {
        autoResolution.note
    } else {
        null
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            if (isHorizontal) {
                DynamicGridLandscapeToolbar(
                    matrix = activeMatrix,
                    summary = summary,
                    isHorizontal = isHorizontal,
                    isCollapsed = isHeaderCollapsed,
                    controlHeight = controlHeight,
                    isDark = isDark,
                    highContrast = a11y.highContrastMode,
                    reduceMotion = a11y.reduceMotion,
                    autoMatchNote = autoMatchNote,
                    isAutoCourseMatched = autoResolution.isCourseMatched,
                    onBack = handleDismiss,
                    onToggleCollapsed = { isHeaderCollapsed = !isHeaderCollapsed },
                    onOpenMatrixPicker = { showMatrixPickerDialog = true },
                    onSelectOrientation = handleToggleOrientation,
                    onRequestClear = { showClearConfirmDialog = true }
                )
            } else {
                DynamicGridPortraitTopBar(
                    onBack = handleDismiss
                )

                DynamicGridSummaryCard(
                    matrix = activeMatrix,
                    summary = summary,
                    isHorizontal = isHorizontal,
                    isCollapsed = isHeaderCollapsed,
                    onToggleCollapsed = { isHeaderCollapsed = !isHeaderCollapsed },
                    onOpenMatrixPicker = { showMatrixPickerDialog = true },
                    onSelectOrientation = handleToggleOrientation,
                    onRequestClear = { showClearConfirmDialog = true },
                    controlHeight = controlHeight,
                    autoMatchNote = autoMatchNote,
                    isAutoCourseMatched = autoResolution.isCourseMatched,
                    isDark = isDark,
                    largeTouchTargets = a11y.largeTouchTargets,
                    highContrast = a11y.highContrastMode,
                    reduceMotion = a11y.reduceMotion,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            feedbackState?.let { banner ->
                DynamicGridFeedbackBanner(
                    state = banner,
                    isDark = isDark,
                    onDismiss = { feedbackState = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            DynamicGridScrollableBoard(
                matrix = activeMatrix,
                coursesByPeriod = coursesByPeriod,
                completedCodes = completedCodes,
                isHorizontal = isHorizontal,
                isDark = isDark,
                highContrast = a11y.highContrastMode,
                fontScaleMultiplier = a11y.fontScaleMultiplier,
                bottomContentPadding = if (isHorizontal) 20.dp else 28.dp,
                onCourseClick = handleCourseClick,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }

    if (showClearConfirmDialog) {
        DynamicGridClearConfirmDialog(
            completedCount = summary.completedCount,
            completedHours = summary.completedHours,
            courseName = activeMatrix.courseName,
            isDark = isDark,
            onConfirm = handleConfirmClear,
            onDismiss = { showClearConfirmDialog = false }
        )
    }

    if (showMatrixPickerDialog) {
        DynamicMatrixPickerDialog(
            availableMatrices = availableMatrices,
            selectedMatrixId = activeMatrix.id,
            autoMatchedMatrixId = autoResolution.matrix.id.takeIf { autoResolution.isCourseMatched },
            highContrast = a11y.highContrastMode,
            onDismiss = { showMatrixPickerDialog = false },
            onSelectMatrix = { chosen ->
                selectedMatrixId = chosen.id
                showMatrixPickerDialog = false
                feedbackState = null
            }
        )
    }
}
