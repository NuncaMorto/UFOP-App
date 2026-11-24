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
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import br.ufop.campus.ui.theme.accessibleTextColor

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
                if (activeFriend != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FriendAvatarBadge(
                            friendName = activeFriend.friendName,
                            avatarBase64 = activeFriend.avatarBase64,
                            size = 20.dp
                        )
                        val courseLabel = activeFriend.courseName
                            .takeIf { it.isNotBlank() }
                            ?: "Estudante"
                        Text(
                            text = "${activeFriend.friendName} · $courseLabel · $timetableSemester",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = accessibleTextColor(MaterialTheme.colorScheme.primary, isDark),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = "Semestre $effectiveSemester · Toque em uma aula para ver detalhes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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

        // Linha de controles compactos: Seletor de grade (com avatar), Semestre, Compartilhar, Horizontal/Vertical, Compacto/Normal, Só meus horários/Grade completa
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(if (largeTouchTargets) 6.dp else 2.dp)
        ) {
            // Seletor 1: Minha grade ▾ (preserva foto/avatar do usuário e dos amigos)
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
                onOpenImportTextDialog = onOpenImportTextDialog
            )

            // Seletor 2: Semestre (ex.: 2026.2 ▾)
            SemesterSelectorDropdown(
                effectiveSemester = effectiveSemester,
                availableSemesters = availableSemesters,
                toolbarVisualHeight = toolbarVisualHeight,
                onSelectSemester = onSelectSemester
            )

            // Botão Compartilhar (.ufopgrade)
            Surface(
                onClick = onShareGrade,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .defaultMinSize(minHeight = toolbarVisualHeight),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.40f))
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = 11.dp,
                        vertical = if (largeTouchTargets) 10.dp else 6.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Compartilhar grade",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Compartilhar",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Alternância persistida de orientação: Horizontal / Vertical
            TimetableToolbarChip(
                label = if (isLandscape) "Horizontal" else "Vertical",
                icon = Icons.Outlined.ScreenRotation,
                isActive = !isLandscape,
                visualHeight = toolbarVisualHeight,
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
                onClick = onToggleLandscape
            )

            // Alternância de densidade: Normal / Compacto
            TimetableToolbarChip(
                label = if (isReducedMode) "Compacto" else "Normal",
                icon = if (isReducedMode) Icons.Outlined.UnfoldMore else Icons.Outlined.UnfoldLess,
                isActive = isReducedMode,
                visualHeight = toolbarVisualHeight,
                onClickLabel = if (isReducedMode) "Alternar para modo normal" else "Alternar para modo compacto",
                onClick = onToggleReducedMode
            )

            // Alternância de faixa de horários: Só meus horários / Grade completa (07:30–22:40)
            TimetableToolbarChip(
                label = if (showFullDayGrid) "Grade completa (07:30–22:40)" else "Só meus horários",
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
        shape = RoundedCornerShape(50),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
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
        shape = RoundedCornerShape(50),
        color = if (isHighlighted) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.70f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isHighlighted) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
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
                overflow = TextOverflow.Ellipsis
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
