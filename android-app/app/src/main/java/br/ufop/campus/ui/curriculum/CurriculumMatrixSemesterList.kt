package br.ufop.campus.ui.curriculum

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.ui.theme.DepartmentMathBlue
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.StatusCancelledAmber
import br.ufop.campus.ui.theme.accessibleTextColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CurriculumSearchAndModeBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    groupMode: MatrixGroupMode,
    onGroupModeChange: (MatrixGroupMode) -> Unit,
    minTouchTarget: Dp
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar código, nome ou departamento...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null
                )
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.size(minTouchTarget)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Limpar busca"
                        )
                    }
                }
            } else {
                null
            },
            singleLine = true,
            shape = RoundedCornerShape(20.dp)
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            MatrixGroupMode.entries.forEachIndexed { index, mode ->
                val isSelected = groupMode == mode
                SegmentedButton(
                    selected = isSelected,
                    onClick = { onGroupModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = MatrixGroupMode.entries.size
                    ),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        inactiveContainerColor = MaterialTheme.colorScheme.surface,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    label = {
                        Text(
                            text = mode.label,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
            }
        }
    }
}

@Composable
internal fun CurriculumNoSearchResultsCard(
    searchQuery: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = "Nenhum componente encontrado para \"$searchQuery\".",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(18.dp)
        )
    }
}

@Composable
internal fun MatrixAccordionGroup(
    title: String,
    subjects: List<EvaluatedSubject>,
    isDepartmentGroup: Boolean,
    isConsultationMode: Boolean,
    isExpanded: Boolean,
    isDark: Boolean,
    reduceMotion: Boolean,
    minTouchTarget: Dp,
    onToggleExpand: () -> Unit,
    onSelectSubject: (EvaluatedSubject) -> Unit
) {
    val totalCha = remember(subjects) { subjects.sumOf { it.subject.cha } }
    val deptColor = if (isDepartmentGroup) departmentColorFor(title) else DepartmentMathBlue

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = minTouchTarget)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleExpand)
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isDepartmentGroup) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(deptColor)
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "${subjects.size} componentes · ${formatHoursPtBr(totalCha)}h",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (isExpanded) "Recolher" else "Expandir",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = if (reduceMotion) EnterTransition.None else expandVertically() + fadeIn(),
                exit = if (reduceMotion) ExitTransition.None else shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                    subjects.forEach { item ->
                        MatrixSubjectRow(
                            evaluated = item,
                            isConsultationMode = isConsultationMode,
                            isDark = isDark,
                            minTouchTarget = minTouchTarget,
                            onClick = { onSelectSubject(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MatrixSubjectRow(
    evaluated: EvaluatedSubject,
    isConsultationMode: Boolean,
    isDark: Boolean,
    minTouchTarget: Dp,
    onClick: () -> Unit
) {
    val subject = evaluated.subject
    val deptColor = departmentColorFor(subject.departmentCode)
    val accessibleDeptColor = accessibleTextColor(deptColor, isDark)
    val displaySubjectName = normalizeCurriculumDisplayName(subject.name)

    val (badgeLabel, badgeBg, rawBadgeFg) = when (evaluated.status) {
        SubjectProgressStatus.APPROVED -> Triple(
            "✓ Aprovada",
            StatusAttendingGreen.copy(alpha = 0.14f),
            StatusAttendingGreen
        )
        SubjectProgressStatus.IN_PROGRESS -> Triple(
            "● Cursando",
            DepartmentMathBlue.copy(alpha = 0.14f),
            DepartmentMathBlue
        )
        SubjectProgressStatus.AVAILABLE -> Triple(
            "Disponível",
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        SubjectProgressStatus.BLOCKED_PREREQ -> Triple(
            "Prereq",
            StatusCancelledAmber.copy(alpha = 0.14f),
            StatusCancelledAmber
        )
    }
    val badgeFg = if (evaluated.status == SubjectProgressStatus.AVAILABLE) {
        rawBadgeFg
    } else {
        accessibleTextColor(rawBadgeFg, isDark)
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minTouchTarget)
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(deptColor)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = deptColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = subject.code,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accessibleDeptColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "${subject.departmentCode} · ${subject.cha}h",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!isConsultationMode) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Text(
                text = displaySubjectName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val weeklyHoursText = "${subject.weeklyTheory} teóricas · ${subject.weeklyPractice} práticas/sem."
            if (isConsultationMode) {
                val consultInfo = buildString {
                    append(subject.type)
                    if (subject.weeklyTheory > 0 || subject.weeklyPractice > 0) {
                        append(" · $weeklyHoursText")
                    }
                    if (subject.prerequisites.isNotEmpty()) {
                        append(" · Pré-req: ${subject.prerequisites.joinToString(", ")}")
                    }
                }
                Text(
                    text = consultInfo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (evaluated.status == SubjectProgressStatus.BLOCKED_PREREQ && evaluated.missingPrerequisites.isNotEmpty()) {
                Text(
                    text = "Falta pré-req: ${evaluated.missingPrerequisites.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accessibleTextColor(StatusCancelledAmber, isDark),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (subject.weeklyTheory > 0 || subject.weeklyPractice > 0) {
                Text(
                    text = "${subject.type} · $weeklyHoursText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
