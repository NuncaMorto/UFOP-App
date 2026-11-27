package br.ufop.campus.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Binder
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import br.ufop.campus.R
import br.ufop.campus.core.ffi.formatAcademicAssessmentTitle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * RemoteViewsService mantido por compatibilidade com registros de sistema (API 26–30).
 * Os layouts atuais da Rodada 2 (4×2 e 2×2) são renderizados diretamente no Provider sem ListView.
 */
class VidaAcademicaWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return VidaAcademicaRemoteViewsFactory(applicationContext)
    }
}

internal data class WidgetTonalPalette(
    val canvas: Int,
    val surface1: Int,
    val surface2: Int,
    val primary: Int,
    val onPrimary: Int,
    val highlight: Int,
    val ink: Int,
    val onSurfaceVariant: Int,
    val accentText: Int,
    val liveBadgeBg: Int,
    val liveBadgeFg: Int
)

internal data class WidgetClassHighlight(
    val courseId: String,
    val title: String,
    val startTime: String,
    val endTime: String,
    val timeRange: String,
    val roomPill: String,
    val smallRoomPill: String,
    val isOngoing: Boolean,
    val isEad: Boolean,
    val statusNote: String? = null
)

internal data class WidgetCompactClassRow(
    val courseId: String,
    val startTime: String,
    val title: String,
    val inlineRoom: String
)

internal data class WidgetDeadlineHighlight(
    val id: String,
    val title: String,
    val shortDate: String,
    val freeDayHeader: String,
    val dueDate: String,
    val dueTime: String,
    val courseName: String
)

internal data class WidgetNextDayClass(
    val dayBanner: String,
    val startTime: String,
    val title: String,
    val roomLabel: String
)

internal data class WidgetCorridorSnapshot(
    val dayPillText: String,
    val headerSummary: String,
    val freeDayMessage: String,
    val countdownLabel: String,
    val compactCountdownBadge: String,
    val isOngoing: Boolean,
    val todayClassesCount: Int,
    val remainingAfterSpotlightCount: Int,
    val spotlightClass: WidgetClassHighlight?,
    val followingClass: WidgetCompactClassRow?,
    val thirdClass: WidgetCompactClassRow?,
    val nextDeadline: WidgetDeadlineHighlight?,
    val nextSchoolDayClass: WidgetNextDayClass?,
    val activeSemester: String
)

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

internal object WidgetAccentThemeHelper {
    private const val PREFS_NAME = "vida_academica_prefs"
    private const val KEY_ACCENT_COLOR = "accent_color_key"
    private const val KEY_DARK_THEME_MODE = "dark_theme_mode"

    fun resolvePalette(context: Context, stateRoot: JSONObject? = null): WidgetTonalPalette {
        val accentKey = resolveAccentKey(context, stateRoot)
        val isDark = resolveIsDark(context, stateRoot)
        return resolvePalette(accentKey = accentKey, isDark = isDark)
    }

    fun resolveAccentKey(context: Context, stateRoot: JSONObject? = null): String {
        val fromPrefs = runCatching {
            (context.applicationContext ?: context)
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ACCENT_COLOR, null)
        }.getOrNull()?.trim()?.uppercase()
        if (!fromPrefs.isNullOrEmpty()) return fromPrefs

