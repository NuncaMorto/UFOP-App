package br.ufop.campus.ui.activities

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.AssessmentWithCourse
import br.ufop.campus.core.ffi.DisciplineSummaryCard
import br.ufop.campus.ui.theme.parseHexColor

internal fun formatActivityTypeFilterLabel(
    selectedTypes: Set<String>,
    onlyCompleted: Boolean
): String {
    return when {
        selectedTypes.isEmpty() && !onlyCompleted -> "Todos os tipos"
        selectedTypes.isEmpty() && onlyCompleted -> "Concluídas"
        selectedTypes.size == 1 && !onlyCompleted -> when (selectedTypes.first()) {
            "Prova" -> "Provas"
            "Trabalho" -> "Trabalhos"
            "Seminário" -> "Seminários"
            else -> selectedTypes.first()
        }
        selectedTypes.size >= 2 && !onlyCompleted -> "${selectedTypes.size} tipos"
        else -> "${selectedTypes.size + 1} filtros"
    }
}

internal fun filterActivityEntries(
    entries: List<AssessmentWithCourse>,
    selectedTypes: Set<String>,
    onlyCompleted: Boolean,
    selectedCourseId: String?
): List<AssessmentWithCourse> {
    return entries.filter { entry ->
        val assessment = entry.assessment
        val matchesType = selectedTypes.isEmpty() || selectedTypes.any { type ->
            when (type) {
                "Prova" -> assessment.kind.contains("Prova", ignoreCase = true)
                "Trabalho" -> assessment.kind.contains("Trabalho", ignoreCase = true)
                "Seminário" -> assessment.kind.contains("Semin", ignoreCase = true)
                else -> assessment.kind.contains(type, ignoreCase = true)
            }
        }
        val matchesCompleted = !onlyCompleted || assessment.completed
        val matchesCourse = selectedCourseId == null || assessment.courseId == selectedCourseId
        matchesType && matchesCompleted && matchesCourse
    }
}

@Composable
internal fun ActivitiesFilterBar(
    typeFilterLabel: String,
    hasActiveTypeFilter: Boolean,
    selectedTypes: Set<String>,
    onlyCompleted: Boolean,
    showTypeFilterDropdown: Boolean,
    onShowTypeFilterDropdownChange: (Boolean) -> Unit,
    onSelectAllTypes: () -> Unit,
    onToggleTypeFilter: (String) -> Unit,
    onToggleOnlyCompleted: () -> Unit,
    disciplines: List<DisciplineSummaryCard>,
    selectedFilterCourseId: String?,
    selectedCourseLabel: String,
    showDisciplineDropdown: Boolean,
    onShowDisciplineDropdownChange: (Boolean) -> Unit,
    onSelectCourseId: (String?) -> Unit,
    largeTouchTargets: Boolean,
    minTouchTarget: Dp
) {
    val dropdownVisualHeight = if (largeTouchTargets) minTouchTarget else 35.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActivityTypeFilterDropdown(
            typeFilterLabel = typeFilterLabel,
            hasActiveTypeFilter = hasActiveTypeFilter,
            selectedTypes = selectedTypes,
            onlyCompleted = onlyCompleted,
            expanded = showTypeFilterDropdown,
            onExpandedChange = onShowTypeFilterDropdownChange,
            onSelectAllTypes = onSelectAllTypes,
            onToggleTypeFilter = onToggleTypeFilter,
            onToggleOnlyCompleted = onToggleOnlyCompleted,
            largeTouchTargets = largeTouchTargets,
            minTouchTarget = minTouchTarget,
            dropdownVisualHeight = dropdownVisualHeight
        )

        if (disciplines.isNotEmpty()) {
            ActivityDisciplineFilterDropdown(
                modifier = Modifier.weight(1f, fill = false),
                disciplines = disciplines,
                selectedFilterCourseId = selectedFilterCourseId,
                selectedCourseLabel = selectedCourseLabel,
                expanded = showDisciplineDropdown,
                onExpandedChange = onShowDisciplineDropdownChange,
                onSelectCourseId = onSelectCourseId,
                largeTouchTargets = largeTouchTargets,
                minTouchTarget = minTouchTarget,
                dropdownVisualHeight = dropdownVisualHeight
            )
        }
    }
}

