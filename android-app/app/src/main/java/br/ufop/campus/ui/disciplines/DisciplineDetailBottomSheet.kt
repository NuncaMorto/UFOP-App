package br.ufop.campus.ui.disciplines

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ufop.campus.core.ffi.AbsenceHistoryEntry
import br.ufop.campus.core.ffi.AssessmentItem
import br.ufop.campus.core.ffi.AssessmentWithCourse
import br.ufop.campus.core.ffi.AttendanceStatus
import br.ufop.campus.core.ffi.CourseScheduleSlotItem
import br.ufop.campus.core.ffi.DisciplineDetail
import br.ufop.campus.core.ffi.SyllabusImportReport
import br.ufop.campus.core.ffi.UfopBuildingOption
import br.ufop.campus.core.security.SecurePdfProcessor
import br.ufop.campus.ui.activities.AssessmentDetailBottomSheet
import br.ufop.campus.ui.components.CampusDatePickerField
import br.ufop.campus.ui.components.CampusTimePickerField
import br.ufop.campus.ui.components.defaultTodayIsoDate
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.StatusCancelledAmber
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.parseHexColor
import br.ufop.campus.ui.theme.semanticAbsenceRiskColor
import br.ufop.campus.ui.theme.semanticAbsenceRiskLabel
import br.ufop.campus.ui.theme.semanticAbsenceRiskLevel
import kotlinx.coroutines.flow.MutableStateFlow

private val EDIT_WORKLOAD_PRESETS = listOf(30, 45, 60, 72, 90, 120)

