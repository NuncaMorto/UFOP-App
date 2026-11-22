package br.ufop.campus.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Binder
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import br.ufop.campus.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * RemoteViewsService que alimenta a lista rolável do widget (fallback API 26–30)
 * com os horários e compromissos de hoje.
 * Lê diretamente do arquivo `campus_ufop_state.json` (com fallback para `.bak`) via JSONObject.
 */
class VidaAcademicaWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return VidaAcademicaRemoteViewsFactory(applicationContext)
    }
}

internal data class WidgetScheduleItem(
    val stableId: Long,
    val title: String,
    val subtitle: String,
    val time: String,
    val startTime: String,
    val colorHex: String
)

internal data class WidgetScheduleSnapshot(
    val items: List<WidgetScheduleItem>,
    val classesCount: Int,
    val commitmentsCount: Int,
    val activeSemester: String
)

internal data class WidgetAccentPalette(
    val headerSubtitleColor: Int,
    val itemTimeColor: Int
)

internal object WidgetAccentThemeHelper {
    private const val PREFS_NAME = "vida_academica_prefs"
    private const val KEY_ACCENT_COLOR = "accent_color_key"

    fun resolvePalette(context: Context): WidgetAccentPalette {
        val rawKey = runCatching {
            (context.applicationContext ?: context)
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ACCENT_COLOR, null)
        }.getOrNull()?.trim()?.uppercase()

        return when (rawKey) {
            "RED" -> WidgetAccentPalette(
                headerSubtitleColor = Color.parseColor("#F2B8B5"),
                itemTimeColor = Color.parseColor("#FFDAD6")
            )
            "ORANGE" -> WidgetAccentPalette(
                headerSubtitleColor = Color.parseColor("#F6BD87"),
                itemTimeColor = Color.parseColor("#FFDCBE")
            )
            "PURPLE" -> WidgetAccentPalette(
                headerSubtitleColor = Color.parseColor("#D0BCFF"),
                itemTimeColor = Color.parseColor("#E9DDFF")
            )
            "YELLOW" -> WidgetAccentPalette(
                headerSubtitleColor = Color.parseColor("#FFE082"),
                itemTimeColor = Color.parseColor("#FFF59D")
            )
            "CYAN" -> WidgetAccentPalette(
                headerSubtitleColor = Color.parseColor("#80DEEA"),
                itemTimeColor = Color.parseColor("#B2EBF2")
            )
            "BROWN" -> WidgetAccentPalette(
                headerSubtitleColor = Color.parseColor("#D7CCC8"),
                itemTimeColor = Color.parseColor("#EFEBE9")
            )
            else -> WidgetAccentPalette(
                headerSubtitleColor = Color.parseColor("#A0B89C"),
                itemTimeColor = Color.parseColor("#CBE6B1")
            )
        }
    }
}

internal object WidgetScheduleItemRenderer {
    private val DEFAULT_BAR_COLOR: Int = Color.parseColor("#5E8BFF")

    fun buildItemRemoteViews(
        context: Context,
        item: WidgetScheduleItem,
        timeAccentColor: Int
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_schedule_item)
        views.setTextViewText(R.id.item_title, item.title)
        views.setTextViewText(R.id.item_subtitle, item.subtitle)
        views.setTextViewText(R.id.item_time, item.time)
        views.setTextColor(R.id.item_time, timeAccentColor)

        val parsedColor = try {
            Color.parseColor(item.colorHex)
        } catch (_: Throwable) {
            DEFAULT_BAR_COLOR
        }
        views.setInt(R.id.item_accent_bar, "setColorFilter", parsedColor)

        // Fill-in Intent para abrir a aplicação ao tocar no compromisso
        views.setOnClickFillInIntent(R.id.widget_item_container, Intent())
        return views
    }
}

private data class CourseMeta(
    val id: String,
    val name: String,
    val professor: String,
    val colorHex: String,
    val buildingAndRoom: String,
    val shortLocation: String,
    val semester: String
)

