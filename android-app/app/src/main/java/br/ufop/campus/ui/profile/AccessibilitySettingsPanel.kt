package br.ufop.campus.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.MotionPhotosOff
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SettingsAccessibility
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ufop.campus.ui.theme.CampusAccessibilitySettings

private data class FontScaleStep(
    val scale: Float,
    val label: String
)

private val FONT_SCALE_STEPS = listOf(
    FontScaleStep(1.00f, "100% · Padrão"),
    FontScaleStep(1.15f, "115% · Grande"),
    FontScaleStep(1.30f, "130% · Extra"),
    FontScaleStep(1.45f, "145% · Máximo")
)

/**
 * Painel completo e reutilizável de configurações de Acessibilidade UFOP.
 *
 * Oferece perfil rápido para leitor de tela (TalkBack), seletor de escala de fonte com
 * prévia ao vivo, controles refinados para alto contraste WCAG, texto em negrito,
 * redução de movimentos e ampliação de alvos de toque com feedback tátil e semântico.
 */
@Composable
fun AccessibilitySettingsPanel(
    settings: CampusAccessibilitySettings,
    onUpdateSettings: (CampusAccessibilitySettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val rowMinHeight = if (settings.largeTouchTargets) 56.dp else 48.dp

    val accessibleSwitchColors = SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        uncheckedBorderColor = MaterialTheme.colorScheme.outline
    )

    val isTalkBackProfileActive = settings.screenReaderOptimized &&
        settings.largeTouchTargets &&
        settings.reduceMotion

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Card Destaque: Perfil Rápido TalkBack ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    role = Role.Switch
                    contentDescription = "Perfil rápido para Leitor de Tela TalkBack. " +
                        if (isTalkBackProfileActive) "Ativado." else "Desativado."
                },
            colors = CardDefaults.cardColors(
                containerColor = if (isTalkBackProfileActive) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = if (settings.highContrastMode) 2.dp else 1.dp,
                color = if (isTalkBackProfileActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val willActivate = !isTalkBackProfileActive
                        onUpdateSettings(
                            settings.copy(
                                screenReaderOptimized = willActivate,
                                largeTouchTargets = willActivate,
                                reduceMotion = willActivate
                            )
                        )
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (settings.largeTouchTargets) 48.dp else 40.dp)
                            .background(
                                color = if (isTalkBackProfileActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Hearing,
                            contentDescription = null,
                            tint = if (isTalkBackProfileActive) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "Perfil rápido para Leitor de Tela (TalkBack)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isTalkBackProfileActive) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Ativa em 1 toque: descrições expandidas, áreas de toque de 56dp e redução de animações.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isTalkBackProfileActive) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Switch(
                    checked = isTalkBackProfileActive,
                    onCheckedChange = { willActivate ->
                        onUpdateSettings(
                            settings.copy(
                                screenReaderOptimized = willActivate,
                                largeTouchTargets = willActivate,
                                reduceMotion = willActivate
                            )
                        )
                    },
                    colors = accessibleSwitchColors,
                    modifier = Modifier.clearAndSetSemantics { }
                )
            }
        }

        // --- 2. Seção: Tamanho de Texto ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = if (settings.highContrastMode) 2.dp else 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FormatSize,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Tamanho de Texto",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Ajuste o multiplicador de fontes do aplicativo sem alterar o tamanho do sistema operacional.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Prévia ao vivo do texto
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Prévia de Legibilidade:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Campus Morro do Cruzeiro · Sala 204",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = (16 * settings.fontScaleMultiplier).sp,
                                lineHeight = (22 * settings.fontScaleMultiplier).sp
                            ),
                            fontWeight = if (settings.boldTextBoost) FontWeight.Bold else FontWeight.SemiBold
                        )
                        Text(
                            text = "Aulas e horários com alta legibilidade no aplicativo.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = (14 * settings.fontScaleMultiplier).sp,
                                lineHeight = (20 * settings.fontScaleMultiplier).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Seletor de 4 passos
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FONT_SCALE_STEPS.chunked(2).forEach { rowSteps ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowSteps.forEach { step ->
                                val isSelected = kotlin.math.abs(settings.fontScaleMultiplier - step.scale) < 0.05f
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = rowMinHeight)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            onUpdateSettings(settings.copy(fontScaleMultiplier = step.scale))
                                        }
                                        .semantics(mergeDescendants = true) {
                                            role = Role.RadioButton
                                            contentDescription = "${step.label}. ${if (isSelected) "Selecionado." else ""}"
                                        },
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                    border = BorderStroke(
                                        width = if (isSelected || settings.highContrastMode) 2.dp else 1.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        }
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Outlined.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = step.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 3. Seção: Ajustes Individuais de Acessibilidade ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = if (settings.highContrastMode) 2.dp else 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // Switch 1: Leitor de Tela (TalkBack)
                AccessibilitySwitchRow(
                    icon = Icons.Outlined.Hearing,
                    title = "Otimizar para leitor de tela (TalkBack)",
                    subtitle = "Descrições semânticas detalhadas em horários, botões e listas para o TalkBack.",
                    checked = settings.screenReaderOptimized,
                    onCheckedChange = { onUpdateSettings(settings.copy(screenReaderOptimized = it)) },
                    switchColors = accessibleSwitchColors,
                    minHeight = rowMinHeight
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Switch 2: Alto Contraste WCAG
                AccessibilitySwitchRow(
                    icon = Icons.Outlined.Contrast,
                    title = "Alto contraste WCAG",
                    subtitle = "Reforça bordas nítidas e contraste preto/branco para proporção de até 7:1.",
                    checked = settings.highContrastMode,
                    onCheckedChange = { onUpdateSettings(settings.copy(highContrastMode = it)) },
                    switchColors = accessibleSwitchColors,
                    minHeight = rowMinHeight
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Switch 3: Texto em Negrito Reforçado
                AccessibilitySwitchRow(
                    icon = Icons.Outlined.FormatBold,
                    title = "Texto em negrito reforçado",
                    subtitle = "Aumenta o peso de títulos e parágrafos facilitando a leitura em qualquer tela.",
                    checked = settings.boldTextBoost,
                    onCheckedChange = { onUpdateSettings(settings.copy(boldTextBoost = it)) },
                    switchColors = accessibleSwitchColors,
                    minHeight = rowMinHeight
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Switch 4: Reduzir Animações e Movimentos
                AccessibilitySwitchRow(
                    icon = Icons.Outlined.MotionPhotosOff,
                    title = "Reduzir animações e movimentos",
                    subtitle = "Elimina transições aceleradas e rolagem contínua para prevenir tontura.",
                    checked = settings.reduceMotion,
                    onCheckedChange = { onUpdateSettings(settings.copy(reduceMotion = it)) },
                    switchColors = accessibleSwitchColors,
                    minHeight = rowMinHeight
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Switch 5: Áreas de Toque Ampliadas (56dp)
                AccessibilitySwitchRow(
                    icon = Icons.Outlined.TouchApp,
                    title = "Áreas de toque ampliadas (mín. 56dp)",
                    subtitle = "Amplia alvos interativos facilitando o toque de mãos trêmulas ou telas menores.",
                    checked = settings.largeTouchTargets,
                    onCheckedChange = { onUpdateSettings(settings.copy(largeTouchTargets = it)) },
                    switchColors = accessibleSwitchColors,
                    minHeight = rowMinHeight
                )
            }
        }

        // --- 4. Botão Restaurar Padrão ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            OutlinedButton(
                onClick = { onUpdateSettings(CampusAccessibilitySettings()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = rowMinHeight)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Restaurar padrão de acessibilidade"
                    },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Restaurar padrão de acessibilidade",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun AccessibilitySwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchColors: androidx.compose.material3.SwitchColors,
    minHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .clickable { onCheckedChange(!checked) }
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                contentDescription = "$title. $subtitle. ${if (checked) "Ativado." else "Desativado."}"
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = switchColors,
            modifier = Modifier.clearAndSetSemantics { }
        )
    }
}
