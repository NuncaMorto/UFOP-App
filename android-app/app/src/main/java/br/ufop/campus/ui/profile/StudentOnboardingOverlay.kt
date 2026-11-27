package br.ufop.campus.ui.profile

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.curriculum.UfopCourseCurriculum
import br.ufop.campus.core.curriculum.UfopCurriculumCatalog
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.core.ffi.SyllabusImportReport
import br.ufop.campus.core.ffi.normalizeUfopPortugueseText
import br.ufop.campus.core.security.CampusCryptoEngine
import br.ufop.campus.core.security.SecurePdfProcessor
import br.ufop.campus.core.storage.GradeExportStorage
import br.ufop.campus.ui.theme.CampusAccentColor
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentOnboardingOverlay(
    repository: CampusCoreRepository,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val systemDark = isSystemInDarkTheme()
    val themeController = LocalCampusThemeController.current
    val accessibilityController = LocalCampusAccessibility.current
    val a11y = accessibilityController.settings
    val buttonMinHeight = if (a11y.largeTouchTargets) 56.dp else 50.dp

    val initialProfile = remember { repository.getStudentProfile() }

    var currentStep by rememberSaveable { mutableStateOf(OnboardingStep.PROFILE_AND_ATTESTATION) }

    var fullName by rememberSaveable { mutableStateOf(initialProfile.fullName) }
    var courseName by rememberSaveable {
        mutableStateOf(
            initialProfile.courseName.trim().let {
                if (it.isBlank()) "" else normalizeUfopPortugueseText(it)
            }
        )
    }
    var registrationNumber by rememberSaveable { mutableStateOf(initialProfile.registrationNumber) }
    var selectedCampus by rememberSaveable {
        mutableStateOf(
            UFOP_CAMPUS_OPTIONS.find {
                it.equals(initialProfile.campusName, ignoreCase = true)
            } ?: "Ouro Preto"
        )
    }
    var avatarPath by rememberSaveable { mutableStateOf(initialProfile.avatarPath) }
    var avatarRevision by remember { mutableIntStateOf(0) }
    var photoSkippedHint by remember { mutableStateOf(false) }
    var importedCoursesCount by rememberSaveable { mutableIntStateOf(0) }

    var isImportingPdf by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorStatus by remember { mutableStateOf(false) }
    var identifiedCourse by remember { mutableStateOf<UfopCourseCurriculum?>(null) }

    // Estado de restauração de backup criptografado (.ufopbackup)
    var pendingBackupUri by remember { mutableStateOf<Uri?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordVisible by remember { mutableStateOf(false) }
    var isRestoringBackup by remember { mutableStateOf(false) }
    var backupErrorMessage by remember { mutableStateOf<String?>(null) }

    // Estado da etapa 2 (Planos de Ensino por disciplina)
    var syllabusRefreshTick by remember { mutableIntStateOf(0) }
    val syllabusReports = remember { mutableStateMapOf<String, SyllabusImportReport>() }
    var activeSyllabusCourseId by remember { mutableStateOf<String?>(null) }
    var uploadingSyllabusCourseId by remember { mutableStateOf<String?>(null) }

    val catalogCourses by produceState<List<UfopCourseCurriculum>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) { UfopCurriculumCatalog.getAllCourses(context) }
    }

    val avatarBitmap = rememberAvatarBitmap(avatarPath, avatarRevision)

    fun goToPreviousStep() {
        currentStep = when (currentStep) {
            OnboardingStep.PROFILE_AND_ATTESTATION -> OnboardingStep.PROFILE_AND_ATTESTATION
            OnboardingStep.SYLLABUS_UPLOAD -> OnboardingStep.PROFILE_AND_ATTESTATION
            OnboardingStep.PERSONALIZATION_AND_A11Y_QUESTION -> OnboardingStep.SYLLABUS_UPLOAD
            OnboardingStep.ACCESSIBILITY_SETUP -> OnboardingStep.PERSONALIZATION_AND_A11Y_QUESTION
        }
    }

    BackHandler(enabled = currentStep != OnboardingStep.PROFILE_AND_ATTESTATION) {
        goToPreviousStep()
    }

    fun saveDraftProfileAndAdvance() {
        val normalizedCourse = courseName.trim().let {
            if (it.isBlank()) "" else normalizeUfopPortugueseText(it)
        }
        val matched = identifiedCourse ?: UfopCurriculumCatalog.findCourse(
            courses = catalogCourses,
            courseNameOrCode = normalizedCourse,
            campusHint = selectedCampus
        )
        val trimmedReg = registrationNumber.trim()
        val earliestSemester = resolveEarliestSemester(
            repository.getAcademicCreReport().semesters.map { it.semester }
        )
        val resolvedCourse = matched?.let {
            UfopCurriculumCatalog.resolveCourseForStudent(
                baseCourse = it,
                registrationNumber = trimmedReg,
                earliestTranscriptSemester = earliestSemester
            )
        }
        if (resolvedCourse != null && shouldApplyCatalogRequiredHours(
                repository = repository,
                registrationNumber = trimmedReg,
                course = resolvedCourse
            )
        ) {
            repository.setTotalRequiredHours(resolvedCourse.chTotal)
        }
        val latest = repository.getStudentProfile()
        val draftProfile = latest.copy(
            fullName = fullName.trim(),
            registrationNumber = trimmedReg,
            courseName = normalizedCourse,
            campusName = selectedCampus,
            activeSemester = latest.activeSemester.ifBlank { "2026.2" },
            semesterStartDate = latest.semesterStartDate.ifBlank { "2026-08-24" },
            semesterEndDate = latest.semesterEndDate.ifBlank { "2026-12-23" },
            avatarPath = avatarPath ?: latest.avatarPath,
            onboardingCompleted = false
        )
        repository.saveStudentProfile(draftProfile)
        syllabusRefreshTick += 1
        currentStep = OnboardingStep.SYLLABUS_UPLOAD
    }

    fun finishOnboardingAndStartTour() {
        val latest = repository.getStudentProfile()
        repository.saveStudentProfile(latest.copy(onboardingCompleted = true))
        CampusRepositoryHolder.setLastSeenReleaseVersion(
            context = context,
            repository = repository,
            version = CampusRepositoryHolder.CURRENT_APP_VERSION
        )
        CampusRepositoryHolder.setGuidedTourCompleted(
            context = context,
            repository = repository,
            completed = false
        )
        onCompleted()
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val savedPath = copyUriToProfileAvatarFile(context, uri)
            if (savedPath != null) {
                avatarPath = savedPath
                avatarRevision += 1
                photoSkippedHint = false
                isErrorStatus = false
                statusMessage = null
            } else {
                isErrorStatus = true
                statusMessage = "Não foi possível carregar a imagem selecionada."
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isImportingPdf = true
        isErrorStatus = false
        statusMessage = null

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    runCatching {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("Não foi possível abrir o arquivo PDF.")
                        val importedCount = SecurePdfProcessor.withZeroedPdfBytes(bytes) { safeBytes ->
                            repository.importEnrollmentCertificatePdf(safeBytes, completeOnboarding = false)
                        }
                        if (importedCount <= 0) {
                            throw IllegalStateException(
                                "Nenhuma disciplina encontrada. Verifique se o arquivo é um Atestado de Matrícula da UFOP."
                            )
                        }
                        val importedProfile = repository.getStudentProfile()
                        val currentAvatar = avatarPath
                        if (!currentAvatar.isNullOrBlank() && importedProfile.avatarPath != currentAvatar) {
                            repository.saveStudentProfile(importedProfile.copy(avatarPath = currentAvatar))
                        }
                        val matchedCurriculum = UfopCurriculumCatalog.findCourse(
                            context = context,
                            courseNameOrCode = importedProfile.courseName,
                            campusHint = importedProfile.campusName
                        )?.let { found ->
                            val earliestSem = resolveEarliestSemester(
                                repository.getAcademicCreReport().semesters.map { it.semester }
                            )
                            UfopCurriculumCatalog.resolveCourseForStudent(
                                baseCourse = found,
                                registrationNumber = importedProfile.registrationNumber,
                                earliestTranscriptSemester = earliestSem
                            )
                        }
                        if (matchedCurriculum != null && shouldApplyCatalogRequiredHours(
                                repository = repository,
                                registrationNumber = importedProfile.registrationNumber,
                                course = matchedCurriculum
                            )
                        ) {
                            repository.setTotalRequiredHours(matchedCurriculum.chTotal)
                        }
                        importedCount to matchedCurriculum
                    }
                } finally {
                    runCatching { SecurePdfProcessor.purgeResidualPdfFiles(context) }
                }
            }
            isImportingPdf = false
            result.onSuccess { (count, matchedCurriculum) ->
                val imported = repository.getStudentProfile()
                val currentAvatar = avatarPath
                if (!currentAvatar.isNullOrBlank() && imported.avatarPath != currentAvatar) {
                    repository.saveStudentProfile(imported.copy(avatarPath = currentAvatar))
                }
                if (imported.fullName.isNotBlank()) fullName = imported.fullName
                if (imported.courseName.isNotBlank()) {
                    courseName = normalizeUfopPortugueseText(imported.courseName.trim())
                }
                if (imported.registrationNumber.isNotBlank()) {
                    registrationNumber = imported.registrationNumber
                }
                UFOP_CAMPUS_OPTIONS.find { it.equals(imported.campusName, ignoreCase = true) }
                    ?.let { selectedCampus = it }
                identifiedCourse = matchedCurriculum
                importedCoursesCount = count
                syllabusRefreshTick += 1
                isErrorStatus = false
                statusMessage = "Atestado importado ($count disciplinas, incluindo detecção automática de EAD)! Revise seus dados e toque em \"Continuar\"."
            }.onFailure { error ->
                isErrorStatus = true
                statusMessage = error.message ?: "Erro ao ler o Atestado de Matrícula (PDF)."
            }
        }
    }

    val backupPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingBackupUri = uri
            backupPassword = ""
            backupPasswordVisible = false
            backupErrorMessage = null
        }
    }

    val syllabusPdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val targetCourseId = activeSyllabusCourseId
        if (uri == null || targetCourseId.isNullOrBlank()) return@rememberLauncherForActivityResult
        uploadingSyllabusCourseId = targetCourseId

        scope.launch {
            val report = withContext(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@withContext SyllabusImportReport(
                            importedCount = 0,
                            matchedCourse = false,
                            errorMessage = "Não foi possível abrir o arquivo PDF selecionado."
                        )
                    runCatching {
                        SecurePdfProcessor.withZeroedPdfBytes(bytes) { safeBytes ->
                            repository.importCourseSyllabusDetailedPdf(targetCourseId, safeBytes)
                        }
                    }.getOrElse { err ->
                        SyllabusImportReport(
                            importedCount = 0,
                            matchedCourse = false,
                            errorMessage = err.message ?: "Erro ao ler o Plano de Ensino (PDF)."
                        )
                    }
                } finally {
                    runCatching { SecurePdfProcessor.purgeResidualPdfFiles(context) }
                }
            }
            syllabusReports[targetCourseId] = report
            uploadingSyllabusCourseId = null
            syllabusRefreshTick += 1
        }
    }

    val activeSemester = remember(currentStep, syllabusRefreshTick) {
        repository.getStudentProfile().activeSemester.ifBlank { "2026.2" }
    }
    val semesterDisciplines = remember(activeSemester, currentStep, syllabusRefreshTick) {
        if (currentStep == OnboardingStep.SYLLABUS_UPLOAD) {
            repository.listDisciplines(activeSemester, null)
        } else {
            emptyList()
        }
    }
    val disciplineDetailsById = remember(semesterDisciplines, syllabusRefreshTick) {
        semesterDisciplines.associate { card ->
            card.courseId to runCatching { repository.getDisciplineDetail(card.courseId) }.getOrNull()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Barra superior de progresso do Wizard (4 etapas)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (currentStep != OnboardingStep.PROFILE_AND_ATTESTATION) {
                                IconButton(
                                    onClick = { goToPreviousStep() },
                                    modifier = Modifier.semantics {
                                        contentDescription = "Voltar para etapa anterior"
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Etapa ${currentStep.stepNumber} de 4",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = currentStep.shortLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "INDEPENDENTE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { currentStep.stepNumber / 4f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Corpo rolável da etapa ativa
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = currentStep.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = currentStep.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                when (currentStep) {
                    OnboardingStep.PROFILE_AND_ATTESTATION -> {
                        OnboardingStep1ProfileAndAttestation(
                            fullName = fullName,
                            onFullNameChange = { fullName = it },
                            courseName = courseName,
                            onCourseNameChange = {
                                courseName = it
                                identifiedCourse = null
                            },
                            registrationNumber = registrationNumber,
                            onRegistrationNumberChange = { registrationNumber = it },
                            selectedCampus = selectedCampus,
                            onSelectCampus = { selectedCampus = it },
                            avatarBitmap = avatarBitmap,
                            photoSkippedHint = photoSkippedHint,
                            isImportingPdf = isImportingPdf,
                            statusMessage = statusMessage,
                            isErrorStatus = isErrorStatus,
                            catalogCourses = catalogCourses,
                            identifiedCourse = identifiedCourse,
                            onSelectCatalogCourse = { chosen ->
                                courseName = normalizeUfopPortugueseText(chosen.name)
                                selectedCampus = resolveUfopCampusOption(chosen.campus, selectedCampus)
                                identifiedCourse = chosen
                            },
                            onPickPhoto = { imagePickerLauncher.launch("image/*") },
                            onSkipPhoto = {
                                avatarPath = null
                                avatarRevision += 1
                                photoSkippedHint = true
                            },
                            onImportAttestationPdf = {
                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                            },
                            onRestoreBackup = {
                                backupPickerLauncher.launch(arrayOf("*/*"))
                            },
                            onContinue = { saveDraftProfileAndAdvance() },
                            buttonMinHeight = buttonMinHeight
                        )
                    }

                    OnboardingStep.SYLLABUS_UPLOAD -> {
                        OnboardingStep2SyllabusUpload(
                            disciplines = semesterDisciplines,
                            disciplineDetailsById = disciplineDetailsById,
                            syllabusReports = syllabusReports,
                            uploadingCourseId = uploadingSyllabusCourseId,
                            onUploadSyllabus = { course ->
                                activeSyllabusCourseId = course.courseId
                                syllabusPdfPickerLauncher.launch(arrayOf("application/pdf"))
                            },
                            onBack = { goToPreviousStep() },
                            onSkipOrContinue = {
                                currentStep = OnboardingStep.PERSONALIZATION_AND_A11Y_QUESTION
                            },
                            buttonMinHeight = buttonMinHeight
                        )
                    }

                    OnboardingStep.PERSONALIZATION_AND_A11Y_QUESTION -> {
                        OnboardingStep3PersonalizationAndA11y(
                            isDark = themeController.isDark,
                            selectedAccent = themeController.accentColor,
                            onSelectDarkMode = { wantDark ->
                                if (themeController.isDark != wantDark) {
                                    themeController.toggleTheme()
                                }
                            },
                            onSelectAccent = { accent ->
                                themeController.selectAccentColor(accent)
                            },
                            onConfigureAccessibility = {
                                currentStep = OnboardingStep.ACCESSIBILITY_SETUP
                            },
                            onFinishWithoutAccessibility = {
                                finishOnboardingAndStartTour()
                            },
                            onBack = { goToPreviousStep() },
                            buttonMinHeight = buttonMinHeight
                        )
                    }

                    OnboardingStep.ACCESSIBILITY_SETUP -> {
                        OnboardingStep4AccessibilitySetup(
                            onBack = { goToPreviousStep() },
                            onFinish = { finishOnboardingAndStartTour() },
                            buttonMinHeight = buttonMinHeight
                        )
                    }
                }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }

    // Diálogo de senha para restauração de backup criptografado (.ufopbackup)
    val backupUri = pendingBackupUri
    if (backupUri != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isRestoringBackup) {
                    pendingBackupUri = null
                    backupPassword = ""
                    backupErrorMessage = null
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.LockReset,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Restaurar backup criptografado",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Digite a senha usada ao criar o arquivo .ufopbackup para restaurar seu perfil, disciplinas, histórico e preferências.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = {
                            backupPassword = it
                            backupErrorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Senha do backup") },
                        placeholder = { Text("Digite a senha") },
                        singleLine = true,
                        enabled = !isRestoringBackup,
                        isError = backupErrorMessage != null,
                        visualTransformation = if (backupPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { backupPasswordVisible = !backupPasswordVisible }) {
                                Icon(
                                    imageVector = if (backupPasswordVisible) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = if (backupPasswordVisible) {
                                        "Ocultar senha"
                                    } else {
                                        "Mostrar senha"
                                    }
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(14.dp)
                    )
                    if (!backupErrorMessage.isNullOrBlank()) {
                        Text(
                            text = backupErrorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAbsentRed
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isRestoringBackup && backupPassword.isNotBlank(),
                    onClick = {
                        isRestoringBackup = true
                        backupErrorMessage = null
                        val passwordAttempt = backupPassword
                        scope.launch {
                            val restoreResult = withContext(Dispatchers.IO) {
                                runCatching {
                                    val rawPayload = GradeExportStorage.readPayloadFromUriSafely(
                                        context = context,
                                        uri = backupUri
                                    )
                                    val chars = passwordAttempt.toCharArray()
                                    val decryptedJson = try {
                                        CampusCryptoEngine.decryptBackupPayload(rawPayload, chars)
                                    } finally {
                                        chars.fill('\u0000')
                                    }
                                    val restored = repository.restoreFullAccountSnapshotJson(decryptedJson)
                                    if (!restored) {
                                        throw IllegalStateException(
                                            "Não foi possível aplicar o backup selecionado."
                                        )
                                    }
                                }
                            }
                            isRestoringBackup = false
                            restoreResult.onSuccess {
                                // Sincroniza preferências restauradas no CampusRepositoryHolder e tema/a11y ativos
                                val restoredDark = when (repository.getDarkThemePreference().trim().uppercase()) {
                                    "DARK" -> true
                                    "LIGHT" -> false
                                    else -> systemDark
                                }
                                CampusRepositoryHolder.saveDarkTheme(context, repository, restoredDark)
                                if (themeController.isDark != restoredDark) {
                                    themeController.toggleTheme()
                                }

                                val restoredAccent = CampusAccentColor.fromKey(
                                    repository.getAccentColorPreference()
                                )
                                CampusRepositoryHolder.saveAccentColor(
                                    context,
                                    repository,
                                    restoredAccent.storageKey
                                )
                                themeController.selectAccentColor(restoredAccent)

                                val restoredAttendanceStyle = CampusRepositoryHolder.saveAttendanceIndicatorStyle(
                                    context = context,
                                    repository = repository,
                                    style = repository.getAttendanceIndicatorStyle()
                                )
                                themeController.selectAttendanceIndicatorStyle(restoredAttendanceStyle)

                                val restoredA11y = repository.getAccessibilitySettings()
                                CampusRepositoryHolder.saveAccessibilitySettings(
                                    context,
                                    repository,
                                    restoredA11y
                                )
                                accessibilityController.updateSettings(restoredA11y)

                                CampusRepositoryHolder.setGuidedTourCompleted(
                                    context,
                                    repository,
                                    repository.isGuidedTourCompleted()
                                )
                                val restoredRelease = repository.getLastSeenReleaseVersion()
                                    .ifBlank { CampusRepositoryHolder.CURRENT_APP_VERSION }
                                CampusRepositoryHolder.setLastSeenReleaseVersion(
                                    context,
                                    repository,
                                    restoredRelease
                                )

                                val restoredProfile = repository.getStudentProfile()
                                if (!restoredProfile.onboardingCompleted) {
                                    repository.saveStudentProfile(
                                        restoredProfile.copy(onboardingCompleted = true)
                                    )
                                }

                                pendingBackupUri = null
                                backupPassword = ""
                                backupErrorMessage = null
                                onCompleted()
                            }.onFailure { err ->
                                backupErrorMessage = err.message
                                    ?: "Senha incorreta ou arquivo de backup inválido."
                            }
                        }
                    }
                ) {
                    if (isRestoringBackup) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restaurando...")
                    } else {
                        Text("Restaurar conta")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isRestoringBackup,
                    onClick = {
                        pendingBackupUri = null
                        backupPassword = ""
                        backupErrorMessage = null
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}