/**
 * Leitor compartilhado do estado persistido para o Service e para o AppWidgetProvider.
 */
internal object WidgetScheduleLoader {

    private const val DEFAULT_COURSE_COLOR = "#5E8BFF"
    private const val DEFAULT_COMMITMENT_COLOR = "#E9B44C"
    private const val CANCELLED_ACCENT_COLOR = "#6B7280"

    /** Tabela oficial de cores dos 48 departamentos UFOP (sincronizada com CampusContracts.kt). */
    private val UFOP_DEPARTMENT_COLORS: Map<String, String> = mapOf(
        "deabi" to "#C2410C",
        "deacl" to "#0284C7",
        "deali" to "#16A34A",
        "deamb" to "#7C3AED",
        "dearq" to "#DB2777",
        "deart" to "#D97706",
        "debio" to "#14B8A6",
        "decad" to "#4338CA",
        "decat" to "#2E7D32",
        "decbi" to "#E11D48",
        "decea" to "#2563EB",
        "decgp" to "#9333EA",
        "deciv" to "#B45309",
        "decom" to "#6E56CF",
        "decpa" to "#0891B2",
        "decsi" to "#047857",
        "decso" to "#DC2626",
        "dedir" to "#1D4ED8",
        "deeco" to "#CA8A04",
        "deedu" to "#BE185D",
        "deefd" to "#15803D",
        "deelt" to "#EA580C",
        "deema" to "#4F46E5",
        "deenp" to "#0F766E",
        "deest" to "#8B5CF6",
        "deete" to "#0369A1",
        "defar" to "#65A30D",
        "defil" to "#A21CAF",
        "defis" to "#8D5B16",
        "degeo" to "#9F1239",
        "degep" to "#06B6D4",
        "dehis" to "#A16207",
        "deish" to "#1E3A8A",
        "dejor" to "#F97316",
        "delet" to "#6D28D9",
        "demat" to "#3B68FF",
        "demec" to "#475569",
        "demet" to "#B91C1C",
        "demin" to "#00897B",
        "demsc" to "#C026D3",
        "demul" to "#7C2D12",
        "demus" to "#38BDF8",
        "dencs" to "#4D7C0F",
        "depro" to "#5B21B6",
        "dequi" to "#EC4899",
        "desso" to "#006064",
        "detur" to "#F59E0B",
        "deurb" to "#166534"
    )

    private val LEGACY_DEPARTMENT_ALIASES: Map<String, String> = mapOf(
        "math" to "demat",
        "comp" to "decom",
        "cat" to "decat",
        "geom" to "decat",
        "phys" to "defis",
        "em" to "demin",
        "deedf" to "deefd",
        "eedf" to "deefd",
        "deped" to "deedu",
        "ped" to "deedu",
        "denut" to "dencs",
        "nut" to "dencs",
        "demed" to "demsc",
        "med" to "demsc",
        "deadm" to "decad",
        "adm" to "decad"
    )

