package br.ufop.campus.ui.curriculum

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.curriculum.DynamicCurriculumMatrix
import br.ufop.campus.core.curriculum.DynamicGridSummary
import br.ufop.campus.ui.theme.DarkStatusAttendingGreen
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.accessibleTextColor

@Composable
internal fun DynamicGridPortraitTopBar(
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize(),
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar para Perfil",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Voltar para Perfil",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }

        Text(
            text = "Grade Dinâmica",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
    }
}

@Composable
internal fun DynamicGridLandscapeToolbar(
    matrix: DynamicCurriculumMatrix,
    summary: DynamicGridSummary,
    isHorizontal: Boolean,
    isCollapsed: Boolean,
    controlHeight: Dp,
    isDark: Boolean,
    highContrast: Boolean,
    reduceMotion: Boolean,
    autoMatchNote: String?,
    isAutoCourseMatched: Boolean,
    onBack: () -> Unit,
    onToggleCollapsed: () -> Unit,
    onOpenMatrixPicker: () -> Unit,
    onSelectOrientation: (Boolean) -> Unit,
    onRequestClear: () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val progressColor = accessibleTextColor(
        if (isDark) DarkStatusAttendingGreen else StatusAttendingGreen,
        isDark,
        surfaceColor
    )
    val progressRatio = if (summary.totalGridHours > 0) {
        (summary.completedHours.toFloat() / summary.totalGridHours.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = surfaceColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(if (isCollapsed) 0.dp else 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = "Voltar para Perfil", onClick = onBack)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Voltar para Perfil"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.size(controlHeight),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    MatrixSelectorDropdownChip(
                        height = controlHeight,
                        compactLabel = "${matrix.courseName} · ${matrix.curriculumLabel} · ${summary.completionPercent}%",
                        onClick = onOpenMatrixPicker
                    )
                }

                DynamicGridOrientationSegmentedPill(
                    isHorizontal = isHorizontal,
                    controlHeight = controlHeight,
                    onSelectOrientation = onSelectOrientation
                )

                DynamicGridClearPillButton(
                    enabled = summary.completedCount > 0,
                    controlHeight = controlHeight,
                    onClick = onRequestClear
                )

                CollapseArrowIconButton(
                    isCollapsed = isCollapsed,
                    size = controlHeight,
                    onClick = onToggleCollapsed
                )
            }

            AnimatedVisibility(
                visible = !isCollapsed,
                enter = if (reduceMotion) EnterTransition.None else expandVertically() + fadeIn(),
                exit = if (reduceMotion) ExitTransition.None else shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${summary.completedHours}/${summary.totalGridHours}h (${summary.completionPercent}%) · ${summary.completedCount}/${summary.totalCount}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LinearProgressIndicator(
                            progress = { progressRatio },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = progressColor,
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DynamicGridLegendRow(
                            isDark = isDark,
                            highContrast = highContrast
                        )
                        if (!autoMatchNote.isNullOrBlank()) {
                            Text(
                                text = autoMatchNote,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isAutoCourseMatched) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                maxLines = if (isAutoCourseMatched) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DynamicGridSummaryCard(
    matrix: DynamicCurriculumMatrix,
    summary: DynamicGridSummary,
    isHorizontal: Boolean,
    isCollapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onOpenMatrixPicker: () -> Unit,
    onSelectOrientation: (Boolean) -> Unit,
    onRequestClear: () -> Unit,
    controlHeight: Dp,
    autoMatchNote: String?,
    isAutoCourseMatched: Boolean,
    isDark: Boolean,
    largeTouchTargets: Boolean,
    highContrast: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val arrowButtonSize = if (largeTouchTargets) 44.dp else 36.dp
    val progressRatio = if (summary.totalGridHours > 0) {
        (summary.completedHours.toFloat() / summary.totalGridHours.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progressColor = accessibleTextColor(
        if (isDark) DarkStatusAttendingGreen else StatusAttendingGreen,
        isDark,
        surfaceColor
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (isCollapsed) 18.dp else 22.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isCollapsed) 14.dp else 16.dp,
                    vertical = if (isCollapsed) 10.dp else 12.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (isCollapsed) 0.dp else 10.dp)
        ) {
            if (isCollapsed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${summary.completedHours}/${summary.totalGridHours}h (${summary.completionPercent}%) · ${summary.completedCount}/${summary.totalCount}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        LinearProgressIndicator(
                            progress = { progressRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = progressColor,
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                    }

                    CollapseArrowIconButton(
                        isCollapsed = true,
                        size = arrowButtonSize,
                        onClick = onToggleCollapsed
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        MatrixSelectorDropdownChip(
                            height = arrowButtonSize,
                            compactLabel = "${matrix.courseName} · ${matrix.curriculumLabel}",
                            onClick = onOpenMatrixPicker
                        )
                    }

                    CollapseArrowIconButton(
                        isCollapsed = false,
                        size = arrowButtonSize,
                        onClick = onToggleCollapsed
                    )
                }
            }

            AnimatedVisibility(
                visible = !isCollapsed,
                enter = if (reduceMotion) EnterTransition.None else expandVertically() + fadeIn(),
                exit = if (reduceMotion) ExitTransition.None else shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = "${summary.completedHours}/${summary.totalGridHours}h (${summary.completionPercent}%) · ${summary.completedCount}/${summary.totalCount} disciplinas",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LinearProgressIndicator(
                            progress = { progressRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = progressColor,
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                        if (!autoMatchNote.isNullOrBlank()) {
                            Text(
                                text = autoMatchNote,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isAutoCourseMatched) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                maxLines = if (isAutoCourseMatched) 1 else 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DynamicGridOrientationSegmentedPill(
                            isHorizontal = isHorizontal,
                            controlHeight = controlHeight,
                            onSelectOrientation = onSelectOrientation
                        )

                        DynamicGridClearPillButton(
                            enabled = summary.completedCount > 0,
                            controlHeight = controlHeight,
                            onClick = onRequestClear
                        )
                    }

                    DynamicGridLegendRow(
                        isDark = isDark,
                        highContrast = highContrast
                    )
                }
            }
        }
    }
}
