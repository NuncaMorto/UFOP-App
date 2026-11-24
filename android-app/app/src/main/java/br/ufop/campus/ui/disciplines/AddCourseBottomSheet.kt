package br.ufop.campus.ui.disciplines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.DepartmentColorOption
import br.ufop.campus.core.ffi.DisciplineSummaryCard
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.accessibleContentColorOn
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.parseHexColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCourseBottomSheet(
    semester: String,
    departments: List<DepartmentColorOption>,
    initialDepartmentId: String,
    onDismiss: () -> Unit,
    onSaveCourse: (
        departmentId: String,
        name: String,
        professor: String,
        workloadHours: Int,
        colorOverrideHex: String?,
        initialSlot: InitialCourseSlotInput?
    ) -> Result<DisciplineSummaryCard>
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = LocalCampusThemeController.current.isDark
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = if (a11y.largeTouchTargets) 56.dp else 48.dp

    val resolvedDepartments = remember(departments) {
        departments
            .ifEmpty { DisciplinesViewModel.sharedRepository?.listDepartments().orEmpty() }
            .ifEmpty { DEFAULT_48_UFOP_DEPARTMENTS }
    }

    val normalizedInitialDeptId = remember(initialDepartmentId, resolvedDepartments) {
        val clean = initialDepartmentId.trim().lowercase()
        val mapped = LEGACY_DEPARTMENT_ALIASES[clean] ?: clean
        resolvedDepartments.find { it.id.equals(mapped, ignoreCase = true) }?.id
            ?: resolvedDepartments.find { it.id == "decom" }?.id
            ?: resolvedDepartments.first().id
    }

    var name by remember { mutableStateOf("") }
    var professor by remember { mutableStateOf("") }
    var selectedDepartmentId by remember { mutableStateOf(normalizedInitialDeptId) }
    var showDepartmentPickerDialog by remember { mutableStateOf(false) }
    var selectedWorkloadHours by remember { mutableIntStateOf(60) }
    var isCustomWorkload by remember { mutableStateOf(false) }
    var customWorkloadText by remember { mutableStateOf("60") }
    var includeInitialSlot by remember { mutableStateOf(false) }
    var slotDraft by remember { mutableStateOf(ScheduleSlotDraft()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val selectedDepartment = resolvedDepartments.find { it.id == selectedDepartmentId }
        ?: resolvedDepartments.first()
    val accentColor = parseHexColor(selectedDepartment.colorHex)
    val accessibleAccent = remember(accentColor, isDark) {
        accessibleTextColor(accentColor, isDark)
    }
    val parsedCustomHours = customWorkloadText.toIntOrNull()
    val isCustomWorkloadValid = !isCustomWorkload || (parsedCustomHours != null && parsedCustomHours in 15..360)
    val effectiveWorkloadHours = if (isCustomWorkload) {
        (parsedCustomHours ?: selectedWorkloadHours).coerceIn(15, 360)
    } else {
        selectedWorkloadHours
    }
    val previewMaxAbsences = (effectiveWorkloadHours * 3) / 10
    val previewTotalClasses = ((effectiveWorkloadHours * 60) / 50).coerceAtLeast(1)

    if (showDepartmentPickerDialog) {
        UfopDepartmentPickerDialog(
            departments = resolvedDepartments,
            selectedDepartmentId = selectedDepartmentId,
            onSelectDepartment = { chosenId ->
                selectedDepartmentId = chosenId
                errorMessage = null
                showDepartmentPickerDialog = false
            },
            onDismiss = { showDepartmentPickerDialog = false }
        )
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
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AddCourseHeader(
                semester = semester,
                minTouchTarget = minTouchTarget,
                onDismiss = onDismiss
            )

            AddCourseIdentityFields(
                name = name,
                professor = professor,
                onNameChange = {
                    name = it
                    errorMessage = null
                },
                onProfessorChange = {
                    professor = it
                    errorMessage = null
                }
            )

            DepartmentSelectorCard(
                selectedDepartment = selectedDepartment,
                totalDepartments = resolvedDepartments.size,
                accentColor = accentColor,
                accessibleAccent = accessibleAccent,
                minTouchTarget = minTouchTarget,
                onOpenPicker = { showDepartmentPickerDialog = true }
            )

            AddCourseWorkloadSection(
                selectedDepartmentName = selectedDepartment.name,
                selectedWorkloadHours = selectedWorkloadHours,
                isCustomWorkload = isCustomWorkload,
                customWorkloadText = customWorkloadText,
                parsedCustomHours = parsedCustomHours,
                effectiveWorkloadHours = effectiveWorkloadHours,
                previewMaxAbsences = previewMaxAbsences,
                previewTotalClasses = previewTotalClasses,
                accentColor = accentColor,
                accessibleAccent = accessibleAccent,
                minTouchTarget = minTouchTarget,
                onSelectPresetWorkload = { hours ->
                    isCustomWorkload = false
                    selectedWorkloadHours = hours
                },
                onSelectCustomWorkload = {
                    isCustomWorkload = true
                    if (customWorkloadText.isBlank()) {
                        customWorkloadText = selectedWorkloadHours.toString()
                    }
                },
                onCustomWorkloadTextChange = { raw ->
                    customWorkloadText = raw.filter { it.isDigit() }.take(3)
                    errorMessage = null
                }
            )

            InitialScheduleSlotSection(
                includeInitialSlot = includeInitialSlot,
                slotDraft = slotDraft,
                accentColor = accentColor,
                onToggleInclude = { enabled ->
                    includeInitialSlot = enabled
                    errorMessage = null
                },
                onDraftChange = { updated ->
                    slotDraft = updated
                    errorMessage = null
                }
            )

            if (errorMessage != null) {
                InlineErrorBanner(message = errorMessage.orEmpty())
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
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
                        val initialSlot = if (includeInitialSlot) {
                            val cleanBuilding = slotDraft.building.trim().ifBlank { "ICEB (III)" }
                            val cleanRoom = slotDraft.room.trim().ifBlank {
                                if (isEadBuildingLabel(cleanBuilding)) "Online" else "Sala 101"
                            }
                            InitialCourseSlotInput(
                                dayOfWeek = slotDraft.dayOfWeek,
                                startTime = slotDraft.startTime.trim().ifBlank { "08:00" },
                                endTime = slotDraft.endTime.trim().ifBlank { "09:40" },
                                building = cleanBuilding,
                                room = cleanRoom,
                                shortLocation = slotDraft.shortLocationPreview
                            )
                        } else {
                            null
                        }

                        val result = onSaveCourse(
                            selectedDepartmentId,
                            name.trim(),
                            professor.trim(),
                            effectiveWorkloadHours,
                            null,
                            initialSlot
                        )
                        result.onFailure { throwable ->
                            errorMessage = throwable.message
                                ?: "Não foi possível criar a disciplina."
                        }
                    },
                    enabled = name.trim().isNotEmpty() && isCustomWorkloadValid,
                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                    shape = RoundedCornerShape(22.dp),
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
                    Text("Salvar disciplina")
                }
            }
        }
    }
}
