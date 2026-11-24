package br.ufop.campus.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import br.ufop.campus.core.map.CampusMapMath
import br.ufop.campus.core.map.MapRect2D
import br.ufop.campus.core.uniffi.MapLayer
import br.ufop.campus.core.uniffi.PoiType
import br.ufop.campus.core.uniffi.RenderBatch
import br.ufop.campus.core.uniffi.RenderFeature
import br.ufop.campus.core.uniffi.RenderGeomKind
import br.ufop.campus.core.uniffi.RenderOccupancyKind
import br.ufop.campus.ui.theme.DarkStatusAttendingGreen
import br.ufop.campus.ui.theme.StatusAttendingGreen
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class StaticPolygonLayerBatch(
    val layer: MapLayer,
    val path: Path,
    val bounds: MapRect2D
)

internal data class CachedDynamicGeometry(
    val featureIndex: Int,
    val id: String,
    val layer: MapLayer,
    val geomKind: RenderGeomKind,
    val path: Path?,
    val bboxMinX: Float,
    val bboxMinY: Float,
    val bboxMaxX: Float,
    val bboxMaxY: Float,
    val minZoom: Float,
    val pointX: Float,
    val pointY: Float,
    val labelX: Float,
    val labelY: Float,
    val isMock: Boolean,
    val poiType: PoiType?,
    val categoryLower: String
)

/**
 * Cache geométrico imutável de um [RenderBatch], desacoplado de ocupação/seleção/filtros.
 * Preserva estritamente o z-order oficial das camadas (`0..8`) agrupando polígonos estáticos
 * single-ring e todas as vias (`WALKWAY`) em paths compostos por camada.
 */
