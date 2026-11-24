package br.ufop.campus.ui.curriculum

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.curriculum.CurriculumCompatibilityStatus
import br.ufop.campus.core.curriculum.UfopCourseCurriculum
import br.ufop.campus.core.curriculum.UfopCourseOfficialLinks
import br.ufop.campus.core.curriculum.UfopLegacyReference
import br.ufop.campus.ui.theme.DepartmentMathBlue
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.StatusCancelledAmber
import br.ufop.campus.ui.theme.accessibleTextColor

internal fun openExternalCurriculumUrl(context: Context, rawUrl: String) {
    val cleanUrl = rawUrl.trim()
    if (cleanUrl.isEmpty()) return
    val parsed = runCatching { Uri.parse(cleanUrl) }.getOrNull() ?: return
    val scheme = parsed.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return
    val intent = Intent(Intent.ACTION_VIEW, parsed).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

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
                    val versionLabel = version.curriculumVersion.ifBlank {
                        "${version.effectiveFromYear}/${version.effectiveFromSemester}"
                    }
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
    compatibility: CurriculumCompatibilityStatus,
    course: UfopCourseCurriculum
) {
    val effectiveStartLabel = "${course.effectiveFromYear}/${course.effectiveFromSemester}"
    val entryLabel = compatibility.studentEntryLabel ?: "anterior a $effectiveStartLabel"
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
                text = "Sua matrícula ($entryLabel) ou histórico diverge da vigência desta matriz ($effectiveStartLabel). Por isso, marcações de \"Já cursada\", \"Disponível\" e \"Falta pré-requisito\" foram ocultadas para evitar divergências com sua grade original.",
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

private fun formatReferenceValidity(ref: UfopLegacyReference): String? {
    val fromLabel = ref.effectiveFromYear?.let { year ->
        "$year/${ref.effectiveFromSemester ?: 1}"
    }
    val untilLabel = ref.effectiveUntilYear?.let { year ->
        "$year/${ref.effectiveUntilSemester ?: 2}"
    }
    return when {
        fromLabel != null && untilLabel != null -> "Vigência: $fromLabel – $untilLabel"
        fromLabel != null -> "Vigência a partir de $fromLabel"
        untilLabel != null -> "Vigência até $untilLabel"
        else -> null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CurriculumLegacyReferencesCard(
    references: List<UfopLegacyReference>,
    officialLinks: UfopCourseOfficialLinks?,
    initiallyExpanded: Boolean,
    isLegacyAlertContext: Boolean,
    isDark: Boolean,
    reduceMotion: Boolean,
    minTouchTarget: Dp,
    onOpenUrl: (String) -> Unit
) {
    var isExpanded by rememberSaveable(initiallyExpanded, references.firstOrNull()?.id) {
        mutableStateOf(initiallyExpanded)
    }
    val containerColor = if (isLegacyAlertContext) {
        StatusCancelledAmber.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (isLegacyAlertContext) {
        StatusCancelledAmber.copy(alpha = 0.36f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
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
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Matrizes anteriores e PPCs oficiais",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    val subtitle = if (references.isNotEmpty()) {
                        "${references.size} referência(s) oficial(is) para consulta"
                    } else {
                        "Links oficiais do curso na UFOP"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (isExpanded) "Recolher referências" else "Expandir referências",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = if (reduceMotion) EnterTransition.None else expandVertically() + fadeIn(),
                exit = if (reduceMotion) ExitTransition.None else shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    references.forEach { ref ->
                        LegacyReferenceItemRow(
                            reference = ref,
                            isDark = isDark,
                            minTouchTarget = minTouchTarget,
                            onOpenUrl = onOpenUrl
                        )
                    }

                    val matrixUrl = officialLinks?.officialMatrixUrl?.takeIf { it.isNotBlank() }
                    val driveUrl = officialLinks?.drivePdfUrl?.takeIf { it.isNotBlank() }
                    if (matrixUrl != null || driveUrl != null) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (matrixUrl != null) {
                                OutlinedButton(
                                    onClick = { onOpenUrl(matrixUrl) },
                                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Portal oficial UFOP")
                                }
                            }
                            if (driveUrl != null) {
                                OutlinedButton(
                                    onClick = { onOpenUrl(driveUrl) },
                                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("PDF matriz 2026/2")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegacyReferenceItemRow(
    reference: UfopLegacyReference,
    isDark: Boolean,
    minTouchTarget: Dp,
    onOpenUrl: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = reference.versionTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (reference.accessType.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = DepartmentMathBlue.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = reference.accessType,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accessibleTextColor(DepartmentMathBlue, isDark),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (reference.entryRuleSummary.isNotBlank()) {
                Text(
                    text = reference.entryRuleSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            formatReferenceValidity(reference)?.let { validity ->
                Text(
                    text = validity,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = { onOpenUrl(reference.url) },
                modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Abrir oficial")
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CurriculumEmptySubjectsNotice(
    course: UfopCourseCurriculum,
    catalogCourseCount: Int,
    officialLinks: UfopCourseOfficialLinks?,
    legacyReferences: List<UfopLegacyReference>,
    isDark: Boolean,
    minTouchTarget: Dp,
    onOpenUrl: (String) -> Unit,
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

            val matrixUrl = officialLinks?.officialMatrixUrl?.takeIf { it.isNotBlank() }
            val driveUrl = officialLinks?.drivePdfUrl?.takeIf { it.isNotBlank() }
            if (matrixUrl != null || driveUrl != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (matrixUrl != null) {
                        Button(
                            onClick = { onOpenUrl(matrixUrl) },
                            modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Abrir matriz oficial no navegador")
                        }
                    }
                    if (driveUrl != null) {
                        OutlinedButton(
                            onClick = { onOpenUrl(driveUrl) },
                            modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Abrir PDF oficial")
                        }
                    }
                }
            }

            if (legacyReferences.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Text(
                    text = "PPCs e páginas oficiais de referência",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                legacyReferences.forEach { ref ->
                    LegacyReferenceItemRow(
                        reference = ref,
                        isDark = isDark,
                        minTouchTarget = minTouchTarget,
                        onOpenUrl = onOpenUrl
                    )
                }
            }

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
