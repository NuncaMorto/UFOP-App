package br.ufop.campus

import br.ufop.campus.core.curriculum.DynamicCurriculumCatalog
import br.ufop.campus.core.curriculum.DynamicGridEngine
import br.ufop.campus.core.curriculum.UfopCourseCurriculum
import br.ufop.campus.core.curriculum.UfopCurriculumCatalog
import br.ufop.campus.core.ffi.InMemoryCampusRepository
import br.ufop.campus.core.ffi.StudentProfile
import br.ufop.campus.ui.profile.ProfileDestination
import br.ufop.campus.ui.profile.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class ProfilePerformanceOptimizationTest {

    private lateinit var catalogCourses: List<UfopCourseCurriculum>

    @Before
    fun setUp() {
        UfopCurriculumCatalog.setCachedCoursesForTesting(null)
        DynamicCurriculumCatalog.clearCachesForTesting()
        catalogCourses = loadCatalogCoursesFromAsset()
    }

    @After
    fun tearDown() {
        UfopCurriculumCatalog.setCachedCoursesForTesting(null)
        DynamicCurriculumCatalog.clearCachesForTesting()
    }

    private fun loadCatalogCoursesFromAsset(): List<UfopCourseCurriculum> {
        val candidates = listOf(
            File("app/src/main/assets/ufop_matrizes_2026_2.json"),
            File("src/main/assets/ufop_matrizes_2026_2.json"),
            File("android-app/app/src/main/assets/ufop_matrizes_2026_2.json")
        )
        val assetFile = candidates.firstOrNull { it.isFile }
        assertNotNull("Asset ufop_matrizes_2026_2.json não encontrado para testes JVM.", assetFile)
        val loaded = UfopCurriculumCatalog.loadFromJsonString(assetFile!!.readText(Charsets.UTF_8))
        assertTrue("Catálogo UFOP deve conter cursos válidos.", loaded.size >= 50)
        return loaded
    }

    @Test
    fun peekCachedCourses_returnsNullBeforeWarmAndMatchesResolutionAfterWarm() {
        assertNull(UfopCurriculumCatalog.peekCachedCourses())

        UfopCurriculumCatalog.setCachedCoursesForTesting(catalogCourses)
        val peeked = UfopCurriculumCatalog.peekCachedCourses()
        assertNotNull(peeked)
        assertSame(catalogCourses, peeked)

        val directResolution = DynamicCurriculumCatalog.resolveMatrixForStudent(
            courseName = "Ciência da Computação",
            registrationNumber = "26.1.4001",
            catalogCourses = catalogCourses,
            campusHint = "Ouro Preto"
        )
        val cachedResolution = DynamicCurriculumCatalog.resolveMatrixForStudent(
            courseName = "Ciência da Computação",
            registrationNumber = "26.1.4001",
            catalogCourses = peeked!!,
            campusHint = "Ouro Preto"
        )

        assertTrue(cachedResolution.isCourseMatched)
        assertEquals(directResolution.matrix.id, cachedResolution.matrix.id)
        assertEquals(directResolution.matrix.courses.size, cachedResolution.matrix.courses.size)
        assertEquals(directResolution.note, cachedResolution.note)
    }

    @Test
    fun resolveMatrixForStudent_avoidsFullCatalogRebuildAndCachesBuildAllMatrices() {
        DynamicCurriculumCatalog.clearCachesForTesting()

        // 1. Curso curado (AUT) não deve acionar buildAllMatrices
        val aut2024 = DynamicCurriculumCatalog.resolveMatrixForStudent(
            courseName = "Engenharia de Controle e Automação",
            registrationNumber = "24.2.1165",
            catalogCourses = catalogCourses,
            campusHint = "Ouro Preto"
        )
        assertTrue(aut2024.isCourseMatched)
        assertEquals("eng_controle_automacao_2024_1", aut2024.matrix.id)
        assertEquals(0, DynamicCurriculumCatalog.buildAllMatricesBuildCountForTest)

        // 2. Curso do catálogo com desambiguação de campus (Produção JM vs OP) também resolve sem construir os 53 outros cursos
        val prodJm = DynamicCurriculumCatalog.resolveMatrixForStudent(
            courseName = "Engenharia de Produção",
            registrationNumber = "26.1.2200",
            catalogCourses = catalogCourses,
            campusHint = "João Monlevade"
        )
        val prodOp = DynamicCurriculumCatalog.resolveMatrixForStudent(
            courseName = "Engenharia de Produção",
            registrationNumber = "26.1.2201",
            catalogCourses = catalogCourses,
            campusHint = "Ouro Preto"
        )
        assertTrue(prodJm.isCourseMatched)
        assertTrue(prodOp.isCourseMatched)
        assertEquals("PJM", prodJm.matrix.courseCode)
        assertEquals("PRO", prodOp.matrix.courseCode)
        assertEquals(0, DynamicCurriculumCatalog.buildAllMatricesBuildCountForTest)

        // 3. Chamadas repetidas e concorrentes de buildAllMatrices reutilizam o cache thread-safe
        val executor = Executors.newFixedThreadPool(4)
        try {
            val tasks = (1..8).map {
                Callable { DynamicCurriculumCatalog.buildAllMatrices(catalogCourses) }
            }
            val results = executor.invokeAll(tasks).map { it.get() }
            val first = results.first()
            assertEquals(49, first.size)
            results.forEach { list ->
                assertSame(first, list)
            }
            assertEquals(1, DynamicCurriculumCatalog.buildAllMatricesBuildCountForTest)
        } finally {
            executor.shutdownNow()
        }

        // 4. DynamicGridEngine.summarize(alreadySanitized = true) coincide com summarize padrão
        val autMatrix = aut2024.matrix
        val rawCodes = setOf("bcc701", "mtm122", "mtm730", "bcc702", "CAT999_INVALIDO")
        val sanitized = DynamicGridEngine.sanitizeCompleted(autMatrix, rawCodes)
        val summaryFromRaw = DynamicGridEngine.summarize(autMatrix, rawCodes)
        val summaryFromSanitized = DynamicGridEngine.summarize(
            matrix = autMatrix,
            completedCodes = sanitized,
            alreadySanitized = true
        )
        assertEquals(summaryFromRaw, summaryFromSanitized)
        assertEquals(4, summaryFromSanitized.completedCount)
    }

    @Test
    fun getAcademicCreReport_handlesLargeTranscriptAccuratelyWithIndexedLookup() {
        val repo = InMemoryCampusRepository().apply { wipeAllUserDataForLogout() }

        // Cria 8 disciplinas no semestre atual (2026.2)
        val currentSemester = "2026.2"
        val currentCodes = listOf(
            "BCC701" to "Programação de Computadores I",
            "MTM122" to "Cálculo Diferencial e Integral I",
            "FIS106" to "Fundamentos de Mecânica",
            "CAT176" to "Análise de Circuitos Elétricos",
            "EST202" to "Estatística e Probabilidade",
            "QUI701" to "Química Fundamental",
            "MEC124" to "Fenômenos de Transporte",
            "PRO224" to "Economia da Engenharia"
        )
        val createdIds = mutableListOf<String>()
        currentCodes.forEachIndexed { idx, (code, name) ->
            val created = repo.createCourse(
                semester = currentSemester,
                departmentId = "decom",
                name = "$code - $name",
                professor = "Prof. Docente ${idx + 1}",
                workloadHours = 60
            )
            repo.updateCourseAbsences(created.courseId, idx + 1)
            createdIds.add(created.courseId)
        }

        // Monta histórico grande com 42 registros em 7 semestres anteriores (6 disciplinas por semestre) + 2 em curso em 2026.2
        val pastSemesters = listOf("2023.1", "2023.2", "2024.1", "2024.2", "2025.1", "2025.2", "2026.1")
        var expectedWeightedSum = 0.0
        var expectedGradedHours = 0
        var expectedApprovedHours = 0
        var expectedApprovedCount = 0
        var expectedFailedGradeCount = 0

        val transcriptText = buildString {
            appendLine("UNIVERSIDADE FEDERAL DE OURO PRETO")
            appendLine("HISTORICO ESCOLAR")
            appendLine("Nome: Estudante Benchmark UFOP")
            appendLine("Matrícula: 23.1.4021")
            appendLine("CURSO: ENGENHARIA DE CONTROLE E AUTOMACAO")
            appendLine("CARGA CURSO 3610")
            pastSemesters.forEachIndexed { semIdx, sem ->
                val semSlash = sem.replace('.', '/')
                for (i in 1..6) {
                    val code = "HIS${semIdx + 1}0$i"
                    val ch = if (i % 3 == 0) 30 else 60
                    val totalClasses = (ch * 12) / 10
                    val isFail = (semIdx == 1 && i == 6)
                    val grade = if (isFail) 4.5 else 6.5 + ((semIdx + i) % 4) * 0.8
                    val roundedGrade = (grade * 10.0).roundToInt() / 10.0
                    val status = if (isFail) "RN" else "AP"
                    val gradeStr = String.format(java.util.Locale.US, "%.1f", roundedGrade).replace('.', ',')
                    appendLine("$semSlash $code - Disciplina Histórica $semIdx-$i $ch/$totalClasses $gradeStr 2/$totalClasses $status")

                    expectedWeightedSum += roundedGrade * ch
                    expectedGradedHours += ch
                    if (status == "AP") {
                        expectedApprovedHours += ch
                        expectedApprovedCount += 1
                    } else {
                        expectedFailedGradeCount += 1
                    }
                }
            }
            // Inclui 2 disciplinas em curso (CS) que coincidem com as criadas em 2026.2
            val currentSemSlash = currentSemester.replace('.', '/')
            appendLine("$currentSemSlash BCC701 - Programação de Computadores I 60/72 - 0/72 CS")
            appendLine("$currentSemSlash MTM122 - Cálculo Diferencial e Integral I 60/72 - 0/72 CS")
        }

        val importedReport = repo.importAcademicTranscriptText(transcriptText)
        val report = repo.getAcademicCreReport()

        assertEquals(importedReport, report)
        val expectedCre = ((expectedWeightedSum / expectedGradedHours) * 100.0).roundToInt() / 100.0
        assertEquals(expectedCre, report.overallCre, 0.001)
        assertEquals(expectedApprovedHours, report.completedHours)
        assertEquals(expectedApprovedCount, report.totalApproved)
        assertEquals(expectedFailedGradeCount, report.totalFailedByGrade)
        assertEquals(0, report.totalFailedByAbsence)

        // 7 semestres passados + 1 semestre atual (2026.2) com exatamente 8 disciplinas (2 mescladas + 6 adicionadas)
        assertEquals(8, report.semesters.size)
        val currentSemSummary = report.semesters.first { it.semester == currentSemester }
        assertEquals(8, currentSemSummary.courses.size)

        // Verifica que faltas ao vivo das disciplinas em curso foram sincronizadas na mesclagem indexada
        val bccEntry = currentSemSummary.courses.first { it.code == "BCC701" }
        val mtmEntry = currentSemSummary.courses.first { it.code == "MTM122" }
        assertEquals(1, bccEntry.absences)
        assertEquals(2, mtmEntry.absences)
    }

    @Test
    fun profileViewModel_preservesCacheOnUnrelatedMutationsAndUpdatesOnRelevantChanges() = runBlocking {
        UfopCurriculumCatalog.setCachedCoursesForTesting(catalogCourses)
        val repo = InMemoryCampusRepository().apply { wipeAllUserDataForLogout() }
        repo.saveStudentProfile(
            StudentProfile(
                fullName = "Estudante Perfil",
                registrationNumber = "24.2.1165",
                courseName = "Engenharia de Controle e Automação",
                campusName = "Ouro Preto",
                activeSemester = "2026.2",
                onboardingCompleted = true
            )
        )
        repo.createCourse(
            semester = "2026.2",
            departmentId = "decom",
            name = "BCC701 - Programação de Computadores I",
            professor = "Prof. Ana",
            workloadHours = 60
        )

        val viewModel = ProfileViewModel(
            repository = repo,
            catalogProvider = { UfopCurriculumCatalog.peekCachedCourses() },
            ioDispatcher = Dispatchers.Unconfined,
            computeDispatcher = Dispatchers.Unconfined
        )

        try {
            viewModel.refreshNow()
            val initialState = viewModel.uiState.value
            assertTrue(initialState.isCatalogLoaded)
            assertEquals("eng_controle_automacao_2024_1", initialState.dynamicGridResolution.matrix.id)
            assertEquals(0, initialState.dynamicGridSummary.completedCount)
            // Na raiz do Perfil (single-pane), currentDisciplines não é carregado antecipadamente
            assertTrue(initialState.currentDisciplines.isEmpty())

            val resolutionsAfterInit = viewModel.matrixResolutionCount
            val summariesAfterInit = viewModel.gridSummaryComputationCount

            // Mutação irrelevante para a matriz (compromisso na Agenda) não deve re-resolver matriz nem resumo da grade
            repo.createCommitment(
                title = "Estudo no ICEB",
                description = "Lista de exercícios",
                commitmentDate = "2026-11-24",
                startTime = "14:00",
                endTime = "16:00",
                location = "Biblioteca"
            )
            viewModel.refreshNow()

            assertEquals(resolutionsAfterInit, viewModel.matrixResolutionCount)
            assertEquals(summariesAfterInit, viewModel.gridSummaryComputationCount)

            // Mutação nas concluídas da Grade Dinâmica atualiza resumo sem re-resolver matriz
            repo.saveDynamicGridCompletedCodes("eng_controle_automacao_2024_1", setOf("BCC701", "MTM122"))
            viewModel.refreshNow()
            assertEquals(resolutionsAfterInit, viewModel.matrixResolutionCount)
            assertEquals(summariesAfterInit + 1, viewModel.gridSummaryComputationCount)
            assertEquals(2, viewModel.uiState.value.dynamicGridSummary.completedCount)
            assertEquals(150, viewModel.uiState.value.dynamicGridSummary.completedHours)

            // Alteração do curso no perfil deve re-resolver a matriz corretamente
            val updatedProfile = repo.getStudentProfile().copy(
                courseName = "Ciência da Computação",
                registrationNumber = "26.1.4010"
            )
            repo.saveStudentProfile(updatedProfile)
            viewModel.refreshNow()
            assertEquals(resolutionsAfterInit + 1, viewModel.matrixResolutionCount)
            assertEquals("COM", viewModel.uiState.value.dynamicGridResolution.matrix.courseCode)

            // Carregamento sob demanda das sub-telas METRICS_CRE e SETTINGS
            viewModel.setVisibleDestination(ProfileDestination.METRICS_CRE, useTwoPane = false)
            viewModel.refreshNow()
            assertEquals(1, viewModel.uiState.value.currentDisciplines.size)

            assertFalse(viewModel.uiState.value.isAdminUnlocked)
            repo.unlockAdminMode("UFOP2026")
            viewModel.setVisibleDestination(ProfileDestination.SETTINGS, useTwoPane = false)
            viewModel.refreshNow()
            assertTrue(viewModel.uiState.value.isAdminUnlocked)
        } finally {
            viewModel.close()
        }
    }
}
