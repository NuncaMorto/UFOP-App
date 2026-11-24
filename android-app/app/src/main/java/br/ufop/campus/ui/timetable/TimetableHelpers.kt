package br.ufop.campus.ui.timetable

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.FriendScheduleInfo
import br.ufop.campus.core.ffi.StudentProfile
import br.ufop.campus.core.ffi.TimetableCell
import br.ufop.campus.core.ffi.TimetableRow
import br.ufop.campus.core.map.extractCourseSectionFromNotes
import br.ufop.campus.core.map.isEadMapLocation
import java.io.File
import kotlin.math.max

internal val TIME_COLUMN_WIDTH: Dp = 64.dp
internal val REDUCED_TIME_COLUMN_WIDTH: Dp = 56.dp
internal val HEADER_ROW_HEIGHT: Dp = 36.dp
internal val REDUCED_HEADER_ROW_HEIGHT: Dp = 28.dp
internal val CELL_ROW_HEIGHT: Dp = 76.dp
internal val REDUCED_CELL_ROW_HEIGHT: Dp = 48.dp

internal val FULL_WEEK_DAYS = listOf(
    1 to "SEG",
    2 to "TER",
    3 to "QUA",
    4 to "QUI",
    5 to "SEX",
    6 to "SÁB"
)

internal fun fullDayNamePt(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "Segunda-feira"
    2 -> "Terça-feira"
    3 -> "Quarta-feira"
    4 -> "Quinta-feira"
    5 -> "Sexta-feira"
    6 -> "Sábado"
    7 -> "Domingo"
    else -> "Dia letivo"
}

internal data class SelectedTimetableCellDetail(
    val cell: TimetableCell,
    val dayOfWeek: Int,
    val dayFullName: String,
    val startTime: String,
    val endTime: String,
    val fullLocation: String,
    val professor: String,
    val rawLocationForMap: String = fullLocation,
    val section: String = "",
    val isEad: Boolean = false
)

internal fun isEadLocation(raw: String): Boolean = isEadMapLocation(raw)

/**
 * Formata o local curto de forma limpa e legível nas células da grade
 * (ex.: "ICEB · Sala 19", "Bloco · Sala 205", "EAD", ou sigla do prédio como "EM" quando sem sala).
 */
internal fun formatTimetableShortLocation(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return "UFOP"
    if (isEadLocation(trimmed)) return "EAD"

    val rawTokens = trimmed
        .replace('•', '·')
        .replace("–", "-")
        .replace("—", "-")
        .split(Regex("\\s*[·\\-]\\s*"))
        .map { token ->
            token.trim().trim { ch ->
                ch == '-' || ch == '–' || ch == '—' || ch == '·' || ch == '/' || ch == ',' || ch == ':' || ch == '.'
            }.trim()
        }
        .filter { it.isNotEmpty() }

    if (rawTokens.isEmpty()) return "UFOP"

    val cleanedTokens = rawTokens.mapIndexed { index, token ->
        val lower = token.lowercase()
        when {
            lower == "escola de minas" -> "EM"
            lower == "instituto de ciências exatas e biológicas" ||
                lower == "instituto de ciencias exatas e biologicas" -> "ICEB"
            lower == "instituto de ciências sociais aplicadas" ||
                lower == "instituto de ciencias sociais aplicadas" -> "ICSA"
            lower == "instituto de ciências humanas e sociais" ||
                lower == "instituto de ciencias humanas e sociais" -> "ICHS"
            lower == "instituto de ciências exatas e aplicadas" ||
                lower == "instituto de ciencias exatas e aplicadas" -> "ICEA"
            lower == "instituto de filosofia, artes e cultura" ||
                lower == "instituto de filosofia artes e cultura" -> "IFAC"
            lower == "escola de direito, turismo e museologia" ||
                lower == "escola de direito turismo e museologia" -> "EDTM"
            lower == "escola de farmácia" || lower == "escola de farmacia" -> "EF"
            lower == "escola de nutrição" || lower == "escola de nutricao" -> "ENUT"
            lower == "escola de medicina" -> "EMED"
            lower == "escola de educação física" || lower == "escola de educacao fisica" -> "EEF"
            lower.startsWith("bloco de salas") -> "Bloco"
            lower == "pavilhão de aulas" || lower == "pavilhao de aulas" -> "Pavilhão"
            lower.startsWith("sala ") -> "Sala " + token.substring(5).trim()
            index > 0 && Regex("^\\d+[A-Za-z]?$").matches(token) -> "Sala $token"
            lower.startsWith("laborat") -> token.replaceFirst(Regex("(?i)^laborat[oó]rios?\\b"), "Lab").trim()
            lower.contains("teór") || lower.contains("teor") -> "Teórica"
            lower.contains("prát") || lower.contains("prat") -> "Prática"
            lower.startsWith("aula ") -> token.substring(5).trim()
            else -> token
        }
    }.filter { it.isNotEmpty() }.distinct()

    if (cleanedTokens.isEmpty()) return "UFOP"

    val hasConcreteRoom = cleanedTokens.any { token ->
        val l = token.lowercase()
        l.startsWith("sala ") || l.startsWith("lab") || l.startsWith("audit") || l.startsWith("anfiteatro")
    }
    val finalTokens = if (hasConcreteRoom) {
        cleanedTokens.filterNot { it == "Teórica" || it == "Prática" }
    } else {
        cleanedTokens
    }

    return finalTokens.ifEmpty { cleanedTokens }.joinToString(" · ")
}

