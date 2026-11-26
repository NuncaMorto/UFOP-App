package br.ufop.campus.core.map

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

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

    /** Calcula o alvo de câmera que centraliza e enquadra `bounds` no viewport considerando margens do HUD. */
    fun computeCameraTarget(
        bounds: MapRect2D,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        density: Float,
        paddingDp: Float = 36f,
        topInsetDp: Float = 0f,
        bottomInsetDp: Float = 0f,
        startInsetDp: Float = 0f,
        endInsetDp: Float = 0f
    ): MapViewportTransform {
        val safeDensity = density.coerceAtLeast(0.5f)
        val safeW = max(1f, viewportWidthPx)
        val safeH = max(1f, viewportHeightPx)
        val padPx = paddingDp * safeDensity
        val startPadPx = if (startInsetDp > 0f) max(padPx, startInsetDp * safeDensity + padPx * 0.5f) else padPx
        val endPadPx = if (endInsetDp > 0f) max(padPx, endInsetDp * safeDensity + padPx * 0.5f) else padPx
        val topPadPx = if (topInsetDp > 0f) max(padPx, topInsetDp * safeDensity + 12f * safeDensity) else padPx
        val bottomPadPx = if (bottomInsetDp > 0f) max(padPx, bottomInsetDp * safeDensity + 12f * safeDensity) else padPx

        val availW = max(24f, safeW - startPadPx - endPadPx)
        val availH = max(24f, safeH - topPadPx - bottomPadPx)

        val spanX = max(8f, bounds.maxX - bounds.minX)
        val spanY = max(8f, bounds.maxY - bounds.minY)
        val targetScalePxPerMeter = min(availW / spanX, availH / spanY)
        val targetZoom = clampZoom(targetScalePxPerMeter / safeDensity)
        val finalScale = targetZoom * safeDensity

        val centerMapX = (bounds.minX + bounds.maxX) * 0.5f
        val centerMapY = (bounds.minY + bounds.maxY) * 0.5f
        val centerScreenX = startPadPx + availW * 0.5f
        val centerScreenY = topPadPx + availH * 0.5f

        return MapViewportTransform(
            offsetX = centerScreenX - centerMapX * finalScale,
            offsetY = centerScreenY - centerMapY * finalScale,
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

    /** Calcula o retângulo visível do mapa em metros com margem de folga em pixels. */
    fun visibleMapRect(
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        transform: MapViewportTransform,
        density: Float,
        marginPx: Float = 48f
    ): MapRect2D {
        val safeW = max(1f, viewportWidthPx)
        val safeH = max(1f, viewportHeightPx)
        val safeMargin = max(0f, marginPx)
        val topLeft = screenToMap(-safeMargin, -safeMargin, transform, density)
        val bottomRight = screenToMap(safeW + safeMargin, safeH + safeMargin, transform, density)
        return MapRect2D(
            minX = min(topLeft.x, bottomRight.x),
            minY = min(topLeft.y, bottomRight.y),
            maxX = max(topLeft.x, bottomRight.x),
            maxY = max(topLeft.y, bottomRight.y)
        )
    }

    fun rectsIntersect(a: MapRect2D, b: MapRect2D): Boolean =
        a.maxX >= b.minX && a.minX <= b.maxX && a.maxY >= b.minY && a.minY <= b.maxY

    fun bboxIntersects(
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float,
        viewport: MapRect2D
    ): Boolean =
        maxX >= viewport.minX && minX <= viewport.maxX && maxY >= viewport.minY && minY <= viewport.maxY

    fun pointInRect(
        x: Float,
        y: Float,
        rect: MapRect2D,
        marginM: Float = 0f
    ): Boolean =
        x >= (rect.minX - marginM) &&
            x <= (rect.maxX + marginM) &&
            y >= (rect.minY - marginM) &&
            y <= (rect.maxY + marginM)

    /** Quantiza o fator de escala em buckets discretos para reutilizar `PathEffect` durante pan/zoom. */
    fun zoomDashBucket(pxPerMeter: Float): Int {
        val safeScale = if (pxPerMeter.isFinite()) max(0.1f, pxPerMeter) else 1.0f
        return round(ln(safeScale.toDouble()) * 8.0).toInt()
    }
}

/**
 * Buffer reutilizável (zero alocações por frame) para evitar sobreposição de rótulos no Canvas.
 */
class LabelCollisionBuffer(initialCapacity: Int = 64) {
    private var bounds = FloatArray(initialCapacity.coerceAtLeast(8) * 4)

    var placedCount: Int = 0
        private set

    fun reset() {
        placedCount = 0
    }

    fun tryPlace(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        paddingPx: Float = 4f,
        ignoreLastPlaced: Boolean = false
    ): Boolean {
        if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) {
            return false
        }
        if (right <= left || bottom <= top) {
            return false
        }
        val pad = max(0f, paddingPx)
        val pLeft = left - pad
        val pTop = top - pad
        val pRight = right + pad
        val pBottom = bottom + pad

        val checkCount = if (ignoreLastPlaced && placedCount > 0) placedCount - 1 else placedCount
        for (i in 0 until checkCount) {
            val base = i * 4
            if (pRight > bounds[base] &&
                pLeft < bounds[base + 2] &&
                pBottom > bounds[base + 1] &&
                pTop < bounds[base + 3]
            ) {
                return false
            }
        }

        val needed = (placedCount + 1) * 4
        if (needed > bounds.size) {
            bounds = bounds.copyOf(bounds.size * 2)
        }
        val base = placedCount * 4
        bounds[base] = left
        bounds[base + 1] = top
        bounds[base + 2] = right
        bounds[base + 3] = bottom
        placedCount += 1
        return true
    }
}
