package br.ufop.campus.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import br.ufop.campus.ui.theme.LocalCampusAccessibility

enum class TourSpotlightMode {
    TAB,
    TOP_BANNER
}

private data class GuidedTourStep(
    val targetTab: CampusTab,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val spotlightMode: TourSpotlightMode = TourSpotlightMode.TAB
)

private val GUIDED_TOUR_STEPS = listOf(
    GuidedTourStep(
        targetTab = CampusTab.AGENDA,
        title = "Sua Agenda do Dia",
        description = "Acompanhe as aulas de hoje, horários, salas ou marcação EAD, clima do campus e faça check-in rápido de presença, falta ou aula cancelada.",
        icon = Icons.Default.DateRange,
        spotlightMode = TourSpotlightMode.TAB
    ),
    GuidedTourStep(
        targetTab = CampusTab.DISCIPLINES,
        title = "Disciplinas & Grade Horária",
        description = "Gerencie suas matérias do semestre, controle o limite de faltas, importe Planos de Ensino em PDF e abra sua grade semanal completa.",
        icon = Icons.AutoMirrored.Filled.List,
        spotlightMode = TourSpotlightMode.TAB
    ),
    GuidedTourStep(
        targetTab = CampusTab.ACTIVITIES,
        title = "Atividades, Provas & Prazos",
        description = "Veja todas as provas, trabalhos e entregas reunidos por data, crie lembretes rápidos e marque o que já foi concluído.",
        icon = Icons.Default.CheckCircle,
        spotlightMode = TourSpotlightMode.TAB
    ),
    GuidedTourStep(
        targetTab = CampusTab.PROFILE,
        title = "Perfil, CRE & Matriz UFOP",
        description = "Acompanhe seu CRE, horas integralizadas, Histórico Escolar e a Matriz do seu curso, além de personalizar cores e tema.",
        icon = Icons.Default.Person,
        spotlightMode = TourSpotlightMode.TAB
    ),
    GuidedTourStep(
        targetTab = CampusTab.PROFILE,
        title = "Configurações, Segurança & FAQ",
        description = "No Perfil você também configura bloqueio por senha/biometria, backup criptografado, acessibilidade, Notas de Atualização e pode rever este tour quando quiser!",
        icon = Icons.Outlined.Shield,
        spotlightMode = TourSpotlightMode.TOP_BANNER
    )
)