internal class CampusMapGeometryCache(
    val groundBatch: StaticPolygonLayerBatch?,
    val groundOverflow: List<CachedDynamicGeometry>,
    val campusZones: List<CachedDynamicGeometry>,
    val overlayStaticBatches: List<StaticPolygonLayerBatch>,
    val overlayStaticOverflow: List<CachedDynamicGeometry>,
    val walkwayPath: Path?,
    val walkwayBounds: MapRect2D?,
    val walkwayOverflow: List<CachedDynamicGeometry>,
    val foregroundItems: List<CachedDynamicGeometry>
) {
    companion object {
        fun build(batch: RenderBatch): CampusMapGeometryCache {
            val staticPaths = mutableMapOf<MapLayer, Path>()
            val staticBounds = mutableMapOf<MapLayer, FloatArray>()
            val groundOverflow = ArrayList<CachedDynamicGeometry>()
            val campusZones = ArrayList<CachedDynamicGeometry>()
            val overlayStaticOverflow = ArrayList<CachedDynamicGeometry>()
            var walkwayPath: Path? = null
            var walkwayBounds: FloatArray? = null
            val walkwayOverflow = ArrayList<CachedDynamicGeometry>()
            val foregroundItems = ArrayList<CachedDynamicGeometry>()

            batch.items.forEachIndexed { index, item ->
                when (item.layer) {
                    MapLayer.CAMPUS_GROUND,
                    MapLayer.GREEN_AREA,
                    MapLayer.PARKING,
                    MapLayer.SPORTS -> {
                        if (item.geomKind == RenderGeomKind.POLYGON &&
                            item.ringCount == 1u &&
                            item.minZoom <= 0f
                        ) {
                            val layerPath = staticPaths.getOrPut(item.layer) {
                                Path().apply { fillType = PathFillType.NonZero }
                            }
                            appendItemToPath(layerPath, item, batch)
                            val acc = staticBounds[item.layer]
                            if (acc == null) {
                                staticBounds[item.layer] = floatArrayOf(
                                    item.bboxMinX,
                                    item.bboxMinY,
                                    item.bboxMaxX,
                                    item.bboxMaxY
                                )
                            } else {
                                acc[0] = min(acc[0], item.bboxMinX)
                                acc[1] = min(acc[1], item.bboxMinY)
                                acc[2] = max(acc[2], item.bboxMaxX)
                                acc[3] = max(acc[3], item.bboxMaxY)
                            }
                        } else {
                            val dynamicEntry = toDynamicGeometry(index, item, batch)
                            if (item.layer == MapLayer.CAMPUS_GROUND) {
                                groundOverflow.add(dynamicEntry)
                            } else {
                                overlayStaticOverflow.add(dynamicEntry)
                            }
                        }
                    }

                    MapLayer.CAMPUS_ZONE -> {
                        campusZones.add(toDynamicGeometry(index, item, batch))
                    }

                    MapLayer.WALKWAY -> {
                        if (item.geomKind == RenderGeomKind.POLYLINE && item.minZoom <= 0f) {
                            val path = walkwayPath ?: Path().also { walkwayPath = it }
                            appendItemToPath(path, item, batch)
                            val acc = walkwayBounds
                            if (acc == null) {
                                walkwayBounds = floatArrayOf(
                                    item.bboxMinX,
                                    item.bboxMinY,
                                    item.bboxMaxX,
                                    item.bboxMaxY
                                )
                            } else {
                                acc[0] = min(acc[0], item.bboxMinX)
                                acc[1] = min(acc[1], item.bboxMinY)
                                acc[2] = max(acc[2], item.bboxMaxX)
                                acc[3] = max(acc[3], item.bboxMaxY)
                            }
                        } else {
                            walkwayOverflow.add(toDynamicGeometry(index, item, batch))
                        }
                    }

                    MapLayer.BUILDING,
                    MapLayer.ROOM,
                    MapLayer.POI -> {
                        foregroundItems.add(toDynamicGeometry(index, item, batch))
                    }
                }
            }

            val groundBatch = staticPaths[MapLayer.CAMPUS_GROUND]?.let { path ->
                val b = staticBounds.getValue(MapLayer.CAMPUS_GROUND)
                StaticPolygonLayerBatch(
                    layer = MapLayer.CAMPUS_GROUND,
                    path = path,
                    bounds = MapRect2D(b[0], b[1], b[2], b[3])
                )
            }

            val overlayStaticBatches = buildList {
                for (layer in listOf(MapLayer.GREEN_AREA, MapLayer.PARKING, MapLayer.SPORTS)) {
                    val path = staticPaths[layer] ?: continue
                    val b = staticBounds[layer] ?: continue
                    add(
                        StaticPolygonLayerBatch(
                            layer = layer,
                            path = path,
                            bounds = MapRect2D(b[0], b[1], b[2], b[3])
                        )
                    )
                }
            }

            val finalWalkwayBounds = walkwayBounds?.let { b ->
                MapRect2D(b[0], b[1], b[2], b[3])
            }

            return CampusMapGeometryCache(
                groundBatch = groundBatch,
                groundOverflow = groundOverflow,
                campusZones = campusZones,
                overlayStaticBatches = overlayStaticBatches,
                overlayStaticOverflow = overlayStaticOverflow,
                walkwayPath = walkwayPath,
                walkwayBounds = finalWalkwayBounds,
                walkwayOverflow = walkwayOverflow,
                foregroundItems = foregroundItems
            )
        }

        private fun toDynamicGeometry(
            index: Int,
            item: RenderFeature,
            batch: RenderBatch
        ): CachedDynamicGeometry {
            val path = if (item.geomKind == RenderGeomKind.POINT) {
                null
            } else {
                Path().apply {
                    fillType = PathFillType.EvenOdd
                    appendItemToPath(this, item, batch)
                }
            }
            return CachedDynamicGeometry(
                featureIndex = index,
                id = item.id,
                layer = item.layer,
                geomKind = item.geomKind,
                path = path,
                bboxMinX = item.bboxMinX,
                bboxMinY = item.bboxMinY,
                bboxMaxX = item.bboxMaxX,
                bboxMaxY = item.bboxMaxY,
                minZoom = item.minZoom,
                pointX = item.pointX,
                pointY = item.pointY,
                labelX = item.labelX,
                labelY = item.labelY,
                isMock = item.isMock,
                poiType = item.poiType,
                categoryLower = item.category.lowercase()
            )
        }

        private fun appendItemToPath(path: Path, item: RenderFeature, batch: RenderBatch) {
            if (item.geomKind == RenderGeomKind.POINT) return
            val startRing = item.ringStartIdx.toInt()
            val endRing = minOf(startRing + item.ringCount.toInt(), batch.ringStarts.size)
            if (startRing >= endRing) return

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
        }
    }
}

internal data class CachedMapLabel(
    val featureId: String,
    val layer: MapLayer,
    val labelX: Float,
    val labelY: Float,
    val bboxMinX: Float,
    val bboxMaxX: Float,
    val minZoom: Float,
    val primaryLayout: TextLayoutResult,
    val secondaryLayout: TextLayoutResult?
)

