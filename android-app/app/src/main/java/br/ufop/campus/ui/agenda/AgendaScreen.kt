package br.ufop.campus.ui.agenda

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.ufop.campus.core.ffi.AcademicCalendarEvent
import br.ufop.campus.core.ffi.AgendaTimelineItem
import br.ufop.campus.core.ffi.AttendanceStatus
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.DayActivityIndicator
import br.ufop.campus.core.ffi.DayPillItem
import br.ufop.campus.core.ffi.TimelineItemKind
import br.ufop.campus.ui.theme.CommitmentNeutralGray
import br.ufop.campus.ui.theme.DarkWeatherBadgeBg
import br.ufop.campus.ui.theme.DarkWeatherBadgeText
import br.ufop.campus.ui.theme.LightWeatherBadgeBg
import br.ufop.campus.ui.theme.LightWeatherBadgeText
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.StatusCancelledAmber
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.parseHexColor
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun AgendaScreen(
    repository: CampusCoreRepository,
    modifier: Modifier = Modifier,
    viewModel: AgendaViewModel = viewModel(factory = AgendaViewModel.provideFactory(repository))
) {
    val uiState by viewModel.uiState.collectAsState()
    val themeController = LocalCampusThemeController.current
    val a11y = LocalCampusAccessibility.current.settings
    val dayOverview = uiState.dayOverview

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 0.dp),
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
                Spacer(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .height(136.dp)
                )
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

@Composable
private fun AgendaHeaderSection(
    campusCity: String,
    weatherTempC: Int,
    isDark: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Seu dia no campus",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Um dia de cada vez. Tudo por aqui.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        WeatherPillBadge(
            tempC = weatherTempC,
            isDark = isDark,
            modifier = Modifier
                .padding(top = 6.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Clima em $campusCity: $weatherTempC graus Celsius"
                }
        )
    }
}

