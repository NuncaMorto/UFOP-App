package br.ufop.campus.ui.curriculum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.curriculum.DynamicCourseNode
import br.ufop.campus.core.curriculum.DynamicCourseStatus
import br.ufop.campus.core.curriculum.DynamicCurriculumMatrix
import br.ufop.campus.core.curriculum.DynamicGridEngine
import br.ufop.campus.ui.theme.DarkStatusAttendingGreen
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.accessibleTextColor

@Composable
internal fun DynamicGridScrollableBoard(
    matrix: DynamicCurriculumMatrix,
    coursesByPeriod: Map<Int, List<DynamicCourseNode>>,
    completedCodes: Set<String>,
    isHorizontal: Boolean,
    isDark: Boolean,
    highContrast: Boolean,
    fontScaleMultiplier: Float,
    bottomContentPadding: Dp,
    onCourseClick: (DynamicCourseNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    Box(
        modifier = modifier
            .clipToBounds()
            .verticalScroll(verticalScrollState)
    ) {
        Box(
            modifier = Modifier
                .horizontalScroll(horizontalScrollState)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomContentPadding)
        ) {
            if (isHorizontal) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    for (period in 1..matrix.totalPeriods) {
                        val periodCourses = coursesByPeriod[period].orEmpty()
                        val doneInPeriod = periodCourses.count { it.code.uppercase() in completedCodes }
                        Column(
                            modifier = Modifier.width(136.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PeriodHeaderCard(
                                period = period,
                                completedCount = doneInPeriod,
                                totalCount = periodCourses.size,
                                isDark = isDark,
                                modifier = Modifier.fillMaxWidth()
                            )
                            periodCourses.forEach { course ->
                                val status = DynamicGridEngine.statusOf(matrix, course.code, completedCodes)
                                val missing = if (status == DynamicCourseStatus.LOCKED) {
                                    DynamicGridEngine.missingPrerequisites(matrix, course.code, completedCodes)
                                } else {
                                    emptyList()
                                }
                                DynamicCourseCard(
                                    course = course,
                                    status = status,
                                    missingPrerequisites = missing,
                                    isDark = isDark,
                                    highContrast = highContrast,
                                    fontScaleMultiplier = fontScaleMultiplier,
                                    onClick = { onCourseClick(course) }
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (period in 1..matrix.totalPeriods) {
                        val periodCourses = coursesByPeriod[period].orEmpty()
                        val doneInPeriod = periodCourses.count { it.code.uppercase() in completedCodes }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PeriodRowBadgeCard(
                                period = period,
                                completedCount = doneInPeriod,
                                totalCount = periodCourses.size,
                                totalHours = periodCourses.sumOf { it.workloadHours },
                                isDark = isDark
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                periodCourses.forEach { course ->
                                    val status = DynamicGridEngine.statusOf(matrix, course.code, completedCodes)
                                    val missing = if (status == DynamicCourseStatus.LOCKED) {
                                        DynamicGridEngine.missingPrerequisites(matrix, course.code, completedCodes)
                                    } else {
                                        emptyList()
                                    }
                                    DynamicCourseCard(
                                        course = course,
                                        status = status,
                                        missingPrerequisites = missing,
                                        isDark = isDark,
                                        highContrast = highContrast,
                                        fontScaleMultiplier = fontScaleMultiplier,
                                        onClick = { onCourseClick(course) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodHeaderCard(
    period: Int,
    completedCount: Int,
    totalCount: Int,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val allDone = totalCount > 0 && completedCount == totalCount
    val container = if (allDone) {
        if (isDark) Color(0xFF173320) else Color(0xFFE0F2E2)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val borderColor = if (allDone) {
        accessibleTextColor(
            if (isDark) DarkStatusAttendingGreen else StatusAttendingGreen,
            isDark,
            container
        ).copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${period}º Período",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$completedCount/$totalCount",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PeriodRowBadgeCard(
    period: Int,
    completedCount: Int,
    totalCount: Int,
    totalHours: Int,
    isDark: Boolean
) {
    val allDone = totalCount > 0 && completedCount == totalCount
    val container = if (allDone) {
        if (isDark) Color(0xFF173320) else Color(0xFFE0F2E2)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val borderColor = if (allDone) {
        accessibleTextColor(
            if (isDark) DarkStatusAttendingGreen else StatusAttendingGreen,
            isDark,
            container
        ).copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = Modifier
            .width(92.dp)
            .heightIn(min = 114.dp),
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${period}º Período",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "$completedCount/$totalCount",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${totalHours}h",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
