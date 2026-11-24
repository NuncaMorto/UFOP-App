package br.ufop.campus.core.map

import kotlin.math.max
import kotlin.math.min

/**
 * Transformações matemáticas puras entre coordenadas métricas do mapa (metros relativos
 * à origem local, eixo Y crescente para o Sul) e coordenadas de tela (px).
 */
data class MapViewportTransform(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val zoomDpPerMeter: Float = 1.2f
)

data class MapPoint2D(
    val x: Float,
    val y: Float
)

data class MapRect2D(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
)

object CampusMapMath {
    const val MIN_ZOOM_DP_PER_METER = 0.18f
    const val MAX_ZOOM_DP_PER_METER = 18.0f

    fun clampZoom(zoomDpPerMeter: Float): Float {
        if (!zoomDpPerMeter.isFinite()) return 1.2f
        return zoomDpPerMeter.coerceIn(MIN_ZOOM_DP_PER_METER, MAX_ZOOM_DP_PER_METER)
    }

    /** Converte um ponto métrico `(mapX, mapY)` para pixels na tela dado o transformador atual. */
    fun mapToScreen(
        mapX: Float,
        mapY: Float,
        transform: MapViewportTransform,
        density: Float
    ): MapPoint2D {
        val scale = clampZoom(transform.zoomDpPerMeter) * density.coerceAtLeast(0.5f)
        return MapPoint2D(
            x = mapX * scale + transform.offsetX,
            y = mapY * scale + transform.offsetY
        )
    }

    /** Transformada inversa no toque: converte pixels `(screenX, screenY)` em metros `(mapX, mapY)`. */
    fun screenToMap(
        screenX: Float,
        screenY: Float,
        transform: MapViewportTransform,
        density: Float
    ): MapPoint2D {
        val scale = clampZoom(transform.zoomDpPerMeter) * density.coerceAtLeast(0.5f)
        return MapPoint2D(
            x = (screenX - transform.offsetX) / scale,
            y = (screenY - transform.offsetY) / scale
        )
    }

    /** Atualiza `(offsetX, offsetY, zoom)` mantendo o ponto focal do gesto fixo sob os dedos. */
    fun applyPanAndZoom(
        current: MapViewportTransform,
        panDeltaX: Float,
        panDeltaY: Float,
        zoomFactor: Float,
        centroidX: Float,
        centroidY: Float,
        density: Float
    ): MapViewportTransform {
        val safeDensity = density.coerceAtLeast(0.5f)
        val oldScale = clampZoom(current.zoomDpPerMeter) * safeDensity
        val newZoom = clampZoom(current.zoomDpPerMeter * zoomFactor)
        val newScale = newZoom * safeDensity

        val anchorMapX = (centroidX - current.offsetX) / oldScale
        val anchorMapY = (centroidY - current.offsetY) / oldScale

        return MapViewportTransform(
            offsetX = centroidX - anchorMapX * newScale + panDeltaX,
            offsetY = centroidY - anchorMapY * newScale + panDeltaY,
            zoomDpPerMeter = newZoom
        )
    }

    /** Calcula o alvo de câmera que centraliza e enquadra `bounds` no viewport. */
    fun computeCameraTarget(
        bounds: MapRect2D,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        density: Float,
        paddingDp: Float = 36f
    ): MapViewportTransform {
        val safeDensity = density.coerceAtLeast(0.5f)
        val safeW = max(1f, viewportWidthPx)
        val safeH = max(1f, viewportHeightPx)
        val padPx = paddingDp * safeDensity
        val availW = max(24f, safeW - padPx * 2f)
        val availH = max(24f, safeH - padPx * 2f)

        val spanX = max(8f, bounds.maxX - bounds.minX)
        val spanY = max(8f, bounds.maxY - bounds.minY)
        val targetScalePxPerMeter = min(availW / spanX, availH / spanY)
        val targetZoom = clampZoom(targetScalePxPerMeter / safeDensity)
        val finalScale = targetZoom * safeDensity

        val centerMapX = (bounds.minX + bounds.maxX) * 0.5f
        val centerMapY = (bounds.minY + bounds.maxY) * 0.5f

        return MapViewportTransform(
            offsetX = safeW * 0.5f - centerMapX * finalScale,
            offsetY = safeH * 0.5f - centerMapY * finalScale,
            zoomDpPerMeter = targetZoom
        )
    }

    /** Oculta rótulos secundários quando a largura do polígono em tela for menor que o texto. */
    fun shouldShowSecondaryLabel(
        bboxMinX: Float,
        bboxMaxX: Float,
        zoomDpPerMeter: Float,
        density: Float,
        textWidthPx: Float,
        marginPx: Float = 8f
    ): Boolean {
        if (textWidthPx <= 0f) return false
        val scale = clampZoom(zoomDpPerMeter) * density.coerceAtLeast(0.5f)
        val polyScreenWidthPx = (bboxMaxX - bboxMinX) * scale
        return polyScreenWidthPx >= (textWidthPx + marginPx)
    }
}
