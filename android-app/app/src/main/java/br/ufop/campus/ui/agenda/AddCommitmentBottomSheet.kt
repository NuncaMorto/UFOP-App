package br.ufop.campus.ui.agenda

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.AgendaTimelineItem
import br.ufop.campus.ui.components.CampusDatePickerField
import br.ufop.campus.ui.components.CampusTimePickerField
import br.ufop.campus.ui.components.defaultTodayIsoDate
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

private data class CommitmentCategoryChip(
    val label: String,
    val titlePlaceholder: String
)

private val ufopCategoryChips = listOf(
    CommitmentCategoryChip(
        label = "Grupo de estudo",
        titlePlaceholder = "Ex.: Grupo de estudo de Cálculo II"
    ),
    CommitmentCategoryChip(
        label = "Monitoria",
        titlePlaceholder = "Ex.: Monitoria de Algoritmos"
    ),
    CommitmentCategoryChip(
        label = "Laboratório",
        titlePlaceholder = "Ex.: Prática no Laboratório"
    ),
    CommitmentCategoryChip(
        label = "Iniciação Científica",
        titlePlaceholder = "Ex.: Reunião de Iniciação Científica"
    )
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddCommitmentBottomSheet(
    formattedDate: String,
    selectedIsoDate: String,
    campusLabel: String,
    weatherTempC: Int,
    editingCommitment: AgendaTimelineItem? = null,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String,
        commitmentDate: String,
        startTime: String,
        endTime: String,
        location: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = LocalCampusThemeController.current.isDark
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = if (a11y.largeTouchTargets) 56.dp else 48.dp
    val minFieldHeight = if (a11y.largeTouchTargets) 56.dp else 52.dp
    val isEditMode = editingCommitment != null
    val errorTextColor = accessibleTextColor(StatusAbsentRed, isDark)

    var selectedCategory by remember(editingCommitment?.id) { mutableStateOf<String?>(null) }
    var title by remember(editingCommitment?.id) {
        mutableStateOf(editingCommitment?.title ?: "")
    }
    var commitmentDate by remember(editingCommitment?.id, selectedIsoDate) {
        mutableStateOf(selectedIsoDate.ifBlank { defaultTodayIsoDate() })
    }
    var startTime by remember(editingCommitment?.id) {
        mutableStateOf(editingCommitment?.startTime ?: "14:00")
    }
    var endTime by remember(editingCommitment?.id) {
        mutableStateOf(editingCommitment?.endTime ?: "15:30")
    }
    var location by remember(editingCommitment?.id) {
        mutableStateOf(editingCommitment?.location ?: "")
    }
    var description by remember(editingCommitment?.id) {
        mutableStateOf(editingCommitment?.subtitle ?: "")
    }
    var showTitleError by remember { mutableStateOf(false) }
    val isTimeRangeValid = isEndTimeAfterStart(startTime, endTime)

    val activeTitlePlaceholder = ufopCategoryChips
        .firstOrNull { it.label == selectedCategory }
        ?.titlePlaceholder
        ?: "Ex.: Reunião ou atividade no campus"

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
            // Header: Tag + Pílula de clima + Título + Botão Fechar (>= 48.dp unclipped)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AGENDA UFOP · $campusLabel",
                                style = MaterialTheme.typography.labelMedium,
                                color = accessibleTextColor(MaterialTheme.colorScheme.primary, isDark),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isDark) DarkWeatherBadgeBg else LightWeatherBadgeBg
                        ) {
                            Text(
                                text = "☀ $weatherTempC °C",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) DarkWeatherBadgeText else LightWeatherBadgeText,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isEditMode) "Editar compromisso" else "Novo compromisso",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

            Spacer(modifier = Modifier.height(16.dp))

            // Categorias rápidas (apenas marca a tag selecionada, sem sobrescrever campos)
            Text(
                text = "Categoria",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ufopCategoryChips.forEach { chip ->
                    val isSelected = selectedCategory == chip.label
                    Surface(
                        modifier = Modifier
                            .defaultMinSize(minHeight = minTouchTarget)
                            .clip(RoundedCornerShape(50))
                            .clickable(role = Role.Button) {
                                selectedCategory = if (isSelected) null else chip.label
                                showTitleError = false
                            },
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = chip.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Campo: Título
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (it.isNotBlank()) showTitleError = false
                },
                label = { Text("Título") },
                placeholder = { Text(activeTitlePlaceholder) },
                isError = showTitleError,
                supportingText = if (showTitleError) {
                    { Text("Informe um título ou selecione uma categoria.") }
                } else {
                    null
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Campo nativo M3: Data do compromisso
            CampusDatePickerField(
                label = "Data",
                isoDate = commitmentDate,
                onIsoDateSelected = { commitmentDate = it },
                minHeight = minFieldHeight,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Campos nativos M3: Horário início / fim
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CampusTimePickerField(
                    label = "Horário início",
                    time24h = startTime,
                    onTimeSelected = { startTime = it },
                    minHeight = minFieldHeight,
                    modifier = Modifier.weight(1f)
                )

                CampusTimePickerField(
                    label = "Horário fim",
                    time24h = endTime,
                    onTimeSelected = { endTime = it },
                    isError = !isTimeRangeValid,
                    minHeight = minFieldHeight,
                    modifier = Modifier.weight(1f)
                )
            }

            if (!isTimeRangeValid) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "O horário de término deve ser após o início.",
                    style = MaterialTheme.typography.bodySmall,
                    color = errorTextColor,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Campo: Local
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Local") },
                placeholder = { Text("Ex.: Biblioteca · Sala 02") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Campo: Descrição
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descrição") },
                placeholder = { Text("Detalhes opcionais do compromisso") },
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Ações: Cancelar (neutro) e Salvar compromisso / alterações
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
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
                        val resolvedTitle = title.trim().ifBlank { selectedCategory.orEmpty() }
                        if (resolvedTitle.isBlank()) {
                            showTitleError = true
                            return@Button
                        }
                        if (!isEndTimeAfterStart(startTime, endTime)) {
                            return@Button
                        }
                        val resolvedDescription = description.trim().ifBlank {
                            selectedCategory ?: "Compromisso acadêmico no campus"
                        }
                        onSave(
                            resolvedTitle,
                            resolvedDescription,
                            commitmentDate.trim().ifBlank { selectedIsoDate.ifBlank { defaultTodayIsoDate() } },
                            startTime.trim().ifBlank { "14:00" },
                            endTime.trim().ifBlank { "15:30" },
                            location.trim().ifBlank { "Campus UFOP" }
                        )
                    },
                    enabled = isTimeRangeValid,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(minFieldHeight),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = if (isEditMode) "Salvar alterações" else "Salvar",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitmentDetailBottomSheet(
    item: AgendaTimelineItem,
    formattedDate: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = LocalCampusThemeController.current.isDark
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = if (a11y.largeTouchTargets) 56.dp else 48.dp
    val accentColor = parseHexColor(item.colorHex)
    val deleteColor = accessibleTextColor(StatusAbsentRed, isDark)

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
            // Header com botão fechar >= 48.dp unclipped
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
                                .background(accentColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "COMPROMISSO · AGENDA UFOP",
                            style = MaterialTheme.typography.labelMedium,
                            color = accessibleTextColor(MaterialTheme.colorScheme.primary, isDark),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.title,
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

            // Metadados do compromisso
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
                        text = formattedDate,
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
                            text = "${item.startTime}–${item.endTime}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )

                        item.weatherTempC?.let { tempC ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isDark) DarkWeatherBadgeBg else LightWeatherBadgeBg
                            ) {
                                Text(
                                    text = "☀ $tempC °C",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isDark) DarkWeatherBadgeText else LightWeatherBadgeText,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        thickness = 1.dp
                    )

                    Text(
                        text = "Local: ${item.location}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (item.subtitle.isNotBlank()) {
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Opção de excluir compromisso
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(minTouchTarget),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, deleteColor.copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = deleteColor)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = deleteColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Excluir compromisso",
                    style = MaterialTheme.typography.labelLarge,
                    color = deleteColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dois botões Expressivos lado a lado: Editar (esquerda) e Fechar neutro (direita)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .height(if (a11y.largeTouchTargets) 56.dp else 52.dp),
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

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(if (a11y.largeTouchTargets) 56.dp else 52.dp),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        text = "Fechar",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
