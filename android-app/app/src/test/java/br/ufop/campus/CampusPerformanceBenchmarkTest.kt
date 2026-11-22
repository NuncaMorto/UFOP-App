package br.ufop.campus

import br.ufop.campus.core.curriculum.UfopCurriculumCatalog
import br.ufop.campus.core.ffi.AttendanceStatus
import br.ufop.campus.core.ffi.InMemoryCampusRepository
import br.ufop.campus.core.ffi.StudentProfile
import br.ufop.campus.core.security.CampusCryptoEngine
import br.ufop.campus.ui.theme.CampusAccessibilitySettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.util.Locale
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil

class CampusPerformanceBenchmarkTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUpCryptoKey() {
        val keyBytes = ByteArray(32) { idx -> (0x3D xor (idx * 5 + 17)).toByte() }
        CampusCryptoEngine.setTestKeyOverrideForJvm(SecretKeySpec(keyBytes, "AES"))
    }

    @After
    fun tearDownCryptoKey() {
        CampusCryptoEngine.setTestKeyOverrideForJvm(null)
    }

    private data class PerfStats(
        val avgMs: Double,
        val p95Ms: Double,
        val maxMs: Double
    )

    private inline fun measureIterations(
        warmup: Int,
        iterations: Int,
        block: (Int) -> Unit
    ): PerfStats {
        repeat(warmup) { idx -> block(idx) }
        val samplesMs = DoubleArray(iterations)
        for (i in 0 until iterations) {
            val startNs = System.nanoTime()
            block(i)
            samplesMs[i] = (System.nanoTime() - startNs) / 1_000_000.0
        }
        val sorted = samplesMs.sortedArray()
        val avg = samplesMs.average()
        val p95Index = (ceil(0.95 * iterations).toInt() - 1).coerceIn(0, iterations - 1)
        return PerfStats(
            avgMs = avg,
            p95Ms = sorted[p95Index],
            maxMs = sorted.last()
        )
    }

    private fun printPerf(name: String, stats: PerfStats, budgetP95Ms: Double) {
        println(
            String.format(
                Locale.US,
                "[PERF] %-42s avg=%.3fms  p95=%.3fms  max=%.3fms  (p95 budget < %.1fms)",
                name,
                stats.avgMs,
                stats.p95Ms,
                stats.maxMs,
                budgetP95Ms
            )
        )
    }

    private fun buildRichRepository(stateFile: File? = null): InMemoryCampusRepository {
        val repo = InMemoryCampusRepository(stateFile = stateFile)
        repo.saveStudentProfile(
            StudentProfile(
                fullName = "Rafael Henrique Teixeira",
                registrationNumber = "24.2.5091",
                courseName = "Engenharia de Controle e Automação",
                campusName = "Ouro Preto",
                activeSemester = "2026.2",
                semesterStartDate = "2026-08-24",
                semesterEndDate = "2026-12-23",
                onboardingCompleted = true
            )
        )
        repo.setAccentColorPreference("CYAN")
        repo.setDarkThemePreference("DARK")
        repo.setAccessibilitySettings(
            CampusAccessibilitySettings(
                screenReaderOptimized = true,
                fontScaleMultiplier = 1.15f,
                highContrastMode = false,
                reduceMotion = true,
                largeTouchTargets = true,
                boldTextBoost = false
            )
        )
        repo.setGuidedTourCompleted(true)
        repo.setLastSeenReleaseVersion("1.8.0")

        val depts = listOf("decat", "decom", "demat", "defis", "deelt", "depro", "deciv", "dequi")
        val courseIds = mutableListOf<String>()
        val slotIds = mutableListOf<String>()

        for (c in 0 until 20) {
            val card = repo.createCourse(
                semester = "2026.2",
                departmentId = depts[c % depts.size],
                name = "CAT${100 + c} Disciplina UFOP $c",
                professor = "Prof. Docente $c",
                workloadHours = 60
            )
            courseIds.add(card.courseId)
        }

        val timeWindows = listOf(
            "07:30" to "08:20", "08:20" to "09:10", "09:20" to "10:10", "10:10" to "11:00", "11:10" to "12:00",
            "13:30" to "14:20", "14:20" to "15:10", "15:20" to "16:10", "16:10" to "17:00", "19:00" to "19:50"
        )
        var slotCounter = 0
        for (day in 1..6) {
            for ((start, end) in timeWindows) {
                val cid = courseIds[slotCounter % courseIds.size]
                val slot = repo.addScheduleSlot(cid, day, start, end, "ICEB (III)", "Sala ${100 + slotCounter}", "ICEB · ${100 + slotCounter}")
                slotIds.add(slot.slotId)
                slotCounter += 1
            }
        }
        for (i in 0 until 80) {
            val iso = "2026-09-%02d".format((i % 28) + 1)
            repo.saveClassCheckIn(
                slotIds[i % slotIds.size],
                iso,
                if (i % 3 == 0) AttendanceStatus.CANCELLED else AttendanceStatus.ABSENT,
                null
            )
        }
        for (i in 0 until 60) {
            repo.createCommitment(
                title = "Compromisso $i",
                description = "Monitoria / Projeto $i",
                commitmentDate = "2026-10-%02d".format((i % 28) + 1),
                startTime = "17:10",
                endTime = "18:00",
                location = "ICEB · Sala 12"
            )
        }
        for (i in 0 until 60) {
            repo.createAssessment(
                courseId = courseIds[i % courseIds.size],
                title = "AVALIAÇÃO $i",
                kind = if (i % 2 == 0) "Prova" else "Trabalho",
                dueDate = "2026-11-%02d".format((i % 28) + 1),
                dueTime = "18:00"
            )
        }
        repo.updateTranscriptOrCourseGrade("2026.1", "MTM122", 8.9, 2)
        repo.updateTranscriptOrCourseGrade("2026.1", "BCC201", 9.4, 0)
        return repo
    }

    @Test
    fun benchmark1_encryptedPersistAndColdReload_staysUnderBudget() {
        val stateFile = File(tempFolder.root, "perf_encrypted_state.json")
        val seedJson = buildRichRepository().exportFullAccountSnapshotJson()
        val diskRepo = InMemoryCampusRepository(stateFile = stateFile)
        assertTrue(diskRepo.restoreFullAccountSnapshotJson(seedJson))

        val stats = measureIterations(warmup = 5, iterations = 25) { idx ->
            diskRepo.setAccentColorPreference(if (idx % 2 == 0) "CYAN" else "ORANGE")
            val cold = InMemoryCampusRepository(stateFile = stateFile)
            assertEquals(20, cold.listDisciplines("2026.2", null).size)
        }
        printPerf("1_encrypted_save_and_cold_reload", stats, 250.0)
        assertTrue(stateFile.readText(Charsets.UTF_8).startsWith(CampusCryptoEngine.STATE_PREFIX))
        assertTrue("p95=${stats.p95Ms}ms exceeded 250ms budget", stats.p95Ms < 250.0)
    }

    @Test
    fun benchmark2_ufopCatalogLoadAndSearch54Courses_staysUnderBudget() {
        val assetFile = listOf(
            File("src/main/assets/ufop_matrizes_2026_2.json"),
            File("app/src/main/assets/ufop_matrizes_2026_2.json"),
            File("android-app/app/src/main/assets/ufop_matrizes_2026_2.json")
        ).firstOrNull { it.exists() }
        assertNotNull("ufop_matrizes_2026_2.json must exist", assetFile)
        val rawJson = assetFile!!.readText(Charsets.UTF_8)

        val loadStats = measureIterations(warmup = 3, iterations = 15) {
            val loaded = UfopCurriculumCatalog.loadFromJsonString(rawJson)
            assertEquals(54, loaded.size)
        }
        printPerf("2a_catalog_load_54_courses", loadStats, 250.0)
        assertTrue("p95=${loadStats.p95Ms}ms exceeded 250ms budget", loadStats.p95Ms < 250.0)

        val courses = UfopCurriculumCatalog.loadFromJsonString(rawJson)
        val queries = listOf(
            "computacao", "engenharia de producao", "automacao", "direito",
            "medicina", "farmacia", "ouro preto", "joao monlevade", "mariana", "arquitetura"
        )
        val searchStats = measureIterations(warmup = 20, iterations = 100) { idx ->
            val q = queries[idx % queries.size]
            val matches = UfopCurriculumCatalog.searchCourses(courses, q)
            assertTrue(matches.isNotEmpty())
            val found = UfopCurriculumCatalog.findCourse(courses, "Engenharia de Produção", "João Monlevade")
            assertEquals("PJM", found?.code)
        }
        printPerf("2b_catalog_search_and_find_54_courses", searchStats, 10.0)
        assertTrue("p95=${searchStats.p95Ms}ms exceeded 10ms budget", searchStats.p95Ms < 10.0)
    }

    @Test
    fun benchmark3_atestadoParseMultiCoursesWithEadAndConsecutiveSlots_staysUnderBudget() {
        val certText = """
            UNIVERSIDADE FEDERAL DE OURO PRETO
            ATESTADO DE MATRÍCULA 2026/2
            Atestamos que LUCAS ALMEIDA COSTA matrícula nº 24.2.7712 está regularmente matriculado no curso de ENGENHARIA DE CONTROLE E AUTOMACAO, cuja situação é regular no campus Ouro Preto.
            Início das Aulas: 24 de agosto de 2026
            Término das Aulas: 18 de dezembro de 2026

            CAT101 11 INTRODUCAO A INSTRUMENTACAO ICEB PORTUGUES
            MTM125 11 EQUACOES DIFERENCIAIS ORDINARIAS ICEB PORTUGUES
            ELT201 11 ANALISE DE CIRCUITOS ELETRICOS EM PORTUGUES
            CSI301 11 CIRCUITOS DIGITAIS ICEB PORTUGUES
            BCC202 11 ESTRUTURAS DE DADOS I ICEB PORTUGUES
            PRO102 11 GESTAO DA PRODUCAO EM PORTUGUES
            EAD105 11 METODOLOGIA CIENTIFICA A DISTANCIA CEAD PORTUGUES
            CET210 12 ETICA E SOCIEDADE EAD PORTUGUES

            Horário          Segunda        Terça          Quarta         Quinta         Sexta
            07:30 - 08:20    MTM125-11T     CSI301-11P     BCC202-11T     PRO102-11T     EAD105-11T
            08:20 - 09:10    MTM125-11T     CSI301-11P     BCC202-11T     PRO102-11T     EAD105-11T
            09:20 - 10:10    MTM125-11T     CSI301-11T     BCC202-11P
            10:10 - 11:00                   CSI301-11T
            13:30 - 14:20    CAT101-11T     ELT201-11T     CET210-12T
            14:20 - 15:10                   ELT201-11T     CET210-12T
            15:20 - 16:10                   ELT201-11P
            16:10 - 17:00                   ELT201-11P
        """.trimIndent()

        val repo = InMemoryCampusRepository()
        val stats = measureIterations(warmup = 5, iterations = 30) {
            val count = repo.importEnrollmentCertificateText(certText, completeOnboarding = false)
            assertEquals(8, count)
            assertEquals("EAD", repo.getDisciplineDetail("ead105_2026_2").shortLocation)
        }
        printPerf("3_atestado_parse_8_courses_ead_slots", stats, 50.0)
        assertTrue("p95=${stats.p95Ms}ms exceeded 50ms budget", stats.p95Ms < 50.0)
    }

    @Test
    fun benchmark4_syllabusValidationAndExtraction_staysUnderBudget() {
        val repo = InMemoryCampusRepository()
        val edo = repo.createCourse("2026.2", "demat", "MTM125 Equações Diferenciais Ordinárias", "", 60)

        val validSyllabus = """
            UNIVERSIDADE FEDERAL DE OURO PRETO
            PLANO DE ENSINO - MTM125 - EQUAÇÕES DIFERENCIAIS ORDINÁRIAS
            Docente responsável: Prof. Marcos Antônio Silva
            Distribuição dos pontos: P1 (30 pts), P2 (35 pts), P3 (35 pts)
            18/09/2026 - P1 - 1ª Prova Escrita (30 pts)
            20/10/2026 - P2 - 2ª Prova Escrita (35 pts)
            24/11/2026 - P3 - 3ª Prova Escrita (35 pts)
            04/12/2026 - Prova Substitutiva
            11/12/2026 - Exame Especial
        """.trimIndent()

        val wrongSyllabus = """
            PLANO DE ENSINO - FIS105 - FÍSICA I
            Professor: Prof. Helena Costa
            18/09/2026 - Prova 1 (30 pts)
        """.trimIndent()

        val stats = measureIterations(warmup = 10, iterations = 40) { idx ->
            if (idx % 2 == 0) {
                val report = repo.importCourseSyllabusDetailedText(edo.courseId, validSyllabus)
                assertTrue(report.matchedCourse)
            } else {
                val mismatch = repo.importCourseSyllabusDetailedText(edo.courseId, wrongSyllabus)
                assertFalse(mismatch.matchedCourse)
            }
        }
        printPerf("4_syllabus_validate_and_extract", stats, 30.0)
        assertTrue("p95=${stats.p95Ms}ms exceeded 30ms budget", stats.p95Ms < 30.0)
    }

    @Test
    fun benchmark5_encryptedUfopBackupExportAndRestore_staysUnderBudget() {
        val sourceRepo = buildRichRepository()
        val targetRepo = InMemoryCampusRepository()
        val password = "UfopBackup!2026".toCharArray()

        val stats = measureIterations(warmup = 2, iterations = 10) {
            val snapshot = sourceRepo.exportFullAccountSnapshotJson()
            val encrypted = CampusCryptoEngine.encryptBackupPayload(snapshot, password)
            assertTrue(encrypted.startsWith(CampusCryptoEngine.BACKUP_PREFIX))
            val decrypted = CampusCryptoEngine.decryptBackupPayload(encrypted, password)
            assertTrue(targetRepo.restoreFullAccountSnapshotJson(decrypted))
            assertEquals(20, targetRepo.listDisciplines("2026.2", null).size)
        }
        printPerf("5_backup_export_pbkdf2_gcm_restore", stats, 1500.0)
        assertTrue("p95=${stats.p95Ms}ms exceeded 1500ms budget", stats.p95Ms < 1500.0)
    }

    @Test
    fun benchmark6_agendaBatch100Days_staysUnderBudget() {
        val repo = buildRichRepository()
        val startDate = LocalDate.of(2026, 8, 24)
        val dates = Array(100) { offset -> startDate.plusDays(offset.toLong()).toString() }

        val stats = measureIterations(warmup = 5, iterations = 25) {
            var totalItems = 0
            for (iso in dates) {
                val day = repo.getAgendaDay(iso)
                assertEquals(7, day.weekDays.size)
                totalItems += day.items.size
            }
            assertTrue(totalItems > 0)
        }
        printPerf("6_agenda_batch_100_days", stats, 250.0)
        println(String.format(Locale.US, "[PERF] 6_agenda_per_day_avg=%.4fms", stats.avgMs / 100.0))
        assertTrue("p95=${stats.p95Ms}ms exceeded 250ms budget for 100 days", stats.p95Ms < 250.0)
    }
}