internal class CampusMapLabelCache(
    val orderedLabels: List<CachedMapLabel>,
    val byId: Map<String, CachedMapLabel>
) {
    companion object {
        fun build(
            batch: RenderBatch,
            palette: MapCanvasPalette,
            textMeasurer: TextMeasurer
        ): CampusMapLabelCache {
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

            val items = ArrayList<CachedMapLabel>()
            val byId = HashMap<String, CachedMapLabel>()

            for (item in batch.items) {
                val isLabeledLayer = item.layer == MapLayer.BUILDING ||
                    item.layer == MapLayer.CAMPUS_ZONE ||
                    item.layer == MapLayer.ROOM ||
                    item.layer == MapLayer.POI
                if (!isLabeledLayer || item.primaryLabel.isBlank()) continue

                val style = if (item.layer == MapLayer.ROOM) roomStyle else buildingStyle
                val primaryLayout = textMeasurer.measure(text = item.primaryLabel, style = style)
                val secondaryLayout = if (item.layer == MapLayer.BUILDING && item.secondaryLabel.isNotBlank()) {
                    textMeasurer.measure(text = item.secondaryLabel, style = secondaryStyle)
                } else {
                    null
                }

                val entry = CachedMapLabel(
                    featureId = item.id,
                    layer = item.layer,
                    labelX = item.labelX,
                    labelY = item.labelY,
                    bboxMinX = item.bboxMinX,
                    bboxMaxX = item.bboxMaxX,
                    minZoom = item.minZoom,
                    primaryLayout = primaryLayout,
                    secondaryLayout = secondaryLayout
                )
                items.add(entry)
                byId[item.id] = entry
            }

            items.sortBy { labelPriority(it.layer) }
            return CampusMapLabelCache(orderedLabels = items, byId = byId)
        }

        private fun labelPriority(layer: MapLayer): Int = when (layer) {
            MapLayer.POI -> 0
            MapLayer.ROOM -> 1
            MapLayer.BUILDING -> 2
            MapLayer.CAMPUS_ZONE -> 3
            else -> 4
        }
    }
}

internal class CampusMapDrawColors(
    val freeHighlightColor: Color,
    val roomFreeHighlightedFill: Color,
    val dimmedBuildingFill: Color,
    val dimmedRoomOccupiedFill: Color,
    val dimmedRoomFreeFill: Color,
    val dimmedRoomNoDataFill: Color,
    val dimmedRoomOutOfTermFill: Color,
    val dimmedRoomStroke: Color,
    val dimmedOtherStroke: Color,
    val defaultOtherStroke: Color,
    val poiHaloColor: Color,
    val dimmedPoiFill: Color,
    val dimmedPoiStroke: Color
) {
    companion object {
        fun resolve(palette: MapCanvasPalette, isDark: Boolean): CampusMapDrawColors =
            CampusMapDrawColors(
                freeHighlightColor = if (isDark) DarkStatusAttendingGreen else StatusAttendingGreen,
                roomFreeHighlightedFill = palette.roomFreeFill.copy(alpha = 0.96f),
                dimmedBuildingFill = palette.buildingFill.copy(alpha = 0.30f),
                dimmedRoomOccupiedFill = palette.roomOccupiedFill.copy(alpha = 0.30f),
                dimmedRoomFreeFill = palette.roomFreeFill.copy(alpha = 0.30f),
                dimmedRoomNoDataFill = palette.roomNoDataFill.copy(alpha = 0.30f),
                dimmedRoomOutOfTermFill = palette.roomOutOfTermFill.copy(alpha = 0.30f),
                dimmedRoomStroke = palette.roomStroke.copy(alpha = 0.35f),
                dimmedOtherStroke = palette.buildingStroke.copy(alpha = 0.35f),
                defaultOtherStroke = palette.buildingStroke.copy(alpha = 0.45f),
                poiHaloColor = palette.poiFill.copy(alpha = 0.28f),
                dimmedPoiFill = palette.poiFill.copy(alpha = 0.30f),
                dimmedPoiStroke = Color.White.copy(alpha = 0.35f)
            )
    }
}

internal class CampusMapStrokeCache(private val density: Float) {
    private var lastPxPerMeter: Float = -1f
    private var lastDashBucket: Int = Int.MIN_VALUE
    private var dashEffect: PathEffect? = null

