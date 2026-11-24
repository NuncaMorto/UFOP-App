package br.ufop.campus.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusMapMathTest {

    @Test
    fun screenToMapIsExactInverseOfMapToScreen() {
        val transform = MapViewportTransform(
            offsetX = 420f,
            offsetY = 310f,
            zoomDpPerMeter = 2.5f
        )
        val density = 3.0f
        val original = MapPoint2D(x = -12.4f, y = 178.9f)

        val screen = CampusMapMath.mapToScreen(original.x, original.y, transform, density)
        val roundTrip = CampusMapMath.screenToMap(screen.x, screen.y, transform, density)

        assertEquals(original.x, roundTrip.x, 0.001f)
        assertEquals(original.y, roundTrip.y, 0.001f)
    }

    @Test
    fun panAndZoomPreservesAnchorUnderCentroid() {
        val initial = MapViewportTransform(offsetX = 200f, offsetY = 150f, zoomDpPerMeter = 1.5f)
        val density = 2.0f
        val centroidX = 500f
        val centroidY = 400f
        val anchorBefore = CampusMapMath.screenToMap(centroidX, centroidY, initial, density)

        val updated = CampusMapMath.applyPanAndZoom(
            current = initial,
            panDeltaX = 0f,
            panDeltaY = 0f,
            zoomFactor = 2.0f,
            centroidX = centroidX,
            centroidY = centroidY,
            density = density
        )
        val anchorAfter = CampusMapMath.screenToMap(centroidX, centroidY, updated, density)

        assertEquals(3.0f, updated.zoomDpPerMeter, 0.001f)
        assertEquals(anchorBefore.x, anchorAfter.x, 0.01f)
        assertEquals(anchorBefore.y, anchorAfter.y, 0.01f)
    }

    @Test
    fun computeCameraTargetCentersFocusBoundsInViewport() {
        val bounds = MapRect2D(minX = -20f, minY = 140f, maxX = 20f, maxY = 180f)
        val target = CampusMapMath.computeCameraTarget(
            bounds = bounds,
            viewportWidthPx = 1080f,
            viewportHeightPx = 1800f,
            density = 3.0f,
            paddingDp = 40f
        )
        val centerScreen = CampusMapMath.mapToScreen(0f, 160f, target, 3.0f)

        assertEquals(540f, centerScreen.x, 0.1f)
        assertEquals(900f, centerScreen.y, 0.1f)
        assertTrue(target.zoomDpPerMeter > 1.0f)
    }

    @Test
    fun secondaryLabelVisibilityRespectsProjectedPolygonWidth() {
        assertFalse(
            CampusMapMath.shouldShowSecondaryLabel(
                bboxMinX = 0f,
                bboxMaxX = 8f,
                zoomDpPerMeter = 2.0f,
                density = 2.0f,
                textWidthPx = 60f
            )
        )
        assertTrue(
            CampusMapMath.shouldShowSecondaryLabel(
                bboxMinX = 0f,
                bboxMaxX = 30f,
                zoomDpPerMeter = 2.0f,
                density = 2.0f,
                textWidthPx = 60f
            )
        )
    }
}