@Composable
private fun ActivityTypeFilterDropdown(
    typeFilterLabel: String,
    hasActiveTypeFilter: Boolean,
    selectedTypes: Set<String>,
    onlyCompleted: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectAllTypes: () -> Unit,
    onToggleTypeFilter: (String) -> Unit,
    onToggleOnlyCompleted: () -> Unit,
    largeTouchTargets: Boolean,
    minTouchTarget: Dp,
    dropdownVisualHeight: Dp
) {
    Box {
        Surface(
            onClick = { onExpandedChange(true) },
            modifier = if (largeTouchTargets) {
                Modifier.defaultMinSize(minHeight = minTouchTarget)
            } else {
                Modifier.minimumInteractiveComponentSize()
            },
            shape = RoundedCornerShape(22.dp),
            color = if (hasActiveTypeFilter) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            border = BorderStroke(
                width = 1.dp,
                color = if (hasActiveTypeFilter) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = dropdownVisualHeight)
                    .padding(
                        horizontal = 12.dp,
                        vertical = if (largeTouchTargets) 10.dp else 6.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = typeFilterLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (hasActiveTypeFilter) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (hasActiveTypeFilter) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Filtrar por tipo de atividade",
                    modifier = Modifier.size(18.dp),
                    tint = if (hasActiveTypeFilter) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            shape = RoundedCornerShape(22.dp)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Todos os tipos",
                        fontWeight = if (!hasActiveTypeFilter) FontWeight.Bold else FontWeight.Normal
                    )
                },
                trailingIcon = if (!hasActiveTypeFilter) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null,
                onClick = onSelectAllTypes
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            listOf(
                "Provas" to "Prova",
                "Trabalhos" to "Trabalho",
                "Seminários" to "Seminário"
            ).forEach { (label, typeKey) ->
                val isSelected = typeKey in selectedTypes
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isSelected) {
                                Icons.Default.CheckBox
                            } else {
                                Icons.Default.CheckBoxOutlineBlank
                            },
                            contentDescription = null,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = { onToggleTypeFilter(typeKey) }
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = "Somente concluídas",
                        fontWeight = if (onlyCompleted) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (onlyCompleted) {
                            Icons.Default.CheckBox
                        } else {
                            Icons.Default.CheckBoxOutlineBlank
                        },
                        contentDescription = null,
                        tint = if (onlyCompleted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = onToggleOnlyCompleted
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasActiveTypeFilter) {
                    TextButton(onClick = onSelectAllTypes) {
                        Text(
                            text = "Limpar",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = { onExpandedChange(false) }) {
                    Text(
                        text = "Aplicar",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityDisciplineFilterDropdown(
    disciplines: List<DisciplineSummaryCard>,
    selectedFilterCourseId: String?,
    selectedCourseLabel: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectCourseId: (String?) -> Unit,
    largeTouchTargets: Boolean,
    minTouchTarget: Dp,
    dropdownVisualHeight: Dp,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Surface(
            onClick = { onExpandedChange(true) },
            modifier = if (largeTouchTargets) {
                Modifier.defaultMinSize(minHeight = minTouchTarget)
            } else {
                Modifier.minimumInteractiveComponentSize()
            },
            shape = RoundedCornerShape(22.dp),
            color = if (selectedFilterCourseId != null) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            border = BorderStroke(
                width = 1.dp,
                color = if (selectedFilterCourseId != null) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = dropdownVisualHeight)
                    .padding(
                        horizontal = 12.dp,
                        vertical = if (largeTouchTargets) 10.dp else 6.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = selectedCourseLabel,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selectedFilterCourseId != null) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selectedFilterCourseId != null) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Filtrar por disciplina",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            shape = RoundedCornerShape(22.dp)
        ) {
            DropdownMenuItem(
                text = { Text("Todas as disciplinas") },
                onClick = { onSelectCourseId(null) },
                trailingIcon = if (selectedFilterCourseId == null) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null
            )
            disciplines.forEach { course ->
                val isSelected = selectedFilterCourseId == course.courseId
                DropdownMenuItem(
                    text = {
                        Text(
                            text = course.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(course.colorHex))
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    onClick = { onSelectCourseId(course.courseId) }
                )
            }
        }
    }
}
