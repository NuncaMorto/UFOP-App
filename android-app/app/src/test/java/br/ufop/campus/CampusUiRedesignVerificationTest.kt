package br.ufop.campus

import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.core.ffi.InMemoryCampusRepository
import br.ufop.campus.core.ffi.formatAcademicAssessmentTitle
import br.ufop.campus.core.map.LabelCollisionBuffer
import br.ufop.campus.core.map.localizeCampusMapCategory
import br.ufop.campus.core.map.sanitizeMapAttributionText
import br.ufop.campus.ui.theme.CampusAccentColor
import br.ufop.campus.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CampusUiRedesignVerificationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun allNineTonalPalettes_meetWcagAaInLightAndDarkAndPreserveEnumContract() {
        assertEquals(
            listOf("GREEN", "RED", "ORANGE", "PURPLE", "YELLOW", "CYAN", "BROWN", "PINK", "BLUE"),
            CampusAccentColor.entries.map { it.storageKey }
        )

        CampusAccentColor.entries.forEach { accent ->
            val light = accent.palette(isDark = false)
            assertTrue(
                "${accent.name} Light onPrimary on primary < 4.5",
                contrastRatio(light.onPrimary, light.primary) >= 4.5
            )
            assertTrue(
                "${accent.name} Light primary on canvas < 4.5",
                contrastRatio(light.primary, light.canvas) >= 4.5
            )
            assertTrue(
                "${accent.name} Light primary on surface1 < 4.5",
                contrastRatio(light.primary, light.surface1) >= 4.5
            )
            assertTrue(
                "${accent.name} Light ink on surface2 < 4.5",
                contrastRatio(light.ink, light.surface2) >= 4.5
            )
            assertTrue(
                "${accent.name} Light ink on canvas < 7.0",
                contrastRatio(light.ink, light.canvas) >= 7.0
            )

            val dark = accent.palette(isDark = true)
            assertTrue(
                "${accent.name} Dark onPrimary on primary < 4.5",
                contrastRatio(dark.onPrimary, dark.primary) >= 4.5
            )
            assertTrue(
                "${accent.name} Dark highlight on canvas < 4.5",
                contrastRatio(dark.highlight, dark.canvas) >= 4.5
            )
            assertTrue(
                "${accent.name} Dark highlight on surface1 < 4.5",
                contrastRatio(dark.highlight, dark.surface1) >= 4.5
            )
            assertTrue(
                "${accent.name} Dark highlight on surface2 < 4.5",
                contrastRatio(dark.highlight, dark.surface2) >= 4.5
            )
            assertTrue(
                "${accent.name} Dark ink on surface1 < 7.0",
                contrastRatio(dark.ink, dark.surface1) >= 7.0
            )
        }
    }

    @Test
    fun virginDefaultIsRed_whileSavedGreenPreferenceRemainsPreserved() {
        val virginRepo = InMemoryCampusRepository(stateFile = File(tempFolder.root, "virgin.json"))
        assertEquals("RED", virginRepo.getAccentColorPreference())
        assertEquals("RED", CampusRepositoryHolder.loadSavedAccentColorFromPrefs(null, virginRepo))

        val savedState = File(tempFolder.root, "saved_green.json")
        val existingRepo = InMemoryCampusRepository(stateFile = savedState)
        existingRepo.setAccentColorPreference("GREEN")
        val reloaded = InMemoryCampusRepository(stateFile = savedState)
        assertEquals("GREEN", reloaded.getAccentColorPreference())
    }

    @Test
    fun formatAcademicAssessmentTitle_normalizesUppercaseAndPreservesAcronymsAndRomanNumerals() {
        assertEquals(
            "Trabalho Circuitos Digitais",
            formatAcademicAssessmentTitle("TRABALHO CIRCUITOS DIGITAIS")
        )
        assertEquals(
            "Prova P1 de EDO e Álgebra II",
            formatAcademicAssessmentTitle("PROVA P1 DE EDO E ALGEBRA II")
        )
        assertEquals(
            "Entrega TP1 · TCC BCC721",
            formatAcademicAssessmentTitle("ENTREGA TP1 · TCC BCC721")
        )
        assertEquals(
            "Cálculo Diferencial e Integral III",
            formatAcademicAssessmentTitle("CALCULO DIFERENCIAL E INTEGRAL III")
        )
    }

    @Test
    fun mapCategoryLocalizerAndAttributionSanitizer_translateAllCategoriesAndHideTechnicalFlags() {
        assertNull(localizeCampusMapCategory(null))
        assertNull(localizeCampusMapCategory("   "))
        assertEquals("Sala de aula", localizeCampusMapCategory("classroom"))
        assertEquals("Laboratório", localizeCampusMapCategory("lab"))
        assertEquals("Biblioteca", localizeCampusMapCategory("library"))
        assertEquals("Alimentação", localizeCampusMapCategory("dining"))
        assertEquals("Secretaria / Docentes", localizeCampusMapCategory("faculty_office"))
        assertEquals("Estacionamento", localizeCampusMapCategory("parking_aisle"))
        assertEquals("Ginásio / Esportes", localizeCampusMapCategory("sports_building"))
        assertEquals("Quadra de areia", localizeCampusMapCategory("beachvolleyball"))

        val sanitized = sanitizeMapAttributionText(
            "© OpenStreetMap (ODbL) · Layouts internos (is_mock=true)"
        )
        assertFalse(sanitized.contains("is_mock", ignoreCase = true))
        assertTrue(sanitized.contains("OpenStreetMap"))
    }

    @Test
    fun labelCollisionBuffer_allowsSecondaryBuildingLabelRightBelowParentButBlocksOverlappingThird() {
        val buffer = LabelCollisionBuffer()
        // Primary label: x in [100, 150], y in [100, 120]
        assertTrue(buffer.tryPlace(100f, 100f, 150f, 120f, paddingPx = 8f))
        // Secondary label immediately below primary: top = 122f (< 120 + 8 = 128 -> would fail if parent wasn't ignored)
        assertTrue(
            buffer.tryPlace(
                left = 80f,
                top = 122f,
                right = 170f,
                bottom = 138f,
                paddingPx = 8f,
                ignoreLastPlaced = true
            )
        )
        // Third label colliding with primary should still be rejected even with ignoreLastPlaced = true
        assertFalse(
            buffer.tryPlace(
                left = 105f,
                top = 104f,
                right = 145f,
                bottom = 118f,
                paddingPx = 8f,
                ignoreLastPlaced = true
            )
        )
    }
}
