package br.ufop.campus.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.AcademicCalendarEvent
import br.ufop.campus.core.ffi.AcademicCalendarUpdatePackage
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.ui.theme.DepartmentMathBlue
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.accessibleTextColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AcademicCalendarSubView(
    repository: CampusCoreRepository,
    activeSemester: String,
    mutationVersion: Long,
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
    val isDark = LocalCampusThemeController.current.isDark
    val surfaceColor = MaterialTheme.colorScheme.surface
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = campusMinTouchTarget()
    val safeGreenText = accessibleTextColor(StatusAttendingGreen, isDark, surfaceColor)
    val accessibleBlue = accessibleTextColor(DepartmentMathBlue, isDark, surfaceColor)

    val availableSemesters = remember(mutationVersion) {
        repository.listAvailableCalendarSemesters().ifEmpty { listOf("2026.2", "2027.1", "2027.2") }
    }
    var selectedCalendarSemester by rememberSaveable(activeSemester) {
        mutableStateOf(activeSemester)
    }
    val semesterOptions = remember(availableSemesters, selectedCalendarSemester) {
        if (selectedCalendarSemester in availableSemesters) {
            availableSemesters
        } else {
            listOf(selectedCalendarSemester) + availableSemesters
        }
    }
    val events = remember(selectedCalendarSemester, mutationVersion) {
        repository.getAcademicCalendarEvents(selectedCalendarSemester)
    }
    val eventCountsBySemester = remember(semesterOptions, mutationVersion) {
        semesterOptions.associateWith { sem -> repository.getAcademicCalendarEvents(sem).size }
    }

    val officialSourceLabel = when (selectedCalendarSemester) {
        "2026.2" -> "Resolução CUNI 2945 (ref. pública)"
        "2027.1", "2027.2" -> "Calendário Presencial 2027 (ref. UFOP)"
        else -> "Calendário Acadêmico (ref. UFOP)"
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("Todos") }
    var advancedOptionsExpanded by rememberSaveable { mutableStateOf(false) }
    val expandedIds = remember { mutableStateMapOf<String, Boolean>() }
    val categories = listOf("Todos", "Feriados & Recessos", "Prazos & Matrícula", "Avaliações")

    val filteredEvents = remember(events, searchQuery, selectedCategory) {
        val query = searchQuery.trim().lowercase()
        events.filter { event ->
            val normalizedCat = event.category.lowercase()
                .replace("ç", "c")
                .replace("ã", "a")
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
            val matchesCategory = when (selectedCategory) {
                "Todos" -> true
                "Feriados & Recessos" -> normalizedCat.contains("feriado") ||
                    normalizedCat.contains("recesso") ||
                    normalizedCat.contains("ferias")
                "Prazos & Matrícula" -> normalizedCat.contains("prazo") ||
                    normalizedCat.contains("matric") ||
                    normalizedCat.contains("semestre") ||
                    normalizedCat.contains("inicio") ||
                    normalizedCat.contains("termino")
                "Avaliações" -> normalizedCat.contains("avaliac") ||
                    normalizedCat.contains("prova") ||
                    normalizedCat.contains("exame") ||
                    event.title.lowercase().contains("exame")
                else -> true
            }
            val matchesQuery = if (query.isEmpty()) {
                true
            } else {
                event.title.lowercase().contains(query) ||
                    event.category.lowercase().contains(query) ||
                    event.eventDate.lowercase().contains(query) ||
                    event.monthShort.lowercase().contains(query) ||
                    event.dayNumber.lowercase().contains(query)
            }
            matchesCategory && matchesQuery
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = surfaceColor,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    semesterOptions.forEach { sem ->
                        val isSelected = selectedCalendarSemester == sem
                        val count = eventCountsBySemester[sem] ?: 0
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCalendarSemester = sem },
                            label = {
                                Text(
                                    text = "$sem ($count)",
                                    maxLines = 1,
                                    softWrap = false,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            modifier = Modifier.heightIn(min = if (a11y.largeTouchTargets) 48.dp else 34.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Semestre $selectedCalendarSemester · ${filteredEvents.size}/${events.size} marcos",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Referência: $officialSourceLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = accessibleBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar marco, mês ou categoria...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(minTouchTarget)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpar busca"
                                )
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { category ->
                        val isSelected = selectedCategory == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            label = { Text(category, maxLines = 1, softWrap = false) },
                            modifier = Modifier.heightIn(min = if (a11y.largeTouchTargets) 48.dp else 34.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }

                if (filteredEvents.isEmpty()) {
                    Text(
                        text = if (events.isEmpty()) {
                            "Nenhum marco acadêmico cadastrado para $selectedCalendarSemester."
                        } else {
                            "Nenhum marco encontrado para o filtro selecionado."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        filteredEvents.forEach { event ->
                            val isExpanded = expandedIds[event.id] == true
                            CalendarMilestoneRow(
                                event = event,
                                isExpanded = isExpanded,
                                onToggleExpand = {
                                    expandedIds[event.id] = !isExpanded
                                }
                            )
                        }
                    }
                }
            }
        }

        // Card expansível discreto de Opções avançadas (Modo Administrador de Calendário)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTouchTarget)
                        .clickable { advancedOptionsExpanded = !advancedOptionsExpanded }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AdminPanelSettings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Opções avançadas",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Modo Administrador de Calendário e atualização manual",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = if (advancedOptionsExpanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = if (advancedOptionsExpanded) {
                            "Recolher opções avançadas"
                        } else {
                            "Expandir opções avançadas"
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                CampusAnimatedVisibility(visible = advancedOptionsExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Text(
                            text = "Modo Administrador de Calendário",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )

                        if (!isAdminUnlocked) {
                            Text(
                                text = "Área avançada para importar um novo PDF de Calendário Acadêmico, ou aplicar um pacote compartilhado entre estudantes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = adminPinInput,
                                onValueChange = onAdminPinChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("PIN de manutenção") },
                                placeholder = { Text("Informe o PIN de manutenção") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                shape = RoundedCornerShape(18.dp)
                            )

                            Button(
                                onClick = onUnlockAdmin,
                                enabled = adminPinInput.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = minTouchTarget),
                                shape = RoundedCornerShape(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Desbloquear modo administrador",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Text(
                                text = "Recebeu um arquivo de atualização do calendário?",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            FilledTonalButton(
                                onClick = onImportCalendarJson,
                                enabled = !isImportingCalendarJson,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = minTouchTarget),
                                shape = RoundedCornerShape(22.dp)
                            ) {
                                if (isImportingCalendarJson) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.UploadFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Importar arquivo de calendário",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = StatusAttendingGreen.copy(alpha = if (isDark) 0.16f else 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.VerifiedUser,
                                        contentDescription = null,
                                        tint = safeGreenText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Modo Administrador ativo · Verificação de integridade ativa",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = safeGreenText
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = { onPublishCalendarPdf(selectedCalendarSemester) },
                                enabled = !isPublishingCalendarPdf,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = minTouchTarget),
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                if (isPublishingCalendarPdf) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Processando PDF do calendário...",
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
                                        text = "Enviar PDF do Calendário ($selectedCalendarSemester)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { onExportCalendarJson(selectedCalendarSemester) },
                                enabled = !isExportingCalendarJson,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = minTouchTarget),
                                shape = RoundedCornerShape(22.dp)
                            ) {
                                if (isExportingCalendarJson) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.FileDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Exportar arquivo de calendário ($selectedCalendarSemester)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (activePkg != null) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = surfaceColor
                                ) {
                                    Text(
                                        text = "Versão ${activePkg.version} · ${activePkg.events.size} eventos sincronizados",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        if (adminFeedbackMessage != null) {
                            StatusFeedbackBanner(
                                message = adminFeedbackMessage,
                                isError = adminFeedbackIsError
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun CalendarMilestoneRow(
    event: AcademicCalendarEvent,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {}
) {
    val isDark = LocalCampusThemeController.current.isDark
    val surfaceColor = MaterialTheme.colorScheme.surface
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = campusMinTouchTarget()
    val accessibleBlue = accessibleTextColor(DepartmentMathBlue, isDark, surfaceColor)

    Surface(
        onClick = onToggleExpand,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minTouchTarget),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(width = 52.dp, height = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = DepartmentMathBlue.copy(alpha = if (isDark) 0.18f else 0.12f),
                    border = BorderStroke(1.dp, accessibleBlue.copy(alpha = 0.32f))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = event.dayNumber,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = accessibleBlue
                        )
                        Text(
                            text = event.monthShort.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = accessibleBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${event.category} · ${event.dayNumber} ${event.monthShort}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (isExpanded) "Recolher detalhes" else "Expandir detalhes",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            CampusAnimatedVisibility(visible = isExpanded) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = surfaceColor
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Data: ${event.eventDate.ifBlank { "${event.dayNumber} ${event.monthShort}" }}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = accessibleBlue
                        )
                        Text(
                            text = "Categoria: ${event.category} · Semestre ${event.semester}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
