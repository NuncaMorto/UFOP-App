package br.ufop.campus.ui.activities

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.AssessmentWithCourse
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.DisciplineDetail
import br.ufop.campus.core.ffi.DisciplineSummaryCard
import br.ufop.campus.core.ffi.SyllabusImportReport
import br.ufop.campus.core.security.SecurePdfProcessor
import br.ufop.campus.ui.components.CampusDatePickerField
import br.ufop.campus.ui.components.CampusTimePickerField
import br.ufop.campus.ui.components.defaultTodayIsoDate
import br.ufop.campus.ui.disciplines.DisciplineDetailBottomSheet
import br.ufop.campus.ui.disciplines.DisciplinesViewModel
import br.ufop.campus.ui.disciplines.normalizeAssessmentDateInput
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.StatusCancelledAmber
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.parseHexColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun formatActivityTypeFilterLabel(
    selectedTypes: Set<String>,
    onlyCompleted: Boolean
): String {
    return when {
        selectedTypes.isEmpty() && !onlyCompleted -> "Todos os tipos"
        selectedTypes.isEmpty() && onlyCompleted -> "Concluídas"
        selectedTypes.size == 1 && !onlyCompleted -> when (selectedTypes.first()) {
            "Prova" -> "Provas"
            "Trabalho" -> "Trabalhos"
            "Seminário" -> "Seminários"
            else -> selectedTypes.first()
        }
        selectedTypes.size >= 2 && !onlyCompleted -> "${selectedTypes.size} tipos"
        else -> "${selectedTypes.size + 1} filtros"
    }
}

internal fun filterActivityEntries(
    entries: List<AssessmentWithCourse>,
    selectedTypes: Set<String>,
    onlyCompleted: Boolean,
    selectedCourseId: String?
): List<AssessmentWithCourse> {
    return entries.filter { entry ->
        val assessment = entry.assessment
        val matchesType = selectedTypes.isEmpty() || selectedTypes.any { type ->
            when (type) {
                "Prova" -> assessment.kind.contains("Prova", ignoreCase = true)
                "Trabalho" -> assessment.kind.contains("Trabalho", ignoreCase = true)
                "Seminário" -> assessment.kind.contains("Semin", ignoreCase = true)
                else -> assessment.kind.contains(type, ignoreCase = true)
            }
        }
        val matchesCompleted = !onlyCompleted || assessment.completed
        val matchesCourse = selectedCourseId == null || assessment.courseId == selectedCourseId
        matchesType && matchesCompleted && matchesCourse
    }
}

