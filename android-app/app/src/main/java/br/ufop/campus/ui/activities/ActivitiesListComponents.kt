package br.ufop.campus.ui.activities

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.StatusCancelledAmber
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.parseHexColor

@Composable
internal fun ActivitiesHeader(
    activeSemester: String,
    pendingCount: Int,
    completedCount: Int,
    largeTouchTargets: Boolean,
    minTouchTarget: Dp,
    onNewDeadlineClick: () -> Unit
) {
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

        val newDeadlineHeight = if (largeTouchTargets) minTouchTarget else 38.dp
        Surface(
            onClick = onNewDeadlineClick,
            modifier = Modifier
                .let {
                    if (largeTouchTargets) {
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

@Composable
internal fun ActivitiesSyllabusImportBanner(
    message: String,
    isWarning: Boolean,
    isDark: Boolean,
    minTouchTarget: Dp,
    onDismiss: () -> Unit
) {
    val rawBannerColor = if (isWarning) {
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
                text = message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = bannerTextColor,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
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

@Composable
internal fun ActivitiesEmptyCoursesBanner() {
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

@Composable
internal fun ActivitiesEmptyStateCard(
    hasDisciplines: Boolean,
    typeFilterLabel: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        val emptyText = if (!hasDisciplines) {
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

@Composable
internal fun ConsolidatedAssessmentCard(
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