        val fromState = stateRoot?.optString("accentColorPreference", "")
            ?.ifBlank { stateRoot.optString("accent_color_key", "") }
            ?.trim()
            ?.uppercase()
        return if (!fromState.isNullOrEmpty()) fromState else "RED"
    }

    fun resolveIsDark(context: Context, stateRoot: JSONObject? = null): Boolean {
        val rawMode = runCatching {
            (context.applicationContext ?: context)
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_DARK_THEME_MODE, null)
        }.getOrNull()?.trim()?.uppercase()
            ?: stateRoot?.optString("darkThemePreference", "")?.trim()?.uppercase()

        return when (rawMode) {
            "DARK" -> true
            "LIGHT" -> false
            else -> {
                val uiMode = (context.applicationContext ?: context).resources.configuration.uiMode
                (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    /**
     * Resolve uma das 9 escadas tonais WCAG 2.1 AA idênticas às de `Theme.kt`
     * usando inteiros ARGB puros (100% compatível com testes JVM locais).
     */
    fun resolvePalette(accentKey: String?, isDark: Boolean): WidgetTonalPalette {
        val normalized = accentKey?.trim()?.uppercase() ?: "RED"
        val liveBg = if (isDark) 0xFF25352E.toInt() else 0xFFE1EFE8.toInt()
        val liveFg = if (isDark) 0xFFA6DFC3.toInt() else 0xFF11291F.toInt()
        val darkInk = 0xFFF6F4F5.toInt()
        val darkVariant = 0xFFCCC6CA.toInt()
        val lightVariant = 0xFF524B4E.toInt()

        return when (normalized to isDark) {
            "GREEN" to true -> WidgetTonalPalette(
                canvas = 0xFF101513.toInt(),
                surface1 = 0xFF1A2520.toInt(),
                surface2 = 0xFF25352E.toInt(),
                primary = 0xFF267A54.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                highlight = 0xFFA6DFC3.toInt(),
                ink = darkInk,
                onSurfaceVariant = darkVariant,
                accentText = 0xFFA6DFC3.toInt(),
                liveBadgeBg = liveBg,
                liveBadgeFg = liveFg
            )
            "GREEN" to false -> WidgetTonalPalette(
                canvas = 0xFFF3F9F6.toInt(),
                surface1 = 0xFFFFFFFF.toInt(),
                surface2 = 0xFFE1EFE8.toInt(),
                primary = 0xFF216B4A.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                highlight = 0xFF11291F.toInt(),
                ink = 0xFF11291F.toInt(),
                onSurfaceVariant = lightVariant,
                accentText = 0xFF216B4A.toInt(),
                liveBadgeBg = liveBg,
                liveBadgeFg = liveFg
            )
            "ORANGE" to true -> WidgetTonalPalette(
                canvas = 0xFF161210.toInt(),
                surface1 = 0xFF261E19.toInt(),
                surface2 = 0xFF362A23.toInt(),
                primary = 0xFFBD501E.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                highlight = 0xFFF7C4A8.toInt(),
                ink = darkInk,
                onSurfaceVariant = darkVariant,
                accentText = 0xFFF7C4A8.toInt(),
                liveBadgeBg = liveBg,
                liveBadgeFg = liveFg
            )
            "ORANGE" to false -> WidgetTonalPalette(
                canvas = 0xFFFBF6F3.toInt(),
                surface1 = 0xFFFFFFFF.toInt(),
                surface2 = 0xFFF4E6DD.toInt(),
                primary = 0xFFB54717.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                highlight = 0xFF2E170D.toInt(),
                ink = 0xFF2E170D.toInt(),
                onSurfaceVariant = lightVariant,
                accentText = 0xFFB54717.toInt(),
                liveBadgeBg = liveBg,
                liveBadgeFg = liveFg
            )
            "PURPLE" to true -> WidgetTonalPalette(
                canvas = 0xFF131117.toInt(),
                surface1 = 0xFF201C29.toInt(),
                surface2 = 0xFF2D273B.toInt(),
                primary = 0xFF7554D1.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                highlight = 0xFFCFC0FA.toInt(),
                ink = darkInk,
                onSurfaceVariant = darkVariant,
                accentText = 0xFFCFC0FA.toInt(),
                liveBadgeBg = liveBg,
                liveBadgeFg = liveFg
            )
            "PURPLE" to false -> WidgetTonalPalette(
                canvas = 0xFFF7F5FC.toInt(),
                surface1 = 0xFFFFFFFF.toInt(),
                surface2 = 0xFFEAE4F7.toInt(),
                primary = 0xFF5F3DC4.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                highlight = 0xFF1E1438.toInt(),
                ink = 0xFF1E1438.toInt(),
                onSurfaceVariant = lightVariant,
                accentText = 0xFF5F3DC4.toInt(),
                liveBadgeBg = liveBg,
                liveBadgeFg = liveFg
            )
            "YELLOW" to true -> WidgetTonalPalette(
                canvas = 0xFF15130F.toInt(),
                surface1 = 0xFF242017.toInt(),
                surface2 = 0xFF332D20.toInt(),
                primary = 0xFFE0AE43.toInt(),
                onPrimary = 0xFF211602.toInt(),
                highlight = 0xFFF2D591.toInt(),
                ink = darkInk,
                onSurfaceVariant = darkVariant,
                accentText = 0xFFF2D591.toInt(),
                liveBadgeBg = liveBg,
                liveBadgeFg = liveFg
            )
            "YELLOW" to false -> WidgetTonalPalette(
                canvas = 0xFFFAF7F0.toInt(),
                surface1 = 0xFFFFFFFF.toInt(),
                surface2 = 0xFFF0E7D1.toInt(),
                primary = 0xFF9A680E.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                highlight = 0xFF2E2107.toInt(),
                ink = 0xFF2E2107.toInt(),
                onSurfaceVariant = lightVariant,
                accentText = 0xFF9A680E.toInt(),
                liveBadgeBg = liveBg,
                liveBadgeFg = liveFg
            )
            "CYAN" to true -> WidgetTonalPalette(
                canvas = 0xFF101517.toInt(),
                surface1 = 0xFF192428.toInt(),
                surface2 = 0xFF233339.toInt(),
                primary = 0xFF177C8A.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                highlight = 0xFF9EE0E8.toInt(),
                ink = darkInk,
                onSurfaceVariant = darkVariant,
                accentText = 0xFF9EE0E8.toInt(),
                liveBadgeBg = liveBg,
                liveBadgeFg = liveFg
            )
            "CYAN" to false -> WidgetTonalPalette(
                canvas = 0xFFF2F9FA.toInt(),
                surface1 = 0xFFFFFFFF.toInt(),
                surface2 = 0xFFDFEEF1.toInt(),
                primary = 0xFF126E7B.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                highlight = 0xFF0D282D.toInt(),
                ink = 0xFF0D282D.toInt(),
                onSurfaceVariant = lightVariant,
                accentText = 0xFF126E7B.toInt(),
                liveBadgeBg = liveBg,
                liveBadgeFg = liveFg
            )
            "BROWN" to true -> WidgetTonalPalette(
                canvas = 0xFF151210.toInt(),
                surface1 = 0xFF241E1B.toInt(),
                surface2 = 0xFF332B26.toInt(),
                primary = 0xFF8F6249.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                highlight = 0xFFDFC2B2.toInt(),
                ink = darkInk,
                onSurfaceVariant = darkVariant,
                accentText = 0xFFDFC2B2.toInt(),
                liveBadgeBg = liveBg,
                liveBadgeFg = liveFg
            )
            "BROWN" to false -> WidgetTonalPalette(
                canvas = 0xFFFAF6F3.toInt(),
                surface1 = 0xFFFFFFFF.toInt(),
                surface2 = 0xFFEFE6E0.toInt(),
                primary = 0xFF734A34.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                highlight = 0xFF291A12.toInt(),
                ink = 0xFF291A12.toInt(),
                onSurfaceVariant = lightVariant,
                accentText = 0xFF734A34.toInt(),
                liveBadgeBg = liveBg,
                liveBadgeFg = liveFg
            )
            "PINK" to true -> WidgetTonalPalette(
                canvas = 0xFF161114.toInt(),
                surface1 = 0xFF261B21.toInt(),
                surface2 = 0xFF36252F.toInt(),
                primary = 0xFFC43E78.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                highlight = 0xFFF5B8D2.toInt(),
                ink = darkInk,
                onSurfaceVariant = darkVariant,
                accentText = 0xFFF5B8D2.toInt(),
                liveBadgeBg = liveBg,
                liveBadgeFg = liveFg
            )
            "PINK" to false -> WidgetTonalPalette(
                canvas = 0xFFFCF4F8.toInt(),
                surface1 = 0xFFFFFFFF.toInt(),
                surface2 = 0xFFF4E2EB.toInt(),
                primary = 0xFFA62660.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                highlight = 0xFF300F1E.toInt(),
                ink = 0xFF300F1E.toInt(),
                onSurfaceVariant = lightVariant,
                accentText = 0xFFA62660.toInt(),
                liveBadgeBg = liveBg,
                liveBadgeFg = liveFg
            )
            "BLUE" to true -> WidgetTonalPalette(
                canvas = 0xFF101318.toInt(),
                surface1 = 0xFF1A212D.toInt(),
                surface2 = 0xFF252F40.toInt(),
                primary = 0xFF356AD1.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                highlight = 0xFFB3CCF8.toInt(),
                ink = darkInk,
                onSurfaceVariant = darkVariant,
                accentText = 0xFFB3CCF8.toInt(),
                liveBadgeBg = liveBg,
                liveBadgeFg = liveFg
            )
            "BLUE" to false -> WidgetTonalPalette(
                canvas = 0xFFF3F7FC.toInt(),
                surface1 = 0xFFFFFFFF.toInt(),
                surface2 = 0xFFE2EBF8.toInt(),
                primary = 0xFF2557B8.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                highlight = 0xFF121F3B.toInt(),
                ink = 0xFF121F3B.toInt(),
                onSurfaceVariant = lightVariant,
                accentText = 0xFF2557B8.toInt(),
                liveBadgeBg = liveBg,
                liveBadgeFg = liveFg
            )
            "RED" to true -> WidgetTonalPalette(
                canvas = 0xFF151112.toInt(),
                surface1 = 0xFF231A1D.toInt(),
                surface2 = 0xFF312428.toInt(),
                primary = 0xFFA82E42.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                highlight = 0xFFF2B8C1.toInt(),
                ink = darkInk,
                onSurfaceVariant = darkVariant,
                accentText = 0xFFF2B8C1.toInt(),
                liveBadgeBg = liveBg,
                liveBadgeFg = liveFg
            )
            else -> WidgetTonalPalette(
                canvas = 0xFFFBF5F6.toInt(),
                surface1 = 0xFFFFFFFF.toInt(),
                surface2 = 0xFFF2E4E7.toInt(),
                primary = 0xFF8C1D35.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                highlight = 0xFF2B1217.toInt(),
                ink = 0xFF2B1217.toInt(),
                onSurfaceVariant = lightVariant,
                accentText = 0xFF8C1D35.toInt(),
                liveBadgeBg = liveBg,
                liveBadgeFg = liveFg
            )
        }
    }
}

private data class CourseMeta(
    val id: String,
    val name: String,
    val professor: String,
    val buildingAndRoom: String,
    val shortLocation: String,
    val semester: String
)

private data class ParsedSlotCandidate(
    val courseId: String,
    val title: String,
    val startTime: String,
    val endTime: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val roomPill: String,
    val smallRoomPill: String,
    val inlineRoom: String,
    val isEad: Boolean,
    val isCancelled: Boolean,
    val statusNote: String?
)

/**
 * Leitor compartilhado do estado criptografado local para os widgets 4×2 e 2×2.
 */
internal object WidgetScheduleLoader {

    private val PT_BR_WEEKDAYS = arrayOf("", "SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")
    private val PT_BR_MONTHS = arrayOf("", "jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    private val ISO_DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE

    fun loadCorridorSnapshot(
        context: Context,
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now()
    ): Pair<WidgetCorridorSnapshot, JSONObject?> {
        val root = readStateJson(context)
        return buildWidgetSnapshot(root = root, today = today, now = now) to root
    }

    /**
     * Função pura que transforma o JSON de estado em um resumo pronto para leitura em 1 segundo no corredor.
     */
    fun buildWidgetSnapshot(
        root: JSONObject?,
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now()
    ): WidgetCorridorSnapshot {
        val dayPillText = "${PT_BR_WEEKDAYS[today.dayOfWeek.value]} ${today.dayOfMonth}"
        if (root == null) {
            return WidgetCorridorSnapshot(
                dayPillText = dayPillText,
                headerSummary = "Dia livre",
                freeDayMessage = "Sem aulas hoje",
                countdownLabel = "",
                compactCountdownBadge = dayPillText,
                isOngoing = false,
                todayClassesCount = 0,
                remainingAfterSpotlightCount = 0,
                spotlightClass = null,
                followingClass = null,
                thirdClass = null,
                nextDeadline = null,
                nextSchoolDayClass = null,
                activeSemester = "2026.2"
            )
        }

        val coursesJson = root.optJSONArray("courses") ?: JSONArray()
        val firstCourseSemester = findFirstNonBlankSemester(coursesJson)
        val profileJson = root.optJSONObject("studentProfile")
        val activeSemester = profileJson?.optNonNullString("activeSemester")
            ?.trim()
            ?.ifBlank { firstCourseSemester ?: "2026.2" }
            ?: (firstCourseSemester ?: "2026.2")

        val activeCourses = mutableMapOf<String, CourseMeta>()
        for (i in 0 until coursesJson.length()) {
            val c = coursesJson.optJSONObject(i) ?: continue
            val id = c.optNonNullString("courseId").ifBlank { c.optNonNullString("id") }.trim()
            if (id.isEmpty()) continue
            val sem = c.optNonNullString("semester").trim()
            if (sem.isNotEmpty() && sem != activeSemester) continue

            activeCourses[id] = CourseMeta(
                id = id,
                name = c.optNonNullString("name").trim().ifBlank { id },
                professor = c.optNonNullString("professor").trim(),
                buildingAndRoom = c.optNonNullString("buildingAndRoom").trim(),
                shortLocation = c.optNonNullString("shortLocation").trim(),
                semester = sem.ifBlank { activeSemester }
            )
        }

        val todayIso = today.format(ISO_DATE_FMT)
        val statusBySlotToday = loadStatusBySlotForDate(root, todayIso)
        val slotsJson = root.optJSONArray("slots") ?: JSONArray()

        val todayCandidates = collectClassesForWeekday(
            slotsJson = slotsJson,
            activeCourses = activeCourses,
            dayOfWeek = today.dayOfWeek.value,
            statusBySlotId = statusBySlotToday
        ).filterNot { it.isCancelled }

        val nowMinutes = now.hour * 60 + now.minute
        val spotlightIndex = todayCandidates.indexOfFirst { cand ->
            (nowMinutes >= cand.startMinutes && nowMinutes < cand.endMinutes) || nowMinutes < cand.startMinutes
        }

        val spotlightCandidate = if (spotlightIndex >= 0) todayCandidates[spotlightIndex] else null
        val followingCandidate = if (spotlightIndex >= 0) todayCandidates.getOrNull(spotlightIndex + 1) else null
        val thirdCandidate = if (spotlightIndex >= 0) todayCandidates.getOrNull(spotlightIndex + 2) else null
        val remainingAfterSpotlightCount = if (spotlightIndex >= 0) {
            (todayCandidates.size - spotlightIndex - 1).coerceAtLeast(0)
        } else {
            0
        }

        val isOngoing = spotlightCandidate != null &&
            nowMinutes >= spotlightCandidate.startMinutes &&
            nowMinutes < spotlightCandidate.endMinutes

        val countdownLabel = when {
            spotlightCandidate == null -> ""
            isOngoing -> "AGORA"
            else -> formatCountdown(
                minutesUntilStart = (spotlightCandidate.startMinutes - nowMinutes).coerceAtLeast(0),
                uppercase = false
            )
        }
        val compactCountdownBadge = when {
            spotlightCandidate == null -> dayPillText
            isOngoing -> "AGORA"
            else -> formatCountdown(
                minutesUntilStart = (spotlightCandidate.startMinutes - nowMinutes).coerceAtLeast(0),
                uppercase = true
            )
        }

        val headerSummary = when {
            todayCandidates.isEmpty() -> "Dia livre"
            todayCandidates.size == 1 -> "1 aula hoje"
            else -> "${todayCandidates.size} aulas hoje"
        }
        val freeDayMessage = if (todayCandidates.isEmpty()) {
            "Sem aulas hoje"
        } else {
            "Aulas de hoje concluídas"
        }

        val nextDeadline = findNextPendingDeadline(
            root = root,
            activeCourses = activeCourses,
            today = today
        )

        val nextSchoolDayClass = if (spotlightCandidate == null) {
            findNextSchoolDayClass(
                root = root,
                slotsJson = slotsJson,
                activeCourses = activeCourses,
                today = today
            )
        } else {
            null
        }

        return WidgetCorridorSnapshot(
            dayPillText = dayPillText,
            headerSummary = headerSummary,
            freeDayMessage = freeDayMessage,
            countdownLabel = countdownLabel,
            compactCountdownBadge = compactCountdownBadge,
            isOngoing = isOngoing,
            todayClassesCount = todayCandidates.size,
            remainingAfterSpotlightCount = remainingAfterSpotlightCount,
            spotlightClass = spotlightCandidate?.let {
                WidgetClassHighlight(
                    courseId = it.courseId,
                    title = it.title,
                    startTime = it.startTime,
                    endTime = it.endTime,
                    timeRange = "${it.startTime}–${it.endTime}",
                    roomPill = it.roomPill,
                    smallRoomPill = it.smallRoomPill,
                    isOngoing = isOngoing,
                    isEad = it.isEad,
                    statusNote = it.statusNote
                )
            },
            followingClass = followingCandidate?.let {
                WidgetCompactClassRow(
                    courseId = it.courseId,
                    startTime = it.startTime,
                    title = it.title,
                    inlineRoom = it.inlineRoom
                )
            },
            thirdClass = thirdCandidate?.let {
                WidgetCompactClassRow(
                    courseId = it.courseId,
                    startTime = it.startTime,
                    title = it.title,
                    inlineRoom = it.inlineRoom
                )
            },
            nextDeadline = nextDeadline,
            nextSchoolDayClass = nextSchoolDayClass,
            activeSemester = activeSemester
        )
    }

    private fun loadStatusBySlotForDate(root: JSONObject, targetIso: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val sessionsJson = root.optJSONArray("sessions") ?: return map
        for (i in 0 until sessionsJson.length()) {
            val session = sessionsJson.optJSONObject(i) ?: continue
            if (session.optNonNullString("sessionDate").trim() != targetIso) continue
            val slotId = session.optNonNullString("slotId").trim()
            val status = session.optNonNullString("status").trim().uppercase()
            if (slotId.isNotEmpty() && status.isNotEmpty()) {
                map[slotId] = status
            }
        }
        return map
    }

    private fun collectClassesForWeekday(
        slotsJson: JSONArray,
        activeCourses: Map<String, CourseMeta>,
        dayOfWeek: Int,
        statusBySlotId: Map<String, String>
    ): List<ParsedSlotCandidate> {
        val list = mutableListOf<ParsedSlotCandidate>()
        for (i in 0 until slotsJson.length()) {
            val slot = slotsJson.optJSONObject(i) ?: continue
            if (slot.optInt("dayOfWeek", -1) != dayOfWeek) continue

            val courseId = slot.optNonNullString("courseId").trim()
            val course = activeCourses[courseId] ?: continue

            val slotId = slot.optNonNullString("slotId").trim()
            val startTime = slot.optNonNullString("startTime", "08:00").ifBlank { "08:00" }
            val endTime = slot.optNonNullString("endTime", "09:40").ifBlank { "09:40" }
            val startMin = parseStartMinutes(startTime)
            val endMin = parseStartMinutes(endTime).let { if (it == Int.MAX_VALUE) startMin + 50 else it }

            val building = slot.optNonNullString("building").trim()
            val room = slot.optNonNullString("room").trim()
            val shortLoc = slot.optNonNullString("shortLocation").trim()
            val isEad = isEadLocation(building, room, shortLoc, course.buildingAndRoom, course.shortLocation)

            val roomBadges = formatWidgetRoomLabels(
                building = building,
                room = room,
                shortLocation = shortLoc,
                fallbackBuildingAndRoom = course.buildingAndRoom,
                fallbackShortLocation = course.shortLocation,
                isEad = isEad
            )

            val status = statusBySlotId[slotId]
            val isCancelled = status == "CANCELLED"
            val statusNote = if (status == "ABSENT") "Falta registrada" else null

            list.add(
                ParsedSlotCandidate(
                    courseId = courseId,
                    title = course.name,
                    startTime = startTime,
                    endTime = endTime,
                    startMinutes = startMin,
                    endMinutes = endMin,
                    roomPill = roomBadges.spotlightPill,
                    smallRoomPill = roomBadges.smallPill,
                    inlineRoom = roomBadges.inlineRoom,
                    isEad = isEad,
                    isCancelled = isCancelled,
                    statusNote = statusNote
                )
            )
        }
        list.sortWith(compareBy<ParsedSlotCandidate> { it.startMinutes }.thenBy { it.title })
        return list
    }

    private fun findNextSchoolDayClass(
        root: JSONObject,
        slotsJson: JSONArray,
        activeCourses: Map<String, CourseMeta>,
        today: LocalDate
    ): WidgetNextDayClass? {
        for (offset in 1..7) {
            val candidateDate = today.plusDays(offset.toLong())
            val iso = candidateDate.format(ISO_DATE_FMT)
            val statusMap = loadStatusBySlotForDate(root, iso)
            val candidates = collectClassesForWeekday(
                slotsJson = slotsJson,
                activeCourses = activeCourses,
                dayOfWeek = candidateDate.dayOfWeek.value,
                statusBySlotId = statusMap
            ).filterNot { it.isCancelled }
            val first = candidates.firstOrNull() ?: continue
            val banner = formatRelativeDayBanner(candidateDate, today)
            return WidgetNextDayClass(
                dayBanner = banner,
                startTime = first.startTime,
                title = first.title,
                roomLabel = first.roomPill
            )
        }
        return null
    }

    private fun findNextPendingDeadline(
        root: JSONObject,
        activeCourses: Map<String, CourseMeta>,
        today: LocalDate
    ): WidgetDeadlineHighlight? {
        val assessmentsJson = root.optJSONArray("assessments") ?: return null
        val todayIso = today.format(ISO_DATE_FMT)

        data class RawAssessment(
            val id: String,
            val courseId: String,
            val title: String,
            val dueDate: String,
            val dueTime: String
        )

        val pending = mutableListOf<RawAssessment>()
        for (i in 0 until assessmentsJson.length()) {
            val obj = assessmentsJson.optJSONObject(i) ?: continue
            if (obj.optBoolean("completed", false)) continue
            val dueDate = obj.optNonNullString("dueDate").trim()
            if (dueDate.isEmpty() || dueDate < todayIso) continue
            val id = obj.optNonNullString("id").trim()
            val courseId = obj.optNonNullString("courseId").trim()
            val rawTitle = obj.optNonNullString("title").trim()
            if (rawTitle.isEmpty()) continue
            val dueTime = obj.optNonNullString("dueTime", "18:00").trim().ifBlank { "18:00" }
            pending.add(
                RawAssessment(
                    id = id.ifBlank { "assess_$i" },
                    courseId = courseId,
                    title = formatAcademicAssessmentTitle(rawTitle),
                    dueDate = dueDate,
                    dueTime = dueTime
                )
            )
        }

        val earliest = pending.minWithOrNull(
            compareBy<RawAssessment> { it.dueDate }
                .thenBy { parseStartMinutes(it.dueTime) }
                .thenBy { it.title }
        ) ?: return null

        val parsedDate = runCatching { LocalDate.parse(earliest.dueDate, ISO_DATE_FMT) }.getOrNull()
        val shortDate = if (parsedDate != null) {
            "${parsedDate.dayOfMonth} ${PT_BR_MONTHS[parsedDate.monthValue]}"
        } else {
            earliest.dueDate
        }
        val freeDayHeader = if (parsedDate != null) {
            formatRelativeDayBanner(parsedDate, today)
        } else {
            shortDate.uppercase()
        }

        return WidgetDeadlineHighlight(
            id = earliest.id,
            title = earliest.title,
            shortDate = shortDate,
            freeDayHeader = freeDayHeader,
            dueDate = earliest.dueDate,
            dueTime = earliest.dueTime,
            courseName = activeCourses[earliest.courseId]?.name.orEmpty()
        )
    }

    private fun formatRelativeDayBanner(date: LocalDate, today: LocalDate): String {
        val dayNum = date.dayOfMonth
        val monthUpper = PT_BR_MONTHS[date.monthValue].uppercase()
        return when {
            date == today -> "HOJE · $dayNum $monthUpper"
            date == today.plusDays(1) -> "AMANHÃ · $dayNum $monthUpper"
            else -> "${PT_BR_WEEKDAYS[date.dayOfWeek.value]} · $dayNum $monthUpper"
        }
    }

    private fun formatCountdown(minutesUntilStart: Int, uppercase: Boolean): String {
        val text = when {
            minutesUntilStart <= 0 -> "agora"
            minutesUntilStart < 60 -> "em ${minutesUntilStart}m"
            else -> {
                val hours = minutesUntilStart / 60
                val mins = minutesUntilStart % 60
                if (mins == 0) "em ${hours}h" else "em ${hours}h ${mins}m"
            }
        }
        return if (uppercase) text.uppercase() else text
    }

    private data class RoomBadgeFormats(
        val spotlightPill: String,
        val smallPill: String,
        val inlineRoom: String
    )

    private fun formatWidgetRoomLabels(
        building: String,
        room: String,
        shortLocation: String,
        fallbackBuildingAndRoom: String,
        fallbackShortLocation: String,
        isEad: Boolean
    ): RoomBadgeFormats {
        if (isEad) {
            return RoomBadgeFormats(spotlightPill = "EAD", smallPill = "EAD", inlineRoom = "EAD")
        }

        val cleanBuilding = simplifyBuildingName(building.ifBlank { fallbackBuildingAndRoom.substringBefore("·").trim() })
        val cleanRoom = room.trim().ifBlank {
            if (fallbackBuildingAndRoom.contains("·")) {
                fallbackBuildingAndRoom.substringAfter("·").trim()
            } else {
                ""
            }
        }

        val roomNumberMatch = Regex("""(?:Sala|Lab(?:orat[óo]rio)?\.?)\s+(.+)""", RegexOption.IGNORE_CASE).find(cleanRoom)
        val roomToken = roomNumberMatch?.groupValues?.getOrNull(1)?.trim()

        val spotlightPill = when {
            cleanBuilding.isNotEmpty() && roomToken != null && cleanRoom.startsWith("Sala", ignoreCase = true) ->
                "$cleanBuilding · S. $roomToken"
            cleanBuilding.isNotEmpty() && cleanRoom.isNotEmpty() ->
                "$cleanBuilding · $cleanRoom"
            shortLocation.isNotEmpty() -> shortLocation
            fallbackShortLocation.isNotEmpty() -> fallbackShortLocation
            cleanBuilding.isNotEmpty() -> cleanBuilding
            else -> "Campus"
        }

        val smallPill = when {
            cleanBuilding.isNotEmpty() && cleanRoom.isNotEmpty() -> "$cleanBuilding · $cleanRoom"
            else -> spotlightPill
        }

        val inlineRoom = when {
            cleanBuilding.isNotEmpty() && roomToken != null && cleanRoom.startsWith("Sala", ignoreCase = true) ->
                "$cleanBuilding $roomToken"
            cleanBuilding.isNotEmpty() && cleanRoom.isNotEmpty() ->
                "$cleanBuilding $cleanRoom"
            shortLocation.isNotEmpty() -> shortLocation.replace(" · ", " ")
            fallbackShortLocation.isNotEmpty() -> fallbackShortLocation.replace(" · ", " ")
            cleanBuilding.isNotEmpty() -> cleanBuilding
            else -> "Campus"
        }

        return RoomBadgeFormats(
            spotlightPill = spotlightPill,
            smallPill = smallPill,
            inlineRoom = inlineRoom
        )
    }

    private fun simplifyBuildingName(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.equals("Bloco de Salas", ignoreCase = true) -> "Bloco"
            trimmed.equals("Escola de Minas", ignoreCase = true) -> "EM"
            trimmed.startsWith("Prédio ", ignoreCase = true) -> trimmed.removePrefix("Prédio ").trim()
            else -> trimmed
        }
    }

    private fun isEadLocation(vararg values: String): Boolean {
        return values.any { v ->
            val lower = v.lowercase()
            lower.contains("ead") ||
                lower.contains("distância") ||
                lower.contains("distancia") ||
                lower.contains("remoto")
        }
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
    override fun onCreate() {}
    override fun onDataSetChanged() {}
    override fun onDestroy() {}
    override fun getCount(): Int = 0
    override fun getViewAt(position: Int): RemoteViews? = null
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
