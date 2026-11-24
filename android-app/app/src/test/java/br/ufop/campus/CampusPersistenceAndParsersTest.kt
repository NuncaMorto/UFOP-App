package br.ufop.campus

import android.content.SharedPreferences
import br.ufop.campus.core.curriculum.UfopCourseCurriculum
import br.ufop.campus.core.curriculum.UfopCurriculumCatalog
import br.ufop.campus.core.curriculum.UfopCurriculumSubject
import br.ufop.campus.core.curriculum.checkCurriculumCompatibility
import br.ufop.campus.core.curriculum.parseEnrollmentYearSemester
import br.ufop.campus.core.ffi.AttendanceStatus
import br.ufop.campus.core.ffi.CampusEngineBridgeRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.core.ffi.InMemoryCampusRepository
import br.ufop.campus.core.ffi.StudentProfile
import br.ufop.campus.core.security.CampusCryptoEngine
import br.ufop.campus.ui.theme.CampusAccentColor
import br.ufop.campus.ui.theme.CampusAccessibilitySettings
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
import java.util.Locale
import javax.crypto.spec.SecretKeySpec

class CampusPersistenceAndParsersTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUpTestCryptoKey() {
        val syntheticKeyBytes = ByteArray(32) { idx -> (0xA5 xor (idx * 7 + 3)).toByte() }
        CampusCryptoEngine.setTestKeyOverrideForJvm(SecretKeySpec(syntheticKeyBytes, "AES"))
    }

    @After
    fun tearDownTestCryptoKey() {
        CampusCryptoEngine.setTestKeyOverrideForJvm(null)
    }

    // ── 1. Atestado de Matrícula: 1/2/3/4x50min, mixed T/P splits, whitespace & check-in weights ─

    @Test
    fun atestadoParser_mergesOneToFourConsecutiveSameTypeSlots_andSplitsMixedTypesAndLunch() {
        val repo = InMemoryCampusRepository()
        val certText = """
            UNIVERSIDADE FEDERAL DE OURO PRETO
            ATESTADO DE MATRÍCULA 2026/2
            Atestamos que MARIA CLARA OLIVEIRA matrícula nº 23.1.4021 está regularmente matriculada no curso de ENGENHARIA DE CONTROLE E AUTOMACAO, cuja situação é regular no campus Ouro Preto.
            Início das Aulas: 24 de agosto de 2026
            Término das Aulas: 18 de dezembro de 2026

            CAT101 11 INTRODUCAO A INSTRUMENTACAO ICEB PORTUGUES
            MTM125 11 EQUACOES DIFERENCIAIS ORDINARIAS ICEB PORTUGUES
            ELT201 11 ANALISE DE CIRCUITOS ELETRICOS EM PORTUGUES
            CSI301 11 CIRCUITOS DIGITAIS ICEB PORTUGUES
            FIS105 11 FISICA I ICEB PORTUGUES
            BCC202 11 ESTRUTURAS DE DADOS I ICEB PORTUGUES
            PRO102 11 GESTAO DA PRODUCAO EM PORTUGUES

            Horário          Segunda        Terça          Quarta         Quinta         Sexta
            07:30 - 08:20    MTM125-11T     CSI301-11P     BCC202 - 11 T  PRO102-11T
            08:20 - 09:10    MTM125-11T     CSI301-11P     BCC202 - 11 T  PRO102-11T
            09:20 - 10:10    MTM125-11T     CSI301-11T     BCC202 - 11 P
            10:10 - 11:00                   CSI301-11T
            11:10 - 12:00                                                 FIS105-11T
            13:30 - 14:20    CAT101-11T     ELT201-11T     FIS105-11P     FIS105-11T
            14:20 - 15:10                   ELT201-11T     FIS105-11P
            15:20 - 16:10                   ELT201-11P     FIS105-11P
            16:10 - 17:00                   ELT201-11P     FIS105-11P
            19:00 - 19:50                                                                PRO102-11T
            19:50 - 20:40                                                                PRO102-11T
            21:00 - 21:50                                                                PRO102-11T
            21:50 - 22:40                                                                PRO102-11T
        """.trimIndent()

        val imported = repo.importEnrollmentCertificateText(certText)
        assertEquals(7, imported)

        // 1x50min ("meia aula"): CAT101 -> 15h, 1 slot weight 1
        val catSlots = repo.listCourseSlots("cat101_2026_2")
        assertEquals(1, catSlots.size)
        assertEquals("13:30", catSlots[0].startTime)
        assertEquals("14:20", catSlots[0].endTime)
        val catCard = repo.getDisciplineDetail("cat101_2026_2").summary
        assertEquals(15, catCard.workloadHours)
        val catAbsent = repo.saveClassCheckIn(catSlots[0].slotId, "2026-08-24", AttendanceStatus.ABSENT, null)
        assertEquals(1, catAbsent.currentAbsences)

        // 3x50min same type across 10-min break (07:30-08:20, 08:20-09:10, 09:20-10:10): MTM125 -> 1 slot weight 3
        val mtmSlots = repo.listCourseSlots("mtm125_2026_2")
        assertEquals(1, mtmSlots.size)
        assertEquals("07:30", mtmSlots[0].startTime)
        assertEquals("10:10", mtmSlots[0].endTime)
        assertEquals("", mtmSlots[0].room)
        val mtmAbsent = repo.saveClassCheckIn(mtmSlots[0].slotId, "2026-08-24", AttendanceStatus.ABSENT, null)
        assertEquals(3, mtmAbsent.currentAbsences)

        // 4x50min mixed 2T + 2P (Tuesday): ELT201 -> split into 2 distinct slots (weight 2 + weight 2)
        val eltSlots = repo.listCourseSlots("elt201_2026_2")
        assertEquals(2, eltSlots.size)
        assertEquals("13:30", eltSlots[0].startTime)
        assertEquals("15:10", eltSlots[0].endTime)
        assertEquals("", eltSlots[0].room)
        assertEquals("15:20", eltSlots[1].startTime)
        assertEquals("17:00", eltSlots[1].endTime)
        assertEquals("", eltSlots[1].room)
        val eltAfterFirst = repo.saveClassCheckIn(eltSlots[0].slotId, "2026-08-25", AttendanceStatus.ABSENT, null)
        assertEquals(2, eltAfterFirst.currentAbsences)
        val eltAfterSecond = repo.saveClassCheckIn(eltSlots[1].slotId, "2026-08-25", AttendanceStatus.ABSENT, null)
        assertEquals(4, eltAfterSecond.currentAbsences)

        // 4x50min mixed 2P + 2T (Tuesday morning): CSI301 -> split into 2 distinct slots (Aula Prática + Aula Teórica)
        val csiSlots = repo.listCourseSlots("csi301_2026_2")
        assertEquals(2, csiSlots.size)
        assertEquals("07:30", csiSlots[0].startTime)
        assertEquals("09:10", csiSlots[0].endTime)
        assertEquals("", csiSlots[0].room)
        assertEquals("09:20", csiSlots[1].startTime)
        assertEquals("11:00", csiSlots[1].endTime)
        assertEquals("", csiSlots[1].room)

        // 3x50min mixed 2T + 1P with whitespace inside token (`BCC202 - 11 T`): split into weight 2 + weight 1
        val bccSlots = repo.listCourseSlots("bcc202_2026_2")
        assertEquals(2, bccSlots.size)
        assertEquals("07:30", bccSlots[0].startTime)
        assertEquals("09:10", bccSlots[0].endTime)
        assertEquals("", bccSlots[0].room)
        assertEquals("09:20", bccSlots[1].startTime)
        assertEquals("10:10", bccSlots[1].endTime)
        assertEquals("", bccSlots[1].room)
        val bccAfterT = repo.saveClassCheckIn(bccSlots[0].slotId, "2026-08-26", AttendanceStatus.ABSENT, null)
        assertEquals(2, bccAfterT.currentAbsences)
        val bccAfterP = repo.saveClassCheckIn(bccSlots[1].slotId, "2026-08-26", AttendanceStatus.ABSENT, null)
        assertEquals(3, bccAfterP.currentAbsences)

        // 4x50min same type (4P) across 10-min break (Wednesday) + non-merge across lunch break (Thursday 11:10-12:00 & 13:30-14:20)
        val fisSlots = repo.listCourseSlots("fis105_2026_2")
        val wedSlots = fisSlots.filter { it.dayOfWeek == 3 }
        assertEquals(1, wedSlots.size)
        assertEquals("13:30", wedSlots[0].startTime)
        assertEquals("17:00", wedSlots[0].endTime)
        assertEquals("", wedSlots[0].room)
        val fisAfter4P = repo.saveClassCheckIn(wedSlots[0].slotId, "2026-08-26", AttendanceStatus.ABSENT, null)
        assertEquals(4, fisAfter4P.currentAbsences)

        val thuSlots = fisSlots.filter { it.dayOfWeek == 4 }
        assertEquals(2, thuSlots.size)
        assertEquals("11:10", thuSlots[0].startTime)
        assertEquals("12:00", thuSlots[0].endTime)
        assertEquals("13:30", thuSlots[1].startTime)
        assertEquals("14:20", thuSlots[1].endTime)

        // 2x50min (Thursday) + 4x50min across 20-min evening break (Friday 19:00-22:40): PRO102
        val proSlots = repo.listCourseSlots("pro102_2026_2")
        val proThu = proSlots.filter { it.dayOfWeek == 4 }
        assertEquals(1, proThu.size)
        assertEquals("07:30", proThu[0].startTime)
        assertEquals("09:10", proThu[0].endTime)
        val proFri = proSlots.filter { it.dayOfWeek == 5 }
        assertEquals(1, proFri.size)
        assertEquals("19:00", proFri[0].startTime)
        assertEquals("22:40", proFri[0].endTime)
        val proAfterFri = repo.saveClassCheckIn(proFri[0].slotId, "2026-08-28", AttendanceStatus.ABSENT, null)
        assertEquals(4, proAfterFri.currentAbsences)
    }

    @Test
    fun manualSlotAddAndUpdate_computesAccurateClassWeightFromDuration() {
        val repo = InMemoryCampusRepository()
        val course = repo.createCourse("2026.2", "decom", "Compiladores", "Prof. José", 60)

        val slot50 = repo.addScheduleSlot(course.courseId, 1, "08:00", "08:50", "ICEB", "101", "ICEB · 101")
        val slot100 = repo.addScheduleSlot(course.courseId, 2, "08:00", "09:40", "ICEB", "101", "ICEB · 101")
        val slot160 = repo.addScheduleSlot(course.courseId, 3, "07:30", "10:10", "ICEB", "101", "ICEB · 101")
        val slot210 = repo.addScheduleSlot(course.courseId, 4, "13:30", "17:00", "ICEB", "101", "ICEB · 101")

        repo.saveClassCheckIn(slot50.slotId, "2026-08-24", AttendanceStatus.ABSENT, null)
        assertEquals(1, repo.getDisciplineDetail(course.courseId).summary.currentAbsences)

        repo.saveClassCheckIn(slot100.slotId, "2026-08-25", AttendanceStatus.ABSENT, null)
        assertEquals(3, repo.getDisciplineDetail(course.courseId).summary.currentAbsences)

        repo.saveClassCheckIn(slot160.slotId, "2026-08-26", AttendanceStatus.ABSENT, null)
        assertEquals(6, repo.getDisciplineDetail(course.courseId).summary.currentAbsences)

        repo.saveClassCheckIn(slot210.slotId, "2026-08-27", AttendanceStatus.ABSENT, null)
        assertEquals(10, repo.getDisciplineDetail(course.courseId).summary.currentAbsences)

        // Update slot50 from 50min (weight 1) to 160min (weight 3) -> total absences increases by 2 (10 -> 12)
        repo.updateScheduleSlot(slot50.slotId, 1, "07:30", "10:10", "ICEB", "101", "ICEB · 101")
        assertEquals(12, repo.getDisciplineDetail(course.courseId).summary.currentAbsences)
    }

    // ── 2. Cold-restart full state persistence roundtrip + downward manual absences ──────────────

    @Test
    fun fullStatePersistenceRoundtrip_preservesAllEntitiesAndDownwardManualAbsences() {
        val stateFile = File(tempFolder.root, "campus_ufop_state.json")
        var persistCallbackCount = 0
        val repo1 = InMemoryCampusRepository(
            stateFile = stateFile,
            onStatePersisted = { persistCallbackCount += 1 }
        )

        repo1.saveStudentProfile(
            StudentProfile(
                fullName = "Ana Beatriz Ferreira",
                registrationNumber = "22.2.4110",
                courseName = "Ciência da Computação",
                campusName = "Ouro Preto",
                activeSemester = "2026.2",
                semesterStartDate = "2026-08-24",
                semesterEndDate = "2026-12-23",
                avatarPath = "/data/user/0/br.ufop.campus/files/avatar.jpg",
                onboardingCompleted = true
            )
        )
        repo1.setDarkThemePreference("DARK")
        repo1.setExportFolderConfig(
            treeUri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FMinhasGrades",
            displayLabel = "Documentos/MinhasGrades"
        )

        val course = repo1.createCourse("2026.2", "decom", "BCC322 Engenharia de Software", "Prof. Carlos", 60)
        val slot = repo1.addScheduleSlot(course.courseId, 2, "13:30", "17:00", "ICEB (III)", "Sala 201", "ICEB · 201")
        // 4 absences from check-in
        val afterAbsent = repo1.saveClassCheckIn(slot.slotId, "2026-09-01", AttendanceStatus.ABSENT, null)
        assertEquals(4, afterAbsent.currentAbsences)
        repo1.saveClassCheckIn(slot.slotId, "2026-09-08", AttendanceStatus.CANCELLED, null)

        // Downward manual absence edit: 4 -> 1 (baseAbsences becomes -3!)
        val afterManualDown = repo1.updateCourseAbsences(course.courseId, 1)
        assertEquals(1, afterManualDown.currentAbsences)

        repo1.updateDisciplineNotes(course.courseId, "Revisar padrões de projeto e UML.")
        val comm = repo1.createCommitment(
            title = "Reunião IC",
            description = "Alinhar experimento",
            commitmentDate = "2026-09-10",
            startTime = "14:00",
            endTime = "15:30",
            location = "ICEB · Lab 02"
        )
        val assess = repo1.createAssessment(course.courseId, "PROVA 1 ES", "Prova", "2026-10-15", "13:30")
        repo1.toggleAssessmentCompleted(assess.id, true)

        val friendJson = repo1.exportMyGradeBundleJson("2026.2")
        val friend = repo1.importFriendScheduleJson("Lucas Mendes", friendJson)
        assertEquals("Lucas Mendes", friend.friendName)

        repo1.updateTranscriptOrCourseGrade("2026.1", "BCC201", 8.7, 2)
        repo1.updateTranscriptOrCourseGrade("2026.2", course.courseId, 9.2, null)

        assertTrue(repo1.unlockAdminMode("UFOP2026"))
        val calJson = repo1.exportCalendarUpdateJson()
        val importedCal = repo1.importCalendarUpdateJson(calJson)
        assertNotNull(repo1.getPendingCalendarUpdate())
        assertEquals(importedCal.version, repo1.getPendingCalendarUpdate()?.version)

        assertTrue(persistCallbackCount > 0)
        assertTrue(stateFile.exists())
        // Backup should also exist after multiple mutations
        assertTrue(File(stateFile.parentFile, "${stateFile.name}.bak").exists())

        // Cold restart in brand-new instance
        val repo2 = InMemoryCampusRepository(stateFile = stateFile)
        val profile2 = repo2.getStudentProfile()
        assertEquals("Ana Beatriz Ferreira", profile2.fullName)
        assertEquals("22.2.4110", profile2.registrationNumber)
        assertEquals("/data/user/0/br.ufop.campus/files/avatar.jpg", profile2.avatarPath)
        assertTrue(profile2.onboardingCompleted)

        assertEquals("DARK", repo2.getDarkThemePreference())
        val folder2 = repo2.getExportFolderConfig()
        assertEquals(
            "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FMinhasGrades",
            folder2.treeUri
        )
        assertEquals("Documentos/MinhasGrades", folder2.displayLabel)

        val detail2 = repo2.getDisciplineDetail(course.courseId)
        assertEquals(1, detail2.summary.currentAbsences) // downward manual edit preserved!
        assertEquals(1, detail2.summary.cancelledClasses)
        assertEquals("Revisar padrões de projeto e UML.", detail2.notes)
        assertEquals(1, detail2.assessments.size)
        assertTrue(detail2.assessments[0].completed)
        assertEquals(1, repo2.listCourseSlots(course.courseId).size)
        assertEquals(2, repo2.listAbsenceHistory(course.courseId).size)

        val agenda2 = repo2.getAgendaDay("2026-09-10")
        assertTrue(agenda2.items.any { it.id == comm.id && it.title == "Reunião IC" })

        val friends2 = repo2.listFriendSchedules()
        assertEquals(1, friends2.size)
        assertEquals("Lucas Mendes", friends2[0].friendName)

        val cre2 = repo2.getAcademicCreReport()
        assertTrue(cre2.semesters.any { it.semester == "2026.1" })
        assertTrue(cre2.overallCre > 8.5)

        assertTrue(repo2.isAdminUnlocked())
        assertNotNull(repo2.getPendingCalendarUpdate())
        assertEquals(importedCal.version, repo2.getPendingCalendarUpdate()?.version)
        assertTrue(repo2.applyPendingCalendarUpdate() > 0)

        // Verify CampusEngineBridgeRepository also forwards onStatePersisted & Sprint 9 APIs
        var bridgePersisted = 0
        val bridge = CampusEngineBridgeRepository(
            dbPath = null,
            stateFile = stateFile,
            onStatePersisted = { bridgePersisted += 1 }
        )
        assertEquals("DARK", bridge.getDarkThemePreference())
        assertEquals(1, bridge.getDisciplineDetail(course.courseId).summary.currentAbsences)
        bridge.updateCourseAbsences(course.courseId, 0)
        assertEquals(0, bridge.getDisciplineDetail(course.courseId).summary.currentAbsences)
        assertTrue(bridgePersisted > 0)
    }

    // ── 3. Schema migration v1/v2/v3/v4 -> v5, .bak recovery & partial corruption tolerance ──────

    @Test
    fun schemaMigrationV1ToV4_restoresWithoutDataLoss() {
        for (version in 1..4) {
            val file = File(tempFolder.root, "state_v$version.json")
            val vJson = """
                {
                  "schemaVersion": $version,
                  "activeCampusId": "jm_icea",
                  "generatedIdCounter": 250,
                  "courses": [
                    {
                      "id": "csi101_2026_2",
                      "code": "CSI101",
                      "semester": "2026.2",
                      "departmentId": "decsi",
                      "name": "PROGRAMACAO DE COMPUTADORES I",
                      "professor": "Prof. Bruno",
                      "workloadHours": 60,
                      "maxAbsences": 18,
                      "manualAbsences": 3,
                      "notes": "Teste v$version"
                    }
                  ],
                  "slots": [
                    {
                      "slotId": "slot_v${version}_1",
                      "courseId": "csi101_2026_2",
                      "dayOfWeek": 1,
                      "startTime": "08:00",
                      "endTime": "09:40",
                      "building": "ICEA",
                      "room": "C102",
                      "shortLocation": "ICEA · C102",
                      "classWeight": 2
                    }
                  ]
                }
            """.trimIndent()
            file.writeText(vJson, Charsets.UTF_8)

            val repo = InMemoryCampusRepository(stateFile = file)
            val disciplines = repo.listDisciplines("2026.2", null)
            assertEquals("Failed migration for schema v$version", 1, disciplines.size)
            assertEquals("CSI101", disciplines[0].code)
            assertEquals(3, disciplines[0].currentAbsences)
            assertTrue(repo.getStudentProfile().onboardingCompleted)
            assertEquals("SYSTEM", repo.getDarkThemePreference())
            assertNull(repo.getExportFolderConfig().treeUri)

            // Mutate and confirm upgraded JSON is schema v5 and encrypted on disk (UFOPENC1:)
            repo.setDarkThemePreference("LIGHT")
            val upgradedRaw = file.readText(Charsets.UTF_8)
            assertTrue(upgradedRaw.startsWith(CampusCryptoEngine.STATE_PREFIX))
            val decryptedUpgraded = CampusCryptoEngine.decryptStatePayloadIfNeeded(upgradedRaw)
            assertTrue(decryptedUpgraded.contains("\"schemaVersion\":5"))
        }
    }

    @Test
    fun corruptedPrimaryFile_recoversFromBak_andPartialItemCorruptionDoesNotWipeSemester() {
        val stateFile = File(tempFolder.root, "recovery_state.json")
        val repo1 = InMemoryCampusRepository(stateFile = stateFile)
        val c1 = repo1.createCourse("2026.2", "demat", "Cálculo II", "Prof. Ana", 60)
        // Second mutation rotates the first valid file into .bak
        repo1.createCourse("2026.2", "defis", "Física I", "Prof. Helena", 60)
        // Third mutation ensures both c1 and c2 are now in .bak
        repo1.updateDisciplineNotes(c1.courseId, "Anotação importante")

        val bakFile = File(stateFile.parentFile, "${stateFile.name}.bak")
        assertTrue(bakFile.exists())

        // Truncate/corrupt primary file
        stateFile.writeText("{\"schemaVersion\":5,\"courses\":[{\"id\":\"broken", Charsets.UTF_8)

        val recoveredRepo = InMemoryCampusRepository(stateFile = stateFile)
        val recoveredCourses = recoveredRepo.listDisciplines("2026.2", null)
        assertEquals(2, recoveredCourses.size)
        assertTrue(recoveredCourses.any { it.name == "Cálculo II" })
        assertTrue(recoveredCourses.any { it.name == "Física I" })

        // Now test partial-item corruption tolerance (malformed elements inside JSON arrays)
        val partialFile = File(tempFolder.root, "partial_corrupt.json")
        partialFile.writeText(
            """
            {
              "schemaVersion": 5,
              "activeCampusId": "op_cruzeiro",
              "darkThemePreference": "DARK",
              "studentProfile": {
                "fullName": "Pedro Henrique",
                "activeSemester": "2026.2",
                "onboardingCompleted": true
              },
              "courses": [
                "INVALID_COURSE_STRING",
                null,
                {
                  "id": "mtm122_2026_2",
                  "code": "MTM122",
                  "semester": "2026.2",
                  "departmentId": "demat",
                  "name": "Cálculo I",
                  "workloadHours": 90,
                  "maxAbsences": 27,
                  "manualAbsences": -2
                }
              ],
              "slots": [
                12345,
                {
                  "slotId": "slot_mtm_1",
                  "courseId": "mtm122_2026_2",
                  "dayOfWeek": 2,
                  "startTime": "08:00",
                  "endTime": "09:40",
                  "classWeight": 2
                }
              ],
              "sessions": [
                "BAD_SESSION",
                {
                  "slotId": "slot_mtm_1",
                  "sessionDate": "2026-09-01",
                  "status": "ABSENT"
                },
                {
                  "slotId": "slot_mtm_1",
                  "sessionDate": "2026-09-08",
                  "status": "ABSENT"
                }
              ],
              "commitments": ["BAD_COMM", {"id": "c1", "isoDate": "2026-09-05", "title": "Monitoria"}],
              "assessments": ["BAD_ASSESS", {"id": "a1", "courseId": "mtm122_2026_2", "title": "PROVA 1 CÁLCULO I", "kind": "Prova", "dueDate": "2026-10-01"}],
              "transcriptEntries": ["BAD_TX", {"id": "t1", "semester": "2026.1", "code": "MTM112", "name": "GAAL", "workloadHours": 60, "grade": 8.0, "status": "AP"}]
            }
            """.trimIndent(),
            Charsets.UTF_8
        )

        val partialRepo = InMemoryCampusRepository(stateFile = partialFile)
        val courses = partialRepo.listDisciplines("2026.2", null)
        assertEquals(1, courses.size)
        assertEquals("Cálculo I", courses[0].name)
        // manualAbsences (-2) + 2 absent sessions (2 + 2 = 4) = 2!
        assertEquals(2, courses[0].currentAbsences)
        assertEquals("DARK", partialRepo.getDarkThemePreference())
        assertEquals(1, partialRepo.getDisciplineDetail("mtm122_2026_2").assessments.size)
        assertEquals(1, partialRepo.getAgendaDay("2026-09-05").commitmentsCount)
        assertEquals(1, partialRepo.getAcademicCreReport().totalApproved)
    }

    // ── 4. Syllabus PDF text parser: 3+ realistic messy syllabi, ALL-CAPS & noise rejection ────

    @Test
    fun syllabusParser_formatsAllCapsCleanTitles_andRejectsNoisyProseAndBoilerplate() {
        val repo = InMemoryCampusRepository()
        val ptBr = Locale("pt", "BR")

        // Syllabus 1: EDO (pipes, clutter, boilerplate, revision, bibliography, segunda chamada, apresentação da disciplina)
        val edo = repo.createCourse("2026.2", "demat", "Introdução às Equações Diferenciais Ordinárias", "Prof. Marcos", 60)
        val edoSyllabus = """
            PLANO DE ENSINO - MTM125 - INTRODUÇÃO ÀS EQUAÇÕES DIFERENCIAIS ORDINÁRIAS
            24/08/2026 - Apresentação da disciplina e do plano de ensino
            Critério de avaliação: A média final será calculada como MF = (P1 + P2 + P3)/3 em 18/12/2026.
            Observação: O aluno com frequência inferior a 75% estará reprovado por falta conforme resolução CEPE.
            15/09/2026 - Aula de revisão e plantão de dúvidas para a Prova 1
            Aula 12 - 18/09/2026 | P1 - 1ª Prova Escrita (valor: 30 pontos) | Unidade 1 e 2
            18/09/2026 - Prova 1 (3,0 pts)
            20/10/2026 - Segunda Avaliação (peso 3, terça-feira, 15:20)
            Observação: em caso de ausência na prova, apresentar atestado médico até 25/10/2026 para solicitar segunda chamada.
            24/11/2026 - P3 (40% da nota, sem consulta)
            04/12/2026 - Prova Substitutiva (todo o conteúdo)
            11/12/2026 - Exame Especial (todo o conteúdo)
            Bibliografia básica: BOYCE, W. E.; DIPRIMA, R. C. Equações Diferenciais Elementares, 10/2020.
            O estudante que não obtiver média mínima de seis pontos durante o período letivo terá direito apenas ao exame especial previsto em 11/12/2026 no calendário.
        """.trimIndent()

        val edoCount = repo.importCourseSyllabusText(edo.courseId, edoSyllabus)
        val edoAssessments = repo.getDisciplineDetail(edo.courseId).assessments
        assertEquals(5, edoCount)
        val edoTitles = edoAssessments.map { it.dueDate to it.title }
        assertTrue(edoTitles.contains("2026-09-18" to "PROVA 1 EDO"))
        assertTrue(edoTitles.contains("2026-10-20" to "PROVA 2 EDO"))
        assertTrue(edoTitles.contains("2026-11-24" to "PROVA 3 EDO"))
        assertTrue(edoTitles.contains("2026-12-04" to "PROVA SUBSTITUTIVA EDO"))
        assertTrue(edoTitles.contains("2026-12-11" to "EXAME ESPECIAL EDO"))
        edoAssessments.forEach { item ->
            assertEquals("Title must be strictly ALL CAPS", item.title.uppercase(ptBr), item.title)
            assertTrue("Title too long: ${item.title}", item.title.length <= 64)
        }

        // Syllabus 2: Análise de Circuitos Elétricos (multi-eval single lines, topics, lists, reports, seminars, prose rejection)
        val circ = repo.createCourse("2026.2", "deelt", "Análise de Circuitos Elétricos", "Prof.ª Carla", 60)
        val circSyllabus = """
            Cronograma de Avaliações:
            P1: 22/09/2026 (25%); P2: 27/10/2026 (25%); Exame Especial: 15/12/2026
            Avaliações práticas: 10/11/2026 - TP1 - Trabalho Prático de Filtros Ativos (20 pontos), 24/11/2026 - TP2 (15 pts) e 01/12/2026 - Seminário sobre Amplificadores Operacionais (10%)
            05/10/2026 - Entrega da Lista 2 de exercícios (peso 1)
            19/10/2026 - Relatório 1 (Lab 03, 10 pontos)
            A nota obtida pelo estudante nas listas entregues fora do prazo em 08/10/2026 não será considerada para composição da pontuação final da disciplina.
            Revisão de prova e vista de prova: agendada para 30/10/2026 no gabinete do docente para tirar dúvidas.
        """.trimIndent()

        val circCount = repo.importCourseSyllabusText(circ.courseId, circSyllabus)
        val circAssessments = repo.getDisciplineDetail(circ.courseId).assessments
        assertEquals(8, circCount)
        val circTitles = circAssessments.map { it.title }.toSet()
        assertTrue(circTitles.contains("PROVA 1 CIRCUITOS ELÉTRICOS"))
        assertTrue(circTitles.contains("PROVA 2 CIRCUITOS ELÉTRICOS"))
        assertTrue(circTitles.contains("EXAME ESPECIAL CIRCUITOS ELÉTRICOS"))
        assertTrue(circTitles.contains("TRABALHO DE FILTROS ATIVOS"))
        assertTrue(circTitles.contains("TRABALHO 2 CIRCUITOS ELÉTRICOS"))
        assertTrue(circTitles.contains("SEMINÁRIO DE AMPLIFICADORES OPERACIONAIS"))
        assertTrue(circTitles.contains("LISTA 2 CIRCUITOS ELÉTRICOS"))
        assertTrue(circTitles.contains("RELATÓRIO 1 CIRCUITOS ELÉTRICOS"))
        circAssessments.forEach { item ->
            assertEquals("Title must be strictly ALL CAPS", item.title.uppercase(ptBr), item.title)
        }

        // Syllabus 3: Cálculo III, GAAL & Instrumentação (vertical PDF table cells, short tags)
        val calc3 = repo.createCourse("2026.2", "demat", "Cálculo Diferencial e Integral III", "Prof. João", 60)
        val calc3Syllabus = """
            16/09/2026
            1ª Avaliação (30 pts)
            09/12/2026
            Prova Final (40 pts)
        """.trimIndent()
        assertEquals(2, repo.importCourseSyllabusText(calc3.courseId, calc3Syllabus))
        val calc3Titles = repo.getDisciplineDetail(calc3.courseId).assessments.map { it.title }.toSet()
        assertTrue(calc3Titles.contains("PROVA 1 CÁLCULO III"))
        assertTrue(calc3Titles.contains("PROVA FINAL CÁLCULO III"))

        val gaal = repo.createCourse("2026.2", "demat", "Geometria Analítica e Álgebra Linear", "Prof. Paulo", 60)
        val gaalSyllabus = """
            12/10/2026 - Lista 1 (10 pts)
            19/10/2026 - Prova 1 (30 pontos)
        """.trimIndent()
        assertEquals(2, repo.importCourseSyllabusText(gaal.courseId, gaalSyllabus))
        val gaalTitles = repo.getDisciplineDetail(gaal.courseId).assessments.map { it.title }.toSet()
        assertTrue(gaalTitles.contains("LISTA 1 GAAL"))
        assertTrue(gaalTitles.contains("PROVA 1 GAAL"))

        val instr = repo.createCourse("2026.2", "decat", "Instrumentação", "Prof. Lucas", 60)
        val instrSyllabus = """
            03/11/2026 - Seminário 1 (15 pontos)
            08/12/2026 - Projeto Final (30 pts)
        """.trimIndent()
        assertEquals(2, repo.importCourseSyllabusText(instr.courseId, instrSyllabus))
        val instrTitles = repo.getDisciplineDetail(instr.courseId).assessments.map { it.title }.toSet()
        assertTrue(instrTitles.contains("SEMINÁRIO 1 INSTRUMENTAÇÃO"))
        assertTrue(instrTitles.contains("PROJETO FINAL INSTRUMENTAÇÃO"))
    }

    // ── 5. Onboarding import with completeOnboarding = false ─────────────────────────────────────

    @Test
    fun enrollmentImport_withCompleteOnboardingFalse_preservesOnboardingFlagAndPopulatesProfile() {
        val repo = InMemoryCampusRepository()
        assertFalse(repo.getStudentProfile().onboardingCompleted)

        val certText = """
            ATESTADO DE MATRÍCULA 2026/2
            Atestamos que GABRIEL SANTOS LIMA matrícula nº 24.1.8055 está matriculado no curso de CIENCIA DA COMPUTACAO, cuja situação é regular no campus Ouro Preto.
            Início das Aulas: 24 de agosto de 2026
            Término das Aulas: 18 de dezembro de 2026

            BCC201 11 PROGRAMACAO I ICEB PORTUGUES
            MTM122 11 CALCULO DIFERENCIAL E INTEGRAL I ICEB PORTUGUES

            Horário          Segunda        Terça          Quarta         Quinta         Sexta
            08:00 - 08:50    BCC201-11T     MTM122-11T     BCC201-11T     MTM122-11T
            08:50 - 09:40    BCC201-11T     MTM122-11T     BCC201-11T     MTM122-11T
        """.trimIndent()

        val count = repo.importEnrollmentCertificateText(certText, completeOnboarding = false)
        assertEquals(2, count)

        val profile = repo.getStudentProfile()
        assertFalse("onboardingCompleted must remain false when completeOnboarding=false", profile.onboardingCompleted)
        assertEquals("Gabriel Santos Lima", profile.fullName)
        assertEquals("24.1.8055", profile.registrationNumber)
        assertEquals("Ciência da Computação", profile.courseName)
        assertEquals("Ouro Preto", profile.campusName)
        assertEquals("2026.2", profile.activeSemester)
        assertEquals("2026-08-24", profile.semesterStartDate)
        assertEquals("2026-12-18", profile.semesterEndDate)
        assertEquals(2, repo.listDisciplines("2026.2", null).size)

        // Default parameter (completeOnboarding = true) completes onboarding
        repo.importEnrollmentCertificateText(certText)
        assertTrue(repo.getStudentProfile().onboardingCompleted)
    }

    // ── 6. Performance Benchmark Test ────────────────────────────────────────────────────────────

    @Test
    fun performanceBenchmark_saveColdReloadAndHotQueriesStayWellWithinBudget() {
        val stateFile = File(tempFolder.root, "perf_state.json")
        // Build in-memory first (no disk I/O during synthetic fixture construction), then measure real save + cold reload
        val seedRepo = InMemoryCampusRepository()
        val deptIds = listOf("decom", "demat", "defis", "decat", "deelt", "depro", "deciv", "dequi")
        val courseIds = mutableListOf<String>()
        val slotIds = mutableListOf<String>()

        for (c in 0 until 20) {
            val card = seedRepo.createCourse(
                semester = "2026.2",
                departmentId = deptIds[c % deptIds.size],
                name = "DIS${100 + c} Disciplina Benchmark $c",
                professor = "Prof. Docente $c",
                workloadHours = 60
            )
            courseIds.add(card.courseId)
        }

        // 60 non-overlapping slots across 6 days x 10 time windows
        val timeWindows = listOf(
            "07:30" to "08:20", "08:20" to "09:10", "09:20" to "10:10", "10:10" to "11:00", "11:10" to "12:00",
            "13:30" to "14:20", "14:20" to "15:10", "15:20" to "16:10", "16:10" to "17:00", "19:00" to "19:50"
        )
        var slotCounter = 0
        for (day in 1..6) {
            for ((start, end) in timeWindows) {
                val cid = courseIds[slotCounter % courseIds.size]
                val slot = seedRepo.addScheduleSlot(cid, day, start, end, "ICEB", "Sala ${100 + slotCounter}", "ICEB · ${100 + slotCounter}")
                slotIds.add(slot.slotId)
                slotCounter += 1
            }
        }
        assertEquals(60, slotIds.size)

        // 200 combined sessions, commitments & assessments
        for (i in 0 until 80) {
            val dayOffset = (i % 28) + 1
            val iso = "2026-09-%02d".format(dayOffset)
            seedRepo.saveClassCheckIn(
                slotIds[i % slotIds.size],
                iso,
                if (i % 3 == 0) AttendanceStatus.CANCELLED else AttendanceStatus.ABSENT,
                null
            )
        }
        for (i in 0 until 60) {
            val dayOffset = (i % 28) + 1
            seedRepo.createCommitment(
                title = "Compromisso $i",
                description = "Descrição $i",
                commitmentDate = "2026-10-%02d".format(dayOffset),
                startTime = "17:10",
                endTime = "18:00",
                location = "Biblioteca"
            )
        }
        for (i in 0 until 60) {
            val dayOffset = (i % 28) + 1
            seedRepo.createAssessment(
                courseId = courseIds[i % courseIds.size],
                title = "AVALIAÇÃO $i",
                kind = if (i % 2 == 0) "Prova" else "Trabalho",
                dueDate = "2026-11-%02d".format(dayOffset),
                dueTime = "18:00"
            )
        }

        // Write initial state to disk via import + profile save, then measure save + cold reload
        val exportedSchedule = seedRepo.exportSchedulePayload("2026.2")
        val diskRepo = InMemoryCampusRepository(stateFile = stateFile)
        diskRepo.importSchedulePayload(exportedSchedule)
        for (i in 0 until 80) {
            val iso = "2026-09-%02d".format((i % 28) + 1)
            diskRepo.saveClassCheckIn(slotIds[i % slotIds.size], iso, AttendanceStatus.ABSENT, null)
        }
        for (i in 0 until 60) {
            diskRepo.createCommitment("Comp $i", "Desc $i", "2026-10-%02d".format((i % 28) + 1), "17:10", "18:00", "ICEB")
        }
        for (i in 0 until 60) {
            diskRepo.createAssessment(courseIds[i % courseIds.size], "PROVA $i", "Prova", "2026-11-%02d".format((i % 28) + 1), "18:00")
        }

        // Measure 1 full save + 1 full cold reload of 20 courses + 60 slots + 200 records
        val startNs = System.nanoTime()
        diskRepo.setDarkThemePreference("DARK") // triggers full atomic JSON serialization + fsync
        val coldLoaded = InMemoryCampusRepository(stateFile = stateFile)
        val saveAndReloadMs = (System.nanoTime() - startNs) / 1_000_000.0

        assertEquals(20, coldLoaded.listDisciplines("2026.2", null).size)
        assertEquals(60, coldLoaded.listAllAssessments("2026.2", null).size)
        assertTrue(
            "Save + cold reload took ${"%.2f".format(saveAndReloadMs)}ms (budget < 250ms)",
            saveAndReloadMs < 250.0
        )

        // Measure 100 iterations of getAgendaDay + getWeeklyTimetable
        val queryStartNs = System.nanoTime()
        repeat(100) { idx ->
            val iso = "2026-10-%02d".format((idx % 28) + 1)
            val agenda = coldLoaded.getAgendaDay(iso)
            val table = coldLoaded.getWeeklyTimetable("2026.2")
            assertTrue(agenda.weekDays.size == 7)
            assertTrue(table.rows.isNotEmpty())
        }
        val avgCallMs = ((System.nanoTime() - queryStartNs) / 1_000_000.0) / 100.0
        assertTrue(
            "Average getAgendaDay + getWeeklyTimetable took ${"%.2f".format(avgCallMs)}ms (budget < 25ms)",
            avgCallMs < 25.0
        )
    }

    // ── 7. Sprint 10: updateAssessment edit + course move + cold-restart persistence ─────────────

    @Test
    fun updateAssessment_editsFieldsMovesCourseAndPersistsAcrossColdRestart() {
        val stateFile = File(tempFolder.root, "assessment_edit_state.json")
        val repo1 = InMemoryCampusRepository(stateFile = stateFile)
        val c1 = repo1.createCourse("2026.2", "decat", "CAT411 Aquisição de Dados", "Prof. Rodrigo", 60)
        val c2 = repo1.createCourse("2026.2", "decom", "BCC704 Algoritmos e Estruturas de Dados", "Prof.ª Marina", 60)

        val created = repo1.createAssessment(
            courseId = c1.courseId,
            title = "PROVA 1 CAT411",
            kind = "Prova",
            dueDate = "2026-09-20",
            dueTime = "09:20"
        )
        assertEquals("20", created.dayNumber)
        assertEquals("SET", created.monthShort)
        assertFalse(created.completed)

        val versionBefore = repo1.mutationVersion.value
        val updated = repo1.updateAssessment(
            assessmentId = created.id,
            courseId = c2.courseId,
            title = "TRABALHO FINAL BCC704",
            kind = "Trabalho",
            dueDate = "2026-11-05",
            dueTime = "23:59",
            completed = true
        )

        assertTrue(repo1.mutationVersion.value > versionBefore)
        assertEquals(created.id, updated.id)
        assertEquals(c2.courseId, updated.courseId)
        assertEquals("TRABALHO FINAL BCC704", updated.title)
        assertEquals("Trabalho", updated.kind)
        assertEquals("2026-11-05", updated.dueDate)
        assertEquals("05", updated.dayNumber)
        assertEquals("NOV", updated.monthShort)
        assertEquals("23:59", updated.dueTime)
        assertTrue(updated.completed)

        // Old course no longer has it; new course does
        assertTrue(repo1.getDisciplineDetail(c1.courseId).assessments.isEmpty())
        assertEquals(1, repo1.getDisciplineDetail(c2.courseId).assessments.size)

        // Cold restart preserves moved/edited assessment
        val repo2 = InMemoryCampusRepository(stateFile = stateFile)
        assertTrue(repo2.getDisciplineDetail(c1.courseId).assessments.isEmpty())
        val reloaded = repo2.getDisciplineDetail(c2.courseId).assessments.single()
        assertEquals(created.id, reloaded.id)
        assertEquals("TRABALHO FINAL BCC704", reloaded.title)
        assertEquals("Trabalho", reloaded.kind)
        assertEquals("2026-11-05", reloaded.dueDate)
        assertEquals("05", reloaded.dayNumber)
        assertEquals("NOV", reloaded.monthShort)
        assertEquals("23:59", reloaded.dueTime)
        assertTrue(reloaded.completed)

        // Bridge repository also forwards updateAssessment and persists
        val bridge = CampusEngineBridgeRepository(dbPath = null, stateFile = stateFile)
        val bridgeVersionBefore = bridge.mutationVersion.value
        val bridgeUpdated = bridge.updateAssessment(
            assessmentId = created.id,
            courseId = c1.courseId,
            title = "SEMINÁRIO CAT411",
            kind = "Seminário",
            dueDate = "2026-12-03",
            dueTime = "14:00",
            completed = false
        )
        assertTrue(bridge.mutationVersion.value > bridgeVersionBefore)
        assertEquals(c1.courseId, bridgeUpdated.courseId)
        assertEquals("03", bridgeUpdated.dayNumber)
        assertEquals("DEZ", bridgeUpdated.monthShort)
        assertFalse(bridgeUpdated.completed)

        val repo3 = InMemoryCampusRepository(stateFile = stateFile)
        assertEquals(1, repo3.getDisciplineDetail(c1.courseId).assessments.size)
        assertTrue(repo3.getDisciplineDetail(c2.courseId).assessments.isEmpty())
        assertEquals("SEMINÁRIO CAT411", repo3.getDisciplineDetail(c1.courseId).assessments.single().title)
    }

    // ── 8. Sprint 10: DisciplineDetail enriched metadata (professor, location, code) ─────────────

    @Test
    fun getDisciplineDetail_populatesProfessorLocationAndCode() {
        val stateFile = File(tempFolder.root, "discipline_detail_state.json")
        val repo1 = InMemoryCampusRepository(stateFile = stateFile)
        val created = repo1.createCourse(
            semester = "2026.2",
            departmentId = "decat",
            name = "CAT425 Controle Digital",
            professor = "Prof. Dr. Leonardo Rocha",
            workloadHours = 60
        )
        repo1.addScheduleSlot(
            courseId = created.courseId,
            dayOfWeek = 2,
            startTime = "13:30",
            endTime = "15:10",
            building = "Escola de Minas (EM)",
            room = "Sala 204",
            shortLocation = "EM · 204"
        )

        val detail1 = repo1.getDisciplineDetail(created.courseId)
        assertEquals("Prof. Dr. Leonardo Rocha", detail1.professor)
        assertEquals("Escola de Minas (EM) · Sala 204", detail1.buildingAndRoom)
        assertEquals("EM · 204", detail1.shortLocation)
        assertEquals("CAT425", detail1.code)

        // Verify persistence across cold restart
        val repo2 = InMemoryCampusRepository(stateFile = stateFile)
        val detail2 = repo2.getDisciplineDetail(created.courseId)
        assertEquals("Prof. Dr. Leonardo Rocha", detail2.professor)
        assertEquals("Escola de Minas (EM) · Sala 204", detail2.buildingAndRoom)
        assertEquals("EM · 204", detail2.shortLocation)
        assertEquals("CAT425", detail2.code)
    }

    // ── 9. Sprint 10: setTotalRequiredHours updates AcademicCreReport & persists ─────────────────

    @Test
    fun setTotalRequiredHours_persistsAcrossColdRestartAndUpdatesCreReport() {
        val stateFile = File(tempFolder.root, "required_hours_state.json")
        val repo1 = InMemoryCampusRepository(stateFile = stateFile)
        assertEquals(3610, repo1.getAcademicCreReport().totalRequiredHours)

        val versionBefore = repo1.mutationVersion.value
        repo1.setTotalRequiredHours(3390)
        assertTrue(repo1.mutationVersion.value > versionBefore)
        assertEquals(3390, repo1.getAcademicCreReport().totalRequiredHours)

        // Cold restart retains custom required hours
        val repo2 = InMemoryCampusRepository(stateFile = stateFile)
        assertEquals(3390, repo2.getAcademicCreReport().totalRequiredHours)

        // Clamping bounds (100..10000) and BridgeRepository forwarding
        val bridge = CampusEngineBridgeRepository(dbPath = null, stateFile = stateFile)
        bridge.setTotalRequiredHours(50)
        assertEquals(100, bridge.getAcademicCreReport().totalRequiredHours)
        bridge.setTotalRequiredHours(99999)
        assertEquals(10000, bridge.getAcademicCreReport().totalRequiredHours)
        bridge.setTotalRequiredHours(7509)
        assertEquals(7509, InMemoryCampusRepository(stateFile = stateFile).getAcademicCreReport().totalRequiredHours)
    }

    // ── 10. Sprint 10: Official UFOP 2026/2 54-course Curriculum Catalog & benchmark ─────────────

    @Test
    fun ufopCurriculumCatalog_loadsAll54CoursesDisambiguatesCampusAndStaysUnder250ms() {
        val assetFile = listOf(
            File("src/main/assets/ufop_matrizes_2026_2.json"),
            File("app/src/main/assets/ufop_matrizes_2026_2.json"),
            File("android-app/app/src/main/assets/ufop_matrizes_2026_2.json")
        ).firstOrNull { it.exists() }
        assertNotNull("Asset ufop_matrizes_2026_2.json must exist in src/main/assets", assetFile)

        val rawJson = assetFile!!.readText(Charsets.UTF_8)
        assertTrue("Asset JSON should be non-empty", rawJson.length > 50_000)

        // Warmup + measure actual JSON load + Portuguese normalization speed
        val startNs = System.nanoTime()
        val courses = UfopCurriculumCatalog.loadFromJsonString(rawJson)
        val loadMs = (System.nanoTime() - startNs) / 1_000_000.0

        assertEquals("Catalog must contain all 54 official UFOP courses", 54, courses.size)
        val detailedCount = courses.count { it.subjects.isNotEmpty() }
        assertTrue("Expected >= 45 courses with detailed subjects, got $detailedCount", detailedCount >= 45)

        // Sanity checks on every course name & code (zero PDF wrap artifacts)
        courses.forEach { c ->
            assertTrue("Code must be clean: ${c.code}", c.code.isNotBlank() && !c.code.contains(")"))
            assertEquals(
                "Balanced parentheses in course name: ${c.name}",
                c.name.count { it == '(' },
                c.name.count { it == ')' }
            )
            c.subjects.forEach { s ->
                assertTrue("Subject name must not be blank for ${c.code}:${s.code}", s.name.isNotBlank())
                assertTrue("Subject type invalid: ${s.type}", s.type == "Obrigatória" || s.type == "Atividade")
                assertTrue("Period out of range: ${s.period}", s.period in 0..12)
            }
        }

        // Exact normalized lookup for AUT (Engenharia de Controle e Automação)
        val aut = UfopCurriculumCatalog.findCourse(courses, "ENGENHARIA DE CONTROLE E AUTOMACAO")
        assertNotNull("AUT must match unaccented uppercase name", aut)
        assertEquals("AUT", aut!!.code)
        assertTrue("AUT chTotal expected > 3000, got ${aut.chTotal}", aut.chTotal > 3000)
        assertTrue("AUT subjects expected > 40, got ${aut.subjects.size}", aut.subjects.size > 40)
        assertTrue(
            "AUT subjects should have normalized Portuguese accents",
            aut.subjects.any { it.name.contains("Cálculo") || it.name.contains("Programação") || it.name.contains("Introdução") }
        )

        // Campus disambiguation for Engenharia de Produção (Ouro Preto -> PRO, João Monlevade -> PJM)
        val prodOp = UfopCurriculumCatalog.findCourse(courses, "Engenharia de Produção", campusHint = "Ouro Preto")
        assertNotNull(prodOp)
        assertEquals("PRO", prodOp!!.code)

        val prodJm = UfopCurriculumCatalog.findCourse(courses, "Engenharia de Produção", campusHint = "João Monlevade")
        assertNotNull(prodJm)
        assertEquals("PJM", prodJm!!.code)
        assertEquals("Engenharia de Produção (João Monlevade)", prodJm.name)

        // Exact code lookup & accent-insensitive search
        assertEquals("COM", UfopCurriculumCatalog.findCourse(courses, "COM")?.code)
        val searchComputacao = UfopCurriculumCatalog.searchCourses(courses, "computacao")
        assertTrue(searchComputacao.any { it.code == "COM" })
        assertTrue(searchComputacao.any { it.code == "CJM" })

        assertTrue(
            "UfopCurriculumCatalog.loadFromJsonString took ${"%.2f".format(loadMs)}ms (budget < 250ms)",
            loadMs < 250.0
        )
    }

    // ── 11. Sprint 11/12: CampusAccentColor contract (7 colors, NO Blue) & fromKey resolution ───

    @Test
    fun campusAccentColor_fromKeyResolvesAllSevenAccentsAndExcludesBlue() {
        assertEquals(7, CampusAccentColor.entries.size)
        assertEquals(
            listOf("GREEN", "RED", "ORANGE", "PURPLE", "YELLOW", "CYAN", "BROWN"),
            CampusAccentColor.entries.map { it.storageKey }
        )
        assertFalse(CampusAccentColor.entries.any { it.storageKey.equals("BLUE", ignoreCase = true) })

        assertEquals("Verde", CampusAccentColor.GREEN.displayName)
        assertEquals("#3B6939", CampusAccentColor.GREEN.previewHex)
        assertEquals("Vermelho", CampusAccentColor.RED.displayName)
        assertEquals("#B3261E", CampusAccentColor.RED.previewHex)
        assertEquals("Laranja", CampusAccentColor.ORANGE.displayName)
        assertEquals("#C75B00", CampusAccentColor.ORANGE.previewHex)
        assertEquals("Roxo", CampusAccentColor.PURPLE.displayName)
        assertEquals("#6B3FA0", CampusAccentColor.PURPLE.previewHex)
        assertEquals("Amarelo", CampusAccentColor.YELLOW.displayName)
        assertEquals("#B58500", CampusAccentColor.YELLOW.previewHex)
        assertEquals("Ciano", CampusAccentColor.CYAN.displayName)
        assertEquals("#00838F", CampusAccentColor.CYAN.previewHex)
        assertEquals("Marrom", CampusAccentColor.BROWN.displayName)
        assertEquals("#6D4C41", CampusAccentColor.BROWN.previewHex)

        assertEquals(CampusAccentColor.GREEN, CampusAccentColor.fromKey("GREEN"))
        assertEquals(CampusAccentColor.RED, CampusAccentColor.fromKey("red"))
        assertEquals(CampusAccentColor.ORANGE, CampusAccentColor.fromKey("  OrAnGe  "))
        assertEquals(CampusAccentColor.PURPLE, CampusAccentColor.fromKey("purple"))
        assertEquals(CampusAccentColor.YELLOW, CampusAccentColor.fromKey("yellow"))
        assertEquals(CampusAccentColor.CYAN, CampusAccentColor.fromKey("  cYaN "))
        assertEquals(CampusAccentColor.BROWN, CampusAccentColor.fromKey("BROWN"))

        // Fallback to GREEN for null, blank, invalid, or reserved BLUE
        assertEquals(CampusAccentColor.GREEN, CampusAccentColor.fromKey(null))
        assertEquals(CampusAccentColor.GREEN, CampusAccentColor.fromKey(""))
        assertEquals(CampusAccentColor.GREEN, CampusAccentColor.fromKey("   "))
        assertEquals(CampusAccentColor.GREEN, CampusAccentColor.fromKey("BLUE"))
        assertEquals(CampusAccentColor.GREEN, CampusAccentColor.fromKey("UNKNOWN_COLOR"))
    }

    // ── 12. Sprint 11/12: Accent color & theme persistence (encrypted disk + holder + bridge) ────

    @Test
    fun accentColorPreference_persistsAcrossColdRestartHolderAndBridge() {
        assertEquals("vida_academica_prefs", CampusRepositoryHolder.PREFS_NAME)
        assertEquals("accent_color_key", CampusRepositoryHolder.KEY_ACCENT_COLOR)

        val stateFile = File(tempFolder.root, "accent_color_state.json")
        val repo1 = InMemoryCampusRepository(stateFile = stateFile)
        assertEquals("GREEN", repo1.getAccentColorPreference())

        val prefs = InMemorySharedPreferences()
        var widgetUpdates = 0

        CampusRepositoryHolder.saveAccentColorToPrefs(
            prefs = prefs,
            repository = repo1,
            accentKey = "  purple ",
            onWidgetUpdate = { widgetUpdates += 1 }
        )

        assertEquals(1, prefs.commitCount)
        assertEquals(0, prefs.applyCount)
        assertEquals("PURPLE", prefs.getString(CampusRepositoryHolder.KEY_ACCENT_COLOR, null))
        assertEquals("PURPLE", repo1.getAccentColorPreference())
        assertEquals(1, widgetUpdates)

        // State file on disk is AES-256-GCM encrypted (UFOPENC1:) and decrypts to JSON with both keys
        val persistedRaw = stateFile.readText(Charsets.UTF_8)
        assertTrue(persistedRaw.startsWith(CampusCryptoEngine.STATE_PREFIX))
        assertFalse(persistedRaw.contains("PURPLE"))
        val persistedJson = CampusCryptoEngine.decryptStatePayloadIfNeeded(persistedRaw)
        assertTrue(persistedJson.contains("\"accentColorPreference\":\"PURPLE\""))
        assertTrue(persistedJson.contains("\"accent_color_key\":\"PURPLE\""))

        // Cold restart of repository + empty SharedPreferences falls back to persisted JSON
        val repo2 = InMemoryCampusRepository(stateFile = stateFile)
        assertEquals("PURPLE", repo2.getAccentColorPreference())
        assertEquals(
            "PURPLE",
            CampusRepositoryHolder.loadSavedAccentColorFromPrefs(InMemorySharedPreferences(), repo2)
        )

        // SharedPreferences takes precedence when valid
        val prefsWithOrange = InMemorySharedPreferences().apply {
            edit().putString(CampusRepositoryHolder.KEY_ACCENT_COLOR, "ORANGE").commit()
        }
        assertEquals(
            "ORANGE",
            CampusRepositoryHolder.loadSavedAccentColorFromPrefs(prefsWithOrange, repo2)
        )

        // Invalid value in SharedPreferences (e.g. BLUE) falls back to repository JSON
        val prefsWithInvalid = InMemorySharedPreferences().apply {
            edit().putString(CampusRepositoryHolder.KEY_ACCENT_COLOR, "BLUE").commit()
        }
        assertEquals(
            "PURPLE",
            CampusRepositoryHolder.loadSavedAccentColorFromPrefs(prefsWithInvalid, repo2)
        )

        // BridgeRepository forwards get/setAccentColorPreference and persists across cold restart
        val bridge = CampusEngineBridgeRepository(dbPath = null, stateFile = stateFile)
        assertEquals("PURPLE", bridge.getAccentColorPreference())
        val vBefore = bridge.mutationVersion.value
        bridge.setAccentColorPreference("red")
        assertTrue(bridge.mutationVersion.value > vBefore)
        assertEquals("RED", bridge.getAccentColorPreference())
        assertEquals("RED", InMemoryCampusRepository(stateFile = stateFile).getAccentColorPreference())

        // Invalid value set via repository falls back to GREEN
        bridge.setAccentColorPreference("BLUE")
        assertEquals("GREEN", InMemoryCampusRepository(stateFile = stateFile).getAccentColorPreference())

        // Dark theme save also uses synchronous commit() and notifies widget
        val darkPrefs = InMemorySharedPreferences()
        var darkWidgetUpdates = 0
        CampusRepositoryHolder.saveDarkThemeToPrefs(
            prefs = darkPrefs,
            repository = bridge,
            isDark = true,
            onWidgetUpdate = { darkWidgetUpdates += 1 }
        )
        assertEquals(1, darkPrefs.commitCount)
        assertEquals(0, darkPrefs.applyCount)
        assertEquals("DARK", darkPrefs.getString(CampusRepositoryHolder.KEY_DARK_THEME_MODE, null))
        assertEquals("DARK", bridge.getDarkThemePreference())
        assertEquals(1, darkWidgetUpdates)
    }

    // ── 13. Sprint 11: parseEnrollmentYearSemester formats & edge cases ─────────────────────────

    @Test
    fun parseEnrollmentYearSemester_handlesAllUfopMatriculaFormatsAndRejectsInvalid() {
        assertEquals(2024 to 2, parseEnrollmentYearSemester("24.2.1165"))
        assertEquals(2024 to 1, parseEnrollmentYearSemester("2024.1.1234"))
        assertEquals(2026 to 1, parseEnrollmentYearSemester("26.1.1000"))
        assertEquals(2026 to 2, parseEnrollmentYearSemester("26.2.0042"))
        assertEquals(1998 to 1, parseEnrollmentYearSemester("98.1.4321"))
        assertEquals(2024 to 2, parseEnrollmentYearSemester("  24/2/1165  "))
        assertEquals(2023 to 1, parseEnrollmentYearSemester("23-1-8099"))
        assertEquals(2022 to 2, parseEnrollmentYearSemester("Matrícula nº 22.2.4110"))
        assertEquals(2024 to 2, parseEnrollmentYearSemester("2421165"))
        assertEquals(2024 to 1, parseEnrollmentYearSemester("202411234"))

        // Invalid or non-semester numbers return null
        assertNull(parseEnrollmentYearSemester(""))
        assertNull(parseEnrollmentYearSemester("   "))
        assertNull(parseEnrollmentYearSemester("24.3.1165"))
        assertNull(parseEnrollmentYearSemester("24.0.1165"))
        assertNull(parseEnrollmentYearSemester("SEM-MATRICULA"))
        assertNull(parseEnrollmentYearSemester("1899.1.1000"))

        // Object delegate matches top-level function
        assertEquals(2024 to 2, UfopCurriculumCatalog.parseEnrollmentYearSemester("24.2.1165"))
    }

    // ── 14. Sprint 11: checkCurriculumCompatibility (legacy < 2026/1 & code mismatch) ──────────

    @Test
    fun checkCurriculumCompatibility_detectsLegacyEnrollmentTranscriptAndCodeMismatch() {
        val sampleCourse = UfopCourseCurriculum(
            code = "AUT",
            name = "Engenharia de Controle e Automação",
            degree = "Bacharelado",
            campus = "Ouro Preto",
            curriculumVersion = "2026/2",
            chMandatory = 3000,
            chOptional = 240,
            chElective = 120,
            chExtension = 360,
            chActivities = 120,
            chTotal = 3840,
            status = "Ativo",
            subjects = listOf("MTM701", "FIS701", "BCC701", "CAT701", "CAT702", "ELT701").mapIndexed { idx, code ->
                UfopCurriculumSubject(
                    code = code,
                    name = "Disciplina $code",
                    departmentId = "decat",
                    departmentCode = "DECAT",
                    period = (idx % 4) + 1,
                    type = "Obrigatória",
                    chs = 60,
                    che = 0,
                    cha = 60,
                    weeklyTheory = 4,
                    weeklyPractice = 0,
                    prerequisites = emptyList()
                )
            }
        )

        // 1. Legacy matrícula 24.2.1165 (< 2026/1) -> legacy mismatch even if codes match
        val legacyByReg = checkCurriculumCompatibility(
            registrationNumber = "24.2.1165",
            earliestTranscriptSemester = "2024.2",
            transcriptCourseCodes = setOf("MTM701", "FIS701"),
            course = sampleCourse
        )
        assertTrue(legacyByReg.isLegacyMatrixMismatch)
        assertEquals("2024/2", legacyByReg.studentEntryLabel)
        assertEquals("2026/2", legacyByReg.matrixVersionLabel)
        assertEquals(0, legacyByReg.unmatchedTranscriptCodesCount)

        // 2. Compatible matrícula 26.1.1000 (>= 2026/1) with matching courses + 1 optional/elective
        val compatible2026 = checkCurriculumCompatibility(
            registrationNumber = "26.1.1000",
            earliestTranscriptSemester = "2026.1",
            transcriptCourseCodes = setOf("MTM701", "FIS701", "BCC701", "CAT701", "OPT999"),
            course = sampleCourse
        )
        assertFalse(compatible2026.isLegacyMatrixMismatch)
        assertEquals("2026/1", compatible2026.studentEntryLabel)
        assertEquals("2026/2", compatible2026.matrixVersionLabel)
        assertEquals(1, compatible2026.unmatchedTranscriptCodesCount)

        // 3. Blank matrícula, but earliest transcript semester is legacy (2025/2 < 2026/1)
        val legacyByTranscriptSem = checkCurriculumCompatibility(
            registrationNumber = "",
            earliestTranscriptSemester = "2025/2",
            transcriptCourseCodes = setOf("MTM701"),
            course = sampleCourse
        )
        assertTrue(legacyByTranscriptSem.isLegacyMatrixMismatch)
        assertEquals("2025/2", legacyByTranscriptSem.studentEntryLabel)
        assertEquals("2026/2", legacyByTranscriptSem.matrixVersionLabel)
        assertEquals(0, legacyByTranscriptSem.unmatchedTranscriptCodesCount)

        // 4. Relevant code divergence (old codes absent from 2026/2 matrix) even with 26.1 registration
        val legacyByCodeDivergence = checkCurriculumCompatibility(
            registrationNumber = "26.1.1000",
            earliestTranscriptSemester = "2026.1",
            transcriptCourseCodes = setOf("MTM122", "FIS105", "BCC201", "CAT101", "MTM701"),
            course = sampleCourse
        )
        assertTrue(legacyByCodeDivergence.isLegacyMatrixMismatch)
        assertEquals("2026/1", legacyByCodeDivergence.studentEntryLabel)
        assertEquals("2026/2", legacyByCodeDivergence.matrixVersionLabel)
        assertEquals(4, legacyByCodeDivergence.unmatchedTranscriptCodesCount)

        // 5. Fresh 2026/2 student with no transcript yet -> compatible
        val freshStudent = UfopCurriculumCatalog.checkCurriculumCompatibility(
            registrationNumber = "26.2.0512",
            earliestTranscriptSemester = null,
            transcriptCourseCodes = emptySet(),
            course = sampleCourse
        )
        assertFalse(freshStudent.isLegacyMatrixMismatch)
        assertEquals("2026/2", freshStudent.studentEntryLabel)
        assertEquals("2026/2", freshStudent.matrixVersionLabel)
        assertEquals(0, freshStudent.unmatchedTranscriptCodesCount)
    }

    @Test
    fun resolveCourseForStudent_selectsAut2024_1Or2026_2ByEntryAndKeepsLegacyFlagForUnversionedCourses() {
        val assetFile = listOf(
            File("src/main/assets/ufop_matrizes_2026_2.json"),
            File("app/src/main/assets/ufop_matrizes_2026_2.json"),
            File("android-app/app/src/main/assets/ufop_matrizes_2026_2.json")
        ).firstOrNull { it.exists() }
        assertNotNull("Asset ufop_matrizes_2026_2.json must exist", assetFile)
        val courses = UfopCurriculumCatalog.loadFromJsonString(assetFile!!.readText(Charsets.UTF_8))

        val autBase = UfopCurriculumCatalog.findCourse(courses, "AUT")
        assertNotNull(autBase)
        val autVersions = UfopCurriculumCatalog.getCourseVersions(autBase!!)
        assertEquals(2, autVersions.size)

        // 1. AUT com matrícula 24.1.4001 -> retorna 2024/1 (Currículo 2) (3610h, 49 disciplinas) e isLegacyMatrixMismatch == false
        val aut2024ByReg = UfopCurriculumCatalog.resolveCourseForStudent(
            baseCourse = autBase,
            registrationNumber = "24.1.4001",
            earliestTranscriptSemester = null
        )
        assertEquals("2024/1 (Currículo 2)", aut2024ByReg.curriculumVersion)
        assertEquals(3610, aut2024ByReg.chTotal)
        assertEquals(2670, aut2024ByReg.chMandatory)
        assertEquals(300, aut2024ByReg.chElective)
        assertEquals(640, aut2024ByReg.chActivities)
        assertEquals(49, aut2024ByReg.subjects.size)
        val compatAut2024 = checkCurriculumCompatibility(
            registrationNumber = "24.1.4001",
            earliestTranscriptSemester = null,
            transcriptCourseCodes = setOf("MTM122", "BCC201", "CAT311"),
            course = aut2024ByReg
        )
        assertFalse(compatAut2024.isLegacyMatrixMismatch)
        assertEquals("2024/1", compatAut2024.studentEntryLabel)
        assertEquals("2024/1 (Currículo 2)", compatAut2024.matrixVersionLabel)

        // 2. AUT com matrícula 25.2.9999 ou histórico "2024.1" -> retorna 2024/1 (Currículo 2) e isLegacyMatrixMismatch == false
        val aut2025ByReg = UfopCurriculumCatalog.resolveCourseForStudent(
            baseCourse = autBase,
            registrationNumber = "25.2.9999",
            earliestTranscriptSemester = null
        )
        assertEquals("2024/1 (Currículo 2)", aut2025ByReg.curriculumVersion)
        assertEquals(3610, aut2025ByReg.chTotal)
        assertEquals(49, aut2025ByReg.subjects.size)
        assertFalse(
            checkCurriculumCompatibility(
                registrationNumber = "25.2.9999",
                earliestTranscriptSemester = null,
                transcriptCourseCodes = emptySet(),
                course = aut2025ByReg
            ).isLegacyMatrixMismatch
        )

        val aut2024ByTranscript = UfopCurriculumCatalog.resolveCourseForStudent(
            baseCourse = autBase,
            registrationNumber = "",
            earliestTranscriptSemester = "2024.1"
        )
        assertEquals("2024/1 (Currículo 2)", aut2024ByTranscript.curriculumVersion)
        assertEquals(3610, aut2024ByTranscript.chTotal)
        assertEquals(49, aut2024ByTranscript.subjects.size)
        assertFalse(
            checkCurriculumCompatibility(
                registrationNumber = "",
                earliestTranscriptSemester = "2024.1",
                transcriptCourseCodes = emptySet(),
                course = aut2024ByTranscript
            ).isLegacyMatrixMismatch
        )

        // 3. AUT com matrícula 26.1.1234 -> retorna versão 2026/2 (3390h) e isLegacyMatrixMismatch == false
        val aut2026 = UfopCurriculumCatalog.resolveCourseForStudent(
            baseCourse = autBase,
            registrationNumber = "26.1.1234",
            earliestTranscriptSemester = "2026.1"
        )
        assertEquals(autBase.curriculumVersion, aut2026.curriculumVersion)
        assertEquals(3390, aut2026.chTotal)
        val compatAut2026 = checkCurriculumCompatibility(
            registrationNumber = "26.1.1234",
            earliestTranscriptSemester = "2026.1",
            transcriptCourseCodes = emptySet(),
            course = aut2026
        )
        assertFalse(compatAut2026.isLegacyMatrixMismatch)
        assertEquals(autBase.curriculumVersion, compatAut2026.matrixVersionLabel)

        // 4. Curso sem matriz histórica 2024 cadastrada (COM) com 24.1.4001 continua retornando isLegacyMatrixMismatch == true
        val comBase = UfopCurriculumCatalog.findCourse(courses, "COM")
        assertNotNull(comBase)
        val comResolved = UfopCurriculumCatalog.resolveCourseForStudent(
            baseCourse = comBase!!,
            registrationNumber = "24.1.4001",
            earliestTranscriptSemester = "2024.1"
        )
        assertEquals(comBase.curriculumVersion, comResolved.curriculumVersion)
        val compatCom2024 = checkCurriculumCompatibility(
            registrationNumber = "24.1.4001",
            earliestTranscriptSemester = "2024.1",
            transcriptCourseCodes = emptySet(),
            course = comResolved
        )
        assertTrue(compatCom2024.isLegacyMatrixMismatch)
    }

    // ── 15. Sprint 12: Rigorous Syllabus Validation (wrong course, partial, complete) ──────────

    @Test
    fun importCourseSyllabusDetailedText_validatesCourseMatchExtractsProfessorAndWarnsOnPartial() {
        val repo = InMemoryCampusRepository()
        val bcc201 = repo.createCourse("2026.2", "decom", "BCC201 Programação I", "", 60)
        val mtm125 = repo.createCourse("2026.2", "demat", "MTM125 Equações Diferenciais Ordinárias", "", 60)
        val calc1 = repo.createCourse("2026.2", "demat", "Cálculo I", "", 90)

        // 1. Syllabus from another course is rejected with zero mutations
        val wrongSyllabus = """
            UNIVERSIDADE FEDERAL DE OURO PRETO
            PLANO DE ENSINO - FIS105 - FÍSICA I
            Professor: Prof. Roberto Mendes
            18/09/2026 - Prova 1 (30 pts)
        """.trimIndent()
        val wrongReport = repo.importCourseSyllabusDetailedText(bcc201.courseId, wrongSyllabus)
        assertFalse(wrongReport.matchedCourse)
        assertNotNull(wrongReport.errorMessage)
        assertEquals(0, wrongReport.importedCount)
        assertEquals("", repo.getDisciplineDetail(bcc201.courseId).professor)
        assertTrue(repo.getDisciplineDetail(bcc201.courseId).assessments.isEmpty())

        // Level conflict (Cálculo II syllabus uploaded to Cálculo I without matching code) is rejected
        val calc2Syllabus = """
            PLANO DE ENSINO
            Disciplina: Cálculo II
            Professor: Prof.ª Helena
            18/09/2026 - Prova 1 (30 pts)
        """.trimIndent()
        val levelMismatch = repo.importCourseSyllabusDetailedText(calc1.courseId, calc2Syllabus)
        assertFalse(levelMismatch.matchedCourse)
        assertNotNull(levelMismatch.errorMessage)
        assertTrue(repo.getDisciplineDetail(calc1.courseId).assessments.isEmpty())

        // 2. Partial syllabus (course + professor only, no grade distribution and no assessment dates)
        val partialSyllabus = """
            UNIVERSIDADE FEDERAL DE OURO PRETO
            Disciplina: BCC201 - Programação I
            Pré-requisitos: Nenhum (ou MTM122 opcional)
            Docente: Prof.ª Marina Costa
            Ementa: Estruturas de controle, vetores, matrizes e funções.
            Frequência mínima exigida: 75% das aulas.
        """.trimIndent()
        val partialReport = repo.importCourseSyllabusDetailedText(bcc201.courseId, partialSyllabus)
        assertTrue(partialReport.matchedCourse)
        assertNull(partialReport.errorMessage)
        assertNotNull(partialReport.warningMessage)
        assertFalse(partialReport.hasGradeDistribution)
        assertFalse(partialReport.hasAssessmentDates)
        assertEquals("Prof.ª Marina Costa", partialReport.extractedProfessor)
        assertEquals(0, partialReport.importedCount)
        assertEquals("Prof.ª Marina Costa", repo.getDisciplineDetail(bcc201.courseId).professor)

        // 3. Complete syllabus extracts professor, grade distribution & assessment dates
        val completeSyllabus = """
            PLANO DE ENSINO
            Disciplina: MTM125 - Equações Diferenciais Ordinárias
            Professor responsável: Prof. Marcos Antonio Silva
            Distribuição dos pontos: P1 (30 pts), P2 (35 pts), P3 (35 pts)
            18/09/2026 - P1 - 1ª Prova Escrita (30 pts)
            20/10/2026 - P2 - 2ª Prova Escrita (35 pts)
            24/11/2026 - P3 (35 pts)
        """.trimIndent()
        val fullReport = repo.importCourseSyllabusDetailedText(mtm125.courseId, completeSyllabus)
        assertTrue(fullReport.matchedCourse)
        assertNull(fullReport.errorMessage)
        assertNull(fullReport.warningMessage)
        assertTrue(fullReport.hasGradeDistribution)
        assertTrue(fullReport.hasAssessmentDates)
        assertEquals("Prof. Marcos Antônio Silva", fullReport.extractedProfessor)
        assertEquals(3, fullReport.importedCount)
        val mtmDetail = repo.getDisciplineDetail(mtm125.courseId)
        assertEquals("Prof. Marcos Antônio Silva", mtmDetail.professor)
        assertEquals(3, mtmDetail.assessments.size)
    }

    // ── 16. Sprint 12: Atestado with EAD/CEAD/A DISTÂNCIA detection ─────────────────────────────

    @Test
    fun atestadoParser_detectsEadCeadAndDistanciaWithoutPhysicalBuilding() {
        val repo = InMemoryCampusRepository()
        val certText = """
            UNIVERSIDADE FEDERAL DE OURO PRETO
            ATESTADO DE MATRÍCULA 2026/2
            Atestamos que CAMILA RIBEIRO SOUZA matrícula nº 24.1.2090 está regularmente matriculada no curso de CIENCIA DA COMPUTACAO, cuja situação é regular no campus Ouro Preto.

            EAD101 11 METODOLOGIA CIENTIFICA A DISTANCIA CEAD PORTUGUES
            CET202 12 INFORMATICA BASICA EAD PORTUGUES
            BCC201 11 PROGRAMACAO I ICEB PORTUGUES

            Horário          Segunda        Terça          Quarta
            08:00 - 08:50    EAD101-11T     CET202-12T     BCC201-11T
            08:50 - 09:40    EAD101-11T     CET202-12T     BCC201-11T
        """.trimIndent()

        val count = repo.importEnrollmentCertificateText(certText)
        assertEquals(3, count)

        val eadDetail = repo.getDisciplineDetail("ead101_2026_2")
        assertEquals("Ensino a Distância (EAD)", eadDetail.buildingAndRoom)
        assertEquals("EAD", eadDetail.shortLocation)
        val eadSlots = repo.listCourseSlots("ead101_2026_2")
        assertEquals(1, eadSlots.size)
        assertEquals("Ensino a Distância (EAD)", eadSlots[0].building)
        assertEquals("EAD", eadSlots[0].shortLocation)

        val cetDetail = repo.getDisciplineDetail("cet202_2026_2")
        assertEquals("Ensino a Distância (EAD)", cetDetail.buildingAndRoom)
        assertEquals("EAD", cetDetail.shortLocation)
        val cetSlots = repo.listCourseSlots("cet202_2026_2")
        assertEquals(1, cetSlots.size)
        assertEquals("Ensino a Distância (EAD)", cetSlots[0].building)
        assertEquals("EAD", cetSlots[0].shortLocation)

        val bccDetail = repo.getDisciplineDetail("bcc201_2026_2")
        assertTrue(bccDetail.buildingAndRoom.startsWith("ICEB"))
        assertEquals("ICEB", bccDetail.shortLocation)
    }

    // ── 17. Sprint 12: Empty professor remains blank (never injects "Docente UFOP") ────────────

    @Test
    fun manualCourseAndAtestadoWithoutProfessor_keepProfessorBlankWithoutDocenteUfop() {
        val stateFile = File(tempFolder.root, "blank_prof_state.json")
        val repo = InMemoryCampusRepository(stateFile = stateFile)

        // Manual course creation with blank professor
        val manual = repo.createCourse("2026.2", "decom", "BCC301 Compiladores", "   ", 60)
        val manualSlot = repo.addScheduleSlot(manual.courseId, 1, "08:00", "09:40", "ICEB", "Sala 10", "ICEB · 10")
        assertEquals("", repo.getDisciplineDetail(manual.courseId).professor)
        assertEquals("", repo.getClassCheckIn(manualSlot.slotId, "2026-08-24").professor)

        // Atestado import without transcript professor
        val certText = """
            ATESTADO DE MATRÍCULA 2026/2
            Atestamos que PEDRO ALVES matrícula nº 25.1.1001 está regularmente matriculado no curso de CIENCIA DA COMPUTACAO, cuja situação é regular no campus Ouro Preto.

            MTM122 11 CALCULO I ICEB PORTUGUES

            Horário          Segunda
            10:00 - 10:50    MTM122-11T
        """.trimIndent()
        repo.importEnrollmentCertificateText(certText)
        val mtmDetail = repo.getDisciplineDetail("mtm122_2026_2")
        assertEquals("", mtmDetail.professor)
        assertFalse(mtmDetail.professor.contains("Docente UFOP", ignoreCase = true))

        // Cold restart still preserves empty professor
        val repoReloaded = InMemoryCampusRepository(stateFile = stateFile)
        assertEquals("", repoReloaded.getDisciplineDetail("mtm122_2026_2").professor)
    }

    // ── 18. Sprint 12: Accessibility, GuidedTour, ReleaseVersion, Snapshot & Logout Wipe ─────────

    @Test
    fun sprint12PreferencesSnapshotRestoreAndLogoutWipe_workAcrossColdRestartHolderAndBridge() {
        val stateFile = File(tempFolder.root, "sprint12_full_state.json")
        val repo1 = InMemoryCampusRepository(stateFile = stateFile)
        val prefs = InMemorySharedPreferences()

        repo1.saveStudentProfile(
            StudentProfile(
                fullName = "Sofia Andrade",
                registrationNumber = "26.1.3002",
                courseName = "Ciência da Computação",
                campusName = "Ouro Preto",
                activeSemester = "2026.2",
                onboardingCompleted = true
            )
        )
        val course = repo1.createCourse("2026.2", "decom", "BCC201 Programação I", "Prof. Rafael", 60)
        repo1.createAssessment(course.courseId, "PROVA 1", "Prova", "2026-09-30", "08:00")
        repo1.setAccentColorPreference("CYAN")

        val customA11y = CampusAccessibilitySettings(
            screenReaderOptimized = true,
            fontScaleMultiplier = 1.45f,
            highContrastMode = true,
            reduceMotion = true,
            largeTouchTargets = true,
            boldTextBoost = true
        )
        var a11yWidgetUpdates = 0
        CampusRepositoryHolder.saveAccessibilitySettingsToPrefs(
            prefs = prefs,
            repository = repo1,
            settings = customA11y,
            onWidgetUpdate = { a11yWidgetUpdates += 1 }
        )
        assertEquals(1, a11yWidgetUpdates)
        assertEquals(1, prefs.commitCount)

        CampusRepositoryHolder.setGuidedTourCompletedToPrefs(prefs, repo1, true)
        CampusRepositoryHolder.setLastSeenReleaseVersionToPrefs(prefs, repo1, "1.8.0")

        // Clamping check on out-of-range fontScaleMultiplier
        repo1.setAccessibilitySettings(customA11y.copy(fontScaleMultiplier = Float.NaN))
        assertEquals(1.0f, repo1.getAccessibilitySettings().fontScaleMultiplier, 0.001f)
        repo1.setAccessibilitySettings(customA11y.copy(fontScaleMultiplier = 9.0f))
        assertEquals(2.0f, repo1.getAccessibilitySettings().fontScaleMultiplier, 0.001f)
        repo1.setAccessibilitySettings(customA11y)

        // Cold restart restores a11y, guidedTourCompleted, lastSeenReleaseVersion
        val repo2 = InMemoryCampusRepository(stateFile = stateFile)
        assertEquals(customA11y, repo2.getAccessibilitySettings())
        assertTrue(repo2.isGuidedTourCompleted())
        assertEquals("1.8.0", repo2.getLastSeenReleaseVersion())
        assertEquals(
            customA11y,
            CampusRepositoryHolder.loadAccessibilitySettingsFromPrefs(InMemorySharedPreferences(), repo2)
        )
        assertTrue(CampusRepositoryHolder.isGuidedTourCompletedFromPrefs(InMemorySharedPreferences(), repo2))
        assertEquals(
            "1.8.0",
            CampusRepositoryHolder.getLastSeenReleaseVersionFromPrefs(InMemorySharedPreferences(), repo2)
        )

        // Export & restore full account snapshot JSON into brand-new repository
        val snapshotJson = repo2.exportFullAccountSnapshotJson()
        val restoreFile = File(tempFolder.root, "restored_state.json")
        val targetBridge = CampusEngineBridgeRepository(dbPath = null, stateFile = restoreFile)
        assertFalse(targetBridge.restoreFullAccountSnapshotJson("{invalid"))
        assertFalse(targetBridge.restoreFullAccountSnapshotJson("   "))
        assertTrue(targetBridge.restoreFullAccountSnapshotJson(snapshotJson))

        assertEquals("Sofia Andrade", targetBridge.getStudentProfile().fullName)
        assertEquals("CYAN", targetBridge.getAccentColorPreference())
        assertEquals(customA11y, targetBridge.getAccessibilitySettings())
        assertTrue(targetBridge.isGuidedTourCompleted())
        assertEquals("1.8.0", targetBridge.getLastSeenReleaseVersion())
        assertEquals(1, targetBridge.listDisciplines("2026.2", null).size)
        assertEquals(1, targetBridge.getDisciplineDetail(course.courseId).assessments.size)
        assertTrue(restoreFile.readText(Charsets.UTF_8).startsWith(CampusCryptoEngine.STATE_PREFIX))

        // Wipe all user data for logout deletes disk files and resets state to virgin
        val bakFile = File(restoreFile.parentFile, "${restoreFile.name}.bak")
        targetBridge.setDarkThemePreference("DARK") // ensure .bak exists
        assertTrue(restoreFile.exists())
        assertTrue(bakFile.exists())

        targetBridge.wipeAllUserDataForLogout()
        assertFalse("State file must be deleted on logout wipe", restoreFile.exists())
        assertFalse("Backup .bak file must be deleted on logout wipe", bakFile.exists())
        assertFalse(targetBridge.getStudentProfile().onboardingCompleted)
        assertEquals("", targetBridge.getStudentProfile().fullName)
        assertTrue(targetBridge.listDisciplines("2026.2", null).isEmpty())
        assertEquals("GREEN", targetBridge.getAccentColorPreference())
        assertEquals(CampusAccessibilitySettings(), targetBridge.getAccessibilitySettings())
        assertFalse(targetBridge.isGuidedTourCompleted())
        assertEquals("", targetBridge.getLastSeenReleaseVersion())
    }

    @Test
    fun bridgeRepositoryUsesEncryptedStateAsSingleSourceOfTruthWhenDbPathProvided() {
        val stateFile = File(tempFolder.root, "campus_ufop_state.json")
        val dbFile = File(tempFolder.root, "campus_ufop.db")

        val seedRepo = InMemoryCampusRepository(stateFile = stateFile)
        seedRepo.saveStudentProfile(
            StudentProfile(
                fullName = "Aluno UFOP",
                activeSemester = "2026.2",
                onboardingCompleted = true
            )
        )
        val course = seedRepo.createCourse(
            semester = "2026.2",
            departmentId = "decom",
            name = "Estrutura de Dados",
            professor = "Prof. Ana",
            workloadHours = 60,
            colorOverrideHex = null
        )
        seedRepo.addScheduleSlot(
            courseId = course.courseId,
            dayOfWeek = 1,
            startTime = "08:00",
            endTime = "09:40",
            building = "ICEB",
            room = "205",
            shortLocation = "ICEB · 205"
        )
        seedRepo.createCommitment(
            title = "Monitoria BCC202",
            description = "Sala 205",
            commitmentDate = "2026-11-24",
            startTime = "14:00",
            endTime = "15:00",
            location = "ICEB",
            weatherTempC = null
        )
        seedRepo.createAssessment(
            courseId = course.courseId,
            title = "Prova 1",
            kind = "Prova",
            dueDate = "2026-11-30",
            dueTime = "08:00"
        )

        val bridge = CampusEngineBridgeRepository(
            dbPath = dbFile.absolutePath,
            stateFile = stateFile
        )
        assertFalse(bridge.isNativeCoreActive)
        assertEquals("Aluno UFOP", bridge.getStudentProfile().fullName)
        assertEquals(1, bridge.listDisciplines("2026.2", null).size)
        assertEquals(1, bridge.listCourseSlots(course.courseId).size)
        assertEquals(
            1,
            bridge.getWeeklyTimetable("2026.2").rows
                .flatMap { it.cellsByDay.values.filterNotNull() }
                .count { it.courseId == course.courseId }
        )
        assertTrue(bridge.getAgendaDay("2026-11-24").items.any { it.title == "Monitoria BCC202" })
        assertEquals(1, bridge.listAllAssessments("2026.2", null).size)

        bridge.updateCourseAbsences(course.courseId, 2)
        assertEquals(2, bridge.getDisciplineDetail(course.courseId).summary.currentAbsences)
        assertEquals(2, bridge.listDisciplines("2026.2", null).first().currentAbsences)
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = linkedMapOf<String, Any?>()
        var commitCount: Int = 0
            private set
        var applyCount: Int = 0
            private set

        override fun getAll(): MutableMap<String, *> = LinkedHashMap(data)
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private val removals = linkedSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = values?.toSet()
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) removals.add(key)
            }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }

            private fun flush() {
                if (clearAll) data.clear()
                removals.forEach { data.remove(it) }
                pending.forEach { (k, v) ->
                    if (v == null) data.remove(k) else data[k] = v
                }
            }

            override fun commit(): Boolean {
                flush()
                commitCount += 1
                return true
            }

            override fun apply() {
                flush()
                applyCount += 1
            }
        }
    }
}
