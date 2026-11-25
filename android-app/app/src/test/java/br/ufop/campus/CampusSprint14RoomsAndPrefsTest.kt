package br.ufop.campus

import android.content.SharedPreferences
import br.ufop.campus.BuildConfig
import br.ufop.campus.core.curriculum.UfopRoomAllocationCatalog
import br.ufop.campus.core.curriculum.UfopRoomAllocationIndex
import br.ufop.campus.core.ffi.AssessmentItem
import br.ufop.campus.core.ffi.AssessmentWithCourse
import br.ufop.campus.core.ffi.CampusEngineBridgeRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.core.ffi.InMemoryCampusRepository
import br.ufop.campus.core.security.CampusCryptoEngine
import br.ufop.campus.ui.activities.filterActivityEntries
import br.ufop.campus.ui.activities.formatActivityTypeFilterLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
import javax.crypto.spec.SecretKeySpec

class CampusSprint14RoomsAndPrefsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUpCryptoKey() {
        val syntheticKeyBytes = ByteArray(32) { idx -> (0x6D xor (idx * 11 + 7)).toByte() }
        CampusCryptoEngine.setTestKeyOverrideForJvm(SecretKeySpec(syntheticKeyBytes, "AES"))
    }

    @After
    fun tearDownCryptoKey() {
        CampusCryptoEngine.setTestKeyOverrideForJvm(null)
    }

    private fun locateRoomAllocationsJsonFile(): File {
        val userDir = System.getProperty("user.dir").orEmpty()
        val relativePaths = listOf(
            "src/main/assets/ufop_room_allocations_2026_2.json",
            "app/src/main/assets/ufop_room_allocations_2026_2.json",
            "android-app/app/src/main/assets/ufop_room_allocations_2026_2.json"
        )
        val candidates = buildList {
            relativePaths.forEach { add(File(it)) }
            if (userDir.isNotBlank()) {
                val baseDir = File(userDir)
                relativePaths.forEach { add(File(baseDir, it)) }
            }
        }
        return candidates.firstOrNull { it.isFile }
            ?: throw IllegalStateException(
                "Could not locate ufop_room_allocations_2026_2.json from user.dir='$userDir' / cwd='${File(".").absolutePath}'"
            )
    }

    private fun loadRealRoomAllocationIndex(): UfopRoomAllocationIndex {
        val jsonFile = locateRoomAllocationsJsonFile()
        return UfopRoomAllocationCatalog.loadFromJson(jsonFile.readText(Charsets.UTF_8))
    }

    // ── a) Carga real de ufop_room_allocations_2026_2.json ───────────────────────────────────────

    @Test
    fun roomAllocationAsset_loadsReal2026_2DatasetWithIcebAndBlocoDeSalas() {
        val index = loadRealRoomAllocationIndex()

        assertEquals("2026.2", index.semester)
        assertTrue(
            "Expected at least 1000 room allocation entries, got ${index.entries.size}",
            index.entries.size >= 1000
        )
        assertTrue(index.buildingsCovered.contains("ICEB"))
        assertTrue(index.buildingsCovered.contains("Bloco de Salas"))
    }

    // ── b) Consultas reais via lookup(...) ───────────────────────────────────────────────────────

    @Test
    fun roomAllocationLookup_resolvesRealTurmasAndExcludesEscolaDeMinas() {
        val index = loadRealRoomAllocationIndex()

        val mtm124Seg = index.lookup(courseCode = "MTM124", turma = "64", dayOfWeek = 1, startTime = "15:20")
        assertNotNull(mtm124Seg)
        assertEquals("ICEB", mtm124Seg!!.building)
        assertEquals("Sala 19", mtm124Seg.room)

        val mtm124Qua = index.lookup(courseCode = "MTM124", turma = "64", dayOfWeek = 3, startTime = "15:20")
        assertNotNull(mtm124Qua)
        assertEquals("ICEB", mtm124Qua!!.building)
        assertEquals("Sala 10", mtm124Qua.room)

        val bcc721Ter = index.lookup(courseCode = "BCC721", turma = "11", dayOfWeek = 2, startTime = "15:20")
        assertNotNull(bcc721Ter)
        assertEquals("Bloco de Salas", bcc721Ter!!.building)
        assertEquals("Sala 205", bcc721Ter.room)

        val bcc721Qui = index.lookup(courseCode = "BCC721", turma = "11", dayOfWeek = 4, startTime = "15:20")
        assertNotNull(bcc721Qui)
        assertEquals("Bloco de Salas", bcc721Qui!!.building)
        assertEquals("Sala 106", bcc721Qui.room)

        val mtm125Qua = index.lookup(courseCode = "MTM125", turma = "84", dayOfWeek = 3, startTime = "15:20")
        assertNotNull(mtm125Qua)
        assertEquals("ICEB", mtm125Qua!!.building)
        assertEquals("Sala 6", mtm125Qua.room)

        // Escola de Minas não está na tabela oficial de ICEB/Bloco de Salas
        assertNull(index.lookup(courseCode = "CAT163", turma = "11", dayOfWeek = 1, startTime = "13:30"))
        assertNull(index.lookup(courseCode = "CAT176", turma = "11", dayOfWeek = 2, startTime = "13:30"))
        assertTrue(index.entries.none { it.courseCode == "CAT163" || it.courseCode == "CAT176" })
    }

    // ── c) Importação de Atestado + applyRoomAllocationIndex(index, enrichExisting = true) ───────

    @Test
    fun atestadoImportAndEnrichExisting_assignsRealRoomsKeepsEscolaDeMinasCleanAndPreservesCustomRooms() {
        val index = loadRealRoomAllocationIndex()
        val stateFile = File(tempFolder.root, "sprint14_rooms_state.json")
        val repo = InMemoryCampusRepository(stateFile = stateFile)

        val certText = """
            UNIVERSIDADE FEDERAL DE OURO PRETO
            ATESTADO DE MATRÍCULA 2026/2
            Atestamos que GABRIEL HENRIQUE COSTA matrícula nº 24.1.5089 está regularmente matriculado no curso de CIÊNCIA DA COMPUTAÇÃO, cuja situação é regular no campus Ouro Preto.
            Início das Aulas: 24 de agosto de 2026
            Término das Aulas: 23 de dezembro de 2026

            MTM124 64 ALGEBRA LINEAR ICEB PORTUGUES
            BCC721 11 INTRODUCAO A CIENCIA DE DADOS ICEB PORTUGUES
            CAT163 11 CONTROLE AUTOMATICO ESCOLA DE MINAS PORTUGUES

            Horário          Segunda        Terça          Quarta         Quinta         Sexta
            13:30 - 14:20    CAT163-11P
            14:20 - 15:10    CAT163-11P
            15:20 - 16:10    MTM124-64T     BCC721-11T     MTM124-64T     BCC721-11T
            16:10 - 17:00    MTM124-64T     BCC721-11T     MTM124-64T     BCC721-11T
        """.trimIndent()

        val importedCount = repo.importEnrollmentCertificateText(certText)
        assertEquals(3, importedCount)

        val updatedOnEnrich = repo.applyRoomAllocationIndex(index, enrichExisting = true)
        assertEquals(4, updatedOnEnrich)

        val mtmSlots = repo.listCourseSlots("mtm124_2026_2").sortedBy { it.dayOfWeek }
        assertEquals(2, mtmSlots.size)
        assertEquals(1, mtmSlots[0].dayOfWeek)
        assertEquals("ICEB", mtmSlots[0].building)
        assertEquals("Sala 19", mtmSlots[0].room)
        assertEquals(3, mtmSlots[1].dayOfWeek)
        assertEquals("ICEB", mtmSlots[1].building)
        assertEquals("Sala 10", mtmSlots[1].room)

        val bccSlots = repo.listCourseSlots("bcc721_2026_2").sortedBy { it.dayOfWeek }
        assertEquals(2, bccSlots.size)
        assertEquals(2, bccSlots[0].dayOfWeek)
        assertEquals("Bloco de Salas", bccSlots[0].building)
        assertEquals("Sala 205", bccSlots[0].room)
        assertEquals(4, bccSlots[1].dayOfWeek)
        assertEquals("Bloco de Salas", bccSlots[1].building)
        assertEquals("Sala 106", bccSlots[1].room)

        // CAT163 (Escola de Minas, sem tabela) permanece sem sala e com exibição limpa na Agenda
        val catSlots = repo.listCourseSlots("cat163_2026_2")
        assertEquals(1, catSlots.size)
        assertEquals("Escola de Minas", catSlots[0].building)
        assertEquals("", catSlots[0].room)
        assertEquals("EM", catSlots[0].shortLocation)

        // 2026-08-24 é segunda-feira (início de 2026.2)
        val mondayAgenda = repo.getAgendaDay("2026-08-24")
        val catAgendaItem = mondayAgenda.items.firstOrNull { it.courseId == "cat163_2026_2" }
        assertNotNull(catAgendaItem)
        assertEquals("Escola de Minas", catAgendaItem!!.location)
        assertFalse(catAgendaItem.location.contains("Aula Prática", ignoreCase = true))
        assertFalse(catAgendaItem.location.contains("Sala 101", ignoreCase = true))

        // Reimportação após índice já carregado também aplica salas automaticamente
        val repoWithPreloadedIndex = InMemoryCampusRepository()
        repoWithPreloadedIndex.applyRoomAllocationIndex(index, enrichExisting = false)
        assertEquals(3, repoWithPreloadedIndex.importEnrollmentCertificateText(certText))
        val preloadedMtm = repoWithPreloadedIndex.listCourseSlots("mtm124_2026_2").sortedBy { it.dayOfWeek }
        assertEquals("ICEB", preloadedMtm[0].building)
        assertEquals("Sala 19", preloadedMtm[0].room)
        assertEquals("ICEB", preloadedMtm[1].building)
        assertEquals("Sala 10", preloadedMtm[1].room)

        // Slots legados 2026.2 com "Aula Teórica" / "Aula Prática" são enriquecidos, mas sala manual ("Lab 03") é preservada
        val legacyStateFile = File(tempFolder.root, "sprint14_legacy_slots.json")
        val legacyRepo = InMemoryCampusRepository(stateFile = legacyStateFile)

        val legacyMtm = legacyRepo.createCourse("2026.2", "demat", "MTM124 Álgebra Linear", "Prof. Ana", 60)
        legacyRepo.updateDisciplineNotes(legacyMtm.courseId, "Código: MTM124 · Turma 64 · Português")
        val mtmGenericSlot = legacyRepo.addScheduleSlot(
            courseId = legacyMtm.courseId,
            dayOfWeek = 1,
            startTime = "15:20",
            endTime = "17:00",
            building = "ICEB",
            room = "Aula Teórica",
            shortLocation = "ICEB · Aula Teórica"
        )
        val mtmCustomSlot = legacyRepo.addScheduleSlot(
            courseId = legacyMtm.courseId,
            dayOfWeek = 3,
            startTime = "15:20",
            endTime = "17:00",
            building = "ICEB",
            room = "Lab 03",
            shortLocation = "ICEB · Lab 03"
        )

        val legacyMtm125 = legacyRepo.createCourse("2026.2", "demat", "MTM125 EDO", "Prof. Bruno", 60)
        legacyRepo.updateDisciplineNotes(legacyMtm125.courseId, "Código: MTM125 · Turma 84 · Português")
        val mtm125GenericSlot = legacyRepo.addScheduleSlot(
            courseId = legacyMtm125.courseId,
            dayOfWeek = 5,
            startTime = "15:20",
            endTime = "17:00",
            building = "ICEB",
            room = "Aula Prática",
            shortLocation = "ICEB · Aula Prática"
        )

        val legacyCat = legacyRepo.createCourse("2026.2", "decat", "CAT163 Controle", "Prof. Carla", 60)
        legacyRepo.updateDisciplineNotes(legacyCat.courseId, "Código: CAT163 · Turma 11 · Português")
        val catGenericSlot = legacyRepo.addScheduleSlot(
            courseId = legacyCat.courseId,
            dayOfWeek = 2,
            startTime = "13:30",
            endTime = "15:10",
            building = "Escola de Minas",
            room = "Aula Prática",
            shortLocation = "EM · Aula Prática"
        )

        val enrichedLegacyCount = legacyRepo.applyRoomAllocationIndex(index, enrichExisting = true)
        assertEquals(3, enrichedLegacyCount)

        val afterMtmSlots = legacyRepo.listCourseSlots(legacyMtm.courseId).associateBy { it.slotId }
        assertEquals("ICEB", afterMtmSlots[mtmGenericSlot.slotId]?.building)
        assertEquals("Sala 19", afterMtmSlots[mtmGenericSlot.slotId]?.room)
        assertEquals("ICEB", afterMtmSlots[mtmCustomSlot.slotId]?.building)
        assertEquals("Lab 03", afterMtmSlots[mtmCustomSlot.slotId]?.room)
        assertEquals("ICEB · Lab 03", afterMtmSlots[mtmCustomSlot.slotId]?.shortLocation)

        val afterMtm125Slot = legacyRepo.listCourseSlots(legacyMtm125.courseId).single()
        assertEquals(mtm125GenericSlot.slotId, afterMtm125Slot.slotId)
        assertEquals("ICEB", afterMtm125Slot.building)
        assertEquals("Sala 6", afterMtm125Slot.room)

        val afterCatSlot = legacyRepo.listCourseSlots(legacyCat.courseId).single()
        assertEquals(catGenericSlot.slotId, afterCatSlot.slotId)
        assertEquals("Escola de Minas", afterCatSlot.building)
        assertEquals("", afterCatSlot.room)
        val tuesdayAgenda = legacyRepo.getAgendaDay("2026-08-25")
        val legacyCatItem = tuesdayAgenda.items.firstOrNull { it.courseId == legacyCat.courseId }
        assertNotNull(legacyCatItem)
        assertEquals("Escola de Minas", legacyCatItem!!.location)

        // Persistência após cold restart via Bridge preserva as salas enriquecidas e a sala customizada
        val bridgeReloaded = CampusEngineBridgeRepository(dbPath = null, stateFile = legacyStateFile)
        val reloadedMtmSlots = bridgeReloaded.listCourseSlots(legacyMtm.courseId).associateBy { it.slotId }
        assertEquals("Sala 19", reloadedMtmSlots[mtmGenericSlot.slotId]?.room)
        assertEquals("Lab 03", reloadedMtmSlots[mtmCustomSlot.slotId]?.room)
    }

    @Test
    fun backgroundRoomAllocationEnrichment_emitsReactiveMutationVersionThreadSafely() {
        val index = loadRealRoomAllocationIndex()
        val stateFile = File(tempFolder.root, "sprint14_async_rooms_state.json")
        val bridge = CampusEngineBridgeRepository(dbPath = null, stateFile = stateFile)

        val certText = """
            UNIVERSIDADE FEDERAL DE OURO PRETO
            ATESTADO DE MATRÍCULA 2026/2
            Atestamos que GABRIEL HENRIQUE COSTA matrícula nº 24.1.5089 está regularmente matriculado no curso de CIÊNCIA DA COMPUTAÇÃO, cuja situação é regular no campus Ouro Preto.
            Início das Aulas: 24 de agosto de 2026
            Término das Aulas: 23 de dezembro de 2026

            MTM124 64 ALGEBRA LINEAR ICEB PORTUGUES
            BCC721 11 INTRODUCAO A CIENCIA DE DADOS ICEB PORTUGUES

            Horário          Segunda        Terça          Quarta         Quinta         Sexta
            15:20 - 16:10    MTM124-64T     BCC721-11T     MTM124-64T     BCC721-11T
            16:10 - 17:00    MTM124-64T     BCC721-11T     MTM124-64T     BCC721-11T
        """.trimIndent()

        assertEquals(2, bridge.importEnrollmentCertificateText(certText))
        assertTrue(bridge.listCourseSlots("mtm124_2026_2").all { it.room.isEmpty() })

        val versionBefore = bridge.mutationVersion.value
        val updatedCount = runBlocking(Dispatchers.IO) {
            bridge.applyRoomAllocationIndex(index, enrichExisting = true)
        }
        assertEquals(4, updatedCount)
        assertTrue(
            "mutationVersion must advance after background room enrichment",
            bridge.mutationVersion.value > versionBefore
        )

        val mtmSlots = bridge.listCourseSlots("mtm124_2026_2").sortedBy { it.dayOfWeek }
        assertEquals("ICEB", mtmSlots[0].building)
        assertEquals("Sala 19", mtmSlots[0].room)
        assertEquals("ICEB", mtmSlots[1].building)
        assertEquals("Sala 10", mtmSlots[1].room)

        val coldReloaded = CampusEngineBridgeRepository(dbPath = null, stateFile = stateFile)
        val persistedSlots = coldReloaded.listCourseSlots("mtm124_2026_2").sortedBy { it.dayOfWeek }
        assertEquals("Sala 19", persistedSlots[0].room)
        assertEquals("Sala 10", persistedSlots[1].room)
    }

    // ── d) Helpers de preferência em CampusRepositoryHolder ──────────────────────────────────────

    @Test
    fun repositoryHolderPreferenceHelpers_persistTimetableOrientationAndDisciplinesLayoutMode() {
        assertEquals(BuildConfig.VERSION_NAME, CampusRepositoryHolder.CURRENT_APP_VERSION)
        assertTrue(Regex("""^\d+\.\d+\.\d+$""").matches(CampusRepositoryHolder.CURRENT_APP_VERSION))
        assertTrue(BuildConfig.VERSION_CODE > 0)

        val prefs = InMemorySharedPreferences()

        // Default sem preferência gravada (e também com prefs == null)
        assertTrue(CampusRepositoryHolder.loadTimetableIsLandscapeFromPrefs(null))
        assertTrue(CampusRepositoryHolder.loadTimetableIsLandscapeFromPrefs(prefs))
        assertEquals("GRID", CampusRepositoryHolder.loadDisciplinesLayoutModeFromPrefs(null))
        assertEquals("GRID", CampusRepositoryHolder.loadDisciplinesLayoutModeFromPrefs(prefs))

        // Orientação da grade horária: PORTRAIT / LANDSCAPE + legado boolean
        assertFalse(CampusRepositoryHolder.saveTimetableIsLandscapeToPrefs(prefs, isLandscape = false))
        assertFalse(CampusRepositoryHolder.loadTimetableIsLandscapeFromPrefs(prefs))
        assertEquals("PORTRAIT", prefs.getString(CampusRepositoryHolder.KEY_TIMETABLE_ORIENTATION, null))

        assertTrue(CampusRepositoryHolder.saveTimetableIsLandscapeToPrefs(prefs, isLandscape = true))
        assertTrue(CampusRepositoryHolder.loadTimetableIsLandscapeFromPrefs(prefs))
        assertEquals("LANDSCAPE", prefs.getString(CampusRepositoryHolder.KEY_TIMETABLE_ORIENTATION, null))

        // Compatibilidade com preferência booleana legada
        prefs.edit().putBoolean(CampusRepositoryHolder.KEY_TIMETABLE_ORIENTATION, false).commit()
        assertFalse(CampusRepositoryHolder.loadTimetableIsLandscapeFromPrefs(prefs))

        // Modo de exibição das disciplinas: LIST / GRID + normalização case-insensitive
        assertEquals("LIST", CampusRepositoryHolder.saveDisciplinesLayoutModeToPrefs(prefs, " list "))
        assertEquals("LIST", CampusRepositoryHolder.loadDisciplinesLayoutModeFromPrefs(prefs))
        assertEquals("LIST", prefs.getString(CampusRepositoryHolder.KEY_DISCIPLINES_LAYOUT_MODE, null))

        assertEquals("GRID", CampusRepositoryHolder.saveDisciplinesLayoutModeToPrefs(prefs, "grid"))
        assertEquals("GRID", CampusRepositoryHolder.loadDisciplinesLayoutModeFromPrefs(prefs))

        assertEquals("GRID", CampusRepositoryHolder.saveDisciplinesLayoutModeToPrefs(prefs, "UNKNOWN_VALUE"))
        assertEquals("GRID", CampusRepositoryHolder.loadDisciplinesLayoutModeFromPrefs(prefs))

        // Limpeza local restaura defaults
        CampusRepositoryHolder.clearAllLocalPreferencesFromPrefs(prefs)
        assertTrue(CampusRepositoryHolder.loadTimetableIsLandscapeFromPrefs(prefs))
        assertEquals("GRID", CampusRepositoryHolder.loadDisciplinesLayoutModeFromPrefs(prefs))
    }

    // ── e) Sprint 15: Filtro multi-seleção de tipos/concluídas e rótulo dinâmico em Atividades ────

    @Test
    fun activityTypeFilterAndLabel_handlesEmptySingleMultiCompletedAndCourseCombinations() {
        fun sampleEntry(
            id: String,
            courseId: String,
            courseName: String,
            kind: String,
            completed: Boolean
        ) = AssessmentWithCourse(
            assessment = AssessmentItem(
                id = id,
                courseId = courseId,
                dayNumber = "20",
                monthShort = "NOV",
                title = "Atividade $id",
                kind = kind,
                dueTime = "23:59",
                dueDate = "2026-11-20",
                completed = completed
            ),
            courseName = courseName,
            departmentName = "DECOM",
            colorHex = "#3B6939"
        )

        val e1ProvaPendingMtm = sampleEntry("a1", "mtm124_2026_2", "Álgebra Linear", "Prova · 30 pts", completed = false)
        val e2ProvaCompletedBcc = sampleEntry("a2", "bcc721_2026_2", "Ciência de Dados", "Prova Escrita", completed = true)
        val e3TrabalhoPendingBcc = sampleEntry("a3", "bcc721_2026_2", "Ciência de Dados", "Trabalho Prático", completed = false)
        val e4TrabalhoCompletedMtm = sampleEntry("a4", "mtm124_2026_2", "Álgebra Linear", "Trabalho Final", completed = true)
        val e5SeminarioPendingBcc = sampleEntry("a5", "bcc721_2026_2", "Ciência de Dados", "Seminário", completed = false)
        val entries = listOf(
            e1ProvaPendingMtm,
            e2ProvaCompletedBcc,
            e3TrabalhoPendingBcc,
            e4TrabalhoCompletedMtm,
            e5SeminarioPendingBcc
        )

        // 1. Nenhum filtro (selectedTypes = emptySet(), onlyCompleted = false) -> "Todos os tipos" e retorna tudo
        assertEquals("Todos os tipos", formatActivityTypeFilterLabel(emptySet(), onlyCompleted = false))
        assertEquals(
            entries,
            filterActivityEntries(
                entries = entries,
                selectedTypes = emptySet(),
                onlyCompleted = false,
                selectedCourseId = null
            )
        )

        // 2. 1 tipo (setOf("Prova"), false) -> "Provas" e filtra provas (tbm verifica Trabalhos/Seminários)
        assertEquals("Provas", formatActivityTypeFilterLabel(setOf("Prova"), onlyCompleted = false))
        assertEquals("Trabalhos", formatActivityTypeFilterLabel(setOf("Trabalho"), onlyCompleted = false))
        assertEquals("Seminários", formatActivityTypeFilterLabel(setOf("Seminário"), onlyCompleted = false))
        assertEquals(
            listOf(e1ProvaPendingMtm, e2ProvaCompletedBcc),
            filterActivityEntries(
                entries = entries,
                selectedTypes = setOf("Prova"),
                onlyCompleted = false,
                selectedCourseId = null
            )
        )

        // 3. Múltiplos tipos (setOf("Prova", "Trabalho"), false) -> "2 tipos" e inclui ambos os tipos
        assertEquals("2 tipos", formatActivityTypeFilterLabel(setOf("Prova", "Trabalho"), onlyCompleted = false))
        assertEquals("3 tipos", formatActivityTypeFilterLabel(setOf("Prova", "Trabalho", "Seminário"), onlyCompleted = false))
        assertEquals(
            listOf(e1ProvaPendingMtm, e2ProvaCompletedBcc, e3TrabalhoPendingBcc, e4TrabalhoCompletedMtm),
            filterActivityEntries(
                entries = entries,
                selectedTypes = setOf("Prova", "Trabalho"),
                onlyCompleted = false,
                selectedCourseId = null
            )
        )

        // 4. Somente concluídas (emptySet(), true) -> "Concluídas"
        assertEquals("Concluídas", formatActivityTypeFilterLabel(emptySet(), onlyCompleted = true))
        assertEquals(
            listOf(e2ProvaCompletedBcc, e4TrabalhoCompletedMtm),
            filterActivityEntries(
                entries = entries,
                selectedTypes = emptySet(),
                onlyCompleted = true,
                selectedCourseId = null
            )
        )

        // 5. Tipo(s) + concluídas (setOf("Prova"), true) -> "2 filtros" com interseção correta
        assertEquals("2 filtros", formatActivityTypeFilterLabel(setOf("Prova"), onlyCompleted = true))
        assertEquals("3 filtros", formatActivityTypeFilterLabel(setOf("Prova", "Trabalho"), onlyCompleted = true))
        assertEquals(
            listOf(e2ProvaCompletedBcc),
            filterActivityEntries(
                entries = entries,
                selectedTypes = setOf("Prova"),
                onlyCompleted = true,
                selectedCourseId = null
            )
        )

        // 6. Combinação com filtro de disciplina (selectedCourseId)
        assertEquals(
            listOf(e2ProvaCompletedBcc, e3TrabalhoPendingBcc),
            filterActivityEntries(
                entries = entries,
                selectedTypes = setOf("Prova", "Trabalho"),
                onlyCompleted = false,
                selectedCourseId = "bcc721_2026_2"
            )
        )
        assertEquals(
            listOf(e4TrabalhoCompletedMtm),
            filterActivityEntries(
                entries = entries,
                selectedTypes = setOf("Prova", "Trabalho"),
                onlyCompleted = true,
                selectedCourseId = "mtm124_2026_2"
            )
        )
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = linkedMapOf<String, Any?>()

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
                return true
            }

            override fun apply() {
                flush()
            }
        }
    }
}
