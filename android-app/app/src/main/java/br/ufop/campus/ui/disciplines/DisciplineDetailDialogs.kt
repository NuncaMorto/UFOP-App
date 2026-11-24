package br.ufop.campus.ui.disciplines

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.ui.components.CampusDatePickerField
import br.ufop.campus.ui.components.CampusTimePickerField
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.accessibleContentColorOn

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DisciplineEditCourseDialog(
    name: String,
    professor: String,
    workloadText: String,
    parsedWorkload: Int?,
    accentColor: Color,
    accessibleAccent: Color,
    chipMinHeight: Dp,
    minTouchTarget: Dp,
    onNameChange: (String) -> Unit,
    onProfessorChange: (String) -> Unit,
    onWorkloadTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Editar disciplina",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome da disciplina") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = professor,
                    onValueChange = onProfessorChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Professor(a)") },
                    placeholder = { Text("Opcional · deixe em branco para preencher depois") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                Text(
                    text = "Carga horária (h)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EDIT_WORKLOAD_PRESETS.forEach { preset ->
                        FilterChip(
                            selected = parsedWorkload == preset,
                            onClick = { onWorkloadTextChange(preset.toString()) },
                            modifier = Modifier.heightIn(min = chipMinHeight),
                            label = { Text("${preset}h") },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.18f),
                                selectedLabelColor = accessibleAccent
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = workloadText,
                    onValueChange = { raw ->
                        onWorkloadTextChange(raw.filter { it.isDigit() }.take(3))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Carga horária personalizada") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = minTouchTarget)
            ) {
                Text(
                    text = "Cancelar",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = name.isNotBlank() && parsedWorkload != null,
                modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = accessibleContentColorOn(accentColor)
                )
            ) {
                Text("Salvar")
            }
        }
    )
}

@Composable
internal fun DisciplineEditAbsencesDialog(
    courseName: String,
    currentAbsences: Int,
    maxAbsences: Int,
    absencesInputDraft: String,
    accentColor: Color,
    accessibleAccent: Color,
    minTouchTarget: Dp,
    onDraftChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit
) {
    val parsedDraft = absencesInputDraft.toIntOrNull()?.coerceAtLeast(0)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Editar total de faltas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "$courseName · limite de $maxAbsences faltas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            val next = ((parsedDraft ?: currentAbsences) - 1).coerceAtLeast(0)
                            onDraftChange(next.toString())
                        },
                        enabled = (parsedDraft ?: currentAbsences) > 0,
                        modifier = Modifier.size(minTouchTarget)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Diminuir falta"
                        )
                    }

                    OutlinedTextField(
                        value = absencesInputDraft,
                        onValueChange = { raw ->
                            onDraftChange(raw.filter { it.isDigit() }.take(3))
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Total de faltas") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp)
                    )

                    FilledTonalIconButton(
                        onClick = {
                            val next = ((parsedDraft ?: currentAbsences) + 1).coerceAtMost(999)
                            onDraftChange(next.toString())
                        },
                        modifier = Modifier.size(minTouchTarget),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = accentColor.copy(alpha = 0.16f),
                            contentColor = accessibleAccent
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Adicionar falta"
                        )
                    }
                }

                Text(
                    text = "O ajuste manual é somado ou descontado sem apagar seu histórico de marcações na Agenda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = minTouchTarget)
            ) {
                Text(
                    text = "Cancelar",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(parsedDraft ?: currentAbsences) },
                enabled = parsedDraft != null,
                modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = accessibleContentColorOn(accentColor)
                )
            ) {
                Text("Aplicar")
            }
        }
    )
}

@Composable
internal fun DisciplineAddAssessmentFormCard(
    courseName: String,
    newTitle: String,
    newKind: String,
    newDateText: String,
    newTimeText: String,
    accentColor: Color,
    accessibleAccent: Color,
    secondaryControlHeight: Dp,
    secondaryChipHeight: Dp,
    minTouchTarget: Dp,
    onTitleChange: (String) -> Unit,
    onKindChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
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
                text = "Novo prazo em $courseName",
                style = MaterialTheme.typography.labelLarge,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = newTitle,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Título da avaliação ou entrega") },
                placeholder = { Text("Ex.: Lista 3 de Exercícios") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Prova", "Trabalho", "Seminário").forEach { kindOption ->
                    val selected = newKind == kindOption
                    FilterChip(
                        selected = selected,
                        onClick = { onKindChange(kindOption) },
                        modifier = Modifier.heightIn(min = secondaryChipHeight),
                        label = { Text(kindOption) },
                        shape = RoundedCornerShape(22.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor.copy(alpha = 0.18f),
                            selectedLabelColor = accessibleAccent
                        )
                    )
                }
            }

            CampusDatePickerField(
                label = "Data da entrega/avaliação",
                isoDate = newDateText,
                onIsoDateSelected = onDateChange,
                minHeight = minTouchTarget
            )

            CampusTimePickerField(
                label = "Horário (opcional)",
                time24h = newTimeText,
                onTimeSelected = onTimeChange,
                minHeight = minTouchTarget
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
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
                    onClick = onSave,
                    enabled = newTitle.isNotBlank(),
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
                    Text("Salvar prazo")
                }
            }
        }
    }
}

@Composable
internal fun DisciplineDeleteCourseSection(
    courseName: String,
    confirmDeleteCourse: Boolean,
    accessibleDeleteRed: Color,
    secondaryControlHeight: Dp,
    minTouchTarget: Dp,
    onRequestConfirm: () -> Unit,
    onCancelConfirm: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    if (!confirmDeleteCourse) {
        OutlinedButton(
            onClick = onRequestConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = secondaryControlHeight),
            shape = RoundedCornerShape(22.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            border = BorderStroke(1.dp, StatusAbsentRed.copy(alpha = 0.50f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = accessibleDeleteRed
            )
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Remover disciplina",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = StatusAbsentRed.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, StatusAbsentRed.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Remover $courseName?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = StatusAbsentRed
                )
                Text(
                    text = "Esta ação exclui a disciplina, seus horários semanais e avaliações vinculadas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onCancelConfirm,
                        modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirmDelete,
                        modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusAbsentRed,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Confirmar remoção")
                    }
                }
            }
        }
    }
}