    fun loadTodaySchedule(
        context: Context,
        today: LocalDate = LocalDate.now()
    ): WidgetScheduleSnapshot {
        val loadedItems = mutableListOf<WidgetScheduleItem>()
        val usedStableIds = HashSet<Long>()
        var classesCount = 0
        var commitmentsCount = 0
        var resolvedSemester = "2026.2"

        try {
            val root = readStateJson(context) ?: return WidgetScheduleSnapshot(
                items = emptyList(),
                classesCount = 0,
                commitmentsCount = 0,
                activeSemester = resolvedSemester
            )

            val coursesJson = root.optJSONArray("courses") ?: JSONArray()
            val firstCourseSemester = findFirstNonBlankSemester(coursesJson)

            val profileJson = root.optJSONObject("studentProfile")
            val activeSemester = profileJson?.optNonNullString("activeSemester")
                ?.trim()
                ?.ifBlank { firstCourseSemester ?: "2026.2" }
                ?: (firstCourseSemester ?: "2026.2")
            resolvedSemester = activeSemester

            val deptColorOverrides = mutableMapOf<String, String>()
            root.optJSONObject("departmentColorOverrides")?.let { overridesObj ->
                val keys = overridesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val hex = overridesObj.optNonNullString(key).trim()
                    if (hex.isNotEmpty()) {
                        deptColorOverrides[key.lowercase()] = hex
                    }
                }
            }

            // Mapeia apenas disciplinas do semestre ativo
            val activeCourses = mutableMapOf<String, CourseMeta>()
            for (i in 0 until coursesJson.length()) {
                val c = coursesJson.optJSONObject(i) ?: continue
                val id = c.optNonNullString("courseId")
                    .ifBlank { c.optNonNullString("id") }
                    .trim()
                if (id.isEmpty()) continue

                val sem = c.optNonNullString("semester").trim()
                if (sem.isNotEmpty() && sem != activeSemester) continue

                val name = c.optNonNullString("name").trim().ifBlank { id }
                val prof = c.optNonNullString("professor").trim()
                val rawDeptId = c.optNonNullString("departmentId").trim()
                val buildingAndRoom = c.optNonNullString("buildingAndRoom").trim()
                val shortLocation = c.optNonNullString("shortLocation").trim()
                val color = resolveCourseColor(
                    explicitOverride = c.optNonNullString("colorOverrideHex"),
                    defaultColor = c.optNonNullString("defaultColorHex"),
                    departmentId = rawDeptId,
                    deptColorOverrides = deptColorOverrides
                )

                activeCourses[id] = CourseMeta(
                    id = id,
                    name = name,
                    professor = prof,
                    colorHex = color,
                    buildingAndRoom = buildingAndRoom,
                    shortLocation = shortLocation,
                    semester = sem.ifBlank { activeSemester }
                )
            }

            val todayDow = today.dayOfWeek.value // 1=SEG .. 7=DOM
            val todayIso = today.toString() // YYYY-MM-DD

            // Mapeia check-in / faltas / cancelamentos registrados para hoje
            val statusBySlotId = mutableMapOf<String, String>()
            val sessionsJson = root.optJSONArray("sessions") ?: JSONArray()
            for (i in 0 until sessionsJson.length()) {
                val session = sessionsJson.optJSONObject(i) ?: continue
                val sessionDate = session.optNonNullString("sessionDate").trim()
                if (sessionDate != todayIso) continue
                val slotId = session.optNonNullString("slotId").trim()
                val status = session.optNonNullString("status").trim().uppercase()
                if (slotId.isNotEmpty() && status.isNotEmpty()) {
                    statusBySlotId[slotId] = status
                }
            }

            // Horários das aulas (slots) para o dia de hoje no semestre ativo
            val slotsJson = root.optJSONArray("slots") ?: JSONArray()
            for (i in 0 until slotsJson.length()) {
                val slot = slotsJson.optJSONObject(i) ?: continue
                val slotDow = slot.optInt("dayOfWeek", -1)
                if (slotDow != todayDow) continue

                val courseId = slot.optNonNullString("courseId").trim()
                val course = activeCourses[courseId] ?: continue

                val slotId = slot.optNonNullString("slotId").trim()
                val startTime = slot.optNonNullString("startTime", "08:00").ifBlank { "08:00" }
                val endTime = slot.optNonNullString("endTime", "09:40").ifBlank { "09:40" }
                val building = slot.optNonNullString("building").trim()
                val room = slot.optNonNullString("room").trim()
                val slotShortLocation = slot.optNonNullString("shortLocation").trim()

                val locationStr = when {
                    building.isNotEmpty() && room.isNotEmpty() -> "$building · $room"
                    slotShortLocation.isNotEmpty() -> slotShortLocation
                    course.buildingAndRoom.isNotEmpty() -> course.buildingAndRoom
                    course.shortLocation.isNotEmpty() -> course.shortLocation
                    else -> ""
                }

                val baseSubtitle = when {
                    course.professor.isNotEmpty() && locationStr.isNotEmpty() ->
                        "${course.professor} · $locationStr"
                    course.professor.isNotEmpty() ->
                        course.professor
                    locationStr.isNotEmpty() ->
                        locationStr
                    else ->
                        "Campus"
                }

                val sessionStatus = statusBySlotId[slotId]
                val itemTitle = when (sessionStatus) {
                    "ABSENT" -> "${course.name} · Falta"
                    "CANCELLED" -> "${course.name} · Cancelada"
                    else -> course.name
                }
                val itemColor = if (sessionStatus == "CANCELLED") {
                    CANCELLED_ACCENT_COLOR
                } else {
                    course.colorHex
                }

                val stableId = allocateUniqueStableId(
                    rawKey = "class:${slotId.ifBlank { "$courseId:$startTime:$i" }}:$todayIso",
                    usedIds = usedStableIds
                )
                loadedItems.add(
                    WidgetScheduleItem(
                        stableId = stableId,
                        title = itemTitle,
                        subtitle = baseSubtitle,
                        time = "$startTime–$endTime",
                        startTime = startTime,
                        colorHex = itemColor
                    )
                )
                classesCount++
            }

            // Compromissos e lembretes para o dia de hoje
            val commitmentsJson = root.optJSONArray("commitments") ?: JSONArray()
            for (i in 0 until commitmentsJson.length()) {
                val comm = commitmentsJson.optJSONObject(i) ?: continue
                val isoDate = comm.optNonNullString("isoDate").trim()
                if (isoDate != todayIso) continue

                val commId = comm.optNonNullString("id").trim()
                val title = comm.optNonNullString("title", "Compromisso").ifBlank { "Compromisso" }
                val sub = comm.optNonNullString("subtitle").trim()
                val loc = comm.optNonNullString("location").trim()
                val startTime = comm.optNonNullString("startTime", "08:00").ifBlank { "08:00" }
                val endTime = comm.optNonNullString("endTime", "09:00").ifBlank { "09:00" }
                val colorHex = comm.optNonNullString("colorHex", DEFAULT_COMMITMENT_COLOR)
                    .ifBlank { DEFAULT_COMMITMENT_COLOR }

                val subtitle = when {
                    sub.isNotEmpty() && loc.isNotEmpty() -> "$sub · $loc"
                    sub.isNotEmpty() -> sub
                    loc.isNotEmpty() -> loc
                    else -> "Compromisso Acadêmico"
                }

                val stableId = allocateUniqueStableId(
                    rawKey = "comm:${commId.ifBlank { "$title:$startTime:$i" }}:$todayIso",
                    usedIds = usedStableIds
                )
                loadedItems.add(
                    WidgetScheduleItem(
                        stableId = stableId,
                        title = title,
                        subtitle = subtitle,
                        time = "$startTime–$endTime",
                        startTime = startTime,
                        colorHex = colorHex
                    )
                )
                commitmentsCount++
            }

            loadedItems.sortWith(
                compareBy<WidgetScheduleItem> { parseStartMinutes(it.startTime) }
                    .thenBy { it.startTime }
                    .thenBy { it.title }
            )
        } catch (_: Throwable) {
            // Mantém resiliência em caso de falha de I/O ou JSON inválido
        }

