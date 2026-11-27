package br.ufop.campus.ui.timetable

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.FriendScheduleInfo
import br.ufop.campus.core.ffi.StudentProfile
import br.ufop.campus.ui.theme.CampusPillRadius
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.campusCardBorder
import br.ufop.campus.ui.theme.campusCardElevation

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimetableTopBar(
    effectiveSemester: String,
    timetableSemester: String,
    activeFriend: FriendScheduleInfo?,
    studentProfile: StudentProfile,
    friendSchedules: List<FriendScheduleInfo>,
    availableSemesters: List<String>,
    selectedGradeLabel: String,
    isDark: Boolean,
    largeTouchTargets: Boolean,
    minTouchTarget: Dp,
    toolbarVisualHeight: Dp,
    isLandscape: Boolean,
    isReducedMode: Boolean,
    showFullDayGrid: Boolean,
    onSelectMyGrade: () -> Unit,
    onSelectFriend: (String) -> Unit,
    onRequestRemoveFriend: (FriendScheduleInfo) -> Unit,
    onImportFile: () -> Unit,
    onOpenImportTextDialog: () -> Unit,
    onOpenQrScanner: () -> Unit = {},
    onSelectSemester: (String) -> Unit,
    onShareGrade: () -> Unit,
    onToggleLandscape: () -> Unit,
    onToggleReducedMode: () -> Unit,
    onToggleFullDayGrid: () -> Unit,
    onDismiss: () -> Unit
) {
    // Barra superior compacta sempre visível (seletores, orientação, densidade, faixa de horários e Fechar >= 48.dp fora da tabela)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Grade de horários",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = buildTimetableHeaderSubtitle(
                        effectiveSemester = effectiveSemester,
                        timetableSemester = timetableSemester,
                        activeFriend = activeFriend
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (activeFriend != null) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (activeFriend != null) {
                        accessibleTextColor(MaterialTheme.colorScheme.primary, isDark)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Botão X Fechar: círculo visual de 36.dp em área de toque de 48.dp (expande com largeTouchTargets)
            val closeVisualSize = if (largeTouchTargets) 48.dp else 36.dp
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(minTouchTarget)
            ) {
                Surface(
                    modifier = Modifier.size(closeVisualSize),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    border = campusCardBorder()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar grade de horários",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(if (largeTouchTargets) 22.dp else 18.dp)
                        )
                    }
                }
            }
        }

        // Barra de controles em 2 linhas limpas: Linha 1 (Seletores + Compartilhar circular), Linha 2 (Ícones de visualização + Faixa horária)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GradeSelectorDropdown(
                    selectedGradeLabel = selectedGradeLabel,
                    activeFriend = activeFriend,
                    studentProfile = studentProfile,
                    friendSchedules = friendSchedules,
                    isDark = isDark,
                    toolbarVisualHeight = toolbarVisualHeight,
                    onSelectMyGrade = onSelectMyGrade,
                    onSelectFriend = onSelectFriend,
                    onRequestRemoveFriend = onRequestRemoveFriend,
                    onImportFile = onImportFile,
                    onOpenImportTextDialog = onOpenImportTextDialog,
                    onOpenQrScanner = onOpenQrScanner,
                    modifier = Modifier.weight(1f, fill = false)
                )

                SemesterSelectorDropdown(
                    effectiveSemester = effectiveSemester,
                    availableSemesters = availableSemesters,
                    toolbarVisualHeight = toolbarVisualHeight,
                    onSelectSemester = onSelectSemester
                )
            }

            // Botão circular tonal Compartilhar (.ufopgrade) — sempre fixo na extremidade direita
            val shareVisualSize = if (largeTouchTargets) minTouchTarget else 40.dp
            IconButton(
                onClick = onShareGrade,
                modifier = Modifier.size(minTouchTarget)
            ) {
                Surface(
                    modifier = Modifier.size(shareVisualSize),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = campusCardBorder()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Compartilhar grade",
                            modifier = Modifier.size(if (largeTouchTargets) 22.dp else 18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimetableToolbarIconButton(
                icon = Icons.Outlined.ScreenRotation,
                isActive = isLandscape,
                tooltipText = if (isLandscape) "Orientação horizontal" else "Orientação vertical",
                contentDescriptionText = if (isLandscape) {
                    "Orientação horizontal ativa. Toque para alternar para vertical"
                } else {
                    "Orientação vertical ativa. Toque para alternar para horizontal"
                },
                onClickLabel = if (isLandscape) {
                    "Alternar para orientação vertical"
                } else {
                    "Alternar para orientação horizontal"
                },
                visualSize = if (largeTouchTargets) minTouchTarget else 40.dp,
                minTouchTarget = minTouchTarget,
                onClick = onToggleLandscape
            )

            TimetableToolbarIconButton(
                icon = if (isReducedMode) Icons.Outlined.UnfoldMore else Icons.Outlined.UnfoldLess,
                isActive = isReducedMode,
                tooltipText = if (isReducedMode) "Densidade compacta" else "Densidade normal",
                contentDescriptionText = if (isReducedMode) {
                    "Modo compacto ativo. Toque para alternar para normal"
                } else {
                    "Modo normal ativo. Toque para alternar para compacto"
                },
                onClickLabel = if (isReducedMode) "Alternar para modo normal" else "Alternar para modo compacto",
                visualSize = if (largeTouchTargets) minTouchTarget else 40.dp,
                minTouchTarget = minTouchTarget,
                onClick = onToggleReducedMode
            )

            TimetableToolbarChip(
                label = if (showFullDayGrid) "07:30–22:40" else "Meus horários",
                icon = Icons.Outlined.Schedule,
                isActive = showFullDayGrid,
                visualHeight = toolbarVisualHeight,
                onClickLabel = if (showFullDayGrid) "Mostrar só meus horários" else "Mostrar grade completa de 07:30 às 22:40",
                onClick = onToggleFullDayGrid
            )
        }

        Spacer(modifier = Modifier.height(2.dp))
    }
}

