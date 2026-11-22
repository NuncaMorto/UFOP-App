package br.ufop.campus.ui.agenda

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ufop.campus.core.ffi.AttendanceStatus
import br.ufop.campus.core.ffi.ClassCheckInDetail
import br.ufop.campus.core.ffi.CourseScheduleSlotItem
import br.ufop.campus.ui.components.CampusTimePickerField
import br.ufop.campus.ui.components.isEndTimeAfterStart
import br.ufop.campus.ui.theme.DarkWeatherBadgeBg
import br.ufop.campus.ui.theme.DarkWeatherBadgeText
import br.ufop.campus.ui.theme.LightWeatherBadgeBg
import br.ufop.campus.ui.theme.LightWeatherBadgeText
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.parseHexColor
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassCheckInBottomSheet(
    detail: ClassCheckInDetail,
    slotItem: CourseScheduleSlotItem? = null,
    workloadHours: Int = 60,
    selectedIsoDate: String = "",
    editError: String? = null,
    onClearEditError: () -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (slotId: String, status: AttendanceStatus) -> Unit,
    onSaveEdit: (
        courseId: String,
        slotId: String,
        courseName: String,
        professor: String,
        departmentId: String,
        workloadHours: Int,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String
    ) -> Boolean
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedStatus by remember(detail.slotId, detail.status) {
        mutableStateOf(detail.status)
    }
    var isEditing by remember(detail.slotId) { mutableStateOf(false) }

    val parsedBuildingRoom = remember(detail.buildingAndRoom, slotItem) {
        if (slotItem != null) {
            slotItem.building to slotItem.room
        } else {
            val parts = detail.buildingAndRoom.split("·", limit = 2).map { it.trim() }
            when (parts.size) {
                2 -> parts[0] to parts[1]
                1 -> parts[0] to "Sala 101"
                else -> "Prédio A" to "Sala 101"
            }
        }
    }

    var editCourseName by remember(detail.courseId, detail.courseName) {
        mutableStateOf(detail.courseName)
    }
    var editProfessor by remember(detail.courseId, detail.professor) {
        mutableStateOf(detail.professor)
    }
    var editStartTime by remember(detail.slotId, detail.startTime) {
        mutableStateOf(detail.startTime)
    }
    var editEndTime by remember(detail.slotId, detail.endTime) {
        mutableStateOf(detail.endTime)
    }
    var editBuilding by remember(detail.slotId, parsedBuildingRoom) {
        mutableStateOf(parsedBuildingRoom.first)
    }
    var editRoom by remember(detail.slotId, parsedBuildingRoom) {
        mutableStateOf(parsedBuildingRoom.second)
    }

    val isDark = LocalCampusThemeController.current.isDark
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = if (a11y.largeTouchTargets) 56.dp else 48.dp
    val minFieldHeight = if (a11y.largeTouchTargets) 56.dp else 52.dp
    val activeAccentColor = parseHexColor(detail.selectedColorHex)
    val readableAccentText = accessibleTextColor(activeAccentColor, isDark)
    val readableErrorText = accessibleTextColor(StatusAbsentRed, isDark)
    val isEditTimeValid = isEndTimeAfterStart(editStartTime, editEndTime)
    val resolvedDayOfWeek = slotItem?.dayOfWeek
        ?: runCatching { LocalDate.parse(selectedIsoDate).dayOfWeek.value }.getOrDefault(1)

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
                .padding(start = 24.dp, end = 24.dp, top = 6.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Header: Departamento + Título da disciplina + Botão fechar (>= 48.dp unclipped)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(activeAccentColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = detail.departmentName,
                            style = MaterialTheme.typography.labelMedium,
                            color = readableAccentText,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isEditing) "Editar aula" else detail.courseName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    onClick = onDismiss,
                    modifier = Modifier.size(minTouchTarget),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Fechar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedContent(
                targetState = isEditing,
                transitionSpec = {
                    if (a11y.reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn() togetherWith fadeOut()
                    }
                },
                label = "ClassCheckInMode"
            ) { editingMode ->
                if (editingMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!editError.isNullOrBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = StatusAbsentRed.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, StatusAbsentRed.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = editError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = readableErrorText,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = editCourseName,
                            onValueChange = {
                                editCourseName = it
                                onClearEditError()
                            },
                            label = { Text("Nome da disciplina") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editProfessor,
                            onValueChange = {
                                editProfessor = it
                                onClearEditError()
                            },
                            label = { Text("Professor(a)") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CampusTimePickerField(
                                label = "Início",
                                time24h = editStartTime,
                                onTimeSelected = {
                                    editStartTime = it
                                    onClearEditError()
                                },
                                minHeight = minFieldHeight,
                                modifier = Modifier.weight(1f)
                            )
                            CampusTimePickerField(
                                label = "Término",
                                time24h = editEndTime,
                                onTimeSelected = {
                                    editEndTime = it
                                    onClearEditError()
                                },
                                isError = !isEditTimeValid,
                                minHeight = minFieldHeight,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (!isEditTimeValid) {
                            Text(
                                text = "O horário de término deve ser após o início.",
                                style = MaterialTheme.typography.bodySmall,
                                color = readableErrorText,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = editBuilding,
                                onValueChange = {
                                    editBuilding = it
                                    onClearEditError()
                                },
                                label = { Text("Prédio") },
                                placeholder = { Text("ICEB") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = editRoom,
                                onValueChange = {
                                    editRoom = it
                                    onClearEditError()
                                },
                                label = { Text("Sala") },
                                placeholder = { Text("Sala 204") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    editCourseName = detail.courseName
                                    editProfessor = detail.professor
                                    editStartTime = detail.startTime
                                    editEndTime = detail.endTime
                                    editBuilding = parsedBuildingRoom.first
                                    editRoom = parsedBuildingRoom.second
                                    onClearEditError()
                                    isEditing = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(minFieldHeight),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(
                                    text = "Cancelar",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Button(
                                onClick = {
                                    if (!isEndTimeAfterStart(editStartTime, editEndTime)) {
                                        return@Button
                                    }
                                    val ok = onSaveEdit(
                                        detail.courseId,
                                        detail.slotId,
                                        editCourseName,
                                        editProfessor,
                                        detail.departmentId,
                                        workloadHours,
                                        resolvedDayOfWeek,
                                        editStartTime,
                                        editEndTime,
                                        editBuilding,
                                        editRoom
                                    )
                                    if (ok) {
                                        isEditing = false
                                    }
                                },
                                enabled = editCourseName.isNotBlank() && isEditTimeValid,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(minFieldHeight),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = "Salvar",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                } else {
                    Column {
                        // Metadados da aula: Data, Horário + Clima, Localização e Docente
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.55f else 0.7f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = detail.formattedDate,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${detail.startTime}–${detail.endTime}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (isDark) DarkWeatherBadgeBg else LightWeatherBadgeBg
                                    ) {
                                        Text(
                                            text = "☀ ${detail.weatherTempC} °C · ${detail.weatherCondition}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isDark) DarkWeatherBadgeText else LightWeatherBadgeText,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                    thickness = 1.dp
                                )

                                Text(
                                    text = detail.buildingAndRoom,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (detail.professor.isNotBlank()) {
                                    Text(
                                        text = detail.professor,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Seção: Como fica esta aula?
                        Text(
                            text = "Como fica esta aula?",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val attendanceOptions = listOf(
                            AttendanceStatus.ATTENDING to "Vou à aula",
                            AttendanceStatus.ABSENT to "Vou faltar",
                            AttendanceStatus.CANCELLED to "Aula cancelada"
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            attendanceOptions.forEach { (statusOption, label) ->
                                val selected = selectedStatus == statusOption
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = minTouchTarget)
                                        .clip(RoundedCornerShape(16.dp))
                                        .selectable(
                                            selected = selected,
                                            onClick = { selectedStatus = statusOption },
                                            role = Role.RadioButton
                                        ),
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.45f else 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    border = BorderStroke(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = null,
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = MaterialTheme.colorScheme.primary,
                                                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Aula cancelada não conta como falta. Confirme em Concluído para atualizar o relatório em Disciplinas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Dois botões Expressivos lado a lado: Editar (esquerda) e Concluído (direita)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    onClearEditError()
                                    isEditing = true
                                },
                                enabled = detail.courseId.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(50)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Editar",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Button(
                                onClick = {
                                    onSave(detail.slotId, selectedStatus)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = "Concluído",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
