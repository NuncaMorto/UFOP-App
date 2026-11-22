package br.ufop.campus.core.curriculum

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class UfopRoomAllocationEntry(
    val courseCode: String,
    val turmas: List<String>,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val building: String,
    val room: String,
    val kind: String = ""
)

class UfopRoomAllocationIndex(
    val semester: String,
    val buildingsCovered: Set<String>,
    val entries: List<UfopRoomAllocationEntry>
) {
    private val byCourseDayStart: Map<String, List<UfopRoomAllocationEntry>> =
        entries.groupBy { entry ->
            buildKey(entry.courseCode, entry.dayOfWeek, entry.startTime)
        }

    fun lookup(
        courseCode: String,
        turma: String?,
        dayOfWeek: Int,
        startTime: String,
        kind: String? = null
    ): UfopRoomAllocationEntry? {
        val key = buildKey(courseCode, dayOfWeek, startTime)
        val candidates = byCourseDayStart[key] ?: return null
        if (candidates.isEmpty()) return null

        val normTurma = normalizeTurma(turma)
        val normKind = normalizeKind(kind)

        // Prioridade 1: turma normalizada + kind igual
        if (normTurma.isNotEmpty() && normKind.isNotEmpty()) {
            candidates.firstOrNull { entry ->
                entry.turmas.any { normalizeTurma(it) == normTurma } &&
                    normalizeKind(entry.kind) == normKind
            }?.let { return it }
        }

        // Prioridade 2: turma normalizada igual
        if (normTurma.isNotEmpty()) {
            candidates.firstOrNull { entry ->
                entry.turmas.any { normalizeTurma(it) == normTurma }
            }?.let { return it }
        }

        // Prioridade 3: entrada sem turma específica (turmas.isEmpty())
        val untypedCandidates = candidates.filter { it.turmas.isEmpty() }
        if (untypedCandidates.isNotEmpty()) {
            if (normKind.isNotEmpty()) {
                untypedCandidates.firstOrNull { normalizeKind(it.kind) == normKind }?.let { return it }
            }
            return untypedCandidates.first()
        }

        // Prioridade 4: se turma vier em branco e todas as candidatas no horário forem da mesma (building, room)
        if (normTurma.isEmpty()) {
            if (normKind.isNotEmpty()) {
                val kindFiltered = candidates.filter { normalizeKind(it.kind) == normKind }
                if (kindFiltered.isNotEmpty() &&
                    kindFiltered.map { it.building to it.room }.distinct().size == 1
                ) {
                    return kindFiltered.first()
                }
            }
            val distinctRooms = candidates.map { it.building to it.room }.distinct()
            if (distinctRooms.size == 1) {
                return candidates.first()
            }
        }

        return null
    }

    private companion object {
        fun buildKey(courseCode: String, dayOfWeek: Int, startTime: String): String =
            "${courseCode.trim().uppercase()}|$dayOfWeek|${startTime.trim()}"

        fun normalizeTurma(raw: String?): String {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return ""
            return trimmed.trimStart('0').ifEmpty { "0" }.uppercase()
        }

        fun normalizeKind(raw: String?): String {
            val upper = raw?.trim()?.uppercase().orEmpty()
            return if (upper == "T" || upper == "P") upper else ""
        }
    }
}

object UfopRoomAllocationCatalog {

    const val ASSET_FILE_NAME = "ufop_room_allocations_2026_2.json"

    private val loadLock = Any()

    @Volatile
    private var cachedIndex: UfopRoomAllocationIndex? = null

    fun loadFromAssets(context: Context): UfopRoomAllocationIndex {
        cachedIndex?.let { return it }
        return synchronized(loadLock) {
            cachedIndex?.let { return@synchronized it }
            val rawJson = runCatching {
                context.assets.open(ASSET_FILE_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrDefault("")
            val parsed = loadFromJson(rawJson)
            cachedIndex = parsed
            parsed
        }
    }

    fun loadFromJson(jsonText: String): UfopRoomAllocationIndex {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty()) {
            return UfopRoomAllocationIndex(
                semester = "2026.2",
                buildingsCovered = emptySet(),
                entries = emptyList()
            )
        }
        return runCatching {
            val root = JSONObject(trimmed)
            val semester = root.optString("semester", "2026.2").trim().ifEmpty { "2026.2" }
            val buildingsArr = root.optJSONArray("buildingsCovered") ?: JSONArray()
            val buildingsCovered = LinkedHashSet<String>(buildingsArr.length())
            for (i in 0 until buildingsArr.length()) {
                val b = buildingsArr.optString(i, "").trim()
                if (b.isNotEmpty()) buildingsCovered.add(b)
            }

            val allocArr = root.optJSONArray("allocations") ?: JSONArray()
            val entries = ArrayList<UfopRoomAllocationEntry>(allocArr.length())
            for (i in 0 until allocArr.length()) {
                val obj = allocArr.optJSONObject(i) ?: continue
                val code = obj.optString("c", obj.optString("courseCode", "")).trim().uppercase()
                val day = obj.optInt("d", obj.optInt("dayOfWeek", 0))
                val start = obj.optString("s", obj.optString("startTime", "")).trim()
                val end = obj.optString("e", obj.optString("endTime", "")).trim()
                val building = obj.optString("b", obj.optString("building", "")).trim()
                val room = obj.optString("r", obj.optString("room", "")).trim()
                val kind = obj.optString("k", obj.optString("kind", "")).trim().uppercase()
                if (code.isEmpty() || day !in 1..7 || start.isEmpty() || building.isEmpty() || room.isEmpty()) {
                    continue
                }
                val turmasArr = obj.optJSONArray("t") ?: obj.optJSONArray("turmas")
                val turmas = if (turmasArr != null && turmasArr.length() > 0) {
                    ArrayList<String>(turmasArr.length()).apply {
                        for (j in 0 until turmasArr.length()) {
                            val t = turmasArr.optString(j, "").trim()
                            if (t.isNotEmpty()) {
                                add(t.trimStart('0').ifEmpty { "0" })
                            }
                        }
                    }
                } else {
                    emptyList()
                }
                entries.add(
                    UfopRoomAllocationEntry(
                        courseCode = code,
                        turmas = turmas,
                        dayOfWeek = day,
                        startTime = start,
                        endTime = end,
                        building = building,
                        room = room,
                        kind = kind
                    )
                )
            }
            UfopRoomAllocationIndex(
                semester = semester,
                buildingsCovered = buildingsCovered,
                entries = entries
            )
        }.getOrElse {
            UfopRoomAllocationIndex(
                semester = "2026.2",
                buildingsCovered = emptySet(),
                entries = emptyList()
            )
        }
    }
}
