package br.ufop.campus

import br.ufop.campus.core.ffi.AcademicCalendarEvent
import br.ufop.campus.core.ffi.CampusEngineBridgeRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.core.ffi.InMemoryCampusRepository
import br.ufop.campus.core.ffi.resolveDefaultSemesterBounds
import br.ufop.campus.core.security.CampusCryptoEngine
import br.ufop.campus.ui.components.formatIsoToBrDate
import br.ufop.campus.ui.components.formatIsoToShortPtBr
import br.ufop.campus.ui.components.isEndTimeAfterStart
import br.ufop.campus.ui.components.isoDateToUtcMillis
import br.ufop.campus.ui.components.parseHourMinuteOrNull
import br.ufop.campus.ui.components.utcMillisToIsoDate
import br.ufop.campus.ui.theme.CampusAccentColor
import br.ufop.campus.ui.theme.DarkDepartmentBlueText
import br.ufop.campus.ui.theme.DarkStatusAbsentRed
import br.ufop.campus.ui.theme.DarkStatusAttendingGreen
import br.ufop.campus.ui.theme.DarkStatusCancelledAmber
import br.ufop.campus.ui.theme.DarkSurface
import br.ufop.campus.ui.theme.DepartmentMathBlue
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.StatusCancelledAmber
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.contrastRatio
import br.ufop.campus.ui.theme.semanticAbsenceRiskColor
import br.ufop.campus.ui.theme.semanticAbsenceRiskLabel
import br.ufop.campus.ui.theme.semanticAbsenceRiskLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.TimeZone
import javax.crypto.spec.SecretKeySpec