@Composable
fun ActivitiesPlaceholderScreen(
    repository: CampusCoreRepository,
    modifier: Modifier = Modifier
) {
    SideEffect {
        DisciplinesViewModel.sharedRepository = repository
    }

    val isDark = LocalCampusThemeController.current.isDark
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = if (a11y.largeTouchTargets) 56.dp else 48.dp
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val mutationVersion by repository.mutationVersion.collectAsState()
    val activeSemester = remember(mutationVersion) {
        repository.getStudentProfile().activeSemester.ifBlank { "2026.2" }
    }
    var selectedTypesList by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val selectedTypes = remember(selectedTypesList) { selectedTypesList.toSet() }
    var onlyCompleted by rememberSaveable { mutableStateOf(false) }
    var showTypeFilterDropdown by remember { mutableStateOf(false) }
    var selectedFilterCourseId by remember { mutableStateOf<String?>(null) }
    var showDisciplineDropdown by remember { mutableStateOf(false) }
    var activeAssessmentDetailId by remember { mutableStateOf<String?>(null) }
    var activeCourseDetailId by remember { mutableStateOf<String?>(null) }
    var showCreateAssessmentSheet by remember { mutableStateOf(false) }
    var emptyCoursesNotice by remember { mutableStateOf(false) }
    var syllabusImportBannerMessage by remember { mutableStateOf<String?>(null) }
    var syllabusImportBannerIsWarning by remember { mutableStateOf(false) }

    val allSemesterEntries = remember(mutationVersion, activeSemester) {
        repository.listAllAssessments(semester = activeSemester, kindFilter = null)
    }

    val disciplines = remember(mutationVersion, activeSemester) {
        repository.listDisciplines(semester = activeSemester, departmentId = null)
    }
    val courseCodeById = remember(disciplines) {
        disciplines.associate { it.courseId to it.code.trim() }
    }

    val filteredEntries = remember(
        allSemesterEntries,
        selectedTypes,
        onlyCompleted,
        selectedFilterCourseId
    ) {
        filterActivityEntries(
            entries = allSemesterEntries,
            selectedTypes = selectedTypes,
            onlyCompleted = onlyCompleted,
            selectedCourseId = selectedFilterCourseId
        )
    }
    val typeFilterLabel = remember(selectedTypes, onlyCompleted) {
        formatActivityTypeFilterLabel(selectedTypes, onlyCompleted)
    }
    val hasActiveTypeFilter = selectedTypes.isNotEmpty() || onlyCompleted

    val activeAssessmentEntry: AssessmentWithCourse? = remember(
        activeAssessmentDetailId,
        allSemesterEntries
    ) {
        activeAssessmentDetailId?.let { id ->
            allSemesterEntries.find { it.assessment.id == id }
        }
    }

    val activeDetail: DisciplineDetail? = remember(activeCourseDetailId, mutationVersion) {
        activeCourseDetailId?.let { repository.getDisciplineDetail(it) }
    }

    val pendingCount = allSemesterEntries.count { !it.assessment.completed }
    val completedCount = allSemesterEntries.count { it.assessment.completed }
    val selectedCourseLabel = disciplines.find { it.courseId == selectedFilterCourseId }?.name
        ?: "Todas as disciplinas"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 136.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SEMESTRE $activeSemester",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Atividades",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$pendingCount pendentes · $completedCount concluídas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val newDeadlineHeight = if (a11y.largeTouchTargets) minTouchTarget else 38.dp
                    Surface(
                        onClick = {
                            if (disciplines.isEmpty()) {
                                emptyCoursesNotice = true
                            } else {
                                emptyCoursesNotice = false
                                showCreateAssessmentSheet = true
                            }
                        },
                        modifier = Modifier
                            .let {
                                if (a11y.largeTouchTargets) {
                                    it.defaultMinSize(minHeight = minTouchTarget)
                                } else {
                                    it.minimumInteractiveComponentSize()
                                }
                            }
                            .semantics { role = Role.Button },
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .heightIn(min = newDeadlineHeight)
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Nova avaliação",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Novo prazo",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            if (syllabusImportBannerMessage != null) {
                item {
                    val rawBannerColor = if (syllabusImportBannerIsWarning) {
                        StatusCancelledAmber
                    } else {
                        StatusAttendingGreen
                    }
                    val bannerTextColor = accessibleTextColor(rawBannerColor, isDark)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = rawBannerColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, rawBannerColor.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = syllabusImportBannerMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = bannerTextColor,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { syllabusImportBannerMessage = null },
                                modifier = Modifier.size(minTouchTarget)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar aviso",
                                    tint = bannerTextColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (emptyCoursesNotice && disciplines.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = "Adicione ou importe disciplinas primeiro na aba Disciplinas ou no Perfil para cadastrar prazos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            item {
                val dropdownVisualHeight = if (a11y.largeTouchTargets) minTouchTarget else 35.dp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Surface(
                            onClick = { showTypeFilterDropdown = true },
                            modifier = if (a11y.largeTouchTargets) {
                                Modifier.defaultMinSize(minHeight = minTouchTarget)
                            } else {
                                Modifier.minimumInteractiveComponentSize()
                            },
                            shape = RoundedCornerShape(22.dp),
                            color = if (hasActiveTypeFilter) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (hasActiveTypeFilter) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = dropdownVisualHeight)
                                    .padding(
                                        horizontal = 12.dp,
                                        vertical = if (a11y.largeTouchTargets) 10.dp else 6.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = typeFilterLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (hasActiveTypeFilter) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (hasActiveTypeFilter) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Filtrar por tipo de atividade",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (hasActiveTypeFilter) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showTypeFilterDropdown,
                            onDismissRequest = { showTypeFilterDropdown = false },
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Todos os tipos",
                                        fontWeight = if (!hasActiveTypeFilter) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                trailingIcon = if (!hasActiveTypeFilter) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    selectedTypesList = emptyList()
                                    onlyCompleted = false
                                    showTypeFilterDropdown = false
                                }
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            )

                            listOf(
                                "Provas" to "Prova",
                                "Trabalhos" to "Trabalho",
                                "Seminários" to "Seminário"
                            ).forEach { (label, typeKey) ->
                                val isSelected = typeKey in selectedTypes
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isSelected) {
                                                Icons.Default.CheckBox
                                            } else {
                                                Icons.Default.CheckBoxOutlineBlank
                                            },
                                            contentDescription = null,
                                            tint = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        selectedTypesList = if (isSelected) {
                                            selectedTypesList.filterNot { it == typeKey }
                                        } else {
                                            (selectedTypesList + typeKey).distinct()
                                        }
                                    }
                                )
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Somente concluídas",
                                        fontWeight = if (onlyCompleted) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (onlyCompleted) {
                                            Icons.Default.CheckBox
                                        } else {
                                            Icons.Default.CheckBoxOutlineBlank
                                        },
                                        contentDescription = null,
                                        tint = if (onlyCompleted) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    onlyCompleted = !onlyCompleted
                                }
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (hasActiveTypeFilter) {
                                    TextButton(
                                        onClick = {
                                            selectedTypesList = emptyList()
                                            onlyCompleted = false
                                            showTypeFilterDropdown = false
                                        }
                                    ) {
                                        Text(
                                            text = "Limpar",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = { showTypeFilterDropdown = false }
                                ) {
                                    Text(
                                        text = "Aplicar",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (disciplines.isNotEmpty()) {
                        Box(modifier = Modifier.weight(1f, fill = false)) {
                            Surface(
                                onClick = { showDisciplineDropdown = true },
                                modifier = if (a11y.largeTouchTargets) {
                                    Modifier.defaultMinSize(minHeight = minTouchTarget)
                                } else {
                                    Modifier.minimumInteractiveComponentSize()
                                },
                                shape = RoundedCornerShape(22.dp),
                                color = if (selectedFilterCourseId != null) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (selectedFilterCourseId != null) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .heightIn(min = dropdownVisualHeight)
                                        .padding(
                                            horizontal = 12.dp,
                                            vertical = if (a11y.largeTouchTargets) 10.dp else 6.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = selectedCourseLabel,
                                        modifier = Modifier.weight(1f, fill = false),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selectedFilterCourseId != null) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (selectedFilterCourseId != null) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Filtrar por disciplina",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showDisciplineDropdown,
                                onDismissRequest = { showDisciplineDropdown = false },
                                shape = RoundedCornerShape(22.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Todas as disciplinas") },
                                    onClick = {
                                        selectedFilterCourseId = null
                                        showDisciplineDropdown = false
                                    },
                                    trailingIcon = if (selectedFilterCourseId == null) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null
                                )
                                disciplines.forEach { course ->
                                    val isSelected = selectedFilterCourseId == course.courseId
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = course.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(parseHexColor(course.colorHex))
                                            )
                                        },
                                        trailingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else null,
                                        onClick = {
                                            selectedFilterCourseId = course.courseId
                                            showDisciplineDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (filteredEntries.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        val emptyText = if (disciplines.isEmpty()) {
                            "Nenhuma avaliação cadastrada ainda. Importe seu atestado ou crie disciplinas para adicionar prazos."
                        } else {
                            "Nenhuma avaliação encontrada para o filtro \"$typeFilterLabel\"."
                        }
                        Text(
                            text = emptyText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                items(
                    items = filteredEntries,
                    key = { it.assessment.id }
                ) { entry ->
                    ConsolidatedAssessmentCard(
                        entry = entry,
                        courseCode = courseCodeById[entry.assessment.courseId].orEmpty(),
                        isDark = isDark,
                        minTouchTarget = minTouchTarget,
                        onToggleCompleted = {
                            repository.toggleAssessmentCompleted(
                                assessmentId = entry.assessment.id,
                                completed = !entry.assessment.completed
                            )
                        },
                        onClick = { activeAssessmentDetailId = entry.assessment.id }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Toque em uma avaliação para editar data, horário ou título, excluir ou marcar como concluída.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 112.dp)
        )

        if (activeAssessmentEntry != null) {
            AssessmentDetailBottomSheet(
                entry = activeAssessmentEntry,
                disciplines = disciplines,
                onDismiss = { activeAssessmentDetailId = null },
                onToggleCompleted = { completed ->
                    repository.toggleAssessmentCompleted(
                        assessmentId = activeAssessmentEntry.assessment.id,
                        completed = completed
                    )
                },
                onSaveEdit = { courseId, title, kind, dueDate, dueTime, completed ->
                    repository.updateAssessment(
                        assessmentId = activeAssessmentEntry.assessment.id,
                        courseId = courseId,
                        title = title,
                        kind = kind,
                        dueDate = dueDate,
                        dueTime = dueTime,
                        completed = completed
                    )
                },
                onDelete = {
                    val deletedItem = activeAssessmentEntry.assessment
                    activeAssessmentDetailId = null
                    repository.deleteAssessment(deletedItem.id)
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        val result = snackbarHostState.showSnackbar(
                            message = "Avaliação \"${deletedItem.title}\" excluída",
                            actionLabel = "Desfazer",
                            withDismissAction = true,
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            val restored = repository.createAssessment(
                                courseId = deletedItem.courseId,
                                title = deletedItem.title,
                                kind = deletedItem.kind,
                                dueDate = deletedItem.dueDate,
                                dueTime = deletedItem.dueTime
                            )
                            if (deletedItem.completed) {
                                repository.toggleAssessmentCompleted(restored.id, true)
                            }
                        }
                    }
                },
                onOpenDiscipline = {
                    val targetCourseId = activeAssessmentEntry.assessment.courseId
                    activeAssessmentDetailId = null
                    activeCourseDetailId = targetCourseId
                }
            )
        }

        if (activeDetail != null) {
            DisciplineDetailBottomSheet(
                detail = activeDetail,
                onDismiss = { activeCourseDetailId = null },
                onSaveNotes = { notes ->
                    repository.updateDisciplineNotes(
                        courseId = activeDetail.summary.courseId,
                        notes = notes
                    )
                },
                onToggleAssessmentCompleted = { assessmentId, completed ->
                    repository.toggleAssessmentCompleted(
                        assessmentId = assessmentId,
                        completed = completed
                    )
                },
                onCreateAssessment = { title, kind, dueDate, dueTime ->
                    repository.createAssessment(
                        courseId = activeDetail.summary.courseId,
                        title = title,
                        kind = kind,
                        dueDate = dueDate,
                        dueTime = dueTime
                    )
                },
                onDeleteAssessment = { assessmentId ->
                    repository.deleteAssessment(assessmentId)
                },
                onUpdateCourseDetails = { name, professor, departmentId, workloadHours ->
                    repository.updateCourse(
                        courseId = activeDetail.summary.courseId,
                        name = name,
                        professor = professor,
                        departmentId = departmentId,
                        workloadHours = workloadHours
                    )
                },
                onUpdateAssessment = { assessmentId, courseId, title, kind, dueDate, dueTime, completed ->
                    repository.updateAssessment(
                        assessmentId = assessmentId,
                        courseId = courseId,
                        title = title,
                        kind = kind,
                        dueDate = dueDate,
                        dueTime = dueTime,
                        completed = completed
                    )
                }
            )
        }

        if (showCreateAssessmentSheet && disciplines.isNotEmpty()) {
            CreateAssessmentBottomSheet(
                repository = repository,
                semester = activeSemester,
                disciplines = disciplines,
                initialCourseId = selectedFilterCourseId ?: disciplines.first().courseId,
                onDismiss = { showCreateAssessmentSheet = false },
                onSyllabusImported = { message, isWarning ->
                    syllabusImportBannerMessage = message
                    syllabusImportBannerIsWarning = isWarning
                    showCreateAssessmentSheet = false
                },
                onSave = { courseId, title, kind, dueDate, dueTime ->
                    repository.createAssessment(
                        courseId = courseId,
                        title = title,
                        kind = kind,
                        dueDate = dueDate,
                        dueTime = dueTime
                    )
                    showCreateAssessmentSheet = false
                }
            )
        }
    }
}

@Composable
private fun ConsolidatedAssessmentCard(
    entry: AssessmentWithCourse,
    courseCode: String,
    isDark: Boolean,
    minTouchTarget: Dp,
    onToggleCompleted: () -> Unit,
    onClick: () -> Unit
) {
    val accentColor = parseHexColor(entry.colorHex)
    val accessibleAccent = remember(accentColor, isDark) {
        accessibleTextColor(accentColor, isDark)
    }
    val accessibleGreen = remember(isDark) {
        accessibleTextColor(StatusAttendingGreen, isDark)
    }
    val item = entry.assessment
    val isCompleted = item.completed
    val rawBadgeColor = if (isCompleted) StatusAttendingGreen else accentColor
    val badgeColor = if (isCompleted) accessibleGreen else accessibleAccent

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (isCompleted) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isCompleted) {
                StatusAttendingGreen.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleCompleted,
                modifier = Modifier.size(minTouchTarget)
            ) {
                Icon(
                    imageVector = if (isCompleted) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Outlined.RadioButtonUnchecked
                    },
                    contentDescription = if (isCompleted) {
                        "Desmarcar conclusão"
                    } else {
                        "Marcar como concluída"
                    },
                    tint = if (isCompleted) {
                        accessibleGreen
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(width = 54.dp, height = 54.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = rawBadgeColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, rawBadgeColor.copy(alpha = 0.28f))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = item.dayNumber,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                        Text(
                            text = item.monthShort.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val cleanCourseLabel = if (courseCode.isNotBlank()) {
                        "$courseCode · ${entry.courseName}"
                    } else {
                        entry.courseName
                    }
                    Text(
                        text = cleanCourseLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = accessibleAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCompleted) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (isCompleted) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val suffix = if (isCompleted) " · Concluída" else ""
                    val timePart = if (item.dueTime.isNotBlank()) " · ${item.dueTime}" else ""
                    Text(
                        text = "${item.kind}$timePart$suffix",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCompleted) {
                            accessibleGreen
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatSyllabusImportBanner(courseName: String, report: SyllabusImportReport): Pair<String, Boolean> {
    val missingDetails = buildList {
        if (!report.hasGradeDistribution) add("distribuição de notas não identificada")
        if (!report.hasAssessmentDates) add("datas de avaliações não encontradas")
    }
    val hasWarning = !report.warningMessage.isNullOrBlank() || missingDetails.isNotEmpty()
    val parts = buildList {
        if (report.importedCount > 0) {
            add("${report.importedCount} avaliação(ões) importada(s) do Plano de Ensino de $courseName.")
        } else {
            add("Plano de Ensino de $courseName analisado (nenhuma nova data pendente).")
        }
        if (report.extractedProfessor.isNotBlank()) {
            add("Docente: ${report.extractedProfessor}.")
        }
        if (!report.warningMessage.isNullOrBlank()) {
            add(report.warningMessage)
        } else if (missingDetails.isNotEmpty()) {
            add("Aviso: ${missingDetails.joinToString(" e ")}.")
        }
    }
    return parts.joinToString(" ") to hasWarning
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAssessmentBottomSheet(
    repository: CampusCoreRepository,
    semester: String,
    disciplines: List<DisciplineSummaryCard>,
    initialCourseId: String,
    onDismiss: () -> Unit,
    onSyllabusImported: (message: String, isWarning: Boolean) -> Unit,
    onSave: (courseId: String, title: String, kind: String, dueDate: String, dueTime: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = LocalCampusThemeController.current.isDark
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = if (a11y.largeTouchTargets) 56.dp else 48.dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCourseId by remember(initialCourseId) { mutableStateOf(initialCourseId) }
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("Trabalho") }
    var dateIso by remember { mutableStateOf(defaultTodayIsoDate()) }
    var time24h by remember { mutableStateOf("") }
    var isImportingSyllabus by remember { mutableStateOf(false) }
    var syllabusFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var syllabusFeedbackIsError by remember { mutableStateOf(false) }

    val selectedCourseName = disciplines.find { it.courseId == selectedCourseId }?.name ?: "disciplina selecionada"

    val syllabusPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isImportingSyllabus = true
        syllabusFeedbackMessage = null
        syllabusFeedbackIsError = false
        val targetCourseId = selectedCourseId
        val targetCourseName = selectedCourseName
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    try {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("Não foi possível ler o PDF selecionado.")
                        repository.importCourseSyllabusDetailedPdf(targetCourseId, bytes)
                    } finally {
                        runCatching { SecurePdfProcessor.purgeResidualPdfFiles(context) }
                    }
                }
            }
            isImportingSyllabus = false
            result.onSuccess { report ->
                if (!report.matchedCourse) {
                    syllabusFeedbackIsError = true
                    syllabusFeedbackMessage = report.errorMessage
                        ?: "Este PDF parece ser de outra disciplina e não de $targetCourseName. Nenhuma alteração foi feita."
                } else {
                    val (msg, isWarning) = formatSyllabusImportBanner(targetCourseName, report)
                    if (report.importedCount > 0) {
                        onSyllabusImported(msg, isWarning)
                    } else {
                        syllabusFeedbackIsError = false
                        syllabusFeedbackMessage = msg
                    }
                }
            }.onFailure { error ->
                syllabusFeedbackIsError = true
                syllabusFeedbackMessage =
                    error.message ?: "Falha ao processar o Plano de Ensino (PDF)."
            }
        }
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
                .padding(start = 24.dp, end = 24.dp, top = 6.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "NOVO PRAZO · $semester",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Adicionar avaliação",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier.size(minTouchTarget),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Text(
                text = "Disciplina",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                disciplines.forEach { course ->
                    val isSelected = course.courseId == selectedCourseId
                    val courseColor = parseHexColor(course.colorHex)
                    val accessibleCourseColor = accessibleTextColor(courseColor, isDark)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedCourseId = course.courseId
                            syllabusFeedbackMessage = null
                        },
                        modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(courseColor)
                            )
                        },
                        label = { Text(course.name) },
                        shape = RoundedCornerShape(22.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = courseColor.copy(alpha = 0.16f),
                            selectedLabelColor = accessibleCourseColor
                        )
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Extração automática · $selectedCourseName",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Envie o Plano de Ensino em PDF do professor para importar automaticamente todas as datas de Provas, Trabalhos e Seminários.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = { syllabusPdfLauncher.launch(arrayOf("application/pdf")) },
                        enabled = !isImportingSyllabus,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = minTouchTarget),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        if (isImportingSyllabus) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Lendo Plano de Ensino...",
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
                                text = "Importar datas do Plano de Ensino (PDF)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (syllabusFeedbackMessage != null) {
                        val rawBannerTone = if (syllabusFeedbackIsError) StatusAbsentRed else StatusCancelledAmber
                        val bannerFg = accessibleTextColor(rawBannerTone, isDark)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = rawBannerTone.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = syllabusFeedbackMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = bannerFg,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
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
                    text = "ou cadastre manualmente",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Título da atividade") },
                placeholder = { Text("Ex.: Entrega do Projeto Final") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Prova", "Trabalho", "Seminário").forEach { option ->
                    FilterChip(
                        selected = kind == option,
                        onClick = { kind = option },
                        modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                        label = { Text(option) },
                        shape = RoundedCornerShape(22.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            CampusDatePickerField(
                label = "Data",
                isoDate = dateIso,
                onIsoDateSelected = { dateIso = it },
                minHeight = minTouchTarget
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CampusTimePickerField(
                    label = "Horário (opcional)",
                    time24h = time24h,
                    onTimeSelected = { time24h = it },
                    minHeight = minTouchTarget
                )
                if (time24h.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { time24h = "" },
                            modifier = Modifier.defaultMinSize(minHeight = minTouchTarget)
                        ) {
                            Text(
                                text = "Limpar horário",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
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
                        val cleanTitle = title.trim()
                        if (cleanTitle.isNotEmpty()) {
                            onSave(
                                selectedCourseId,
                                cleanTitle,
                                kind,
                                normalizeAssessmentDateInput(dateIso),
                                time24h.trim()
                            )
                        }
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Salvar avaliação")
                }
            }
        }
    }
}
