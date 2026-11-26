package br.ufop.campus.ui.activities

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.DisciplineSummaryCard
import br.ufop.campus.core.ffi.SyllabusImportReport
import br.ufop.campus.core.security.SecurePdfProcessor
import br.ufop.campus.ui.components.CampusDatePickerField
import br.ufop.campus.ui.components.CampusTimePickerField
import br.ufop.campus.ui.components.defaultTodayIsoDate
import br.ufop.campus.ui.disciplines.normalizeAssessmentDateInput
import br.ufop.campus.ui.theme.CampusCardRadius
import br.ufop.campus.ui.theme.CampusPillRadius
import br.ufop.campus.ui.theme.CampusSheetRadius
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusCancelledAmber
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.campusCardBorder
import br.ufop.campus.ui.theme.parseHexColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun formatSyllabusImportBanner(courseName: String, report: SyllabusImportReport): Pair<String, Boolean> {
    val missingDetails = buildList {
        if (!report.hasGradeDistribution) add("distribuição de notas não identificada")
        if (!report.hasAssessmentDates) add("datas de avaliações não encontradas")
    }
    val hasWarning = !report.warningMessage.isNullOrBlank() || missingDetails.isNotEmpty()
    val parts = buildList {
        if (report.importedCount > 0) {
            add("${report.importedCount} avaliação(ões) importada(s) do Plano de Ensino de $courseName.")
        } else {
            add("Plano de Ensino de $courseName analisado (nenhuma nova data pendente).")
        }
        if (report.extractedProfessor.isNotBlank()) {
            add("Docente: ${report.extractedProfessor}.")
        }
        if (!report.warningMessage.isNullOrBlank()) {
            add(report.warningMessage)
        } else if (missingDetails.isNotEmpty()) {
            add("Aviso: ${missingDetails.joinToString(" e ")}.")
        }
    }
    return parts.joinToString(" ") to hasWarning
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateAssessmentBottomSheet(
    repository: CampusCoreRepository,
    semester: String,
    disciplines: List<DisciplineSummaryCard>,
    initialCourseId: String,
    onDismiss: () -> Unit,
    onSyllabusImported: (message: String, isWarning: Boolean) -> Unit,
    onSave: (courseId: String, title: String, kind: String, dueDate: String, dueTime: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = LocalCampusThemeController.current.isDark
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = if (a11y.largeTouchTargets) 56.dp else 48.dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCourseId by remember(initialCourseId) { mutableStateOf(initialCourseId) }
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("Trabalho") }
    var dateIso by remember { mutableStateOf(defaultTodayIsoDate()) }
    var time24h by remember { mutableStateOf("") }
    var isImportingSyllabus by remember { mutableStateOf(false) }
    var syllabusFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var syllabusFeedbackIsError by remember { mutableStateOf(false) }

    val selectedCourseName = disciplines.find { it.courseId == selectedCourseId }?.name ?: "disciplina selecionada"

    val syllabusPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isImportingSyllabus = true
        syllabusFeedbackMessage = null
        syllabusFeedbackIsError = false
        val targetCourseId = selectedCourseId
        val targetCourseName = selectedCourseName
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    try {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("Não foi possível ler o PDF selecionado.")
                        repository.importCourseSyllabusDetailedPdf(targetCourseId, bytes)
                    } finally {
                        runCatching { SecurePdfProcessor.purgeResidualPdfFiles(context) }
                    }
                }
            }
            isImportingSyllabus = false
            result.onSuccess { report ->
                if (!report.matchedCourse) {
                    syllabusFeedbackIsError = true
                    syllabusFeedbackMessage = report.errorMessage
                        ?: "Este PDF parece ser de outra disciplina e não de $targetCourseName. Nenhuma alteração foi feita."
                } else {
                    val (msg, isWarning) = formatSyllabusImportBanner(targetCourseName, report)
                    if (report.importedCount > 0) {
                        onSyllabusImported(msg, isWarning)
                    } else {
                        syllabusFeedbackIsError = false
                        syllabusFeedbackMessage = msg
                    }
                }
            }.onFailure { error ->
                syllabusFeedbackIsError = true
                syllabusFeedbackMessage =
                    error.message ?: "Falha ao processar o Plano de Ensino (PDF)."
            }
        }
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
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "NOVO PRAZO · $semester",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Adicionar avaliação",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier.size(minTouchTarget),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Text(
                text = "Disciplina",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                disciplines.forEach { course ->
                    val isSelected = course.courseId == selectedCourseId
                    val courseColor = parseHexColor(course.colorHex)
                    val accessibleCourseColor = accessibleTextColor(courseColor, isDark)
                    Surface(
                        modifier = Modifier
                            .defaultMinSize(minHeight = if (a11y.largeTouchTargets) minTouchTarget else 40.dp)
                            .clip(RoundedCornerShape(CampusPillRadius))
                            .clickable(role = Role.RadioButton) {
                                selectedCourseId = course.courseId
                                syllabusFeedbackMessage = null
                            },
                        shape = RoundedCornerShape(CampusPillRadius),
                        color = if (isSelected) {
                            courseColor.copy(alpha = if (isDark) 0.24f else 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = if (isSelected || a11y.highContrastMode) {
                            BorderStroke(
                                width = if (a11y.highContrastMode) 1.5.dp else 1.dp,
                                color = if (isSelected) accessibleCourseColor else MaterialTheme.colorScheme.outline
                            )
                        } else {
                            null
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(courseColor)
                            )
                            Text(
                                text = course.name,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) accessibleCourseColor else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            SyllabusPdfImportCard(
                selectedCourseName = selectedCourseName,
                isImportingSyllabus = isImportingSyllabus,
                syllabusFeedbackMessage = syllabusFeedbackMessage,
                syllabusFeedbackIsError = syllabusFeedbackIsError,
                isDark = isDark,
                minTouchTarget = minTouchTarget,
                onPickPdfClick = { syllabusPdfLauncher.launch(arrayOf("application/pdf")) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    text = "ou cadastre manualmente",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Título da atividade") },
                placeholder = { Text("Ex.: Entrega do Projeto Final") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Prova", "Trabalho", "Seminário").forEach { option ->
                    FilterChip(
                        selected = kind == option,
                        onClick = { kind = option },
                        modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                        label = { Text(option) },
                        shape = RoundedCornerShape(22.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            CampusDatePickerField(
                label = "Data",
                isoDate = dateIso,
                onIsoDateSelected = { dateIso = it },
                minHeight = minTouchTarget
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CampusTimePickerField(
                    label = "Horário (opcional)",
                    time24h = time24h,
                    onTimeSelected = { time24h = it },
                    minHeight = minTouchTarget
                )
                if (time24h.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { time24h = "" },
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Cancelar")
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = {
                        val cleanTitle = title.trim()
                        if (cleanTitle.isNotEmpty()) {
                            onSave(
                                selectedCourseId,
                                cleanTitle,
                                kind,
                                normalizeAssessmentDateInput(dateIso),
                                time24h.trim()
                            )
                        }
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Salvar avaliação")
                }
            }
        }
    }
}

@Composable
private fun SyllabusPdfImportCard(
    selectedCourseName: String,
    isImportingSyllabus: Boolean,
    syllabusFeedbackMessage: String?,
    syllabusFeedbackIsError: Boolean,
    isDark: Boolean,
    minTouchTarget: Dp,
    onPickPdfClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CampusCardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = campusCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PictureAsPdf,
                    contentDescription = null,
                    tint = accessibleTextColor(MaterialTheme.colorScheme.primary, isDark),
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Plano de Ensino (PDF)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Importar datas de $selectedCourseName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilledTonalButton(
                    onClick = onPickPdfClick,
                    enabled = !isImportingSyllabus,
                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                    shape = RoundedCornerShape(CampusPillRadius),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    if (isImportingSyllabus) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Importar PDF",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            if (syllabusFeedbackMessage != null) {
                val rawBannerTone = if (syllabusFeedbackIsError) StatusAbsentRed else StatusCancelledAmber
                val bannerFg = accessibleTextColor(rawBannerTone, isDark)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = rawBannerTone.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = syllabusFeedbackMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = bannerFg,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
