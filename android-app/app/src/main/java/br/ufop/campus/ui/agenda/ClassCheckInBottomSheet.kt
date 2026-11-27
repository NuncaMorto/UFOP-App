package br.ufop.campus.ui.agenda

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ufop.campus.core.ffi.AttendanceStatus
import br.ufop.campus.core.ffi.ClassCheckInDetail
import br.ufop.campus.core.ffi.CourseScheduleSlotItem
import br.ufop.campus.core.ffi.extractDepartmentAcronym
import br.ufop.campus.core.map.MapMeetingFocusRequest
import br.ufop.campus.core.map.isEadMapLocation
import br.ufop.campus.ui.components.CampusTimePickerField
import br.ufop.campus.ui.components.isEndTimeAfterStart
import br.ufop.campus.ui.theme.CampusCardRadius
import br.ufop.campus.ui.theme.CampusPillRadius
import br.ufop.campus.ui.theme.CampusSheetRadius
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.StatusCancelledAmber
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.campusCardBorder
import br.ufop.campus.ui.theme.parseHexColor
import br.ufop.campus.ui.theme.tabularNums
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassCheckInBottomSheet(
    detail: ClassCheckInDetail,
    slotItem: CourseScheduleSlotItem? = null,
    workloadHours: Int = 60,
    selectedIsoDate: String = "",
    courseCode: String = "",
    courseSection: String = "",
    editError: String? = null,
    onClearEditError: () -> Unit = {},
    onDismiss: () -> Unit,
    onOpenLocationInMap: ((MapMeetingFocusRequest) -> Unit)? = null,
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
        shape = RoundedCornerShape(topStart = CampusSheetRadius, topEnd = CampusSheetRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 6.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Header: Departamento + Título da disciplina + Pílula discreta Editar + Botão fechar (>= 48.dp)
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
                            text = extractDepartmentAcronym(detail.departmentName),
                            style = MaterialTheme.typography.labelMedium,
                            color = readableAccentText,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
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

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isEditing && detail.courseId.isNotBlank()) {
                        Surface(
                            onClick = {
                                onClearEditError()
                                isEditing = true
                            },
                            modifier = Modifier.size(minTouchTarget),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = campusCardBorder()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Editar aula",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = onDismiss,
                        modifier = Modifier.size(minTouchTarget),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = campusCardBorder()
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
                        // Metadados da aula: Data, Horário tabular, Localização e Docente
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(CampusCardRadius),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = campusCardBorder()
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

                                Text(
                                    text = "${detail.startTime}–${detail.endTime}",
                                    style = MaterialTheme.typography.bodyMedium.tabularNums(),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                    thickness = 1.dp
                                )

                                val canOpenInMap = onOpenLocationInMap != null &&
                                    detail.buildingAndRoom.isNotBlank() &&
                                    !isEadMapLocation(detail.buildingAndRoom)
                                if (canOpenInMap) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = minTouchTarget)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(role = Role.Button) {
                                                val fallbackLoc = slotItem?.let {
                                                    listOf(it.building.trim(), it.room.trim())
                                                        .filter { s -> s.isNotEmpty() }
                                                        .joinToString(" · ")
                                                }?.ifBlank { detail.buildingAndRoom } ?: detail.buildingAndRoom
                                                val targetWeekday = slotItem?.dayOfWeek ?: resolvedDayOfWeek
                                                val targetStart = slotItem?.startTime?.ifBlank { detail.startTime }
                                                    ?: detail.startTime
                                                onDismiss()
                                                onOpenLocationInMap?.invoke(
                                                    MapMeetingFocusRequest(
                                                        courseCode = courseCode,
                                                        section = courseSection,
                                                        weekday = targetWeekday,
                                                        startTime = targetStart,
                                                        fallbackBuildingRaw = fallbackLoc
                                                    )
                                                )
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.LocationOn,
                                            contentDescription = null,
                                            tint = readableAccentText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = detail.buildingAndRoom,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "Ver no mapa",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = readableAccentText,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = readableAccentText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = detail.buildingAndRoom,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
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
                            Triple(AttendanceStatus.ATTENDING, "Vou à aula", Icons.Rounded.CheckCircle to StatusAttendingGreen),
                            Triple(AttendanceStatus.ABSENT, "Vou faltar", Icons.Rounded.Cancel to StatusAbsentRed),
                            Triple(AttendanceStatus.CANCELLED, "Aula cancelada", Icons.Rounded.EventBusy to StatusCancelledAmber)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            attendanceOptions.forEach { (statusOption, label, stylePair) ->
                                val (statusIcon, semanticColor) = stylePair
                                val selected = selectedStatus == statusOption
                                val readableSemantic = accessibleTextColor(semanticColor, isDark)
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = if (a11y.largeTouchTargets) 88.dp else 78.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .selectable(
                                            selected = selected,
                                            onClick = { selectedStatus = statusOption },
                                            role = Role.RadioButton
                                        ),
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (selected) {
                                        semanticColor.copy(alpha = if (isDark) 0.24f else 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    border = if (selected) {
                                        BorderStroke(1.5.dp, readableSemantic)
                                    } else {
                                        campusCardBorder()
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = statusIcon,
                                            contentDescription = null,
                                            tint = if (selected) readableSemantic else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) readableSemantic else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2
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

                        Button(
                            onClick = {
                                onSave(detail.slotId, selectedStatus)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(minFieldHeight),
                            shape = RoundedCornerShape(CampusPillRadius),
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
