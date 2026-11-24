package br.ufop.campus.ui.disciplines

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.ui.theme.StatusAbsentRed

private val WORKLOAD_OPTIONS = listOf(30, 45, 60, 72, 90)

@Composable
internal fun AddCourseHeader(
    semester: String,
    minTouchTarget: Dp,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SEMESTRE $semester",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Nova disciplina",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(minTouchTarget)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Fechar cadastro de disciplina",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun AddCourseIdentityFields(
    name: String,
    professor: String,
    onNameChange: (String) -> Unit,
    onProfessorChange: (String) -> Unit
) {
    // Nome e Professor
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Nome") },
        placeholder = { Text("Ex.: Engenharia de Software I") },
        singleLine = true,
        shape = RoundedCornerShape(16.dp)
    )

    OutlinedTextField(
        value = professor,
        onValueChange = onProfessorChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Professor(a)") },
        placeholder = { Text("Opcional · deixe em branco para preencher depois") },
        singleLine = true,
        shape = RoundedCornerShape(16.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddCourseWorkloadSection(
    selectedDepartmentName: String,
    selectedWorkloadHours: Int,
    isCustomWorkload: Boolean,
    customWorkloadText: String,
    parsedCustomHours: Int?,
    effectiveWorkloadHours: Int,
    previewMaxAbsences: Int,
    previewTotalClasses: Int,
    accentColor: Color,
    accessibleAccent: Color,
    minTouchTarget: Dp,
    onSelectPresetWorkload: (Int) -> Unit,
    onSelectCustomWorkload: () -> Unit,
    onCustomWorkloadTextChange: (String) -> Unit
) {
    // Carga horária + Prévia em tempo real do limite de faltas (30h, 45h, 60h, 72h, 90h + Outra)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Carga horária",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WORKLOAD_OPTIONS.forEach { hours ->
                val isSelected = !isCustomWorkload && selectedWorkloadHours == hours
                val limit = (hours * 3) / 10
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectPresetWorkload(hours) },
                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                    label = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "${hours}h",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "$limit faltas",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor.copy(alpha = 0.16f),
                        selectedLabelColor = accessibleAccent
                    )
                )
            }

            FilterChip(
                selected = isCustomWorkload,
                onClick = onSelectCustomWorkload,
                modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                label = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "Outra",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "15–360h",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accentColor.copy(alpha = 0.16f),
                    selectedLabelColor = accessibleAccent
                )
            )
        }

        if (isCustomWorkload) {
            val customInvalid = parsedCustomHours == null || parsedCustomHours !in 15..360
            OutlinedTextField(
                value = customWorkloadText,
                onValueChange = onCustomWorkloadTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Carga horária personalizada (horas)") },
                placeholder = { Text("Ex.: 75") },
                isError = customInvalid,
                supportingText = {
                    Text(
                        text = if (customInvalid) {
                            "Informe um valor inteiro entre 15h e 360h."
                        } else {
                            "Limite automático: $previewMaxAbsences faltas (25%)"
                        }
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp)
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = accentColor.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Limite automático: $previewMaxAbsences faltas (25%)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accessibleAccent
                )
                Text(
                    text = "$effectiveWorkloadHours h ($previewTotalClasses aulas de 50 min) · $selectedDepartmentName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun InlineErrorBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = StatusAbsentRed.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, StatusAbsentRed.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = "Erro de horário",
                tint = StatusAbsentRed,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = StatusAbsentRed
            )
        }
    }
}
