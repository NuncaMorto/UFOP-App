package br.ufop.campus.ui.activities

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.AssessmentWithCourse
import br.ufop.campus.core.ffi.DisciplineSummaryCard
import br.ufop.campus.core.ffi.extractDepartmentAcronym
import br.ufop.campus.core.ffi.formatAcademicAssessmentTitle
import br.ufop.campus.ui.components.CampusDatePickerField
import br.ufop.campus.ui.components.CampusTimePickerField
import br.ufop.campus.ui.components.defaultTodayIsoDate
import br.ufop.campus.ui.components.formatIsoToBrDate
import br.ufop.campus.ui.disciplines.normalizeAssessmentDateInput
import br.ufop.campus.ui.theme.CampusCardRadius
import br.ufop.campus.ui.theme.CampusPillRadius
import br.ufop.campus.ui.theme.CampusSheetRadius
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.campusCardBorder
import br.ufop.campus.ui.theme.parseHexColor
import br.ufop.campus.ui.theme.tabularNums

private val ASSESSMENT_KIND_OPTIONS = listOf(
    "Prova",
    "Trabalho",
    "Seminário",
    "Lista",
    "Relatório",
    "Projeto",
    "Exame"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssessmentDetailBottomSheet(
    entry: AssessmentWithCourse,
    disciplines: List<DisciplineSummaryCard>,
    onDismiss: () -> Unit,
    onToggleCompleted: (completed: Boolean) -> Unit,
    onSaveEdit: (courseId: String, title: String, kind: String, dueDate: String, dueTime: String, completed: Boolean) -> Unit,
    onDelete: () -> Unit,
    onOpenDiscipline: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = LocalCampusThemeController.current.isDark
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = if (a11y.largeTouchTargets) 56.dp else 48.dp
    val item = entry.assessment

    var isEditing by remember(item.id) { mutableStateOf(false) }
    var editCourseId by remember(item.id, item.courseId) { mutableStateOf(item.courseId) }
    var editTitle by remember(item.id, item.title) { mutableStateOf(item.title) }
    var editKind by remember(item.id, item.kind) { mutableStateOf(item.kind) }
    var editDueDate by remember(item.id, item.dueDate) {
        mutableStateOf(normalizeAssessmentDateInput(item.dueDate).ifBlank { defaultTodayIsoDate() })
    }
    var editDueTime by remember(item.id, item.dueTime) { mutableStateOf(item.dueTime) }
    var editCompleted by remember(item.id, item.completed) { mutableStateOf(item.completed) }
    var showTitleError by remember(item.id) { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember(item.id) { mutableStateOf(false) }

    val selectedCourse = remember(editCourseId, disciplines) {
        disciplines.find { it.courseId == editCourseId }
    }
    val headerAccentColor = parseHexColor(
        if (isEditing && selectedCourse != null) selectedCourse.colorHex else entry.colorHex
    )
    val accessibleHeaderAccent = remember(headerAccentColor, isDark) {
        accessibleTextColor(headerAccentColor, isDark)
    }
    val rawCourseName = if (isEditing && selectedCourse != null) selectedCourse.name else entry.courseName
    val rawDeptAcronym = extractDepartmentAcronym(
        if (isEditing && selectedCourse != null) selectedCourse.departmentName else entry.departmentName
    )
    val headerCourseLabel = if (rawDeptAcronym.isNotEmpty()) {
        "$rawCourseName · $rawDeptAcronym"
    } else {
        rawCourseName
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Excluir avaliação?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Deseja excluir \"${item.title}\"? Você poderá desfazer essa ação logo em seguida.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget)
                ) {
                    Text(
                        text = "Cancelar",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    },
                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusAbsentRed,
                        contentColor = Color.White
                    )
                ) {
                    Text("Excluir")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = CampusSheetRadius, topEnd = CampusSheetRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 6.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Header: Disciplina + Título + Botão Fechar (>= 48.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(headerAccentColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = headerCourseLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = accessibleHeaderAccent,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isEditing) "Editar avaliação" else formatAcademicAssessmentTitle(item.title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(minTouchTarget)
                ) {
                    Surface(
                        modifier = Modifier.size(if (a11y.largeTouchTargets) 44.dp else 36.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Fechar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedContent(
                targetState = isEditing,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "AssessmentDetailMode"
            ) { editingMode ->
                if (!editingMode) {
                    val accentColor = parseHexColor(entry.colorHex)
                    val accessibleAccent = remember(accentColor, isDark) {
                        accessibleTextColor(accentColor, isDark)
                    }
                    val accessibleGreen = remember(isDark) {
                        accessibleTextColor(StatusAttendingGreen, isDark)
                    }
                    val accessibleRed = remember(isDark) {
                        accessibleTextColor(StatusAbsentRed, isDark)
                    }
                    val isCompleted = item.completed
                    val rawBadgeColor = if (isCompleted) StatusAttendingGreen else accentColor
                    val badgeColor = if (isCompleted) accessibleGreen else accessibleAccent
                    val tagVisualHeight = if (a11y.largeTouchTargets) 40.dp else 32.dp
                    val secondaryButtonHeight = if (a11y.largeTouchTargets) minTouchTarget else 40.dp
                    val primaryButtonHeight = if (a11y.largeTouchTargets) 52.dp else 44.dp

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Card de metadados ricos
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(CampusCardRadius),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = campusCardBorder()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(width = 56.dp, height = 56.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        color = rawBadgeColor.copy(alpha = 0.14f),
                                        border = campusCardBorder(1.dp)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = item.dayNumber,
                                                style = MaterialTheme.typography.titleMedium.tabularNums(),
                                                fontWeight = FontWeight.Bold,
                                                color = badgeColor
                                            )
                                            Text(
                                                text = item.monthShort.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = badgeColor
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Event,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Data: ${formatIsoToBrDate(item.dueDate)}",
                                                style = MaterialTheme.typography.bodyMedium.tabularNums(),
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Schedule,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = if (item.dueTime.isNotBlank()) {
                                                    "Horário: ${item.dueTime}"
                                                } else {
                                                    "Horário: Não informado"
                                                },
                                                style = MaterialTheme.typography.bodyMedium.tabularNums(),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                    thickness = 1.dp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Chip do tipo de avaliação (compacto 32.dp)
                                    Surface(
                                        modifier = Modifier.heightIn(min = tagVisualHeight),
                                        shape = RoundedCornerShape(CampusPillRadius),
                                        color = accentColor.copy(alpha = 0.14f),
                                        border = campusCardBorder(1.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = item.kind,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = accessibleAccent
                                            )
                                        }
                                    }

                                    // Chip de status (pendente / concluída)
                                    Surface(
                                        modifier = Modifier.heightIn(min = tagVisualHeight),
                                        shape = RoundedCornerShape(CampusPillRadius),
                                        color = if (isCompleted) {
                                            StatusAttendingGreen.copy(alpha = 0.16f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        border = campusCardBorder(1.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .heightIn(min = tagVisualHeight)
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isCompleted) {
                                                    Icons.Default.CheckCircle
                                                } else {
                                                    Icons.Outlined.RadioButtonUnchecked
                                                },
                                                contentDescription = null,
                                                tint = if (isCompleted) {
                                                    accessibleGreen
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = if (isCompleted) "Concluída" else "Pendente",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isCompleted) {
                                                    accessibleGreen
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                }

                                if (onOpenDiscipline != null) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                        thickness = 1.dp
                                    )
                                    Surface(
                                        onClick = onOpenDiscipline,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = minTouchTarget),
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                                contentDescription = null,
                                                tint = accessibleAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Ver disciplina",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = accessibleAccent,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Ações secundárias limpas e neutras: Editar + Excluir (sem conflito vermelho)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    editCourseId = item.courseId
                                    editTitle = item.title
                                    editKind = item.kind
                                    editDueDate = normalizeAssessmentDateInput(item.dueDate)
                                        .ifBlank { defaultTodayIsoDate() }
                                    editDueTime = item.dueTime
                                    editCompleted = item.completed
                                    showTitleError = false
                                    isEditing = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = secondaryButtonHeight),
                                shape = RoundedCornerShape(CampusPillRadius),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Editar",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            FilledTonalButton(
                                onClick = { showDeleteConfirmDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = secondaryButtonHeight),
                                shape = RoundedCornerShape(CampusPillRadius),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = accessibleRed
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = accessibleRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Excluir",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = accessibleRed,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Único CTA primário claro para concluir / reabrir
                        Button(
                            onClick = {
                                onToggleCompleted(!isCompleted)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = primaryButtonHeight),
                            shape = RoundedCornerShape(CampusPillRadius),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = if (isCompleted) "Reabrir atividade" else "Marcar como concluída",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = {
                                editTitle = it
                                if (it.isNotBlank()) showTitleError = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Título da avaliação") },
                            placeholder = { Text("Ex.: Prova 1 de Cálculo") },
                            isError = showTitleError,
                            supportingText = if (showTitleError) {
                                { Text("Informe o título da avaliação.") }
                            } else {
                                null
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Tipo",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ASSESSMENT_KIND_OPTIONS.forEach { option ->
                                    val selected = editKind.equals(option, ignoreCase = true)
                                    FilterChip(
                                        selected = selected,
                                        onClick = { editKind = option },
                                        modifier = Modifier.heightIn(min = if (a11y.largeTouchTargets) minTouchTarget else 36.dp),
                                        label = { Text(option) },
                                        shape = RoundedCornerShape(22.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = headerAccentColor.copy(alpha = 0.18f),
                                            selectedLabelColor = accessibleHeaderAccent
                                        )
                                    )
                                }
                            }
                        }

                        if (disciplines.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Disciplina",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    disciplines.forEach { course ->
                                        val isSelected = editCourseId == course.courseId
                                        val courseColor = parseHexColor(course.colorHex)
                                        val accessibleCourseColor = accessibleTextColor(courseColor, isDark)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { editCourseId = course.courseId },
                                            modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                                            leadingIcon = {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(courseColor)
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = course.name,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            shape = RoundedCornerShape(22.dp),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = courseColor.copy(alpha = 0.16f),
                                                selectedLabelColor = accessibleCourseColor
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        CampusDatePickerField(
                            label = "Data",
                            isoDate = editDueDate,
                            onIsoDateSelected = { editDueDate = it },
                            minHeight = minTouchTarget
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CampusTimePickerField(
                                label = "Horário (opcional)",
                                time24h = editDueTime,
                                onTimeSelected = { editDueTime = it },
                                minHeight = minTouchTarget
                            )
                            if (editDueTime.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { editDueTime = "" },
                                        modifier = Modifier.defaultMinSize(minHeight = minTouchTarget)
                                    ) {
                                        Text(
                                            text = "Limpar horário",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Surface(
                            onClick = { editCompleted = !editCompleted },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = minTouchTarget),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Marcar como concluída",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Switch(
                                    checked = editCompleted,
                                    onCheckedChange = { editCompleted = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                        checkedBorderColor = MaterialTheme.colorScheme.primary,
                                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        uncheckedTrackColor = if (isDark) {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
                                        },
                                        uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isEditing = false
                                    showTitleError = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = minTouchTarget.coerceAtLeast(50.dp)),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text("Cancelar")
                            }

                            Button(
                                onClick = {
                                    val cleanTitle = editTitle.trim()
                                    if (cleanTitle.isEmpty()) {
                                        showTitleError = true
                                        return@Button
                                    }
                                    val normalizedDate = normalizeAssessmentDateInput(editDueDate)
                                    val cleanTime = editDueTime.trim()
                                    onSaveEdit(
                                        editCourseId,
                                        cleanTitle,
                                        editKind.trim().ifBlank { "Avaliação" },
                                        normalizedDate,
                                        cleanTime,
                                        editCompleted
                                    )
                                    isEditing = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = minTouchTarget.coerceAtLeast(50.dp)),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = "Salvar alterações",
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
