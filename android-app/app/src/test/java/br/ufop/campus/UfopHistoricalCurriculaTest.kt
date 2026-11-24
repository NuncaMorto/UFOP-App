package br.ufop.campus

import br.ufop.campus.core.curriculum.DynamicCurriculumCatalog
import br.ufop.campus.core.curriculum.DynamicCurriculumMatrix
import br.ufop.campus.core.curriculum.UfopCourseCurriculum
import br.ufop.campus.core.curriculum.UfopCurriculumCatalog
import br.ufop.campus.core.curriculum.UfopLegacyCatalogData
import br.ufop.campus.core.curriculum.checkCurriculumCompatibility
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class UfopHistoricalCurriculaTest {

    private lateinit var baseCourses: List<UfopCourseCurriculum>
    private lateinit var legacyCatalog: UfopLegacyCatalogData

    @Before
    fun setUp() {
        UfopCurriculumCatalog.setCachedCoursesForTesting(null)
        UfopCurriculumCatalog.setCachedLegacyCatalogForTesting(null)
        DynamicCurriculumCatalog.clearCachesForTesting()

        baseCourses = UfopCurriculumCatalog.loadFromJsonString(
            locateAssetFile("ufop_matrizes_2026_2.json").readText(Charsets.UTF_8)
        )
        legacyCatalog = UfopCurriculumCatalog.loadLegacyCatalogFromJsonString(
            locateAssetFile("ufop_matrizes_antigas.json").readText(Charsets.UTF_8)
        )
        UfopCurriculumCatalog.setCachedCoursesForTesting(baseCourses)
        UfopCurriculumCatalog.setCachedLegacyCatalogForTesting(legacyCatalog)
    }

    @After
    fun tearDown() {
        UfopCurriculumCatalog.setCachedCoursesForTesting(null)
        UfopCurriculumCatalog.setCachedLegacyCatalogForTesting(null)
        DynamicCurriculumCatalog.clearCachesForTesting()
    }

    private fun locateAssetFile(fileName: String): File {
        val userDir = System.getProperty("user.dir").orEmpty()
        val relativePaths = listOf(
            "src/main/assets/$fileName",
            "app/src/main/assets/$fileName",
            "android-app/app/src/main/assets/$fileName"
        )
        val candidates = buildList {
            relativePaths.forEach { add(File(it)) }
            if (userDir.isNotBlank()) {
                val baseDir = File(userDir)
                relativePaths.forEach { add(File(baseDir, it)) }
            }
        }
        return candidates.firstOrNull { it.isFile }
            ?: throw IllegalStateException("Could not locate $fileName from user.dir='$userDir'")
    }

    private fun hasCycle(matrix: DynamicCurriculumMatrix): Boolean {
        val byCode = matrix.courses.associateBy { it.code.uppercase() }
        val visitState = HashMap<String, Int>(matrix.courses.size)
        fun dfs(code: String): Boolean {
            when (visitState[code]) {
                1 -> return true
                2 -> return false
            }
            visitState[code] = 1
            val node = byCode[code] ?: return false
            for (prereq in node.prerequisites) {
                if (dfs(prereq.uppercase())) return true
            }
            visitState[code] = 2
            return false
        }
        return matrix.courses.any { dfs(it.code.uppercase()) }
    }

    @Test
    fun legacyCatalogAsset_loadsAll33Curricula42References54OfficialLinksWithoutBreaking2026Catalog() {
        assertEquals(54, baseCourses.size)
        assertEquals(48, baseCourses.count { it.subjects.isNotEmpty() })
        assertEquals(6, baseCourses.count { it.subjects.isEmpty() })

        assertEquals(1, legacyCatalog.schemaVersion)
        assertEquals(33, legacyCatalog.historicalCurricula.size)
        assertEquals(21, legacyCatalog.historicalCurricula.map { it.code }.toSet().size)
        assertEquals(1650, legacyCatalog.historicalCurricula.sumOf { it.subjects.size })
        assertEquals(42, legacyCatalog.legacyReferences.size)
        assertEquals(39, legacyCatalog.legacyReferences.flatMap { it.courseCodes }.toSet().size)
        assertEquals(54, legacyCatalog.courseOfficialLinks.size)

        // Nenhum campo opcional pode ter virado literal "null" do org.json.JSONObject
        legacyCatalog.historicalCurricula.forEach { hc ->
            assertNotEquals("null", hc.sourceUrl)
            assertTrue("${hc.code} ${hc.curriculumVersion} deve ter disciplinas", hc.subjects.isNotEmpty())
        }
        legacyCatalog.courseOfficialLinks.values.forEach { links ->
            assertNotEquals("null", links.officialMatrixUrl)
            assertNotEquals("null", links.drivePdfUrl)
            assertFalse(links.officialMatrixUrl.isNullOrBlank())
        }

        // Extração de effectiveFromYear/effectiveFromSemester de curriculumVersion no catálogo 2026/2
        val byCode = baseCourses.associateBy { it.code }
        assertEquals(2018 to 2, byCode.getValue("TUR").let { it.effectiveFromYear to it.effectiveFromSemester })
        assertEquals(2019 to 1, byCode.getValue("MUS").let { it.effectiveFromYear to it.effectiveFromSemester })
        assertEquals(2024 to 1, byCode.getValue("MTL").let { it.effectiveFromYear to it.effectiveFromSemester })
        assertEquals(2026 to 2, byCode.getValue("FAR").let { it.effectiveFromYear to it.effectiveFromSemester })
        assertEquals(2026 to 1, byCode.getValue("AUT").let { it.effectiveFromYear to it.effectiveFromSemester })
        assertEquals(2026 to 1, byCode.getValue("IA").let { it.effectiveFromYear to it.effectiveFromSemester })

        // Curada AUT 2024/1 preservada sem duplicação
        val autBase = byCode.getValue("AUT")
        val autVersions = UfopCurriculumCatalog.getCourseVersions(autBase)
        assertEquals(2, autVersions.size)
        assertEquals(
            listOf("2024/1 (Currículo 2)", "2026/2 (currículo 3)"),
            autVersions.map { it.curriculumVersion }
        )
    }

    @Test
    fun courseVersionsAndResolveCourseForStudent_selectsCorrectVersionByEntrySemester() {
        val byCode = baseCourses.associateBy { it.code }

        // FAR: 3 históricas + 2026/2
        val farBase = byCode.getValue("FAR")
        assertEquals(
            listOf("2018/2 (currículo 3)", "2022/1 (currículo 4)", "2024/1 (currículo 4)", "2026/2 (currículo 5)"),
            UfopCurriculumCatalog.getCourseVersions(farBase).map { it.curriculumVersion }
        )
        assertEquals("2018/2 (currículo 3)", UfopCurriculumCatalog.resolveCourseForStudent(farBase, "20.1.1000").curriculumVersion)
        assertEquals("2022/1 (currículo 4)", UfopCurriculumCatalog.resolveCourseForStudent(farBase, "22.2.1000").curriculumVersion)
        assertEquals("2024/1 (currículo 4)", UfopCurriculumCatalog.resolveCourseForStudent(farBase, "24.2.1000").curriculumVersion)
        assertEquals("2026/2 (currículo 5)", UfopCurriculumCatalog.resolveCourseForStudent(farBase, "26.2.1000").curriculumVersion)

        // MED
        val medBase = byCode.getValue("MED")
        assertEquals(2, UfopCurriculumCatalog.getCourseVersions(medBase).size)
        assertEquals("2018/2 (currículo 2)", UfopCurriculumCatalog.resolveCourseForStudent(medBase, "21.1.1000").curriculumVersion)
        assertEquals("2026/2 (currículo 3)", UfopCurriculumCatalog.resolveCourseForStudent(medBase, "26.2.1000").curriculumVersion)

        // AMB
        val ambBase = byCode.getValue("AMB")
        assertEquals(2, UfopCurriculumCatalog.getCourseVersions(ambBase).size)
        assertEquals("2016/2 (currículo 2)", UfopCurriculumCatalog.resolveCourseForStudent(ambBase, "19.1.1000").curriculumVersion)
        assertEquals("2026/2 (currículo 2)", UfopCurriculumCatalog.resolveCourseForStudent(ambBase, "26.2.1000").curriculumVersion)

        // LTI: base vazia no 2026_2 é excluída em favor das 3 versões históricas completas
        val ltiBase = byCode.getValue("LTI")
        val ltiVersions = UfopCurriculumCatalog.getCourseVersions(ltiBase)
        assertEquals(
            listOf("2020/2 (currículo 4)", "2024/1 (currículo 1)", "2025/1 (currículo 2)"),
            ltiVersions.map { it.curriculumVersion }
        )
        assertTrue(ltiVersions.all { it.subjects.isNotEmpty() })
        assertEquals("2020/2 (currículo 4)", UfopCurriculumCatalog.resolveCourseForStudent(ltiBase, "21.1.1000").curriculumVersion)
        assertEquals("2024/1 (currículo 1)", UfopCurriculumCatalog.resolveCourseForStudent(ltiBase, "24.2.1000").curriculumVersion)
        assertEquals("2025/1 (currículo 2)", UfopCurriculumCatalog.resolveCourseForStudent(ltiBase, "25.2.1000").curriculumVersion)
        assertEquals("2025/1 (currículo 2)", UfopCurriculumCatalog.resolveCourseForStudent(ltiBase, "").curriculumVersion)

        // LTP
        val ltpBase = byCode.getValue("LTP")
        assertEquals(4, UfopCurriculumCatalog.getCourseVersions(ltpBase).size)
        assertEquals("2020/2 (currículo 4)", UfopCurriculumCatalog.resolveCourseForStudent(ltpBase, "21.1.1000").curriculumVersion)
        assertEquals("2024/1 (currículo 1)", UfopCurriculumCatalog.resolveCourseForStudent(ltpBase, "24.2.1000").curriculumVersion)
        assertEquals("2025/1 (currículo 2)", UfopCurriculumCatalog.resolveCourseForStudent(ltpBase, "25.2.1000").curriculumVersion)
        assertEquals("2026/2 (currículo 2)", UfopCurriculumCatalog.resolveCourseForStudent(ltpBase, "26.2.1000").curriculumVersion)

        // EFB / EFL: múltiplas versões com mesmo effectiveFrom (2019/1 currículos 1, 2 e 3) selecionam currículo 3 por padrão
        listOf("EFB", "EFL").forEach { code ->
            val base = byCode.getValue(code)
            val versions = UfopCurriculumCatalog.getCourseVersions(base)
            assertEquals(
                listOf("2019/1 (currículo 1)", "2019/1 (currículo 2)", "2019/1 (currículo 3)", "2026/2 (currículo 4)"),
                versions.map { it.curriculumVersion }
            )
            assertEquals("2019/1 (currículo 3)", UfopCurriculumCatalog.resolveCourseForStudent(base, "20.1.1000").curriculumVersion)
            assertEquals("2026/2 (currículo 4)", UfopCurriculumCatalog.resolveCourseForStudent(base, "26.2.1000").curriculumVersion)
        }

        // EST, D06, LTT: base vazia no 2026_2 agora retorna a versão histórica completa diretamente
        val estBase = byCode.getValue("EST")
        assertEquals(1, UfopCurriculumCatalog.getCourseVersions(estBase).size)
        val estResolved = UfopCurriculumCatalog.resolveCourseForStudent(estBase, "23.1.1000")
        assertEquals("2021/1 (currículo 2)", estResolved.curriculumVersion)
        assertEquals(37, estResolved.subjects.size)

        val d06Base = byCode.getValue("D06")
        assertEquals(1, UfopCurriculumCatalog.getCourseVersions(d06Base).size)
        val d06Resolved = UfopCurriculumCatalog.resolveCourseForStudent(d06Base, "23.1.1000")
        assertEquals("2022/2 (currículo 1)", d06Resolved.curriculumVersion)
        assertEquals(43, d06Resolved.subjects.size)

        val lttBase = byCode.getValue("LTT")
        assertEquals(1, UfopCurriculumCatalog.getCourseVersions(lttBase).size)
        val lttResolved = UfopCurriculumCatalog.resolveCourseForStudent(lttBase, "22.1.1000")
        assertEquals("2020/2 (currículo 4)", lttResolved.curriculumVersion)
        assertEquals(56, lttResolved.subjects.size)

        // AUT: preserva comportamento 2024/1-2025/2 -> Currículo 2; 2026/1+ -> Currículo 3
        val autBase = byCode.getValue("AUT")
        assertEquals("2024/1 (Currículo 2)", UfopCurriculumCatalog.resolveCourseForStudent(autBase, "24.1.4001").curriculumVersion)
        assertEquals("2024/1 (Currículo 2)", UfopCurriculumCatalog.resolveCourseForStudent(autBase, "25.2.9999").curriculumVersion)
        assertEquals("2026/2 (currículo 3)", UfopCurriculumCatalog.resolveCourseForStudent(autBase, "26.1.1234").curriculumVersion)
        assertEquals("2026/2 (currículo 3)", UfopCurriculumCatalog.resolveCourseForStudent(autBase, "26.2.1234").curriculumVersion)
    }

    @Test
    fun dynamicCurriculumCatalog_buildsUniqueIdsAndResolvesHistoricalMatrices() {
        val allMatrices = DynamicCurriculumCatalog.buildAllMatrices(baseCourses)
        // 2 curadas AUT + 47 base não-AUT + 33 históricas = 82 matrizes
        assertEquals(82, allMatrices.size)
        assertEquals(2, allMatrices.count { it.courseCode.equals("AUT", ignoreCase = true) })
        assertEquals(82, allMatrices.map { it.id }.distinct().size)
        assertTrue(allMatrices.all { !hasCycle(it) })

        // Unicidade explícita de IDs em EFB, EFL e ECO (mesmo semestre e currículos distintos)
        assertEquals(
            setOf("ufop_efb_2019_1_c1", "ufop_efb_2019_1_c2", "ufop_efb_2019_1_c3", "ufop_efb_2026_2_c4"),
            allMatrices.filter { it.courseCode == "EFB" }.map { it.id }.toSet()
        )
        assertEquals(
            setOf("ufop_efl_2019_1_c1", "ufop_efl_2019_1_c2", "ufop_efl_2019_1_c3", "ufop_efl_2026_2_c4"),
            allMatrices.filter { it.courseCode == "EFL" }.map { it.id }.toSet()
        )
        assertEquals(
            setOf("ufop_eco_2021_1_c2", "ufop_eco_2021_1_c3", "ufop_eco_2026_2_c3"),
            allMatrices.filter { it.courseCode == "ECO" }.map { it.id }.toSet()
        )

        // Retrocompatibilidade de ID legado para progresso salvo
        assertEquals(
            "ufop_far_2026_2_c5",
            DynamicCurriculumCatalog.findById("ufop_far_2026_2", baseCourses)?.id
        )

        // Resolução automática para versões históricas sem falso aviso de "matriz antiga não cadastrada"
        val expectedMatches = listOf(
            Triple("FAR", "20.1.1000", "ufop_far_2018_2_c3"),
            Triple("FAR", "22.2.1000", "ufop_far_2022_1_c4"),
            Triple("FAR", "24.2.1000", "ufop_far_2024_1_c4"),
            Triple("FAR", "26.2.1000", "ufop_far_2026_2_c5"),
            Triple("MED", "21.1.1000", "ufop_med_2018_2_c2"),
            Triple("AMB", "19.1.1000", "ufop_amb_2016_2_c2"),
            Triple("EFB", "20.1.1000", "ufop_efb_2019_1_c3"),
            Triple("EST", "23.1.1000", "ufop_est_2021_1_c2"),
            Triple("D06", "23.1.1000", "ufop_d06_2022_2_c1"),
            Triple("LTI", "24.2.1000", "ufop_lti_2024_1_c1"),
            Triple("LTT", "22.1.1000", "ufop_ltt_2020_2_c4"),
            Triple("AUT", "24.1.4001", "eng_controle_automacao_2024_1"),
            Triple("AUT", "26.1.1234", "eng_controle_automacao_2026_1")
        )
        expectedMatches.forEach { (courseCode, reg, expectedMatrixId) ->
            val res = DynamicCurriculumCatalog.resolveMatrixForStudent(
                courseName = courseCode,
                registrationNumber = reg,
                catalogCourses = baseCourses
            )
            assertTrue("$courseCode ($reg) deveria casar curso", res.isCourseMatched)
            assertTrue("$courseCode ($reg) deveria ter correspondência exata", res.isExactVersionMatch)
            assertEquals("$courseCode ($reg)", expectedMatrixId, res.matrix.id)
            assertFalse(
                "$courseCode ($reg) não deveria gerar aviso de matriz não cadastrada",
                res.note.orEmpty().contains("não cadastrada", ignoreCase = true)
            )
        }

        // Cursos sem detalhamento (IA e D05) mantêm estado pendente
        listOf("IA", "D05").forEach { pendingCode ->
            val res = DynamicCurriculumCatalog.resolveMatrixForStudent(
                courseName = pendingCode,
                registrationNumber = "26.1.1000",
                catalogCourses = baseCourses
            )
            assertFalse(res.isCourseMatched)
            assertTrue(res.note.orEmpty().contains("pendente", ignoreCase = true))
        }
    }

    @Test
    fun checkCurriculumCompatibility_andLegacyReferencesAndOfficialLinks_workAccurately() {
        val byCode = baseCourses.associateBy { it.code }

        val coveredCases = listOf(
            "FAR" to "20.1.1000",
            "FAR" to "22.2.1000",
            "FAR" to "24.2.1000",
            "MED" to "21.1.1000",
            "AMB" to "19.1.1000",
            "EFB" to "20.1.1000",
            "LTI" to "24.2.1000",
            "EST" to "23.1.1000",
            "D06" to "23.1.1000",
            "LTT" to "22.1.1000",
            "AUT" to "24.1.4001",
            "AUT" to "26.1.1234"
        )
        coveredCases.forEach { (code, reg) ->
            val resolved = UfopCurriculumCatalog.resolveCourseForStudent(byCode.getValue(code), reg)
            val compat = checkCurriculumCompatibility(
                registrationNumber = reg,
                earliestTranscriptSemester = null,
                transcriptCourseCodes = emptySet(),
                course = resolved
            )
            assertFalse(
                "$code ($reg -> ${resolved.curriculumVersion}) não deveria marcar falso legado",
                compat.isLegacyMatrixMismatch
            )
        }

        // Ingresso anterior à versão histórica mais antiga continua sinalizando modo consulta
        val farVeryOld = UfopCurriculumCatalog.resolveCourseForStudent(byCode.getValue("FAR"), "16.1.1000")
        assertTrue(
            checkCurriculumCompatibility("16.1.1000", null, emptySet(), farVeryOld).isLegacyMatrixMismatch
        )

        // Curso sem matriz histórica cadastrada continua sinalizando legado
        val comOld = UfopCurriculumCatalog.resolveCourseForStudent(byCode.getValue("COM"), "24.1.1000")
        assertTrue(
            checkCurriculumCompatibility("24.1.1000", null, emptySet(), comOld).isLegacyMatrixMismatch
        )

        // legacyReferences e officialLinks
        val amb2019Refs = UfopCurriculumCatalog.getLegacyReferences("AMB", entryYear = 2019, entrySemester = 1)
        assertEquals(1, amb2019Refs.size)
        assertEquals("Grade Curricular 2016/2", amb2019Refs.first().versionTitle)
        assertTrue(UfopCurriculumCatalog.getLegacyReferences("AMB", entryYear = 2026, entrySemester = 2).isEmpty())

        val farRefs = UfopCurriculumCatalog.getLegacyReferences("FAR", entryYear = 2020, entrySemester = 1)
        assertTrue(farRefs.isNotEmpty())

        val iaRefs = UfopCurriculumCatalog.getLegacyReferences("IA")
        assertEquals(1, iaRefs.size)
        assertTrue(iaRefs.first().url.startsWith("https://"))

        val iaLinks = UfopCurriculumCatalog.getOfficialLinks("IA")
        assertNotNull(iaLinks)
        assertTrue(iaLinks!!.officialMatrixUrl!!.startsWith("https://"))
        assertNull(iaLinks.drivePdfUrl)

        val d05Links = UfopCurriculumCatalog.getOfficialLinks("D05")
        assertNotNull(d05Links)
        assertTrue(d05Links!!.officialMatrixUrl!!.startsWith("https://"))
        assertNull(d05Links.drivePdfUrl)

        baseCourses.forEach { course ->
            val links = UfopCurriculumCatalog.getOfficialLinks(course.code)
            assertNotNull("Official links ausente para ${course.code}", links)
            assertFalse(links!!.officialMatrixUrl.isNullOrBlank())
        }
    }
}