private val STANDARD_UFOP_TIME_SLOTS = listOf(
    "07:30" to "09:10",
    "09:20" to "11:00",
    "13:30" to "15:10",
    "15:20" to "17:00",
    "17:10" to "18:50",
    "19:00" to "20:40",
    "20:50" to "22:30"
)

internal val friendAvatarBitmapCache = LruCache<String, Bitmap>(16)
internal val localAvatarBitmapCache = LruCache<String, Bitmap>(8)

internal data class GradeAvatarSource(
    val displayName: String,
    val avatarBase64: String?,
    val avatarFilePath: String?
)

internal fun resolveGradeSelectorAvatarSource(
    activeFriend: FriendScheduleInfo?,
    studentProfile: StudentProfile
): GradeAvatarSource = if (activeFriend != null) {
    GradeAvatarSource(
        displayName = activeFriend.friendName.trim().ifBlank { "Amigo" },
        avatarBase64 = activeFriend.avatarBase64?.trim()?.takeIf { it.isNotEmpty() },
        avatarFilePath = null
    )
} else {
    GradeAvatarSource(
        displayName = studentProfile.fullName.trim().ifBlank { "Você" },
        avatarBase64 = null,
        avatarFilePath = studentProfile.avatarPath?.trim()?.takeIf { it.isNotEmpty() }
    )
}

internal fun buildTimetableHeaderSubtitle(
    effectiveSemester: String,
    timetableSemester: String,
    activeFriend: FriendScheduleInfo?
): String {
    if (activeFriend == null) {
        return "Semestre $effectiveSemester · Toque em uma aula para ver detalhes"
    }
    val course = activeFriend.courseName.trim()
    return if (course.isNotEmpty()) {
        "Grade compartilhada · $course · $timetableSemester"
    } else {
        "Grade compartilhada · Semestre $timetableSemester"
    }
}

private fun computeSampleSize(width: Int, height: Int, maxSidePx: Int): Int {
    var sample = 1
    var longest = max(width, height)
    while (longest / 2 >= maxSidePx) {
        longest /= 2
        sample *= 2
    }
    return sample
}

internal fun decodeLocalAvatarFile(avatarPath: String?): Bitmap? {
    val cleanPath = avatarPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val file = File(cleanPath)
    if (!file.exists() || !file.isFile) return null
    val cacheKey = "${cleanPath}:${file.lastModified()}"
    localAvatarBitmapCache.get(cacheKey)?.let { return it }
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(cleanPath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, 192)
        }
        BitmapFactory.decodeFile(cleanPath, opts)?.also { decoded ->
            localAvatarBitmapCache.put(cacheKey, decoded)
        }
    }.getOrNull()
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun parseMinutes(hhmm: String): Int {
    val parts = hhmm.trim().split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return h * 60 + m
}

internal fun formatBlockClassCountBadge(startTime: String, endTime: String): String? {
    val durationMinutes = parseMinutes(endTime) - parseMinutes(startTime)
    if (durationMinutes <= 0) return null
    return when {
        durationMinutes < 70 -> "(1 aula)"
        durationMinutes < 125 -> null
        durationMinutes < 185 -> "(3 aulas)"
        else -> "(4 aulas)"
    }
}