private enum class SyllabusFeedbackTone {
    SUCCESS,
    WARNING,
    ERROR
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DisciplineDetailBottomSheet(
    detail: DisciplineDetail,
    onDismiss: () -> Unit,
    onSaveNotes: (String) -> Unit,
    onToggleAssessmentCompleted: ((assessmentId: String, completed: Boolean) -> Unit)? = null,
    onCreateAssessment: ((title: String, kind: String, dueDate: String, dueTime: String) -> Unit)? = null,
    onDeleteAssessment: ((assessmentId: String) -> Unit)? = null,
    scheduleSlots: List<CourseScheduleSlotItem>? = null,
    absenceHistory: List<AbsenceHistoryEntry>? = null,
    onAddScheduleSlot: ((dayOfWeek: Int, startTime: String, endTime: String, building: String, room: String, shortLocation: String) -> Result<CourseScheduleSlotItem>)? = null,
    onUpdateScheduleSlot: ((slotId: String, dayOfWeek: Int, startTime: String, endTime: String, building: String, room: String, shortLocation: String) -> Result<CourseScheduleSlotItem>)? = null,
    onDeleteScheduleSlot: ((slotId: String) -> Unit)? = null,
    onDeleteCourse: (() -> Unit)? = null,
    onImportSyllabusPdf: ((pdfBytes: ByteArray) -> Result<Int>)? = null,
    onUpdateAbsences: ((totalAbsences: Int) -> Unit)? = null,
    onUpdateCourseDetails: ((name: String, professor: String, departmentId: String, workloadHours: Int) -> Unit)? = null,
    onUpdateAssessment: ((assessmentId: String, courseId: String, title: String, kind: String, dueDate: String, dueTime: String, completed: Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = LocalCampusThemeController.current.isDark
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = if (a11y.largeTouchTargets) 56.dp else 48.dp
    val secondaryControlHeight = if (a11y.largeTouchTargets) minTouchTarget else 38.dp
    val secondaryChipHeight = if (a11y.largeTouchTargets) minTouchTarget else 36.dp

    // Fallback reativo para chamadas sem ViewModel (ex.: aba Atividades)
    val fallbackRepo = DisciplinesViewModel.sharedRepository
    val fallbackVersion by (fallbackRepo?.mutationVersion ?: remember { MutableStateFlow(0L) })
        .collectAsState()
    val effectiveDetail = remember(detail.summary.courseId, fallbackVersion, detail) {
        fallbackRepo?.getDisciplineDetail(detail.summary.courseId) ?: detail
    }
    val effectiveSummary = effectiveDetail.summary
    val accentColor = remember(effectiveSummary.colorHex) { parseHexColor(effectiveSummary.colorHex) }
    val accessibleAccent = remember(accentColor, isDark) {
        accessibleTextColor(accentColor, isDark)
    }
    var notesDraft by remember(detail.summary.courseId) {
        mutableStateOf(effectiveDetail.notes)
    }
    var savedConfirmation by remember(detail.summary.courseId) {
        mutableStateOf(false)
    }

    val resolvedScheduleSlots = scheduleSlots ?: remember(detail.summary.courseId, fallbackVersion) {
        fallbackRepo?.listCourseSlots(detail.summary.courseId).orEmpty()
    }
    val resolvedAbsenceHistory = absenceHistory ?: remember(detail.summary.courseId, fallbackVersion) {
        fallbackRepo?.listAbsenceHistory(detail.summary.courseId).orEmpty()
    }
    val resolvedAssessments = effectiveDetail.assessments

    // Acordeões auto-fechantes (SEMPRE iniciam fechados ao abrir o sheet; nunca usar rememberSaveable!)
    var historyExpanded by remember(detail.summary.courseId) { mutableStateOf(false) }
    var slotsExpanded by remember(detail.summary.courseId) { mutableStateOf(false) }
    var assessmentsExpanded by remember(detail.summary.courseId) { mutableStateOf(false) }
    var notesExpanded by remember(detail.summary.courseId) { mutableStateOf(false) }

    // Sheet de detalhe/edição da avaliação selecionada
    var selectedAssessmentForDetailId by remember(detail.summary.courseId) { mutableStateOf<String?>(null) }

    // Estado do editor das informações da disciplina (nome, docente, carga horária)
    var showEditCourseDialog by remember(detail.summary.courseId) { mutableStateOf(false) }
    var editCourseName by remember(detail.summary.courseId) { mutableStateOf(effectiveSummary.name) }
    var editCourseProfessor by remember(detail.summary.courseId) { mutableStateOf(effectiveDetail.professor) }
    var editCourseWorkloadText by remember(detail.summary.courseId) {
        mutableStateOf(effectiveSummary.workloadHours.toString())
    }

    // Estado do editor manual de faltas
    var showEditAbsencesDialog by remember(detail.summary.courseId) { mutableStateOf(false) }
    var absencesInputDraft by remember(detail.summary.courseId) {
        mutableStateOf(effectiveSummary.currentAbsences.toString())
    }

    val handleUpdateAbsences: (Int) -> Unit = { newCount ->
        val clamped = newCount.coerceAtLeast(0)
        if (onUpdateAbsences != null) {
            onUpdateAbsences(clamped)
        } else {
            fallbackRepo?.updateCourseAbsences(detail.summary.courseId, clamped)
        }
    }

    val openEditAbsencesDialog: () -> Unit = {
        absencesInputDraft = effectiveSummary.currentAbsences.toString()
        showEditAbsencesDialog = true
    }

    // Estado do importador de Plano de Ensino (PDF)
    var syllabusFeedbackMessage by remember(detail.summary.courseId) { mutableStateOf<String?>(null) }
    var syllabusFeedbackTone by remember(detail.summary.courseId) { mutableStateOf(SyllabusFeedbackTone.SUCCESS) }

    val syllabusPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalArgumentException("Não foi possível ler o arquivo PDF selecionado.")
                    val repo = fallbackRepo ?: DisciplinesViewModel.activeInstance?.repository
                    when {
                        repo != null -> repo.importCourseSyllabusDetailedPdf(detail.summary.courseId, bytes)
                        onImportSyllabusPdf != null -> {
                            val imported = onImportSyllabusPdf(bytes).getOrThrow()
                            SyllabusImportReport(
                                importedCount = imported,
                                matchedCourse = true,
                                hasGradeDistribution = imported > 0,
                                hasAssessmentDates = imported > 0
                            )
                        }
                        else -> throw IllegalStateException("Repositório indisponível.")
                    }
                } finally {
                    runCatching { SecurePdfProcessor.purgeResidualPdfFiles(context) }
                }
            }.onSuccess { report ->
                if (!report.matchedCourse) {
                    syllabusFeedbackTone = SyllabusFeedbackTone.ERROR
                    syllabusFeedbackMessage = report.errorMessage
                        ?: "Este PDF parece ser de outra disciplina e não de ${effectiveSummary.name}. Nenhuma alteração foi feita."
                } else if (!report.warningMessage.isNullOrBlank()) {
                    syllabusFeedbackTone = SyllabusFeedbackTone.WARNING
                    val prefix = buildList {
                        if (report.importedCount > 0) {
                            add("${report.importedCount} avaliação(ões) adicionada(s).")
                        }
                        if (report.extractedProfessor.isNotBlank()) {
                            add("Docente: ${report.extractedProfessor}.")
                        }
                    }.joinToString(" ")
                    syllabusFeedbackMessage = if (prefix.isNotEmpty()) {
                        "$prefix ${report.warningMessage}"
                    } else {
                        report.warningMessage
                    }
                } else {
                    syllabusFeedbackTone = SyllabusFeedbackTone.SUCCESS
                    val profSuffix = if (report.extractedProfessor.isNotBlank()) {
                        " Docente: ${report.extractedProfessor}."
                    } else {
                        ""
                    }
                    syllabusFeedbackMessage = if (report.importedCount > 0) {
                        "Plano de Ensino importado! ${report.importedCount} avaliação(ões) adicionada(s) em ${effectiveSummary.name}.$profSuffix"
                    } else {
                        "Plano de Ensino verificado para ${effectiveSummary.name} (nenhuma nova data pendente).$profSuffix"
                    }
                }
            }.onFailure { err ->
                syllabusFeedbackTone = SyllabusFeedbackTone.ERROR
                syllabusFeedbackMessage = err.message
                    ?: "Não foi possível importar o Plano de Ensino (PDF)."
            }
        }
    }

    // Estado do formulário inline "Adicionar horário"
    var showAddSlotForm by remember(detail.summary.courseId) { mutableStateOf(false) }
    var slotDraft by remember(detail.summary.courseId) { mutableStateOf(ScheduleSlotDraft(room = "")) }
    var slotErrorMessage by remember(detail.summary.courseId) { mutableStateOf<String?>(null) }

    // Estado do formulário inline de edição de horário existente
    var editingSlotId by remember(detail.summary.courseId) { mutableStateOf<String?>(null) }
    var editSlotDraft by remember(detail.summary.courseId) { mutableStateOf(ScheduleSlotDraft(room = "")) }
    var editSlotErrorMessage by remember(detail.summary.courseId) { mutableStateOf<String?>(null) }

    // Confirmação de exclusão da disciplina
    var confirmDeleteCourse by remember(detail.summary.courseId) { mutableStateOf(false) }

    // Estado do formulário inline "Adicionar prazo"
    var showAddForm by remember(detail.summary.courseId) { mutableStateOf(false) }
    var newTitle by remember(detail.summary.courseId) { mutableStateOf("") }
    var newKind by remember(detail.summary.courseId) { mutableStateOf("Trabalho") }
    var newDateText by remember(detail.summary.courseId) { mutableStateOf(defaultTodayIsoDate()) }
    var newTimeText by remember(detail.summary.courseId) { mutableStateOf("") }

    val handleAddScheduleSlot: () -> Unit = {
        val cleanStart = slotDraft.startTime.trim().ifBlank { "08:00" }
        val cleanEnd = slotDraft.endTime.trim().ifBlank { "09:40" }
        val cleanBuilding = slotDraft.building.trim().ifBlank { "ICEB (III)" }
        val cleanRoom = slotDraft.room.trim()
        val cleanShort = buildShortLocationPreview(cleanBuilding, cleanRoom)
        val result = if (onAddScheduleSlot != null) {
            onAddScheduleSlot(
                slotDraft.dayOfWeek,
                cleanStart,
                cleanEnd,
                cleanBuilding,
                cleanRoom,
                cleanShort
            )
        } else {
            runCatching {
                val repo = DisciplinesViewModel.sharedRepository
                    ?: throw IllegalStateException("Repositório indisponível.")
                repo.addScheduleSlot(
                    courseId = detail.summary.courseId,
                    dayOfWeek = slotDraft.dayOfWeek,
                    startTime = cleanStart,
                    endTime = cleanEnd,
                    building = cleanBuilding,
                    room = cleanRoom,
                    shortLocation = cleanShort
                )
            }
        }
        result.onSuccess {
            slotErrorMessage = null
            showAddSlotForm = false
        }.onFailure { throwable ->
            slotErrorMessage = throwable.message ?: "Conflito de horário ao adicionar aula."
        }
    }

    val handleUpdateScheduleSlot: (CourseScheduleSlotItem) -> Unit = { originalSlot ->
        val cleanStart = editSlotDraft.startTime.trim().ifBlank { originalSlot.startTime }
        val cleanEnd = editSlotDraft.endTime.trim().ifBlank { originalSlot.endTime }
        val cleanBuilding = editSlotDraft.building.trim().ifBlank { originalSlot.building }
        val cleanRoom = editSlotDraft.room.trim()
        val cleanShort = buildShortLocationPreview(cleanBuilding, cleanRoom)
        val result = if (onUpdateScheduleSlot != null) {
            onUpdateScheduleSlot(
                originalSlot.slotId,
                editSlotDraft.dayOfWeek,
                cleanStart,
                cleanEnd,
                cleanBuilding,
                cleanRoom,
                cleanShort
            )
        } else {
            runCatching {
                val repo = DisciplinesViewModel.sharedRepository
                    ?: throw IllegalStateException("Repositório indisponível.")
                repo.updateScheduleSlot(
                    slotId = originalSlot.slotId,
                    dayOfWeek = editSlotDraft.dayOfWeek,
                    startTime = cleanStart,
                    endTime = cleanEnd,
                    building = cleanBuilding,
                    room = cleanRoom,
                    shortLocation = cleanShort
                )
            }
        }
        result.onSuccess {
            editSlotErrorMessage = null
            editingSlotId = null
        }.onFailure { throwable ->
            editSlotErrorMessage = throwable.message ?: "Conflito de horário ao atualizar aula."
        }
    }

    val handleDeleteScheduleSlot: (String) -> Unit = { slotId ->
        if (editingSlotId == slotId) {
            editingSlotId = null
            editSlotErrorMessage = null
        }
        if (onDeleteScheduleSlot != null) {
            onDeleteScheduleSlot(slotId)
        } else {
            DisciplinesViewModel.sharedRepository?.deleteScheduleSlot(slotId)
        }
    }

    val handleDeleteCourse: () -> Unit = {
        if (onDeleteCourse != null) {
            onDeleteCourse()
        } else {
            DisciplinesViewModel.sharedRepository?.deleteCourse(detail.summary.courseId)
        }
        confirmDeleteCourse = false
        onDismiss()
    }

    val handleToggleCompleted: (AssessmentItem) -> Unit = { item ->
        val targetCompleted = !item.completed
        if (onToggleAssessmentCompleted != null) {
            onToggleAssessmentCompleted(item.id, targetCompleted)
        } else {
            DisciplinesViewModel.sharedRepository?.toggleAssessmentCompleted(
                assessmentId = item.id,
                completed = targetCompleted
            )
        }
    }

    val handleCreateAssessment: () -> Unit = {
        val cleanTitle = newTitle.trim()
        if (cleanTitle.isNotEmpty()) {
            val formattedDueDate = normalizeAssessmentDateInput(newDateText)
            val formattedDueTime = newTimeText.trim().ifBlank { "18:00" }
            if (onCreateAssessment != null) {
                onCreateAssessment(cleanTitle, newKind, formattedDueDate, formattedDueTime)
            } else {
                DisciplinesViewModel.sharedRepository?.createAssessment(
                    courseId = detail.summary.courseId,
                    title = cleanTitle,
                    kind = newKind,
                    dueDate = formattedDueDate,
                    dueTime = formattedDueTime
                )
            }
            newTitle = ""
            newKind = "Trabalho"
            newDateText = defaultTodayIsoDate()
            newTimeText = ""
            showAddForm = false
        }
    }

    val handleDeleteAssessment: (AssessmentItem) -> Unit = { item ->
        if (onDeleteAssessment != null) {
            onDeleteAssessment(item.id)
        } else {
            DisciplinesViewModel.sharedRepository?.deleteAssessment(item.id)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 6.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
        ) {
            val courseCode = effectiveDetail.code.ifBlank { effectiveSummary.code }
            // Header: Departamento + Código + Título Grande + Botão Fechar (>= 48.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                        Text(
                            text = effectiveSummary.departmentName,
                            style = MaterialTheme.typography.labelLarge,
                            color = accessibleAccent,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (courseCode.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = accentColor.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f))
                            ) {
                                Text(
                                    text = courseCode,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accessibleAccent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = effectiveSummary.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${effectiveSummary.workloadHours} h · ${effectiveDetail.semester}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                contentDescription = "Fechar detalhes da disciplina",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card rico: Informações da disciplina (Docente, Local, CH e Limite) + Editar
            val totalClasses = ((effectiveSummary.workloadHours * 60) / 50).coerceAtLeast(1)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Informações da disciplina",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                        Surface(
                            onClick = {
                                editCourseName = effectiveSummary.name
                                editCourseProfessor = effectiveDetail.professor
                                editCourseWorkloadText = effectiveSummary.workloadHours.toString()
                                showEditCourseDialog = true
                            },
                            modifier = if (a11y.largeTouchTargets) {
                                Modifier.defaultMinSize(minHeight = minTouchTarget)
                            } else {
                                Modifier.minimumInteractiveComponentSize()
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = accentColor.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = secondaryChipHeight)
                                    .padding(
                                        horizontal = 12.dp,
                                        vertical = if (a11y.largeTouchTargets) 10.dp else 6.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Editar informações da disciplina",
                                    tint = accessibleAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Editar",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accessibleAccent,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    DisciplineInfoDetailRow(
                        icon = Icons.Outlined.Person,
                        label = "Professor(a)",
                        value = effectiveDetail.professor.ifBlank { "Não informado (toque em Editar para adicionar)" },
                        accentColor = accessibleAccent
                    )

                    DisciplineInfoDetailRow(
                        icon = Icons.Outlined.LocationOn,
                        label = "Prédio / Sala",
                        value = effectiveDetail.buildingAndRoom.ifBlank {
                            effectiveDetail.shortLocation.ifBlank { "Campus UFOP" }
                        },
                        accentColor = accessibleAccent
                    )

                    DisciplineInfoDetailRow(
                        icon = Icons.Outlined.School,
                        label = "Carga horária e limite",
                        value = "${effectiveSummary.workloadHours}h no semestre (${totalClasses} aulas de 50 min) · Limite de ${effectiveSummary.maxAbsences} faltas (25%)",
                        accentColor = accessibleAccent
                    )
                }
            }

            if (showEditCourseDialog) {
                val parsedWorkload = editCourseWorkloadText.toIntOrNull()?.coerceIn(15, 360)
                AlertDialog(
                    onDismissRequest = { showEditCourseDialog = false },
                    shape = RoundedCornerShape(24.dp),
                    title = {
                        Text(
                            text = "Editar disciplina",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = editCourseName,
                                onValueChange = { editCourseName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Nome da disciplina") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )
                            OutlinedTextField(
                                value = editCourseProfessor,
                                onValueChange = { editCourseProfessor = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Professor(a)") },
                                placeholder = { Text("Opcional · deixe em branco para preencher depois") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )
                            Text(
                                text = "Carga horária (h)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                EDIT_WORKLOAD_PRESETS.forEach { preset ->
                                    FilterChip(
                                        selected = parsedWorkload == preset,
                                        onClick = { editCourseWorkloadText = preset.toString() },
                                        modifier = Modifier.heightIn(min = secondaryChipHeight),
                                        label = { Text("${preset}h") },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = accentColor.copy(alpha = 0.18f),
                                            selectedLabelColor = accessibleAccent
                                        )
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = editCourseWorkloadText,
                                onValueChange = { raw ->
                                    editCourseWorkloadText = raw.filter { it.isDigit() }.take(3)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Carga horária personalizada") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showEditCourseDialog = false },
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
                                val cleanName = editCourseName.trim().ifBlank { effectiveSummary.name }
                                val cleanProf = editCourseProfessor.trim()
                                val targetHours = parsedWorkload ?: effectiveSummary.workloadHours
                                if (onUpdateCourseDetails != null) {
                                    onUpdateCourseDetails(
                                        cleanName,
                                        cleanProf,
                                        effectiveSummary.departmentId,
                                        targetHours
                                    )
                                } else {
                                    fallbackRepo?.updateCourse(
                                        courseId = effectiveSummary.courseId,
                                        name = cleanName,
                                        professor = cleanProf,
                                        departmentId = effectiveSummary.departmentId,
                                        workloadHours = targetHours
                                    )
                                }
                                showEditCourseDialog = false
                            },
                            enabled = editCourseName.isNotBlank() && parsedWorkload != null,
                            modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            Text("Salvar")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card de Faltas em destaque com semáforo semântico + editor manual (- / Editar / +)
            val currentAbsences = effectiveSummary.currentAbsences
            val maxAbsences = effectiveSummary.maxAbsences
            val absenceRatio = (currentAbsences.toFloat() /
                maxAbsences.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
            val riskLevel = semanticAbsenceRiskLevel(currentAbsences, maxAbsences)
            val riskLabel = semanticAbsenceRiskLabel(currentAbsences, maxAbsences)
            val indicatorColor = semanticAbsenceRiskColor(currentAbsences, maxAbsences, isDark)
            val riskIcon = when (riskLevel) {
                2 -> Icons.Outlined.ErrorOutline
                1 -> Icons.Outlined.WarningAmber
                else -> Icons.Outlined.CheckCircle
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(
                                        SpanStyle(
                                            fontSize = 30.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        append("$currentAbsences")
                                    }
                                    withStyle(
                                        SpanStyle(
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        append(" / $maxAbsences")
                                    }
                                    withStyle(
                                        SpanStyle(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        append(" faltas")
                                    }
                                },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = minTouchTarget)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = openEditAbsencesDialog)
                                    .padding(vertical = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = indicatorColor.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, indicatorColor.copy(alpha = 0.32f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = riskIcon,
                                        contentDescription = null,
                                        tint = indicatorColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = riskLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = indicatorColor,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { handleUpdateAbsences((currentAbsences - 1).coerceAtLeast(0)) },
                                enabled = currentAbsences > 0,
                                modifier = Modifier.size(minTouchTarget)
                            ) {
                                Surface(
                                    modifier = Modifier.size(if (a11y.largeTouchTargets) 44.dp else 38.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(
                                        alpha = if (currentAbsences > 0) 1f else 0.5f
                                    )
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Remove,
                                            contentDescription = "Diminuir falta",
                                            tint = MaterialTheme.colorScheme.onSurface.copy(
                                                alpha = if (currentAbsences > 0) 1f else 0.38f
                                            ),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Surface(
                                onClick = openEditAbsencesDialog,
                                modifier = if (a11y.largeTouchTargets) {
                                    Modifier.defaultMinSize(minHeight = minTouchTarget)
                                } else {
                                    Modifier.minimumInteractiveComponentSize()
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = accentColor.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .heightIn(min = secondaryChipHeight)
                                        .padding(
                                            horizontal = 10.dp,
                                            vertical = if (a11y.largeTouchTargets) 10.dp else 6.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "Editar faltas manualmente",
                                        tint = accessibleAccent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Editar",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = accessibleAccent,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            IconButton(
                                onClick = { handleUpdateAbsences(currentAbsences + 1) },
                                modifier = Modifier.size(minTouchTarget)
                            ) {
                                Surface(
                                    modifier = Modifier.size(if (a11y.largeTouchTargets) 44.dp else 38.dp),
                                    shape = CircleShape,
                                    color = accentColor.copy(alpha = 0.16f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "Adicionar falta",
                                            tint = accessibleAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { absenceRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = indicatorColor,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Use − / + ou toque em Editar para informar o total de faltas diretamente.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showEditAbsencesDialog) {
                val parsedDraft = absencesInputDraft.toIntOrNull()?.coerceAtLeast(0)
                AlertDialog(
                    onDismissRequest = { showEditAbsencesDialog = false },
                    shape = RoundedCornerShape(24.dp),
                    title = {
                        Text(
                            text = "Editar total de faltas",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "${effectiveSummary.name} · limite de $maxAbsences faltas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilledTonalIconButton(
                                    onClick = {
                                        val next = ((parsedDraft ?: currentAbsences) - 1).coerceAtLeast(0)
                                        absencesInputDraft = next.toString()
                                    },
                                    enabled = (parsedDraft ?: currentAbsences) > 0,
                                    modifier = Modifier.size(minTouchTarget)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Remove,
                                        contentDescription = "Diminuir falta"
                                    )
                                }

                                OutlinedTextField(
                                    value = absencesInputDraft,
                                    onValueChange = { raw ->
                                        absencesInputDraft = raw.filter { it.isDigit() }.take(3)
                                    },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Total de faltas") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(14.dp)
                                )

                                FilledTonalIconButton(
                                    onClick = {
                                        val next = ((parsedDraft ?: currentAbsences) + 1).coerceAtMost(999)
                                        absencesInputDraft = next.toString()
                                    },
                                    modifier = Modifier.size(minTouchTarget),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = accentColor.copy(alpha = 0.16f),
                                        contentColor = accessibleAccent
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Adicionar falta"
                                    )
                                }
                            }

                            Text(
                                text = "O ajuste manual é somado ou descontado sem apagar seu histórico de marcações na Agenda.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showEditAbsencesDialog = false },
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
                                val nextTotal = parsedDraft ?: currentAbsences
                                handleUpdateAbsences(nextTotal)
                                showEditAbsencesDialog = false
                            },
                            enabled = parsedDraft != null,
                            modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            Text("Aplicar")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 1. Acordeão: Histórico de marcações (Faltas e Cancelamentos) — fechado por padrão
            val totalAbsenceWeight = resolvedAbsenceHistory.sumOf { it.classWeight }
            val historySummaryText = if (resolvedAbsenceHistory.isEmpty()) {
                "0 registros"
            } else {
                val regWord = if (resolvedAbsenceHistory.size == 1) "registro" else "registros"
                "${resolvedAbsenceHistory.size} $regWord ($totalAbsenceWeight faltas)"
            }

            DisciplineAccordionCard(
                title = "Histórico de marcações",
                summaryPillText = historySummaryText,
                accentColor = accentColor,
                accessibleAccent = accessibleAccent,
                expanded = historyExpanded,
                reduceMotion = a11y.reduceMotion,
                minTouchTarget = minTouchTarget,
                onToggle = { historyExpanded = !historyExpanded }
            ) {
                Text(
                    text = if (resolvedAbsenceHistory.isEmpty()) {
                        "Nenhuma sessão marcada como falta ou cancelada"
                    } else {
                        "$historySummaryText em sessões de aula"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (resolvedAbsenceHistory.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = "Nenhuma falta ou aula cancelada foi registrada nas sessões de aula desta disciplina até o momento. As marcações feitas na Agenda aparecerão aqui.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        resolvedAbsenceHistory.forEach { entry ->
                            AbsenceHistoryRowCard(entry = entry, isDark = isDark)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Acordeão: Horários semanais — fechado por padrão
            val slotsSummaryPill = if (resolvedScheduleSlots.size == 1) {
                "1 horário"
            } else {
                "${resolvedScheduleSlots.size} horários"
            }

            DisciplineAccordionCard(
                title = "Horários semanais",
                summaryPillText = slotsSummaryPill,
                accentColor = accentColor,
                accessibleAccent = accessibleAccent,
                expanded = slotsExpanded,
                reduceMotion = a11y.reduceMotion,
                minTouchTarget = minTouchTarget,
                onToggle = { slotsExpanded = !slotsExpanded }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (resolvedScheduleSlots.size == 1) {
                            "1 horário na semana"
                        } else {
                            "${resolvedScheduleSlots.size} horários na semana"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    FilledTonalButton(
                        onClick = {
                            showAddSlotForm = !showAddSlotForm
                            slotErrorMessage = null
                            if (showAddSlotForm) {
                                slotDraft = ScheduleSlotDraft(room = "")
                                editingSlotId = null
                                editSlotErrorMessage = null
                            }
                        },
                        modifier = Modifier.defaultMinSize(minHeight = secondaryControlHeight),
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = accentColor.copy(alpha = 0.14f),
                            contentColor = accessibleAccent
                        )
                    ) {
                        Icon(
                            imageVector = if (showAddSlotForm) Icons.Default.Close else Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showAddSlotForm) "Fechar" else "Adicionar horário",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (showAddSlotForm) MaterialTheme.colorScheme.onSurfaceVariant else accessibleAccent
                        )
                    }
                }

                if (showAddSlotForm) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Novo horário para ${effectiveSummary.name}",
                                style = MaterialTheme.typography.labelLarge,
                                color = accentColor,
                                fontWeight = FontWeight.Bold
                            )

                            DetailScheduleSlotEditorFields(
                                draft = slotDraft,
                                accentColor = accentColor,
                                chipMinHeight = secondaryChipHeight,
                                onDraftChange = { updated ->
                                    slotDraft = updated
                                    slotErrorMessage = null
                                }
                            )

                            if (slotErrorMessage != null) {
                                InlineErrorBanner(message = slotErrorMessage.orEmpty())
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        showAddSlotForm = false
                                        slotErrorMessage = null
                                    },
                                    modifier = Modifier.defaultMinSize(minHeight = secondaryControlHeight),
                                    shape = RoundedCornerShape(22.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text("Cancelar")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = handleAddScheduleSlot,
                                    modifier = Modifier.defaultMinSize(minHeight = secondaryControlHeight),
                                    shape = RoundedCornerShape(22.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Salvar horário")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (resolvedScheduleSlots.isEmpty()) {
                    Text(
                        text = "Nenhum horário semanal cadastrado para esta disciplina.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        resolvedScheduleSlots.forEach { slot ->
                            val isEditingThisSlot = editingSlotId == slot.slotId
                            ScheduleSlotRowCard(
                                slot = slot,
                                accentColor = accentColor,
                                accessibleAccent = accessibleAccent,
                                isEditing = isEditingThisSlot,
                                minTouchTarget = minTouchTarget,
                                onEdit = {
                                    if (isEditingThisSlot) {
                                        editingSlotId = null
                                        editSlotErrorMessage = null
                                    } else {
                                        editingSlotId = slot.slotId
                                        editSlotDraft = ScheduleSlotDraft(
                                            dayOfWeek = slot.dayOfWeek,
                                            startTime = slot.startTime,
                                            endTime = slot.endTime,
                                            building = slot.building,
                                            room = slot.room
                                        )
                                        editSlotErrorMessage = null
                                        showAddSlotForm = false
                                    }
                                },
                                onDelete = { handleDeleteScheduleSlot(slot.slotId) }
                            )

                            if (isEditingThisSlot) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Editar horário (${slot.dayLabel} ${slot.startTime}–${slot.endTime})",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = accentColor,
                                            fontWeight = FontWeight.Bold
                                        )

                                        DetailScheduleSlotEditorFields(
                                            draft = editSlotDraft,
                                            accentColor = accentColor,
                                            chipMinHeight = secondaryChipHeight,
                                            onDraftChange = { updated ->
                                                editSlotDraft = updated
                                                editSlotErrorMessage = null
                                            }
                                        )

                                        if (editSlotErrorMessage != null) {
                                            InlineErrorBanner(message = editSlotErrorMessage.orEmpty())
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    editingSlotId = null
                                                    editSlotErrorMessage = null
                                                },
                                                modifier = Modifier.defaultMinSize(minHeight = secondaryControlHeight),
                                                shape = RoundedCornerShape(22.dp),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            ) {
                                                Text("Cancelar")
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = { handleUpdateScheduleSlot(slot) },
                                                modifier = Modifier.defaultMinSize(minHeight = secondaryControlHeight),
                                                shape = RoundedCornerShape(22.dp),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Salvar alterações")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Acordeão: Prazos e avaliações — fechado por padrão
            val completedAssessmentsCount = resolvedAssessments.count { it.completed }
            val assessmentsSummaryPill = "$completedAssessmentsCount/${resolvedAssessments.size} concluídas"

            DisciplineAccordionCard(
                title = "Prazos e avaliações",
                summaryPillText = assessmentsSummaryPill,
                accentColor = accentColor,
                accessibleAccent = accessibleAccent,
                expanded = assessmentsExpanded,
                reduceMotion = a11y.reduceMotion,
                minTouchTarget = minTouchTarget,
                onToggle = { assessmentsExpanded = !assessmentsExpanded }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$completedAssessmentsCount de ${resolvedAssessments.size} concluídas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    FilledTonalButton(
                        onClick = { showAddForm = !showAddForm },
                        modifier = Modifier.defaultMinSize(minHeight = secondaryControlHeight),
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = accentColor.copy(alpha = 0.14f),
                            contentColor = accessibleAccent
                        )
                    ) {
                        Icon(
                            imageVector = if (showAddForm) Icons.Default.Close else Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showAddForm) "Fechar" else "Adicionar prazo",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (showAddForm) MaterialTheme.colorScheme.onSurfaceVariant else accessibleAccent
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Botão Material 3 Expressive: Importar Plano de Ensino (PDF)
                OutlinedButton(
                    onClick = {
                        syllabusFeedbackMessage = null
                        syllabusPdfLauncher.launch(arrayOf("application/pdf"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = secondaryControlHeight),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = accessibleAccent
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Importar Plano de Ensino (PDF)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                syllabusFeedbackMessage?.let { feedback ->
                    Spacer(modifier = Modifier.height(10.dp))
                    val bannerColor = when (syllabusFeedbackTone) {
                        SyllabusFeedbackTone.ERROR -> StatusAbsentRed
                        SyllabusFeedbackTone.WARNING -> StatusCancelledAmber
                        SyllabusFeedbackTone.SUCCESS -> StatusAttendingGreen
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { syllabusFeedbackMessage = null },
                        shape = RoundedCornerShape(14.dp),
                        color = bannerColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = feedback,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = bannerColor,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "✕",
                                style = MaterialTheme.typography.labelMedium,
                                color = bannerColor
                            )
                        }
                    }
                }

                if (showAddForm) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Novo prazo em ${effectiveSummary.name}",
                                style = MaterialTheme.typography.labelLarge,
                                color = accentColor,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = newTitle,
                                onValueChange = { newTitle = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Título da avaliação ou entrega") },
                                placeholder = { Text("Ex.: Lista 3 de Exercícios") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Prova", "Trabalho", "Seminário").forEach { kindOption ->
                                    val selected = newKind == kindOption
                                    FilterChip(
                                        selected = selected,
                                        onClick = { newKind = kindOption },
                                        modifier = Modifier.heightIn(min = secondaryChipHeight),
                                        label = { Text(kindOption) },
                                        shape = RoundedCornerShape(22.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = accentColor.copy(alpha = 0.18f),
                                            selectedLabelColor = accessibleAccent
                                        )
                                    )
                                }
                            }

                            CampusDatePickerField(
                                label = "Data da entrega/avaliação",
                                isoDate = newDateText,
                                onIsoDateSelected = { newDateText = it },
                                minHeight = minTouchTarget
                            )

                            CampusTimePickerField(
                                label = "Horário (opcional)",
                                time24h = newTimeText,
                                onTimeSelected = { newTimeText = it },
                                minHeight = minTouchTarget
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { showAddForm = false },
                                    modifier = Modifier.defaultMinSize(minHeight = secondaryControlHeight),
                                    shape = RoundedCornerShape(22.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text("Cancelar")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = handleCreateAssessment,
                                    enabled = newTitle.isNotBlank(),
                                    modifier = Modifier.defaultMinSize(minHeight = secondaryControlHeight),
                                    shape = RoundedCornerShape(22.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Salvar prazo")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (resolvedAssessments.isEmpty()) {
                    Text(
                        text = "Nenhuma avaliação cadastrada para esta disciplina.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        resolvedAssessments.forEach { item ->
                            AssessmentRowCard(
                                item = item,
                                accentColor = accentColor,
                                isDark = isDark,
                                minTouchTarget = minTouchTarget,
                                onToggleCompleted = { handleToggleCompleted(item) },
                                onDelete = null,
                                onClick = { selectedAssessmentForDetailId = item.id }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Acordeão: Anotações — fechado por padrão
            val hasUnsavedNotesChanges = notesDraft != effectiveDetail.notes
            val notesSummaryPill = when {
                hasUnsavedNotesChanges -> "Editando"
                notesDraft.isBlank() -> "Sem notas"
                else -> "Salva"
            }

            DisciplineAccordionCard(
                title = "Anotações",
                summaryPillText = notesSummaryPill,
                accentColor = accentColor,
                accessibleAccent = accessibleAccent,
                expanded = notesExpanded,
                reduceMotion = a11y.reduceMotion,
                minTouchTarget = minTouchTarget,
                onToggle = { notesExpanded = !notesExpanded }
            ) {
                if (hasUnsavedNotesChanges || savedConfirmation) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                            onClick = {
                                onSaveNotes(notesDraft.trim())
                                savedConfirmation = true
                            },
                            modifier = Modifier.defaultMinSize(minHeight = secondaryControlHeight),
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = accentColor.copy(alpha = 0.14f),
                                contentColor = accessibleAccent
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasUnsavedNotesChanges) "Salvar anotação" else "Salvo",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = notesDraft,
                    onValueChange = {
                        notesDraft = it
                        savedConfirmation = false
                        onSaveNotes(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("Adicione lembretes, tópicos de revisão ou observações da disciplina...")
                    },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Ação destrutiva: Remover disciplina (com confirmação)
            val accessibleDeleteRed = accessibleTextColor(StatusAbsentRed, isDark)
            if (!confirmDeleteCourse) {
                OutlinedButton(
                    onClick = { confirmDeleteCourse = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = secondaryControlHeight),
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    border = BorderStroke(1.dp, StatusAbsentRed.copy(alpha = 0.50f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = accessibleDeleteRed
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Remover disciplina",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = StatusAbsentRed.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, StatusAbsentRed.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Remover ${effectiveSummary.name}?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = StatusAbsentRed
                        )
                        Text(
                            text = "Esta ação exclui a disciplina, seus horários semanais e avaliações vinculadas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { confirmDeleteCourse = false },
                                modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text("Cancelar")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = handleDeleteCourse,
                                modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StatusAbsentRed,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("Confirmar remoção")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Rodapé discreto no fim da folha com aviso de aulas canceladas (Item 5a)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Aulas canceladas (${effectiveSummary.cancelledClasses}) não somam faltas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "As marcações salvas na Agenda atualizam automaticamente este relatório.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }

    val selectedAssessmentItem = remember(selectedAssessmentForDetailId, resolvedAssessments) {
        selectedAssessmentForDetailId?.let { targetId ->
            resolvedAssessments.find { it.id == targetId }
        }
    }
    if (selectedAssessmentItem != null) {
        val semesterDisciplines = remember(effectiveDetail.semester, fallbackVersion) {
            fallbackRepo?.listDisciplines(effectiveDetail.semester, null).orEmpty()
        }
        val assessmentEntry = AssessmentWithCourse(
            assessment = selectedAssessmentItem,
            courseName = effectiveSummary.name,
            departmentName = effectiveSummary.departmentName,
            colorHex = effectiveSummary.colorHex
        )
        AssessmentDetailBottomSheet(
            entry = assessmentEntry,
            disciplines = semesterDisciplines,
            onDismiss = { selectedAssessmentForDetailId = null },
            onToggleCompleted = { completed ->
                if (onToggleAssessmentCompleted != null) {
                    onToggleAssessmentCompleted(selectedAssessmentItem.id, completed)
                } else {
                    fallbackRepo?.toggleAssessmentCompleted(selectedAssessmentItem.id, completed)
                }
            },
            onSaveEdit = { targetCourseId, title, kind, dueDate, dueTime, completed ->
                if (onUpdateAssessment != null) {
                    onUpdateAssessment(
                        selectedAssessmentItem.id,
                        targetCourseId,
                        title,
                        kind,
                        dueDate,
                        dueTime,
                        completed
                    )
                } else {
                    fallbackRepo?.updateAssessment(
                        assessmentId = selectedAssessmentItem.id,
                        courseId = targetCourseId,
                        title = title,
                        kind = kind,
                        dueDate = dueDate,
                        dueTime = dueTime,
                        completed = completed
                    )
                }
                if (targetCourseId != effectiveSummary.courseId) {
                    selectedAssessmentForDetailId = null
                }
            },
            onDelete = {
                val targetId = selectedAssessmentItem.id
                selectedAssessmentForDetailId = null
                handleDeleteAssessment(selectedAssessmentItem.copy(id = targetId))
            },
            onOpenDiscipline = null
        )
    }
}

@Composable
private fun DisciplineInfoDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(10.dp),
            color = accentColor.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DisciplineAccordionCard(
    title: String,
    summaryPillText: String,
    accentColor: Color,
    accessibleAccent: Color = accentColor,
    expanded: Boolean,
    reduceMotion: Boolean = false,
    minTouchTarget: Dp = 48.dp,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduceMotion) snap() else spring(),
        label = "DisciplineAccordionChevron"
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            width = 1.dp,
            color = if (expanded) {
                accentColor.copy(alpha = 0.38f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = minTouchTarget)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accentColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f))
                ) {
                    Text(
                        text = summaryPillText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accessibleAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Recolher $title" else "Expandir $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(chevronRotation)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = if (reduceMotion) EnterTransition.None else expandVertically() + fadeIn(),
                exit = if (reduceMotion) ExitTransition.None else shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun ScheduleSlotRowCard(
    slot: CourseScheduleSlotItem,
    accentColor: androidx.compose.ui.graphics.Color,
    accessibleAccent: androidx.compose.ui.graphics.Color = accentColor,
    isEditing: Boolean,
    minTouchTarget: Dp = 48.dp,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isEditing) {
            accentColor.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isEditing) {
                accentColor.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = accessibleAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                val timeAndShort = if (slot.shortLocation.isNotBlank()) {
                    "${slot.dayLabel} ${slot.startTime}–${slot.endTime} · ${slot.shortLocation}"
                } else {
                    "${slot.dayLabel} ${slot.startTime}–${slot.endTime}"
                }
                val buildingAndRoomText = if (slot.room.isNotBlank()) {
                    "${slot.building} · ${slot.room}"
                } else {
                    slot.building
                }
                Text(
                    text = timeAndShort,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (buildingAndRoomText.isNotBlank()) {
                    Text(
                        text = buildingAndRoomText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(minTouchTarget)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Editar horário",
                    tint = if (isEditing) accessibleAccent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(minTouchTarget)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Remover horário",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AbsenceHistoryRowCard(
    entry: AbsenceHistoryEntry,
    isDark: Boolean = false
) {
    val isAbsent = entry.status == AttendanceStatus.ABSENT
    val pillColor = accessibleTextColor(
        baseColor = if (isAbsent) StatusAbsentRed else StatusAttendingGreen,
        isDarkSurface = isDark
    )
    val pillLabel = if (isAbsent) {
        "Falta (+${entry.classWeight})"
    } else {
        "Cancelada (0 faltas)"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.formattedDate,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${entry.startTime}–${entry.endTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = pillColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, pillColor.copy(alpha = 0.35f))
            ) {
                Text(
                    text = pillLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = pillColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
internal fun AssessmentRowCard(
    item: AssessmentItem,
    accentColor: androidx.compose.ui.graphics.Color,
    courseBadgeText: String? = null,
    isDark: Boolean = LocalCampusThemeController.current.isDark,
    minTouchTarget: Dp = 48.dp,
    onToggleCompleted: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val isCompleted = item.completed
    val baseBadgeColor = if (isCompleted) StatusAttendingGreen else accentColor
    val badgeColor = accessibleTextColor(baseBadgeColor, isDark)

    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (isCompleted) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isCompleted) {
                StatusAttendingGreen.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onToggleCompleted != null) {
                IconButton(
                    onClick = onToggleCompleted,
                    modifier = Modifier.size(minTouchTarget)
                ) {
                    Icon(
                        imageVector = if (isCompleted) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Outlined.RadioButtonUnchecked
                        },
                        contentDescription = if (isCompleted) {
                            "Marcar como pendente"
                        } else {
                            "Marcar como concluída"
                        },
                        tint = if (isCompleted) {
                            accessibleTextColor(StatusAttendingGreen, isDark)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bloco de data à esquerda em pílula quadrada (ex: 20 NOV, 24 NOV, 27 NOV)
                Surface(
                    modifier = Modifier.size(width = 52.dp, height = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = baseBadgeColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, baseBadgeColor.copy(alpha = 0.28f))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = item.dayNumber,
                            style = MaterialTheme.typography.titleMedium,
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

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (!courseBadgeText.isNullOrBlank()) {
                        Text(
                            text = courseBadgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = accessibleTextColor(accentColor, isDark),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isCompleted) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (isCompleted) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val statusSuffix = if (isCompleted) " · Concluída" else ""
                    val timePart = if (item.dueTime.isNotBlank()) " · ${item.dueTime}" else ""
                    Text(
                        text = "${item.kind}$timePart$statusSuffix",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCompleted) {
                            accessibleTextColor(StatusAttendingGreen, isDark)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(minTouchTarget)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Remover avaliação",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal fun normalizeAssessmentDateInput(rawDate: String): String {
    val trimmed = rawDate.trim()
    if (trimmed.isEmpty()) return "2026-11-28"
    if (trimmed.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
        return trimmed
    }
    val parts = trimmed.uppercase().split(Regex("[\\s/.-]+")).filter { it.isNotBlank() }
    if (parts.size >= 2) {
        val day = parts[0].padStart(2, '0').takeLast(2)
        val monthMap = mapOf(
            "JAN" to "01", "FEV" to "02", "MAR" to "03", "ABR" to "04",
            "MAI" to "05", "JUN" to "06", "JUL" to "07", "AGO" to "08",
            "SET" to "09", "OUT" to "10", "NOV" to "11", "DEZ" to "12"
        )
        val monthToken = parts[1]
        val monthNum = monthMap[monthToken.take(3)] ?: monthToken.padStart(2, '0').takeLast(2)
        val year = if (parts.size >= 3 && parts[2].matches(Regex("\\d{4}"))) parts[2] else "2026"
        return "$year-$monthNum-$day"
    }
    return trimmed
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailScheduleSlotEditorFields(
    draft: ScheduleSlotDraft,
    accentColor: Color,
    chipMinHeight: Dp,
    onDraftChange: (ScheduleSlotDraft) -> Unit,
    ufopBuildings: List<UfopBuildingOption>? = null
) {
    val resolvedBuildings = remember(ufopBuildings) {
        val base = (ufopBuildings ?: DisciplinesViewModel.sharedRepository?.listUfopBuildings())
            .orEmpty()
            .ifEmpty { DEFAULT_UFOP_BUILDINGS }
        if (base.any { isEadBuildingLabel(it.shortName) }) {
            base
        } else {
            base + UfopBuildingOption(
                id = "ufop_ead",
                campusId = "ead",
                campusName = "EAD",
                shortName = "Ensino a Distância (EAD)",
                fullName = "Ensino a Distância (AVA / Moodle / Remoto)"
            )
        }
    }

    val campusOptions = listOf("Todos", "Ouro Preto", "Mariana", "João Monlevade", "EAD")
    var selectedCampusFilter by remember { mutableStateOf("Todos") }
    var showBuildingPickerDialog by remember { mutableStateOf(false) }

    val applySelectedBuilding: (String) -> Unit = { selectedShortName ->
        val nextRoom = when {
            !isEadBuildingLabel(selectedShortName) && draft.room == "Online" -> ""
            else -> draft.room
        }
        onDraftChange(draft.copy(building = selectedShortName, room = nextRoom))
    }

    val filteredBuildings = remember(resolvedBuildings, selectedCampusFilter) {
        if (selectedCampusFilter == "Todos") {
            resolvedBuildings
        } else {
            resolvedBuildings.filter { it.campusName.equals(selectedCampusFilter, ignoreCase = true) }
        }
    }

    val matchedBuilding = remember(resolvedBuildings, draft.building) {
        resolvedBuildings.find {
            it.shortName.equals(draft.building.trim(), ignoreCase = true) ||
                it.fullName.equals(draft.building.trim(), ignoreCase = true)
        }
    }

    if (showBuildingPickerDialog) {
        DetailUfopBuildingPickerDialog(
            buildings = resolvedBuildings,
            selectedBuildingShortName = draft.building,
            accentColor = accentColor,
            onSelectBuilding = { chosen ->
                applySelectedBuilding(chosen.shortName)
                showBuildingPickerDialog = false
            },
            onDismiss = { showBuildingPickerDialog = false }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Dia da semana",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WEEK_DAY_OPTIONS.forEach { (dayNum, label) ->
                val isSelected = draft.dayOfWeek == dayNum
                FilterChip(
                    selected = isSelected,
                    onClick = { onDraftChange(draft.copy(dayOfWeek = dayNum)) },
                    modifier = Modifier.heightIn(min = chipMinHeight),
                    label = { Text(label) },
                    shape = RoundedCornerShape(22.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor.copy(alpha = 0.18f),
                        selectedLabelColor = accentColor
                    )
                )
            }
        }

        Text(
            text = "Horário",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TIME_SLOT_PRESETS.forEach { (start, end) ->
                val isSelected = draft.startTime == start && draft.endTime == end
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onDraftChange(draft.copy(startTime = start, endTime = end))
                    },
                    modifier = Modifier.heightIn(min = chipMinHeight),
                    label = { Text("$start–$end") },
                    shape = RoundedCornerShape(22.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor.copy(alpha = 0.18f),
                        selectedLabelColor = accentColor
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = draft.startTime,
                onValueChange = { onDraftChange(draft.copy(startTime = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("Início") },
                placeholder = { Text("08:00") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                value = draft.endTime,
                onValueChange = { onDraftChange(draft.copy(endTime = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("Término") },
                placeholder = { Text("09:40") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Prédio da UFOP",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = matchedBuilding?.let { "${it.campusName} · ${it.fullName}" }
                        ?: "Ouro Preto · Mariana · João Monlevade",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Surface(
                onClick = { showBuildingPickerDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = accentColor.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = matchedBuilding?.shortName ?: draft.building.ifBlank { "Selecionar prédio da UFOP" },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = matchedBuilding?.let { "${it.fullName} (${it.campusName})" }
                                    ?: "Toque para ver todos os prédios oficiais da UFOP",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Escolher prédio UFOP",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                campusOptions.forEach { campusLabel ->
                    val isCampusSelected = selectedCampusFilter == campusLabel
                    FilterChip(
                        selected = isCampusSelected,
                        onClick = { selectedCampusFilter = campusLabel },
                        modifier = Modifier.heightIn(min = chipMinHeight),
                        label = {
                            Text(
                                text = campusLabel,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        shape = RoundedCornerShape(22.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filteredBuildings.forEach { buildingOption ->
                    val isSelected = draft.building.trim().equals(buildingOption.shortName, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            applySelectedBuilding(buildingOption.shortName)
                        },
                        modifier = Modifier.heightIn(min = chipMinHeight),
                        label = { Text(buildingOption.shortName) },
                        shape = RoundedCornerShape(22.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor.copy(alpha = 0.18f),
                            selectedLabelColor = accentColor
                        )
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = draft.building,
                onValueChange = { onDraftChange(draft.copy(building = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("Prédio") },
                placeholder = { Text("ICEB (III)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                value = draft.room,
                onValueChange = { onDraftChange(draft.copy(room = it)) },
                modifier = Modifier.weight(1f),
                label = { Text(if (isEadBuildingLabel(draft.building)) "Ambiente" else "Sala") },
                placeholder = {
                    Text(
                        text = if (isEadBuildingLabel(draft.building)) {
                            "AVA / Moodle (opcional)"
                        } else {
                            "Sala (opcional, ex.: Sala 104)"
                        }
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        }

        Text(
            text = "Exibição na grade: ${draft.shortLocationPreview}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DetailUfopBuildingPickerDialog(
    buildings: List<UfopBuildingOption>,
    selectedBuildingShortName: String,
    accentColor: Color,
    onSelectBuilding: (UfopBuildingOption) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(buildings, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            buildings
        } else {
            buildings.filter {
                it.shortName.contains(q, ignoreCase = true) ||
                    it.fullName.contains(q, ignoreCase = true) ||
                    it.campusName.contains(q, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Prédios de Ensino UFOP",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ouro Preto · Mariana · João Monlevade",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar prédio (ex.: ICEB, Escola de Minas, ICEA)...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Buscar prédio",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = filtered,
                        key = { it.id }
                    ) { b ->
                        val isSelected = b.shortName.equals(selectedBuildingShortName.trim(), ignoreCase = true)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectBuilding(b) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                accentColor.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) {
                                    accentColor.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${b.shortName} · ${b.campusName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = b.fullName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selecionado",
                                        tint = accentColor,
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
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Fechar",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
