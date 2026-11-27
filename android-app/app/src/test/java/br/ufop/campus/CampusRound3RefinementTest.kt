package br.ufop.campus

import androidx.compose.ui.graphics.Color
import br.ufop.campus.core.ffi.InMemoryCampusRepository
import br.ufop.campus.core.ffi.extractDepartmentAcronym
import br.ufop.campus.ui.curriculum.formatCurriculumVersionLabel
import br.ufop.campus.ui.curriculum.formatHoursPtBr
import br.ufop.campus.ui.curriculum.normalizeCurriculumDisplayName
import br.ufop.campus.ui.theme.CampusAccentColor
import br.ufop.campus.ui.theme.DarkSurface
import br.ufop.campus.ui.theme.LightSurface
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.StatusCancelledAmber
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.contrastRatio
import br.ufop.campus.ui.theme.parseHexColor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CampusRound3RefinementTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun extractDepartmentAcronym_handlesSeparatorsWhitespaceAndEmpty() {
        assertEquals("DECOM", extractDepartmentAcronym("DECOM · Ciência da Computação"))
        assertEquals("DEMAT", extractDepartmentAcronym("DEMAT - Departamento de Matemática"))
        assertEquals("DECSI", extractDepartmentAcronym("DECSI / ICEA"))
        assertEquals("DEFIS", extractDepartmentAcronym("DEFIS – Física"))
        assertEquals("DEELT", extractDepartmentAcronym("DEELT — Engenharia Elétrica"))
        assertEquals("DECOM", extractDepartmentAcronym("  DECOM  "))
        assertEquals("DECOM", extractDepartmentAcronym("· DECOM · Ciência da Computação"))
        assertEquals("", extractDepartmentAcronym(""))
        assertEquals("", extractDepartmentAcronym("   "))
    }

    @Test
    fun attendanceIndicatorStyle_persistsAcrossReloadSnapshotAndWipe() {
        val stateFile = File(tempFolder.root, "attendance_style_state.json")
        val repo = InMemoryCampusRepository(stateFile = stateFile)

        // Padrão inicial deve ser BADGE
        assertEquals("BADGE", repo.getAttendanceIndicatorStyle())

        // Normalização de entrada case-insensitive + persistência em disco
        repo.setAttendanceIndicatorStyle("  card_border  ")
        assertEquals("CARD_BORDER", repo.getAttendanceIndicatorStyle())

        val reloaded = InMemoryCampusRepository(stateFile = stateFile)
        assertEquals("CARD_BORDER", reloaded.getAttendanceIndicatorStyle())

        // Round-trip via snapshot completo (.ufopbackup)
        val snapshotJson = reloaded.exportFullAccountSnapshotJson()
        val parsed = JSONObject(snapshotJson)
        assertEquals("CARD_BORDER", parsed.getString("attendanceIndicatorStyle"))

        val targetRepo = InMemoryCampusRepository(
            stateFile = File(tempFolder.root, "attendance_style_restored.json")
        )
        assertEquals("BADGE", targetRepo.getAttendanceIndicatorStyle())
        assertTrue(targetRepo.restoreFullAccountSnapshotJson(snapshotJson))
        assertEquals("CARD_BORDER", targetRepo.getAttendanceIndicatorStyle())

        // Valores inválidos caem no padrão seguro BADGE
        targetRepo.setAttendanceIndicatorStyle("UNKNOWN_STYLE")
        assertEquals("BADGE", targetRepo.getAttendanceIndicatorStyle())

        // Logout limpa preferência de volta para BADGE
        targetRepo.setAttendanceIndicatorStyle("CARD_BORDER")
        targetRepo.wipeAllUserDataForLogout()
        assertEquals("BADGE", targetRepo.getAttendanceIndicatorStyle())
    }

    @Test
    fun selectedCurriculumVersionsByCourse_persistsAcrossReloadSnapshotAndWipe() {
        val stateFile = File(tempFolder.root, "curriculum_versions_state.json")
        val repo = InMemoryCampusRepository(stateFile = stateFile)

        assertNull(repo.getSelectedCurriculumVersion("BCC"))
        assertTrue(repo.getAllSelectedCurriculumVersions().isEmpty())

        repo.setSelectedCurriculumVersion("bcc", "2026/2 (currículo 3)")
        repo.setSelectedCurriculumVersion("ECA", "2020/1 (currículo 2)")
        assertEquals("2026/2 (currículo 3)", repo.getSelectedCurriculumVersion("BCC"))
        assertEquals("2020/1 (currículo 2)", repo.getSelectedCurriculumVersion("eca"))

        // Recarrega do disco e verifica manutenção definitiva sem reset
        val reloaded = InMemoryCampusRepository(stateFile = stateFile)
        assertEquals("2026/2 (currículo 3)", reloaded.getSelectedCurriculumVersion("BCC"))
        assertEquals("2020/1 (currículo 2)", reloaded.getSelectedCurriculumVersion("ECA"))

        // Sobrescreve versão de um curso e exporta snapshot
        reloaded.setSelectedCurriculumVersion("BCC", "2026/2 (currículo 4)")
        val snapshotJson = reloaded.exportFullAccountSnapshotJson()

        val restoredRepo = InMemoryCampusRepository(
            stateFile = File(tempFolder.root, "curriculum_versions_restored.json")
        )
        assertTrue(restoredRepo.restoreFullAccountSnapshotJson(snapshotJson))
        assertEquals("2026/2 (currículo 4)", restoredRepo.getSelectedCurriculumVersion("BCC"))
        assertEquals("2020/1 (currículo 2)", restoredRepo.getSelectedCurriculumVersion("ECA"))
        assertEquals(2, restoredRepo.getAllSelectedCurriculumVersions().size)

        // Valor vazio remove a entrada salva daquele curso
        restoredRepo.setSelectedCurriculumVersion("ECA", "   ")
        assertNull(restoredRepo.getSelectedCurriculumVersion("ECA"))
        assertEquals("2026/2 (currículo 4)", restoredRepo.getSelectedCurriculumVersion("BCC"))

        // Logout limpa todas as versões salvas
        restoredRepo.wipeAllUserDataForLogout()
        assertTrue(restoredRepo.getAllSelectedCurriculumVersions().isEmpty())
    }

    @Test
    fun curriculumFormattingHelpers_normalizeCapitalizationAndHours() {
        assertEquals(
            "2026/2 (Currículo 3)",
            formatCurriculumVersionLabel("2026/2 (currículo 3)", 2026, 2)
        )
        assertEquals(
            "2020/1 (Currículo 2)",
            formatCurriculumVersionLabel("2020/1 (curriculo 2)", 2020, 1)
        )
        assertEquals(
            "2026/2",
            formatCurriculumVersionLabel("", 2026, 2)
        )
        assertEquals(
            "Expressão Gráfica",
            normalizeCurriculumDisplayName("Expressao Grafica")
        )
        assertEquals(
            "EXPRESSÃO GRÁFICA",
            normalizeCurriculumDisplayName("EXPRESSAO GRAFICA")
        )
        assertEquals("3.610", formatHoursPtBr(3610))
    }

    @Test
    fun colorCache_isDeterministic_andGuaranteesWcagContrastAcrossAllPalettes() {
        val sampleColors = listOf(
            StatusAttendingGreen,
            StatusCancelledAmber,
            StatusAbsentRed,
            Color(0xFF999999),
            Color(0xFFFDE047),
            Color(0xFF334155)
        )

        CampusAccentColor.entries.forEach { accent ->
            listOf(false, true).forEach { isDark ->
                val surface = accent.palette(isDark).surface1
                val defaultSurface = if (isDark) DarkSurface else LightSurface

                sampleColors.forEach { base ->
                    val first = accessibleTextColor(base, isDarkSurface = isDark, surfaceColor = surface)
                    val second = accessibleTextColor(base, isDarkSurface = isDark, surfaceColor = surface)
                    assertEquals(first, second)
                    assertTrue(
                        "Contrast < 4.5 for $base on ${accent.storageKey} (dark=$isDark): ${contrastRatio(first, surface)}",
                        contrastRatio(first, surface) >= 4.5
                    )

                    val defaultResolved = accessibleTextColor(base, isDarkSurface = isDark)
                    assertTrue(
                        "Contrast < 4.5 for $base on default surface (dark=$isDark)",
                        contrastRatio(defaultResolved, defaultSurface) >= 4.5
                    )
                }
            }
        }

        // parseHexColor determinístico e sem colisão de fallback
        val parsed6 = parseHexColor("#2E7D32")
        assertEquals(parsed6, parseHexColor("#2E7D32"))
        assertEquals(Color(0xFF2E7D32), parsed6)
        assertEquals(Color(0x802E7D32), parseHexColor("802E7D32"))

        val fallbackA = parseHexColor("invalid", fallback = Color.Red)
        val fallbackB = parseHexColor("invalid", fallback = Color.Blue)
        assertEquals(Color.Red, fallbackA)
        assertEquals(Color.Blue, fallbackB)
    }
}
