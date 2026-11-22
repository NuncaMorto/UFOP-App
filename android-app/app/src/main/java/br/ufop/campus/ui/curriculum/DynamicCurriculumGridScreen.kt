package br.ufop.campus.ui.curriculum

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.ufop.campus.core.curriculum.DynamicCourseNode
import br.ufop.campus.core.curriculum.DynamicCourseStatus
import br.ufop.campus.core.curriculum.DynamicCurriculumCatalog
import br.ufop.campus.core.curriculum.DynamicCurriculumMatrix
import br.ufop.campus.core.curriculum.DynamicGridEngine
import br.ufop.campus.core.curriculum.DynamicGridSummary
import br.ufop.campus.core.curriculum.DynamicGridToggleAction
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.ui.theme.DarkStatusAttendingGreen
import br.ufop.campus.ui.theme.DarkTextPrimary
import br.ufop.campus.ui.theme.DepartmentMathBlue
import br.ufop.campus.ui.theme.DarkDepartmentBlueText
import br.ufop.campus.ui.theme.HighContrastDarkTextPrimary
import br.ufop.campus.ui.theme.HighContrastLightTextPrimary
import br.ufop.campus.ui.theme.LightTextPrimary
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.accessibleTextColor

private data class DynamicGridFeedbackState(
    val message: String,
    val isBlockedWarning: Boolean
)

