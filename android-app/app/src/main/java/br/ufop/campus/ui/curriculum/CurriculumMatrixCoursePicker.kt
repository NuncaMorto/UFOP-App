package br.ufop.campus.ui.curriculum

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.curriculum.CurriculumCompatibilityStatus
import br.ufop.campus.core.curriculum.UfopCourseCurriculum
import br.ufop.campus.ui.theme.DepartmentMathBlue
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.StatusCancelledAmber
import br.ufop.campus.ui.theme.accessibleTextColor

@Composable
internal fun CurriculumMatrixTopBar(
    selectedCourse: UfopCourseCurriculum?,
    catalogCourseCount: Int,
    minTouchTarget: Dp,
    onDismiss: () -> Unit,
    onOpenCoursePicker: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            onClick = onDismiss,
            modifier = Modifier.size(minTouchTarget),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Voltar",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "MATRIZ CURRICULAR · ${selectedCourse?.curriculumVersion?.ifBlank { "2026/2" } ?: "2026/2"}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = selectedCourse?.let { "${it.code} · ${normalizeCurriculumDisplayName(it.name)}" } ?: "Carregando matrizes...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
        }

        OutlinedButton(
            onClick = onOpenCoursePicker,
            enabled = catalogCourseCount > 0,
            modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
            shape = RoundedCornerShape(999.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "${catalogCourseCount.takeIf { it > 0 } ?: 54} cursos",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Selecionar curso",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CurriculumVersionSelectorCard(
    availableVersions: List<UfopCourseCurriculum>,
    selectedCurriculumVersion: String,
    onSelectVersion: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Versão da matriz curricular",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                availableVersions.forEach { version ->
                    val isVersionSelected = version.curriculumVersion == selectedCurriculumVersion
                    val versionLabel = version.curriculumVersion.ifBlank { "2026/2 (Currículo 3)" }
                    FilterChip(
                        selected = isVersionSelected,
                        onClick = { onSelectVersion(version.curriculumVersion) },
                        label = {
                            Text(
                                text = versionLabel,
                                fontWeight = if (isVersionSelected) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Medium
                                }
                            )
                        },
                        leadingIcon = if (isVersionSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun LegacyMatrixConsultationBanner(
    compatibility: CurriculumCompatibilityStatus
) {
    val entryLabel = compatibility.studentEntryLabel ?: "anterior a 2026/1"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = StatusCancelledAmber.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, StatusCancelledAmber.copy(alpha = 0.42f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = StatusCancelledAmber,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Matriz de referência ${compatibility.matrixVersionLabel} · Modo Consulta",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Sua matrícula ($entryLabel) precede a atualização de matriz curricular de 2026/1. Por isso, marcações de \"Já cursada\", \"Disponível\" e \"Falta pré-requisito\" foram ocultadas para evitar divergências com sua grade original.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Você continua podendo buscar disciplinas, abrir períodos e consultar cargas horárias e pré-requisitos livremente.",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CourseWorkloadSummaryCard(
    course: UfopCourseCurriculum,
    evaluatedSubjects: List<EvaluatedSubject>,
    isConsultationMode: Boolean,
    isDark: Boolean
) {
    val approvedCount = evaluatedSubjects.count { it.status == SubjectProgressStatus.APPROVED }
    val inProgressCount = evaluatedSubjects.count { it.status == SubjectProgressStatus.IN_PROGRESS }
    val campusColor = accessibleTextColor(DepartmentMathBlue, isDark)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = course.code,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (course.degree.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = course.degree,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (course.campus.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DepartmentMathBlue.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = course.campus,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = campusColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Text(
                    text = normalizeCurriculumDisplayName(course.name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )

                val visibleStatus = course.status
                    .trim()
                    .takeIf { it.isNotBlank() && !it.equals("Coletada", ignoreCase = true) }
                val versionText = listOfNotNull(
                    course.curriculumVersion.takeIf { it.isNotBlank() }?.let { "Versão $it" },
                    visibleStatus
                ).joinToString(" · ")
                if (versionText.isNotEmpty()) {
                    Text(
                        text = versionText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WorkloadPill(label = "Total", hours = course.chTotal, highlighted = true)
                if (course.chMandatory > 0) WorkloadPill(label = "Obrig.", hours = course.chMandatory)
                if (course.chOptional > 0) WorkloadPill(label = "Elet./Opt.", hours = course.chOptional)
                if (course.chElective > 0) WorkloadPill(label = "Facult.", hours = course.chElective)
                if (course.chExtension > 0) WorkloadPill(label = "Extensão", hours = course.chExtension)
                if (course.chActivities > 0) WorkloadPill(label = "Ativ.", hours = course.chActivities)
            }

            if (evaluatedSubjects.isNotEmpty()) {
                Text(
                    text = if (isConsultationMode) {
                        "${evaluatedSubjects.size} componentes na matriz de referência · Modo Consulta"
                    } else {
                        "${evaluatedSubjects.size} componentes na matriz · $approvedCount aprovadas · $inProgressCount cursando"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WorkloadPill(
    label: String,
    hours: Int,
    highlighted: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    ) {
        Text(
            text = "$label ${formatHoursPtBr(hours)}h",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
            color = if (highlighted) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
internal fun CurriculumConfirmationBanner(
    message: String,
    isError: Boolean,
    isDark: Boolean,
    minTouchTarget: Dp,
    onDismiss: () -> Unit
) {
    val rawColor = if (isError) StatusAbsentRed else StatusAttendingGreen
    val textColor = accessibleTextColor(rawColor, isDark)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = rawColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, rawColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(minTouchTarget)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fechar",
                    modifier = Modifier.size(16.dp),
                    tint = textColor
                )
            }
        }
    }
}

@Composable
internal fun CurriculumEmptySubjectsNotice(
    course: UfopCourseCurriculum,
    catalogCourseCount: Int,
    isDark: Boolean,
    minTouchTarget: Dp,
    onOpenCoursePicker: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = accessibleTextColor(StatusCancelledAmber, isDark)
                )
                Text(
                    text = "Resumo 2026/2 · Detalhamento PDF pendente na fonte pública",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "As cargas horárias totais de ${normalizeCurriculumDisplayName(course.name)} já estão sincronizadas com o catálogo 2026/2 (${formatHoursPtBr(course.chTotal)}h totais). A lista individual de componentes curriculares aguarda disponibilidade na grade pública.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onOpenCoursePicker,
                modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                shape = RoundedCornerShape(22.dp)
            ) {
                Text("Explorar outro dos $catalogCourseCount cursos")
            }
        }
    }
}