        return WidgetScheduleSnapshot(
            items = loadedItems,
            classesCount = classesCount,
            commitmentsCount = commitmentsCount,
            activeSemester = resolvedSemester
        )
    }

    private fun allocateUniqueStableId(rawKey: String, usedIds: MutableSet<Long>): Long {
        var hash = -3750763034362895579L // FNV-1a 64-bit offset basis
        for (ch in rawKey) {
            hash = hash xor ch.code.toLong()
            hash *= 1099511628211L
        }
        while (hash == 0L || !usedIds.add(hash)) {
            hash += 1L
        }
        return hash
    }

    private fun readStateJson(context: Context): JSONObject? {
        val primary = File(context.filesDir, "campus_ufop_state.json")
        val backup = File(context.filesDir, "campus_ufop_state.json.bak")
        return tryParseJsonFile(primary) ?: tryParseJsonFile(backup)
    }

    private fun tryParseJsonFile(file: File): JSONObject? {
        if (!file.exists() || !file.isFile) return null
        return try {
            val raw = file.readText(Charsets.UTF_8)
            if (raw.isBlank()) return null
            val decrypted = try {
                br.ufop.campus.core.security.CampusCryptoEngine.decryptStatePayloadIfNeeded(raw)
            } catch (_: Throwable) {
                if (raw.startsWith("UFOPENC1:")) return null
                raw
            }
            if (decrypted.isBlank()) null else JSONObject(decrypted)
        } catch (_: Throwable) {
            null
        }
    }

    private fun findFirstNonBlankSemester(coursesJson: JSONArray): String? {
        for (i in 0 until coursesJson.length()) {
            val c = coursesJson.optJSONObject(i) ?: continue
            val sem = c.optNonNullString("semester").trim()
            if (sem.isNotEmpty()) return sem
        }
        return null
    }

    private fun resolveCourseColor(
        explicitOverride: String,
        defaultColor: String,
        departmentId: String,
        deptColorOverrides: Map<String, String>
    ): String {
        val cleanOverride = explicitOverride.trim()
        if (isValidHexColor(cleanOverride)) return cleanOverride

        val cleanDefault = defaultColor.trim()
        if (isValidHexColor(cleanDefault)) return cleanDefault

        val normalizedDept = departmentId.trim().lowercase().let { raw ->
            LEGACY_DEPARTMENT_ALIASES[raw] ?: raw
        }
        val deptOverride = deptColorOverrides[normalizedDept]
        if (deptOverride != null && isValidHexColor(deptOverride)) return deptOverride

        return UFOP_DEPARTMENT_COLORS[normalizedDept] ?: DEFAULT_COURSE_COLOR
    }

    private fun isValidHexColor(value: String): Boolean {
        if (value.isEmpty() || !value.startsWith("#")) return false
        return try {
            Color.parseColor(value)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun parseStartMinutes(hhmm: String): Int {
        val parts = hhmm.trim().split(":")
        if (parts.size != 2) return Int.MAX_VALUE
        val h = parts[0].toIntOrNull() ?: return Int.MAX_VALUE
        val m = parts[1].toIntOrNull() ?: return Int.MAX_VALUE
        if (h !in 0..23 || m !in 0..59) return Int.MAX_VALUE
        return h * 60 + m
    }

    private fun JSONObject.optNonNullString(key: String, fallback: String = ""): String =
        if (!has(key) || isNull(key)) fallback else optString(key, fallback)
}

private class VidaAcademicaRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private val items = mutableListOf<WidgetScheduleItem>()

    override fun onCreate() {
        loadData()
    }

    override fun onDataSetChanged() {
        loadData()
    }

    override fun onDestroy() {
        synchronized(items) {
            items.clear()
        }
    }

    override fun getCount(): Int = synchronized(items) { items.size }

    override fun getViewAt(position: Int): RemoteViews? {
        val item = synchronized(items) {
            if (position < 0 || position >= items.size) return null
            items[position]
        }
        val palette = WidgetAccentThemeHelper.resolvePalette(context)
        return WidgetScheduleItemRenderer.buildItemRemoteViews(
            context = context,
            item = item,
            timeAccentColor = palette.itemTimeColor
        )
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = synchronized(items) {
        items.getOrNull(position)?.stableId ?: position.toLong()
    }

    override fun hasStableIds(): Boolean = true

    private fun loadData() {
        val identityToken = Binder.clearCallingIdentity()
        try {
            val snapshot = WidgetScheduleLoader.loadTodaySchedule(context)
            synchronized(items) {
                items.clear()
                items.addAll(snapshot.items)
            }
        } finally {
            Binder.restoreCallingIdentity(identityToken)
        }
    }
}