@Composable
private fun SemesterSelectorDropdown(
    effectiveSemester: String,
    availableSemesters: List<String>,
    toolbarVisualHeight: Dp,
    onSelectSemester: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        ExpressiveSelectorPill(
            label = effectiveSemester,
            isHighlighted = false,
            visualHeight = toolbarVisualHeight,
            onClick = { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            availableSemesters.forEach { semesterOption ->
                val isSelected = semesterOption == effectiveSemester
                DropdownMenuItem(
                    text = {
                        Text(
                            text = semesterOption,
                            fontWeight = if (isSelected) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Semestre ativo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        onSelectSemester(semesterOption)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimetableToolbarIconButton(
    icon: ImageVector,
    isActive: Boolean,
    tooltipText: String,
    contentDescriptionText: String,
    onClickLabel: String,
    visualSize: Dp,
    minTouchTarget: Dp,
    onClick: () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(text = tooltipText)
            }
        },
        state = rememberTooltipState()
    ) {
        Surface(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(visualSize)
                .clip(CircleShape)
                .semantics(mergeDescendants = true) {
                    selected = isActive
                    role = Role.Button
                    contentDescription = contentDescriptionText
                }
                .clickable(onClickLabel = onClickLabel, onClick = onClick),
            shape = CircleShape,
            color = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            border = campusCardBorder()
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (minTouchTarget >= 56.dp) 22.dp else 18.dp),
                    tint = if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun TimetableToolbarChip(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    visualHeight: Dp,
    onClickLabel: String,
    contentDescriptionText: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = visualHeight)
            .clip(RoundedCornerShape(50))
            .semantics(mergeDescendants = true) {
                selected = isActive
                role = Role.Button
                if (contentDescriptionText != null) {
                    contentDescription = contentDescriptionText
                }
            }
            .clickable(onClickLabel = onClickLabel, onClick = onClick),
        shape = RoundedCornerShape(CampusPillRadius),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = campusCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 11.dp,
                vertical = if (visualHeight >= 48.dp) 10.dp else 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
internal fun ExpressiveSelectorPill(
    label: String,
    isHighlighted: Boolean,
    leadingAvatarName: String? = null,
    leadingAvatarBase64: String? = null,
    leadingAvatarFilePath: String? = null,
    visualHeight: Dp = 36.dp,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = visualHeight),
        shape = RoundedCornerShape(CampusPillRadius),
        color = if (isHighlighted) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.70f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = campusCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = if (visualHeight >= 48.dp) 10.dp else 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!leadingAvatarName.isNullOrBlank()) {
                FriendAvatarBadge(
                    friendName = leadingAvatarName,
                    avatarBase64 = leadingAvatarBase64,
                    size = 20.dp,
                    avatarFilePath = leadingAvatarFilePath
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
