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
    fun clampZoomHandlesNonFiniteAndRangeBoundaries() {
        assertEquals(1.2f, CampusMapMath.clampZoom(Float.NaN), 0.0001f)
        assertEquals(1.2f, CampusMapMath.clampZoom(Float.POSITIVE_INFINITY), 0.0001f)
        assertEquals(1.2f, CampusMapMath.clampZoom(Float.NEGATIVE_INFINITY), 0.0001f)

        assertEquals(
            CampusMapMath.MIN_ZOOM_DP_PER_METER,
            CampusMapMath.clampZoom(-5.0f),
            0.0001f
        )
        assertEquals(
            CampusMapMath.MIN_ZOOM_DP_PER_METER,
            CampusMapMath.clampZoom(0.01f),
            0.0001f
        )
        assertEquals(
            CampusMapMath.MAX_ZOOM_DP_PER_METER,
            CampusMapMath.clampZoom(99.0f),
            0.0001f
        )
        assertEquals(4.25f, CampusMapMath.clampZoom(4.25f), 0.0001f)
    }

    @Test
    fun projectionClampsLowOrZeroDensityToSafeMinimum() {
        val transform = MapViewportTransform(offsetX = 10f, offsetY = 20f, zoomDpPerMeter = 2.0f)

        val zeroDensityPt = CampusMapMath.mapToScreen(4f, 6f, transform, density = 0f)
        val negDensityPt = CampusMapMath.mapToScreen(4f, 6f, transform, density = -3f)
        val minDensityPt = CampusMapMath.mapToScreen(4f, 6f, transform, density = 0.5f)

        assertEquals(minDensityPt.x, zeroDensityPt.x, 0.0001f)
        assertEquals(minDensityPt.y, zeroDensityPt.y, 0.0001f)
        assertEquals(minDensityPt.x, negDensityPt.x, 0.0001f)
        assertEquals(minDensityPt.y, negDensityPt.y, 0.0001f)

        val back = CampusMapMath.screenToMap(zeroDensityPt.x, zeroDensityPt.y, transform, density = 0f)
        assertEquals(4f, back.x, 0.001f)
        assertEquals(6f, back.y, 0.001f)
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
    fun combinedPanAndZoomShiftsAnchorByPanDeltaAndClampsZoom() {
        val initial = MapViewportTransform(offsetX = 100f, offsetY = 80f, zoomDpPerMeter = 10.0f)
        val density = 2.0f
        val centroidX = 300f
        val centroidY = 250f
        val anchorMap = CampusMapMath.screenToMap(centroidX, centroidY, initial, density)

        val zoomedMax = CampusMapMath.applyPanAndZoom(
            current = initial,
            panDeltaX = 24f,
            panDeltaY = -16f,
            zoomFactor = 5.0f,
            centroidX = centroidX,
            centroidY = centroidY,
            density = density
        )
        assertEquals(CampusMapMath.MAX_ZOOM_DP_PER_METER, zoomedMax.zoomDpPerMeter, 0.001f)
        val anchorAfterScreen = CampusMapMath.mapToScreen(anchorMap.x, anchorMap.y, zoomedMax, density)
        assertEquals(centroidX + 24f, anchorAfterScreen.x, 0.01f)
        assertEquals(centroidY - 16f, anchorAfterScreen.y, 0.01f)

        val zoomedMin = CampusMapMath.applyPanAndZoom(
            current = zoomedMax,
            panDeltaX = 0f,
            panDeltaY = 0f,
            zoomFactor = 0.001f,
            centroidX = centroidX,
            centroidY = centroidY,
            density = 0.1f
        )
        assertEquals(CampusMapMath.MIN_ZOOM_DP_PER_METER, zoomedMin.zoomDpPerMeter, 0.001f)
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
    fun computeCameraTargetHandlesNarrowWideAndDegenerateViewports() {
        val wideBounds = MapRect2D(minX = 0f, minY = 0f, maxX = 200f, maxY = 40f)
        val narrowViewport = CampusMapMath.computeCameraTarget(
            bounds = wideBounds,
            viewportWidthPx = 400f,
            viewportHeightPx = 1200f,
            density = 2.0f,
            paddingDp = 20f
        )
        val wideViewport = CampusMapMath.computeCameraTarget(
            bounds = wideBounds,
            viewportWidthPx = 3000f,
            viewportHeightPx = 600f,
            density = 2.0f,
            paddingDp = 20f
        )

        // Narrow portrait viewport must fit 200m horizontally: availW = 320px -> scale = 1.6 -> zoom = 0.8
        assertEquals(0.8f, narrowViewport.zoomDpPerMeter, 0.001f)
        val narrowCenter = CampusMapMath.mapToScreen(100f, 20f, narrowViewport, 2.0f)
        assertEquals(200f, narrowCenter.x, 0.1f)
        assertEquals(600f, narrowCenter.y, 0.1f)

        // Wide landscape viewport is constrained vertically: availH = 520px over 40m -> scale = 13 -> zoom = 6.5
        assertEquals(6.5f, wideViewport.zoomDpPerMeter, 0.001f)
        val wideCenter = CampusMapMath.mapToScreen(100f, 20f, wideViewport, 2.0f)
        assertEquals(1500f, wideCenter.x, 0.1f)
        assertEquals(300f, wideCenter.y, 0.1f)

        // Point-like bounds and tiny viewport stay finite and valid
        val pointBounds = MapRect2D(minX = 12f, minY = 24f, maxX = 12f, maxY = 24f)
        val tinyTarget = CampusMapMath.computeCameraTarget(
            bounds = pointBounds,
            viewportWidthPx = 0f,
            viewportHeightPx = -10f,
            density = 0f,
            paddingDp = 36f
        )
        assertTrue(tinyTarget.zoomDpPerMeter.isFinite())
        assertTrue(tinyTarget.offsetX.isFinite())
        assertTrue(tinyTarget.offsetY.isFinite())
    }

    @Test
    fun secondaryLabelVisibilityRespectsProjectedPolygonWidthAndBoundaries() {
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
        // Zero or negative textWidthPx always returns false
        assertFalse(
            CampusMapMath.shouldShowSecondaryLabel(
                bboxMinX = 0f,
                bboxMaxX = 100f,
                zoomDpPerMeter = 2.0f,
                density = 2.0f,
                textWidthPx = 0f
            )
        )
        assertFalse(
            CampusMapMath.shouldShowSecondaryLabel(
                bboxMinX = 0f,
                bboxMaxX = 100f,
                zoomDpPerMeter = 2.0f,
                density = 2.0f,
                textWidthPx = -10f
            )
        )
        // Exact boundary: width = 10m, scale = 2*2 = 4 -> 40px == 32px + 8px
        assertTrue(
            CampusMapMath.shouldShowSecondaryLabel(
                bboxMinX = 5f,
                bboxMaxX = 15f,
                zoomDpPerMeter = 2.0f,
                density = 2.0f,
                textWidthPx = 32f,
                marginPx = 8f
            )
        )
        // Just under threshold (40px < 32.5px + 8px)
        assertFalse(
            CampusMapMath.shouldShowSecondaryLabel(
                bboxMinX = 5f,
                bboxMaxX = 15f,
                zoomDpPerMeter = 2.0f,
                density = 2.0f,
                textWidthPx = 32.5f,
                marginPx = 8f
            )
        )
    }

    @Test
    fun visibleMapRectAndCullingHelpersWorkAccurately() {
        val transform = MapViewportTransform(offsetX = 100f, offsetY = 200f, zoomDpPerMeter = 2.0f)
        val density = 2.0f
        // scale = 4 px/m; screen (0,0)..(800, 1200) -> map (-25, -50)..(175, 250), plus 40px (=10m) margin
        val rect = CampusMapMath.visibleMapRect(
            viewportWidthPx = 800f,
            viewportHeightPx = 1200f,
            transform = transform,
            density = density,
            marginPx = 40f
        )
        assertEquals(-35f, rect.minX, 0.01f)
        assertEquals(-60f, rect.minY, 0.01f)
        assertEquals(185f, rect.maxX, 0.01f)
        assertEquals(260f, rect.maxY, 0.01f)

        assertTrue(CampusMapMath.bboxIntersects(0f, 0f, 10f, 10f, rect))
        assertTrue(CampusMapMath.bboxIntersects(-50f, -80f, -35f, -59f, rect))
        assertFalse(CampusMapMath.bboxIntersects(-80f, 0f, -36f, 20f, rect))
        assertFalse(CampusMapMath.bboxIntersects(186f, 0f, 220f, 20f, rect))
        assertFalse(CampusMapMath.bboxIntersects(0f, 261f, 20f, 300f, rect))

        assertTrue(CampusMapMath.pointInRect(0f, 0f, rect))
        assertFalse(CampusMapMath.pointInRect(-40f, 0f, rect, marginM = 2f))
        assertTrue(CampusMapMath.pointInRect(-40f, 0f, rect, marginM = 6f))
        assertTrue(
            CampusMapMath.rectsIntersect(
                MapRect2D(-50f, -50f, -30f, -30f),
                rect
            )
        )
    }

    @Test
    fun zoomDashBucketIsStableWithinSmallChangesAndUpdatesOnZoomSteps() {
        val b1 = CampusMapMath.zoomDashBucket(4.00f)
        val b2 = CampusMapMath.zoomDashBucket(4.02f)
        val b3 = CampusMapMath.zoomDashBucket(8.00f)
        assertEquals(b1, b2)
        assertTrue(b3 != b1)
    }

    @Test
    fun labelCollisionBufferPreventsOverlapSupportsResetAndGrowsCapacity() {
        val buffer = LabelCollisionBuffer(initialCapacity = 8)
        assertTrue(buffer.tryPlace(100f, 100f, 180f, 120f, paddingPx = 4f))
        assertEquals(1, buffer.placedCount)

        // Overlapping directly or within paddingPx must be rejected
        assertFalse(buffer.tryPlace(120f, 110f, 200f, 130f, paddingPx = 4f))
        assertFalse(buffer.tryPlace(182f, 100f, 240f, 120f, paddingPx = 4f))

        // Far enough (>= 4px gap) succeeds
        assertTrue(buffer.tryPlace(185f, 100f, 240f, 120f, paddingPx = 4f))
        assertEquals(2, buffer.placedCount)

        // Degenerate or non-finite boxes are rejected
        assertFalse(buffer.tryPlace(10f, 10f, 10f, 20f))
        assertFalse(buffer.tryPlace(Float.NaN, 10f, 30f, 20f))

        // Reset clears count without reallocating and allows reuse of same region
        buffer.reset()
        assertEquals(0, buffer.placedCount)
        assertTrue(buffer.tryPlace(100f, 100f, 180f, 120f, paddingPx = 4f))

        // Growing beyond initial capacity works cleanly
        for (i in 1..20) {
            val y = i * 40f
            assertTrue(buffer.tryPlace(500f, y, 560f, y + 20f, paddingPx = 2f))
        }
        assertEquals(21, buffer.placedCount)
    }
}