    var normalStroke = Stroke(width = 1f)
        private set
    var selectedStroke = Stroke(width = 1f)
        private set
    var highlightStroke = Stroke(width = 1f)
        private set
    var mockNormalStroke = Stroke(width = 1f)
        private set
    var mockSelectedStroke = Stroke(width = 1f)
        private set
    var mockHighlightStroke = Stroke(width = 1f)
        private set
    var walkwayStroke = Stroke(width = 1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        private set
    var poiNormalStroke = Stroke(width = 1f)
        private set
    var poiHighlightStroke = Stroke(width = 1f)
        private set

    fun updateIfNeeded(pxPerMeter: Float) {
        val safePx = pxPerMeter.coerceAtLeast(0.05f)
        val bucket = CampusMapMath.zoomDashBucket(safePx)
        val bucketChanged = bucket != lastDashBucket
        val scaleChanged = abs(safePx - lastPxPerMeter) > 0.001f

        if (!bucketChanged && !scaleChanged) return

        if (bucketChanged) {
            lastDashBucket = bucket
            val onM = (5f * density) / safePx
            val offM = (3.5f * density) / safePx
            dashEffect = PathEffect.dashPathEffect(floatArrayOf(onM, offM), 0f)
        }

        lastPxPerMeter = safePx
        val normalW = (1.2f * density) / safePx
        val selectedW = (2.5f * density) / safePx
        val highlightW = (2.2f * density) / safePx

        normalStroke = Stroke(width = normalW)
        selectedStroke = Stroke(width = selectedW)
        highlightStroke = Stroke(width = highlightW)
        mockNormalStroke = Stroke(width = normalW, pathEffect = dashEffect)
        mockSelectedStroke = Stroke(width = selectedW, pathEffect = dashEffect)
        mockHighlightStroke = Stroke(width = highlightW, pathEffect = dashEffect)
        walkwayStroke = Stroke(
            width = maxOf(1.4f, density / safePx),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        poiNormalStroke = Stroke(width = (1.5f * density) / safePx)
        poiHighlightStroke = Stroke(width = (2.0f * density) / safePx)
    }
}

internal fun fillColorFor(
    layer: MapLayer,
    occupancy: RenderOccupancyKind,
    isSelected: Boolean,
    palette: MapCanvasPalette
): Color {
    if (isSelected && (layer == MapLayer.ROOM || layer == MapLayer.BUILDING || layer == MapLayer.CAMPUS_ZONE)) {
        return palette.selectedFill
    }
    return when (layer) {
        MapLayer.CAMPUS_GROUND -> palette.campusGroundFill
        MapLayer.CAMPUS_ZONE -> palette.campusZoneFill
        MapLayer.GREEN_AREA -> palette.greenAreaFill
        MapLayer.PARKING -> palette.parkingFill
        MapLayer.SPORTS -> palette.sportsFill
        MapLayer.WALKWAY -> Color.Transparent
        MapLayer.BUILDING -> palette.buildingFill
        MapLayer.ROOM -> when (occupancy) {
            RenderOccupancyKind.OCCUPIED -> palette.roomOccupiedFill
            RenderOccupancyKind.NO_REGULAR_CLASS -> palette.roomFreeFill
            RenderOccupancyKind.NO_DATA -> palette.roomNoDataFill
            RenderOccupancyKind.OUT_OF_TERM -> palette.roomOutOfTermFill
            RenderOccupancyKind.NONE -> palette.buildingFill
        }
        MapLayer.POI -> palette.poiFill
    }
}

internal fun dimmedRoomFillFor(
    occupancy: RenderOccupancyKind,
    colors: CampusMapDrawColors
): Color = when (occupancy) {
    RenderOccupancyKind.OCCUPIED -> colors.dimmedRoomOccupiedFill
    RenderOccupancyKind.NO_REGULAR_CLASS -> colors.dimmedRoomFreeFill
    RenderOccupancyKind.NO_DATA -> colors.dimmedRoomNoDataFill
    RenderOccupancyKind.OUT_OF_TERM -> colors.dimmedRoomOutOfTermFill
    RenderOccupancyKind.NONE -> colors.dimmedBuildingFill
}

internal fun strokeColorFor(
    layer: MapLayer,
    palette: MapCanvasPalette,
    colors: CampusMapDrawColors
): Color = when (layer) {
    MapLayer.CAMPUS_GROUND -> palette.campusGroundStroke
    MapLayer.CAMPUS_ZONE -> palette.campusZoneStroke
    MapLayer.BUILDING -> palette.buildingStroke
    MapLayer.ROOM -> palette.roomStroke
    else -> colors.defaultOtherStroke
}

internal fun matchesRenderPoiFilter(
    layer: MapLayer,
    poiType: PoiType?,
    categoryLower: String,
    filter: PoiType
): Boolean {
    if (poiType == filter) return true
    return when (filter) {
        PoiType.LIBRARY -> categoryLower == "library"
        PoiType.DINING -> categoryLower == "dining"
        PoiType.PARKING -> layer == MapLayer.PARKING || categoryLower == "parking"
        PoiType.FACULTY_OFFICE -> categoryLower == "faculty_office"
        PoiType.ACADEMIC -> (layer == MapLayer.BUILDING || layer == MapLayer.CAMPUS_ZONE || layer == MapLayer.ROOM) &&
            (categoryLower == "academic" ||
                categoryLower == "academic_zone" ||
                categoryLower == "classroom" ||
                categoryLower == "lab")
        PoiType.TOILET, PoiType.ENTRANCE -> false
    }
}