@Composable
private fun AgendaBannerCard(
    message: String,
    isError: Boolean,
    isDark: Boolean,
    largeTouchTargets: Boolean,
    dismissLabel: String,
    onDismiss: () -> Unit
) {
    val containerColor = if (isError) {
        StatusAbsentRed.copy(alpha = if (isDark) 0.20f else 0.12f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    }
    val borderColor = if (isError) {
        StatusAbsentRed.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    }
    val textColor = if (isError) {
        accessibleTextColor(StatusAbsentRed, isDark)
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val minTouch = if (largeTouchTargets) 56.dp else 48.dp

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(minTouch)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = dismissLabel,
                    tint = textColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun NextClassHeroCard(
    preview: NextClassPreview,
    isSameSelectedDay: Boolean,
    isDark: Boolean,
    largeTouchTargets: Boolean,
    onJumpToNextClass: () -> Unit
) {
    val accentBase = if (preview.isHappeningNow) StatusAttendingGreen else MaterialTheme.colorScheme.primary
    val accentTextColor = accessibleTextColor(accentBase, isDark)
    val cleanLocation = remember(preview.location) { sanitizeAgendaLocation(preview.location) }
    val statusBadgeLabel = if (preview.isHappeningNow) {
        "Em andamento agora"
    } else {
        "Próxima aula · ${preview.dayLabel}"
    }
    val isNavigable = !isSameSelectedDay
    val cardDescription = buildString {
        append(statusBadgeLabel)
        append(": ${preview.courseName}")
        append(", das ${preview.startTime} às ${preview.endTime}")
        if (cleanLocation.isNotBlank()) append(", local $cleanLocation")
        if (isNavigable) append(". Toque para abrir este dia na agenda")
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (largeTouchTargets) 64.dp else 56.dp)
            .clip(RoundedCornerShape(20.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = cardDescription
                if (isNavigable) role = Role.Button
            }
            .then(
                if (isNavigable) {
                    Modifier.clickable(
                        onClickLabel = "Ir para o dia da próxima aula",
                        onClick = onJumpToNextClass
                    )
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(20.dp),
        color = if (preview.isHappeningNow) {
            StatusAttendingGreen.copy(alpha = if (isDark) 0.16f else 0.10f)
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.35f else 0.48f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = accentBase.copy(alpha = 0.40f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accentTextColor)
                    )
                    Text(
                        text = statusBadgeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = accentTextColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = preview.courseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append("${preview.startTime}–${preview.endTime}")
                        if (cleanLocation.isNotBlank()) {
                            append(" · $cleanLocation")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isNavigable) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = accentTextColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun MonthAndWeekSelectorRow(
    monthYearLabel: String,
    weekNumber: Int,
    isCalendarExpanded: Boolean,
    showTodayButton: Boolean,
    reduceMotion: Boolean,
    largeTouchTargets: Boolean,
    onToggleCalendar: () -> Unit,
    onJumpToToday: () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isCalendarExpanded) 180f else 0f,
        animationSpec = if (reduceMotion) snap() else spring(),
        label = "MonthChevronRotation"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .weight(1f, fill = false)
                .minimumInteractiveComponentSize()
                .defaultMinSize(minHeight = if (largeTouchTargets) 52.dp else 36.dp)
                .clip(RoundedCornerShape(50))
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (isCalendarExpanded) "Recolher calendário mensal" else "Expandir calendário mensal",
                    onClick = onToggleCalendar
                ),
            shape = RoundedCornerShape(50),
            color = if (isCalendarExpanded) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                Color.Transparent
            }
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (isCalendarExpanded) 12.dp else 4.dp,
                    vertical = if (largeTouchTargets) 10.dp else 6.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = monthYearLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isCalendarExpanded) "Recolher calendário" else "Expandir calendário mensal",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = showTodayButton,
                enter = if (reduceMotion) EnterTransition.None else fadeIn() + scaleIn(),
                exit = if (reduceMotion) ExitTransition.None else fadeOut() + scaleOut()
            ) {
                TodayFloatingPill(
                    largeTouchTargets = largeTouchTargets,
                    onClick = onJumpToToday
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = "SEMANA $weekNumber",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ExpandableSemesterCalendarCard(
    displayedYearMonth: YearMonth,
    selectedIsoDate: String,
    monthIndicators: Map<Int, DayActivityIndicator>,
    reduceMotion: Boolean,
    largeTouchTargets: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onSelectDay: (String) -> Unit
) {
    val today = LocalDate.now()
    val selectedBg = MaterialTheme.colorScheme.primaryContainer
    val selectedText = MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Navegação < Mês Ano > (alvos >= 48.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPreviousMonth,
                    modifier = Modifier
                        .size(if (largeTouchTargets) 56.dp else 48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = "Mês anterior",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = formatYearMonthTitle(displayedYearMonth),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )

                IconButton(
                    onClick = onNextMonth,
                    modifier = Modifier
                        .size(if (largeTouchTargets) 56.dp else 48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Próximo mês",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Pílulas 6/6 do semestre: Jan..Jun (meses 1..6) ou Jul..Dez (meses 7..12),
            // sempre preservando o ano atualmente exibido (displayedYearMonth.year).
            val halfStartMonth = if (displayedYearMonth.monthValue in 1..6) 1 else 7
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (monthValue in halfStartMonth until halfStartMonth + 6) {
                    val isCurrentMonth = monthValue == displayedYearMonth.monthValue
                    val pillBg by animateColorAsState(
                        targetValue = if (isCurrentMonth) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        animationSpec = if (reduceMotion) snap() else spring(),
                        label = "SemesterMonthPillBg"
                    )

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = if (largeTouchTargets) 56.dp else 48.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(role = Role.Button) {
                                onSelectMonth(YearMonth.of(displayedYearMonth.year, monthValue))
                            },
                        shape = RoundedCornerShape(50),
                        color = pillBg,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isCurrentMonth) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                            }
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = shortMonthPt(monthValue),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isCurrentMonth) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (isCurrentMonth) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            // Cabeçalho dos dias da semana, domingo primeiro (D S T Q Q S S) com descrição TalkBack completa
            val weekLettersWithFullNames = listOf(
                "D" to "Domingo",
                "S" to "Segunda-feira",
                "T" to "Terça-feira",
                "Q" to "Quarta-feira",
                "Q" to "Quinta-feira",
                "S" to "Sexta-feira",
                "S" to "Sábado"
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                weekLettersWithFullNames.forEach { (letter, fullName) ->
                    Text(
                        text = letter,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = fullName }
                    )
                }
            }

            // Grade completa de 7 colunas do mês com indicadores coloridos por dia
            val firstDayOfMonth = displayedYearMonth.atDay(1)
            // Domingo = 0, Segunda = 1, ..., Sábado = 6
            val leadingEmpty = firstDayOfMonth.dayOfWeek.value % 7
            val daysInMonth = displayedYearMonth.lengthOfMonth()
            val totalWeeks = (leadingEmpty + daysInMonth + 6) / 7

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (weekIdx in 0 until totalWeeks) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (colIdx in 0 until 7) {
                            val cellIndex = weekIdx * 7 + colIdx
                            val dayNumber = cellIndex - leadingEmpty + 1
                            if (dayNumber !in 1..daysInMonth) {
                                Spacer(modifier = Modifier.weight(1f))
                            } else {
                                val cellDate = displayedYearMonth.atDay(dayNumber)
                                val iso = cellDate.toString()
                                val isSelected = iso == selectedIsoDate
                                val isToday = cellDate == today
                                val indicator = monthIndicators[dayNumber]

                                CalendarDayCell(
                                    dayNumber = dayNumber,
                                    isSelected = isSelected,
                                    isToday = isToday,
                                    indicator = indicator,
                                    selectedBg = selectedBg,
                                    selectedText = selectedText,
                                    reduceMotion = reduceMotion,
                                    largeTouchTargets = largeTouchTargets,
                                    onClick = { onSelectDay(iso) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    dayNumber: Int,
    isSelected: Boolean,
    isToday: Boolean,
    indicator: DayActivityIndicator?,
    selectedBg: Color,
    selectedText: Color,
    reduceMotion: Boolean,
    largeTouchTargets: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cellBg by animateColorAsState(
        targetValue = if (isSelected) selectedBg else Color.Transparent,
        animationSpec = if (reduceMotion) snap() else spring(),
        label = "CalendarDayBg"
    )
    val cellTextColor = when {
        isSelected -> selectedText
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    val dotColors = buildList {
        if (indicator != null) {
            indicator.colorsHex.forEach { hex ->
                add(parseHexColor(hex))
            }
            if (isEmpty()) {
                if (indicator.hasClasses || indicator.hasAssessments) {
                    add(MaterialTheme.colorScheme.primary)
                }
                if (indicator.hasCommitments) {
                    add(CommitmentNeutralGray)
                }
            }
        }
    }.take(3)

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = if (largeTouchTargets) 56.dp else 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics(mergeDescendants = true) {
                selected = isSelected
                contentDescription = buildString {
                    append("Dia $dayNumber")
                    if (isToday) append(", hoje")
                    if (isSelected) append(", selecionado")
                    if (indicator?.hasClasses == true) append(", tem aulas")
                    if (indicator?.hasAssessments == true) append(", tem avaliações")
                    if (indicator?.hasCommitments == true) append(", tem compromissos")
                }
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(cellBg)
                .then(
                    if (isToday && !isSelected) {
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dayNumber.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = cellTextColor,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium
            )
        }

        // Pontos/barras indicadoras abaixo do número do dia
        Row(
            modifier = Modifier.height(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dotColors.forEach { dotColor ->
                Box(
                    modifier = Modifier
                        .size(if (indicator?.hasAssessments == true && dotColors.size == 1) 6.dp else 4.5.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}

@Composable
private fun AgendaEmptyStateCard(
    hasCoursesInSemester: Boolean,
    nextClassPreview: NextClassPreview?,
    largeTouchTargets: Boolean,
    onJumpToNextClass: () -> Unit,
    onAddCommitmentClick: () -> Unit
) {
    val actionableNextClass = if (hasCoursesInSemester) nextClassPreview else null

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (hasCoursesInSemester) {
                    "Dia livre no campus"
                } else {
                    "Sua agenda está pronta para começar"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasCoursesInSemester) {
                    "Nenhuma aula ou compromisso agendado para este dia."
                } else {
                    "Adicione um compromisso aqui ou importe seu Atestado de Matrícula (PDF) nas abas Disciplinas ou Perfil para preencher suas aulas."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .defaultMinSize(minHeight = if (largeTouchTargets) 54.dp else 42.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        role = Role.Button,
                        onClick = if (actionableNextClass != null) onJumpToNextClass else onAddCommitmentClick
                    ),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (actionableNextClass != null) {
                            "Ver próxima aula (${actionableNextClass.dayLabel})"
                        } else {
                            "+ Adicionar compromisso"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                            vertical = if (largeTouchTargets) 12.dp else 9.dp
                        )
                    )
                }
            }
        }
    }
}

/** Pílula compacta "Hoje" para voltar ao dia atual com um toque. */
@Composable
private fun TodayFloatingPill(
    largeTouchTargets: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = if (largeTouchTargets) 50.dp else 34.dp)
            .clip(RoundedCornerShape(50))
            .clickable(
                role = Role.Button,
                onClickLabel = "Ir para o dia de hoje",
                onClick = onClick
            ),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 2.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (largeTouchTargets) 14.dp else 10.dp,
                vertical = if (largeTouchTargets) 12.dp else 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Today,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Hoje",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

private fun fullWeekdayNameFromShort(dayShort: String): String = when (dayShort.trim().uppercase()) {
    "DOM" -> "Domingo"
    "SEG" -> "Segunda-feira"
    "TER" -> "Terça-feira"
    "QUA" -> "Quarta-feira"
    "QUI" -> "Quinta-feira"
    "SEX" -> "Sexta-feira"
    "SÁB", "SAB" -> "Sábado"
    else -> dayShort
}

@Composable
private fun WeekDayPillsRow(
    days: List<DayPillItem>,
    selectedIsoDate: String,
    isDark: Boolean,
    largeTouchTargets: Boolean,
    onSelectDay: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        days.forEach { day ->
            val isSelected = day.isoDate == selectedIsoDate
            val containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            val secondaryTextColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (isDark) 0.85f else 0.8f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            val fullDayName = fullWeekdayNameFromShort(day.dayShort)

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = if (largeTouchTargets) 64.dp else 56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .semantics(mergeDescendants = true) {
                        selected = isSelected
                        role = Role.Tab
                        contentDescription = "$fullDayName, dia ${day.dayNumber}" +
                            if (isSelected) ", selecionado" else ""
                    }
                    .clickable { onSelectDay(day.isoDate) },
                shape = RoundedCornerShape(20.dp),
                color = containerColor,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = day.dayShort,
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryTextColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = day.dayNumber.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun DaySummaryHeader(
    formattedDate: String,
    classesCount: Int,
    commitmentsCount: Int,
    largeTouchTargets: Boolean,
    onAddCommitmentClick: () -> Unit
) {
    val classesLabel = if (classesCount == 1) "1 aula" else "$classesCount aulas"
    val commitmentsLabel = if (commitmentsCount == 1) "1 compromisso" else "$commitmentsCount compromissos"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "$classesLabel • $commitmentsLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Surface(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .defaultMinSize(minHeight = if (largeTouchTargets) 52.dp else 38.dp)
                .clip(RoundedCornerShape(50))
                .clickable(
                    role = Role.Button,
                    onClickLabel = "Adicionar compromisso",
                    onClick = onAddCommitmentClick
                ),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "+ Compromisso",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(
                        horizontal = 14.dp,
                        vertical = if (largeTouchTargets) 12.dp else 7.dp
                    )
                )
            }
        }
    }
}

private fun sanitizeAgendaLocation(raw: String): String {
    return raw
        .replace('•', '·')
        .replace(Regex("\\s+[–—-]\\s+"), " · ")
        .split("·")
        .map { segment ->
            segment.trim().trim { ch ->
                ch == '-' || ch == '–' || ch == '—' || ch == '·' || ch == '/' || ch == ',' || ch == ':' || ch == '.'
            }.trim()
        }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" · ")
}

private fun sanitizeAgendaSubtitle(raw: String): String {
    return raw.split("·")
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals("Docente UFOP", ignoreCase = true) }
        .joinToString(" · ")
}

private fun attendanceStatusLabel(status: AttendanceStatus): String = when (status) {
    AttendanceStatus.ATTENDING -> "Vou à aula"
    AttendanceStatus.ABSENT -> "Vou faltar"
    AttendanceStatus.CANCELLED -> "Aula cancelada"
}

@Composable
private fun AgendaTimelineCard(
    item: AgendaTimelineItem,
    isDark: Boolean,
    largeTouchTargets: Boolean,
    inlineNextClassLabel: String? = null,
    onClick: () -> Unit
) {
    val accentColor = parseHexColor(item.colorHex)
    val isClickable = (item.kind == TimelineItemKind.CLASS_SESSION && item.slotId != null) ||
        item.kind == TimelineItemKind.COMMITMENT
    val cleanLocation = remember(item.location) { sanitizeAgendaLocation(item.location) }
    val cleanSubtitle = remember(item.subtitle) { sanitizeAgendaSubtitle(item.subtitle) }

    val cardContentDescription = remember(item, cleanLocation, cleanSubtitle, inlineNextClassLabel) {
        buildString {
            if (item.kind == TimelineItemKind.COMMITMENT) {
                append("Compromisso: ")
            } else {
                append("Aula: ")
            }
            append(item.title)
            if (inlineNextClassLabel != null) append(". $inlineNextClassLabel")
            append(". Horário: ${item.startTime} às ${item.endTime}")
            item.status?.let { append(". Status: ${attendanceStatusLabel(it)}") }
            if (cleanLocation.isNotBlank()) append(". Local: $cleanLocation")
            if (cleanSubtitle.isNotBlank()) append(". $cleanSubtitle")
            item.weatherTempC?.let { append(". Previsão: $it graus Celsius") }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (largeTouchTargets) 56.dp else 0.dp)
            .clip(RoundedCornerShape(20.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = cardContentDescription
                if (isClickable) role = Role.Button
            }
            .then(
                if (isClickable) {
                    Modifier.clickable(
                        onClickLabel = if (item.kind == TimelineItemKind.CLASS_SESSION) {
                            "Registrar presença ou editar aula"
                        } else {
                            "Abrir detalhes do compromisso"
                        },
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (inlineNextClassLabel != null) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Barra vertical colorida à esquerda (4dp de largura, cantos arredondados)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(accentColor)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Linha superior: Título + Badge de clima (☀ 22 °C) no canto superior direito
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    item.weatherTempC?.let { tempC ->
                        Spacer(modifier = Modifier.width(8.dp))
                        WeatherPillBadge(
                            tempC = tempC,
                            isDark = isDark
                        )
                    }
                }

                // Horário + badge de status (aula) + destaque inline (Agora / Próxima aula) ou tag discreta (compromisso)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${item.startTime}–${item.endTime}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )

                    item.status?.let { status ->
                        AttendanceStatusBadge(status = status, isDark = isDark)
                    }

                    if (inlineNextClassLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "• $inlineNextClassLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (item.kind == TimelineItemKind.COMMITMENT) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "Compromisso",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Localização / EAD
                if (cleanLocation.isNotBlank()) {
                    Text(
                        text = cleanLocation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Subtítulo / Docente (oculta se vazio, sem separador solto nem fallback "Docente UFOP")
                if (cleanSubtitle.isNotBlank()) {
                    Text(
                        text = cleanSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherPillBadge(
    tempC: Int,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isDark) DarkWeatherBadgeBg else LightWeatherBadgeBg
    val textColor = if (isDark) DarkWeatherBadgeText else LightWeatherBadgeText

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "☀ $tempC °C",
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun AttendanceStatusBadge(
    status: AttendanceStatus,
    isDark: Boolean
) {
    val (label, badgeColor) = when (status) {
        AttendanceStatus.ATTENDING -> "Vou à aula" to StatusAttendingGreen
        AttendanceStatus.ABSENT -> "Vou faltar" to StatusAbsentRed
        AttendanceStatus.CANCELLED -> "Aula cancelada" to StatusCancelledAmber
    }
    val readableColor = accessibleTextColor(badgeColor, isDark)

    Surface(
        shape = RoundedCornerShape(50),
        color = badgeColor.copy(alpha = if (isDark) 0.20f else 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(readableColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = readableColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun shortMonthPt(month: Int): String = when (month) {
    1 -> "Jan"
    2 -> "Fev"
    3 -> "Mar"
    4 -> "Abr"
    5 -> "Mai"
    6 -> "Jun"
    7 -> "Jul"
    8 -> "Ago"
    9 -> "Set"
    10 -> "Out"
    11 -> "Nov"
    12 -> "Dez"
    else -> "Mês"
}

private fun formatYearMonthTitle(yearMonth: YearMonth): String {
    val monthName = when (yearMonth.monthValue) {
        1 -> "Janeiro"
        2 -> "Fevereiro"
        3 -> "Março"
        4 -> "Abril"
        5 -> "Maio"
        6 -> "Junho"
        7 -> "Julho"
        8 -> "Agosto"
        9 -> "Setembro"
        10 -> "Outubro"
        11 -> "Novembro"
        12 -> "Dezembro"
        else -> "Mês"
    }
    return "$monthName ${yearMonth.year}"
}


@Composable
private fun AcademicEventsAccordionCard(
    events: List<AcademicCalendarEvent>,
    selectedIsoDate: String,
    isDark: Boolean,
    reduceMotion: Boolean,
    largeTouchTargets: Boolean
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduceMotion) snap() else spring(),
        label = "AcademicEventsChevron"
    )

    val referenceIso = remember(selectedIsoDate) {
        runCatching { LocalDate.parse(selectedIsoDate) }.getOrDefault(LocalDate.now()).toString()
    }
    val sortedEvents = remember(events) { events.sortedBy { it.eventDate } }
    val upcomingEvents = remember(sortedEvents, referenceIso) {
        sortedEvents.filter { it.eventDate >= referenceIso }
    }
    val displayedEvents = upcomingEvents.ifEmpty { sortedEvents }
    val badgeLabel = when (displayedEvents.size) {
        0 -> "UFOP"
        1 -> "1 evento"
        else -> "${displayedEvents.size} eventos"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = if (largeTouchTargets) 56.dp else 48.dp)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = if (expanded) "Recolher próximos eventos" else "Expandir próximos eventos"
                    ) { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Próximos eventos do Calendário Acadêmico",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Recolher eventos" else "Expandir eventos",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(chevronRotation)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = if (reduceMotion) EnterTransition.None else expandVertically() + fadeIn(),
                exit = if (reduceMotion) ExitTransition.None else shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (displayedEvents.isEmpty()) {
                        Text(
                            text = "Nenhum evento carregado ainda. Feriados, recessos e marcos do calendário acadêmico de referência aparecem aqui quando o calendário é atualizado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val rangeSummary = if (displayedEvents.size > 1) {
                            val first = displayedEvents.first()
                            val last = displayedEvents.last()
                            val scopeLabel = if (upcomingEvents.isNotEmpty()) "Próximos marcos" else "Calendário do semestre"
                            "$scopeLabel · ${first.dayNumber} ${first.monthShort} – ${last.dayNumber} ${last.monthShort}"
                        } else {
                            "Ordem cronológica do semestre"
                        }
                        Text(
                            text = rangeSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            displayedEvents.forEach { event ->
                                AcademicEventRow(event = event, isDark = isDark)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AcademicEventRow(
    event: AcademicCalendarEvent,
    isDark: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .width(52.dp)
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = event.dayNumber,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = event.monthShort.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accessibleTextColor(MaterialTheme.colorScheme.primary, isDark),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (event.category.isNotBlank()) {
                Text(
                    text = event.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
