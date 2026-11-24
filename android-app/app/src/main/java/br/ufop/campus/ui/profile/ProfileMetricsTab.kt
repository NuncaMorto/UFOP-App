package br.ufop.campus.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ufop.campus.core.ffi.AcademicCreReport
import br.ufop.campus.core.ffi.AcademicProfileSummary
import br.ufop.campus.core.ffi.DisciplineSummaryCard
import br.ufop.campus.core.ffi.SemesterCreSummary
import br.ufop.campus.core.ffi.TranscriptCourseEntry
import br.ufop.campus.core.ffi.normalizeUfopPortugueseText
import br.ufop.campus.ui.theme.DepartmentMathBlue
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.parseHexColor
import br.ufop.campus.ui.theme.semanticAbsenceRiskColor
import br.ufop.campus.ui.theme.semanticAbsenceRiskLabel
import br.ufop.campus.ui.theme.semanticAbsenceRiskLevel

@Composable
internal fun ProfileMetricsAndCreTab(
    profileSummary: AcademicProfileSummary,
    currentDisciplines: List<DisciplineSummaryCard>,
    creReport: AcademicCreReport,
    isImportingTranscript: Boolean,
    transcriptFeedbackMessage: String?,
    transcriptFeedbackIsError: Boolean,
    onImportTranscriptPdf: () -> Unit,
    onOpenCurriculumMatrix: () -> Unit,
    onSelectTranscriptEntry: (TranscriptCourseEntry) -> Unit
) {
    val expandedSemesters = remember { mutableStateMapOf<String, Boolean>() }
    val defaultExpandedSemesters = remember(creReport.semesters, profileSummary.semester) {
        buildSet {
            creReport.semesters.firstOrNull()?.semester?.let { add(it) }
            creReport.semesters
                .firstOrNull { it.semester == profileSummary.semester || it.inProgressCount > 0 }
                ?.semester
                ?.let { add(it) }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Faltas do Semestre Atual
        CurrentSemesterAbsencesCard(
            profileSummary = profileSummary,
            currentDisciplines = currentDisciplines
        )

        // 2. Hero Card CRE & Histórico Escolar
        CreHeroAndImportCard(
            creReport = creReport,
            isImportingTranscript = isImportingTranscript,
            transcriptFeedbackMessage = transcriptFeedbackMessage,
            transcriptFeedbackIsError = transcriptFeedbackIsError,
            onImportTranscriptPdf = onImportTranscriptPdf,
            onOpenCurriculumMatrix = onOpenCurriculumMatrix
        )

        // 3. Lista por Semestre + Simulador/Editor de Notas & Faltas
        if (creReport.semesters.isEmpty()) {
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Nenhum histórico semestral carregado",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Importe seu Histórico Escolar (PDF) acima para visualizar todos os semestres cursados, professores e simular notas do período atual.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            creReport.semesters.forEach { semesterSummary ->
                val isExpanded = expandedSemesters[semesterSummary.semester]
                    ?: (semesterSummary.semester in defaultExpandedSemesters)
                SemesterCreCard(
                    summary = semesterSummary,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        expandedSemesters[semesterSummary.semester] = !isExpanded
                    },
                    onSelectCourse = onSelectTranscriptEntry
                )
            }
        }

        // 4. Nota discreta no rodapé sobre aulas canceladas
        CancelledClassesFooterNote(totalCancelled = profileSummary.totalCancelledClasses)
    }
}
@Composable
private fun CurrentSemesterAbsencesCard(
    profileSummary: AcademicProfileSummary,
    currentDisciplines: List<DisciplineSummaryCard>
) {
    val isDark = LocalCampusThemeController.current.isDark
    val surfaceColor = MaterialTheme.colorScheme.surface
    val absenceRatio = remember(profileSummary.totalAbsences, profileSummary.totalMaxAbsences) {
        (profileSummary.totalAbsences.toFloat() /
            profileSummary.totalMaxAbsences.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    }
    val normalizedDisciplineNames = remember(currentDisciplines) {
        currentDisciplines.associate { it.courseId to normalizeUfopPortugueseText(it.name) }
    }
    val hasCourseAtRisk = profileSummary.coursesAtRisk > 0
    val overallBarColor = if (hasCourseAtRisk) {
        semanticAbsenceRiskColor(1, 1, isDark)
    } else {
        semanticAbsenceRiskColor(profileSummary.totalAbsences, profileSummary.totalMaxAbsences, isDark)
    }
    val riskRedText = accessibleTextColor(StatusAbsentRed, isDark, surfaceColor)
    val safeGreenText = accessibleTextColor(StatusAttendingGreen, isDark, surfaceColor)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Faltas do Semestre Atual · ${profileSummary.semester}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )

            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        append("${profileSummary.totalAbsences}")
                    }
                    withStyle(
                        SpanStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        append(" / ${profileSummary.totalMaxAbsences}")
                    }
                    withStyle(
                        SpanStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        append(" faltas no semestre")
                    }
                }
            )

            LinearProgressIndicator(
                progress = { absenceRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = overallBarColor,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatSummaryBox(
                    value = "${profileSummary.totalCourses}",
                    label = "Disciplinas",
                    modifier = Modifier.weight(1f)
                )
                StatSummaryBox(
                    value = "${profileSummary.pendingAssessments}",
                    label = "Prazos pendentes",
                    modifier = Modifier.weight(1f)
                )
                StatSummaryBox(
                    value = "${profileSummary.completedAssessments}",
                    label = "Prazos concluídos",
                    modifier = Modifier.weight(1f)
                )
            }

            if (hasCourseAtRisk) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = StatusAbsentRed.copy(alpha = if (isDark) 0.18f else 0.12f),
                    border = BorderStroke(1.dp, riskRedText.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = riskRedText,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Alerta de risco: ${profileSummary.coursesAtRisk} disciplina(s) atingiu(ram) >= 75% do limite máximo de faltas.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = riskRedText
                        )
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = StatusAttendingGreen.copy(alpha = if (isDark) 0.16f else 0.10f)
                ) {
                    Text(
                        text = "Frequência controlada: nenhuma disciplina está na faixa crítica (>= 75% do limite).",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = safeGreenText,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            if (currentDisciplines.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "Progresso por disciplina",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    currentDisciplines.forEach { discipline ->
                        val ratio = if (discipline.maxAbsences > 0) {
                            (discipline.currentAbsences.toFloat() / discipline.maxAbsences.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        val riskLevel = semanticAbsenceRiskLevel(discipline.currentAbsences, discipline.maxAbsences)
                        val riskColor = semanticAbsenceRiskColor(
                            discipline.currentAbsences,
                            discipline.maxAbsences,
                            isDark
                        )
                        val riskLabel = semanticAbsenceRiskLabel(discipline.currentAbsences, discipline.maxAbsences)
                        val deptColor = parseHexColor(discipline.colorHex)
                        val deptBadgeTextColor = accessibleTextColor(deptColor, isDark, surfaceColor)

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (discipline.code.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = deptColor.copy(alpha = if (isDark) 0.22f else 0.14f)
                                        ) {
                                            Text(
                                                text = discipline.code,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = deptBadgeTextColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = normalizedDisciplineNames[discipline.courseId] ?: discipline.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (riskLevel >= 1) {
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = riskColor.copy(alpha = 0.16f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.WarningAmber,
                                                    contentDescription = null,
                                                    tint = riskColor,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = riskLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = riskColor
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${discipline.currentAbsences}/${discipline.maxAbsences}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (riskLevel >= 1) riskColor else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = riskColor,
                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun CreHeroAndImportCard(
    creReport: AcademicCreReport,
    isImportingTranscript: Boolean,
    transcriptFeedbackMessage: String?,
    transcriptFeedbackIsError: Boolean,
    onImportTranscriptPdf: () -> Unit,
    onOpenCurriculumMatrix: () -> Unit
) {
    val isDark = LocalCampusThemeController.current.isDark
    val surfaceColor = MaterialTheme.colorScheme.surface
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = campusMinTouchTarget()
    val accessibleBlue = accessibleTextColor(DepartmentMathBlue, isDark, surfaceColor)
    val completionRatio = remember(creReport.completedHours, creReport.totalRequiredHours) {
        val requiredHours = creReport.totalRequiredHours.coerceAtLeast(1)
        (creReport.completedHours.toFloat() / requiredHours.toFloat()).coerceIn(0f, 1f)
    }
    val formattedOverallCre = remember(creReport.overallCre) {
        formatScore(creReport.overallCre, 2)
    }
    val hoursSummaryText = remember(creReport.completedHours, creReport.totalRequiredHours) {
        "${creReport.completedHours} / ${creReport.totalRequiredHours} h"
    }
    val approvalCountsText = remember(creReport.totalApproved, creReport.totalFailedByGrade) {
        "${creReport.totalApproved} aprovadas · ${creReport.totalFailedByGrade} reprovadas"
    }
    var showCreFormulaDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "COEFICIENTE DE RENDIMENTO ESCOLAR · CRE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accessibleBlue,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { showCreFormulaDialog = true },
                    modifier = Modifier.size(if (a11y.largeTouchTargets) 56.dp else 48.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "Ver fórmula do cálculo do CRE",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "CRE Acumulado",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formattedOverallCre,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Carga Horária Integralizada",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = hoursSummaryText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LinearProgressIndicator(
                        progress = { completionRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = accessibleTextColor(StatusAttendingGreen, isDark, surfaceColor),
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                    Text(
                        text = approvalCountsText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Botão proeminente Importar Histórico Escolar (PDF)
            Button(
                onClick = onImportTranscriptPdf,
                enabled = !isImportingTranscript,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minTouchTarget),
                shape = RoundedCornerShape(22.dp)
            ) {
                if (isImportingTranscript) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Importando Histórico Escolar...",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Importar Histórico Escolar (PDF)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            OutlinedButton(
                onClick = onOpenCurriculumMatrix,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minTouchTarget),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Abrir Matriz Curricular (2026/2)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (transcriptFeedbackMessage != null) {
                StatusFeedbackBanner(
                    message = transcriptFeedbackMessage,
                    isError = transcriptFeedbackIsError
                )
            }
        }
    }

    if (showCreFormulaDialog) {
        AlertDialog(
            onDismissRequest = { showCreFormulaDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Functions,
                    contentDescription = null,
                    tint = accessibleBlue
                )
            },
            title = {
                Text(
                    text = "Fórmula do CRE (ref. acadêmica)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = "CRE = Σ(Nota × CH) / Σ(CH)",
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Legenda das variáveis:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• Nota: nota final obtida em cada disciplina (0,0 a 10,0).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "• CH: carga horária da disciplina em horas (ex.: 30h, 60h, 90h).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "• Σ: somatório ponderado de todas as disciplinas cursadas com nota atribuída.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = DepartmentMathBlue.copy(alpha = 0.10f)
                    ) {
                        Text(
                            text = "Simulação instantânea: ao tocar em qualquer disciplina nos semestres abaixo e editar a nota ou faltas, tanto o CRE semestral quanto o CRE acumulado são recalculados automaticamente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showCreFormulaDialog = false },
                    modifier = Modifier.heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Text("Entendi")
                }
            }
        )
    }
}
@Composable
private fun SemesterCreCard(
    summary: SemesterCreSummary,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectCourse: (TranscriptCourseEntry) -> Unit
) {
    val isDark = LocalCampusThemeController.current.isDark
    val surfaceColor = MaterialTheme.colorScheme.surface
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = campusMinTouchTarget()
    val accessibleBlue = accessibleTextColor(DepartmentMathBlue, isDark, surfaceColor)
    val semesterStatsLabel = remember(
        summary.courses.size,
        summary.totalWorkloadHours,
        summary.approvedCount,
        summary.failedCount,
        summary.inProgressCount
    ) {
        "${summary.courses.size} disciplinas · ${summary.totalWorkloadHours}h · ${summary.approvedCount} AP · ${summary.failedCount} REP · ${summary.inProgressCount} CS"
    }
    val formattedSemesterCre = remember(summary.cre) {
        formatScore(summary.cre, 2)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minTouchTarget)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onToggleExpand)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Semestre ${summary.semester}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = semesterStatsLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "COEFICIENTE SEMESTRAL",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formattedSemesterCre,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = accessibleBlue
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = if (isExpanded) "Recolher semestre" else "Expandir semestre",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            CampusAnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "Toque em uma disciplina para editar ou simular nota e faltas",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    summary.courses.forEach { entry ->
                        TranscriptCourseRow(
                            entry = entry,
                            onClick = { onSelectCourse(entry) }
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun TranscriptCourseRow(
    entry: TranscriptCourseEntry,
    onClick: () -> Unit
) {
    val isDark = LocalCampusThemeController.current.isDark
    val surfaceColor = MaterialTheme.colorScheme.surface
    val minTouchTarget = campusMinTouchTarget()
    val (statusBg, statusFg) = when (entry.status.uppercase()) {
        "AP", "AE", "DE", "DJ" -> StatusAttendingGreen.copy(alpha = 0.14f) to accessibleTextColor(StatusAttendingGreen, isDark, surfaceColor)
        "CS" -> DepartmentMathBlue.copy(alpha = 0.14f) to accessibleTextColor(DepartmentMathBlue, isDark, surfaceColor)
        "RN", "RF", "NF" -> StatusAbsentRed.copy(alpha = 0.14f) to accessibleTextColor(StatusAbsentRed, isDark, surfaceColor)
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minTouchTarget),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (entry.code.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = entry.code,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusBg
                    ) {
                        Text(
                            text = entry.status,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusFg,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "${entry.workloadHours}h",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val profPrefix = if (entry.professor.isNotBlank()) "${entry.professor} · " else ""
                Text(
                    text = "$profPrefix${entry.absences} faltas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Nota",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = entry.grade?.let { formatScore(it, 1) } ?: "--",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (entry.grade != null) {
                            accessibleTextColor(
                                if (entry.grade >= 6.0) StatusAttendingGreen else StatusAbsentRed,
                                isDark,
                                surfaceColor
                            )
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}
@Composable
internal fun TranscriptGradeSimulatorDialog(
    entry: TranscriptCourseEntry,
    onDismiss: () -> Unit,
    onSave: (Double?, Int) -> Unit
) {
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    var gradeText by remember(entry) {
        mutableStateOf(entry.grade?.let { formatScore(it, 1) } ?: "")
    }
    var absences by remember(entry) { mutableIntStateOf(entry.absences.coerceAtLeast(0)) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "SIMULADOR DE NOTA & FALTAS · ${entry.semester}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (entry.code.isNotBlank()) "${entry.code} — ${entry.name}" else entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Carga horária: ${entry.workloadHours}h · Status atual: ${entry.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = gradeText,
                    onValueChange = {
                        gradeText = it
                        validationError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nota (0.0 a 10.0)") },
                    placeholder = { Text("Ex.: 7.5 (ou vazio para sem nota)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    trailingIcon = {
                        if (gradeText.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    gradeText = ""
                                    validationError = null
                                },
                                modifier = Modifier.size(minTouchTarget)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpar nota"
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("6.0", "7.5", "8.5", "10.0").forEach { preset ->
                        FilterChip(
                            selected = gradeText.replace(",", ".") == preset,
                            onClick = {
                                gradeText = preset
                                validationError = null
                            },
                            label = { Text(preset, maxLines = 1, softWrap = false) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = minTouchTarget),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Faltas registradas",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Ajuste para o semestre",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalIconButton(
                                onClick = { if (absences > 0) absences -= 1 },
                                enabled = absences > 0,
                                modifier = Modifier.size(minTouchTarget)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Diminuir faltas",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "$absences",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            FilledTonalIconButton(
                                onClick = { absences += 1 },
                                modifier = Modifier.size(minTouchTarget)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Aumentar faltas",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = accessibleTextColor(StatusAbsentRed, isDark, MaterialTheme.colorScheme.surface)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = gradeText.trim()
                    if (trimmed.isEmpty()) {
                        onSave(null, absences)
                    } else {
                        val parsed = trimmed.replace(",", ".").toDoubleOrNull()
                        if (parsed == null || !parsed.isFinite() || parsed < 0.0 || parsed > 10.0) {
                            validationError = "Informe uma nota válida entre 0.0 e 10.0."
                        } else {
                            onSave(parsed, absences)
                        }
                    }
                },
                modifier = Modifier.heightIn(min = minTouchTarget),
                shape = RoundedCornerShape(22.dp)
            ) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = minTouchTarget),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("Cancelar")
            }
        }
    )
}
@Composable
private fun CancelledClassesFooterNote(totalCancelled: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            val cancelledLabel = if (totalCancelled == 1) {
                "1 aula cancelada neste semestre · aulas canceladas não somam faltas."
            } else {
                "$totalCancelled aulas canceladas neste semestre · aulas canceladas não somam faltas."
            }
            Text(
                text = cancelledLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
