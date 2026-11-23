package br.ufop.campus

import br.ufop.campus.ui.theme.CampusFoldPosture
import br.ufop.campus.ui.theme.CampusWindowHeightClass
import br.ufop.campus.ui.theme.CampusWindowWidthClass
import br.ufop.campus.ui.theme.computeCampusAdaptiveLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusAdaptiveLayoutTest {

    @Test
    fun computeCampusAdaptiveLayout_classifiesWidthAndHeightBreakpointsAccurately() {
        // 360dp compact phone
        val compact360 = computeCampusAdaptiveLayout(
            widthDp = 360,
            heightDp = 479,
            smallestWidthDp = 360
        )
        assertEquals(CampusWindowWidthClass.COMPACT, compact360.widthClass)
        assertEquals(CampusWindowHeightClass.COMPACT, compact360.heightClass)
        assertFalse(compact360.useNavigationRail)
        assertFalse(compact360.useExpandedRail)
        assertFalse(compact360.useTwoPaneLayout)
        assertEquals(136, compact360.bottomContentPaddingDp)
        assertEquals(Int.MAX_VALUE, compact360.readableMaxWidthDp)

        // 599dp upper boundary of COMPACT
        val compact599 = computeCampusAdaptiveLayout(
            widthDp = 599,
            heightDp = 800,
            smallestWidthDp = 390
        )
        assertEquals(CampusWindowWidthClass.COMPACT, compact599.widthClass)
        assertEquals(CampusWindowHeightClass.MEDIUM, compact599.heightClass)
        assertFalse(compact599.useNavigationRail)
        assertFalse(compact599.useTwoPaneLayout)

        // 600dp lower boundary of MEDIUM
        val medium600 = computeCampusAdaptiveLayout(
            widthDp = 600,
            heightDp = 899,
            smallestWidthDp = 600
        )
        assertEquals(CampusWindowWidthClass.MEDIUM, medium600.widthClass)
        assertEquals(CampusWindowHeightClass.MEDIUM, medium600.heightClass)
        assertTrue(medium600.useNavigationRail)
        assertFalse(medium600.useExpandedRail)
        assertFalse(medium600.useTwoPaneLayout)
        assertEquals(28, medium600.bottomContentPaddingDp)
        assertEquals(760, medium600.readableMaxWidthDp)

        // 839dp upper boundary of MEDIUM
        val medium839 = computeCampusAdaptiveLayout(
            widthDp = 839,
            heightDp = 900,
            smallestWidthDp = 600
        )
        assertEquals(CampusWindowWidthClass.MEDIUM, medium839.widthClass)
        assertEquals(CampusWindowHeightClass.EXPANDED, medium839.heightClass)
        assertTrue(medium839.useNavigationRail)
        assertFalse(medium839.useExpandedRail)
        assertFalse(medium839.useTwoPaneLayout)

        // 840dp lower boundary of EXPANDED
        val expanded840 = computeCampusAdaptiveLayout(
            widthDp = 840,
            heightDp = 960,
            smallestWidthDp = 720
        )
        assertEquals(CampusWindowWidthClass.EXPANDED, expanded840.widthClass)
        assertEquals(CampusWindowHeightClass.EXPANDED, expanded840.heightClass)
        assertTrue(expanded840.useNavigationRail)
        assertFalse(expanded840.useExpandedRail)
        assertTrue(expanded840.useTwoPaneLayout)
        assertEquals(28, expanded840.bottomContentPaddingDp)
        assertEquals(1120, expanded840.readableMaxWidthDp)

        // 1200dp wide tablet / desktop window (>= 1000dp enables useExpandedRail)
        val expanded1200 = computeCampusAdaptiveLayout(
            widthDp = 1200,
            heightDp = 800,
            smallestWidthDp = 800
        )
        assertEquals(CampusWindowWidthClass.EXPANDED, expanded1200.widthClass)
        assertTrue(expanded1200.useNavigationRail)
        assertTrue(expanded1200.useExpandedRail)
        assertTrue(expanded1200.useTwoPaneLayout)
    }

    @Test
    fun computeCampusAdaptiveLayout_handlesFoldPosturesAndHingeDimensions() {
        // Foldable in BOOK_VERTICAL_HINGE posture with width >= 600dp enables 2-pane layout even in MEDIUM widthClass
        val bookFoldable = computeCampusAdaptiveLayout(
            widthDp = 680,
            heightDp = 840,
            smallestWidthDp = 600,
            foldPosture = CampusFoldPosture.BOOK_VERTICAL_HINGE,
            hingeWidthDp = 24,
            hingeLeftOffsetDp = 328
        )
        assertEquals(CampusWindowWidthClass.MEDIUM, bookFoldable.widthClass)
        assertEquals(CampusFoldPosture.BOOK_VERTICAL_HINGE, bookFoldable.foldPosture)
        assertTrue(bookFoldable.useTwoPaneLayout)
        assertEquals(24, bookFoldable.hingeWidthDp)
        assertEquals(328, bookFoldable.hingeLeftOffsetDp)

        // Foldable in TABLETOP_HORIZONTAL_HINGE posture in MEDIUM widthClass stays single-pane horizontally
        val tabletopFoldable = computeCampusAdaptiveLayout(
            widthDp = 700,
            heightDp = 840,
            smallestWidthDp = 600,
            foldPosture = CampusFoldPosture.TABLETOP_HORIZONTAL_HINGE,
            hingeWidthDp = 0,
            hingeLeftOffsetDp = null
        )
        assertEquals(CampusWindowWidthClass.MEDIUM, tabletopFoldable.widthClass)
        assertEquals(CampusFoldPosture.TABLETOP_HORIZONTAL_HINGE, tabletopFoldable.foldPosture)
        assertFalse(tabletopFoldable.useTwoPaneLayout)
        assertNull(tabletopFoldable.hingeLeftOffsetDp)
    }

    @Test
    fun disciplineGridColumns_adaptsToWidthClassAndSideDetailState() {
        val compact = computeCampusAdaptiveLayout(widthDp = 390, heightDp = 844, smallestWidthDp = 390)
        assertEquals(2, compact.disciplineGridColumns(hasSideDetailOpen = false))
        assertEquals(2, compact.disciplineGridColumns(hasSideDetailOpen = true))

        val medium = computeCampusAdaptiveLayout(widthDp = 720, heightDp = 1024, smallestWidthDp = 720)
        assertEquals(3, medium.disciplineGridColumns(hasSideDetailOpen = false))
        assertEquals(3, medium.disciplineGridColumns(hasSideDetailOpen = true))

        val expanded960 = computeCampusAdaptiveLayout(widthDp = 960, heightDp = 800, smallestWidthDp = 720)
        assertEquals(3, expanded960.disciplineGridColumns(hasSideDetailOpen = false))
        assertEquals(2, expanded960.disciplineGridColumns(hasSideDetailOpen = true))

        val expanded1200 = computeCampusAdaptiveLayout(widthDp = 1200, heightDp = 800, smallestWidthDp = 800)
        assertEquals(4, expanded1200.disciplineGridColumns(hasSideDetailOpen = false))
        assertEquals(2, expanded1200.disciplineGridColumns(hasSideDetailOpen = true))
    }

    @Test
    fun shouldForceScreenOrientation_allowsFreeRotationOnTabletsAndMultiWindow() {
        // Phone in full screen (smallestWidthDp < 600, not multi-window) -> true
        val phoneFullScreen = computeCampusAdaptiveLayout(
            widthDp = 390,
            heightDp = 844,
            smallestWidthDp = 390,
            isInMultiWindowMode = false
        )
        assertTrue(phoneFullScreen.shouldForceScreenOrientation)

        // Phone in split-screen / multi-window -> false (never force orientation in multi-window)
        val phoneMultiWindow = computeCampusAdaptiveLayout(
            widthDp = 390,
            heightDp = 420,
            smallestWidthDp = 390,
            isInMultiWindowMode = true
        )
        assertFalse(phoneMultiWindow.shouldForceScreenOrientation)

        // Tablet / unfolded foldable (smallestWidthDp >= 600) -> false
        val tablet = computeCampusAdaptiveLayout(
            widthDp = 800,
            heightDp = 1280,
            smallestWidthDp = 800,
            isInMultiWindowMode = false
        )
        assertFalse(tablet.shouldForceScreenOrientation)
    }
}
