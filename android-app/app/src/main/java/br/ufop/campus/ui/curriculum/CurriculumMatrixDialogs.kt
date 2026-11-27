package br.ufop.campus.ui.curriculum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.curriculum.UfopCourseCurriculum
import br.ufop.campus.core.curriculum.UfopCurriculumCatalog
import br.ufop.campus.core.curriculum.UfopCurriculumSubject
import br.ufop.campus.ui.theme.DepartmentMathBlue
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.StatusCancelledAmber
import br.ufop.campus.ui.theme.accessibleTextColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SubjectDetailDialog(
    evaluated: EvaluatedSubject,
    approvedCodes: Set<String>,
    activeSemester: String,
    isConsultationMode: Boolean,
    isDark: Boolean,
    minTouchTarget: Dp,
    onDismiss: () -> Unit,
    onAddToActiveSemester: (UfopCurriculumSubject) -> Unit
) {
    val subject = evaluated.subject
    val deptColor = departmentColorFor(subject.departmentCode)
    val accessibleDeptColor = accessibleTextColor(deptColor, isDark)
    val displaySubjectName = normalizeCurriculumDisplayName(subject.name)
    val periodLabel = if (subject.period <= 0) "Atividades Curriculares" else "${subject.period}º Período"

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = accessibleDeptColor
            )
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${subject.code} · ${subject.departmentCode} · $periodLabel",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accessibleDeptColor
                )
                Text(
                    text = displaySubjectName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Tipo: ${subject.type}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Carga horária: CHS ${subject.chs}h · CHE ${subject.che}h · CHA ${subject.cha}h",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Aulas semanais: ${subject.weeklyTheory} teóricas · ${subject.weeklyPractice} práticas/sem.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Pré-requisitos",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (subject.prerequisites.isEmpty()) {
                    Text(
                        text = "Nenhum pré-requisito obrigatório.",
                        style = MaterialTheme.typography.bodySmall,
                        color = accessibleTextColor(StatusAttendingGreen, isDark)
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        subject.prerequisites.forEach { prereq ->
                            val token = extractCodeToken(prereq)
                            val satisfied = !isConsultationMode && token in approvedCodes
                            val chipBg = when {
                                isConsultationMode -> MaterialTheme.colorScheme.surfaceVariant
                                satisfied -> StatusAttendingGreen.copy(alpha = 0.14f)
                                else -> StatusCancelledAmber.copy(alpha = 0.14f)
                            }
                            val chipFg = when {
                                isConsultationMode -> MaterialTheme.colorScheme.onSurfaceVariant
                                satisfied -> accessibleTextColor(StatusAttendingGreen, isDark)
                                else -> accessibleTextColor(StatusCancelledAmber, isDark)
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = chipBg
                            ) {
                                Text(
                                    text = if (satisfied) "✓ $prereq" else prereq,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    fontWeight = FontWeight.Bold,
                                    color = chipFg,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isConsultationMode || evaluated.status != SubjectProgressStatus.IN_PROGRESS) {
                Button(
                    onClick = { onAddToActiveSemester(subject) },
                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Adicionar ao semestre ativo",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Text("Em curso em $activeSemester")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = minTouchTarget)
            ) {
                Text(
                    text = "Fechar",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
internal fun CourseCatalogSwitcherDialog(
    courses: List<UfopCourseCurriculum>,
    selectedCourseCode: String?,
    isDark: Boolean,
    minTouchTarget: Dp,
    onDismiss: () -> Unit,
    onSelectCourse: (UfopCourseCurriculum) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(courses, query) {
        UfopCurriculumCatalog.searchCourses(courses, query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.School,
                contentDescription = null,
                tint = accessibleTextColor(DepartmentMathBlue, isDark)
            )
        },
        title = {
            Text(
                text = "Selecionar Curso (${courses.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar por nome, código ou campus...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null
                        )
                    },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = { query = "" },
                                modifier = Modifier.size(minTouchTarget)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpar"
                                )
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filtered,
                        key = { it.code }
                    ) { course ->
                        val isSelected = course.code == selectedCourseCode
                        val versions = UfopCurriculumCatalog.getCourseVersions(course)
                        val displayCourse = versions.lastOrNull { it.subjects.isNotEmpty() } ?: course
                        Surface(
                            onClick = { onSelectCourse(course) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = minTouchTarget),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            border = if (isSelected) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                br.ufop.campus.ui.theme.campusCardBorder()
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "${course.code} · ${normalizeCurriculumDisplayName(course.name)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val meta = buildString {
                                        if (displayCourse.campus.isNotBlank()) append(displayCourse.campus)
                                        if (displayCourse.degree.isNotBlank()) {
                                            if (isNotEmpty()) append(" · ")
                                            append(displayCourse.degree)
                                        }
                                        if (displayCourse.chTotal > 0) {
                                            if (isNotEmpty()) append(" · ")
                                            append("${formatHoursPtBr(displayCourse.chTotal)}h")
                                        }
                                        if (displayCourse.subjects.isNotEmpty()) {
                                            append(" · ${displayCourse.subjects.size} comp.")
                                        }
                                        if (versions.size > 1) {
                                            append(" · ${versions.size} versões")
                                        }
                                    }
                                    Text(
                                        text = meta,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selecionado",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = minTouchTarget)
            ) {
                Text(
                    text = "Fechar",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