internal fun sanitizeFilenameToken(raw: String): String {
    val normalized = java.text.Normalizer.normalize(raw.trim(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
    return normalized.ifBlank { "ufop" }
}

internal fun decodeAvatarBase64(avatarBase64: String?): Bitmap? {
    val clean = avatarBase64?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    friendAvatarBitmapCache.get(clean)?.let { return it }
    return runCatching {
        val rawPayload = clean.substringAfter("base64,", clean)
        val bytes = Base64.decode(rawPayload, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { decoded ->
            friendAvatarBitmapCache.put(clean, decoded)
        }
    }.getOrNull()
}

internal fun buildInitials(name: String): String {
    val tokens = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        tokens.isEmpty() -> "UF"
        tokens.size == 1 -> tokens[0].take(2).uppercase()
        else -> "${tokens.first().first().uppercaseChar()}${tokens.last().first().uppercaseChar()}"
    }
}

private fun buildFullDayTimetableRows(
    rawRows: List<TimetableRow>,
    days: List<Pair<Int, String>>
): List<TimetableRow> {
    val occupiedRows = rawRows.filter { row ->
        row.cellsByDay.values.any { it != null }
    }.map { row ->
        val normalizedMap = days.associate { (day, _) -> day to row.cellsByDay[day] }
        row.copy(cellsByDay = normalizedMap)
    }
    val combined = occupiedRows.toMutableList()
    val emptyCellsMap = days.associate { (day, _) -> day to null as TimetableCell? }

    for ((stdStart, stdEnd) in STANDARD_UFOP_TIME_SLOTS) {
        val stdStartMin = parseMinutes(stdStart)
        val stdEndMin = parseMinutes(stdEnd)
        val overlapsOccupied = occupiedRows.any { row ->
            val rStart = parseMinutes(row.startTime)
            val rEnd = parseMinutes(row.endTime).coerceAtLeast(rStart + 1)
            stdStartMin < rEnd && stdEndMin > rStart
        }
        if (!overlapsOccupied) {
            combined.add(
                TimetableRow(
                    startTime = stdStart,
                    endTime = stdEnd,
                    cellsByDay = emptyCellsMap
                )
            )
        }
    }

    return combined.sortedWith(
        compareBy<TimetableRow> { parseMinutes(it.startTime) }
            .thenBy { parseMinutes(it.endTime) }
    )
}

internal fun buildDisplayedTimetableRows(
    rawRows: List<TimetableRow>,
    days: List<Pair<Int, String>>,
    showFullDayGrid: Boolean
): List<TimetableRow> {
    val fullRows = buildFullDayTimetableRows(rawRows, days)
    if (showFullDayGrid) return fullRows

    val firstOccupiedIdx = fullRows.indexOfFirst { row -> row.cellsByDay.values.any { it != null } }
    val lastOccupiedIdx = fullRows.indexOfLast { row -> row.cellsByDay.values.any { it != null } }
    return if (firstOccupiedIdx >= 0 && lastOccupiedIdx >= firstOccupiedIdx) {
        fullRows.subList(firstOccupiedIdx, lastOccupiedIdx + 1)
    } else {
        // Sem aulas cadastradas: exibe faixa diurna padrão (07:30–19:00)
        fullRows.filter { parseMinutes(it.startTime) < parseMinutes("19:00") }
            .ifEmpty { fullRows }
    }
}

internal fun resolveSelectedTimetableCellDetail(
    repository: CampusCoreRepository,
    activeFriend: FriendScheduleInfo?,
    cell: TimetableCell,
    dayOfWeek: Int,
    startTime: String,
    endTime: String
): SelectedTimetableCellDetail {
    val dayName = fullDayNamePt(dayOfWeek)
    val detail = if (activeFriend == null && cell.courseId.isNotBlank()) {
        runCatching { repository.getDisciplineDetail(cell.courseId) }.getOrNull()
    } else {
        null
    }
    val slot = if (activeFriend == null && cell.courseId.isNotBlank()) {
        runCatching {
            repository.listCourseSlots(cell.courseId).find { it.slotId == cell.slotId }
        }.getOrNull()
    } else {
        null
    }
    val combinedSlotLocation = listOfNotNull(
        slot?.building?.trim()?.takeIf { it.isNotEmpty() },
        slot?.room?.trim()?.takeIf { it.isNotEmpty() }
    ).joinToString(" · ")
    val rawLocationCandidate = when {
        combinedSlotLocation.isNotBlank() -> combinedSlotLocation
        !detail?.buildingAndRoom.isNullOrBlank() -> detail!!.buildingAndRoom.trim()
        else -> cell.shortLocation
    }
    val isEad = isEadLocation(rawLocationCandidate) || isEadLocation(cell.shortLocation)
    val resolvedLocation = if (isEad) {
        "Ensino a Distância (EAD)"
    } else {
        formatTimetableShortLocation(rawLocationCandidate)
    }
    val resolvedProfessor = detail?.professor
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("Docente UFOP", ignoreCase = true) }
        .orEmpty()
    val resolvedCode = cell.code.ifBlank { detail?.code.orEmpty() }
    val resolvedSection = extractCourseSectionFromNotes(detail?.notes.orEmpty())

    return SelectedTimetableCellDetail(
        cell = cell.copy(code = resolvedCode),
        dayOfWeek = dayOfWeek,
        dayFullName = dayName,
        startTime = slot?.startTime?.ifBlank { startTime } ?: startTime,
        endTime = slot?.endTime?.ifBlank { endTime } ?: endTime,
        fullLocation = resolvedLocation,
        professor = resolvedProfessor,
        rawLocationForMap = rawLocationCandidate.ifBlank { resolvedLocation },
        section = resolvedSection,
        isEad = isEad
    )
}
