package br.ufop.campus.ui.disciplines

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.core.ffi.CourseScheduleSlotItem
import br.ufop.campus.core.ffi.DepartmentColorOption
import br.ufop.campus.core.ffi.DisciplineSummaryCard
import br.ufop.campus.ui.curriculum.CurriculumMatrixModal
import br.ufop.campus.ui.theme.CampusFoldPosture
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusAdaptiveLayout
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.parseHexColor
import br.ufop.campus.ui.theme.semanticAbsenceRiskColor
import br.ufop.campus.ui.theme.semanticAbsenceRiskLabel
import br.ufop.campus.ui.theme.semanticAbsenceRiskLevel

@Composable
fun DisciplinesScreen(
    repository: CampusCoreRepository,
    onOpenTimetable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: DisciplinesViewModel = viewModel(
        factory = DisciplinesViewModel.provideFactory(repository)
    )
    val uiState by viewModel.uiState.collectAsState()
    val mutationVersion by repository.mutationVersion.collectAsState()
    val context = LocalContext.current
    val isDark = LocalCampusThemeController.current.isDark
    val adaptive = LocalCampusAdaptiveLayout.current
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = if (a11y.largeTouchTargets) 56.dp else 48.dp
    val actionButtonVisualHeight = if (a11y.largeTouchTargets) minTouchTarget else 37.dp
    val filterChipVisualHeight = if (a11y.largeTouchTargets) minTouchTarget else 35.dp

    var layoutMode by rememberSaveable {
        mutableStateOf(CampusRepositoryHolder.loadDisciplinesLayoutMode(context))
    }
    var showDepartmentMenu by remember { mutableStateOf(false) }
    var showSemesterMenu by remember { mutableStateOf(false) }
    var showCurriculumMatrix by remember { mutableStateOf(false) }
    val activeDetail = uiState.activeDetail
    val showSideDetail = adaptive.useTwoPaneLayout && activeDetail != null
    val columns = adaptive.disciplineGridColumns(hasSideDetailOpen = showSideDetail)
    val gridRows = remember(uiState.disciplines, columns) {
        uiState.disciplines.chunked(columns)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = if (a11y.largeTouchTargets) 18.dp else 14.dp,
                    bottom = adaptive.bottomContentPaddingDp.dp
                ),
                verticalArrangement = Arrangement.spacedBy(if (a11y.largeTouchTargets) 12.dp else 10.dp)
            ) {
            // Header da Aba Disciplinas
            item(key = "disciplines_header") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SEMESTRE ${uiState.selectedSemester}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Disciplinas",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.semantics { heading() }
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Seu acompanhamento de faltas",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Surface(
                            onClick = { viewModel.openAddCourseSheet() },
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
                                    .heightIn(min = actionButtonVisualHeight)
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Disciplina",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (a11y.largeTouchTargets) 12.dp else 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { showCurriculumMatrix = true },
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
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = actionButtonVisualHeight)
                                    .padding(horizontal = 13.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                    contentDescription = "Abrir Matriz Curricular",
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Matriz",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Surface(
                            onClick = onOpenTimetable,
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
                                    .heightIn(min = actionButtonVisualHeight)
                                    .padding(horizontal = 13.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarViewMonth,
                                    contentDescription = "Abrir Grade de horários",
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Grade",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // Linha de Filtros Material 3 Expressive (Departamentos ▾ + 2026.2 ▾ + Alternador Grade/Barras)
            item(key = "disciplines_filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dropdown 1: Todos os departamentos ▾
                        Box(modifier = Modifier.weight(1f, fill = false)) {
                            FilterDropdownChip(
                                label = uiState.selectedDepartmentLabel,
                                selectedColorHex = uiState.departments
                                    .find { it.id == uiState.selectedDepartmentId }
                                    ?.colorHex,
                                isActive = uiState.selectedDepartmentId != null,
                                visualHeight = filterChipVisualHeight,
                                largeTouchTargets = a11y.largeTouchTargets,
                                onClick = { showDepartmentMenu = true }
                            )

                            DropdownMenu(
                                expanded = showDepartmentMenu,
                                onDismissRequest = { showDepartmentMenu = false },
                                shape = RoundedCornerShape(24.dp),
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Todos os departamentos",
                                            fontWeight = if (uiState.selectedDepartmentId == null) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Normal
                                            }
                                        )
                                    },
                                    onClick = {
                                        viewModel.selectDepartment(null)
                                        showDepartmentMenu = false
                                    }
                                )
                                uiState.departments.forEach { dept: DepartmentColorOption ->
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(parseHexColor(dept.colorHex))
                                            )
                                        },
                                        text = {
                                            Text(
                                                text = dept.name,
                                                fontWeight = if (uiState.selectedDepartmentId == dept.id) {
                                                    FontWeight.Bold
                                                } else {
                                                    FontWeight.Normal
                                                }
                                            )
                                        },
                                        onClick = {
                                            viewModel.selectDepartment(dept.id)
                                            showDepartmentMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Dropdown 2: Semestre (ex.: 2026.2 ▾)
                        Box {
                            FilterDropdownChip(
                                label = uiState.selectedSemester,
                                selectedColorHex = null,
                                isActive = false,
                                visualHeight = filterChipVisualHeight,
                                largeTouchTargets = a11y.largeTouchTargets,
                                onClick = { showSemesterMenu = true }
                            )

                            DropdownMenu(
                                expanded = showSemesterMenu,
                                onDismissRequest = { showSemesterMenu = false },
                                shape = RoundedCornerShape(24.dp),
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                uiState.availableSemesters.forEach { semester ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = semester,
                                                fontWeight = if (semester == uiState.selectedSemester) {
                                                    FontWeight.Bold
                                                } else {
                                                    FontWeight.Normal
                                                }
                                            )
                                        },
                                        onClick = {
                                            viewModel.selectSemester(semester)
                                            showSemesterMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Alternador compacto Grade / Barras persistido
                    DisciplinesLayoutTogglePill(
                        layoutMode = layoutMode,
                        largeTouchTargets = a11y.largeTouchTargets,
                        minTouchTarget = minTouchTarget,
                        onSelectMode = { mode ->
                            layoutMode = mode
                            CampusRepositoryHolder.saveDisciplinesLayoutMode(context, mode)
                        }
                    )
                }
            }

            // Barra de busca rápida de disciplinas ou professores (compacta 46.dp no modo normal)
            item(key = "disciplines_search") {
                CompactDisciplinesSearchField(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    height = if (a11y.largeTouchTargets) 56.dp else 46.dp,
                    minTouchTarget = minTouchTarget
                )
            }

            // Lista ou Grade de 2 Colunas de Cards de Disciplinas
            if (uiState.disciplines.isEmpty()) {
                item(key = "disciplines_empty") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (uiState.searchQuery.isNotBlank()) {
                                    "Nenhuma disciplina encontrada para \"${uiState.searchQuery}\""
                                } else {
                                    "Nenhuma disciplina cadastrada"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (uiState.searchQuery.isNotBlank()) {
                                    "Tente outro termo de busca ou limpe o filtro."
                                } else {
                                    "Adicione uma disciplina manualmente ou importe seu Atestado de Matrícula (PDF) na aba Perfil."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            FilledTonalButton(
                                onClick = {
                                    if (uiState.searchQuery.isNotBlank()) {
                                        viewModel.onSearchQueryChange("")
                                    } else {
                                        viewModel.openAddCourseSheet()
                                    }
                                },
                                modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                if (uiState.searchQuery.isBlank()) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = if (uiState.searchQuery.isNotBlank()) {
                                        "Limpar busca"
                                    } else {
                                        "Adicionar disciplina"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else if (layoutMode == "LIST") {
                items(
                    items = uiState.disciplines,
                    key = { "list_${it.courseId}" },
                    contentType = { "discipline_list_card" }
                ) { card ->
                    val courseSlots = remember(card.courseId, mutationVersion) {
                        repository.listCourseSlots(card.courseId)
                    }
                    DisciplineListCard(
                        card = card,
                        slots = courseSlots,
                        isDark = isDark,
                        onClick = { viewModel.openDisciplineDetail(card.courseId) }
                    )
                }
            } else {
                items(
                    items = gridRows,
                    key = { row -> "grid_${columns}_${row.joinToString("_") { it.courseId }}" },
                    contentType = { "discipline_grid_row" }
                ) { rowCards ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowCards.forEach { card ->
                            val courseSlots = remember(card.courseId, mutationVersion) {
                                repository.listCourseSlots(card.courseId)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                DisciplineGridCard(
                                    card = card,
                                    slots = courseSlots,
                                    isDark = isDark,
                                    onClick = { viewModel.openDisciplineDetail(card.courseId) }
                                )
                            }
                        }
                        val missingColumns = (columns - rowCards.size).coerceAtLeast(0)
                        repeat(missingColumns) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Rodapé explicativo abaixo do grid
            item(key = "disciplines_footer") {
                val totalCancelled = remember(uiState.disciplines) {
                    uiState.disciplines.sumOf { it.cancelledClasses }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (totalCancelled > 0) {
                            "Aulas canceladas ($totalCancelled) não somam faltas. As marcações salvas na Agenda atualizam estes relatórios."
                        } else {
                            "Aulas canceladas não somam faltas. As marcações salvas na Agenda atualizam estes relatórios."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Cores padronizadas por departamento · status de faltas em semáforo acessível.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.navigationBarsPadding())
                }
            }
            }

            if (adaptive.useTwoPaneLayout && activeDetail != null) {
                if (adaptive.foldPosture == CampusFoldPosture.BOOK_VERTICAL_HINGE) {
                    Spacer(modifier = Modifier.width(adaptive.hingeWidthDp.coerceAtLeast(16).dp))
                }
                DisciplineDetailPaneContent(
                    detail = activeDetail,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 340.dp, max = 480.dp)
                        .fillMaxHeight(),
                    onDismiss = { viewModel.dismissDisciplineDetail() },
                    onSaveNotes = { updatedNotes ->
                        viewModel.saveDisciplineNotes(
                            courseId = activeDetail.summary.courseId,
                            notes = updatedNotes
                        )
                    },
                    onToggleAssessmentCompleted = { assessmentId, completed ->
                        viewModel.toggleAssessmentCompleted(
                            assessmentId = assessmentId,
                            completed = completed
                        )
                    },
                    onCreateAssessment = { title, kind, dueDate, dueTime ->
                        viewModel.createAssessment(
                            courseId = activeDetail.summary.courseId,
                            title = title,
                            kind = kind,
                            dueDate = dueDate,
                            dueTime = dueTime
                        )
                    },
                    onDeleteAssessment = { assessmentId ->
                        viewModel.deleteAssessment(assessmentId)
                    },
                    scheduleSlots = uiState.activeDetailSlots,
                    absenceHistory = uiState.activeDetailHistory,
                    onAddScheduleSlot = { dayOfWeek, startTime, endTime, building, room, shortLocation ->
                        viewModel.addScheduleSlot(
                            courseId = activeDetail.summary.courseId,
                            dayOfWeek = dayOfWeek,
                            startTime = startTime,
                            endTime = endTime,
                            building = building,
                            room = room,
                            shortLocation = shortLocation
                        )
                    },
                    onUpdateScheduleSlot = { slotId, dayOfWeek, startTime, endTime, building, room, shortLocation ->
                        viewModel.updateScheduleSlot(
                            slotId = slotId,
                            dayOfWeek = dayOfWeek,
                            startTime = startTime,
                            endTime = endTime,
                            building = building,
                            room = room,
                            shortLocation = shortLocation
                        )
                    },
                    onDeleteScheduleSlot = { slotId ->
                        viewModel.deleteScheduleSlot(slotId)
                    },
                    onDeleteCourse = {
                        viewModel.deleteCourse(activeDetail.summary.courseId)
                    },
                    onUpdateAbsences = { newTotal ->
                        viewModel.updateCourseAbsences(activeDetail.summary.courseId, newTotal)
                    },
                    onUpdateCourseDetails = { name, professor, departmentId, workloadHours ->
                        viewModel.updateCourseDetails(
                            courseId = activeDetail.summary.courseId,
                            name = name,
                            professor = professor,
                            departmentId = departmentId,
                            workloadHours = workloadHours
                        )
                    },
                    onUpdateAssessment = { assessmentId, courseId, title, kind, dueDate, dueTime, completed ->
                        viewModel.updateAssessment(
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
        }

        // Bottom Sheet de Detalhe da Disciplina (somente quando não estiver em layout de 2 painéis)
        if (activeDetail != null && !adaptive.useTwoPaneLayout) {
            DisciplineDetailBottomSheet(
                detail = activeDetail,
                onDismiss = { viewModel.dismissDisciplineDetail() },
                onSaveNotes = { updatedNotes ->
                    viewModel.saveDisciplineNotes(
                        courseId = activeDetail.summary.courseId,
                        notes = updatedNotes
                    )
                },
                onToggleAssessmentCompleted = { assessmentId, completed ->
                    viewModel.toggleAssessmentCompleted(
                        assessmentId = assessmentId,
                        completed = completed
                    )
                },
                onCreateAssessment = { title, kind, dueDate, dueTime ->
                    viewModel.createAssessment(
                        courseId = activeDetail.summary.courseId,
                        title = title,
                        kind = kind,
                        dueDate = dueDate,
                        dueTime = dueTime
                    )
                },
                onDeleteAssessment = { assessmentId ->
                    viewModel.deleteAssessment(assessmentId)
                },
                scheduleSlots = uiState.activeDetailSlots,
                absenceHistory = uiState.activeDetailHistory,
                onAddScheduleSlot = { dayOfWeek, startTime, endTime, building, room, shortLocation ->
                    viewModel.addScheduleSlot(
                        courseId = activeDetail.summary.courseId,
                        dayOfWeek = dayOfWeek,
                        startTime = startTime,
                        endTime = endTime,
                        building = building,
                        room = room,
                        shortLocation = shortLocation
                    )
                },
                onUpdateScheduleSlot = { slotId, dayOfWeek, startTime, endTime, building, room, shortLocation ->
                    viewModel.updateScheduleSlot(
                        slotId = slotId,
                        dayOfWeek = dayOfWeek,
                        startTime = startTime,
                        endTime = endTime,
                        building = building,
                        room = room,
                        shortLocation = shortLocation
                    )
                },
                onDeleteScheduleSlot = { slotId ->
                    viewModel.deleteScheduleSlot(slotId)
                },
                onDeleteCourse = {
                    viewModel.deleteCourse(activeDetail.summary.courseId)
                },
                onUpdateAbsences = { newTotal ->
                    viewModel.updateCourseAbsences(activeDetail.summary.courseId, newTotal)
                },
                onUpdateCourseDetails = { name, professor, departmentId, workloadHours ->
                    viewModel.updateCourseDetails(
                        courseId = activeDetail.summary.courseId,
                        name = name,
                        professor = professor,
                        departmentId = departmentId,
                        workloadHours = workloadHours
                    )
                },
                onUpdateAssessment = { assessmentId, courseId, title, kind, dueDate, dueTime, completed ->
                    viewModel.updateAssessment(
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

        if (uiState.showAddCourseSheet) {
            val addCourseDepartments = uiState.allDepartments.ifEmpty { repository.listDepartments() }
            AddCourseBottomSheet(
                semester = uiState.selectedSemester,
                departments = addCourseDepartments,
                initialDepartmentId = uiState.selectedDepartmentId
                    ?: uiState.departments.firstOrNull()?.id
                    ?: addCourseDepartments.firstOrNull()?.id
                    ?: "decom",
                onDismiss = { viewModel.dismissAddCourseSheet() },
                onSaveCourse = { departmentId, name, professor, workloadHours, colorOverrideHex, initialSlot ->
                    viewModel.createCourse(
                        departmentId = departmentId,
                        name = name,
                        professor = professor,
                        workloadHours = workloadHours,
                        colorOverrideHex = colorOverrideHex,
                        initialSlot = initialSlot
                    )
                }
            )
        }

        if (showCurriculumMatrix) {
            Dialog(
                onDismissRequest = { showCurriculumMatrix = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                CurriculumMatrixModal(
                    repository = repository,
                    onDismiss = { showCurriculumMatrix = false }
                )
            }
        }
    }
}

@Composable
private fun FilterDropdownChip(
    label: String,
    selectedColorHex: String?,
    isActive: Boolean,
    visualHeight: Dp = 35.dp,
    largeTouchTargets: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = if (largeTouchTargets) {
            Modifier.defaultMinSize(minHeight = visualHeight)
        } else {
            Modifier.minimumInteractiveComponentSize()
        },
        shape = RoundedCornerShape(22.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = visualHeight)
                .padding(
                    horizontal = if (largeTouchTargets) 14.dp else 11.dp,
                    vertical = if (largeTouchTargets) 10.dp else 6.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (selectedColorHex != null) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(parseHexColor(selectedColorHex))
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DisciplinesLayoutTogglePill(
    layoutMode: String,
    largeTouchTargets: Boolean,
    minTouchTarget: Dp,
    onSelectMode: (String) -> Unit
) {
    val pillHeight = if (largeTouchTargets) minTouchTarget else 35.dp
    val segmentWidth = if (largeTouchTargets) 48.dp else 36.dp
    val options = listOf(
        Triple("GRID", Icons.Rounded.GridView, "Visualizar disciplinas em grade"),
        Triple("LIST", Icons.AutoMirrored.Rounded.ViewList, "Visualizar disciplinas em barras")
    )

    Box(contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.height(pillHeight),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                options.forEach { (mode, icon, _) ->
                    val isSelected = layoutMode == mode
                    Box(
                        modifier = Modifier
                            .width(segmentWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            options.forEach { (mode, _, description) ->
                val isSelected = layoutMode == mode
                Box(
                    modifier = Modifier
                        .size(width = segmentWidth + 3.dp, height = minTouchTarget)
                        .clickable(role = Role.Tab) { onSelectMode(mode) }
                        .semantics {
                            selected = isSelected
                            contentDescription = description
                        }
                )
            }
        }
    }
}

@Composable
private fun CompactDisciplinesSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    height: Dp,
    minTouchTarget: Dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = height),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isFocused) 1.5.dp else 1.dp,
            color = if (isFocused) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = height)
                .padding(start = 14.dp, end = if (query.isNotEmpty()) 4.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Buscar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Buscar disciplina ou professor...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Buscar disciplina ou professor" },
                    singleLine = true,
                    interactionSource = interactionSource,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(minTouchTarget.coerceAtMost(height.coerceAtLeast(44.dp)))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Limpar busca",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun formatCourseSlotsSummary(slots: List<CourseScheduleSlotItem>): String? {
    if (slots.isEmpty()) return null
    val allEad = slots.all {
        it.shortLocation.equals("EAD", ignoreCase = true) ||
            it.building.contains("EAD", ignoreCase = true) ||
            it.building.contains("Distância", ignoreCase = true) ||
            it.building.contains("Distancia", ignoreCase = true)
    }
    if (allEad) {
        return "EAD · Ensino a distância"
    }
    val sorted = slots.sortedWith(compareBy({ it.dayOfWeek }, { it.startTime }))
    val daysJoined = sorted.map {
        it.dayLabel.trim().lowercase().replaceFirstChar { c -> c.uppercase() }.take(3)
    }.distinct().joinToString("/")
    val primary = sorted.first()
    val cleanRoom = primary.room.trim()
    val cleanBuilding = primary.building.trim()
    val cleanShort = primary.shortLocation.trim().trimEnd('·', ' ')
    val locationLabel = if (cleanRoom.isNotBlank()) {
        if (cleanShort.contains(cleanRoom, ignoreCase = true) && cleanShort.isNotBlank()) {
            cleanShort
        } else {
            val shortBuilding = when {
                cleanBuilding.startsWith("Bloco de Salas", ignoreCase = true) ||
                    cleanBuilding.startsWith("Blocos de Salas", ignoreCase = true) -> "Bloco"
                else -> cleanBuilding
            }
            listOf(shortBuilding, cleanRoom).filter { it.isNotBlank() }.joinToString(" · ")
        }
    } else {
        cleanBuilding.ifBlank { cleanShort }
    }
    return if (locationLabel.isNotBlank()) {
        "$daysJoined ${primary.startTime} · $locationLabel"
    } else {
        "$daysJoined ${primary.startTime}"
    }
}

@Composable
private fun DisciplineGridCard(
    card: DisciplineSummaryCard,
    slots: List<CourseScheduleSlotItem>,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val accentColor = remember(card.colorHex) { parseHexColor(card.colorHex) }
    val accessibleDeptTextColor = remember(accentColor, isDark) {
        accessibleTextColor(accentColor, isDark)
    }
    val deptAcronym = remember(card.departmentId, card.departmentName) {
        card.departmentId.trim().uppercase()
            .ifBlank { card.departmentName.substringBefore(" ·").trim() }
    }
    val codeChipLabel = remember(deptAcronym, card.code) {
        card.code.trim().ifBlank { deptAcronym }
    }
    val scheduleSummary = remember(slots) { formatCourseSlotsSummary(slots) }
    val progress = (card.currentAbsences.toFloat() / card.maxAbsences.coerceAtLeast(1).toFloat())
        .coerceIn(0f, 1f)
    val riskLevel = semanticAbsenceRiskLevel(card.currentAbsences, card.maxAbsences)
    val riskLabel = semanticAbsenceRiskLabel(card.currentAbsences, card.maxAbsences)
    val riskColor = semanticAbsenceRiskColor(card.currentAbsences, card.maxAbsences, isDark)
    val cardDescription = buildString {
        append(card.name)
        append(", ")
        append(codeChipLabel)
        if (!scheduleSummary.isNullOrBlank()) {
            append(", horário ")
            append(scheduleSummary)
        }
        append(", ${card.currentAbsences} de ${card.maxAbsences} faltas, situação $riskLabel")
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .heightIn(min = 128.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = cardDescription
            },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = accentColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f))
                    ) {
                        Text(
                            text = codeChipLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accessibleDeptTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = card.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    append("${card.currentAbsences}")
                                }
                                withStyle(
                                    SpanStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    append("/${card.maxAbsences}")
                                }
                                if (riskLevel == 0) {
                                    withStyle(
                                        SpanStyle(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        append(" faltas")
                                    }
                                }
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (riskLevel >= 1) {
                            Spacer(modifier = Modifier.width(4.dp))
                            AbsenceRiskBadge(
                                riskLevel = riskLevel,
                                riskLabel = riskLabel,
                                riskColor = riskColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = riskColor,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DisciplineListCard(
    card: DisciplineSummaryCard,
    slots: List<CourseScheduleSlotItem>,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val accentColor = remember(card.colorHex) { parseHexColor(card.colorHex) }
    val accessibleDeptTextColor = remember(accentColor, isDark) {
        accessibleTextColor(accentColor, isDark)
    }
    val deptAcronym = remember(card.departmentId, card.departmentName) {
        card.departmentId.trim().uppercase()
            .ifBlank { card.departmentName.substringBefore(" ·").trim() }
    }
    val codeChipLabel = remember(deptAcronym, card.code) {
        card.code.trim().ifBlank { deptAcronym }
    }
    val scheduleSummary = remember(slots) { formatCourseSlotsSummary(slots) }
    val progress = (card.currentAbsences.toFloat() / card.maxAbsences.coerceAtLeast(1).toFloat())
        .coerceIn(0f, 1f)
    val riskLevel = semanticAbsenceRiskLevel(card.currentAbsences, card.maxAbsences)
    val riskLabel = semanticAbsenceRiskLabel(card.currentAbsences, card.maxAbsences)
    val riskColor = semanticAbsenceRiskColor(card.currentAbsences, card.maxAbsences, isDark)
    val cardDescription = buildString {
        append(card.name)
        append(", ")
        append(codeChipLabel)
        if (!scheduleSummary.isNullOrBlank()) {
            append(", horário ")
            append(scheduleSummary)
        }
        append(", ${card.currentAbsences} de ${card.maxAbsences} faltas, situação $riskLabel")
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = cardDescription
            },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = accentColor.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f))
                        ) {
                            Text(
                                text = codeChipLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = accessibleDeptTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = card.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = scheduleSummary ?: "Horário a definir",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    append("${card.currentAbsences}")
                                }
                                withStyle(
                                    SpanStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    append("/${card.maxAbsences}")
                                }
                            }
                        )
                        Text(
                            text = "faltas",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (riskLevel >= 1) {
                            Spacer(modifier = Modifier.height(2.dp))
                            AbsenceRiskBadge(
                                riskLevel = riskLevel,
                                riskLabel = riskLabel,
                                riskColor = riskColor
                            )
                        }
                    }
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = riskColor,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
private fun AbsenceRiskBadge(
    riskLevel: Int,
    riskLabel: String,
    riskColor: Color
) {
    val riskIcon = if (riskLevel >= 2) Icons.Outlined.ErrorOutline else Icons.Outlined.WarningAmber
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = riskColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, riskColor.copy(alpha = 0.32f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = riskIcon,
                contentDescription = null,
                tint = riskColor,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = riskLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = riskColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
