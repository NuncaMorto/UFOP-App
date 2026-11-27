package br.ufop.campus.ui.profile

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SettingsAccessibility
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.curriculum.UfopCourseCurriculum
import br.ufop.campus.core.curriculum.UfopCurriculumCatalog
import br.ufop.campus.core.ffi.AcademicCalendarUpdatePackage
import br.ufop.campus.core.ffi.AcademicProfileSummary
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.StudentProfile
import br.ufop.campus.core.ffi.normalizeUfopPortugueseText
import br.ufop.campus.core.security.AppLockManager
import br.ufop.campus.core.storage.GradeExportStorage
import br.ufop.campus.ui.theme.CampusAccentColor
import br.ufop.campus.ui.theme.DepartmentMathBlue
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.accessibleTextColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun ProfilePersonalizationSubView(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    selectedAccent: CampusAccentColor,
    onSelectAccent: (CampusAccentColor) -> Unit,
    attendanceIndicatorStyle: String,
    onSelectAttendanceIndicatorStyle: (String) -> Unit
) {
    val minTouchTarget = campusMinTouchTarget()

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
                    "Ativado · Escada tonal escura (${selectedAccent.displayName})"
                } else {
                    "Desativado · Escada tonal clara (${selectedAccent.displayName})"
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

        SettingsCategoryGroup(title = "Indicador de presença na Agenda") {
            val isBadgeSelected = attendanceIndicatorStyle != "CARD_BORDER"
            SettingsMenuRow(
                icon = Icons.AutoMirrored.Outlined.Label,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Badge no card (padrão)",
                subtitle = "Exibe a pílula de status ao lado do horário (Vou à aula, Cancelada, Vou faltar)",
                isSelected = isBadgeSelected,
                onClick = { onSelectAttendanceIndicatorStyle("BADGE") },
                trailing = {
                    RadioButton(
                        selected = isBadgeSelected,
                        onClick = null
                    )
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            )
            SettingsMenuRow(
                icon = Icons.Outlined.CropSquare,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Borda colorida no card",
                subtitle = "Remove a pílula interna e aplica borda contínua na cor semântica do status",
                isSelected = !isBadgeSelected,
                onClick = { onSelectAttendanceIndicatorStyle("CARD_BORDER") },
                trailing = {
                    RadioButton(
                        selected = !isBadgeSelected,
                        onClick = null
                    )
                }
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "COR DE DESTAQUE E PRÉ-VISUALIZAÇÃO",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .semantics { heading() }
            )
            CampusAccentPalettePreviewAndGrid(
                isDark = isDarkTheme,
                selectedAccent = selectedAccent,
                onSelectAccent = onSelectAccent
            )
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileSettingsTab(
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
    val earliestTranscriptSemester = remember(repository, mutationVersion) {
        resolveEarliestSemester(repository.getAcademicCreReport().semesters.map { it.semester })
    }
    val matchedCourse = remember(
        catalogCourses,
        studentProfile.courseName,
        studentProfile.campusName,
        studentProfile.registrationNumber,
        earliestTranscriptSemester
    ) {
        if (studentProfile.courseName.isBlank()) {
            null
        } else {
            UfopCurriculumCatalog.findCourse(
                catalogCourses,
                studentProfile.courseName,
                studentProfile.campusName
            )?.let { found ->
                UfopCurriculumCatalog.resolveCourseForStudent(
                    baseCourse = found,
                    registrationNumber = studentProfile.registrationNumber,
                    earliestTranscriptSemester = earliestTranscriptSemester
                )
            }
        }
    }

    val matrixSubtitle = remember(matchedCourse, studentProfile.courseName) {
        when {
            matchedCourse != null && matchedCourse.subjects.isNotEmpty() -> {
                val versionSuffix = matchedCourse.curriculumVersion.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                "${matchedCourse.code}$versionSuffix · ${matchedCourse.subjects.size} componentes · ${formatCurriculumHours(matchedCourse.chTotal)}h"
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
                    iconTint = MaterialTheme.colorScheme.primary,
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
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Matriz Curricular (2026/2)",
                    subtitle = matrixSubtitle,
                    onClick = onOpenCurriculumMatrix
                )
            }

            SettingsCategoryGroup(title = "Calendário de Referência") {
                SettingsMenuRow(
                    icon = Icons.Outlined.CalendarMonth,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Calendário Acadêmico",
                    subtitle = "2026.2, 2027.1 e 2027.2 · ${profileSummary.calendarEvents.size} marcos ativos",
                    onClick = { onSelectSection(SettingsSection.ACADEMIC_CALENDAR) }
                )
            }

            SettingsCategoryGroup(title = "Dados & Suporte") {
                SettingsMenuRow(
                    icon = Icons.Outlined.Storage,
                    iconTint = MaterialTheme.colorScheme.primary,
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
                    iconTint = MaterialTheme.colorScheme.primary,
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
                        onSelectAccent = { themeController.selectAccentColor(it) },
                        attendanceIndicatorStyle = themeController.attendanceIndicatorStyle,
                        onSelectAttendanceIndicatorStyle = { themeController.selectAttendanceIndicatorStyle(it) }
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
