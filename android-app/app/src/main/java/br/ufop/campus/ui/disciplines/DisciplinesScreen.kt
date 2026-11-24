package br.ufop.campus.ui.disciplines

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.core.map.MapMeetingFocusRequest
import br.ufop.campus.ui.theme.CampusFoldPosture
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusAdaptiveLayout
import br.ufop.campus.ui.theme.LocalCampusThemeController

@Composable
fun DisciplinesScreen(
    repository: CampusCoreRepository,
    onOpenTimetable: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLocationInMap: ((MapMeetingFocusRequest) -> Unit)? = null
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
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                    DisciplinesFiltersRow(
                        selectedDepartmentId = uiState.selectedDepartmentId,
                        selectedDepartmentLabel = uiState.selectedDepartmentLabel,
                        departments = uiState.departments,
                        selectedSemester = uiState.selectedSemester,
                        availableSemesters = uiState.availableSemesters,
                        layoutMode = layoutMode,
                        filterChipVisualHeight = filterChipVisualHeight,
                        largeTouchTargets = a11y.largeTouchTargets,
                        minTouchTarget = minTouchTarget,
                        onSelectDepartment = { viewModel.selectDepartment(it) },
                        onSelectSemester = { viewModel.selectSemester(it) },
                        onSelectLayoutMode = { mode ->
                            layoutMode = mode
                            CampusRepositoryHolder.saveDisciplinesLayoutMode(context, mode)
                        }
                    )
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
                        DisciplinesEmptyStateCard(
                            searchQuery = uiState.searchQuery,
                            minTouchTarget = minTouchTarget,
                            onClearSearch = { viewModel.onSearchQueryChange("") },
                            onAddCourse = { viewModel.openAddCourseSheet() }
                        )
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
                DisciplineDetailSidePane(
                    detail = activeDetail,
                    scheduleSlots = uiState.activeDetailSlots,
                    absenceHistory = uiState.activeDetailHistory,
                    viewModel = viewModel,
                    onOpenLocationInMap = onOpenLocationInMap,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 340.dp, max = 480.dp)
                        .fillMaxHeight()
                )
            }
        }

        DisciplinesModalOverlays(
            repository = repository,
            uiState = uiState,
            adaptive = adaptive,
            showCurriculumMatrix = showCurriculumMatrix,
            onDismissCurriculumMatrix = { showCurriculumMatrix = false },
            viewModel = viewModel,
            onOpenLocationInMap = onOpenLocationInMap
        )
    }
}
