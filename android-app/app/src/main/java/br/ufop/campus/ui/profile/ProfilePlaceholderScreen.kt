package br.ufop.campus.ui.profile

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.ufop.campus.core.curriculum.UfopCurriculumCatalog
import br.ufop.campus.core.ffi.AcademicCalendarUpdatePackage
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.CampusEngineBridgeRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.core.ffi.TranscriptCourseEntry
import br.ufop.campus.core.security.CampusCryptoEngine
import br.ufop.campus.core.security.SecurePdfProcessor
import br.ufop.campus.core.storage.GradeExportStorage
import br.ufop.campus.ui.curriculum.CurriculumMatrixModal
import br.ufop.campus.ui.disciplines.DisciplinesViewModel
import br.ufop.campus.ui.theme.CampusFoldPosture
import br.ufop.campus.ui.theme.CampusWindowWidthClass
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusAdaptiveLayout
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onExitApp: () -> Unit = {},
    viewModel: ProfileViewModel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeController = LocalCampusThemeController.current
    val adaptive = LocalCampusAdaptiveLayout.current
    val accessibilityController = LocalCampusAccessibility.current

    val fallbackViewModel = if (viewModel == null) {
        remember(repository) { ProfileViewModel(repository = repository) }
    } else {
        null
    }
    DisposableEffect(fallbackViewModel) {
        onDispose {
            fallbackViewModel?.close()
        }
    }
    val activeViewModel = viewModel ?: fallbackViewModel!!
    val uiState by activeViewModel.uiState.collectAsState()

    LaunchedEffect(activeViewModel, context) {
        activeViewModel.ensureCatalogLoaded(context)
    }

    val mutationVersion = uiState.mutationVersion
    val studentProfile = uiState.studentProfile
    val activeSemester = uiState.activeSemester
    val profileSummary = uiState.profileSummary
    val currentDisciplines = uiState.currentDisciplines
    val creReport = uiState.creReport
    val exportFolderConfig = uiState.exportFolderConfig
    val dynamicGridResolution = uiState.dynamicGridResolution
    val activeDynamicMatrix = dynamicGridResolution.matrix
    val dynamicGridSummary = uiState.dynamicGridSummary
    val dynamicGridSubtitle = remember(dynamicGridResolution, dynamicGridSummary, studentProfile.courseName) {
        when {
            dynamicGridResolution.isCourseMatched ->
                "${activeDynamicMatrix.courseName} · ${activeDynamicMatrix.curriculumLabel} · ${dynamicGridSummary.completedHours}/${dynamicGridSummary.totalGridHours}h (${dynamicGridSummary.completionPercent}%)"
            studentProfile.courseName.isNotBlank() ->
                "Detalhamento pendente para ${studentProfile.courseName.trim()} · Toque para explorar cursos"
            else ->
                "${activeDynamicMatrix.courseName} · ${dynamicGridSummary.completedHours}/${dynamicGridSummary.totalGridHours}h (${dynamicGridSummary.completionPercent}%)"
        }
    }

    var avatarRevision by remember { mutableIntStateOf(0) }
    val avatarBitmap = rememberAvatarBitmap(studentProfile.avatarPath, avatarRevision)

    var activeDestination by rememberSaveable { mutableStateOf<ProfileDestination?>(null) }
    var activeSettingsSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    var showCurriculumMatrixModal by rememberSaveable { mutableStateOf(false) }
    var showEditProfileSheet by remember { mutableStateOf(false) }
    var showShareScheduleSheet by remember { mutableStateOf(false) }
    var editingTranscriptEntry by remember { mutableStateOf<TranscriptCourseEntry?>(null) }

    LaunchedEffect(activeViewModel, activeDestination, adaptive.useTwoPaneLayout) {
        activeViewModel.setVisibleDestination(activeDestination, adaptive.useTwoPaneLayout)
    }

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

    val pendingCalendarUpdate = uiState.pendingCalendarUpdate
    val isAdminUnlocked = uiState.isAdminUnlocked
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
                        )?.let { found ->
                            val earliestSem = resolveEarliestSemester(
                                repository.getAcademicCreReport().semesters.map { it.semester }
                            )
                            UfopCurriculumCatalog.resolveCourseForStudent(
                                baseCourse = found,
                                registrationNumber = updatedProfile.registrationNumber,
                                earliestTranscriptSemester = earliestSem
                            )
                        }
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

    val renderOverviewPane: @Composable () -> Unit = {
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
                iconTint = MaterialTheme.colorScheme.primary,
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

    val renderDestinationPane: @Composable (ProfileDestination) -> Unit = { destination ->
        when (destination) {
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

    if (adaptive.useTwoPaneLayout) {
        val rightDestination = activeDestination ?: ProfileDestination.METRICS_CRE
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 12.dp, top = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = adaptive.bottomContentPaddingDp.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                renderOverviewPane()
            }

            if (adaptive.foldPosture == CampusFoldPosture.BOOK_VERTICAL_HINGE) {
                Spacer(modifier = Modifier.width(adaptive.hingeWidthDp.coerceAtLeast(16).dp))
            }

            Column(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight()
            ) {
                if (rightDestination == ProfileDestination.SETTINGS && activeSettingsSection != null) {
                    SettingsSubViewHeader(
                        title = activeSettingsSection!!.title,
                        backLabel = "Voltar para Configurações",
                        onBack = { activeSettingsSection = null }
                    )
                } else {
                    SettingsSubViewHeader(
                        title = rightDestination.title,
                        backLabel = "Voltar para Métricas & CRE",
                        onBack = { activeDestination = ProfileDestination.METRICS_CRE }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(contentScrollState)
                        .padding(start = 12.dp, end = 20.dp, top = 14.dp)
                        .navigationBarsPadding()
                        .padding(bottom = adaptive.bottomContentPaddingDp.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    renderDestinationPane(rightDestination)
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
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
                    .then(
                        if (adaptive.widthClass == CampusWindowWidthClass.MEDIUM) {
                            Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxWidth()
                        } else {
                            Modifier.fillMaxWidth()
                        }
                    )
                    .verticalScroll(contentScrollState)
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = if (activeDestination == null) 20.dp else 14.dp
                    )
                    .navigationBarsPadding()
                    .padding(bottom = adaptive.bottomContentPaddingDp.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (val dest = activeDestination) {
                    null -> renderOverviewPane()
                    else -> renderDestinationPane(dest)
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
                val earliestSem = resolveEarliestSemester(
                    creReport.semesters.map { it.semester }
                )
                val resolvedMatched = matchedCourse?.let { found ->
                    UfopCurriculumCatalog.resolveCourseForStudent(
                        baseCourse = found,
                        registrationNumber = updated.registrationNumber,
                        earliestTranscriptSemester = earliestSem
                    )
                }
                if (resolvedMatched != null && shouldApplyCatalogRequiredHours(
                        repository = repository,
                        registrationNumber = updated.registrationNumber,
                        course = resolvedMatched
                    )
                ) {
                    repository.setTotalRequiredHours(resolvedMatched.chTotal)
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
