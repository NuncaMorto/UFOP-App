package br.ufop.campus

import android.content.SharedPreferences
import br.ufop.campus.core.curriculum.DynamicCourseNode
import br.ufop.campus.core.curriculum.DynamicCourseStatus
import br.ufop.campus.core.curriculum.DynamicCurriculumCatalog
import br.ufop.campus.core.curriculum.DynamicCurriculumMatrix
import br.ufop.campus.core.curriculum.DynamicGridEngine
import br.ufop.campus.core.curriculum.DynamicGridToggleAction
import br.ufop.campus.core.ffi.CampusEngineBridgeRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.core.ffi.InMemoryCampusRepository
import br.ufop.campus.core.security.CampusCryptoEngine
import org.json.JSONObject
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
import javax.crypto.spec.SecretKeySpec

class DynamicCurriculumGridTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUpCryptoKey() {
        val syntheticKeyBytes = ByteArray(32) { idx -> (0x5A xor (idx * 13 + 5)).toByte() }
        CampusCryptoEngine.setTestKeyOverrideForJvm(SecretKeySpec(syntheticKeyBytes, "AES"))
    }

    @After
    fun tearDownCryptoKey() {
        CampusCryptoEngine.setTestKeyOverrideForJvm(null)
    }

    @Test
    fun catalog_engControleAutomacao2023_hasExactTotalsPrerequisitesAndNoCycles() {
        val matrix = DynamicCurriculumCatalog.engControleAutomacao2024_1
        assertEquals("eng_controle_automacao_2024_1", matrix.id)
        assertEquals("Eng. de Controle e Automação", matrix.courseName)
        assertEquals("2024/1 · Currículo 2", matrix.curriculumLabel)
        assertEquals(listOf("eng_controle_automacao_2023"), matrix.legacyIds)
        assertEquals(10, matrix.totalPeriods)
        assertEquals(640, matrix.requiredActivitiesHours)
        assertEquals(3610, matrix.totalCourseHours)
        assertEquals(54, matrix.courses.size)

        val mandatory = matrix.courses.filter { !it.isElective }
        val electives = matrix.courses.filter { it.isElective }
        assertEquals(49, mandatory.size)
        assertEquals(5, electives.size)
        assertEquals(2670, mandatory.sumOf { it.workloadHours })
        assertEquals(300, electives.sumOf { it.workloadHours })
        assertEquals(2970, matrix.totalGridHours)
        assertEquals(matrix.totalCourseHours, matrix.totalGridHours + matrix.requiredActivitiesHours)

        val byCode = matrix.courses.associateBy { it.code }
        assertEquals(54, byCode.size)
        assertTrue(matrix.courses.all { it.period in 1..10 })

        // Todos os pré-requisitos existem na própria matriz e aparecem em períodos anteriores
        matrix.courses.forEach { course ->
            course.prerequisites.forEach { prereqCode ->
                val prereqNode = byCode[prereqCode]
                assertNotNull("Pré-requisito inexistente: $prereqCode em ${course.code}", prereqNode)
                assertTrue(
                    "Pré-requisito $prereqCode deveria estar em período anterior ao de ${course.code}",
                    prereqNode!!.period < course.period
                )
            }
        }

        // Verificação explícita de ausência de ciclos via busca em profundidade
        val visitState = mutableMapOf<String, Int>() // 0=unvisited, 1=visiting, 2=visited
        fun hasCycle(code: String): Boolean {
            when (visitState[code] ?: 0) {
                1 -> return true
                2 -> return false
            }
            visitState[code] = 1
            val node = byCode[code] ?: return false
            for (prereq in node.prerequisites) {
                if (hasCycle(prereq)) return true
            }
            visitState[code] = 2
            return false
        }
        assertFalse(matrix.courses.any { hasCycle(it.code) })

        assertEquals(matrix, DynamicCurriculumCatalog.defaultMatrix)
        assertEquals(matrix, DynamicCurriculumCatalog.findById("eng_controle_automacao_2023"))
        assertEquals(matrix, DynamicCurriculumCatalog.findById("eng_controle_automacao_2024_1"))
    }

    @Test
    fun catalog_engControleAutomacao2026_1_hasExactTotalsPrerequisitesAndNoCycles() {
        val matrix = DynamicCurriculumCatalog.engControleAutomacao2026_1
        assertEquals("eng_controle_automacao_2026_1", matrix.id)
        assertEquals("Eng. de Controle e Automação", matrix.courseName)
        assertEquals("2026/1 · Currículo 3", matrix.curriculumLabel)
        assertEquals(10, matrix.totalPeriods)
        assertEquals(510, matrix.requiredActivitiesHours)
        assertEquals(3390, matrix.totalCourseHours)
        assertEquals(55, matrix.courses.size)

        val mandatory = matrix.courses.filter { !it.isElective }
        val electives = matrix.courses.filter { it.isElective }
        assertEquals(52, mandatory.size)
        assertEquals(3, electives.size)
        assertEquals(2700, mandatory.sumOf { it.workloadHours })
        assertEquals(180, electives.sumOf { it.workloadHours })
        assertEquals(2880, matrix.totalGridHours)
        assertEquals(matrix.totalCourseHours, matrix.totalGridHours + matrix.requiredActivitiesHours)

        val byCode = matrix.courses.associateBy { it.code }
        assertEquals(55, byCode.size)
        matrix.courses.forEach { course ->
            course.prerequisites.forEach { prereq ->
                assertTrue("Prerequisite $prereq of ${course.code} must exist in matrix", byCode.containsKey(prereq))
                val prereqNode = byCode.getValue(prereq)
                assertTrue(
                    "Prerequisite $prereq (${prereqNode.period}) must precede ${course.code} (${course.period})",
                    prereqNode.period < course.period
                )
            }
        }

        val cat425 = byCode.getValue("CAT425")
        assertEquals(2000, cat425.minHoursRequired)
        val cat491 = byCode.getValue("CAT491")
        assertEquals(listOf("CAT425"), cat491.prerequisites)

        val visited = HashMap<String, Int>()
        fun hasCycle(code: String): Boolean {
            when (visited[code]) {
                1 -> return true
                2 -> return false
            }
            visited[code] = 1
            val node = byCode.getValue(code)
            if (node.prerequisites.any { hasCycle(it) }) return true
            visited[code] = 2
            return false
        }
        assertFalse(matrix.courses.any { hasCycle(it.code) })
        assertEquals(matrix, DynamicCurriculumCatalog.findById("eng_controle_automacao_2026_1"))
    }

    @Test
    fun resolveMatrixForStudent_selectsApplicableVersionByEntrySemester() {
        val r24 = DynamicCurriculumCatalog.resolveMatrixForStudent(
            courseName = "Engenharia de Controle e Automação",
            registrationNumber = "24.1.4001",
            earliestTranscriptSemester = null
        )
        assertEquals("eng_controle_automacao_2024_1", r24.matrix.id)
        assertTrue(r24.isCourseMatched)
        assertTrue(r24.isExactVersionMatch)

        val r25 = DynamicCurriculumCatalog.resolveMatrixForStudent(
            courseName = "AUT",
            registrationNumber = "25.2.9999",
            earliestTranscriptSemester = null
        )
        assertEquals("eng_controle_automacao_2024_1", r25.matrix.id)
        assertTrue(r25.isExactVersionMatch)

        val r26 = DynamicCurriculumCatalog.resolveMatrixForStudent(
            courseName = "Eng. de Controle e Automação",
            registrationNumber = "26.1.1234",
            earliestTranscriptSemester = null
        )
        assertEquals("eng_controle_automacao_2026_1", r26.matrix.id)
        assertTrue(r26.isCourseMatched)
        assertTrue(r26.isExactVersionMatch)

        val rTx24 = DynamicCurriculumCatalog.resolveMatrixForStudent(
            courseName = "Engenharia de Controle e Automação",
            registrationNumber = "",
            earliestTranscriptSemester = "2024.1"
        )
        assertEquals("eng_controle_automacao_2024_1", rTx24.matrix.id)
        assertTrue(rTx24.isExactVersionMatch)

        val rLegacy = DynamicCurriculumCatalog.resolveMatrixForStudent(
            courseName = "Engenharia de Controle e Automação",
            registrationNumber = "22.2.1000",
            earliestTranscriptSemester = null
        )
        assertEquals("eng_controle_automacao_2024_1", rLegacy.matrix.id)
        assertTrue(rLegacy.isCourseMatched)
        assertFalse(rLegacy.isExactVersionMatch)
        assertNotNull(rLegacy.note)
    }

    @Test
    fun loadCompletedCodesWithLegacyFallback_migratesProgressFrom2023Key() {
        val matrix = DynamicCurriculumCatalog.engControleAutomacao2024_1
        val legacyOnlyStore = mapOf(
            "eng_controle_automacao_2023" to setOf("BCC701", "MTM122")
        )
        assertEquals(
            setOf("BCC701", "MTM122"),
            DynamicCurriculumCatalog.loadCompletedCodesWithLegacyFallback(matrix) { legacyOnlyStore[it] ?: emptySet() }
        )

        val migratedStore = mapOf(
            "eng_controle_automacao_2024_1" to setOf("BCC701", "BCC702"),
            "eng_controle_automacao_2023" to setOf("MTM122")
        )
        assertEquals(
            setOf("BCC701", "BCC702"),
            DynamicCurriculumCatalog.loadCompletedCodesWithLegacyFallback(matrix) { migratedStore[it] ?: emptySet() }
        )
    }

    @Test
    fun progressionFlow_unlocksPeriodsAndTccSequentially() {
        val matrix = DynamicCurriculumCatalog.engControleAutomacao2023
        var completed = emptySet<String>()

        // Estado inicial: BCC702 bloqueada, BCC701 e CAT490 disponíveis, CAT491 bloqueada
        assertEquals(DynamicCourseStatus.AVAILABLE, DynamicGridEngine.statusOf(matrix, "BCC701", completed))
        assertEquals(DynamicCourseStatus.LOCKED, DynamicGridEngine.statusOf(matrix, "BCC702", completed))
        assertEquals(DynamicCourseStatus.AVAILABLE, DynamicGridEngine.statusOf(matrix, "CAT490", completed))
        assertEquals(DynamicCourseStatus.LOCKED, DynamicGridEngine.statusOf(matrix, "CAT491", completed))

        // BCC701 -> libera BCC702
        val bcc701Toggle = DynamicGridEngine.toggleCourse(matrix, "BCC701", completed)
        assertEquals(DynamicGridToggleAction.MARKED_COMPLETED, bcc701Toggle.action)
        completed = bcc701Toggle.updatedCompletedCodes
        assertEquals(DynamicCourseStatus.COMPLETED, DynamicGridEngine.statusOf(matrix, "BCC701", completed))
        assertEquals(DynamicCourseStatus.AVAILABLE, DynamicGridEngine.statusOf(matrix, "BCC702", completed))

        // Conclui restante do 1º período -> 300h, 10%, libera FIS106 e MTM123
        listOf("CSO117", "MTM122", "MTM730", "QUI701").forEach { code ->
            val res = DynamicGridEngine.toggleCourse(matrix, code, completed)
            assertEquals(DynamicGridToggleAction.MARKED_COMPLETED, res.action)
            completed = res.updatedCompletedCodes
        }
        val summaryP1 = DynamicGridEngine.summarize(matrix, completed)
        assertEquals(300, summaryP1.completedHours)
        assertEquals(2970, summaryP1.totalGridHours)
        assertEquals(10, summaryP1.completionPercent)
        assertEquals(5, summaryP1.completedCount)
        assertEquals(DynamicCourseStatus.AVAILABLE, DynamicGridEngine.statusOf(matrix, "FIS106", completed))
        assertEquals(DynamicCourseStatus.AVAILABLE, DynamicGridEngine.statusOf(matrix, "MTM123", completed))

        // Conclui 2º período -> 600h, 20%, libera FIS107, FIS108, FIS109, MTM124, MTM125, BCC720, BCC740
        listOf("ARQ205", "BCC702", "EST202", "FIS106", "MTM123").forEach { code ->
            val res = DynamicGridEngine.toggleCourse(matrix, code, completed)
            assertEquals(DynamicGridToggleAction.MARKED_COMPLETED, res.action)
            completed = res.updatedCompletedCodes
        }
        val summaryP1P2 = DynamicGridEngine.summarize(matrix, completed)
        assertEquals(600, summaryP1P2.completedHours)
        assertEquals(20, summaryP1P2.completionPercent)
        assertEquals(10, summaryP1P2.completedCount)
        listOf("FIS107", "FIS108", "FIS109", "MTM124", "MTM125", "BCC720", "BCC740").forEach { unlocked ->
            assertEquals(
                "$unlocked deveria estar disponível após 1º e 2º períodos",
                DynamicCourseStatus.AVAILABLE,
                DynamicGridEngine.statusOf(matrix, unlocked, completed)
            )
        }

        // CAT490 -> libera CAT491
        val cat490Toggle = DynamicGridEngine.toggleCourse(matrix, "CAT490", completed)
        assertEquals(DynamicGridToggleAction.MARKED_COMPLETED, cat490Toggle.action)
        completed = cat490Toggle.updatedCompletedCodes
        assertEquals(DynamicCourseStatus.AVAILABLE, DynamicGridEngine.statusOf(matrix, "CAT491", completed))
    }

    @Test
    fun clickingBlockedCourse_returnsExactMissingPrerequisitesAndLeavesSetUnchanged() {
        val matrix = DynamicCurriculumCatalog.engControleAutomacao2023

        // BCC702 sem BCC701
        val blockedBcc702 = DynamicGridEngine.toggleCourse(matrix, "BCC702", emptySet())
        assertEquals(DynamicGridToggleAction.BLOCKED_BY_PREREQUISITES, blockedBcc702.action)
        assertEquals("BCC702", blockedBcc702.affectedCode)
        assertEquals(listOf("BCC701"), blockedBcc702.missingPrerequisites)
        assertEquals(emptySet<String>(), blockedBcc702.updatedCompletedCodes)

        // FIS106 com MTM122 concluída, mas sem MTM730
        val currentWithMtm122 = setOf("MTM122")
        val blockedFis106 = DynamicGridEngine.toggleCourse(matrix, "FIS106", currentWithMtm122)
        assertEquals(DynamicGridToggleAction.BLOCKED_BY_PREREQUISITES, blockedFis106.action)
        assertEquals("FIS106", blockedFis106.affectedCode)
        assertEquals(listOf("MTM730"), blockedFis106.missingPrerequisites)
        assertEquals(currentWithMtm122, blockedFis106.updatedCompletedCodes)
    }

    @Test
    fun unmarkingRootCourse_cascadesTransitivelyWithoutRemovingIndependentCourses() {
        val matrix = DynamicCurriculumCatalog.engControleAutomacao2023
        var completed = emptySet<String>()

        // Marca cadeia BCC701 -> BCC702 -> BCC720 -> BCC721 -> BCC425 -> BCC722 + BCC740 + BCC760 + BCC342
        listOf("BCC701", "BCC702", "BCC720", "BCC721", "BCC425", "BCC722", "BCC740", "BCC760", "BCC342").forEach { code ->
            val step = DynamicGridEngine.toggleCourse(matrix, code, completed)
            assertEquals("Falha ao marcar $code", DynamicGridToggleAction.MARKED_COMPLETED, step.action)
            completed = step.updatedCompletedCodes
        }
        assertEquals(9, completed.size)

        // Desmarca BCC701: deve remover BCC701 + 7 dependentes transitivos e preservar BCC760
        val unmarkResult = DynamicGridEngine.toggleCourse(matrix, "BCC701", completed)
        assertEquals(DynamicGridToggleAction.UNMARKED_WITH_CASCADE, unmarkResult.action)
        assertEquals("BCC701", unmarkResult.affectedCode)
        assertEquals(
            listOf("BCC342", "BCC425", "BCC702", "BCC720", "BCC721", "BCC722", "BCC740"),
            unmarkResult.cascadeRemovedCodes
        )
        assertEquals(setOf("BCC760"), unmarkResult.updatedCompletedCodes)
    }

    @Test
    fun sanitizeCompleted_respectsMinHoursRequiredWithoutSelfBootstrap() {
        val customMatrix = DynamicCurriculumMatrix(
            id = "custom_min_hours",
            courseName = "Teste Horas Mínimas",
            curriculumLabel = "Teste",
            totalPeriods = 2,
            requiredActivitiesHours = 0,
            totalCourseHours = 120,
            courses = listOf(
                DynamicCourseNode("BAS101", "Base", 60, 1),
                DynamicCourseNode("ADV201", "Avançada", 60, 2, minHoursRequired = 60)
            )
        )

        // Sem BAS101, ADV201 (60h) não pode satisfazer seu próprio requisito de 60h
        assertEquals(emptySet<String>(), DynamicGridEngine.sanitizeCompleted(customMatrix, setOf("ADV201")))
        val blocked = DynamicGridEngine.toggleCourse(customMatrix, "ADV201", emptySet())
        assertEquals(DynamicGridToggleAction.BLOCKED_BY_PREREQUISITES, blocked.action)
        assertEquals(60, blocked.missingHours)

        // Com BAS101 concluída (60h), ADV201 desbloqueia
        assertEquals(
            setOf("BAS101", "ADV201"),
            DynamicGridEngine.sanitizeCompleted(customMatrix, setOf("BAS101", "ADV201"))
        )
    }

    @Test
    fun repositoryPersistenceBackupCompatSnapshotAndLogoutWipe_workEndToEnd() {
        val stateFile = File(tempFolder.root, "dynamic_grid_state.json")
        val matrixId = DynamicCurriculumCatalog.engControleAutomacao2023.id
        val savedCodes = setOf("bcc701", "mtm122", "cat490")

        val repo1 = InMemoryCampusRepository(stateFile = stateFile)
        repo1.saveDynamicGridCompletedCodes(matrixId, savedCodes)
        assertEquals(setOf("BCC701", "MTM122", "CAT490"), repo1.getDynamicGridCompletedCodes(matrixId))
        assertEquals(mapOf(matrixId to setOf("BCC701", "MTM122", "CAT490")), repo1.getDynamicGridAllCompleted())

        // Arquivo criptografado no disco (UFOPENC1:) e preservado após cold restart
        val rawPersisted = stateFile.readText(Charsets.UTF_8)
        assertTrue(rawPersisted.startsWith(CampusCryptoEngine.STATE_PREFIX))
        val repo2 = InMemoryCampusRepository(stateFile = stateFile)
        assertEquals(setOf("BCC701", "MTM122", "CAT490"), repo2.getDynamicGridCompletedCodes(matrixId))

        // Export & Restore de snapshot completo preserva a grade dinâmica
        val snapshotJson = repo2.exportFullAccountSnapshotJson()
        val parsedSnapshot = JSONObject(snapshotJson)
        assertTrue(parsedSnapshot.has("dynamicGridCompletedByMatrix"))

        val repoFromSnapshot = InMemoryCampusRepository()
        assertTrue(repoFromSnapshot.restoreFullAccountSnapshotJson(snapshotJson))
        assertEquals(setOf("BCC701", "MTM122", "CAT490"), repoFromSnapshot.getDynamicGridCompletedCodes(matrixId))

        // Retrocompatibilidade com backup legado v1.9.7 sem a chave "dynamicGridCompletedByMatrix"
        val legacyFile = File(tempFolder.root, "legacy_v197_state.json")
        legacyFile.writeText(
            """
            {
              "schemaVersion": 5,
              "activeCampusId": "op_cruzeiro",
              "studentProfile": {
                "fullName": "Estudante Legado",
                "activeSemester": "2026.2",
                "onboardingCompleted": true
              },
              "courses": []
            }
            """.trimIndent(),
            Charsets.UTF_8
        )
        val legacyRepo = InMemoryCampusRepository(stateFile = legacyFile)
        assertEquals(emptySet<String>(), legacyRepo.getDynamicGridCompletedCodes(matrixId))
        assertTrue(legacyRepo.getDynamicGridAllCompleted().isEmpty())

        // Bridge repository delega e persiste corretamente
        val bridgeStateFile = File(tempFolder.root, "bridge_dynamic_grid_state.json")
        val bridge = CampusEngineBridgeRepository(dbPath = null, stateFile = bridgeStateFile)
        bridge.saveDynamicGridCompletedCodes(matrixId, setOf("BCC701", "BCC702"))
        assertEquals(setOf("BCC701", "BCC702"), bridge.getDynamicGridCompletedCodes(matrixId))
        assertEquals(mapOf(matrixId to setOf("BCC701", "BCC702")), bridge.getDynamicGridAllCompleted())

        // wipeAllUserDataForLogout limpa a grade dinâmica
        repo2.wipeAllUserDataForLogout()
        assertTrue(repo2.getDynamicGridCompletedCodes(matrixId).isEmpty())
        assertTrue(repo2.getDynamicGridAllCompleted().isEmpty())
    }

    @Test
    fun orientationPreferences_persistHorizontalDefaultAndClearOnLogout() {
        val prefs = InMemorySharedPreferences()

        assertTrue(CampusRepositoryHolder.loadDynamicGridIsHorizontalFromPrefs(null))
        assertTrue(CampusRepositoryHolder.loadDynamicGridIsHorizontalFromPrefs(prefs))

        assertFalse(CampusRepositoryHolder.saveDynamicGridIsHorizontalToPrefs(prefs, isHorizontal = false))
        assertFalse(CampusRepositoryHolder.loadDynamicGridIsHorizontalFromPrefs(prefs))
        assertEquals("VERTICAL", prefs.getString(CampusRepositoryHolder.KEY_DYNAMIC_GRID_ORIENTATION, null))

        assertTrue(CampusRepositoryHolder.saveDynamicGridIsHorizontalToPrefs(prefs, isHorizontal = true))
        assertTrue(CampusRepositoryHolder.loadDynamicGridIsHorizontalFromPrefs(prefs))
        assertEquals("HORIZONTAL", prefs.getString(CampusRepositoryHolder.KEY_DYNAMIC_GRID_ORIENTATION, null))

        CampusRepositoryHolder.clearAllLocalPreferencesFromPrefs(prefs)
        assertTrue(CampusRepositoryHolder.loadDynamicGridIsHorizontalFromPrefs(prefs))
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
