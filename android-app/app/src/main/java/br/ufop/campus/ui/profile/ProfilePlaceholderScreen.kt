package br.ufop.campus.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsAccessibility
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.ufop.campus.BuildConfig
import br.ufop.campus.core.curriculum.DynamicCurriculumCatalog
import br.ufop.campus.core.curriculum.DynamicGridEngine
import br.ufop.campus.core.curriculum.UfopCourseCurriculum
import br.ufop.campus.core.curriculum.UfopCurriculumCatalog
import br.ufop.campus.core.ffi.AcademicCalendarEvent
import br.ufop.campus.core.ffi.AcademicCalendarUpdatePackage
import br.ufop.campus.core.ffi.AcademicCreReport
import br.ufop.campus.core.ffi.AcademicProfileSummary
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.CampusEngineBridgeRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.core.ffi.DisciplineSummaryCard
import br.ufop.campus.core.ffi.SemesterCreSummary
import br.ufop.campus.core.ffi.StudentProfile
import br.ufop.campus.core.ffi.TranscriptCourseEntry
import br.ufop.campus.core.ffi.UfopCampusInfo
import br.ufop.campus.core.ffi.normalizeUfopPortugueseText
import br.ufop.campus.core.security.AppLockManager
import br.ufop.campus.core.security.CampusCryptoEngine
import br.ufop.campus.core.security.SecurePdfProcessor
import br.ufop.campus.core.storage.GradeExportStorage
import br.ufop.campus.ui.curriculum.CurriculumMatrixModal
import br.ufop.campus.ui.disciplines.DisciplinesViewModel
import br.ufop.campus.ui.theme.CampusAccentColor
import br.ufop.campus.ui.theme.DepartmentMathBlue
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class ProfileDestination(val title: String) {
    METRICS_CRE("Métricas & CRE"),
    SETTINGS("Configurações"),
    UPDATES("Atualizações"),
    FAQ("FAQ & Ajuda")
}

private enum class LogoutStep {
    NONE,
    BACKUP_PROMPT,
    FINAL_CONFIRM
}

@Composable
private fun campusSwitchColors(): SwitchColors {
    val isDark = LocalCampusThemeController.current.isDark
    return SwitchDefaults.colors(
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
}

@Composable
private fun campusMinTouchTarget() =
    if (LocalCampusAccessibility.current.settings.largeTouchTargets) 56.dp else 48.dp

@Composable
private fun CampusAnimatedVisibility(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    if (LocalCampusAccessibility.current.settings.reduceMotion) {
        if (visible) content()
    } else {
        AnimatedVisibility(visible = visible) {
            content()
        }
    }
}

private enum class SettingsSection(val title: String) {
    PERSONALIZATION("Personalização"),
    SECURITY("Segurança & Privacidade"),
    ACCESSIBILITY("Acessibilidade"),
    CAMPUS_WEATHER("Campus & Clima Online"),
    DOCUMENTS_PDF("Documentos Acadêmicos (PDF)"),
    ACADEMIC_CALENDAR("Calendário Acadêmico"),
    STORAGE_SHARE("Armazenamento & Compartilhamento"),
    FEEDBACK("Feedback & Suporte")
}

private fun shareBackupUri(context: Context, uri: Uri, filename: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Backup Criptografado Vida Acadêmica ($filename)")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(sendIntent, "Compartilhar backup criptografado")
        )
    }
}

private fun syncThemeAndA11yAfterBackupRestore(
    context: Context,
    repository: CampusCoreRepository,
    themeController: br.ufop.campus.ui.theme.CampusThemeController,
    accessibilityController: br.ufop.campus.ui.theme.CampusAccessibilityController
) {
    val restoredTheme = repository.getDarkThemePreference().trim().uppercase()
    if (restoredTheme == "DARK" || restoredTheme == "LIGHT") {
        val targetDark = restoredTheme == "DARK"
        CampusRepositoryHolder.saveDarkTheme(context, repository, targetDark)
        if (themeController.isDark != targetDark) {
            themeController.toggleTheme()
        }
    }
    val restoredAccent = CampusAccentColor.fromKey(repository.getAccentColorPreference())
    CampusRepositoryHolder.saveAccentColor(context, repository, restoredAccent.storageKey)
    themeController.selectAccentColor(restoredAccent)

    val restoredA11y = repository.getAccessibilitySettings()
    CampusRepositoryHolder.saveAccessibilitySettings(context, repository, restoredA11y)
    accessibilityController.updateSettings(restoredA11y)

    CampusRepositoryHolder.setGuidedTourCompleted(context, repository, repository.isGuidedTourCompleted())
    val seenRelease = repository.getLastSeenReleaseVersion()
    if (seenRelease.isNotBlank()) {
        CampusRepositoryHolder.setLastSeenReleaseVersion(context, repository, seenRelease)
    }
}

private fun readUriBytes(context: Context, uri: Uri): ByteArray {
    return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("Não foi possível ler o arquivo selecionado.")
}

private fun readUriText(context: Context, uri: Uri): String {
    return context.contentResolver.openInputStream(uri)
        ?.bufferedReader(Charsets.UTF_8)
        ?.use { it.readText() }
        ?: throw IllegalStateException("Não foi possível ler o arquivo selecionado.")
}

private fun formatScore(value: Double, decimals: Int): String {
    return String.format(Locale.US, "%.${decimals}f", value)
}

