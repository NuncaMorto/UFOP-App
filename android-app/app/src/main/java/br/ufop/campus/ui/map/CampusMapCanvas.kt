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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.map.CampusMapController
import br.ufop.campus.core.map.CampusMapMath
import br.ufop.campus.core.map.LabelCollisionBuffer
import br.ufop.campus.core.map.MapRect2D
import br.ufop.campus.core.map.MapViewportTransform
import br.ufop.campus.core.uniffi.MapLayer
import br.ufop.campus.core.uniffi.PoiType
import br.ufop.campus.core.uniffi.RenderBatch
import br.ufop.campus.core.uniffi.RenderGeomKind
import br.ufop.campus.core.uniffi.RenderOccupancyKind
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController

/**
 * Componente Canvas 2D offline nativo para renderização vetorial dos campi UFOP.
 * Pan e pinch-to-zoom atuam exclusivamente sobre a matriz local sem cruzar FFI por frame,
 * com culling de viewport, pré-medição de rótulos e cache de geometria separado da ocupação.
 */
@Composable
fun CampusMapCanvas(
    controller: CampusMapController,
    modifier: Modifier = Modifier,
    showBuiltInHud: Boolean = true,
    topHudInsetDp: Dp = 0.dp,
    bottomHudInsetDp: Dp = 0.dp,
    startHudInsetDp: Dp = 0.dp
) {
    val canvasState by controller.canvasState.collectAsState()
    val isDark = LocalCampusThemeController.current.isDark
    val a11y = LocalCampusAccessibility.current.settings
    val palette = remember(isDark, a11y.highContrastMode) {
        MapCanvasPalette.resolve(isDark = isDark, highContrast = a11y.highContrastMode)
    }
    val drawColors = remember(palette, isDark) {
        CampusMapDrawColors.resolve(palette = palette, isDark = isDark)
    }

    val densityConfig = LocalDensity.current
    val density = densityConfig.density
    val fontScale = densityConfig.fontScale
    val textMeasurer = rememberTextMeasurer()
    val strokeCache = remember(density) { CampusMapStrokeCache(density) }
    val collisionBuffer = remember { LabelCollisionBuffer() }

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var transform by remember { mutableStateOf(MapViewportTransform()) }
    var hasInitialFit by remember { mutableStateOf(false) }

    val focusAnimProgress = remember { Animatable(0f) }

    val batch = canvasState.renderBatch
    val geometryCache = remember(canvasState.geometryRevision) {
        batch?.let(CampusMapGeometryCache::build)
    }
    val labelCache = remember(geometryCache, palette, density, fontScale) {
        if (geometryCache != null && batch != null) {
            CampusMapLabelCache.build(batch = batch, palette = palette, textMeasurer = textMeasurer)
        } else {
            null
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
                density = density,
                topInsetDp = topHudInsetDp.value,
                bottomInsetDp = bottomHudInsetDp.value,
                startInsetDp = startHudInsetDp.value
            )
            hasInitialFit = true
        }
    }

    LaunchedEffect(canvasState.pendingFocus, viewportSize) {
        val focus = canvasState.pendingFocus ?: return@LaunchedEffect
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
        hasInitialFit = true
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
                        controller.onMapTap(
                            mapX = mapPt.x,
                            mapY = mapPt.y,
                            zoom = transform.zoomDpPerMeter
                        )
                    }
                }
        ) {
            val geo = geometryCache ?: return@Canvas
            val zoomDpPerM = CampusMapMath.clampZoom(transform.zoomDpPerMeter)
            val pxPerMeter = zoomDpPerM * density
            val selectedId = canvasState.selectedFeatureId
            val isFreeRoomsMode = canvasState.isFreeRoomsModeEnabled
            val activePoiFilter = canvasState.activePoiFilter
            val visibleRect = CampusMapMath.visibleMapRect(
                viewportWidthPx = size.width,
                viewportHeightPx = size.height,
                transform = transform,
                density = density,
                marginPx = 48f * density
            )

            strokeCache.updateIfNeeded(pxPerMeter)

            withTransform({
                translate(left = transform.offsetX, top = transform.offsetY)
                scale(scaleX = pxPerMeter, scaleY = pxPerMeter, pivot = Offset.Zero)
            }) {
                geo.groundBatch?.let { ground ->
                    drawStaticBatch(
                        batch = ground,
                        visibleRect = visibleRect,
                        activePoiFilter = activePoiFilter,
                        palette = palette,
                        colors = drawColors,
                        strokes = strokeCache
                    )
                }
                drawDynamicList(
                    items = geo.groundOverflow,
                    renderBatch = batch,
                    visibleRect = visibleRect,
                    zoomDpPerM = zoomDpPerM,
                    pxPerMeter = pxPerMeter,
                    density = density,
                    selectedId = selectedId,
                    isFreeRoomsMode = isFreeRoomsMode,
                    activePoiFilter = activePoiFilter,
                    palette = palette,
                    colors = drawColors,
                    strokes = strokeCache
                )

                drawDynamicList(
                    items = geo.campusZones,
                    renderBatch = batch,
                    visibleRect = visibleRect,
                    zoomDpPerM = zoomDpPerM,
                    pxPerMeter = pxPerMeter,
                    density = density,
                    selectedId = selectedId,
                    isFreeRoomsMode = isFreeRoomsMode,
                    activePoiFilter = activePoiFilter,
                    palette = palette,
                    colors = drawColors,
                    strokes = strokeCache
                )

                for (staticLayer in geo.overlayStaticBatches) {
                    drawStaticBatch(
                        batch = staticLayer,
                        visibleRect = visibleRect,
                        activePoiFilter = activePoiFilter,
                        palette = palette,
                        colors = drawColors,
                        strokes = strokeCache
                    )
                }
                drawDynamicList(
                    items = geo.overlayStaticOverflow,
                    renderBatch = batch,
                    visibleRect = visibleRect,
                    zoomDpPerM = zoomDpPerM,
                    pxPerMeter = pxPerMeter,
                    density = density,
                    selectedId = selectedId,
                    isFreeRoomsMode = isFreeRoomsMode,
                    activePoiFilter = activePoiFilter,
                    palette = palette,
                    colors = drawColors,
                    strokes = strokeCache
                )

                val walkwayPath = geo.walkwayPath
                val walkwayBounds = geo.walkwayBounds
                if (walkwayPath != null &&
                    walkwayBounds != null &&
                    CampusMapMath.rectsIntersect(walkwayBounds, visibleRect)
                ) {
                    drawPath(
                        path = walkwayPath,
                        color = palette.walkwayStroke,
                        style = strokeCache.walkwayStroke
                    )
                }
                drawDynamicList(
                    items = geo.walkwayOverflow,
                    renderBatch = batch,
                    visibleRect = visibleRect,
                    zoomDpPerM = zoomDpPerM,
                    pxPerMeter = pxPerMeter,
                    density = density,
                    selectedId = selectedId,
                    isFreeRoomsMode = isFreeRoomsMode,
                    activePoiFilter = activePoiFilter,
                    palette = palette,
                    colors = drawColors,
                    strokes = strokeCache
                )

                drawDynamicList(
                    items = geo.foregroundItems,
                    renderBatch = batch,
                    visibleRect = visibleRect,
                    zoomDpPerM = zoomDpPerM,
                    pxPerMeter = pxPerMeter,
                    density = density,
                    selectedId = selectedId,
                    isFreeRoomsMode = isFreeRoomsMode,
                    activePoiFilter = activePoiFilter,
                    palette = palette,
                    colors = drawColors,
                    strokes = strokeCache
                )
            }

            // Rótulos pré-medidos fora do loop com culling de viewport e evitação simples de colisão (AARB).
            if (labelCache != null) {
                drawCachedLabels(
                    labelCache = labelCache,
                    collisionBuffer = collisionBuffer,
                    visibleRect = visibleRect,
                    transform = transform,
                    zoomDpPerM = zoomDpPerM,
                    density = density,
                    selectedId = selectedId
                )
            }
        }

        if (showBuiltInHud) {
            val bottomState by controller.bottomHudState.collectAsState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                if (bottomState.availableFloors.isNotEmpty()) {
                    FloorSelectorBar(
                        floors = bottomState.availableFloors,
                        activeFloor = bottomState.activeFloor,
                        onSelectFloor = controller::selectFloor
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                val selected = bottomState.selectedFeature
                if (selected != null) {
                    SelectedFeatureSheetCard(
                        feature = selected,
                        occupancy = bottomState.selectedOccupancy,
                        isDark = isDark,
                        onDismiss = controller::clearSelection
                    )
                } else if (!bottomState.isNativeAvailable && !bottomState.isLoading) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f)
                    ) {
                        Text(
                            text = bottomState.errorMessage ?: "Motor vetorial offline pronto para carga nativa.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawStaticBatch(
    batch: StaticPolygonLayerBatch,
    visibleRect: MapRect2D,
    activePoiFilter: PoiType?,
    palette: MapCanvasPalette,
    colors: CampusMapDrawColors,
    strokes: CampusMapStrokeCache
) {
    if (!CampusMapMath.rectsIntersect(batch.bounds, visibleRect)) return
    val isHighlightedParking = activePoiFilter == PoiType.PARKING && batch.layer == MapLayer.PARKING
    val fill = fillColorFor(
        layer = batch.layer,
        occupancy = RenderOccupancyKind.NONE,
        isSelected = false,
        palette = palette
    )
    drawPath(path = batch.path, color = fill, style = Fill)

    val strokeColor = if (isHighlightedParking) {
        palette.poiFill
    } else {
        strokeColorFor(layer = batch.layer, palette = palette, colors = colors)
    }
    drawPath(
        path = batch.path,
        color = strokeColor,
        style = if (isHighlightedParking) strokes.highlightStroke else strokes.normalStroke
    )
}

private fun DrawScope.drawDynamicList(
    items: List<CachedDynamicGeometry>,
    renderBatch: RenderBatch?,
    visibleRect: MapRect2D,
    zoomDpPerM: Float,
    pxPerMeter: Float,
    density: Float,
    selectedId: String?,
    isFreeRoomsMode: Boolean,
    activePoiFilter: PoiType?,
    palette: MapCanvasPalette,
    colors: CampusMapDrawColors,
    strokes: CampusMapStrokeCache
) {
    val batchItems = renderBatch?.items
    for (entry in items) {
        if (zoomDpPerM < entry.minZoom) continue

        if (entry.geomKind == RenderGeomKind.POINT) {
            val pointMarginM = (16f * density) / pxPerMeter.coerceAtLeast(0.05f)
            if (!CampusMapMath.pointInRect(entry.pointX, entry.pointY, visibleRect, pointMarginM)) {
                continue
            }
            val isSelected = entry.id == selectedId
            val matchesPoi = activePoiFilter != null &&
                matchesRenderPoiFilter(entry.layer, entry.poiType, entry.categoryLower, activePoiFilter)
            val dimmedPoi = activePoiFilter != null && entry.layer == MapLayer.POI && !matchesPoi
            val baseRadius = when {
                isSelected -> 7.5f
                matchesPoi -> 7.0f
                dimmedPoi -> 4.5f
                else -> 5.5f
            }
            val radiusM = baseRadius * density / pxPerMeter
            val center = Offset(entry.pointX, entry.pointY)
            if (matchesPoi) {
                drawCircle(
                    color = colors.poiHaloColor,
                    radius = radiusM * 1.75f,
                    center = center
                )
            }
            drawCircle(
                color = if (dimmedPoi) colors.dimmedPoiFill else palette.poiFill,
                radius = radiusM,
                center = center
            )
            drawCircle(
                color = if (dimmedPoi) colors.dimmedPoiStroke else Color.White,
                radius = radiusM,
                center = center,
                style = if (matchesPoi) strokes.poiHighlightStroke else strokes.poiNormalStroke
            )
            continue
        }

        if (!CampusMapMath.bboxIntersects(
                minX = entry.bboxMinX,
                minY = entry.bboxMinY,
                maxX = entry.bboxMaxX,
                maxY = entry.bboxMaxY,
                viewport = visibleRect
            )
        ) {
            continue
        }

        val path = entry.path ?: continue
        if (entry.geomKind == RenderGeomKind.POLYLINE) {
            drawPath(
                path = path,
                color = palette.walkwayStroke,
                style = strokes.walkwayStroke
            )
            continue
        }

        val isSelected = entry.id == selectedId
        val occupancy = batchItems?.getOrNull(entry.featureIndex)?.occupancy ?: RenderOccupancyKind.NONE
        val isHighlightedFreeRoom = isFreeRoomsMode &&
            entry.layer == MapLayer.ROOM &&
            occupancy == RenderOccupancyKind.NO_REGULAR_CLASS
        val isDimmedRoom = isFreeRoomsMode &&
            entry.layer == MapLayer.ROOM &&
            !isHighlightedFreeRoom &&
            !isSelected
        val isHighlightedPoiPolygon = activePoiFilter != null &&
            matchesRenderPoiFilter(entry.layer, entry.poiType, entry.categoryLower, activePoiFilter)

        val finalFill = when {
            isSelected && (entry.layer == MapLayer.ROOM || entry.layer == MapLayer.BUILDING || entry.layer == MapLayer.CAMPUS_ZONE) ->
                palette.selectedFill
            isHighlightedFreeRoom -> colors.roomFreeHighlightedFill
            isDimmedRoom -> dimmedRoomFillFor(occupancy, colors)
            else -> fillColorFor(entry.layer, occupancy, false, palette)
        }
        drawPath(path = path, color = finalFill, style = Fill)

        val strokeColor = when {
            isSelected -> palette.selectedStroke
            isHighlightedFreeRoom -> colors.freeHighlightColor
            isHighlightedPoiPolygon -> palette.poiFill
            isDimmedRoom -> colors.dimmedRoomStroke
            else -> strokeColorFor(entry.layer, palette, colors)
        }
        val strokeStyle = if (entry.isMock && entry.layer == MapLayer.ROOM) {
            when {
                isSelected -> strokes.mockSelectedStroke
                isHighlightedFreeRoom || isHighlightedPoiPolygon -> strokes.mockHighlightStroke
                else -> strokes.mockNormalStroke
            }
        } else {
            when {
                isSelected -> strokes.selectedStroke
                isHighlightedFreeRoom || isHighlightedPoiPolygon -> strokes.highlightStroke
                else -> strokes.normalStroke
            }
        }
        drawPath(path = path, color = strokeColor, style = strokeStyle)
    }
}

private fun DrawScope.drawCachedLabels(
    labelCache: CampusMapLabelCache,
    collisionBuffer: LabelCollisionBuffer,
    visibleRect: MapRect2D,
    transform: MapViewportTransform,
    zoomDpPerM: Float,
    density: Float,
    selectedId: String?
) {
    collisionBuffer.reset()
    val marginPx = 8f * density

    if (selectedId != null) {
        labelCache.byId[selectedId]?.let { selectedLabel ->
            tryDrawCachedLabel(
                entry = selectedLabel,
                collisionBuffer = collisionBuffer,
                visibleRect = visibleRect,
                transform = transform,
                zoomDpPerM = zoomDpPerM,
                density = density,
                paddingPx = marginPx
            )
        }
    }

    for (entry in labelCache.orderedLabels) {
        if (entry.featureId == selectedId) continue
        tryDrawCachedLabel(
            entry = entry,
            collisionBuffer = collisionBuffer,
            visibleRect = visibleRect,
            transform = transform,
            zoomDpPerM = zoomDpPerM,
            density = density,
            paddingPx = marginPx
        )
    }
}

private fun DrawScope.tryDrawCachedLabel(
    entry: CachedMapLabel,
    collisionBuffer: LabelCollisionBuffer,
    visibleRect: MapRect2D,
    transform: MapViewportTransform,
    zoomDpPerM: Float,
    density: Float,
    paddingPx: Float
) {
    if (zoomDpPerM < entry.minZoom) return
    if (!CampusMapMath.pointInRect(entry.labelX, entry.labelY, visibleRect)) return

    val primaryW = entry.primaryLayout.size.width.toFloat()
    val primaryH = entry.primaryLayout.size.height.toFloat()
    if (entry.layer == MapLayer.ROOM) {
        val fits = CampusMapMath.shouldShowSecondaryLabel(
            bboxMinX = entry.bboxMinX,
            bboxMaxX = entry.bboxMaxX,
            zoomDpPerMeter = zoomDpPerM,
            density = density,
            textWidthPx = primaryW
        )
        if (!fits) return
    }

    val screenPt = CampusMapMath.mapToScreen(
        mapX = entry.labelX,
        mapY = entry.labelY,
        transform = transform,
        density = density
    )
    val left = screenPt.x - primaryW * 0.5f
    val top = screenPt.y - primaryH * 0.5f
    val right = left + primaryW
    val bottom = top + primaryH

    if (right < 0f || left > size.width || bottom < 0f || top > size.height) return
    if (!collisionBuffer.tryPlace(left, top, right, bottom, paddingPx = paddingPx)) return

    drawText(
        textLayoutResult = entry.primaryLayout,
        topLeft = Offset(x = left, y = top)
    )

    val secLayout = entry.secondaryLayout ?: return
    val secW = secLayout.size.width.toFloat()
    val secH = secLayout.size.height.toFloat()
    if (CampusMapMath.shouldShowSecondaryLabel(
            bboxMinX = entry.bboxMinX,
            bboxMaxX = entry.bboxMaxX,
            zoomDpPerMeter = zoomDpPerM,
            density = density,
            textWidthPx = secW
        )
    ) {
        val secLeft = screenPt.x - secW * 0.5f
        val secTop = bottom + 2f * density
        val secRight = secLeft + secW
        val secBottom = secTop + secH
        if (secRight >= 0f && secLeft <= size.width && secBottom >= 0f && secTop <= size.height &&
            collisionBuffer.tryPlace(
                left = secLeft,
                top = secTop,
                right = secRight,
                bottom = secBottom,
                paddingPx = paddingPx,
                ignoreLastPlaced = true
            )
        ) {
            drawText(
                textLayoutResult = secLayout,
                topLeft = Offset(x = secLeft, y = secTop)
            )
        }
    }
}