class CampusSprint13IhcAndCalendarTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUpCryptoKey() {
        val syntheticKeyBytes = ByteArray(32) { idx -> (0x4A xor (idx * 9 + 5)).toByte() }
        CampusCryptoEngine.setTestKeyOverrideForJvm(SecretKeySpec(syntheticKeyBytes, "AES"))
    }

    @After
    fun tearDownCryptoKey() {
        CampusCryptoEngine.setTestKeyOverrideForJvm(null)
    }

    private fun assertCalendarMilestone(
        events: List<AcademicCalendarEvent>,
        expectedDate: String,
        vararg titleKeywords: String
    ) {
        val matching = events.filter { it.eventDate == expectedDate }
        assertTrue(
            "Expected calendar milestone on $expectedDate, found none in ${events.map { it.eventDate }}",
            matching.isNotEmpty()
        )
        val hasExpectedTitle = matching.any { ev ->
            titleKeywords.all { keyword -> ev.title.contains(keyword, ignoreCase = true) }
        }
        assertTrue(
            "Expected milestone on $expectedDate matching ${titleKeywords.toList()}, actual=${matching.map { it.title }}",
            hasExpectedTitle
        )
    }

    // ── a) Calendário oficial 2026.2 (24 marcos) ─────────────────────────────────────────────────

    @Test
    fun officialCalendar2026_2_containsAll24MilestonesAndRequiredDates() {
        val repo = InMemoryCampusRepository()
        val events = repo.getAcademicCalendarEvents("2026.2")

        assertEquals(24, events.size)
        assertEquals(24, events.map { it.id }.distinct().size)
        assertTrue(events.all { it.semester == "2026.2" })

        assertCalendarMilestone(events, "2026-08-24", "Início")
        assertCalendarMilestone(events, "2026-09-22", "25%", "Trancamento")
        assertCalendarMilestone(events, "2026-11-24", "75%")
        assertCalendarMilestone(events, "2026-12-17", "Exames Especiais")
        assertCalendarMilestone(events, "2026-12-23", "Término")
        assertCalendarMilestone(events, "2027-01-11", "Alteração de Notas")
        assertEquals("2026-08-24" to "2026-12-23", resolveDefaultSemesterBounds("2026.2"))
    }

    // ── b) Calendário oficial 2027.1 (21 marcos) ─────────────────────────────────────────────────

    @Test
    fun officialCalendar2027_1_containsAll21MilestonesAndRequiredDates() {
        val repo = InMemoryCampusRepository()
        val events = repo.getAcademicCalendarEvents("2027.1")

        assertEquals(21, events.size)
        assertEquals(21, events.map { it.id }.distinct().size)
        assertTrue(events.all { it.semester == "2027.1" })

        assertCalendarMilestone(events, "2027-02-22", "Início")
        assertCalendarMilestone(events, "2027-03-22", "25%", "Trancamento")
        assertCalendarMilestone(events, "2027-05-25", "75%")
        assertCalendarMilestone(events, "2027-06-26", "Término")
        assertCalendarMilestone(events, "2027-06-28", "Exames Especiais")
        assertCalendarMilestone(events, "2027-07-07", "Alteração de Notas")
        assertEquals("2027-02-22" to "2027-06-26", resolveDefaultSemesterBounds("2027.1"))
    }

    // ── c) Calendário oficial 2027.2 (24 marcos) ─────────────────────────────────────────────────

    @Test
    fun officialCalendar2027_2_containsAll24MilestonesAndRequiredDates() {
        val repo = InMemoryCampusRepository()
        val events = repo.getAcademicCalendarEvents("2027.2")

        assertEquals(24, events.size)
        assertEquals(24, events.map { it.id }.distinct().size)
        assertTrue(events.all { it.semester == "2027.2" })

        assertCalendarMilestone(events, "2027-08-02", "Início")
        assertCalendarMilestone(events, "2027-12-11", "Término")
        assertCalendarMilestone(events, "2027-08-30", "25%", "Trancamento")
        assertCalendarMilestone(events, "2027-11-06", "75%")
        assertCalendarMilestone(events, "2027-12-13", "Exames Especiais")
        assertCalendarMilestone(events, "2027-12-21", "Alteração de Notas")
        assertEquals("2027-08-02" to "2027-12-11", resolveDefaultSemesterBounds("2027.2"))
    }

    // ── d) listAvailableCalendarSemesters() ──────────────────────────────────────────────────────

    @Test
    fun listAvailableCalendarSemesters_includes2026_2_2027_1_and_2027_2() {
        val repo = InMemoryCampusRepository()
        val semesters = repo.listAvailableCalendarSemesters()
        assertTrue(semesters.containsAll(listOf("2026.2", "2027.1", "2027.2")))

        val bridgeState = File(tempFolder.root, "bridge_semesters_state.json")
        val bridge = CampusEngineBridgeRepository(dbPath = null, stateFile = bridgeState)
        val bridgeSemesters = bridge.listAvailableCalendarSemesters()
        assertTrue(bridgeSemesters.containsAll(listOf("2026.2", "2027.1", "2027.2")))
    }

    // ── e) Migração automática de estado legado (v1.8.0) com os 4 IDs antigos ────────────────────

    @Test
    fun legacyV180CalendarMigration_replaces4LegacyIdsWithOfficial2026_2AndPopulates2027Semesters() {
        val legacyIdGroups = listOf(
            listOf(
                "cal_2026_2_trancamento",
                "cal_2026_2_estudos_autonomos",
                "cal_2026_2_encerramento",
                "cal_2026_2_exames_especiais"
            ),
            listOf(
                "cal_2026_2_trancamento",
                "cal_2026_2_autonomos",
                "cal_2026_2_encerramento",
                "cal_2026_2_exames"
            )
        )

        legacyIdGroups.forEachIndexed { idx, legacyIds ->
            val stateFile = File(tempFolder.root, "legacy_v180_calendar_$idx.json")
            val legacyEventsJson = legacyIds.mapIndexed { evIdx, id ->
                val day = "%02d".format(10 + evIdx * 5)
                """
                {
                  "id": "$id",
                  "semester": "2026.2",
                  "eventDate": "2026-10-$day",
                  "dayNumber": "$day",
                  "monthShort": "OUT",
                  "title": "Marco Legado $id",
                  "category": "Prazo"
                }
                """.trimIndent()
            }.joinToString(",")

            val legacyStateJson = """
                {
                  "schemaVersion": 5,
                  "activeCampusId": "op_cruzeiro",
                  "lastSeenReleaseVersion": "1.8.0",
                  "studentProfile": {
                    "fullName": "Estudante Legado",
                    "registrationNumber": "24.1.1234",
                    "courseName": "Ciência da Computação",
                    "campusName": "Ouro Preto",
                    "activeSemester": "2026.2",
                    "semesterStartDate": "2026-08-24",
                    "semesterEndDate": "2026-12-23",
                    "onboardingCompleted": true
                  },
                  "calendarEventsBySemester": {
                    "2026.2": [$legacyEventsJson]
                  }
                }
            """.trimIndent()

            stateFile.writeText(CampusCryptoEngine.encryptStatePayload(legacyStateJson), Charsets.UTF_8)

            val migratedRepo = InMemoryCampusRepository(stateFile = stateFile)
            val events20262 = migratedRepo.getAcademicCalendarEvents("2026.2")
            assertEquals(24, events20262.size)
            assertTrue(
                "All legacy IDs must be replaced after migration",
                events20262.none { it.id in legacyIds }
            )
            assertCalendarMilestone(events20262, "2026-08-24", "Início")
            assertCalendarMilestone(events20262, "2027-01-11", "Alteração de Notas")

            val events20271 = migratedRepo.getAcademicCalendarEvents("2027.1")
            assertEquals(21, events20271.size)
            assertCalendarMilestone(events20271, "2027-02-22", "Início")

            val events20272 = migratedRepo.getAcademicCalendarEvents("2027.2")
            assertEquals(24, events20272.size)
            assertCalendarMilestone(events20272, "2027-08-02", "Início")

            assertTrue(migratedRepo.listAvailableCalendarSemesters().containsAll(listOf("2026.2", "2027.1", "2027.2")))

            // Persist and verify cold reload keeps the migrated 69 official milestones
            migratedRepo.setLastSeenReleaseVersion("1.9.0")
            val reloadedRepo = InMemoryCampusRepository(stateFile = stateFile)
            assertEquals(24, reloadedRepo.getAcademicCalendarEvents("2026.2").size)
            assertEquals(21, reloadedRepo.getAcademicCalendarEvents("2027.1").size)
            assertEquals(24, reloadedRepo.getAcademicCalendarEvents("2027.2").size)
        }
    }

    // ── f) IHC-09: Filtro de ruído do plano de ensino ────────────────────────────────────────────

    @Test
    fun syllabusNoiseFilter_ihc09_rejectsProseFragmentsAndPreservesValidAssessments() {
        val repo = InMemoryCampusRepository()
        val course = repo.createCourse(
            semester = "2026.2",
            departmentId = "demat",
            name = "MTM125 Equações Diferenciais Ordinárias",
            professor = "",
            workloadHours = 60
        )

        val noisyOnlyText = """
            25/09/2026 . A ENTREGA, PELOS
            28/09/2026 - A entrega, pelos alunos, deverá ser feita até 23h59.
            02/10/2026 - No caso de ausência na avaliação, os alunos deverão solicitar segunda chamada.
            05/10/2026 - Conforme o regulamento da UFOP, a atividade será realizada em sala.
            09/10/2026 - Entrega de lista para
        """.trimIndent()
        assertEquals(0, repo.importCourseSyllabusText(course.courseId, noisyOnlyText))
        assertTrue(repo.getDisciplineDetail(course.courseId).assessments.isEmpty())

        val mixedSyllabus = """
            PLANO DE ENSINO - MTM125 - EQUAÇÕES DIFERENCIAIS ORDINÁRIAS
            Professor responsável: Prof. Marcos Antônio Silva
            Distribuição dos pontos: P1 (30 pts), P2 (35 pts), Trabalho (20 pts), Seminário (15 pts)
            18/09/2026 | P1 - 1ª Prova Escrita (30 pts)
            25/09/2026 . A ENTREGA, PELOS
            28/09/2026 - A entrega, pelos alunos, deverá ser realizada no Moodle.
            20/10/2026 - P2 - 2ª Prova Escrita (35 pts)
            02/11/2026 - No caso de ausência, os alunos deverão apresentar atestado médico.
            24/11/2026 - Trabalho Prático de Modelagem (20 pts)
            01/12/2026 - Seminário de Aplicações (15 pts)
            04/12/2026 - Entrega de relatório para
        """.trimIndent()

        val report = repo.importCourseSyllabusDetailedText(course.courseId, mixedSyllabus)
        assertTrue(report.matchedCourse)
        assertNull(report.errorMessage)
        assertEquals(4, report.importedCount)

        val assessments = repo.getDisciplineDetail(course.courseId).assessments
        assertEquals(4, assessments.size)
        val datesAndTitles = assessments.map { it.dueDate to it.title }
        assertTrue(datesAndTitles.contains("2026-09-18" to "PROVA 1 EDO"))
        assertTrue(datesAndTitles.contains("2026-10-20" to "PROVA 2 EDO"))
        assertTrue(datesAndTitles.contains("2026-11-24" to "TRABALHO DE MODELAGEM"))
        assertTrue(datesAndTitles.contains("2026-12-01" to "SEMINÁRIO DE APLICAÇÕES"))

        assessments.forEach { item ->
            assertFalse("Noisy prose leaked into title: ${item.title}", item.title.contains("ENTREGA"))
            assertFalse("Noisy prose leaked into title: ${item.title}", item.title.contains("PELOS"))
            assertFalse("Noisy prose leaked into title: ${item.title}", item.title.contains("ALUNOS"))
            assertFalse("Noisy prose leaked into title: ${item.title}", item.title.contains("CONFORME"))
        }
    }

    // ── g) IHC-08: Contraste WCAG AA & Semântica de risco de faltas ──────────────────────────────

    @Test
    fun colorContrastAndSemanticAbsenceRisk_ihc08_satisfiesWcagAaOnDarkSurface() {
        val statusAndDeptColors = listOf(
            StatusAttendingGreen,
            DepartmentMathBlue,
            StatusCancelledAmber,
            StatusAbsentRed
        )
        statusAndDeptColors.forEach { baseColor ->
            val adjusted = accessibleTextColor(baseColor, isDarkSurface = true)
            val ratio = contrastRatio(adjusted, DarkSurface)
            assertTrue(
                "Expected WCAG AA contrast >= 4.5 on DarkSurface for $baseColor, got $ratio",
                ratio >= 4.5
            )
        }

        val darkSemanticTokens = listOf(
            DarkStatusAttendingGreen,
            DarkStatusCancelledAmber,
            DarkStatusAbsentRed,
            DarkDepartmentBlueText
        )
        darkSemanticTokens.forEach { darkToken ->
            val ratio = contrastRatio(darkToken, DarkSurface)
            assertTrue(
                "Expected dark token $darkToken contrast >= 4.5 on DarkSurface, got $ratio",
                ratio >= 4.5
            )
        }

        // < 50% -> 0 ("Controlada")
        assertEquals(0, semanticAbsenceRiskLevel(0, 18))
        assertEquals("Controlada", semanticAbsenceRiskLabel(0, 18))
        assertEquals(0, semanticAbsenceRiskLevel(8, 18))
        assertEquals("Controlada", semanticAbsenceRiskLabel(8, 18))
        assertEquals(0, semanticAbsenceRiskLevel(49, 100))
        assertEquals("Controlada", semanticAbsenceRiskLabel(49, 100))
        assertEquals(0, semanticAbsenceRiskLevel(4, 0))
        assertEquals("Controlada", semanticAbsenceRiskLabel(4, 0))
        assertEquals(DarkStatusAttendingGreen, semanticAbsenceRiskColor(0, 18, isDark = true))
        assertEquals(StatusAttendingGreen, semanticAbsenceRiskColor(0, 18, isDark = false))

        // 50..74% -> 1 ("Atenção")
        assertEquals(1, semanticAbsenceRiskLevel(9, 18))
        assertEquals("Atenção", semanticAbsenceRiskLabel(9, 18))
        assertEquals(1, semanticAbsenceRiskLevel(13, 18))
        assertEquals("Atenção", semanticAbsenceRiskLabel(13, 18))
        assertEquals(1, semanticAbsenceRiskLevel(74, 100))
        assertEquals("Atenção", semanticAbsenceRiskLabel(74, 100))
        assertEquals(DarkStatusCancelledAmber, semanticAbsenceRiskColor(9, 18, isDark = true))
        assertEquals(StatusCancelledAmber, semanticAbsenceRiskColor(9, 18, isDark = false))

        // >= 75% -> 2 ("Risco")
        assertEquals(2, semanticAbsenceRiskLevel(3, 4))
        assertEquals("Risco", semanticAbsenceRiskLabel(3, 4))
        assertEquals(2, semanticAbsenceRiskLevel(75, 100))
        assertEquals("Risco", semanticAbsenceRiskLabel(75, 100))
        assertEquals(2, semanticAbsenceRiskLevel(14, 18))
        assertEquals("Risco", semanticAbsenceRiskLabel(14, 18))
        assertEquals(2, semanticAbsenceRiskLevel(18, 18))
        assertEquals("Risco", semanticAbsenceRiskLabel(18, 18))
        assertEquals(DarkStatusAbsentRed, semanticAbsenceRiskColor(14, 18, isDark = true))
        assertEquals(StatusAbsentRed, semanticAbsenceRiskColor(14, 18, isDark = false))
    }

    // ── h) IHC-04: Helpers de data/hora UTC e validação de horário ───────────────────────────────

    @Test
    fun dateTimePickerHelpers_ihc04_roundTripWithoutTimezoneShiftAndValidateOrder() {
        val originalTz = TimeZone.getDefault()
        try {
            // Simula fuso horário do Brasil (UTC-3) para garantir zero retrocesso de dia
            TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))

            listOf("2026-08-24", "2027-02-22").forEach { iso ->
                val millis = isoDateToUtcMillis(iso)
                assertNotNull("Expected non-null UTC millis for $iso", millis)
                assertEquals(iso, utcMillisToIsoDate(millis!!))
            }

            assertNull(isoDateToUtcMillis("data-invalida"))
            assertEquals("22/02/2027", formatIsoToBrDate("2027-02-22"))
            assertEquals("24/08/2026", formatIsoToBrDate("2026-08-24"))
            assertEquals("22 FEV", formatIsoToShortPtBr("2027-02-22"))

            assertEquals(14 to 0, parseHourMinuteOrNull("14:00"))
            assertNull(parseHourMinuteOrNull("25:00"))
            assertTrue(isEndTimeAfterStart("14:00", "15:30"))
            assertFalse(isEndTimeAfterStart("15:30", "14:00"))
            assertFalse(isEndTimeAfterStart("14:00", "14:00"))
            assertFalse(isEndTimeAfterStart("invalido", "15:30"))
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    // ── Regressão v1.9.5 & 7 cores ───────────────────────────────────────────────────────────────

    @Test
    fun releaseVersionAndSevenAccentColors_preserveSprint13Contract() {
        assertEquals("1.9.9", CampusRepositoryHolder.CURRENT_APP_VERSION)
        assertEquals(
            listOf("GREEN", "RED", "ORANGE", "PURPLE", "YELLOW", "CYAN", "BROWN"),
            CampusAccentColor.entries.map { it.storageKey }
        )
        assertFalse(CampusAccentColor.entries.any { it.storageKey.equals("BLUE", ignoreCase = true) })
    }
}