@Composable
fun ProfilePlaceholderScreen(
    modifier: Modifier
) {
    ProfilePlaceholderScreen(
        repository = remember {
            DisciplinesViewModel.sharedRepository ?: CampusEngineBridgeRepository()
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePlaceholderScreen(
    repository: CampusCoreRepository,
    modifier: Modifier = Modifier,
    onOpenDynamicGrid: () -> Unit = {},
    onOpenReleaseNotes: () -> Unit = {},
    onReplayGuidedTour: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onAppSecurityChanged: () -> Unit = {},
    onExitApp: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeController = LocalCampusThemeController.current
    val accessibilityController = LocalCampusAccessibility.current
    val mutationVersion by repository.mutationVersion.collectAsState()

    val studentProfile = remember(mutationVersion) {
        repository.getStudentProfile()
    }
    val activeSemester = studentProfile.activeSemester.ifBlank { "2026.2" }
    val profileSummary = remember(mutationVersion, activeSemester) {
        repository.getAcademicProfileSummary(activeSemester)
    }
    val currentDisciplines = remember(mutationVersion, activeSemester) {
        repository.listDisciplines(activeSemester, null)
    }
    val creReport = remember(mutationVersion) {
        repository.getAcademicCreReport()
    }
    val exportFolderConfig = remember(mutationVersion) {
        repository.getExportFolderConfig()
    }
    val dynamicGridResolution = remember(
        mutationVersion,
        studentProfile.courseName,
        studentProfile.registrationNumber,
        creReport.semesters
    ) {
        val earliestSemester = creReport.semesters
            .map { it.semester.trim() }
            .filter { it.isNotEmpty() }
            .minByOrNull { raw ->
                DynamicCurriculumCatalog.parseSemesterTerm(raw)
                    ?.let { (year, sem) -> year * 10 + sem }
                    ?: Int.MAX_VALUE
            }
        DynamicCurriculumCatalog.resolveMatrixForStudent(
            courseName = studentProfile.courseName,
            registrationNumber = studentProfile.registrationNumber,
            earliestTranscriptSemester = earliestSemester
        )
    }
    val activeDynamicMatrix = dynamicGridResolution.matrix
    val dynamicGridSummary = remember(mutationVersion, activeDynamicMatrix.id) {
        val completed = DynamicGridEngine.sanitizeCompleted(
            matrix = activeDynamicMatrix,
            completedCodes = DynamicCurriculumCatalog.loadCompletedCodesWithLegacyFallback(
                matrix = activeDynamicMatrix,
                reader = repository::getDynamicGridCompletedCodes
            )
        )
        DynamicGridEngine.summarize(activeDynamicMatrix, completed)
    }
    val dynamicGridSubtitle =
        "${activeDynamicMatrix.curriculumLabel} · ${dynamicGridSummary.completedHours}/${dynamicGridSummary.totalGridHours}h (${dynamicGridSummary.completionPercent}%)"

    var avatarRevision by remember { mutableIntStateOf(0) }
    val avatarBitmap = rememberAvatarBitmap(studentProfile.avatarPath, avatarRevision)

    var activeDestination by rememberSaveable { mutableStateOf<ProfileDestination?>(null) }
    var activeSettingsSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    var showCurriculumMatrixModal by rememberSaveable { mutableStateOf(false) }
    var showEditProfileSheet by remember { mutableStateOf(false) }
    var showShareScheduleSheet by remember { mutableStateOf(false) }
    var editingTranscriptEntry by remember { mutableStateOf<TranscriptCourseEntry?>(null) }

    LaunchedEffect(activeDestination) {
        if (activeDestination != ProfileDestination.SETTINGS) {
            activeSettingsSection = null
        }
    }

    BackHandler(enabled = activeDestination != null) {
        if (activeDestination == ProfileDestination.SETTINGS && activeSettingsSection != null) {
            activeSettingsSection = null
        } else {
            activeDestination = null
        }
    }

    var isImportingPdf by remember { mutableStateOf(false) }
    var pdfFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var pdfFeedbackIsError by remember { mutableStateOf(false) }

    var isImportingTranscript by remember { mutableStateOf(false) }
    var transcriptFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var transcriptFeedbackIsError by remember { mutableStateOf(false) }

    var isRefreshingWeather by remember { mutableStateOf(false) }
    var weatherFeedback by remember { mutableStateOf<String?>(null) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }

    val pendingCalendarUpdate = remember(mutationVersion) {
        repository.getPendingCalendarUpdate()
    }
    val isAdminUnlocked = remember(mutationVersion) {
        repository.isAdminUnlocked()
    }
    var calendarUpdateAppliedMessage by remember { mutableStateOf<String?>(null) }
    var adminTargetSemester by rememberSaveable(activeSemester) { mutableStateOf(activeSemester) }
    var adminPinInput by remember { mutableStateOf("") }
    var isPublishingCalendarPdf by remember { mutableStateOf(false) }
    var isImportingCalendarJson by remember { mutableStateOf(false) }
    var isExportingCalendarJson by remember { mutableStateOf(false) }
    var adminFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var adminFeedbackIsError by remember { mutableStateOf(false) }
    var lastPublishedPackage by remember { mutableStateOf<AcademicCalendarUpdatePackage?>(null) }

    var logoutStep by rememberSaveable { mutableStateOf(LogoutStep.NONE) }
    var logoutBackupPassword by remember { mutableStateOf("") }
    var logoutBackupError by remember { mutableStateOf<String?>(null) }
    var isExportingLogoutBackup by remember { mutableStateOf(false) }
    var logoutBackupSavedPath by remember { mutableStateOf<String?>(null) }
    var logoutBackupShareUri by remember { mutableStateOf<Uri?>(null) }
    var logoutBackupShareFilename by remember { mutableStateOf("campus_ufop_backup.ufopbackup") }
    var isClearingForLogout by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.refreshWeatherIfNeeded(force = false)
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val savedPath = copyUriToProfileAvatarFile(context, uri)
            if (savedPath != null) {
                val latestProfile = repository.getStudentProfile()
                repository.saveStudentProfile(latestProfile.copy(avatarPath = savedPath))
                avatarRevision += 1
            }
        }
    }

    val adminCalendarPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isPublishingCalendarPdf = true
        adminFeedbackMessage = null
        adminFeedbackIsError = false
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    runCatching {
                        val bytes = readUriBytes(context, uri)
                        repository.adminPublishCalendarPdf(bytes, adminTargetSemester)
                    }
                } finally {
                    runCatching { SecurePdfProcessor.purgeResidualPdfFiles(context) }
                }
            }
            isPublishingCalendarPdf = false
            result.onSuccess { pkg ->
                lastPublishedPackage = pkg
                adminFeedbackIsError = false
                adminFeedbackMessage =
                    "Calendário ${pkg.semester} importado e verificado (${pkg.events.size} eventos)."
            }.onFailure { error ->
                adminFeedbackIsError = true
                adminFeedbackMessage =
                    error.message ?: "Falha ao processar o PDF do Calendário Acadêmico."
            }
        }
    }

    val exportCalendarJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isExportingCalendarJson = true
        adminFeedbackMessage = null
        adminFeedbackIsError = false
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = repository.exportCalendarUpdateJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Não foi possível gravar o arquivo selecionado.")
                }
            }
            isExportingCalendarJson = false
            result.onSuccess {
                adminFeedbackIsError = false
                adminFeedbackMessage =
                    "Arquivo do calendário exportado! Compartilhe com outros estudantes."
            }.onFailure { error ->
                adminFeedbackIsError = true
                adminFeedbackMessage =
                    error.message ?: "Falha ao exportar o arquivo de atualização do calendário."
            }
        }
    }

    val importCalendarJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isImportingCalendarJson = true
        adminFeedbackMessage = null
        adminFeedbackIsError = false
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = readUriText(context, uri)
                    repository.importCalendarUpdateJson(json)
                }
            }
            isImportingCalendarJson = false
            result.onSuccess { pkg ->
                lastPublishedPackage = pkg
                adminFeedbackIsError = false
                adminFeedbackMessage =
                    "Atualização ${pkg.version} validada (${pkg.events.size} eventos). Toque em \"Atualizar meu calendário\" no topo do Perfil."
            }.onFailure { error ->
                adminFeedbackIsError = true
                adminFeedbackMessage =
                    error.message ?: "Arquivo de calendário inválido ou corrompido."
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isImportingPdf = true
        pdfFeedbackMessage = null
        pdfFeedbackIsError = false

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    runCatching {
                        val bytes = readUriBytes(context, uri)
                        val importedCourses = repository.importEnrollmentCertificatePdf(bytes)
                        if (importedCourses <= 0) {
                            throw IllegalStateException(
                                "Nenhuma disciplina identificada. Verifique se é um Atestado de Matrícula da UFOP."
                            )
                        }
                        val updatedProfile = repository.getStudentProfile()
                        val matchedCurriculum = UfopCurriculumCatalog.findCourse(
                            context = context,
                            courseNameOrCode = updatedProfile.courseName,
                            campusHint = updatedProfile.campusName
                        )
                        if (matchedCurriculum != null && shouldApplyCatalogRequiredHours(
                                repository = repository,
                                registrationNumber = updatedProfile.registrationNumber,
                                course = matchedCurriculum
                            )
                        ) {
                            repository.setTotalRequiredHours(matchedCurriculum.chTotal)
                        }
                        val semester = updatedProfile.activeSemester.ifBlank { "2026.2" }
                        val importedSlots = repository.listDisciplines(semester, null)
                            .sumOf { repository.listCourseSlots(it.courseId).size }
                        Triple(importedCourses, importedSlots, semester)
                    }
                } finally {
                    runCatching { SecurePdfProcessor.purgeResidualPdfFiles(context) }
                }
            }
            isImportingPdf = false
            result.onSuccess { (coursesCount, slotsCount, semester) ->
                pdfFeedbackIsError = false
                pdfFeedbackMessage = if (slotsCount > 0) {
                    "$coursesCount disciplinas e $slotsCount horários importados do atestado $semester!"
                } else {
                    "$coursesCount disciplinas importadas do atestado $semester!"
                }
            }.onFailure { error ->
                pdfFeedbackIsError = true
                pdfFeedbackMessage = error.message ?: "Falha ao processar o Atestado de Matrícula (PDF)."
            }
        }
    }

    val transcriptPdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isImportingTranscript = true
        transcriptFeedbackMessage = null
        transcriptFeedbackIsError = false

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    runCatching {
                        val bytes = readUriBytes(context, uri)
                        repository.importAcademicTranscriptPdf(bytes)
                    }
                } finally {
                    runCatching { SecurePdfProcessor.purgeResidualPdfFiles(context) }
                }
            }
            isImportingTranscript = false
            result.onSuccess { report ->
                val totalCourses = report.semesters.sumOf { it.courses.size }
                transcriptFeedbackIsError = false
                transcriptFeedbackMessage =
                    "Histórico importado: ${report.semesters.size} semestres · $totalCourses registros · CRE ${formatScore(report.overallCre, 2)}."
            }.onFailure { error ->
                transcriptFeedbackIsError = true
                transcriptFeedbackMessage =
                    error.message ?: "Falha ao processar o Histórico Escolar (PDF)."
            }
        }
    }

    val contentScrollState = rememberScrollState()
    LaunchedEffect(activeDestination, activeSettingsSection) {
        contentScrollState.scrollTo(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            activeDestination == ProfileDestination.METRICS_CRE -> {
                SettingsSubViewHeader(
                    title = ProfileDestination.METRICS_CRE.title,
                    backLabel = "Voltar para Perfil",
                    onBack = { activeDestination = null }
                )
            }
            activeDestination == ProfileDestination.SETTINGS && activeSettingsSection == null -> {
                SettingsSubViewHeader(
                    title = ProfileDestination.SETTINGS.title,
                    backLabel = "Voltar para Perfil",
                    onBack = { activeDestination = null }
                )
            }
            activeDestination == ProfileDestination.SETTINGS && activeSettingsSection != null -> {
                SettingsSubViewHeader(
                    title = activeSettingsSection!!.title,
                    backLabel = "Voltar para Configurações",
                    onBack = { activeSettingsSection = null }
                )
            }
            activeDestination == ProfileDestination.UPDATES -> {
                SettingsSubViewHeader(
                    title = ProfileDestination.UPDATES.title,
                    backLabel = "Voltar para Perfil",
                    onBack = { activeDestination = null }
                )
            }
            activeDestination == ProfileDestination.FAQ -> {
                SettingsSubViewHeader(
                    title = ProfileDestination.FAQ.title,
                    backLabel = "Voltar para Perfil",
                    onBack = { activeDestination = null }
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(contentScrollState)
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = if (activeDestination == null) 20.dp else 14.dp
                )
                .navigationBarsPadding()
                .padding(bottom = 136.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (activeDestination) {
                null -> {
                    // Header principal do Perfil
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "APP INDEPENDENTE PARA ESTUDANTES",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Perfil",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Identidade Acadêmica · Semestre $activeSemester",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Banner de Atualização Pendente do Calendário Acadêmico UFOP
                    if (pendingCalendarUpdate != null) {
                        PendingCalendarUpdateBanner(
                            pkg = pendingCalendarUpdate,
                            onApply = {
                                val appliedCount = repository.applyPendingCalendarUpdate()
                                calendarUpdateAppliedMessage =
                                    "Calendário ${pendingCalendarUpdate.semester} atualizado com sucesso ($appliedCount eventos aplicados)."
                            }
                        )
                    } else if (calendarUpdateAppliedMessage != null) {
                        StatusFeedbackBanner(
                            message = calendarUpdateAppliedMessage!!,
                            isError = false,
                            onDismiss = { calendarUpdateAppliedMessage = null }
                        )
                    }

                    // Card Hero Centralizado: Identidade Acadêmica
                    AcademicIdentityCard(
                        studentProfile = studentProfile,
                        activeSemester = activeSemester,
                        activeCampus = profileSummary.activeCampus,
                        avatarBitmap = avatarBitmap,
                        onEditProfile = { showEditProfileSheet = true }
                    )

                    // Menu agrupado da Central do Perfil (ordem exata: Métricas & CRE, Grade Dinâmica, Configurações, Atualizações, FAQ & Ajuda, Sair da conta)
                    SettingsCategoryGroup(title = "Central do Perfil") {
                        SettingsMenuRow(
                            icon = Icons.Outlined.Analytics,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Métricas & CRE",
                            subtitle = "CRE ${formatScore(creReport.overallCre, 2)} · ${profileSummary.totalAbsences}/${profileSummary.totalMaxAbsences} faltas · ${creReport.completedHours}/${creReport.totalRequiredHours}h",
                            onClick = { activeDestination = ProfileDestination.METRICS_CRE }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                        )
                        SettingsMenuRow(
                            icon = Icons.Outlined.AccountTree,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Grade Dinâmica",
                            subtitle = dynamicGridSubtitle,
                            onClick = onOpenDynamicGrid
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                        )
                        SettingsMenuRow(
                            icon = Icons.Outlined.Settings,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Configurações",
                            subtitle = "Personalização, segurança, acessibilidade, campus, PDFs, tour guiado e suporte",
                            onClick = { activeDestination = ProfileDestination.SETTINGS }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                        )
                        SettingsMenuRow(
                            icon = Icons.Outlined.SystemUpdate,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Atualizações",
                            subtitle = "Checar novas versões no GitHub e histórico de patches (v${CampusRepositoryHolder.CURRENT_APP_VERSION})",
                            onClick = { activeDestination = ProfileDestination.UPDATES }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                        )
                        SettingsMenuRow(
                            icon = Icons.AutoMirrored.Outlined.HelpOutline,
                            iconTint = DepartmentMathBlue,
                            title = "FAQ & Ajuda",
                            subtitle = "Dúvidas frequentes e informações sobre o funcionamento offline",
                            onClick = { activeDestination = ProfileDestination.FAQ }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                        )
                        SettingsMenuRow(
                            icon = Icons.AutoMirrored.Outlined.ExitToApp,
                            iconTint = StatusAbsentRed,
                            title = "Sair da conta",
                            subtitle = "Fazer backup protegido por senha e encerrar sessão neste aparelho",
                            onClick = {
                                logoutBackupPassword = ""
                                logoutBackupError = null
                                logoutBackupSavedPath = null
                                logoutBackupShareUri = null
                                logoutStep = LogoutStep.BACKUP_PROMPT
                            }
                        )
                    }
                }

                ProfileDestination.METRICS_CRE -> {
                    ProfileMetricsAndCreTab(
                        profileSummary = profileSummary,
                        currentDisciplines = currentDisciplines,
                        creReport = creReport,
                        isImportingTranscript = isImportingTranscript,
                        transcriptFeedbackMessage = transcriptFeedbackMessage,
                        transcriptFeedbackIsError = transcriptFeedbackIsError,
                        onImportTranscriptPdf = {
                            transcriptPdfPickerLauncher.launch(arrayOf("application/pdf"))
                        },
                        onOpenCurriculumMatrix = { showCurriculumMatrixModal = true },
                        onSelectTranscriptEntry = { entry ->
                            editingTranscriptEntry = entry
                        }
                    )
                }

                ProfileDestination.SETTINGS -> {
                    ProfileSettingsTab(
                        repository = repository,
                        activeSemester = activeSemester,
                        mutationVersion = mutationVersion,
                        studentProfile = studentProfile,
                        profileSummary = profileSummary,
                        exportFolderLabel = exportFolderConfig.displayLabel,
                        activeSection = activeSettingsSection,
                        onSelectSection = { activeSettingsSection = it },
                        themeController = themeController,
                        accessibilityController = accessibilityController,
                        onAppSecurityChanged = onAppSecurityChanged,
                        onBackupRestored = { avatarRevision += 1 },
                        onOpenCurriculumMatrix = { showCurriculumMatrixModal = true },
                        onReplayGuidedTour = onReplayGuidedTour,
                        onSelectCampus = { campusId ->
                            repository.setActiveCampus(campusId)
                            repository.refreshWeatherIfNeeded(force = false)
                        },
                        isRefreshingWeather = isRefreshingWeather,
                        weatherFeedback = weatherFeedback,
                        onRefreshWeather = {
                            isRefreshingWeather = true
                            weatherFeedback = null
                            scope.launch {
                                val updated = withContext(Dispatchers.IO) {
                                    repository.refreshWeatherIfNeeded(force = true)
                                }
                                isRefreshingWeather = false
                                weatherFeedback =
                                    "Clima atualizado: ${updated.city} · ${updated.weatherTempC} °C (${updated.weatherCondition})"
                            }
                        },
                        isImportingPdf = isImportingPdf,
                        pdfFeedbackMessage = pdfFeedbackMessage,
                        pdfFeedbackIsError = pdfFeedbackIsError,
                        onImportEnrollmentPdf = {
                            pdfPickerLauncher.launch(arrayOf("application/pdf"))
                        },
                        isImportingTranscript = isImportingTranscript,
                        transcriptFeedbackMessage = transcriptFeedbackMessage,
                        transcriptFeedbackIsError = transcriptFeedbackIsError,
                        onImportTranscriptPdf = {
                            transcriptPdfPickerLauncher.launch(arrayOf("application/pdf"))
                        },
                        onOpenShareSchedule = { showShareScheduleSheet = true },
                        backupStatusMessage = backupStatusMessage,
                        onExportLocalBackup = {
                            backupStatusMessage =
                                "Cópia local dos dados do semestre $activeSemester atualizada neste aparelho."
                        },
                        isAdminUnlocked = isAdminUnlocked,
                        adminPinInput = adminPinInput,
                        onAdminPinChange = { adminPinInput = it },
                        onUnlockAdmin = {
                            val unlocked = repository.unlockAdminMode(adminPinInput)
                            if (unlocked) {
                                adminPinInput = ""
                                adminFeedbackIsError = false
                                adminFeedbackMessage =
                                    "Modo administrador desbloqueado. Agora você pode importar o calendário em PDF."
                            } else {
                                adminFeedbackIsError = true
                                adminFeedbackMessage = "PIN inválido. Acesso administrador negado."
                            }
                        },
                        isPublishingCalendarPdf = isPublishingCalendarPdf,
                        onPublishCalendarPdf = { targetSemester ->
                            adminTargetSemester = targetSemester
                            adminCalendarPdfLauncher.launch(arrayOf("application/pdf"))
                        },
                        isExportingCalendarJson = isExportingCalendarJson,
                        onExportCalendarJson = { targetSemester ->
                            val defaultFileName =
                                "calendario_ufop_${targetSemester.replace(".", "_")}.ufopcal.json"
                            exportCalendarJsonLauncher.launch(defaultFileName)
                        },
                        isImportingCalendarJson = isImportingCalendarJson,
                        onImportCalendarJson = {
                            importCalendarJsonLauncher.launch(arrayOf("*/*"))
                        },
                        activePkg = pendingCalendarUpdate ?: lastPublishedPackage,
                        adminFeedbackMessage = adminFeedbackMessage,
                        adminFeedbackIsError = adminFeedbackIsError
                    )
                }

                ProfileDestination.UPDATES -> {
                    ProfileUpdatesSubView(
                        onOpenReleaseNotes = onOpenReleaseNotes,
                        onCheckForUpdates = onCheckForUpdates
                    )
                }

                ProfileDestination.FAQ -> {
                    ProfileFaqSubView()
                }
            }
        }
    }

    if (showCurriculumMatrixModal) {
        Dialog(
            onDismissRequest = { showCurriculumMatrixModal = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            CurriculumMatrixModal(
                repository = repository,
                initialCourseQuery = studentProfile.courseName,
                onDismiss = { showCurriculumMatrixModal = false }
            )
        }
    }

    if (showEditProfileSheet) {
        EditStudentProfileBottomSheet(
            initialProfile = studentProfile,
            avatarBitmap = avatarBitmap,
            onDismiss = { showEditProfileSheet = false },
            onPickPhoto = { avatarPickerLauncher.launch("image/*") },
            onRemovePhoto = {
                val latestProfile = repository.getStudentProfile()
                repository.saveStudentProfile(latestProfile.copy(avatarPath = null))
                avatarRevision += 1
            },
            onSave = { updated, matchedCourse ->
                repository.saveStudentProfile(updated)
                if (matchedCourse != null && shouldApplyCatalogRequiredHours(
                        repository = repository,
                        registrationNumber = updated.registrationNumber,
                        course = matchedCourse
                    )
                ) {
                    repository.setTotalRequiredHours(matchedCourse.chTotal)
                }
                showEditProfileSheet = false
            }
        )
    }

    if (showShareScheduleSheet) {
        ScheduleJsonShareBottomSheet(
            repository = repository,
            semester = activeSemester,
            onDismiss = { showShareScheduleSheet = false },
            onImported = { count ->
                backupStatusMessage = "Grade importada com sucesso ($count disciplinas sincronizadas)."
            }
        )
    }

    editingTranscriptEntry?.let { entry ->
        TranscriptGradeSimulatorDialog(
            entry = entry,
            onDismiss = { editingTranscriptEntry = null },
            onSave = { newGrade, newAbsences ->
                repository.updateTranscriptOrCourseGrade(
                    semester = entry.semester,
                    codeOrCourseId = entry.code.ifBlank { entry.id },
                    grade = newGrade,
                    absences = newAbsences
                )
                editingTranscriptEntry = null
            }
        )
    }

    LogoutAccountFlowDialogs(
        step = logoutStep,
        backupPassword = logoutBackupPassword,
        onBackupPasswordChange = {
            logoutBackupPassword = it
            logoutBackupError = null
        },
        backupError = logoutBackupError,
        isExportingBackup = isExportingLogoutBackup,
        backupSavedPath = logoutBackupSavedPath,
        backupShareUri = logoutBackupShareUri,
        backupShareFilename = logoutBackupShareFilename,
        isClearingAccount = isClearingForLogout,
        onDownloadBackupAndContinue = {
            val cleanPass = logoutBackupPassword.trim()
            if (cleanPass.length < 4) {
                logoutBackupError = "A senha do backup deve ter pelo menos 4 caracteres."
                return@LogoutAccountFlowDialogs
            }
            isExportingLogoutBackup = true
            logoutBackupError = null
            val filename = "campus_ufop_backup_${activeSemester.replace(".", "_")}.ufopbackup"
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val json = repository.exportFullAccountSnapshotJson()
                        val encrypted = CampusCryptoEngine.encryptBackupPayload(json, logoutBackupPassword.toCharArray())
                        GradeExportStorage.saveEncryptedBackup(
                            context = context,
                            repository = repository,
                            filename = filename,
                            encryptedPayload = encrypted
                        )
                    }
                }
                isExportingLogoutBackup = false
                result.onSuccess { saved ->
                    logoutBackupSavedPath = saved.displayPath
                    logoutBackupShareUri = saved.shareUri
                    logoutBackupShareFilename = filename
                    logoutStep = LogoutStep.FINAL_CONFIRM
                }.onFailure { err ->
                    logoutBackupError = err.message ?: "Não foi possível gerar o backup criptografado."
                }
            }
        },
        onContinueWithoutBackup = {
            logoutBackupSavedPath = null
            logoutBackupShareUri = null
            logoutBackupError = null
            logoutStep = LogoutStep.FINAL_CONFIRM
        },
        onShareBackup = {
            logoutBackupShareUri?.let { uri ->
                shareBackupUri(context, uri, logoutBackupShareFilename)
            }
        },
        onConfirmFinalLogout = {
            if (isClearingForLogout) return@LogoutAccountFlowDialogs
            isClearingForLogout = true
            scope.launch {
                withContext(Dispatchers.IO) {
                    CampusRepositoryHolder.clearAllUserDataAndPrefs(context, repository)
                }
                withContext(Dispatchers.Main) {
                    isClearingForLogout = false
                    logoutStep = LogoutStep.NONE
                    onExitApp()
                }
            }
        },
        onDismiss = {
            if (!isExportingLogoutBackup && !isClearingForLogout) {
                logoutStep = LogoutStep.NONE
            }
        }
    )
}

