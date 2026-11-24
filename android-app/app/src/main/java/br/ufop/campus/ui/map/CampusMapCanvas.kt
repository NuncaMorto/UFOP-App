package br.ufop.campus.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ufop.campus.core.map.CampusMapController
import br.ufop.campus.core.map.CampusMapMath
import br.ufop.campus.core.map.MapRect2D
import br.ufop.campus.core.map.MapViewportTransform
import br.ufop.campus.core.uniffi.MapLayer
import br.ufop.campus.core.uniffi.RenderBatch
import br.ufop.campus.core.uniffi.RenderFeature
import br.ufop.campus.core.uniffi.RenderGeomKind
import br.ufop.campus.core.uniffi.RenderOccupancyKind
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController

private data class CachedComposePathItem(
    val feature: RenderFeature,
    val path: Path
)

/**
 * Componente Canvas 2D offline nativo para renderização vetorial dos campi UFOP.
 * Pan e pinch-to-zoom atuam exclusivamente sobre a matriz local sem cruzar FFI por frame.
 */
@Composable
fun CampusMapCanvas(
    controller: CampusMapController,
    modifier: Modifier = Modifier
) {
    val uiState by controller.state.collectAsState()
    val isDark = LocalCampusThemeController.current.isDark
    val a11y = LocalCampusAccessibility.current.settings
    val palette = remember(isDark, a11y.highContrastMode) {
        MapCanvasPalette.resolve(isDark = isDark, highContrast = a11y.highContrastMode)
    }

    val density = LocalDensity.current.density
    val textMeasurer = rememberTextMeasurer()
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var transform by remember { mutableStateOf(MapViewportTransform()) }
    var hasInitialFit by remember { mutableStateOf(false) }

    val focusAnimProgress = remember { Animatable(0f) }

    val batch = uiState.renderBatch
    val cachedPaths = remember(batch) {
        if (batch == null) {
            emptyList()
        } else {
            batch.items.map { item ->
                CachedComposePathItem(
                    feature = item,
                    path = buildMetricPath(item, batch)
                )
            }
        }
    }

    LaunchedEffect(batch, viewportSize) {
        if (!hasInitialFit && batch != null && viewportSize.width > 0 && viewportSize.height > 0) {
            transform = CampusMapMath.computeCameraTarget(
                bounds = MapRect2D(
                    minX = batch.bounds.minX,
                    minY = batch.bounds.minY,
                    maxX = batch.bounds.maxX,
                    maxY = batch.bounds.maxY
                ),
                viewportWidthPx = viewportSize.width.toFloat(),
                viewportHeightPx = viewportSize.height.toFloat(),
                density = density
            )
            hasInitialFit = true
        }
    }

    LaunchedEffect(uiState.pendingFocus, viewportSize) {
        val focus = uiState.pendingFocus ?: return@LaunchedEffect
        if (viewportSize.width <= 0 || viewportSize.height <= 0) return@LaunchedEffect

        val target = CampusMapMath.computeCameraTarget(
            bounds = MapRect2D(
                minX = focus.bounds.minX,
                minY = focus.bounds.minY,
                maxX = focus.bounds.maxX,
                maxY = focus.bounds.maxY
            ),
            viewportWidthPx = viewportSize.width.toFloat(),
            viewportHeightPx = viewportSize.height.toFloat(),
            density = density
        )
        if (a11y.reduceMotion) {
            transform = target
        } else {
            val start = transform
            focusAnimProgress.snapTo(0f)
            focusAnimProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
            ) {
                val t = value
                transform = MapViewportTransform(
                    offsetX = start.offsetX + (target.offsetX - start.offsetX) * t,
                    offsetY = start.offsetY + (target.offsetY - start.offsetY) * t,
                    zoomDpPerMeter = start.zoomDpPerMeter + (target.zoomDpPerMeter - start.zoomDpPerMeter) * t
                )
            }
            transform = target
        }
        controller.consumePendingFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
                .pointerInput(density) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        transform = CampusMapMath.applyPanAndZoom(
                            current = transform,
                            panDeltaX = pan.x,
                            panDeltaY = pan.y,
                            zoomFactor = zoom,
                            centroidX = centroid.x,
                            centroidY = centroid.y,
                            density = density
                        )
                    }
                }
                .pointerInput(density) {
                    detectTapGestures { offset ->
                        val mapPt = CampusMapMath.screenToMap(
                            screenX = offset.x,
                            screenY = offset.y,
                            transform = transform,
                            density = density
                        )
                        controller.handleTap(
                            mapX = mapPt.x,
                            mapY = mapPt.y,
                            zoom = transform.zoomDpPerMeter
                        )
                    }
                }
        ) {
            val zoomDpPerM = CampusMapMath.clampZoom(transform.zoomDpPerMeter)
            val pxPerMeter = zoomDpPerM * density
            val selectedId = uiState.selectedFeature?.id

            withTransform({
                translate(left = transform.offsetX, top = transform.offsetY)
                scale(scaleX = pxPerMeter, scaleY = pxPerMeter, pivot = Offset.Zero)
            }) {
                cachedPaths.forEach { entry ->
                    val item = entry.feature
                    if (zoomDpPerM < item.minZoom) return@forEach
                    val isSelected = item.id == selectedId

                    when (item.geomKind) {
                        RenderGeomKind.POLYGON -> {
                            drawPath(
                                path = entry.path,
                                color = fillColorFor(item, isSelected, palette),
                                style = Fill
                            )
                            val strokePx = if (isSelected) 2.5f * density else 1.2f * density
                            val strokeColor = if (isSelected) palette.selectedStroke else strokeColorFor(item, palette)
                            val dashEffect = if (item.isMock && item.layer == MapLayer.ROOM) {
                                PathEffect.dashPathEffect(
                                    floatArrayOf(5f * density / pxPerMeter, 3.5f * density / pxPerMeter),
                                    0f
                                )
                            } else {
                                null
                            }
                            drawPath(
                                path = entry.path,
                                color = strokeColor,
                                style = Stroke(
                                    width = strokePx / pxPerMeter,
                                    pathEffect = dashEffect
                                )
                            )
                        }
                        RenderGeomKind.POLYLINE -> {
                            drawPath(
                                path = entry.path,
                                color = palette.walkwayStroke,
                                style = Stroke(
                                    width = maxOf(1.4f, density / pxPerMeter),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                        RenderGeomKind.POINT -> {
                            val radiusM = (if (isSelected) 7.5f else 5.5f) * density / pxPerMeter
                            val center = Offset(item.pointX, item.pointY)
                            drawCircle(
                                color = palette.poiFill,
                                radius = radiusM,
                                center = center
                            )
                            drawCircle(
                                color = Color.White,
                                radius = radiusM,
                                center = center,
                                style = Stroke(width = 1.5f * density / pxPerMeter)
                            )
                        }
                    }
                }
            }

            // Rótulos em tamanho fixo em tela (sp), ocultando secundários quando menores que o polígono.
            val buildingStyle = TextStyle(
                color = palette.labelColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            val roomStyle = TextStyle(
                color = palette.labelColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            val secondaryStyle = TextStyle(
                color = palette.secondaryLabelColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal
            )

            cachedPaths.forEach { entry ->
                val item = entry.feature
                if (zoomDpPerM < item.minZoom) return@forEach
                val isLabeledLayer = item.layer == MapLayer.BUILDING ||
                    item.layer == MapLayer.CAMPUS_ZONE ||
                    item.layer == MapLayer.ROOM ||
                    item.layer == MapLayer.POI
                if (!isLabeledLayer || item.primaryLabel.isBlank()) return@forEach

                val style = if (item.layer == MapLayer.ROOM) roomStyle else buildingStyle
                val measured = textMeasurer.measure(text = item.primaryLabel, style = style)

                if (item.layer == MapLayer.ROOM) {
                    val fits = CampusMapMath.shouldShowSecondaryLabel(
                        bboxMinX = item.bboxMinX,
                        bboxMaxX = item.bboxMaxX,
                        zoomDpPerMeter = zoomDpPerM,
                        density = density,
                        textWidthPx = measured.size.width.toFloat()
                    )
                    if (!fits) return@forEach
                }

                val screenPt = CampusMapMath.mapToScreen(
                    mapX = item.labelX,
                    mapY = item.labelY,
                    transform = transform,
                    density = density
                )
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        x = screenPt.x - measured.size.width * 0.5f,
                        y = screenPt.y - measured.size.height * 0.5f
                    )
                )

                if (item.layer == MapLayer.BUILDING && item.secondaryLabel.isNotBlank()) {
                    val secMeasured = textMeasurer.measure(text = item.secondaryLabel, style = secondaryStyle)
                    if (CampusMapMath.shouldShowSecondaryLabel(
                            bboxMinX = item.bboxMinX,
                            bboxMaxX = item.bboxMaxX,
                            zoomDpPerMeter = zoomDpPerM,
                            density = density,
                            textWidthPx = secMeasured.size.width.toFloat()
                        )
                    ) {
                        drawText(
                            textLayoutResult = secMeasured,
                            topLeft = Offset(
                                x = screenPt.x - secMeasured.size.width * 0.5f,
                                y = screenPt.y + measured.size.height * 0.55f
                            )
                        )
                    }
                }
            }
        }

        val floorItems = remember(uiState.activeCampusId, uiState.activeFloor, uiState.selectedFeature, batch) {
            controller.availableFloorsForUi()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            if (floorItems.isNotEmpty()) {
                FloorSelectorBar(
                    floors = floorItems,
                    activeFloor = uiState.activeFloor,
                    onSelectFloor = controller::selectFloor
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            val selected = uiState.selectedFeature
            if (selected != null) {
                SelectedFeatureSheetCard(
                    feature = selected,
                    occupancy = uiState.selectedOccupancy,
                    isDark = isDark,
                    onDismiss = controller::clearSelection
                )
            } else if (!uiState.isNativeAvailable) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f)
                ) {
                    Text(
                        text = uiState.errorMessage ?: "Motor vetorial offline pronto para carga nativa.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun buildMetricPath(item: RenderFeature, batch: RenderBatch): Path {
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
    }
    if (item.geomKind == RenderGeomKind.POINT) return path

    val startRing = item.ringStartIdx.toInt()
    val endRing = minOf(startRing + item.ringCount.toInt(), batch.ringStarts.size)
    if (startRing >= endRing) return path

    for (rIdx in startRing until endRing) {
        val vStart = batch.ringStarts[rIdx].toInt()
        val vCount = batch.ringCounts[rIdx].toInt()
        if (vCount < 2 || (vStart + vCount) * 2 > batch.coords.size) continue

        path.moveTo(batch.coords[vStart * 2], batch.coords[vStart * 2 + 1])
        for (k in 1 until vCount) {
            path.lineTo(batch.coords[(vStart + k) * 2], batch.coords[(vStart + k) * 2 + 1])
        }
        if (item.geomKind == RenderGeomKind.POLYGON) {
            path.close()
        }
    }
    return path
}

private fun fillColorFor(item: RenderFeature, isSelected: Boolean, palette: MapCanvasPalette): Color {
    if (isSelected && (item.layer == MapLayer.ROOM || item.layer == MapLayer.BUILDING || item.layer == MapLayer.CAMPUS_ZONE)) {
        return palette.selectedFill
    }
    return when (item.layer) {
        MapLayer.CAMPUS_GROUND -> palette.campusGroundFill
        MapLayer.CAMPUS_ZONE -> palette.campusZoneFill
        MapLayer.GREEN_AREA -> palette.greenAreaFill
        MapLayer.PARKING -> palette.parkingFill
        MapLayer.SPORTS -> palette.sportsFill
        MapLayer.WALKWAY -> Color.Transparent
        MapLayer.BUILDING -> palette.buildingFill
        MapLayer.ROOM -> when (item.occupancy) {
            RenderOccupancyKind.OCCUPIED -> palette.roomOccupiedFill
            RenderOccupancyKind.NO_REGULAR_CLASS -> palette.roomFreeFill
            RenderOccupancyKind.NO_DATA -> palette.roomNoDataFill
            RenderOccupancyKind.OUT_OF_TERM -> palette.roomOutOfTermFill
            RenderOccupancyKind.NONE -> palette.buildingFill
        }
        MapLayer.POI -> palette.poiFill
    }
}

private fun strokeColorFor(item: RenderFeature, palette: MapCanvasPalette): Color = when (item.layer) {
    MapLayer.CAMPUS_GROUND -> palette.campusGroundStroke
    MapLayer.CAMPUS_ZONE -> palette.campusZoneStroke
    MapLayer.BUILDING -> palette.buildingStroke
    MapLayer.ROOM -> palette.roomStroke
    else -> palette.buildingStroke.copy(alpha = 0.45f)
}
