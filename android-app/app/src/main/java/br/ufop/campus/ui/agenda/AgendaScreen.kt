package br.ufop.campus.ui.agenda

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.TimelineItemKind
import br.ufop.campus.ui.theme.CampusFoldPosture
import br.ufop.campus.ui.theme.CampusWindowWidthClass
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusAdaptiveLayout
import br.ufop.campus.ui.theme.LocalCampusThemeController

@Composable
fun AgendaScreen(
    repository: CampusCoreRepository,
    modifier: Modifier = Modifier,
    viewModel: AgendaViewModel = viewModel(factory = AgendaViewModel.provideFactory(repository))
) {
    val uiState by viewModel.uiState.collectAsState()
    val themeController = LocalCampusThemeController.current
    val adaptive = LocalCampusAdaptiveLayout.current
    val a11y = LocalCampusAccessibility.current.settings
    val dayOverview = uiState.dayOverview

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        if (adaptive.useTwoPaneLayout) {
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 12.dp,
                        top = 18.dp,
                        bottom = adaptive.bottomContentPaddingDp.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        AgendaHeaderSection(
                            campusCity = uiState.activeCampus.city,
                            weatherTempC = uiState.activeCampus.weatherTempC,
                            isDark = themeController.isDark
                        )
                    }
                    item {
                        MonthAndWeekSelectorRow(
                            monthYearLabel = if (uiState.isMonthCalendarExpanded) {
                                formatYearMonthTitle(uiState.displayedYearMonth)
                            } else {
                                dayOverview.monthYearLabel
                            },
                            weekNumber = dayOverview.weekNumber,
                            isCalendarExpanded = uiState.isMonthCalendarExpanded,
                            showTodayButton = !uiState.isViewingToday,
                            reduceMotion = a11y.reduceMotion,
                            largeTouchTargets = a11y.largeTouchTargets,
                            onToggleCalendar = { viewModel.toggleMonthCalendar() },
                            onJumpToToday = { viewModel.jumpToToday() }
                        )
                    }
                    item {
                        AnimatedVisibility(
                            visible = uiState.isMonthCalendarExpanded,
                            enter = if (a11y.reduceMotion) EnterTransition.None else expandVertically() + fadeIn(),
                            exit = if (a11y.reduceMotion) ExitTransition.None else shrinkVertically() + fadeOut()
                        ) {
                            ExpandableSemesterCalendarCard(
                                displayedYearMonth = uiState.displayedYearMonth,
                                selectedIsoDate = uiState.selectedIsoDate,
                                monthIndicators = uiState.monthIndicators,
                                reduceMotion = a11y.reduceMotion,
                                largeTouchTargets = a11y.largeTouchTargets,
                                onPreviousMonth = { viewModel.previousMonth() },
                                onNextMonth = { viewModel.nextMonth() },
                                onSelectMonth = { ym -> viewModel.selectDisplayedMonth(ym) },
                                onSelectDay = { isoDate -> viewModel.selectDay(isoDate) }
                            )
                        }
                    }
                    item {
                        WeekDayPillsRow(
                            days = dayOverview.weekDays,
                            selectedIsoDate = uiState.selectedIsoDate,
                            isDark = themeController.isDark,
                            largeTouchTargets = a11y.largeTouchTargets,
                            onSelectDay = { isoDate -> viewModel.selectDay(isoDate) }
                        )
                    }
                    uiState.semesterRangeNotice?.let { notice ->
                        item {
                            AgendaBannerCard(
                                message = notice,
                                isError = false,
                                isDark = themeController.isDark,
                                largeTouchTargets = a11y.largeTouchTargets,
                                dismissLabel = "Dispensar aviso de período letivo",
                                onDismiss = { viewModel.dismissSemesterRangeNotice() }
                            )
                        }
                    }
                    uiState.actionErrorMessage?.let { errMessage ->
                        item {
                            AgendaBannerCard(
                                message = errMessage,
                                isError = true,
                                isDark = themeController.isDark,
                                largeTouchTargets = a11y.largeTouchTargets,
                                dismissLabel = "Dispensar mensagem de erro",
                                onDismiss = { viewModel.clearActionError() }
                            )
                        }
                    }
                    uiState.nextClassPreview
                        ?.takeIf { it.isoDate != uiState.selectedIsoDate }
                        ?.let { nextClass ->
                            item {
                                NextClassHeroCard(
                                    preview = nextClass,
                                    isSameSelectedDay = false,
                                    isDark = themeController.isDark,
                                    largeTouchTargets = a11y.largeTouchTargets,
                                    onJumpToNextClass = { viewModel.jumpToNextClassDay() }
                                )
                            }
                        }
                    item {
                        AcademicEventsAccordionCard(
                            events = uiState.academicEvents,
                            selectedIsoDate = uiState.selectedIsoDate,
                            isDark = themeController.isDark,
                            reduceMotion = a11y.reduceMotion,
                            largeTouchTargets = a11y.largeTouchTargets
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.navigationBarsPadding())
                    }
                }

                if (adaptive.foldPosture == CampusFoldPosture.BOOK_VERTICAL_HINGE) {
                    Spacer(modifier = Modifier.width(adaptive.hingeWidthDp.coerceAtLeast(16).dp))
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 20.dp,
                        top = 18.dp,
                        bottom = adaptive.bottomContentPaddingDp.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        DaySummaryHeader(
                            formattedDate = dayOverview.formattedDate,
                            classesCount = dayOverview.classesCount,
                            commitmentsCount = dayOverview.commitmentsCount,
                            largeTouchTargets = a11y.largeTouchTargets,
                            onAddCommitmentClick = { viewModel.openAddCommitmentSheet() }
                        )
                    }
                    uiState.statusBannerMessage?.let { message ->
                        item {
                            AgendaBannerCard(
                                message = message,
                                isError = false,
                                isDark = themeController.isDark,
                                largeTouchTargets = a11y.largeTouchTargets,
                                dismissLabel = "Dispensar aviso",
                                onDismiss = { viewModel.clearBannerMessage() }
                            )
                        }
                    }
                    if (dayOverview.items.isEmpty()) {
                        item {
                            AgendaEmptyStateCard(
                                hasCoursesInSemester = uiState.hasCoursesInSemester,
                                nextClassPreview = uiState.nextClassPreview,
                                largeTouchTargets = a11y.largeTouchTargets,
                                onJumpToNextClass = { viewModel.jumpToNextClassDay() },
                                onAddCommitmentClick = { viewModel.openAddCommitmentSheet() }
                            )
                        }
                    } else {
                        val nextClassOnSelectedDay = uiState.nextClassPreview
                            ?.takeIf { it.isoDate == uiState.selectedIsoDate }
                        items(
                            items = dayOverview.items,
                            key = { it.id }
                        ) { item ->
                            val inlineNextClassLabel = nextClassOnSelectedDay?.let { nextClass ->
                                val isMatchingClass = item.kind == TimelineItemKind.CLASS_SESSION && (
                                    (nextClass.slotId != null && item.slotId == nextClass.slotId) ||
                                        (item.title == nextClass.courseName && item.startTime == nextClass.startTime)
                                    )
                                if (isMatchingClass) {
                                    if (nextClass.isHappeningNow) "Agora" else "Próxima aula"
                                } else {
                                    null
                                }
                            }
                            AgendaTimelineCard(
                                item = item,
                                isDark = themeController.isDark,
                                largeTouchTargets = a11y.largeTouchTargets,
                                inlineNextClassLabel = inlineNextClassLabel,
                                onClick = {
                                    when (item.kind) {
                                        TimelineItemKind.CLASS_SESSION -> {
                                            item.slotId?.let { viewModel.openClassCheckIn(it) }
                                        }
                                        TimelineItemKind.COMMITMENT -> {
                                            viewModel.openCommitmentDetail(item)
                                        }
                                    }
                                }
                            )
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.navigationBarsPadding())
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .then(
                        if (adaptive.widthClass == CampusWindowWidthClass.MEDIUM) {
                            Modifier
                                .widthIn(max = 760.dp)
                                .fillMaxWidth()
                        } else {
                            Modifier.fillMaxSize()
                        }
                    ),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 18.dp,
                    bottom = adaptive.bottomContentPaddingDp.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Header editorial: Seu dia no campus + badge de clima azul informativo neutro (sem relógio 1Hz)
                item {
                    AgendaHeaderSection(
                        campusCity = uiState.activeCampus.city,
                        weatherTempC = uiState.activeCampus.weatherTempC,
                        isDark = themeController.isDark
                    )
                }

                // 2. Seletor de Mês/Semana: Novembro 2026 ▾ + [Hoje] + SEMANA N
                item {
                    MonthAndWeekSelectorRow(
                        monthYearLabel = if (uiState.isMonthCalendarExpanded) {
                            formatYearMonthTitle(uiState.displayedYearMonth)
                        } else {
                            dayOverview.monthYearLabel
                        },
                        weekNumber = dayOverview.weekNumber,
                        isCalendarExpanded = uiState.isMonthCalendarExpanded,
                        showTodayButton = !uiState.isViewingToday,
                        reduceMotion = a11y.reduceMotion,
                        largeTouchTargets = a11y.largeTouchTargets,
                        onToggleCalendar = { viewModel.toggleMonthCalendar() },
                        onJumpToToday = { viewModel.jumpToToday() }
                    )
                }

                // 2b. Calendário mensal expansível do semestre letivo com indicadores de atividades
                item {
                    AnimatedVisibility(
                        visible = uiState.isMonthCalendarExpanded,
                        enter = if (a11y.reduceMotion) EnterTransition.None else expandVertically() + fadeIn(),
                        exit = if (a11y.reduceMotion) ExitTransition.None else shrinkVertically() + fadeOut()
                    ) {
                        ExpandableSemesterCalendarCard(
                            displayedYearMonth = uiState.displayedYearMonth,
                            selectedIsoDate = uiState.selectedIsoDate,
                            monthIndicators = uiState.monthIndicators,
                            reduceMotion = a11y.reduceMotion,
                            largeTouchTargets = a11y.largeTouchTargets,
                            onPreviousMonth = { viewModel.previousMonth() },
                            onNextMonth = { viewModel.nextMonth() },
                            onSelectMonth = { ym -> viewModel.selectDisplayedMonth(ym) },
                            onSelectDay = { isoDate -> viewModel.selectDay(isoDate) }
                        )
                    }
                }

                // 3. Semana completa sem corte (DOM..SÁB distribuídos em weight(1f))
                item {
                    WeekDayPillsRow(
                        days = dayOverview.weekDays,
                        selectedIsoDate = uiState.selectedIsoDate,
                        isDark = themeController.isDark,
                        largeTouchTargets = a11y.largeTouchTargets,
                        onSelectDay = { isoDate -> viewModel.selectDay(isoDate) }
                    )
                }

                // 3b. Banner de aviso de fora do período letivo (IHC-13)
                uiState.semesterRangeNotice?.let { notice ->
                    item {
                        AgendaBannerCard(
                            message = notice,
                            isError = false,
                            isDark = themeController.isDark,
                            largeTouchTargets = a11y.largeTouchTargets,
                            dismissLabel = "Dispensar aviso de período letivo",
                            onDismiss = { viewModel.dismissSemesterRangeNotice() }
                        )
                    }
                }

                // 3c. Banner de erro de ação (IHC-13)
                uiState.actionErrorMessage?.let { errMessage ->
                    item {
                        AgendaBannerCard(
                            message = errMessage,
                            isError = true,
                            isDark = themeController.isDark,
                            largeTouchTargets = a11y.largeTouchTargets,
                            dismissLabel = "Dispensar mensagem de erro",
                            onDismiss = { viewModel.clearActionError() }
                        )
                    }
                }

                // 4. Resumo do Dia + botão "+ Compromisso"
                item {
                    DaySummaryHeader(
                        formattedDate = dayOverview.formattedDate,
                        classesCount = dayOverview.classesCount,
                        commitmentsCount = dayOverview.commitmentsCount,
                        largeTouchTargets = a11y.largeTouchTargets,
                        onAddCommitmentClick = { viewModel.openAddCommitmentSheet() }
                    )
                }

                // 4b. Hero Card "Em andamento agora" / "Próxima aula" (IHC-01) — somente quando visualizando outro dia
                uiState.nextClassPreview
                    ?.takeIf { it.isoDate != uiState.selectedIsoDate }
                    ?.let { nextClass ->
                        item {
                            NextClassHeroCard(
                                preview = nextClass,
                                isSameSelectedDay = false,
                                isDark = themeController.isDark,
                                largeTouchTargets = a11y.largeTouchTargets,
                                onJumpToNextClass = { viewModel.jumpToNextClassDay() }
                            )
                        }
                    }

                // Banner de confirmação
                uiState.statusBannerMessage?.let { message ->
                    item {
                        AgendaBannerCard(
                            message = message,
                            isError = false,
                            isDark = themeController.isDark,
                            largeTouchTargets = a11y.largeTouchTargets,
                            dismissLabel = "Dispensar aviso",
                            onDismiss = { viewModel.clearBannerMessage() }
                        )
                    }
                }

                // 5. Timeline de Cards ou Estado Vazio
                if (dayOverview.items.isEmpty()) {
                    item {
                        AgendaEmptyStateCard(
                            hasCoursesInSemester = uiState.hasCoursesInSemester,
                            nextClassPreview = uiState.nextClassPreview,
                            largeTouchTargets = a11y.largeTouchTargets,
                            onJumpToNextClass = { viewModel.jumpToNextClassDay() },
                            onAddCommitmentClick = { viewModel.openAddCommitmentSheet() }
                        )
                    }
                } else {
                    val nextClassOnSelectedDay = uiState.nextClassPreview
                        ?.takeIf { it.isoDate == uiState.selectedIsoDate }
                    items(
                        items = dayOverview.items,
                        key = { it.id }
                    ) { item ->
                        val inlineNextClassLabel = nextClassOnSelectedDay?.let { nextClass ->
                            val isMatchingClass = item.kind == TimelineItemKind.CLASS_SESSION && (
                                (nextClass.slotId != null && item.slotId == nextClass.slotId) ||
                                    (item.title == nextClass.courseName && item.startTime == nextClass.startTime)
                                )
                            if (isMatchingClass) {
                                if (nextClass.isHappeningNow) "Agora" else "Próxima aula"
                            } else {
                                null
                            }
                        }
                        AgendaTimelineCard(
                            item = item,
                            isDark = themeController.isDark,
                            largeTouchTargets = a11y.largeTouchTargets,
                            inlineNextClassLabel = inlineNextClassLabel,
                            onClick = {
                                when (item.kind) {
                                    TimelineItemKind.CLASS_SESSION -> {
                                        item.slotId?.let { viewModel.openClassCheckIn(it) }
                                    }
                                    TimelineItemKind.COMMITMENT -> {
                                        viewModel.openCommitmentDetail(item)
                                    }
                                }
                            }
                        )
                    }
                }

                // 6. Acordeão colapsável: Próximos eventos do Calendário Acadêmico (fechado por padrão)
                item {
                    AcademicEventsAccordionCard(
                        events = uiState.academicEvents,
                        selectedIsoDate = uiState.selectedIsoDate,
                        isDark = themeController.isDark,
                        reduceMotion = a11y.reduceMotion,
                        largeTouchTargets = a11y.largeTouchTargets
                    )
                }

                // Folga inferior explícita para barra flutuante + navegação do sistema (IHC-03)
                item {
                    Spacer(modifier = Modifier.navigationBarsPadding())
                }
            }
        }

        // Bottom Sheet: Check-in e edição rápida da aula
        uiState.activeCheckIn?.let { checkInDetail ->
            ClassCheckInBottomSheet(
                detail = checkInDetail,
                slotItem = uiState.activeCheckInSlot,
                workloadHours = uiState.activeCheckInWorkloadHours,
                selectedIsoDate = uiState.selectedIsoDate,
                editError = uiState.checkInEditError,
                onClearEditError = { viewModel.clearCheckInEditError() },
                onDismiss = { viewModel.dismissClassCheckIn() },
                onSave = { slotId, status ->
                    viewModel.saveClassCheckIn(
                        slotId = slotId,
                        status = status
                    )
                },
                onSaveEdit = { courseId, slotId, name, prof, deptId, workload, dow, start, end, building, room ->
                    viewModel.saveEditedClassDetails(
                        courseId = courseId,
                        slotId = slotId,
                        courseName = name,
                        professor = prof,
                        departmentId = deptId,
                        workloadHours = workload,
                        dayOfWeek = dow,
                        startTime = start,
                        endTime = end,
                        building = building,
                        room = room
                    )
                }
            )
        }

        // Bottom Sheet: Detalhes do Compromisso Extra (Editar / Excluir / Concluído)
        uiState.activeCommitmentDetail?.let { commitmentItem ->
            CommitmentDetailBottomSheet(
                item = commitmentItem,
                formattedDate = dayOverview.formattedDate,
                onDismiss = { viewModel.dismissCommitmentDetail() },
                onEdit = { viewModel.openEditCommitmentSheet(commitmentItem) },
                onDelete = { viewModel.deleteCommitment(commitmentItem.id, commitmentItem.title) }
            )
        }

        // Bottom Sheet: Criar ou Editar Compromisso
        if (uiState.isAddCommitmentSheetOpen) {
            AddCommitmentBottomSheet(
                formattedDate = dayOverview.formattedDate,
                selectedIsoDate = uiState.selectedIsoDate,
                campusLabel = uiState.activeCampus.city,
                weatherTempC = uiState.activeCampus.weatherTempC,
                editingCommitment = uiState.editingCommitment,
                onDismiss = { viewModel.dismissAddCommitmentSheet() },
                onSave = { title, description, commitmentDate, startTime, endTime, location ->
                    viewModel.saveCommitment(
                        title = title,
                        description = description,
                        commitmentDate = commitmentDate,
                        startTime = startTime,
                        endTime = endTime,
                        location = location
                    )
                }
            )
        }
    }
}
