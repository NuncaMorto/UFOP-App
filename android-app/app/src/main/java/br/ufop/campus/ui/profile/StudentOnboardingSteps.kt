package br.ufop.campus.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.curriculum.UfopCourseCurriculum
import br.ufop.campus.core.ffi.DisciplineDetail
import br.ufop.campus.core.ffi.DisciplineSummaryCard
import br.ufop.campus.core.ffi.SyllabusImportReport
import br.ufop.campus.ui.theme.CampusAccentColor
import br.ufop.campus.ui.theme.DepartmentMathBlue
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.StatusCancelledAmber
import br.ufop.campus.ui.theme.accessibleContentColorOn
import br.ufop.campus.ui.theme.parseHexColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OnboardingStep1ProfileAndAttestation(
    fullName: String,
    onFullNameChange: (String) -> Unit,
    courseName: String,
    onCourseNameChange: (String) -> Unit,
    registrationNumber: String,
    onRegistrationNumberChange: (String) -> Unit,
    selectedCampus: String,
    onSelectCampus: (String) -> Unit,
    avatarBitmap: ImageBitmap?,
    photoSkippedHint: Boolean,
    isImportingPdf: Boolean,
    statusMessage: String?,
    isErrorStatus: Boolean,
    catalogCourses: List<UfopCourseCurriculum>,
    identifiedCourse: UfopCourseCurriculum?,
    onSelectCatalogCourse: (UfopCourseCurriculum) -> Unit,
    onPickPhoto: () -> Unit,
    onSkipPhoto: () -> Unit,
    onImportAttestationPdf: () -> Unit,
    onRestoreBackup: () -> Unit,
    onContinue: () -> Unit,
    buttonMinHeight: androidx.compose.ui.unit.Dp
) {
    // Seletor circular de foto de perfil (96.dp Circle) + botão "Pular foto"
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !isImportingPdf, onClick = onPickPhoto),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            ) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap,
                        contentDescription = "Foto de perfil selecionada",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        val initials = extractInitials(fullName)
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
                                contentDescription = "Selecionar foto",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.size(30.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.background)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.AddAPhoto,
                        contentDescription = "Escolher foto",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onPickPhoto,
                enabled = !isImportingPdf,
                shape = RoundedCornerShape(22.dp)
            ) {
                Text(
                    text = if (avatarBitmap != null) "Trocar foto" else "Escolher foto",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false
                )
            }

            TextButton(
                onClick = onSkipPhoto,
                enabled = !isImportingPdf,
                shape = RoundedCornerShape(22.dp)
            ) {
                Text(
                    text = "Pular foto",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        if (photoSkippedHint && avatarBitmap == null) {
            Text(
                text = "Foto opcional pulada · você pode adicionar depois em Perfil.",
                style = MaterialTheme.typography.labelSmall,
                color = StatusAttendingGreen
            )
        }
    }

    // Atalho Rápido — Importar Atestado de Matrícula (PDF)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "ATALHO · ATESTADO DE MATRÍCULA",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Compatível com o Atestado de Matrícula em PDF (ex.: Minha UFOP) para preencher nome, matrícula, curso, disciplinas, prédios e grade horária. Disciplinas EAD são detectadas automaticamente e nenhum arquivo PDF fica salvo no aparelho.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = onImportAttestationPdf,
                enabled = !isImportingPdf,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = buttonMinHeight),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isImportingPdf) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lendo atestado em PDF...",
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
                        text = "Importar Atestado de Matrícula (PDF)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Card discreto — Restaurar backup criptografado (.ufopbackup)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isImportingPdf, onClick = onRestoreBackup)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.LockReset,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Restaurar backup criptografado (.ufopbackup)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Já tinha uma conta? Recupere todos os seus dados com sua senha.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (statusMessage != null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = if (isErrorStatus) {
                StatusAbsentRed.copy(alpha = 0.12f)
            } else {
                StatusAttendingGreen.copy(alpha = 0.12f)
            }
        ) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (isErrorStatus) StatusAbsentRed else StatusAttendingGreen,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }

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
            text = "ou preencha manualmente",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }

    // Campos manuais de cadastro
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = fullName,
                onValueChange = onFullNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nome completo") },
                placeholder = { Text("Ex.: Ana Carolina Silva") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )

            OutlinedTextField(
                value = courseName,
                onValueChange = onCourseNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Curso") },
                placeholder = { Text("Ex.: Engenharia de Controle e Automação") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.School,
                        contentDescription = null
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )

            UfopCourseSuggestionAndPickerBlock(
                courseName = courseName,
                campusHint = selectedCampus,
                catalogCourses = catalogCourses,
                identifiedCourse = identifiedCourse,
                onSelectCourse = onSelectCatalogCourse
            )

            OutlinedTextField(
                value = registrationNumber,
                onValueChange = onRegistrationNumberChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Número de matrícula") },
                placeholder = { Text("Ex.: 24.2.1165") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Badge,
                        contentDescription = null
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Campus universitário",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                UFOP_CAMPUS_OPTIONS.forEach { campus ->
                    val isSelected = selectedCampus == campus
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectCampus(campus) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            null
                        },
                        label = {
                            Text(
                                text = campus,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        shape = RoundedCornerShape(22.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }

    // CTA primário -> avança para Tela 2 (salva rascunho com onboardingCompleted = false)
    Button(
        onClick = onContinue,
        enabled = !isImportingPdf,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = buttonMinHeight),
        shape = RoundedCornerShape(22.dp)
    ) {
        Text(
            text = "Continuar",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
    }

    Text(
        text = "Aplicativo independente para estudantes, sem vínculo oficial ou afiliação com a Universidade Federal de Ouro Preto (UFOP).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}
@Composable
internal fun OnboardingStep2SyllabusUpload(
    disciplines: List<DisciplineSummaryCard>,
    disciplineDetailsById: Map<String, DisciplineDetail?>,
    syllabusReports: Map<String, SyllabusImportReport>,
    uploadingCourseId: String?,
    onUploadSyllabus: (DisciplineSummaryCard) -> Unit,
    onBack: () -> Unit,
    onSkipOrContinue: () -> Unit,
    buttonMinHeight: androidx.compose.ui.unit.Dp
) {
    if (disciplines.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    text = "Sem disciplinas importadas ainda",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Como você optou pelo modo manual, poderá adicionar suas disciplinas na aba Disciplinas — com campo de professor em branco para preenchimento livre ou envio do Plano de Ensino (PDF) depois.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Text(
                text = "Encontramos ${disciplines.size} disciplinas do seu atestado. Enviar os Planos de Ensino agora é opcional — você pode fazer isso depois dentro de cada disciplina.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            disciplines.forEach { course ->
                val detail = disciplineDetailsById[course.courseId]
                val report = syllabusReports[course.courseId]
                val isUploading = uploadingCourseId == course.courseId
                val professorName = detail?.professor?.trim().orEmpty()
                val shortLoc = detail?.shortLocation?.trim().orEmpty()
                val isEad = shortLoc.contains("EAD", ignoreCase = true) ||
                    detail?.buildingAndRoom?.contains("EAD", ignoreCase = true) == true

                val (statusLabel, statusColor) = when {
                    report == null -> "Opcional · Pendente" to MaterialTheme.colorScheme.onSurfaceVariant
                    !report.matchedCourse -> "PDF de outra disciplina" to StatusAbsentRed
                    report.warningMessage != null -> "Aviso parcial" to StatusCancelledAmber
                    else -> "Plano completo importado" to StatusAttendingGreen
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        when {
                            report == null -> MaterialTheme.colorScheme.outlineVariant
                            !report.matchedCourse -> StatusAbsentRed.copy(alpha = 0.55f)
                            report.warningMessage != null -> StatusCancelledAmber.copy(alpha = 0.6f)
                            else -> StatusAttendingGreen.copy(alpha = 0.55f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(parseHexColor(course.colorHex))
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (course.code.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = course.code,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (isEad) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = DepartmentMathBlue.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = "EAD",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = DepartmentMathBlue,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = course.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = if (professorName.isNotBlank()) {
                                        "Docente: $professorName"
                                    } else {
                                        "Professor em branco (preencha ou envie plano)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = statusColor.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }

                            FilledTonalButton(
                                onClick = { onUploadSyllabus(course) },
                                enabled = uploadingCourseId == null,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                if (isUploading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(15.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Lendo...",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.UploadFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Enviar Plano (PDF)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (report != null) {
                            SyllabusImportFeedbackBanner(report = report)
                        }
                    }
                }
            }
        }
    }

    // Ações de rodapé da Tela 2
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                enabled = uploadingCourseId == null,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = buttonMinHeight),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Voltar",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = onSkipOrContinue,
                enabled = uploadingCourseId == null,
                modifier = Modifier
                    .weight(1.3f)
                    .defaultMinSize(minHeight = buttonMinHeight),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "Continuar",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        TextButton(
            onClick = onSkipOrContinue,
            enabled = uploadingCourseId == null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Pular esta etapa",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
@Composable
private fun SyllabusImportFeedbackBanner(
    report: SyllabusImportReport
) {
    when {
        !report.matchedCourse -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = StatusAbsentRed.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, StatusAbsentRed.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = StatusAbsentRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = report.errorMessage?.takeIf { it.isNotBlank() }
                            ?: "Este plano de ensino não parece pertencer a esta disciplina. Confira o PDF enviado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAbsentRed
                    )
                }
            }
        }

        report.warningMessage != null -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = StatusCancelledAmber.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, StatusCancelledAmber.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = StatusCancelledAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (report.extractedProfessor.isNotBlank()) {
                            Text(
                                text = "Docente identificado: ${report.extractedProfessor}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusCancelledAmber
                            )
                        }
                        Text(
                            text = report.warningMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        else -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = StatusAttendingGreen.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, StatusAttendingGreen.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = StatusAttendingGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    val details = buildString {
                        if (report.extractedProfessor.isNotBlank()) {
                            append("Docente: ${report.extractedProfessor} · ")
                        }
                        append(
                            if (report.importedCount == 1) {
                                "1 avaliação importada"
                            } else {
                                "${report.importedCount} avaliações importadas"
                            }
                        )
                    }
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusAttendingGreen
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OnboardingStep3PersonalizationAndA11y(
    isDark: Boolean,
    selectedAccent: CampusAccentColor,
    onSelectDarkMode: (Boolean) -> Unit,
    onSelectAccent: (CampusAccentColor) -> Unit,
    onConfigureAccessibility: () -> Unit,
    onFinishWithoutAccessibility: () -> Unit,
    onBack: () -> Unit,
    buttonMinHeight: androidx.compose.ui.unit.Dp
) {
    // Card 1: Modo Claro / Escuro
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Aparência do aplicativo",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemeModeChoiceCard(
                    title = "Modo Claro",
                    icon = Icons.Outlined.WbSunny,
                    selected = !isDark,
                    onClick = { onSelectDarkMode(false) },
                    modifier = Modifier.weight(1f)
                )
                ThemeModeChoiceCard(
                    title = "Modo Escuro",
                    icon = Icons.Outlined.DarkMode,
                    selected = isDark,
                    onClick = { onSelectDarkMode(true) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Card 2: Paleta com todas as 9 cores de destaque
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Cor de destaque",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CampusAccentColor.entries.forEach { accent ->
                    val isSelected = accent == selectedAccent
                    val swatch = parseHexColor(accent.previewHex)
                    Surface(
                        onClick = { onSelectAccent(accent) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
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
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(
                                        width = 1.dp,
                                        color = Color.Black.copy(alpha = 0.18f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selecionada",
                                        tint = accessibleContentColorOn(swatch),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Text(
                                text = accent.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Card 3: Pergunta explícita de Acessibilidade
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
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
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccessibilityNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "Você precisa configurar opções de acessibilidade?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
            }

            Text(
                text = "Disponibilizamos otimização para leitor de tela (TalkBack), fonte ampliada, alto contraste WCAG, texto em negrito, redução de movimento e áreas de toque ampliadas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FilledTonalButton(
                onClick = onConfigureAccessibility,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = buttonMinHeight),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccessibilityNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sim, configurar acessibilidade",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onFinishWithoutAccessibility,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = buttonMinHeight),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Não, finalizar e iniciar tour",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    TextButton(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Voltar para Planos de Ensino",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
@Composable
private fun ThemeModeChoiceCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}
@Composable
internal fun OnboardingStep4AccessibilitySetup(
    onBack: () -> Unit,
    onFinish: () -> Unit,
    buttonMinHeight: androidx.compose.ui.unit.Dp
) {
    val accessibilityController = LocalCampusAccessibility.current

    AccessibilitySettingsPanel(
        settings = accessibilityController.settings,
        onUpdateSettings = accessibilityController.updateSettings,
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .weight(0.9f)
                .defaultMinSize(minHeight = buttonMinHeight),
            shape = RoundedCornerShape(20.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Voltar",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Button(
            onClick = onFinish,
            modifier = Modifier
                .weight(1.4f)
                .defaultMinSize(minHeight = buttonMinHeight),
            shape = RoundedCornerShape(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Concluir e iniciar tour",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
