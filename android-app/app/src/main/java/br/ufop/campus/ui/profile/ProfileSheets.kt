package br.ufop.campus.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.curriculum.UfopCourseCurriculum
import br.ufop.campus.core.curriculum.UfopCurriculumCatalog
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.StudentProfile
import br.ufop.campus.core.ffi.normalizeUfopPortugueseText
import br.ufop.campus.core.storage.GradeExportStorage
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.campusAccentTextColor
import br.ufop.campus.ui.theme.campusCardBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EditStudentProfileBottomSheet(
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

            var campusDropdownExpanded by remember { mutableStateOf(false) }
            val campusAccentText = campusAccentTextColor()
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    onClick = { campusDropdownExpanded = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = campusCardBorder()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = selectedCampus.ifBlank { UFOP_CAMPUS_OPTIONS.first() },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (campusDropdownExpanded) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = "Selecionar campus universitário",
                            tint = campusAccentText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = campusDropdownExpanded,
                    onDismissRequest = { campusDropdownExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    UFOP_CAMPUS_OPTIONS.forEach { campus ->
                        val isSelected = selectedCampus == campus
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = campus,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) {
                                        campusAccentText
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            },
                            trailingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selecionado",
                                        tint = campusAccentText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                null
                            },
                            onClick = {
                                selectedCampus = campus
                                campusDropdownExpanded = false
                            }
                        )
                    }
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleJsonShareBottomSheet(
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
