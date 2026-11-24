package br.ufop.campus.ui.disciplines

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.GridView
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.DepartmentColorOption
import br.ufop.campus.ui.theme.parseHexColor

@Composable
internal fun DisciplinesFiltersRow(
    selectedDepartmentId: String?,
    selectedDepartmentLabel: String,
    departments: List<DepartmentColorOption>,
    selectedSemester: String,
    availableSemesters: List<String>,
    layoutMode: String,
    filterChipVisualHeight: Dp,
    largeTouchTargets: Boolean,
    minTouchTarget: Dp,
    onSelectDepartment: (String?) -> Unit,
    onSelectSemester: (String) -> Unit,
    onSelectLayoutMode: (String) -> Unit
) {
    var showDepartmentMenu by remember { mutableStateOf(false) }
    var showSemesterMenu by remember { mutableStateOf(false) }

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
                    label = selectedDepartmentLabel,
                    selectedColorHex = departments
                        .find { it.id == selectedDepartmentId }
                        ?.colorHex,
                    isActive = selectedDepartmentId != null,
                    visualHeight = filterChipVisualHeight,
                    largeTouchTargets = largeTouchTargets,
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
                                fontWeight = if (selectedDepartmentId == null) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                }
                            )
                        },
                        onClick = {
                            onSelectDepartment(null)
                            showDepartmentMenu = false
                        }
                    )
                    departments.forEach { dept: DepartmentColorOption ->
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
                                    fontWeight = if (selectedDepartmentId == dept.id) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    }
                                )
                            },
                            onClick = {
                                onSelectDepartment(dept.id)
                                showDepartmentMenu = false
                            }
                        )
                    }
                }
            }

            // Dropdown 2: Semestre (ex.: 2026.2 ▾)
            Box {
                FilterDropdownChip(
                    label = selectedSemester,
                    selectedColorHex = null,
                    isActive = false,
                    visualHeight = filterChipVisualHeight,
                    largeTouchTargets = largeTouchTargets,
                    onClick = { showSemesterMenu = true }
                )

                DropdownMenu(
                    expanded = showSemesterMenu,
                    onDismissRequest = { showSemesterMenu = false },
                    shape = RoundedCornerShape(24.dp),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    availableSemesters.forEach { semester ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = semester,
                                    fontWeight = if (semester == selectedSemester) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    }
                                )
                            },
                            onClick = {
                                onSelectSemester(semester)
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
            largeTouchTargets = largeTouchTargets,
            minTouchTarget = minTouchTarget,
            onSelectMode = onSelectLayoutMode
        )
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
internal fun CompactDisciplinesSearchField(
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
