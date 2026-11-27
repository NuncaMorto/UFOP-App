package br.ufop.campus.ui.disciplines

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ufop.campus.core.ffi.AbsenceHistoryEntry
import br.ufop.campus.core.ffi.AssessmentItem
import br.ufop.campus.core.ffi.AssessmentWithCourse
import br.ufop.campus.core.ffi.CourseScheduleSlotItem
import br.ufop.campus.core.ffi.DisciplineDetail
import br.ufop.campus.core.ffi.SyllabusImportReport
import br.ufop.campus.core.map.MapMeetingFocusRequest
import br.ufop.campus.core.map.extractCourseSectionFromNotes
import br.ufop.campus.core.map.isEadMapLocation
import br.ufop.campus.core.security.SecurePdfProcessor
import br.ufop.campus.ui.activities.AssessmentDetailBottomSheet
import br.ufop.campus.ui.components.CampusDatePickerField
import br.ufop.campus.ui.components.CampusTimePickerField
import br.ufop.campus.ui.components.defaultTodayIsoDate
import br.ufop.campus.ui.theme.CampusCardRadius
import br.ufop.campus.ui.theme.CampusPillRadius
import br.ufop.campus.ui.theme.CampusSheetRadius
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.StatusCancelledAmber
import br.ufop.campus.ui.theme.accessibleContentColorOn
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.campusCardBorder
import br.ufop.campus.ui.theme.campusCardElevation
import br.ufop.campus.ui.theme.parseHexColor
import br.ufop.campus.ui.theme.semanticAbsenceRiskColor
import br.ufop.campus.ui.theme.semanticAbsenceRiskLabel
import br.ufop.campus.ui.theme.semanticAbsenceRiskLevel
import br.ufop.campus.ui.theme.tabularNums
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DisciplineDetailPaneContent(
    detail: DisciplineDetail,
    onDismiss: () -> Unit,
    onSaveNotes: (String) -> Unit,
    modifier: Modifier = Modifier,
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
    onUpdateAssessment: ((assessmentId: String, courseId: String, title: String, kind: String, dueDate: String, dueTime: String, completed: Boolean) -> Unit)? = null,
    onOpenLocationInMap: ((MapMeetingFocusRequest) -> Unit)? = null
) {
    DisciplineDetailBottomSheet(
        detail = detail,
        onDismiss = onDismiss,
        onSaveNotes = onSaveNotes,
        onToggleAssessmentCompleted = onToggleAssessmentCompleted,
        onCreateAssessment = onCreateAssessment,
        onDeleteAssessment = onDeleteAssessment,
        scheduleSlots = scheduleSlots,
        absenceHistory = absenceHistory,
        onAddScheduleSlot = onAddScheduleSlot,
        onUpdateScheduleSlot = onUpdateScheduleSlot,
        onDeleteScheduleSlot = onDeleteScheduleSlot,
        onDeleteCourse = onDeleteCourse,
        onImportSyllabusPdf = onImportSyllabusPdf,
        onUpdateAbsences = onUpdateAbsences,
        onUpdateCourseDetails = onUpdateCourseDetails,
        onUpdateAssessment = onUpdateAssessment,
        onOpenLocationInMap = onOpenLocationInMap,
        asSidePane = true,
        modifier = modifier
    )
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
    onUpdateAssessment: ((assessmentId: String, courseId: String, title: String, kind: String, dueDate: String, dueTime: String, completed: Boolean) -> Unit)? = null,
    onOpenLocationInMap: ((MapMeetingFocusRequest) -> Unit)? = null,
    asSidePane: Boolean = false,
    modifier: Modifier = Modifier
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

    val detailBody: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = if (asSidePane) 18.dp else 6.dp)
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
                shape = RoundedCornerShape(CampusCardRadius),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = campusCardBorder(),
                shadowElevation = campusCardElevation(1.dp)
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

                    val locationDisplay = effectiveDetail.buildingAndRoom.ifBlank {
                        effectiveDetail.shortLocation.ifBlank { "Campus" }
                    }
                    val nextMapSlot = remember(resolvedScheduleSlots) {
                        selectNextWeeklySlotForMap(resolvedScheduleSlots)
                    }
                    val isEadCourse = isEadMapLocation(locationDisplay) ||
                        (resolvedScheduleSlots.isNotEmpty() && nextMapSlot == null)
                    val canOpenLocationInMap = onOpenLocationInMap != null &&
                        !isEadCourse &&
                        locationDisplay.isNotBlank()

                    DisciplineInfoDetailRow(
                        icon = Icons.Outlined.LocationOn,
                        label = "Prédio / Sala",
                        value = locationDisplay,
                        accentColor = accessibleAccent,
                        trailingHint = if (canOpenLocationInMap) "Ver no mapa" else null,
                        minTouchTarget = minTouchTarget,
                        onClick = if (canOpenLocationInMap) {
                            {
                                val fallbackRaw = nextMapSlot?.let {
                                    listOf(it.building.trim(), it.room.trim())
                                        .filter { s -> s.isNotEmpty() }
                                        .joinToString(" · ")
                                }?.ifBlank { locationDisplay } ?: locationDisplay
                                val weekday = nextMapSlot?.dayOfWeek
                                    ?: ZonedDateTime.now(ZoneId.of("America/Sao_Paulo")).dayOfWeek.value
                                val startTime = nextMapSlot?.startTime?.ifBlank { "08:00" } ?: "08:00"
                                val section = extractCourseSectionFromNotes(effectiveDetail.notes)
                                onDismiss()
                                onOpenLocationInMap?.invoke(
                                    MapMeetingFocusRequest(
                                        courseCode = courseCode,
                                        section = section,
                                        weekday = weekday,
                                        startTime = startTime,
                                        fallbackBuildingRaw = fallbackRaw
                                    )
                                )
                            }
                        } else {
                            null
                        }
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
                DisciplineEditCourseDialog(
                    name = editCourseName,
                    professor = editCourseProfessor,
                    workloadText = editCourseWorkloadText,
                    parsedWorkload = parsedWorkload,
                    accentColor = accentColor,
                    accessibleAccent = accessibleAccent,
                    chipMinHeight = secondaryChipHeight,
                    minTouchTarget = minTouchTarget,
                    onNameChange = { editCourseName = it },
                    onProfessorChange = { editCourseProfessor = it },
                    onWorkloadTextChange = { editCourseWorkloadText = it },
                    onDismiss = { showEditCourseDialog = false },
                    onSave = {
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
                shape = RoundedCornerShape(CampusCardRadius),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = campusCardBorder(),
                shadowElevation = campusCardElevation()
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
                            Row(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = minTouchTarget)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = openEditAbsencesDialog)
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(
                                            SpanStyle(
                                                fontSize = 30.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (riskLevel > 0) indicatorColor else MaterialTheme.colorScheme.onSurface
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
                                    },
                                    style = MaterialTheme.typography.headlineMedium.tabularNums(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Editar faltas manualmente",
                                    tint = accessibleAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(CampusPillRadius),
                                color = indicatorColor.copy(alpha = 0.14f),
                                border = campusCardBorder(1.dp)
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
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = { handleUpdateAbsences((currentAbsences - 1).coerceAtLeast(0)) },
                                enabled = currentAbsences > 0,
                                modifier = Modifier.size(minTouchTarget)
                            ) {
                                Surface(
                                    modifier = Modifier.size(if (a11y.largeTouchTargets) 46.dp else 40.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(
                                        alpha = if (currentAbsences > 0) 1f else 0.5f
                                    ),
                                    border = campusCardBorder(1.dp)
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

                            IconButton(
                                onClick = { handleUpdateAbsences(currentAbsences + 1) },
                                modifier = Modifier.size(minTouchTarget)
                            ) {
                                Surface(
                                    modifier = Modifier.size(if (a11y.largeTouchTargets) 46.dp else 40.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    border = campusCardBorder(1.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "Adicionar falta",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
                DisciplineEditAbsencesDialog(
                    courseName = effectiveSummary.name,
                    currentAbsences = currentAbsences,
                    maxAbsences = maxAbsences,
                    absencesInputDraft = absencesInputDraft,
                    accentColor = accentColor,
                    accessibleAccent = accessibleAccent,
                    minTouchTarget = minTouchTarget,
                    onDraftChange = { absencesInputDraft = it },
                    onDismiss = { showEditAbsencesDialog = false },
                    onApply = { nextTotal ->
                        handleUpdateAbsences(nextTotal)
                        showEditAbsencesDialog = false
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            val totalAbsenceWeight = resolvedAbsenceHistory.sumOf { it.classWeight }
            val historySummaryText = if (resolvedAbsenceHistory.isEmpty()) {
                "0 reg."
            } else {
                "${resolvedAbsenceHistory.size} (${totalAbsenceWeight}f)"
            }
            val slotsSummaryPill = "${resolvedScheduleSlots.size}"
            val completedAssessmentsCount = resolvedAssessments.count { it.completed }
            val assessmentsSummaryPill = "$completedAssessmentsCount/${resolvedAssessments.size}"
            val hasUnsavedNotesChanges = notesDraft != effectiveDetail.notes
            val notesSummaryPill = when {
                hasUnsavedNotesChanges -> "Editando"
                notesDraft.isBlank() -> "Sem notas"
                else -> "Salva"
            }

            // Container agrupado 26.dp reunindo as 4 seções com divisórias finas e títulos sem truncamento
            DisciplineGroupedSectionContainer {
                DisciplineAccordionCard(
                    title = "Histórico de faltas",
                    summaryPillText = historySummaryText,
                    accentColor = accentColor,
                    accessibleAccent = accessibleAccent,
                    expanded = historyExpanded,
                    reduceMotion = a11y.reduceMotion,
                    minTouchTarget = minTouchTarget,
                    grouped = true,
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
                        shape = RoundedCornerShape(CampusCardRadius),
                        color = MaterialTheme.colorScheme.surface,
                        border = campusCardBorder()
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

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                thickness = 1.dp
            )

            // 2. Acordeão: Horários semanais
            DisciplineAccordionCard(
                title = "Horários semanais",
                summaryPillText = slotsSummaryPill,
                accentColor = accentColor,
                accessibleAccent = accessibleAccent,
                expanded = slotsExpanded,
                reduceMotion = a11y.reduceMotion,
                minTouchTarget = minTouchTarget,
                grouped = true,
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
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentColor,
                                        contentColor = accessibleContentColorOn(accentColor)
                                    )
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
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = accentColor,
                                                    contentColor = accessibleContentColorOn(accentColor)
                                                )
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

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                thickness = 1.dp
            )

            // 3. Acordeão: Prazos e avaliações
            DisciplineAccordionCard(
                title = "Prazos e avaliações",
                summaryPillText = assessmentsSummaryPill,
                accentColor = accentColor,
                accessibleAccent = accessibleAccent,
                expanded = assessmentsExpanded,
                reduceMotion = a11y.reduceMotion,
                minTouchTarget = minTouchTarget,
                grouped = true,
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
                    DisciplineAddAssessmentFormCard(
                        courseName = effectiveSummary.name,
                        newTitle = newTitle,
                        newKind = newKind,
                        newDateText = newDateText,
                        newTimeText = newTimeText,
                        accentColor = accentColor,
                        accessibleAccent = accessibleAccent,
                        secondaryControlHeight = secondaryControlHeight,
                        secondaryChipHeight = secondaryChipHeight,
                        minTouchTarget = minTouchTarget,
                        onTitleChange = { newTitle = it },
                        onKindChange = { newKind = it },
                        onDateChange = { newDateText = it },
                        onTimeChange = { newTimeText = it },
                        onCancel = { showAddForm = false },
                        onSave = handleCreateAssessment
                    )
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

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                thickness = 1.dp
            )

            // 4. Acordeão: Anotações
            DisciplineAccordionCard(
                title = "Anotações",
                summaryPillText = notesSummaryPill,
                accentColor = accentColor,
                accessibleAccent = accessibleAccent,
                expanded = notesExpanded,
                reduceMotion = a11y.reduceMotion,
                minTouchTarget = minTouchTarget,
                grouped = true,
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
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Ação destrutiva: Remover disciplina (com confirmação)
            val accessibleDeleteRed = accessibleTextColor(StatusAbsentRed, isDark)
            DisciplineDeleteCourseSection(
                courseName = effectiveSummary.name,
                confirmDeleteCourse = confirmDeleteCourse,
                accessibleDeleteRed = accessibleDeleteRed,
                secondaryControlHeight = secondaryControlHeight,
                minTouchTarget = minTouchTarget,
                onRequestConfirm = { confirmDeleteCourse = true },
                onCancelConfirm = { confirmDeleteCourse = false },
                onConfirmDelete = handleDeleteCourse
            )

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

    if (asSidePane) {
        Surface(
            modifier = modifier.fillMaxHeight(),
            shape = RoundedCornerShape(topStart = CampusSheetRadius, bottomStart = CampusSheetRadius),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            border = campusCardBorder()
        ) {
            detailBody()
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = CampusSheetRadius, topEnd = CampusSheetRadius)
        ) {
            detailBody()
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
