package br.ufop.campus.ui.curriculum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.ui.theme.DarkDepartmentBlueText
import br.ufop.campus.ui.theme.DepartmentMathBlue
import br.ufop.campus.ui.theme.accessibleTextColor

@Composable
internal fun MatrixSelectorDropdownChip(
    height: Dp,
    compactLabel: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(
                onClickLabel = "Selecionar matriz curricular",
                onClick = onClick
            )
            .semantics {
                role = Role.Button
                contentDescription = "Selecionar curso e matriz curricular. Atual: $compactLabel"
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            modifier = Modifier.height(height),
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = compactLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
internal fun CollapseArrowIconButton(
    isCollapsed: Boolean,
    size: Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(
                onClickLabel = if (isCollapsed) "Expandir cabeçalho da grade" else "Recolher cabeçalho da grade",
                onClick = onClick
            )
            .semantics {
                role = Role.Button
                contentDescription = if (isCollapsed) {
                    "Expandir cabeçalho e controles da grade dinâmica"
                } else {
                    "Recolher controles para ampliar espaço da grade dinâmica"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(size),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isCollapsed) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
internal fun DynamicGridOrientationSegmentedPill(
    isHorizontal: Boolean,
    controlHeight: Dp,
    onSelectOrientation: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.height(controlHeight),
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                OrientationSegmentChip(
                    label = "Horizontal",
                    selected = isHorizontal,
                    onClick = { onSelectOrientation(true) }
                )
                OrientationSegmentChip(
                    label = "Vertical",
                    selected = !isHorizontal,
                    onClick = { onSelectOrientation(false) }
                )
            }
        }
    }
}

@Composable
private fun OrientationSegmentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics {
                this.role = Role.RadioButton
                this.selected = selected
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
internal fun DynamicGridClearPillButton(
    enabled: Boolean,
    controlHeight: Dp,
    onClick: () -> Unit
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    val borderColor = if (enabled) {
        MaterialTheme.colorScheme.outlineVariant
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }

    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "Limpar disciplinas concluídas na grade dinâmica"
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.height(controlHeight),
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (enabled) 1f else 0.55f),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Limpar",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
internal fun DynamicGridFeedbackBanner(
    state: DynamicGridFeedbackState,
    isDark: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (state.isBlockedWarning) {
        if (isDark) Color(0xFF3A201D) else Color(0xFFFCE6E2)
    } else {
        if (isDark) Color(0xFF1D293A) else Color(0xFFE7EFFC)
    }
    val rawAccent = if (state.isBlockedWarning) {
        if (isDark) Color(0xFFFFA99B) else Color(0xFFB74130)
    } else {
        if (isDark) DarkDepartmentBlueText else DepartmentMathBlue
    }
    val textAccent = accessibleTextColor(rawAccent, isDark, container)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = BorderStroke(1.dp, textAccent.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = textAccent,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fechar aviso",
                    tint = textAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