@Composable
fun GuidedTourOverlay(
    tabBounds: Map<CampusTab, Rect>,
    onSelectTab: (CampusTab) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val a11y = LocalCampusAccessibility.current.settings
    val density = LocalDensity.current
    var currentStepIndex by rememberSaveable { mutableIntStateOf(0) }
    var overlayRootOffset by remember { mutableStateOf(Offset.Zero) }
    var rootCanvasSize by remember { mutableStateOf(IntSize.Zero) }
    var hasInitialRect by remember { mutableStateOf(false) }

    val safeStepIndex = currentStepIndex.coerceIn(0, GUIDED_TOUR_STEPS.lastIndex)
    val currentStep = GUIDED_TOUR_STEPS[safeStepIndex]
    val totalSteps = GUIDED_TOUR_STEPS.size
    val isLastStep = safeStepIndex == GUIDED_TOUR_STEPS.lastIndex

    LaunchedEffect(safeStepIndex) {
        onSelectTab(currentStep.targetTab)
    }

    BackHandler(enabled = true) {
        if (safeStepIndex > 0) {
            currentStepIndex = safeStepIndex - 1
        } else {
            onFinish()
        }
    }

    val statusBarPx = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }
    val rawTabRect = tabBounds[currentStep.targetTab]

    val localTargetRect = remember(currentStep.spotlightMode, rawTabRect, overlayRootOffset, rootCanvasSize, statusBarPx) {
        when (currentStep.spotlightMode) {
            TourSpotlightMode.TAB -> {
                rawTabRect?.translate(-overlayRootOffset.x, -overlayRootOffset.y) ?: Rect.Zero
            }
            TourSpotlightMode.TOP_BANNER -> {
                val width = rootCanvasSize.width.toFloat()
                if (width > 0f) {
                    val padPx = with(density) { 20.dp.toPx() }
                    val topPx = statusBarPx + with(density) { 18.dp.toPx() }
                    val bottomPx = statusBarPx + with(density) { 132.dp.toPx() }
                    Rect(
                        left = padPx,
                        top = topPx,
                        right = (width - padPx).coerceAtLeast(padPx),
                        bottom = bottomPx
                    )
                } else {
                    Rect.Zero
                }
            }
        }
    }

    LaunchedEffect(localTargetRect) {
        if (localTargetRect.width > 1f && !hasInitialRect) {
            hasInitialRect = true
        }
    }

    val animatedSpotlightRect by animateRectAsState(
        targetValue = localTargetRect,
        animationSpec = if (a11y.reduceMotion || !hasInitialRect) {
            snap()
        } else {
            tween(durationMillis = 320, easing = FastOutSlowInEasing)
        },
        label = "GuidedTourSpotlightRect"
    )

    val spotlightRingColor = MaterialTheme.colorScheme.primary
    val arrowFillColor = MaterialTheme.colorScheme.surface
    val buttonMinHeight = if (a11y.largeTouchTargets) 56.dp else 48.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                overlayRootOffset = coords.positionInRoot()
                rootCanvasSize = coords.size
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* Intercepta toques fora do cartão durante o tour */ }
            )
    ) {
        // Spotlight recortado com CompositingStrategy.Offscreen + BlendMode.Clear
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {
            drawRect(color = Color.Black.copy(alpha = 0.74f))

            if (animatedSpotlightRect.width > 1f && animatedSpotlightRect.height > 1f) {
                val isTopBanner = currentStep.spotlightMode == TourSpotlightMode.TOP_BANNER
                val padPx = if (isTopBanner) 0f else 6.dp.toPx()
                val left = (animatedSpotlightRect.left - padPx).coerceAtLeast(0f)
                val top = (animatedSpotlightRect.top - padPx).coerceAtLeast(0f)
                val width = (animatedSpotlightRect.width + padPx * 2f)
                    .coerceAtMost(size.width - left)
                val height = (animatedSpotlightRect.height + padPx * 2f)
                    .coerceAtMost(size.height - top)
                val cornerRadius = if (isTopBanner) {
                    CornerRadius(16.dp.toPx(), 16.dp.toPx())
                } else {
                    CornerRadius(height / 2f, height / 2f)
                }

                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = cornerRadius,
                    blendMode = BlendMode.Clear
                )

                drawRoundRect(
                    color = spotlightRingColor,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = cornerRadius,
                    style = Stroke(width = 2.5.dp.toPx())
                )

                // Seta apontando apenas no modo TAB (em direção à barra inferior de abas)
                if (currentStep.spotlightMode == TourSpotlightMode.TAB) {
                    val arrowTipX = (left + width / 2f).coerceIn(36.dp.toPx(), size.width - 36.dp.toPx())
                    val arrowTipY = (top - 8.dp.toPx()).coerceAtLeast(0f)
                    val arrowHalfWidth = 10.dp.toPx()
                    val arrowHeight = 10.dp.toPx()
                    val triangle = Path().apply {
                        moveTo(arrowTipX, arrowTipY)
                        lineTo(arrowTipX - arrowHalfWidth, arrowTipY - arrowHeight)
                        lineTo(arrowTipX + arrowHalfWidth, arrowTipY - arrowHeight)
                        close()
                    }
                    drawPath(path = triangle, color = arrowFillColor)
                }
            }
        }

        // Cartão explicativo flutuante acima da barra de navegação
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 104.dp)
                .fillMaxWidth()
                .widthIn(max = 460.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "Tour guiado, etapa ${safeStepIndex + 1} de $totalSteps: ${currentStep.title}. ${currentStep.description}"
                    },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 14.dp,
                border = BorderStroke(
                    width = if (a11y.highContrastMode) 2.dp else 1.2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Linha superior: indicador de etapa + botão Pular
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${safeStepIndex + 1} de $totalSteps",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                repeat(totalSteps) { index ->
                                    val active = index == safeStepIndex
                                    Box(
                                        modifier = Modifier
                                            .size(
                                                width = if (active) 16.dp else 6.dp,
                                                height = 6.dp
                                            )
                                            .clip(CircleShape)
                                            .background(
                                                if (active) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outlineVariant
                                                }
                                            )
                                    )
                                }
                            }
                        }

                        TextButton(
                            onClick = onFinish,
                            modifier = Modifier
                                .defaultMinSize(minHeight = buttonMinHeight)
                                .semantics {
                                    contentDescription = "Pular tour guiado"
                                }
                        ) {
                            Text(
                                text = "Pular",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Conteúdo animado da etapa
                    AnimatedContent(
                        targetState = safeStepIndex,
                        transitionSpec = {
                            if (a11y.reduceMotion) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                val direction = if (targetState >= initialState) 1 else -1
                                (fadeIn(tween(220)) + slideInHorizontally(tween(240)) { it / 6 * direction })
                                    .togetherWith(
                                        fadeOut(tween(180)) + slideOutHorizontally(tween(200)) { -it / 6 * direction }
                                    )
                            }
                        },
                        label = "GuidedTourStepContent"
                    ) { stepIdx ->
                        val step = GUIDED_TOUR_STEPS[stepIdx]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = step.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = step.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.semantics { heading() }
                                )
                                Text(
                                    text = step.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Botões Anterior e Próximo / Concluir
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (safeStepIndex > 0) {
                            OutlinedButton(
                                onClick = { currentStepIndex = safeStepIndex - 1 },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = buttonMinHeight)
                                    .semantics {
                                        contentDescription = "Voltar para etapa anterior do tour"
                                    },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Anterior",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(0.6f))
                        }

                        Button(
                            onClick = {
                                if (isLastStep) {
                                    onFinish()
                                } else {
                                    currentStepIndex = safeStepIndex + 1
                                }
                            },
                            modifier = Modifier
                                .weight(1.2f)
                                .defaultMinSize(minHeight = buttonMinHeight)
                                .semantics {
                                    contentDescription = if (isLastStep) {
                                        "Concluir tour guiado"
                                    } else {
                                        "Avançar para a próxima etapa do tour"
                                    }
                                },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = if (isLastStep) "Concluir" else "Próximo",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = if (isLastStep) {
                                    Icons.Default.Check
                                } else {
                                    Icons.AutoMirrored.Filled.ArrowForward
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