@Composable
private fun PendingCalendarUpdateBanner(
    pkg: AcademicCalendarUpdatePackage,
    onApply: () -> Unit
) {
    val minTouchTarget = campusMinTouchTarget()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Atualização do Calendário Acadêmico disponível — Semestre ${pkg.semester} · ${pkg.events.size} eventos",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Text(
                text = "${pkg.publishedBy} · Versão ${pkg.version}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )

            Button(
                onClick = onApply,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minTouchTarget),
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Atualizar meu calendário",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AcademicIdentityCard(
    studentProfile: StudentProfile,
    activeSemester: String,
    activeCampus: UfopCampusInfo,
    avatarBitmap: ImageBitmap?,
    onEditProfile: () -> Unit
) {
    val minTouchTarget = campusMinTouchTarget()
    val displayCourseName = studentProfile.courseName.trim().let {
        if (it.isBlank()) "Curso não informado" else normalizeUfopPortugueseText(it)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            ) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap,
                        contentDescription = "Foto de perfil",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        val initials = extractInitials(studentProfile.fullName)
                        if (initials.isNotEmpty()) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = "Foto de perfil",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = studentProfile.fullName.ifBlank { "Estudante" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = displayCourseName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            val regLabel = studentProfile.registrationNumber.ifBlank { "Não informada" }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = "Matrícula $regLabel · $activeSemester",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Text(
                    text = "${activeCampus.name} · ${activeCampus.weatherTempC} °C (${activeCampus.weatherCondition})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            FilledTonalButton(
                onClick = onEditProfile,
                modifier = Modifier.heightIn(min = minTouchTarget),
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Editar perfil",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun ProfileMetricsAndCreTab(
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
            creReport.semesters.forEachIndexed { idx, semesterSummary ->
                val isExpanded = expandedSemesters[semesterSummary.semester]
                    ?: (idx < 2 || semesterSummary.semester == profileSummary.semester)
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
    val absenceRatio = (profileSummary.totalAbsences.toFloat() /
        profileSummary.totalMaxAbsences.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
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
                                        text = normalizeUfopPortugueseText(discipline.name),
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
    val requiredHours = creReport.totalRequiredHours.coerceAtLeast(1)
    val completionRatio = (creReport.completedHours.toFloat() / requiredHours.toFloat()).coerceIn(0f, 1f)
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
                            text = formatScore(creReport.overallCre, 2),
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
                        text = "${creReport.completedHours} / ${creReport.totalRequiredHours} h",
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
                        text = "${creReport.totalApproved} aprovadas · ${creReport.totalFailedByGrade} reprovadas",
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
                        text = "${summary.courses.size} disciplinas · ${summary.totalWorkloadHours}h · ${summary.approvedCount} AP · ${summary.failedCount} REP · ${summary.inProgressCount} CS",
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
                            text = formatScore(summary.cre, 2),
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
private fun TranscriptGradeSimulatorDialog(
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

@Composable
private fun ProfilePersonalizationSubView(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    selectedAccent: CampusAccentColor,
    onSelectAccent: (CampusAccentColor) -> Unit
) {
    val minTouchTarget = campusMinTouchTarget()
    val swatchMinHeight = if (LocalCampusAccessibility.current.settings.largeTouchTargets) 64.dp else 56.dp
    val accessibleBlue = accessibleTextColor(DepartmentMathBlue, isDarkTheme, MaterialTheme.colorScheme.surface)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsCategoryGroup(title = "Aparência") {
            SettingsMenuRow(
                icon = if (isDarkTheme) Icons.Outlined.DarkMode else Icons.Outlined.WbSunny,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Modo escuro",
                subtitle = if (isDarkTheme) {
                    "Ativado · Carbon Deep"
                } else {
                    "Desativado · Modo claro (Warm Cream)"
                },
                onClick = onToggleTheme,
                trailing = {
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { onToggleTheme() },
                        modifier = Modifier.heightIn(min = minTouchTarget),
                        colors = campusSwitchColors()
                    )
                }
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "COR DE DESTAQUE DO APP",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .semantics { heading() }
            )
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Personalize a cor principal dos botões, destaques da Agenda e do widget. Sua preferência fica salva automaticamente mesmo após fechar o app ou reiniciar o celular.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val accentRows = remember { CampusAccentColor.entries.chunked(2) }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        accentRows.forEach { rowAccents ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowAccents.forEach { accent ->
                                    val isSelected = selectedAccent == accent
                                    val swatchColor = parseHexColor(accent.previewHex)
                                    Surface(
                                        onClick = { onSelectAccent(accent) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = swatchMinHeight),
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        },
                                        border = BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            }
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(34.dp),
                                                shape = CircleShape,
                                                color = swatchColor,
                                                border = BorderStroke(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                                                    } else {
                                                        Color.Black.copy(alpha = 0.12f)
                                                    }
                                                )
                                            ) {
                                                if (isSelected) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Selecionada",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = accent.displayName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (isSelected) "Ativa" else "Toque para aplicar",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                                if (rowAccents.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = DepartmentMathBlue.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, DepartmentMathBlue.copy(alpha = 0.24f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = accessibleBlue,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "O azul é preservado exclusivamente para tags informativas, clima e departamentos para manter o contraste visual.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileSettingsTab(
    repository: CampusCoreRepository,
    activeSemester: String,
    mutationVersion: Long,
    studentProfile: StudentProfile,
    profileSummary: AcademicProfileSummary,
    exportFolderLabel: String,
    activeSection: SettingsSection?,
    onSelectSection: (SettingsSection?) -> Unit,
    themeController: br.ufop.campus.ui.theme.CampusThemeController,
    accessibilityController: br.ufop.campus.ui.theme.CampusAccessibilityController,
    onAppSecurityChanged: () -> Unit,
    onBackupRestored: () -> Unit,
    onOpenCurriculumMatrix: () -> Unit,
    onReplayGuidedTour: () -> Unit,
    onSelectCampus: (String) -> Unit,
    isRefreshingWeather: Boolean,
    weatherFeedback: String?,
    onRefreshWeather: () -> Unit,
    isImportingPdf: Boolean,
    pdfFeedbackMessage: String?,
    pdfFeedbackIsError: Boolean,
    onImportEnrollmentPdf: () -> Unit,
    isImportingTranscript: Boolean,
    transcriptFeedbackMessage: String?,
    transcriptFeedbackIsError: Boolean,
    onImportTranscriptPdf: () -> Unit,
    onOpenShareSchedule: () -> Unit,
    backupStatusMessage: String?,
    onExportLocalBackup: () -> Unit,
    isAdminUnlocked: Boolean,
    adminPinInput: String,
    onAdminPinChange: (String) -> Unit,
    onUnlockAdmin: () -> Unit,
    isPublishingCalendarPdf: Boolean,
    onPublishCalendarPdf: (String) -> Unit,
    isExportingCalendarJson: Boolean,
    onExportCalendarJson: (String) -> Unit,
    isImportingCalendarJson: Boolean,
    onImportCalendarJson: () -> Unit,
    activePkg: AcademicCalendarUpdatePackage?,
    adminFeedbackMessage: String?,
    adminFeedbackIsError: Boolean
) {
    val context = LocalContext.current
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    val safeGreenText = accessibleTextColor(StatusAttendingGreen, isDark, MaterialTheme.colorScheme.surface)
    val catalogCourses by produceState<List<UfopCourseCurriculum>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) { UfopCurriculumCatalog.getAllCourses(context) }
    }
    val matchedCourse = remember(catalogCourses, studentProfile.courseName, studentProfile.campusName) {
        if (studentProfile.courseName.isBlank()) {
            null
        } else {
            UfopCurriculumCatalog.findCourse(catalogCourses, studentProfile.courseName, studentProfile.campusName)
        }
    }

    val matrixSubtitle = remember(matchedCourse, studentProfile.courseName) {
        when {
            matchedCourse != null && matchedCourse.subjects.isNotEmpty() -> {
                "${matchedCourse.code} · ${matchedCourse.subjects.size} componentes · ${formatCurriculumHours(matchedCourse.chTotal)}h"
            }
            matchedCourse != null -> {
                "${matchedCourse.code} · ${matchedCourse.name} · ${formatCurriculumHours(matchedCourse.chTotal)}h"
            }
            studentProfile.courseName.isNotBlank() -> {
                "${normalizeUfopPortugueseText(studentProfile.courseName)} · Consultar matrizes"
            }
            else -> "Consultar catálogo com 54 cursos (ref. UFOP)"
        }
    }

    val lockStatus = remember(activeSection, studentProfile, activeSemester) {
        AppLockManager.getStatus(context)
    }
    val accessibilitySubtitle = remember(accessibilityController.settings) {
        val s = accessibilityController.settings
        val parts = mutableListOf("Fonte ${(s.fontScaleMultiplier * 100).toInt()}%")
        if (s.highContrastMode) parts.add("Alto contraste")
        if (s.screenReaderOptimized) parts.add("TalkBack")
        if (s.reduceMotion) parts.add("Movimento reduzido")
        parts.joinToString(" · ")
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (activeSection == null) {
            SettingsCategoryGroup(title = "Aparência, Segurança & Acessibilidade") {
                SettingsMenuRow(
                    icon = Icons.Outlined.Palette,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Personalização",
                    subtitle = "${if (themeController.isDark) "Modo escuro" else "Modo claro"} · Cor ${themeController.accentColor.displayName}",
                    onClick = { onSelectSection(SettingsSection.PERSONALIZATION) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                )
                SettingsMenuRow(
                    icon = Icons.Outlined.Security,
                    iconTint = if (lockStatus.lockEnabled) StatusAttendingGreen else MaterialTheme.colorScheme.primary,
                    title = "Segurança & Privacidade",
                    subtitle = if (lockStatus.lockEnabled) {
                        "Bloqueio ativo · Dados protegidos no aparelho e cópia de segurança"
                    } else {
                        "Bloqueio por senha/digital, proteção local e cópia de segurança"
                    },
                    onClick = { onSelectSection(SettingsSection.SECURITY) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                )
                SettingsMenuRow(
                    icon = Icons.Outlined.SettingsAccessibility,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Acessibilidade",
                    subtitle = accessibilitySubtitle,
                    onClick = { onSelectSection(SettingsSection.ACCESSIBILITY) }
                )
            }

            SettingsCategoryGroup(title = "Campus & Clima") {
                SettingsMenuRow(
                    icon = Icons.Outlined.LocationOn,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Campus & Clima Online",
                    subtitle = "${profileSummary.activeCampus.city} · ${profileSummary.activeCampus.weatherTempC} °C",
                    onClick = { onSelectSection(SettingsSection.CAMPUS_WEATHER) }
                )
            }

            SettingsCategoryGroup(title = "Acadêmico & Matriz") {
                SettingsMenuRow(
                    icon = Icons.Outlined.PictureAsPdf,
                    iconTint = DepartmentMathBlue,
                    title = "Documentos Acadêmicos (PDF)",
                    subtitle = "Enviar Atestado de Matrícula ou Histórico Escolar",
                    onClick = { onSelectSection(SettingsSection.DOCUMENTS_PDF) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                )
                SettingsMenuRow(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    iconTint = StatusAttendingGreen,
                    title = "Matriz Curricular (2026/2)",
                    subtitle = matrixSubtitle,
                    onClick = onOpenCurriculumMatrix
                )
            }

            SettingsCategoryGroup(title = "Calendário de Referência") {
                SettingsMenuRow(
                    icon = Icons.Outlined.CalendarMonth,
                    iconTint = DepartmentMathBlue,
                    title = "Calendário Acadêmico",
                    subtitle = "2026.2, 2027.1 e 2027.2 · ${profileSummary.calendarEvents.size} marcos ativos",
                    onClick = { onSelectSection(SettingsSection.ACADEMIC_CALENDAR) }
                )
            }

            SettingsCategoryGroup(title = "Dados & Suporte") {
                SettingsMenuRow(
                    icon = Icons.Outlined.Storage,
                    iconTint = DepartmentMathBlue,
                    title = "Armazenamento & Compartilhamento",
                    subtitle = exportFolderLabel,
                    onClick = { onSelectSection(SettingsSection.STORAGE_SHARE) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                )
                SettingsMenuRow(
                    icon = Icons.Outlined.SupportAgent,
                    iconTint = DepartmentMathBlue,
                    title = "Feedback & Suporte",
                    subtitle = "Relatar bug, sugestão ou enviar prints de tela",
                    onClick = { onSelectSection(SettingsSection.FEEDBACK) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                )
                SettingsMenuRow(
                    icon = Icons.Outlined.Explore,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Refazer Tour Guiado",
                    subtitle = "Rever a apresentação inicial das principais abas do app",
                    onClick = onReplayGuidedTour
                )
            }
        } else {
            when (activeSection) {
                SettingsSection.PERSONALIZATION -> {
                    ProfilePersonalizationSubView(
                        isDarkTheme = themeController.isDark,
                        onToggleTheme = { themeController.toggleTheme() },
                        selectedAccent = themeController.accentColor,
                        onSelectAccent = { themeController.selectAccentColor(it) }
                    )
                }

                SettingsSection.SECURITY -> {
                    SecurityAndPrivacySubView(
                        repository = repository,
                        activeSemester = activeSemester,
                        themeController = themeController,
                        accessibilityController = accessibilityController,
                        onAppSecurityChanged = onAppSecurityChanged,
                        onBackupRestored = onBackupRestored
                    )
                }

                SettingsSection.ACCESSIBILITY -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        AccessibilitySettingsPanel(
                            settings = accessibilityController.settings,
                            onUpdateSettings = accessibilityController.updateSettings
                        )
                    }
                }

                SettingsSection.CAMPUS_WEATHER -> {
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
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Escolha a unidade acadêmica para ajustar o clima e o contexto exibido na Agenda:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            profileSummary.campuses.forEach { campus ->
                                CampusSelectorOptionRow(
                                    campus = campus,
                                    onSelect = { onSelectCampus(campus.id) }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.WbSunny,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Atualização leve a cada 5 min",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = onRefreshWeather,
                                    enabled = !isRefreshingWeather,
                                    modifier = Modifier.heightIn(min = minTouchTarget),
                                    shape = RoundedCornerShape(22.dp)
                                ) {
                                    if (isRefreshingWeather) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Atualizar clima agora",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            if (weatherFeedback != null) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                                ) {
                                    Text(
                                        text = weatherFeedback,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsSection.DOCUMENTS_PDF -> {
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
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Envie o PDF do seu atestado (compatível com o Minha UFOP) para sincronizar disciplinas atuais ou importar o Histórico Escolar e calcular o CRE.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            FilledTonalButton(
                                onClick = onImportEnrollmentPdf,
                                enabled = !isImportingPdf,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = minTouchTarget),
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                if (isImportingPdf) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Processando Atestado...",
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
                                        text = "Enviar Atestado de Matrícula (PDF)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            OutlinedButton(
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
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Processando Histórico...",
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
                                        text = "Enviar Histórico Escolar (PDF)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (pdfFeedbackMessage != null) {
                                StatusFeedbackBanner(
                                    message = pdfFeedbackMessage,
                                    isError = pdfFeedbackIsError
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
                }

                SettingsSection.ACADEMIC_CALENDAR -> {
                    AcademicCalendarSubView(
                        repository = repository,
                        activeSemester = activeSemester,
                        mutationVersion = mutationVersion,
                        isAdminUnlocked = isAdminUnlocked,
                        adminPinInput = adminPinInput,
                        onAdminPinChange = onAdminPinChange,
                        onUnlockAdmin = onUnlockAdmin,
                        isPublishingCalendarPdf = isPublishingCalendarPdf,
                        onPublishCalendarPdf = onPublishCalendarPdf,
                        isExportingCalendarJson = isExportingCalendarJson,
                        onExportCalendarJson = onExportCalendarJson,
                        isImportingCalendarJson = isImportingCalendarJson,
                        onImportCalendarJson = onImportCalendarJson,
                        activePkg = activePkg,
                        adminFeedbackMessage = adminFeedbackMessage,
                        adminFeedbackIsError = adminFeedbackIsError
                    )
                }

                SettingsSection.STORAGE_SHARE -> {
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
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Exporte ou importe sua grade acadêmica $activeSemester para compartilhar com amigos, ou envie arquivos .ufopgrade pela aba Horários.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudOff,
                                        contentDescription = null,
                                        tint = safeGreenText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Seus dados ficam salvos offline neste aparelho · compartilhamento de grade (.ufopgrade) disponível na aba Horários.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            ExportFolderSettingBlock(repository = repository)

                            FilledTonalButton(
                                onClick = onOpenShareSchedule,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = minTouchTarget),
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Compartilhar ou importar grade",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = onExportLocalBackup,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = minTouchTarget),
                                shape = RoundedCornerShape(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Salvar cópia local ($activeSemester)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (backupStatusMessage != null) {
                                StatusFeedbackBanner(
                                    message = backupStatusMessage,
                                    isError = false
                                )
                            }
                        }
                    }
                }

                SettingsSection.FEEDBACK -> {
                    FeedbackSupportCard(
                        campusLabel = "${profileSummary.activeCampus.city} · ${profileSummary.activeCampus.name} · Semestre $activeSemester"
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryGroup(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .semantics { heading() }
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    val accessibleTint = accessibleTextColor(iconTint, isDark, MaterialTheme.colorScheme.surface)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minTouchTarget)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = RoundedCornerShape(12.dp),
            color = iconTint.copy(alpha = if (isDark) 0.18f else 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accessibleTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = "Abrir",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SettingsSubViewHeader(
    title: String,
    backLabel: String = "Voltar",
    onBack: () -> Unit
) {
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = campusMinTouchTarget()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = onBack,
                    modifier = if (a11y.largeTouchTargets) {
                        Modifier
                            .heightIn(min = minTouchTarget)
                            .widthIn(min = minTouchTarget)
                    } else {
                        Modifier
                            .minimumInteractiveComponentSize()
                            .heightIn(min = 36.dp)
                    },
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = if (a11y.largeTouchTargets) 10.dp else 6.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = backLabel,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = backLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AcademicCalendarSubView(
    repository: CampusCoreRepository,
    activeSemester: String,
    mutationVersion: Long,
    isAdminUnlocked: Boolean,
    adminPinInput: String,
    onAdminPinChange: (String) -> Unit,
    onUnlockAdmin: () -> Unit,
    isPublishingCalendarPdf: Boolean,
    onPublishCalendarPdf: (String) -> Unit,
    isExportingCalendarJson: Boolean,
    onExportCalendarJson: (String) -> Unit,
    isImportingCalendarJson: Boolean,
    onImportCalendarJson: () -> Unit,
    activePkg: AcademicCalendarUpdatePackage?,
    adminFeedbackMessage: String?,
    adminFeedbackIsError: Boolean
) {
    val isDark = LocalCampusThemeController.current.isDark
    val surfaceColor = MaterialTheme.colorScheme.surface
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = campusMinTouchTarget()
    val safeGreenText = accessibleTextColor(StatusAttendingGreen, isDark, surfaceColor)
    val accessibleBlue = accessibleTextColor(DepartmentMathBlue, isDark, surfaceColor)

    val availableSemesters = remember(mutationVersion) {
        repository.listAvailableCalendarSemesters().ifEmpty { listOf("2026.2", "2027.1", "2027.2") }
    }
    var selectedCalendarSemester by rememberSaveable(activeSemester) {
        mutableStateOf(activeSemester)
    }
    val semesterOptions = remember(availableSemesters, selectedCalendarSemester) {
        if (selectedCalendarSemester in availableSemesters) {
            availableSemesters
        } else {
            listOf(selectedCalendarSemester) + availableSemesters
        }
    }
    val events = remember(selectedCalendarSemester, mutationVersion) {
        repository.getAcademicCalendarEvents(selectedCalendarSemester)
    }
    val eventCountsBySemester = remember(semesterOptions, mutationVersion) {
        semesterOptions.associateWith { sem -> repository.getAcademicCalendarEvents(sem).size }
    }

    val officialSourceLabel = when (selectedCalendarSemester) {
        "2026.2" -> "Resolução CUNI 2945 (ref. pública)"
        "2027.1", "2027.2" -> "Calendário Presencial 2027 (ref. UFOP)"
        else -> "Calendário Acadêmico (ref. UFOP)"
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("Todos") }
    var advancedOptionsExpanded by rememberSaveable { mutableStateOf(false) }
    val expandedIds = remember { mutableStateMapOf<String, Boolean>() }
    val categories = listOf("Todos", "Feriados & Recessos", "Prazos & Matrícula", "Avaliações")

    val filteredEvents = remember(events, searchQuery, selectedCategory) {
        val query = searchQuery.trim().lowercase()
        events.filter { event ->
            val normalizedCat = event.category.lowercase()
                .replace("ç", "c")
                .replace("ã", "a")
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
            val matchesCategory = when (selectedCategory) {
                "Todos" -> true
                "Feriados & Recessos" -> normalizedCat.contains("feriado") ||
                    normalizedCat.contains("recesso") ||
                    normalizedCat.contains("ferias")
                "Prazos & Matrícula" -> normalizedCat.contains("prazo") ||
                    normalizedCat.contains("matric") ||
                    normalizedCat.contains("semestre") ||
                    normalizedCat.contains("inicio") ||
                    normalizedCat.contains("termino")
                "Avaliações" -> normalizedCat.contains("avaliac") ||
                    normalizedCat.contains("prova") ||
                    normalizedCat.contains("exame") ||
                    event.title.lowercase().contains("exame")
                else -> true
            }
            val matchesQuery = if (query.isEmpty()) {
                true
            } else {
                event.title.lowercase().contains(query) ||
                    event.category.lowercase().contains(query) ||
                    event.eventDate.lowercase().contains(query) ||
                    event.monthShort.lowercase().contains(query) ||
                    event.dayNumber.lowercase().contains(query)
            }
            matchesCategory && matchesQuery
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    semesterOptions.forEach { sem ->
                        val isSelected = selectedCalendarSemester == sem
                        val count = eventCountsBySemester[sem] ?: 0
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCalendarSemester = sem },
                            label = {
                                Text(
                                    text = "$sem ($count)",
                                    maxLines = 1,
                                    softWrap = false,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            modifier = Modifier.heightIn(min = if (a11y.largeTouchTargets) 48.dp else 34.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Semestre $selectedCalendarSemester · ${filteredEvents.size}/${events.size} marcos",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Referência: $officialSourceLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = accessibleBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar marco, mês ou categoria...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = { searchQuery = "" },
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
                    shape = RoundedCornerShape(18.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { category ->
                        val isSelected = selectedCategory == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            label = { Text(category, maxLines = 1, softWrap = false) },
                            modifier = Modifier.heightIn(min = if (a11y.largeTouchTargets) 48.dp else 34.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }

                if (filteredEvents.isEmpty()) {
                    Text(
                        text = if (events.isEmpty()) {
                            "Nenhum marco acadêmico cadastrado para $selectedCalendarSemester."
                        } else {
                            "Nenhum marco encontrado para o filtro selecionado."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        filteredEvents.forEach { event ->
                            val isExpanded = expandedIds[event.id] == true
                            CalendarMilestoneRow(
                                event = event,
                                isExpanded = isExpanded,
                                onToggleExpand = {
                                    expandedIds[event.id] = !isExpanded
                                }
                            )
                        }
                    }
                }
            }
        }

        // Card expansível discreto de Opções avançadas (Modo Administrador de Calendário)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTouchTarget)
                        .clickable { advancedOptionsExpanded = !advancedOptionsExpanded }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AdminPanelSettings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Opções avançadas",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Modo Administrador de Calendário e atualização manual",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = if (advancedOptionsExpanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = if (advancedOptionsExpanded) {
                            "Recolher opções avançadas"
                        } else {
                            "Expandir opções avançadas"
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                CampusAnimatedVisibility(visible = advancedOptionsExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Text(
                            text = "Modo Administrador de Calendário",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )

                        if (!isAdminUnlocked) {
                            Text(
                                text = "Área avançada para importar um novo PDF de Calendário Acadêmico, ou aplicar um pacote compartilhado entre estudantes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = adminPinInput,
                                onValueChange = onAdminPinChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("PIN de manutenção") },
                                placeholder = { Text("Informe o PIN de manutenção") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                shape = RoundedCornerShape(18.dp)
                            )

                            Button(
                                onClick = onUnlockAdmin,
                                enabled = adminPinInput.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = minTouchTarget),
                                shape = RoundedCornerShape(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Desbloquear modo administrador",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Text(
                                text = "Recebeu um arquivo de atualização do calendário?",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            FilledTonalButton(
                                onClick = onImportCalendarJson,
                                enabled = !isImportingCalendarJson,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = minTouchTarget),
                                shape = RoundedCornerShape(22.dp)
                            ) {
                                if (isImportingCalendarJson) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.UploadFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Importar arquivo de calendário",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = StatusAttendingGreen.copy(alpha = if (isDark) 0.16f else 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.VerifiedUser,
                                        contentDescription = null,
                                        tint = safeGreenText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Modo Administrador ativo · Verificação de integridade ativa",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = safeGreenText
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = { onPublishCalendarPdf(selectedCalendarSemester) },
                                enabled = !isPublishingCalendarPdf,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = minTouchTarget),
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                if (isPublishingCalendarPdf) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Processando PDF do calendário...",
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
                                        text = "Enviar PDF do Calendário ($selectedCalendarSemester)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { onExportCalendarJson(selectedCalendarSemester) },
                                enabled = !isExportingCalendarJson,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = minTouchTarget),
                                shape = RoundedCornerShape(22.dp)
                            ) {
                                if (isExportingCalendarJson) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.FileDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Exportar arquivo de calendário ($selectedCalendarSemester)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (activePkg != null) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = surfaceColor
                                ) {
                                    Text(
                                        text = "Versão ${activePkg.version} · ${activePkg.events.size} eventos sincronizados",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        if (adminFeedbackMessage != null) {
                            StatusFeedbackBanner(
                                message = adminFeedbackMessage,
                                isError = adminFeedbackIsError
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusFeedbackBanner(
    message: String,
    isError: Boolean,
    onDismiss: (() -> Unit)? = null
) {
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    val baseAccent = if (isError) StatusAbsentRed else StatusAttendingGreen
    val textAccent = accessibleTextColor(baseAccent, isDark, MaterialTheme.colorScheme.surface)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = baseAccent.copy(alpha = if (isDark) 0.16f else 0.12f),
        border = if (onDismiss != null) BorderStroke(1.dp, textAccent.copy(alpha = 0.4f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = if (onDismiss != null) 4.dp else 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = textAccent,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(minTouchTarget)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar aviso",
                        tint = textAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditStudentProfileBottomSheet(
    initialProfile: StudentProfile,
    avatarBitmap: ImageBitmap?,
    onDismiss: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onSave: (StudentProfile, UfopCourseCurriculum?) -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    val accessibleRed = accessibleTextColor(StatusAbsentRed, isDark, MaterialTheme.colorScheme.surface)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val catalogCourses by produceState<List<UfopCourseCurriculum>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) { UfopCurriculumCatalog.getAllCourses(context) }
    }
    var fullName by remember { mutableStateOf(initialProfile.fullName) }
    var courseName by remember {
        mutableStateOf(
            initialProfile.courseName.trim().let {
                if (it.isBlank()) "" else normalizeUfopPortugueseText(it)
            }
        )
    }
    var identifiedCourse by remember { mutableStateOf<UfopCourseCurriculum?>(null) }
    var registrationNumber by remember { mutableStateOf(initialProfile.registrationNumber) }
    var activeSemester by remember {
        mutableStateOf(initialProfile.activeSemester.ifBlank { "2026.2" })
    }
    var selectedCampus by remember {
        mutableStateOf(
            UFOP_CAMPUS_OPTIONS.find {
                it.equals(initialProfile.campusName, ignoreCase = true)
            } ?: "Ouro Preto"
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "IDENTIDADE ACADÊMICA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Editar perfil",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(minTouchTarget)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar"
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                    ) {
                        if (avatarBitmap != null) {
                            Image(
                                bitmap = avatarBitmap,
                                contentDescription = "Foto de perfil atual",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                val initials = extractInitials(fullName)
                                if (initials.isNotEmpty()) {
                                    Text(
                                        text = initials,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Person,
                                        contentDescription = "Sem foto",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = onPickPhoto,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = minTouchTarget),
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AddAPhoto,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Trocar foto",
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        if (!initialProfile.avatarPath.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = onRemovePhoto,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = minTouchTarget),
                                shape = RoundedCornerShape(22.dp),
                                border = BorderStroke(1.dp, accessibleRed.copy(alpha = 0.55f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = accessibleRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Remover foto",
                                    color = accessibleRed,
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nome completo") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )

            OutlinedTextField(
                value = courseName,
                onValueChange = {
                    courseName = it
                    identifiedCourse = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Curso") },
                placeholder = { Text("Ex.: Engenharia de Controle e Automação") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )

            UfopCourseSuggestionAndPickerBlock(
                courseName = courseName,
                campusHint = selectedCampus,
                catalogCourses = catalogCourses,
                identifiedCourse = identifiedCourse,
                onSelectCourse = { chosen ->
                    courseName = normalizeUfopPortugueseText(chosen.name)
                    selectedCampus = resolveUfopCampusOption(chosen.campus, selectedCampus)
                    identifiedCourse = chosen
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = registrationNumber,
                    onValueChange = { registrationNumber = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Matrícula") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
                OutlinedTextField(
                    value = activeSemester,
                    onValueChange = { activeSemester = it },
                    modifier = Modifier.weight(0.75f),
                    label = { Text("Semestre") },
                    placeholder = { Text("2026.2") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
            }

            Text(
                text = "Campus universitário",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                UFOP_CAMPUS_OPTIONS.forEach { campus ->
                    val isSelected = selectedCampus == campus
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCampus = campus },
                        label = { Text(campus) },
                        modifier = Modifier.heightIn(min = minTouchTarget),
                        shape = RoundedCornerShape(22.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = minTouchTarget),
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
                        val normalizedCourse = courseName.trim().let {
                            if (it.isBlank()) "" else normalizeUfopPortugueseText(it)
                        }
                        val matched = identifiedCourse ?: UfopCurriculumCatalog.findCourse(
                            courses = catalogCourses,
                            courseNameOrCode = normalizedCourse,
                            campusHint = selectedCampus
                        )
                        onSave(
                            initialProfile.copy(
                                fullName = fullName.trim(),
                                courseName = normalizedCourse,
                                registrationNumber = registrationNumber.trim(),
                                activeSemester = activeSemester.trim().ifBlank { "2026.2" },
                                campusName = selectedCampus,
                                onboardingCompleted = true
                            ),
                            matched
                        )
                    },
                    modifier = Modifier.heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Salvar perfil")
                }
            }
        }
    }
}

@Composable
private fun CampusSelectorOptionRow(
    campus: UfopCampusInfo,
    onSelect: () -> Unit
) {
    val minTouchTarget = campusMinTouchTarget()
    val label = when (campus.id) {
        "op_cruzeiro" -> "Ouro Preto · Morro do Cruzeiro"
        "mar_ichs" -> "Mariana · ICHS / ICSA"
        "jm_icea" -> "João Monlevade · ICEA"
        else -> "${campus.city} · ${campus.name}"
    }

    Surface(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minTouchTarget),
        shape = RoundedCornerShape(18.dp),
        color = if (campus.isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (campus.isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${campus.weatherTempC} °C · ${campus.weatherCondition}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (campus.isActive) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Campus ativo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarMilestoneRow(
    event: AcademicCalendarEvent,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {}
) {
    val isDark = LocalCampusThemeController.current.isDark
    val surfaceColor = MaterialTheme.colorScheme.surface
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = campusMinTouchTarget()
    val accessibleBlue = accessibleTextColor(DepartmentMathBlue, isDark, surfaceColor)

    Surface(
        onClick = onToggleExpand,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minTouchTarget),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(width = 52.dp, height = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = DepartmentMathBlue.copy(alpha = if (isDark) 0.18f else 0.12f),
                    border = BorderStroke(1.dp, accessibleBlue.copy(alpha = 0.32f))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = event.dayNumber,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = accessibleBlue
                        )
                        Text(
                            text = event.monthShort.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = accessibleBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${event.category} · ${event.dayNumber} ${event.monthShort}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (isExpanded) "Recolher detalhes" else "Expandir detalhes",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            CampusAnimatedVisibility(visible = isExpanded) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = surfaceColor
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Data: ${event.eventDate.ifBlank { "${event.dayNumber} ${event.monthShort}" }}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = accessibleBlue
                        )
                        Text(
                            text = "Categoria: ${event.category} · Semestre ${event.semester}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleJsonShareBottomSheet(
    repository: CampusCoreRepository,
    semester: String,
    onDismiss: () -> Unit,
    onImported: (Int) -> Unit
) {
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    val safeGreenText = accessibleTextColor(StatusAttendingGreen, isDark, MaterialTheme.colorScheme.surface)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = LocalClipboardManager.current
    val exportedJson = remember(semester) { repository.exportSchedulePayload(semester) }
    var importJsonInput by remember { mutableStateOf("") }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var feedbackIsError by remember { mutableStateOf(false) }
    var isSavingBundle by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GRADE ACADÊMICA · COMPARTILHAMENTO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Compartilhar ou Importar Grade",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(minTouchTarget)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar"
                    )
                }
            }

            Text(
                text = "Código da sua grade do semestre $semester:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = exportedJson,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(exportedJson))
                        feedbackMessage = "Código da grade $semester copiado para a área de transferência."
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copiar código")
                }

                OutlinedButton(
                    onClick = {
                        importJsonInput = exportedJson
                        feedbackIsError = false
                        feedbackMessage = "Exemplo carregado no campo de importação abaixo."
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Text("Usar exemplo", maxLines = 1, softWrap = false)
                }
            }

            Button(
                onClick = {
                    if (isSavingBundle) return@Button
                    isSavingBundle = true
                    feedbackMessage = null
                    val filename = "grade_ufop_${semester.replace(".", "_")}.ufopgrade"
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                GradeExportStorage.saveGradeBundle(
                                    context = context,
                                    repository = repository,
                                    filename = filename,
                                    jsonPayload = exportedJson
                                )
                            }
                        }
                        isSavingBundle = false
                        result.onSuccess { saved ->
                            feedbackIsError = false
                            feedbackMessage = "Grade salva: ${saved.displayPath}"
                        }.onFailure { error ->
                            feedbackIsError = true
                            feedbackMessage = error.message ?: "Não foi possível salvar o arquivo .ufopgrade."
                        }
                    }
                },
                enabled = !isSavingBundle,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minTouchTarget),
                shape = RoundedCornerShape(22.dp)
            ) {
                if (isSavingBundle) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("Salvar arquivo .ufopgrade", maxLines = 1, softWrap = false)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = "Importar grade de outro estudante:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        val pasted = clipboardManager.getText()?.text.orEmpty()
                        if (pasted.isBlank()) {
                            feedbackIsError = true
                            feedbackMessage = "A área de transferência está vazia."
                        } else {
                            importJsonInput = pasted
                            feedbackIsError = false
                            feedbackMessage = null
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Colar", maxLines = 1, softWrap = false)
                }

                OutlinedButton(
                    onClick = {
                        importJsonInput = ""
                        feedbackMessage = null
                    },
                    enabled = importJsonInput.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Limpar", maxLines = 1, softWrap = false)
                }
            }

            OutlinedTextField(
                value = importJsonInput,
                onValueChange = { importJsonInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 230.dp),
                placeholder = {
                    Text("Cole aqui o código da grade copiado do app...")
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                minLines = 6,
                maxLines = 10,
                shape = RoundedCornerShape(18.dp)
            )

            if (importJsonInput.isNotEmpty()) {
                Text(
                    text = "✓ ${importJsonInput.length} caracteres colados",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = safeGreenText
                )
            }

            if (feedbackMessage != null) {
                StatusFeedbackBanner(
                    message = feedbackMessage!!,
                    isError = feedbackIsError
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Fechar")
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = {
                        val importedCount = repository.importSchedulePayload(importJsonInput)
                        onImported(importedCount)
                        onDismiss()
                    },
                    enabled = importJsonInput.isNotBlank(),
                    modifier = Modifier.heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Importar grade")
                }
            }
        }
    }
}

@Composable
private fun ExportFolderSettingBlock(
    repository: CampusCoreRepository
) {
    val context = LocalContext.current
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    val accessibleBlue = accessibleTextColor(DepartmentMathBlue, isDark, MaterialTheme.colorScheme.surface)
    var configVersion by remember { mutableIntStateOf(0) }
    val exportFolderConfig = remember(configVersion) { repository.getExportFolderConfig() }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        runCatching {
            val label = GradeExportStorage.resolveFolderDisplayLabel(context, uri)
            repository.setExportFolderConfig(uri.toString(), label)
            label
        }.onSuccess { label ->
            configVersion += 1
            statusIsError = false
            statusMessage = "Pasta padrão atualizada: $label"
        }.onFailure { error ->
            statusIsError = true
            statusMessage = error.message ?: "Não foi possível definir a pasta selecionada."
        }
    }

    val folderLabel = exportFolderConfig.displayLabel.orEmpty().ifBlank { "Documentos/VidaAcademica/Grades" }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = accessibleBlue,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pasta de salvamento (.ufopgrade)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = folderLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { folderPickerLauncher.launch(null) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Alterar pasta de salvamento",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                if (exportFolderConfig.treeUri != null) {
                    TextButton(
                        onClick = {
                            repository.setExportFolderConfig(null, null)
                            configVersion += 1
                            statusIsError = false
                            statusMessage = "Pasta padrão restaurada: Documentos/VidaAcademica/Grades"
                        },
                        modifier = Modifier.heightIn(min = minTouchTarget),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Text(
                            text = "Restaurar padrão",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            if (statusMessage != null) {
                StatusFeedbackBanner(
                    message = statusMessage!!,
                    isError = statusIsError,
                    onDismiss = { statusMessage = null }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedbackSupportCard(
    campusLabel: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    val accessibleBlue = accessibleTextColor(DepartmentMathBlue, isDark, MaterialTheme.colorScheme.surface)
    val categories = listOf("Bug / Erro", "Sugestão", "Visual", "Outro")

    var selectedCategory by rememberSaveable { mutableStateOf(categories.first()) }
    var senderName by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    val screenshots = remember { mutableStateListOf<Uri>() }
    var isSendingFeedback by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var pendingFeedbackCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        pendingFeedbackCount = withContext(Dispatchers.IO) { getPendingFeedbackCount(context) }
    }

    val screenshotPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        val room = FEEDBACK_MAX_SCREENSHOTS - screenshots.size
        uris.filter { it !in screenshots }.take(room.coerceAtLeast(0)).forEach { screenshots.add(it) }
        if (uris.size > room) {
            statusIsError = false
            statusMessage = "Máximo de $FEEDBACK_MAX_SCREENSHOTS prints por feedback."
        }
    }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SupportAgent,
                    contentDescription = null,
                    tint = accessibleBlue,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Feedback & Suporte (com prints de tela)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Relate um problema ou sugira melhorias. O envio é feito direto pelo app, sem abrir e-mail.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category, maxLines = 1, softWrap = false) },
                        modifier = Modifier.heightIn(min = minTouchTarget),
                        shape = RoundedCornerShape(22.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            OutlinedTextField(
                value = senderName,
                onValueChange = { senderName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Seu nome (opcional)") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Mensagem") },
                placeholder = { Text("Descreva o que aconteceu ou sua ideia...") },
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(18.dp)
            )

            OutlinedButton(
                onClick = { screenshotPickerLauncher.launch("image/*") },
                enabled = !isSendingFeedback && screenshots.size < FEEDBACK_MAX_SCREENSHOTS,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minTouchTarget),
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Anexar prints (até 3)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            if (screenshots.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    screenshots.toList().forEach { uri ->
                        FeedbackScreenshotThumbnail(
                            uri = uri,
                            enabled = !isSendingFeedback,
                            onRemove = { screenshots.remove(uri) }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    isSendingFeedback = true
                    statusMessage = null
                    val picked = screenshots.toList()
                    scope.launch {
                        val result = sendFeedback(
                            context = context,
                            category = selectedCategory,
                            message = message,
                            senderName = senderName,
                            campusLabel = campusLabel,
                            screenshotUris = picked
                        )
                        isSendingFeedback = false
                        statusIsError = result.isError
                        statusMessage = result.statusMessage
                        if (result.sentImmediately || result.queuedOffline) {
                            message = ""
                            screenshots.clear()
                        }
                        pendingFeedbackCount = withContext(Dispatchers.IO) { getPendingFeedbackCount(context) }
                    }
                },
                enabled = !isSendingFeedback && message.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minTouchTarget),
                shape = RoundedCornerShape(22.dp)
            ) {
                if (isSendingFeedback) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enviando...",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enviar feedback",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (pendingFeedbackCount > 0) {
                OutlinedButton(
                    onClick = {
                        isSendingFeedback = true
                        statusMessage = null
                        scope.launch {
                            val result = retryPendingFeedback(context)
                            isSendingFeedback = false
                            statusIsError = false
                            statusMessage = result.statusMessage
                            pendingFeedbackCount = withContext(Dispatchers.IO) { getPendingFeedbackCount(context) }
                        }
                    },
                    enabled = !isSendingFeedback,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reenviar pendentes ($pendingFeedbackCount)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (statusMessage != null) {
                StatusFeedbackBanner(
                    message = statusMessage!!,
                    isError = statusIsError,
                    onDismiss = { statusMessage = null }
                )
            }
        }
    }
}

@Composable
private fun FeedbackScreenshotThumbnail(
    uri: Uri,
    enabled: Boolean,
    onRemove: () -> Unit
) {
    val minTouchTarget = campusMinTouchTarget()
    val thumbnail = rememberUriThumbnail(uri, maxSidePx = 160)
    Box(modifier = Modifier.size(width = 104.dp, height = 108.dp)) {
        Surface(
            modifier = Modifier
                .size(88.dp)
                .align(Alignment.BottomStart),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = "Print anexado",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
        IconButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier
                .size(minTouchTarget)
                .align(Alignment.TopEnd)
        ) {
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = StatusAbsentRed,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remover print anexado",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatSummaryBox(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SecurityAndPrivacySubView(
    repository: CampusCoreRepository,
    activeSemester: String,
    themeController: br.ufop.campus.ui.theme.CampusThemeController,
    accessibilityController: br.ufop.campus.ui.theme.CampusAccessibilityController,
    onAppSecurityChanged: () -> Unit,
    onBackupRestored: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = LocalCampusThemeController.current.isDark
    val surfaceColor = MaterialTheme.colorScheme.surface
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = campusMinTouchTarget()
    val safeGreenText = accessibleTextColor(StatusAttendingGreen, isDark, surfaceColor)
    val accessibleRed = accessibleTextColor(StatusAbsentRed, isDark, surfaceColor)

    var lockStatus by remember { mutableStateOf(AppLockManager.getStatus(context)) }
    var securityFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var securityFeedbackIsError by remember { mutableStateOf(false) }

    var showEnableLockDialog by remember { mutableStateOf(false) }
    var showDisableLockDialog by remember { mutableStateOf(false) }

    var showExportBackupDialog by remember { mutableStateOf(false) }
    var isExportingBackup by remember { mutableStateOf(false) }
    var lastExportedPath by remember { mutableStateOf<String?>(null) }
    var lastExportedShareUri by remember { mutableStateOf<Uri?>(null) }
    var lastExportedFilename by remember { mutableStateOf("campus_ufop_backup.ufopbackup") }

    var pendingRestorePayload by remember { mutableStateOf<String?>(null) }
    var isReadingBackupFile by remember { mutableStateOf(false) }
    var isRestoringBackup by remember { mutableStateOf(false) }
    var backupFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var backupFeedbackIsError by remember { mutableStateOf(false) }

    val restoreBackupPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isReadingBackupFile = true
        backupFeedbackMessage = null
        backupFeedbackIsError = false
        scope.launch {
            val readResult = withContext(Dispatchers.IO) {
                runCatching {
                    GradeExportStorage.readPayloadFromUriSafely(context, uri)
                }
            }
            isReadingBackupFile = false
            readResult.onSuccess { payload ->
                if (!payload.trim().startsWith(CampusCryptoEngine.BACKUP_PREFIX)) {
                    backupFeedbackIsError = true
                    backupFeedbackMessage =
                        "Arquivo inválido. Selecione um arquivo de backup (.ufopbackup) gerado pelo aplicativo."
                } else {
                    pendingRestorePayload = payload
                }
            }.onFailure { err ->
                backupFeedbackIsError = true
                backupFeedbackMessage = err.message ?: "Não foi possível ler o arquivo selecionado."
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Card de status da proteção ativa
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = StatusAttendingGreen.copy(alpha = if (isDark) 0.14f else 0.08f),
            border = BorderStroke(1.dp, safeGreenText.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        tint = safeGreenText,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Proteção local ativa",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = safeGreenText,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Seus dados acadêmicos ficam salvos e protegidos apenas neste aparelho",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = safeGreenText,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "PDFs importados são apagados automaticamente logo após a leitura",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 2. Controle de Bloqueio do App por Senha e Digital
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bloqueio do App por Senha e Digital",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "Pede sua senha ou digital ao abrir o aplicativo para proteger suas notas, histórico e agenda.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!lockStatus.lockEnabled) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minTouchTarget)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showEnableLockDialog = true },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ativar bloqueio por senha",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (lockStatus.biometricHardwareAvailable) {
                                        "Senha de pelo menos 4 caracteres + opção de Digital / Biometria"
                                    } else {
                                        "Senha de pelo menos 4 caracteres salva no aparelho"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = false,
                                onCheckedChange = { showEnableLockDialog = true },
                                modifier = Modifier.heightIn(min = minTouchTarget),
                                colors = campusSwitchColors()
                            )
                        }
                    }

                    Button(
                        onClick = { showEnableLockDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minTouchTarget),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ativar bloqueio por senha",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = StatusAttendingGreen.copy(alpha = if (isDark) 0.16f else 0.12f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = safeGreenText,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Bloqueio por senha ativo neste aparelho",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = safeGreenText
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minTouchTarget),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = "Desbloqueio por Digital / Biometria",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (lockStatus.biometricHardwareAvailable) {
                                            if (lockStatus.biometricEnabled) "Ativado" else "Desativado"
                                        } else {
                                            "Biometria indisponível ou não cadastrada neste aparelho"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = lockStatus.biometricEnabled,
                                enabled = lockStatus.biometricHardwareAvailable,
                                onCheckedChange = { enableBio ->
                                    val res = AppLockManager.setBiometricEnabled(
                                        context = context,
                                        currentPasswordOrNull = null,
                                        enabled = enableBio
                                    )
                                    res.onSuccess {
                                        lockStatus = AppLockManager.getStatus(context)
                                        securityFeedbackIsError = false
                                        securityFeedbackMessage = if (enableBio) {
                                            "Desbloqueio por Digital / Biometria ativado."
                                        } else {
                                            "Desbloqueio por Digital / Biometria desativado."
                                        }
                                        onAppSecurityChanged()
                                    }.onFailure { err ->
                                        securityFeedbackIsError = true
                                        securityFeedbackMessage = err.message ?: "Não foi possível alterar a biometria."
                                    }
                                },
                                modifier = Modifier.heightIn(min = minTouchTarget),
                                colors = campusSwitchColors()
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showDisableLockDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minTouchTarget),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, accessibleRed.copy(alpha = 0.55f))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LockOpen,
                            contentDescription = null,
                            tint = accessibleRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Desativar bloqueio por senha",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = accessibleRed
                        )
                    }
                }

                if (securityFeedbackMessage != null) {
                    StatusFeedbackBanner(
                        message = securityFeedbackMessage!!,
                        isError = securityFeedbackIsError,
                        onDismiss = { securityFeedbackMessage = null }
                    )
                }
            }
        }

        // 3. Cópia de segurança protegida da conta (.ufopbackup)
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Backup protegido da conta (.ufopbackup)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Salve uma cópia completa da sua conta (perfil, disciplinas, faltas, histórico, notas, calendário e preferências) protegida com uma senha escolhida por você para restaurar quando trocar de celular.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { showExportBackupDialog = true },
                    enabled = !isExportingBackup && !isRestoringBackup,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Gerar e baixar backup protegido",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { restoreBackupPickerLauncher.launch(arrayOf("*/*")) },
                    enabled = !isReadingBackupFile && !isRestoringBackup && !isExportingBackup,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    if (isReadingBackupFile) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Restaurar backup protegido",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (lastExportedPath != null && lastExportedShareUri != null) {
                    FilledTonalButton(
                        onClick = {
                            shareBackupUri(context, lastExportedShareUri!!, lastExportedFilename)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minTouchTarget),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Compartilhar backup salvo",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (backupFeedbackMessage != null) {
                    StatusFeedbackBanner(
                        message = backupFeedbackMessage!!,
                        isError = backupFeedbackIsError,
                        onDismiss = { backupFeedbackMessage = null }
                    )
                }
            }
        }
    }

    if (showEnableLockDialog) {
        EnableAppLockDialog(
            biometricAvailable = lockStatus.biometricHardwareAvailable,
            onDismiss = { showEnableLockDialog = false },
            onConfirm = { password, allowBiometric ->
                val result = AppLockManager.enableLock(context, password, allowBiometric)
                result.onSuccess {
                    lockStatus = AppLockManager.getStatus(context)
                    showEnableLockDialog = false
                    securityFeedbackIsError = false
                    securityFeedbackMessage = if (allowBiometric && lockStatus.biometricHardwareAvailable) {
                        "Bloqueio por senha e digital ativado!"
                    } else {
                        "Bloqueio por senha ativado!"
                    }
                    onAppSecurityChanged()
                }.onFailure { err ->
                    securityFeedbackIsError = true
                    securityFeedbackMessage = err.message ?: "Não foi possível ativar o bloqueio."
                }
            }
        )
    }

    if (showDisableLockDialog) {
        DisableAppLockDialog(
            onDismiss = { showDisableLockDialog = false },
            onVerifyAndDisable = { currentPassword ->
                when (val verify = AppLockManager.disableLock(context, currentPassword)) {
                    is AppLockManager.VerifyLockResult.Success -> {
                        lockStatus = AppLockManager.getStatus(context)
                        showDisableLockDialog = false
                        securityFeedbackIsError = false
                        securityFeedbackMessage = "Bloqueio por senha desativado."
                        onAppSecurityChanged()
                        null
                    }
                    is AppLockManager.VerifyLockResult.Failed -> {
                        if (verify.lockoutMs > 0L) {
                            val seconds = (verify.lockoutMs + 999L) / 1000L
                            "Muitas tentativas incorretas. Aguarde ${seconds}s."
                        } else {
                            "Senha incorreta (${verify.attemptsRemaining} tentativas restantes)."
                        }
                    }
                }
            }
        )
    }

    if (showExportBackupDialog) {
        ExportEncryptedBackupDialog(
            isExporting = isExportingBackup,
            onDismiss = { if (!isExportingBackup) showExportBackupDialog = false },
            onConfirm = { password ->
                isExportingBackup = true
                backupFeedbackMessage = null
                backupFeedbackIsError = false
                val filename = "campus_ufop_backup_${activeSemester.replace(".", "_")}.ufopbackup"
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val json = repository.exportFullAccountSnapshotJson()
                            val encrypted = CampusCryptoEngine.encryptBackupPayload(json, password.toCharArray())
                            GradeExportStorage.saveEncryptedBackup(
                                context = context,
                                repository = repository,
                                filename = filename,
                                encryptedPayload = encrypted
                            )
                        }
                    }
                    isExportingBackup = false
                    result.onSuccess { saved ->
                        lastExportedPath = saved.displayPath
                        lastExportedShareUri = saved.shareUri
                        lastExportedFilename = filename
                        showExportBackupDialog = false
                        backupFeedbackIsError = false
                        backupFeedbackMessage = "Backup salvo em: ${saved.displayPath}"
                    }.onFailure { err ->
                        backupFeedbackIsError = true
                        backupFeedbackMessage = err.message ?: "Falha ao gerar o backup."
                    }
                }
            }
        )
    }

    pendingRestorePayload?.let { payload ->
        RestoreEncryptedBackupDialog(
            isRestoring = isRestoringBackup,
            onDismiss = {
                if (!isRestoringBackup) {
                    pendingRestorePayload = null
                }
            },
            onConfirm = { password, onError ->
                isRestoringBackup = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val decryptedJson = CampusCryptoEngine.decryptBackupPayload(
                                encryptedBundle = payload,
                                password = password.toCharArray()
                            )
                            val restored = repository.restoreFullAccountSnapshotJson(decryptedJson)
                            if (!restored) {
                                throw IllegalStateException("Dados do backup inválidos ou incompatíveis.")
                            }
                        }
                    }
                    isRestoringBackup = false
                    result.onSuccess {
                        syncThemeAndA11yAfterBackupRestore(
                            context = context,
                            repository = repository,
                            themeController = themeController,
                            accessibilityController = accessibilityController
                        )
                        onBackupRestored()
                        pendingRestorePayload = null
                        backupFeedbackIsError = false
                        backupFeedbackMessage = "Backup restaurado com sucesso! Dados, tema e acessibilidade sincronizados."
                    }.onFailure { err ->
                        onError(err.message ?: "Senha incorreta ou backup corrompido.")
                    }
                }
            }
        )
    }
}

@Composable
private fun EnableAppLockDialog(
    biometricAvailable: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (password: String, allowBiometric: Boolean) -> Unit
) {
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var allowBiometric by remember { mutableStateOf(biometricAvailable) }
    var showPassword by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Ativar bloqueio por senha",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Defina uma senha com pelo menos 4 caracteres para proteger a abertura do aplicativo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        validationError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nova senha (mín. 4 caracteres)") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showPassword = !showPassword },
                            modifier = Modifier.size(minTouchTarget)
                        ) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) "Ocultar senha" else "Mostrar senha"
                            )
                        }
                    },
                    shape = RoundedCornerShape(16.dp)
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        validationError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirmar senha") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    shape = RoundedCornerShape(16.dp)
                )

                if (biometricAvailable) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minTouchTarget)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { allowBiometric = !allowBiometric }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Liberar Digital / Biometria",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Usar leitor biométrico para desbloquear",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = allowBiometric,
                            onCheckedChange = { allowBiometric = it },
                            modifier = Modifier.heightIn(min = minTouchTarget),
                            colors = campusSwitchColors()
                        )
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
                    when {
                        password.trim().length < 4 -> {
                            validationError = "A senha deve ter pelo menos 4 caracteres."
                        }
                        password != confirmPassword -> {
                            validationError = "As senhas informadas não coincidem."
                        }
                        else -> {
                            onConfirm(password, allowBiometric && biometricAvailable)
                        }
                    }
                },
                modifier = Modifier.heightIn(min = minTouchTarget),
                shape = RoundedCornerShape(22.dp)
            ) {
                Text("Ativar")
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
private fun DisableAppLockDialog(
    onDismiss: () -> Unit,
    onVerifyAndDisable: (currentPassword: String) -> String?
) {
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    var currentPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Desativar bloqueio por senha",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Confirme sua senha atual para remover o bloqueio do aplicativo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        errorText = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Senha atual") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showPassword = !showPassword },
                            modifier = Modifier.size(minTouchTarget)
                        ) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) "Ocultar senha" else "Mostrar senha"
                            )
                        }
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = accessibleTextColor(StatusAbsentRed, isDark, MaterialTheme.colorScheme.surface)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentPassword.isBlank()) {
                        errorText = "Informe sua senha atual."
                    } else {
                        errorText = onVerifyAndDisable(currentPassword)
                    }
                },
                modifier = Modifier.heightIn(min = minTouchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusAbsentRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(22.dp)
            ) {
                Text("Desativar")
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
private fun ExportEncryptedBackupDialog(
    isExporting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit
) {
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Gerar backup protegido",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Crie uma senha (mín. 4 caracteres) para proteger seu arquivo de backup (.ufopbackup). Guarde essa senha para restaurar seus dados depois.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorText = null
                    },
                    enabled = !isExporting,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Senha do backup (mín. 4 caracteres)") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showPassword = !showPassword },
                            modifier = Modifier.size(minTouchTarget)
                        ) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) "Ocultar senha" else "Mostrar senha"
                            )
                        }
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorText = null
                    },
                    enabled = !isExporting,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirmar senha do backup") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    shape = RoundedCornerShape(16.dp)
                )
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = accessibleTextColor(StatusAbsentRed, isDark, MaterialTheme.colorScheme.surface)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        password.trim().length < 4 -> {
                            errorText = "A senha deve ter pelo menos 4 caracteres."
                        }
                        password != confirmPassword -> {
                            errorText = "As senhas não coincidem."
                        }
                        else -> onConfirm(password)
                    }
                },
                enabled = !isExporting,
                modifier = Modifier.heightIn(min = minTouchTarget),
                shape = RoundedCornerShape(22.dp)
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("Gerar e baixar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isExporting,
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
private fun RestoreEncryptedBackupDialog(
    isRestoring: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (password: String, onError: (String) -> Unit) -> Unit
) {
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Restaurar backup protegido",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Informe a senha usada quando este arquivo .ufopbackup foi gerado. Os dados atuais do aparelho serão substituídos pelos do backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorText = null
                    },
                    enabled = !isRestoring,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Senha do backup") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showPassword = !showPassword },
                            modifier = Modifier.size(minTouchTarget)
                        ) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) "Ocultar senha" else "Mostrar senha"
                            )
                        }
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = accessibleTextColor(StatusAbsentRed, isDark, MaterialTheme.colorScheme.surface)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password.isBlank()) {
                        errorText = "Informe a senha do backup."
                    } else {
                        onConfirm(password) { msg -> errorText = msg }
                    }
                },
                enabled = !isRestoring,
                modifier = Modifier.heightIn(min = minTouchTarget),
                shape = RoundedCornerShape(22.dp)
            ) {
                if (isRestoring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("Restaurar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isRestoring,
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

private data class FaqAccordionItem(
    val id: String,
    val question: String,
    val answer: String
)

private val PROFILE_FAQ_ITEMS = listOf(
    FaqAccordionItem(
        id = "ufop_absences_25",
        question = "Como funciona o limite de 25% de faltas na UFOP?",
        answer = "Na UFOP, cada hora-aula tem 50 minutos e a aprovação exige no mínimo 75% de frequência (limite de 25% de faltas). Por exemplo, uma disciplina de 60h corresponde a 72 aulas de 50 min, resultando no limite de 18 faltas no semestre (30h = 36 aulas -> 9 faltas; 90h = 108 aulas -> 27 faltas). Aulas marcadas como canceladas não contam como falta."
    ),
    FaqAccordionItem(
        id = "android_widget",
        question = "Como adicionar o widget de horários na tela inicial do Android?",
        answer = "Mantenha pressionado um espaço vazio na tela inicial do seu Android, toque em \"Widgets\" e procure por \"Vida Acadêmica\". Arraste o widget de horários para a tela inicial para acompanhar suas próximas aulas do dia sem abrir o app — ele se adapta automaticamente ao seu tema, cor de destaque e mudanças na grade."
    ),
    FaqAccordionItem(
        id = "share_compare_schedule",
        question = "Como compartilhar minha grade e comparar horários com amigos?",
        answer = "Na aba Disciplinas, abra a Grade Horária e use a opção de compartilhar para enviar seu arquivo .ufopgrade, ou copie o código da grade em Perfil → Configurações → Armazenamento & Compartilhamento. Na Grade Horária, importe a grade de um amigo para comparar horários livres e aulas em comum."
    ),
    FaqAccordionItem(
        id = "official_calendar_2026_2027",
        question = "Como consultar o calendário acadêmico de 2026.2 e 2027.1?",
        answer = "Acesse Perfil → Configurações → Calendário Acadêmico. No topo da tela, alterne entre os semestres 2026.2, 2027.1 e 2027.2 (com referência às datas públicas divulgadas para o calendário acadêmico) e use os filtros de categoria ou a busca para encontrar qualquer marco."
    ),
    FaqAccordionItem(
        id = "security_pdfs",
        question = "Como funciona a segurança e a exclusão imediata de PDFs?",
        answer = "Todos os seus dados acadêmicos (disciplinas, faltas, notas, histórico e agenda) ficam salvos apenas no seu aparelho e protegidos com criptografia ligada à segurança do próprio Android. Ao importar Atestado de Matrícula, Histórico Escolar, Planos de Ensino ou Calendário em PDF, o aplicativo lê as informações na hora e apaga imediatamente qualquer arquivo temporário usado na leitura."
    ),
    FaqAccordionItem(
        id = "app_lock",
        question = "Como ativar o bloqueio por senha e digital?",
        answer = "Acesse Perfil → Configurações → Segurança & Privacidade e toque em \"Ativar bloqueio por senha\". Defina uma senha com pelo menos 4 caracteres e, se o seu celular possuir biometria cadastrada, habilite o desbloqueio por digital. Após 5 tentativas incorretas de senha, o app aplica um intervalo de proteção progressivo."
    ),
    FaqAccordionItem(
        id = "ead_syllabus",
        question = "Como funcionam os planos de ensino e disciplinas EAD?",
        answer = "Na aba Disciplinas, ao tocar em uma matéria você pode anexar o Plano de Ensino em PDF para extrair cronograma e avaliações automaticamente. Turmas identificadas como EAD ou remotas exibem a indicação EAD e não exibem textos genéricos ou separadores vazios quando não houver sala física ou docente informado."
    ),
    FaqAccordionItem(
        id = "personalization_a11y",
        question = "Onde ficam Personalização e Acessibilidade?",
        answer = "Ambas ficam dentro de Perfil → Configurações → Aparência, Segurança & Acessibilidade. Em Personalização você escolhe Modo Claro/Escuro e uma das 7 cores de destaque (o azul é reservado para clima e tags). Em Acessibilidade há perfil rápido para TalkBack, escala de fonte até 145%, alto contraste, texto em negrito, redução de animações e alvos de toque de 56dp."
    ),
    FaqAccordionItem(
        id = "encrypted_backup_logout",
        question = "Como funciona o backup protegido ao sair da conta?",
        answer = "Ao tocar em \"Sair da conta\" na Central do Perfil, o app oferece primeiro a criação de um arquivo de backup (.ufopbackup) protegido por uma senha escolhida por você. Depois, um segundo aviso confirma se você deseja apagar os dados deste aparelho e fechar o app. Você pode restaurar seu backup a qualquer momento em Configurações → Segurança & Privacidade."
    ),
    FaqAccordionItem(
        id = "legacy_curriculum",
        question = "Entrei na UFOP antes de 2026/1: como fica minha matriz?",
        answer = "O catálogo integrado traz a matriz vigente da UFOP (2026/2). Quem entrou na UFOP antes de 2026/1 pode ter equivalências ou cargas horárias específicas — o app sinaliza essa condição na Matriz Curricular e permite importar seu próprio Histórico Escolar (PDF) para calcular o CRE e acompanhar horas cursadas com precisão."
    )
)

@Composable
private fun ProfileUpdatesSubView(
    onOpenReleaseNotes: () -> Unit,
    onCheckForUpdates: () -> Unit
) {
    val minTouchTarget = campusMinTouchTarget()
    val appUpdateState by br.ufop.campus.core.update.AppUpdateManager.state.collectAsState()
    val statusText = when (val st = appUpdateState) {
        is br.ufop.campus.core.update.AppUpdateUiState.Checking ->
            "Consultando o GitHub Releases para verificar novas versões..."
        is br.ufop.campus.core.update.AppUpdateUiState.Available ->
            "Nova versão ${st.info.tagName} disponível para download e instalação."
        is br.ufop.campus.core.update.AppUpdateUiState.Downloading ->
            "Baixando atualização ${st.info.tagName} (${(st.progress * 100f).toInt()}%)..."
        is br.ufop.campus.core.update.AppUpdateUiState.Verifying ->
            "Verificando integridade SHA-256 de ${st.info.tagName}..."
        is br.ufop.campus.core.update.AppUpdateUiState.NeedsInstallPermission ->
            "Permissão de instalação necessária para concluir ${st.info.tagName}."
        is br.ufop.campus.core.update.AppUpdateUiState.ReadyToInstall ->
            "Pacote ${st.info.tagName} baixado e verificado. Pronto para instalar."
        is br.ufop.campus.core.update.AppUpdateUiState.Installing ->
            "Instalando ${st.info.tagName}..."
        is br.ufop.campus.core.update.AppUpdateUiState.UpToDate ->
            "Você já está na versão mais recente (v${CampusRepositoryHolder.CURRENT_APP_VERSION})."
        is br.ufop.campus.core.update.AppUpdateUiState.Error ->
            st.message
        else ->
            "Versão instalada: v${CampusRepositoryHolder.CURRENT_APP_VERSION} (build ${BuildConfig.VERSION_CODE})."
    }
    val actionLabel = when (val st = appUpdateState) {
        is br.ufop.campus.core.update.AppUpdateUiState.Checking -> "Verificando atualizações..."
        is br.ufop.campus.core.update.AppUpdateUiState.Available -> "Baixar ${st.info.tagName}"
        is br.ufop.campus.core.update.AppUpdateUiState.Downloading -> "Baixando ${st.info.tagName} (${(st.progress * 100f).toInt()}%)"
        is br.ufop.campus.core.update.AppUpdateUiState.Verifying -> "Verificando pacote..."
        is br.ufop.campus.core.update.AppUpdateUiState.NeedsInstallPermission -> "Autorizar instalação de ${st.info.tagName}"
        is br.ufop.campus.core.update.AppUpdateUiState.ReadyToInstall -> "Instalar ${st.info.tagName}"
        is br.ufop.campus.core.update.AppUpdateUiState.Installing -> "Instalando..."
        is br.ufop.campus.core.update.AppUpdateUiState.UpToDate -> "Verificar novamente (v${CampusRepositoryHolder.CURRENT_APP_VERSION})"
        else -> "Verificar atualizações (GitHub)"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Atualizações do Aplicativo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "v${CampusRepositoryHolder.CURRENT_APP_VERSION}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onCheckForUpdates,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onOpenReleaseNotes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ver resumo em janela modal",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Text(
            text = "HISTÓRICO DE PATCHES & NOTAS DE VERSÃO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .semantics { heading() }
        )

        CampusReleaseNotes.versions.forEach { entry ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = if (entry.isLatest) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (entry.isLatest) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${entry.version} · ${entry.title}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (entry.isLatest) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        ) {
                            Text(
                                text = entry.badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (entry.isLatest) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    entry.items.forEach { bullet ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(15.dp)
                            )
                            Text(
                                text = bullet,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileFaqSubView() {
    val minTouchTarget = campusMinTouchTarget()
    val expandedIds = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "AVISO DE INDEPENDÊNCIA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Projeto estudantil sem vínculo institucional",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Este é um aplicativo independente, desenvolvido por estudantes e sem qualquer vínculo oficial, endosso ou afiliação com a Universidade Federal de Ouro Preto (UFOP).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = "PERGUNTAS FREQUENTES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .semantics { heading() }
        )

        PROFILE_FAQ_ITEMS.forEach { item ->
            val isExpanded = expandedIds[item.id] == true
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTouchTarget)
                        .clickable { expandedIds[item.id] = !isExpanded }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = item.question,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isExpanded) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = if (isExpanded) "Recolher resposta" else "Expandir resposta",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    CampusAnimatedVisibility(visible = isExpanded) {
                        Text(
                            text = item.answer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogoutAccountFlowDialogs(
    step: LogoutStep,
    backupPassword: String,
    onBackupPasswordChange: (String) -> Unit,
    backupError: String?,
    isExportingBackup: Boolean,
    backupSavedPath: String?,
    backupShareUri: Uri?,
    backupShareFilename: String,
    isClearingAccount: Boolean,
    onDownloadBackupAndContinue: () -> Unit,
    onContinueWithoutBackup: () -> Unit,
    onShareBackup: () -> Unit,
    onConfirmFinalLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    val accessibleRed = accessibleTextColor(StatusAbsentRed, isDark, MaterialTheme.colorScheme.surface)
    val accessibleAmber = accessibleTextColor(StatusCancelledAmber, isDark, MaterialTheme.colorScheme.surface)

    when (step) {
        LogoutStep.NONE -> Unit

        LogoutStep.BACKUP_PROMPT -> {
            var showPassword by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = accessibleAmber
                    )
                },
                title = {
                    Text(
                        text = "Sair da conta · Backup antes de apagar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Ao sair da conta, todos os dados salvos neste aparelho serão apagados (disciplinas, faltas, histórico, agenda, bloqueio e preferências).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Se quiser restaurar sua conta depois, defina uma senha abaixo e baixe um backup protegido (.ufopbackup):",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedTextField(
                            value = backupPassword,
                            onValueChange = onBackupPasswordChange,
                            enabled = !isExportingBackup,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Senha do backup (mín. 4 caracteres)") },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showPassword = !showPassword },
                                    modifier = Modifier.size(minTouchTarget)
                                ) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (showPassword) "Ocultar senha" else "Mostrar senha"
                                    )
                                }
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                        if (backupError != null) {
                            Text(
                                text = backupError,
                                style = MaterialTheme.typography.bodySmall,
                                color = accessibleRed
                            )
                        }

                        Button(
                            onClick = onDownloadBackupAndContinue,
                            enabled = !isExportingBackup,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = minTouchTarget),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            if (isExportingBackup) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Baixar backup protegido (.ufopbackup)")
                        }

                        OutlinedButton(
                            onClick = onContinueWithoutBackup,
                            enabled = !isExportingBackup,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = minTouchTarget),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Text("Continuar sem backup")
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isExportingBackup,
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

        LogoutStep.FINAL_CONFIRM -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                        contentDescription = null,
                        tint = accessibleRed
                    )
                },
                title = {
                    Text(
                        text = "Confirmar saída da conta",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (backupSavedPath != null) {
                            StatusFeedbackBanner(
                                message = "Backup salvo com sucesso: $backupSavedPath",
                                isError = false
                            )
                            if (backupShareUri != null) {
                                TextButton(
                                    onClick = onShareBackup,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = minTouchTarget)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Compartilhar $backupShareFilename antes de sair")
                                }
                            }
                        } else {
                            Text(
                                text = "Backup dispensado: os dados locais não poderão ser recuperados após confirmar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = accessibleAmber,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = "Deseja mesmo sair da conta e fechar o aplicativo?",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onConfirmFinalLogout,
                        enabled = !isClearingAccount,
                        modifier = Modifier.heightIn(min = minTouchTarget),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusAbsentRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        if (isClearingAccount) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Sim, sair e fechar app")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isClearingAccount,
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
    }
}