private data class DynamicCardPalette(
    val container: Color,
    val border: Color,
    val accent: Color,
    val titleColor: Color,
    val badgeBg: Color
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun dynamicCardPaletteFor(
    status: DynamicCourseStatus,
    isDark: Boolean,
    highContrast: Boolean
): DynamicCardPalette {
    val titleColor = when {
        isDark && highContrast -> HighContrastDarkTextPrimary
        !isDark && highContrast -> HighContrastLightTextPrimary
        isDark -> DarkTextPrimary
        else -> LightTextPrimary
    }

    return when (status) {
        DynamicCourseStatus.COMPLETED -> {
            val container = if (isDark) Color(0xFF173320) else Color(0xFFE0F2E2)
            val baseAccent = if (isDark) DarkStatusAttendingGreen else StatusAttendingGreen
            val accent = accessibleTextColor(baseAccent, isDark, container)
            val border = if (highContrast) accent else accent.copy(alpha = 0.72f)
            DynamicCardPalette(
                container = container,
                border = border,
                accent = accent,
                titleColor = titleColor,
                badgeBg = accent.copy(alpha = if (isDark) 0.22f else 0.14f)
            )
        }
        DynamicCourseStatus.AVAILABLE -> {
            val container = if (isDark) Color(0xFF19273D) else Color(0xFFE3EEFD)
            val baseAccent = if (isDark) DarkDepartmentBlueText else DepartmentMathBlue
            val accent = accessibleTextColor(baseAccent, isDark, container)
            val border = if (highContrast) accent else accent.copy(alpha = 0.65f)
            DynamicCardPalette(
                container = container,
                border = border,
                accent = accent,
                titleColor = titleColor,
                badgeBg = accent.copy(alpha = if (isDark) 0.22f else 0.13f)
            )
        }
        DynamicCourseStatus.LOCKED -> {
            val container = if (isDark) Color(0xFF391F1C) else Color(0xFFFCE5E0)
            val baseCoral = if (isDark) Color(0xFFFFA99B) else Color(0xFFC44D3A)
            val accent = accessibleTextColor(baseCoral, isDark, container)
            val border = if (highContrast) accent else accent.copy(alpha = 0.48f)
            DynamicCardPalette(
                container = container,
                border = border,
                accent = accent,
                titleColor = titleColor,
                badgeBg = accent.copy(alpha = if (isDark) 0.22f else 0.12f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DynamicCurriculumGridScreen(
    repository: CampusCoreRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = LocalCampusThemeController.current.isDark
    val a11y = LocalCampusAccessibility.current.settings
    val mutationVersion by repository.mutationVersion.collectAsState()

    var selectedMatrixId by rememberSaveable {
        mutableStateOf(DynamicCurriculumCatalog.defaultMatrix.id)
    }
    val activeMatrix = remember(selectedMatrixId) {
        DynamicCurriculumCatalog.findById(selectedMatrixId) ?: DynamicCurriculumCatalog.defaultMatrix
    }

    var isHorizontal by rememberSaveable {
        mutableStateOf(CampusRepositoryHolder.loadDynamicGridIsHorizontal(context))
    }
    var isFullScreen by rememberSaveable { mutableStateOf(false) }
    var isHeaderCollapsed by rememberSaveable { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showMatrixDropdown by remember { mutableStateOf(false) }
    var feedbackState by remember { mutableStateOf<DynamicGridFeedbackState?>(null) }

    val completedCodes = remember(mutationVersion, activeMatrix.id) {
        DynamicGridEngine.sanitizeCompleted(
            matrix = activeMatrix,
            completedCodes = repository.getDynamicGridCompletedCodes(activeMatrix.id)
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
                        add("${result.missingHours}h pendentes")
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        DynamicGridSummaryCard(
            matrix = activeMatrix,
            availableMatrices = DynamicCurriculumCatalog.allMatrices,
            summary = summary,
            isHorizontal = isHorizontal,
            isCollapsed = isHeaderCollapsed,
            onToggleCollapsed = { isHeaderCollapsed = !isHeaderCollapsed },
            showMatrixDropdown = showMatrixDropdown,
            onToggleMatrixDropdown = { showMatrixDropdown = it },
            onSelectMatrix = { chosen ->
                selectedMatrixId = chosen.id
                showMatrixDropdown = false
                feedbackState = null
            },
            onSelectOrientation = handleToggleOrientation,
            onRequestClear = { showClearConfirmDialog = true },
            onOpenFullScreen = { isFullScreen = true },
            isDark = isDark,
            largeTouchTargets = a11y.largeTouchTargets,
            highContrast = a11y.highContrastMode,
            reduceMotion = a11y.reduceMotion,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

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
            bottomContentPadding = 104.dp,
            onCourseClick = handleCourseClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "Limpar grade dinâmica?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Isso desmarcará todas as ${summary.completedCount} disciplina(s) concluída(s) (${summary.completedHours}h) em ${activeMatrix.courseName}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = handleConfirmClear,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = accessibleTextColor(StatusAbsentRed, isDark)
                    )
                ) {
                    Text(
                        text = "Limpar",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (isFullScreen) {
        Dialog(
            onDismissRequest = { isFullScreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            DynamicGridFullScreenDialogContent(
                matrix = activeMatrix,
                coursesByPeriod = coursesByPeriod,
                completedCodes = completedCodes,
                summary = summary,
                isHorizontal = isHorizontal,
                feedbackState = feedbackState,
                isDark = isDark,
                highContrast = a11y.highContrastMode,
                largeTouchTargets = a11y.largeTouchTargets,
                fontScaleMultiplier = a11y.fontScaleMultiplier,
                onSelectOrientation = handleToggleOrientation,
                onRequestClear = { showClearConfirmDialog = true },
                onDismissFeedback = { feedbackState = null },
                onCourseClick = handleCourseClick,
                onDismiss = { isFullScreen = false }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DynamicGridSummaryCard(
    matrix: DynamicCurriculumMatrix,
    availableMatrices: List<DynamicCurriculumMatrix>,
    summary: DynamicGridSummary,
    isHorizontal: Boolean,
    isCollapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    showMatrixDropdown: Boolean,
    onToggleMatrixDropdown: (Boolean) -> Unit,
    onSelectMatrix: (DynamicCurriculumMatrix) -> Unit,
    onSelectOrientation: (Boolean) -> Unit,
    onRequestClear: () -> Unit,
    onOpenFullScreen: () -> Unit,
    isDark: Boolean,
    largeTouchTargets: Boolean,
    highContrast: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val controlHeight = if (largeTouchTargets) 48.dp else 36.dp
    val progressRatio = if (summary.totalGridHours > 0) {
        (summary.completedHours.toFloat() / summary.totalGridHours.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progressColor = accessibleTextColor(
        if (isDark) DarkStatusAttendingGreen else StatusAttendingGreen,
        isDark,
        surfaceColor
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (isCollapsed) 18.dp else 22.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isCollapsed) 14.dp else 16.dp,
                    vertical = if (isCollapsed) 10.dp else 12.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (isCollapsed) 0.dp else 10.dp)
        ) {
            if (isCollapsed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${summary.completedHours}/${summary.totalGridHours}h (${summary.completionPercent}%) · ${summary.completedCount}/${summary.totalCount}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        LinearProgressIndicator(
                            progress = { progressRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = progressColor,
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                    }

                    Surface(
                        onClick = onToggleCollapsed,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .semantics {
                                contentDescription = "Expandir cabeçalho e controles da grade dinâmica"
                            },
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Expandir",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            onClick = { onToggleMatrixDropdown(true) },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${matrix.courseName} · ${matrix.curriculumLabel}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Selecionar matriz curricular",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showMatrixDropdown,
                            onDismissRequest = { onToggleMatrixDropdown(false) },
                            shape = RoundedCornerShape(20.dp),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            availableMatrices.forEach { item ->
                                val isSelected = item.id == matrix.id
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = item.courseName,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                            Text(
                                                text = "${item.curriculumLabel} · ${item.courses.size} disciplinas (${item.totalGridHours}h)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    trailingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Matriz ativa",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    onClick = { onSelectMatrix(item) }
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = onToggleCollapsed,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .semantics {
                                contentDescription = "Recolher controles para ampliar espaço da grade dinâmica"
                            },
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Recolher",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !isCollapsed,
                enter = if (reduceMotion) EnterTransition.None else expandVertically() + fadeIn(),
                exit = if (reduceMotion) ExitTransition.None else shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${summary.completedHours}/${summary.totalGridHours}h (${summary.completionPercent}%) · ${summary.completedCount}/${summary.totalCount} disciplinas",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LinearProgressIndicator(
                            progress = { progressRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = progressColor,
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DynamicGridOrientationSegmentedPill(
                            isHorizontal = isHorizontal,
                            controlHeight = controlHeight,
                            onSelectOrientation = onSelectOrientation
                        )

                        OutlinedButton(
                            onClick = onRequestClear,
                            enabled = summary.completedCount > 0,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .defaultMinSize(minHeight = controlHeight),
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Limpar",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Surface(
                            onClick = onOpenFullScreen,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .defaultMinSize(minHeight = controlHeight),
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Fullscreen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Tela cheia",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    DynamicGridLegendRow(
                        isDark = isDark,
                        highContrast = highContrast
                    )
                }
            }
        }
    }
}

@Composable
private fun DynamicGridOrientationSegmentedPill(
    isHorizontal: Boolean,
    controlHeight: androidx.compose.ui.unit.Dp,
    onSelectOrientation: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            OrientationSegmentChip(
                label = "Horizontal",
                selected = isHorizontal,
                controlHeight = controlHeight,
                onClick = { onSelectOrientation(true) }
            )
            OrientationSegmentChip(
                label = "Vertical",
                selected = !isHorizontal,
                controlHeight = controlHeight,
                onClick = { onSelectOrientation(false) }
            )
        }
    }
}

@Composable
private fun OrientationSegmentChip(
    label: String,
    selected: Boolean,
    controlHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = (controlHeight - 4.dp).coerceAtLeast(32.dp))
            .semantics {
                this.role = Role.RadioButton
                this.selected = selected
            },
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DynamicGridLegendRow(
    isDark: Boolean,
    highContrast: Boolean
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DynamicLegendChip(
            label = "Concluída",
            palette = dynamicCardPaletteFor(DynamicCourseStatus.COMPLETED, isDark, highContrast)
        )
        DynamicLegendChip(
            label = "Disponível",
            palette = dynamicCardPaletteFor(DynamicCourseStatus.AVAILABLE, isDark, highContrast)
        )
        DynamicLegendChip(
            label = "Bloqueada",
            palette = dynamicCardPaletteFor(DynamicCourseStatus.LOCKED, isDark, highContrast)
        )
    }
}

@Composable
private fun DynamicLegendChip(
    label: String,
    palette: DynamicCardPalette
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = palette.container,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(palette.accent)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.titleColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun DynamicGridFeedbackBanner(
    state: DynamicGridFeedbackState,
    isDark: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (state.isBlockedWarning) {
        if (isDark) Color(0xFF3A201D) else Color(0xFFFCE6E2)
    } else {
        if (isDark) Color(0xFF1D293A) else Color(0xFFE7EFFC)
    }
    val rawAccent = if (state.isBlockedWarning) {
        if (isDark) Color(0xFFFFA99B) else Color(0xFFB74130)
    } else {
        if (isDark) DarkDepartmentBlueText else DepartmentMathBlue
    }
    val textAccent = accessibleTextColor(rawAccent, isDark, container)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = BorderStroke(1.dp, textAccent.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = textAccent,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fechar aviso",
                    tint = textAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DynamicGridScrollableBoard(
    matrix: DynamicCurriculumMatrix,
    coursesByPeriod: Map<Int, List<DynamicCourseNode>>,
    completedCodes: Set<String>,
    isHorizontal: Boolean,
    isDark: Boolean,
    highContrast: Boolean,
    fontScaleMultiplier: Float,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    onCourseClick: (DynamicCourseNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    Box(
        modifier = modifier
            .clipToBounds()
            .verticalScroll(verticalScrollState)
    ) {
        Box(
            modifier = Modifier
                .horizontalScroll(horizontalScrollState)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomContentPadding)
        ) {
            if (isHorizontal) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    for (period in 1..matrix.totalPeriods) {
                        val periodCourses = coursesByPeriod[period].orEmpty()
                        val doneInPeriod = periodCourses.count { it.code.uppercase() in completedCodes }
                        Column(
                            modifier = Modifier.width(136.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PeriodHeaderCard(
                                period = period,
                                completedCount = doneInPeriod,
                                totalCount = periodCourses.size,
                                isDark = isDark,
                                modifier = Modifier.fillMaxWidth()
                            )
                            periodCourses.forEach { course ->
                                val status = DynamicGridEngine.statusOf(matrix, course.code, completedCodes)
                                val missing = if (status == DynamicCourseStatus.LOCKED) {
                                    DynamicGridEngine.missingPrerequisites(matrix, course.code, completedCodes)
                                } else {
                                    emptyList()
                                }
                                DynamicCourseCard(
                                    course = course,
                                    status = status,
                                    missingPrerequisites = missing,
                                    isDark = isDark,
                                    highContrast = highContrast,
                                    fontScaleMultiplier = fontScaleMultiplier,
                                    onClick = { onCourseClick(course) }
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (period in 1..matrix.totalPeriods) {
                        val periodCourses = coursesByPeriod[period].orEmpty()
                        val doneInPeriod = periodCourses.count { it.code.uppercase() in completedCodes }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PeriodRowBadgeCard(
                                period = period,
                                completedCount = doneInPeriod,
                                totalCount = periodCourses.size,
                                totalHours = periodCourses.sumOf { it.workloadHours },
                                isDark = isDark
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                periodCourses.forEach { course ->
                                    val status = DynamicGridEngine.statusOf(matrix, course.code, completedCodes)
                                    val missing = if (status == DynamicCourseStatus.LOCKED) {
                                        DynamicGridEngine.missingPrerequisites(matrix, course.code, completedCodes)
                                    } else {
                                        emptyList()
                                    }
                                    DynamicCourseCard(
                                        course = course,
                                        status = status,
                                        missingPrerequisites = missing,
                                        isDark = isDark,
                                        highContrast = highContrast,
                                        fontScaleMultiplier = fontScaleMultiplier,
                                        onClick = { onCourseClick(course) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodHeaderCard(
    period: Int,
    completedCount: Int,
    totalCount: Int,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val allDone = totalCount > 0 && completedCount == totalCount
    val container = if (allDone) {
        if (isDark) Color(0xFF173320) else Color(0xFFE0F2E2)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val borderColor = if (allDone) {
        accessibleTextColor(
            if (isDark) DarkStatusAttendingGreen else StatusAttendingGreen,
            isDark,
            container
        ).copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${period}º Período",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$completedCount/$totalCount",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PeriodRowBadgeCard(
    period: Int,
    completedCount: Int,
    totalCount: Int,
    totalHours: Int,
    isDark: Boolean
) {
    val allDone = totalCount > 0 && completedCount == totalCount
    val container = if (allDone) {
        if (isDark) Color(0xFF173320) else Color(0xFFE0F2E2)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val borderColor = if (allDone) {
        accessibleTextColor(
            if (isDark) DarkStatusAttendingGreen else StatusAttendingGreen,
            isDark,
            container
        ).copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = Modifier
            .width(92.dp)
            .heightIn(min = 114.dp),
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${period}º Período",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "$completedCount/$totalCount",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${totalHours}h",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DynamicCourseCard(
    course: DynamicCourseNode,
    status: DynamicCourseStatus,
    missingPrerequisites: List<String>,
    isDark: Boolean,
    highContrast: Boolean,
    fontScaleMultiplier: Float,
    onClick: () -> Unit
) {
    val palette = dynamicCardPaletteFor(status = status, isDark = isDark, highContrast = highContrast)
    val statusDescription = when (status) {
        DynamicCourseStatus.COMPLETED -> "Concluída. Toque para desmarcar"
        DynamicCourseStatus.AVAILABLE -> "Disponível. Toque para marcar como concluída"
        DynamicCourseStatus.LOCKED -> {
            val missing = missingPrerequisites.joinToString(", ")
            if (missing.isNotBlank()) "Bloqueada. Pré-requisitos pendentes: $missing" else "Bloqueada"
        }
    }
    val electiveText = if (course.isElective) ", eletiva" else ""
    val a11yDescription =
        "${course.code}, ${course.name}, ${course.workloadHours} horas, ${course.period}º período$electiveText. $statusDescription"
    val clickActionLabel = when (status) {
        DynamicCourseStatus.COMPLETED -> "Desmarcar ${course.code}"
        DynamicCourseStatus.AVAILABLE -> "Marcar ${course.code} como concluída"
        DynamicCourseStatus.LOCKED -> "Ver pré-requisitos de ${course.code}"
    }

    val sizeModifier = if (fontScaleMultiplier > 1.05f) {
        Modifier
            .width(136.dp)
            .heightIn(min = 114.dp)
    } else {
        Modifier
            .width(136.dp)
            .height(114.dp)
    }

    Surface(
        modifier = sizeModifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = clickActionLabel, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = a11yDescription
                onClick(label = clickActionLabel) {
                    onClick()
                    true
                }
            },
        shape = RoundedCornerShape(16.dp),
        color = palette.container,
        border = BorderStroke(if (status == DynamicCourseStatus.COMPLETED || highContrast) 1.5.dp else 1.dp, palette.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = course.code,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.accent,
                    maxLines = 1,
                    softWrap = false
                )
                Icon(
                    imageVector = when (status) {
                        DynamicCourseStatus.COMPLETED -> Icons.Filled.CheckCircle
                        DynamicCourseStatus.AVAILABLE -> Icons.Outlined.CheckCircleOutline
                        DynamicCourseStatus.LOCKED -> Icons.Rounded.Lock
                    },
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = course.name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp
                ),
                fontWeight = FontWeight.SemiBold,
                color = palette.titleColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = palette.badgeBg
                ) {
                    Text(
                        text = "${course.workloadHours}h",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                if (course.isElective) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.35f else 0.72f)
                    ) {
                        Text(
                            text = "Eletiva",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.titleColor,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DynamicGridFullScreenDialogContent(
    matrix: DynamicCurriculumMatrix,
    coursesByPeriod: Map<Int, List<DynamicCourseNode>>,
    completedCodes: Set<String>,
    summary: DynamicGridSummary,
    isHorizontal: Boolean,
    feedbackState: DynamicGridFeedbackState?,
    isDark: Boolean,
    highContrast: Boolean,
    largeTouchTargets: Boolean,
    fontScaleMultiplier: Float,
    onSelectOrientation: (Boolean) -> Unit,
    onRequestClear: () -> Unit,
    onDismissFeedback: () -> Unit,
    onCourseClick: (DynamicCourseNode) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val controlHeight = if (largeTouchTargets) 48.dp else 36.dp
    val progressColor = accessibleTextColor(
        if (isDark) DarkStatusAttendingGreen else StatusAttendingGreen,
        isDark,
        MaterialTheme.colorScheme.surface
    )
    val progressRatio = if (summary.totalGridHours > 0) {
        (summary.completedHours.toFloat() / summary.totalGridHours.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    LaunchedEffect(context) {
        context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    DisposableEffect(context) {
        val activity = context.findActivity()
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val handleDismiss = {
        context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDismiss()
    }

    BackHandler(onBack = handleDismiss)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Grade Dinâmica · ${matrix.courseName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${matrix.curriculumLabel} · ${summary.completedHours}/${summary.totalGridHours}h (${summary.completionPercent}%) · ${summary.completedCount}/${summary.totalCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        DynamicGridOrientationSegmentedPill(
                            isHorizontal = isHorizontal,
                            controlHeight = controlHeight,
                            onSelectOrientation = onSelectOrientation
                        )

                        OutlinedButton(
                            onClick = onRequestClear,
                            enabled = summary.completedCount > 0,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .defaultMinSize(minHeight = controlHeight),
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Limpar",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        IconButton(
                            onClick = handleDismiss,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Sair da tela cheia",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    DynamicGridLegendRow(
                        isDark = isDark,
                        highContrast = highContrast
                    )
                }
            }

            feedbackState?.let { banner ->
                DynamicGridFeedbackBanner(
                    state = banner,
                    isDark = isDark,
                    onDismiss = onDismissFeedback,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            DynamicGridScrollableBoard(
                matrix = matrix,
                coursesByPeriod = coursesByPeriod,
                completedCodes = completedCodes,
                isHorizontal = isHorizontal,
                isDark = isDark,
                highContrast = highContrast,
                fontScaleMultiplier = fontScaleMultiplier,
                bottomContentPadding = 24.dp,
                onCourseClick = onCourseClick,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}
