package br.ufop.campus.ui.timetable

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import java.io.File
import kotlin.math.max
import br.ufop.campus.core.ffi.DepartmentColorOption
import br.ufop.campus.core.ffi.FriendScheduleInfo
import br.ufop.campus.core.ffi.TimetableCell
import br.ufop.campus.core.ffi.TimetableRow
import br.ufop.campus.core.storage.GradeExportStorage
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.parseHexColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TIME_COLUMN_WIDTH: Dp = 64.dp
private val REDUCED_TIME_COLUMN_WIDTH: Dp = 56.dp
private val HEADER_ROW_HEIGHT: Dp = 36.dp
private val REDUCED_HEADER_ROW_HEIGHT: Dp = 28.dp
private val CELL_ROW_HEIGHT: Dp = 76.dp
private val REDUCED_CELL_ROW_HEIGHT: Dp = 48.dp

private val FULL_WEEK_DAYS = listOf(
    1 to "SEG",
    2 to "TER",
    3 to "QUA",
    4 to "QUI",
    5 to "SEX",
    6 to "SÁB"
)

private fun fullDayNamePt(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "Segunda-feira"
    2 -> "Terça-feira"
    3 -> "Quarta-feira"
    4 -> "Quinta-feira"
    5 -> "Sexta-feira"
    6 -> "Sábado"
    7 -> "Domingo"
    else -> "Dia letivo"
}

private data class SelectedTimetableCellDetail(
    val cell: TimetableCell,
    val dayOfWeek: Int,
    val dayFullName: String,
    val startTime: String,
    val endTime: String,
    val fullLocation: String,
    val professor: String
)

private fun isEadLocation(raw: String): Boolean {
    val lower = raw.lowercase()
    return lower.contains("ead") ||
        lower.contains("distância") ||
        lower.contains("distancia") ||
        lower.contains("remoto") ||
        lower.contains("online")
}

/**
 * Formata o local curto de forma limpa e legível nas células da grade
 * (ex.: "ICEB · Sala 19", "Bloco · Sala 205", "EAD", ou sigla do prédio como "EM" quando sem sala).
 */
private fun formatTimetableShortLocation(raw: String): String {
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

private val friendAvatarBitmapCache = LruCache<Int, Bitmap>(16)
private val localAvatarBitmapCache = LruCache<String, Bitmap>(8)

private fun computeSampleSize(width: Int, height: Int, maxSidePx: Int): Int {
    var sample = 1
    var longest = max(width, height)
    while (longest / 2 >= maxSidePx) {
        longest /= 2
        sample *= 2
    }
    return sample
}

private fun decodeLocalAvatarFile(avatarPath: String?): Bitmap? {
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
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

private fun formatBlockClassCountBadge(startTime: String, endTime: String): String? {
    val durationMinutes = parseMinutes(endTime) - parseMinutes(startTime)
    if (durationMinutes <= 0) return null
    return when {
        durationMinutes < 70 -> "(1 aula)"
        durationMinutes < 125 -> null
        durationMinutes < 185 -> "(3 aulas)"
        else -> "(4 aulas)"
    }
}

private fun sanitizeFilenameToken(raw: String): String {
    val normalized = java.text.Normalizer.normalize(raw.trim(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
    return normalized.ifBlank { "ufop" }
}

private fun decodeAvatarBase64(avatarBase64: String?): Bitmap? {
    val clean = avatarBase64?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val cacheKey = clean.hashCode()
    friendAvatarBitmapCache.get(cacheKey)?.let { return it }
    return runCatching {
        val rawPayload = clean.substringAfter("base64,", clean)
        val bytes = Base64.decode(rawPayload, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { decoded ->
            friendAvatarBitmapCache.put(cacheKey, decoded)
        }
    }.getOrNull()
}

private fun buildInitials(name: String): String {
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

private fun buildDisplayedTimetableRows(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimetableModalScreen(
    repository: CampusCoreRepository,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = LocalCampusThemeController.current.isDark
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = if (a11y.largeTouchTargets) 56.dp else 48.dp
    val toolbarVisualHeight = if (a11y.largeTouchTargets) 52.dp else 36.dp

    var isLandscape by rememberSaveable {
        mutableStateOf(CampusRepositoryHolder.loadTimetableIsLandscape(context))
    }
    var isReducedMode by rememberSaveable { mutableStateOf(false) }
    var showFullDayGrid by rememberSaveable { mutableStateOf(false) }

    // Aplica orientação persistida (Horizontal por padrão, alternável para Vertical) e restaura ao sair
    LaunchedEffect(context, isLandscape) {
        context.findActivity()?.requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    DisposableEffect(context) {
        val activity = context.findActivity()
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val handleDismiss: () -> Unit = {
        context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDismiss()
    }

    BackHandler(onBack = handleDismiss)

    val mutationVersion by repository.mutationVersion.collectAsState()
    val studentProfile = remember(mutationVersion) {
        repository.getStudentProfile()
    }
    val availableSemesters = remember(mutationVersion) {
        repository.listAvailableSemesters().ifEmpty { listOf("2026.2") }
    }
    var selectedSemester by rememberSaveable {
        mutableStateOf(
            studentProfile.activeSemester.ifBlank { availableSemesters.first() }
        )
    }
    val effectiveSemester = if (selectedSemester in availableSemesters) {
        selectedSemester
    } else {
        availableSemesters.first()
    }

    val friendSchedules = remember(mutationVersion) {
        repository.listFriendSchedules()
    }
    var selectedFriendId by rememberSaveable { mutableStateOf<String?>(null) }
    val activeFriend: FriendScheduleInfo? = remember(selectedFriendId, friendSchedules) {
        selectedFriendId?.let { id -> friendSchedules.find { it.id == id } }
    }

    val timetable = remember(mutationVersion, effectiveSemester, activeFriend) {
        if (activeFriend != null) {
            repository.getFriendTimetable(activeFriend.id) ?: activeFriend.timetable
        } else {
            repository.getWeeklyTimetable(semester = effectiveSemester)
        }
    }

    val displayDays = FULL_WEEK_DAYS
    val displayRows = remember(timetable.rows, displayDays, showFullDayGrid) {
        buildDisplayedTimetableRows(timetable.rows, displayDays, showFullDayGrid)
    }

    var showGradeSelectorMenu by remember { mutableStateOf(false) }
    var showSemesterSelectorMenu by remember { mutableStateOf(false) }
    var showImportFriendDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var friendPendingRemoval by remember { mutableStateOf<FriendScheduleInfo?>(null) }
    var selectedCellDetail by remember { mutableStateOf<SelectedTimetableCellDetail?>(null) }
    var isExportingGrade by remember { mutableStateOf(false) }
    var pendingExportJson by remember { mutableStateOf("") }
    var pendingSuggestedFilename by remember { mutableStateOf("grade_ufop.ufopgrade") }
    var friendNameInput by remember { mutableStateOf("") }
    var friendJsonInput by remember { mutableStateOf("") }
    var importFriendError by remember { mutableStateOf<String?>(null) }
    var feedbackBannerMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val openCellDetail: (TimetableCell, Int, String, String) -> Unit = { cell, dayOfWeek, startTime, endTime ->
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
        val resolvedLocation = if (isEadLocation(rawLocationCandidate) || isEadLocation(cell.shortLocation)) {
            "Ensino a Distância (EAD)"
        } else {
            formatTimetableShortLocation(rawLocationCandidate)
        }
        val resolvedProfessor = detail?.professor
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("Docente UFOP", ignoreCase = true) }
            .orEmpty()
        val resolvedCode = cell.code.ifBlank { detail?.code.orEmpty() }

        selectedCellDetail = SelectedTimetableCellDetail(
            cell = cell.copy(code = resolvedCode),
            dayOfWeek = dayOfWeek,
            dayFullName = dayName,
            startTime = slot?.startTime?.ifBlank { startTime } ?: startTime,
            endTime = slot?.endTime?.ifBlank { endTime } ?: endTime,
            fullLocation = resolvedLocation,
            professor = resolvedProfessor
        )
    }

    // Launcher para importar arquivo .ufopgrade (OpenDocument)
    val openUfopGradeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val jsonText = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?.trim()
                    .orEmpty()
                require(jsonText.isNotEmpty()) { "O arquivo selecionado está vazio." }
                repository.importFriendScheduleJson("", jsonText)
            }.onSuccess { imported ->
                selectedFriendId = imported.id
                val courseInfo = imported.courseName.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                feedbackBannerMessage =
                    "Grade de ${imported.friendName}$courseInfo (${imported.semester}) importada!"
            }.onFailure { err ->
                feedbackBannerMessage = err.message
                    ?: "Não foi possível importar o arquivo .ufopgrade selecionado."
            }
        }
    }

    val handleStartShareGrade: () -> Unit = {
        val rawName = studentProfile.fullName.ifBlank { "estudante_ufop" }
        val sanitizedName = sanitizeFilenameToken(rawName)
        val suggestedFilename = "grade_${sanitizedName}.ufopgrade"
        val bundleJson = repository.exportMyGradeBundleJson(effectiveSemester)
        pendingSuggestedFilename = suggestedFilename
        pendingExportJson = bundleJson
        showShareDialog = true
    }

    val selectedGradeLabel = activeFriend?.let { "Grade · ${it.friendName}" } ?: "Minha grade"
    val verticalScrollState = rememberScrollState()
    val horizontalGridScrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (isReducedMode) 12.dp else 16.dp,
                        vertical = if (isReducedMode) 6.dp else 10.dp
                    )
            ) {
                // Barra superior compacta sempre visível (seletores, orientação, densidade, faixa de horários e Fechar >= 48.dp fora da tabela)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Grade de horários",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.semantics { heading() }
                            )
                            if (activeFriend != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FriendAvatarBadge(
                                        friendName = activeFriend.friendName,
                                        avatarBase64 = activeFriend.avatarBase64,
                                        size = 20.dp
                                    )
                                    val courseLabel = activeFriend.courseName
                                        .takeIf { it.isNotBlank() }
                                        ?: "Estudante"
                                    Text(
                                        text = "${activeFriend.friendName} · $courseLabel · ${timetable.semester}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = accessibleTextColor(MaterialTheme.colorScheme.primary, isDark),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                Text(
                                    text = "Semestre $effectiveSemester · Toque em uma aula para ver detalhes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Botão X Fechar: círculo visual de 36.dp em área de toque de 48.dp (expande com largeTouchTargets)
                        val closeVisualSize = if (a11y.largeTouchTargets) 48.dp else 36.dp
                        IconButton(
                            onClick = handleDismiss,
                            modifier = Modifier.size(minTouchTarget)
                        ) {
                            Surface(
                                modifier = Modifier.size(closeVisualSize),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Fechar grade de horários",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(if (a11y.largeTouchTargets) 22.dp else 18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Linha de controles compactos: Seletor de grade (com avatar), Semestre, Compartilhar, Horizontal/Vertical, Compacto/Normal, Só meus horários/Grade completa
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(if (a11y.largeTouchTargets) 6.dp else 2.dp)
                    ) {
                        // Seletor 1: Minha grade ▾ (preserva foto/avatar do usuário e dos amigos)
                        Box {
                            val userDisplayName = studentProfile.fullName.ifBlank { "Você" }
                            ExpressiveSelectorPill(
                                label = selectedGradeLabel,
                                isHighlighted = activeFriend != null,
                                leadingAvatarName = activeFriend?.friendName ?: userDisplayName,
                                leadingAvatarBase64 = activeFriend?.avatarBase64,
                                leadingAvatarFilePath = if (activeFriend == null) studentProfile.avatarPath else null,
                                visualHeight = toolbarVisualHeight,
                                onClick = { showGradeSelectorMenu = true }
                            )

                            DropdownMenu(
                                expanded = showGradeSelectorMenu,
                                onDismissRequest = { showGradeSelectorMenu = false },
                                shape = RoundedCornerShape(24.dp),
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                val isViewingMyGrade = activeFriend == null
                                DropdownMenuItem(
                                    leadingIcon = {
                                        FriendAvatarBadge(
                                            friendName = userDisplayName,
                                            avatarBase64 = null,
                                            avatarFilePath = studentProfile.avatarPath,
                                            size = 28.dp
                                        )
                                    },
                                    text = {
                                        Column {
                                            Text(
                                                text = "Minha grade",
                                                fontWeight = if (isViewingMyGrade) {
                                                    FontWeight.Bold
                                                } else {
                                                    FontWeight.SemiBold
                                                },
                                                color = if (isViewingMyGrade) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    accessibleTextColor(MaterialTheme.colorScheme.primary, isDark)
                                                }
                                            )
                                            val mySubtitle = when {
                                                !isViewingMyGrade && studentProfile.fullName.isNotBlank() ->
                                                    "${studentProfile.fullName} · Voltar para minha grade"
                                                !isViewingMyGrade ->
                                                    "Voltar para minha grade"
                                                studentProfile.fullName.isNotBlank() ->
                                                    studentProfile.fullName
                                                else ->
                                                    "Sua grade atual"
                                            }
                                            Text(
                                                text = mySubtitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        if (isViewingMyGrade) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selecionado",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    text = "Voltar",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedFriendId = null
                                        showGradeSelectorMenu = false
                                    }
                                )

                                if (friendSchedules.isNotEmpty()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                    friendSchedules.forEach { friend ->
                                        val isSelected = activeFriend?.id == friend.id
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                FriendAvatarBadge(
                                                    friendName = friend.friendName,
                                                    avatarBase64 = friend.avatarBase64,
                                                    size = 28.dp
                                                )
                                            },
                                            text = {
                                                Column {
                                                    Text(
                                                        text = friend.friendName,
                                                        fontWeight = if (isSelected) {
                                                            FontWeight.Bold
                                                        } else {
                                                            FontWeight.Medium
                                                        }
                                                    )
                                                    val subtitleText = if (friend.courseName.isNotBlank()) {
                                                        "${friend.courseName} · Semestre ${friend.semester}"
                                                    } else {
                                                        "Semestre ${friend.semester}"
                                                    }
                                                    Text(
                                                        text = subtitleText,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            },
                                            trailingIcon = {
                                                IconButton(
                                                    onClick = {
                                                        showGradeSelectorMenu = false
                                                        friendPendingRemoval = friend
                                                    },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.DeleteOutline,
                                                        contentDescription = "Remover grade de ${friend.friendName}",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            },
                                            onClick = {
                                                selectedFriendId = friend.id
                                                showGradeSelectorMenu = false
                                            }
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )

                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = "Importar grade",
                                            color = accessibleTextColor(MaterialTheme.colorScheme.primary, isDark),
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    onClick = {
                                        showGradeSelectorMenu = false
                                        openUfopGradeLauncher.launch(
                                            arrayOf("*/*", "application/json", "application/octet-stream")
                                        )
                                    }
                                )

                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = "Colar texto da grade",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    onClick = {
                                        showGradeSelectorMenu = false
                                        friendNameInput = ""
                                        friendJsonInput = ""
                                        importFriendError = null
                                        showImportFriendDialog = true
                                    }
                                )
                            }
                        }

                        // Seletor 2: Semestre (ex.: 2026.2 ▾)
                        Box {
                            ExpressiveSelectorPill(
                                label = effectiveSemester,
                                isHighlighted = false,
                                visualHeight = toolbarVisualHeight,
                                onClick = { showSemesterSelectorMenu = true }
                            )

                            DropdownMenu(
                                expanded = showSemesterSelectorMenu,
                                onDismissRequest = { showSemesterSelectorMenu = false },
                                shape = RoundedCornerShape(24.dp),
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                availableSemesters.forEach { semesterOption ->
                                    val isSelected = semesterOption == effectiveSemester
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = semesterOption,
                                                fontWeight = if (isSelected) {
                                                    FontWeight.Bold
                                                } else {
                                                    FontWeight.Normal
                                                }
                                            )
                                        },
                                        trailingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Semestre ativo",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        onClick = {
                                            selectedSemester = semesterOption
                                            selectedFriendId = null
                                            showSemesterSelectorMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Botão Compartilhar (.ufopgrade)
                        Surface(
                            onClick = handleStartShareGrade,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .defaultMinSize(minHeight = toolbarVisualHeight),
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.40f))
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 11.dp,
                                    vertical = if (a11y.largeTouchTargets) 10.dp else 6.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = "Compartilhar grade",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Compartilhar",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Alternância persistida de orientação: Horizontal / Vertical
                        TimetableToolbarChip(
                            label = if (isLandscape) "Horizontal" else "Vertical",
                            icon = Icons.Outlined.ScreenRotation,
                            isActive = !isLandscape,
                            visualHeight = toolbarVisualHeight,
                            contentDescriptionText = if (isLandscape) {
                                "Orientação horizontal ativa. Toque para alternar para vertical"
                            } else {
                                "Orientação vertical ativa. Toque para alternar para horizontal"
                            },
                            onClickLabel = if (isLandscape) {
                                "Alternar para orientação vertical"
                            } else {
                                "Alternar para orientação horizontal"
                            },
                            onClick = {
                                val nextLandscape = !isLandscape
                                isLandscape = nextLandscape
                                CampusRepositoryHolder.saveTimetableIsLandscape(context, nextLandscape)
                            }
                        )

                        // Alternância de densidade: Normal / Compacto
                        TimetableToolbarChip(
                            label = if (isReducedMode) "Compacto" else "Normal",
                            icon = if (isReducedMode) Icons.Outlined.UnfoldMore else Icons.Outlined.UnfoldLess,
                            isActive = isReducedMode,
                            visualHeight = toolbarVisualHeight,
                            onClickLabel = if (isReducedMode) "Alternar para modo normal" else "Alternar para modo compacto",
                            onClick = { isReducedMode = !isReducedMode }
                        )

                        // Alternância de faixa de horários: Só meus horários / Grade completa (07:30–22:40)
                        TimetableToolbarChip(
                            label = if (showFullDayGrid) "Grade completa (07:30–22:40)" else "Só meus horários",
                            icon = Icons.Outlined.Schedule,
                            isActive = showFullDayGrid,
                            visualHeight = toolbarVisualHeight,
                            onClickLabel = if (showFullDayGrid) "Mostrar só meus horários" else "Mostrar grade completa de 07:30 às 22:40",
                            onClick = { showFullDayGrid = !showFullDayGrid }
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                }

            if (showShareDialog) {
                Dialog(
                    onDismissRequest = { if (!isExportingGrade) showShareDialog = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .widthIn(max = 620.dp),
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 6.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Compartilhar minha grade (.ufopgrade)",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Inclui seu nome, curso, foto em miniatura e horários do semestre $effectiveSemester ($pendingSuggestedFilename)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (isExportingGrade) return@Button
                                        coroutineScope.launch {
                                            isExportingGrade = true
                                            val payload = pendingExportJson.ifBlank {
                                                repository.exportMyGradeBundleJson(effectiveSemester)
                                            }
                                            val saveResult = runCatching {
                                                withContext(Dispatchers.IO) {
                                                    GradeExportStorage.saveGradeBundle(
                                                        context = context,
                                                        repository = repository,
                                                        filename = pendingSuggestedFilename,
                                                        jsonPayload = payload
                                                    )
                                                }
                                            }
                                            isExportingGrade = false
                                            showShareDialog = false
                                            saveResult.onSuccess { saved ->
                                                feedbackBannerMessage =
                                                    "Arquivo salvo em ${saved.displayPath}"
                                            }.onFailure { err ->
                                                feedbackBannerMessage = err.message
                                                    ?: "Erro ao salvar arquivo .ufopgrade na pasta padrão."
                                            }
                                        }
                                    },
                                    enabled = !isExportingGrade,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    if (isExportingGrade) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Folder,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Salvar na pasta padrão",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            if (isExportingGrade) return@FilledTonalButton
                                            coroutineScope.launch {
                                                isExportingGrade = true
                                                val payload = pendingExportJson.ifBlank {
                                                    repository.exportMyGradeBundleJson(effectiveSemester)
                                                }
                                                val shareUriResult = runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        GradeExportStorage.createShareableFileUri(
                                                            context = context,
                                                            filename = pendingSuggestedFilename,
                                                            jsonPayload = payload
                                                        )
                                                    }
                                                }
                                                isExportingGrade = false
                                                shareUriResult.onSuccess { shareUri ->
                                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/octet-stream"
                                                        putExtra(Intent.EXTRA_SUBJECT, pendingSuggestedFilename)
                                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                                        clipData = ClipData.newRawUri(pendingSuggestedFilename, shareUri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    val chooser = Intent.createChooser(
                                                        sendIntent,
                                                        "Enviar $pendingSuggestedFilename"
                                                    ).apply {
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    runCatching {
                                                        context.startActivity(chooser)
                                                    }.onSuccess {
                                                        showShareDialog = false
                                                        feedbackBannerMessage =
                                                            "Arquivo $pendingSuggestedFilename pronto para envio!"
                                                    }.onFailure { err ->
                                                        showShareDialog = false
                                                        feedbackBannerMessage = err.message
                                                            ?: "Não foi possível abrir o compartilhamento do arquivo."
                                                    }
                                                }.onFailure { err ->
                                                    showShareDialog = false
                                                    feedbackBannerMessage = err.message
                                                        ?: "Erro ao preparar arquivo .ufopgrade para envio."
                                                }
                                            }
                                        },
                                        enabled = !isExportingGrade,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Share,
                                            contentDescription = null,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Enviar arquivo",
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val payload = pendingExportJson.ifBlank {
                                                repository.exportMyGradeBundleJson(effectiveSemester)
                                            }
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            clipboard?.setPrimaryClip(
                                                ClipData.newPlainText("Grade Acadêmica (.ufopgrade)", payload)
                                            )
                                            showShareDialog = false
                                            feedbackBannerMessage =
                                                "Código .ufopgrade copiado para a área de transferência!"
                                        },
                                        enabled = !isExportingGrade,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Copiar JSON",
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { showShareDialog = false },
                                    enabled = !isExportingGrade,
                                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget)
                                ) {
                                    Text(
                                        text = "Fechar",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showImportFriendDialog) {
                Dialog(
                    onDismissRequest = { showImportFriendDialog = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .widthIn(max = 620.dp)
                            .heightIn(max = 520.dp)
                            .imePadding(),
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 6.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Colar texto da grade",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Cole o texto compartilhado de um amigo (nome, curso e foto são lidos automaticamente) ou informe o nome manualmente.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedTextField(
                                value = friendNameInput,
                                onValueChange = {
                                    friendNameInput = it
                                    importFriendError = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Nome do amigo (opcional para .ufopgrade)") },
                                placeholder = { Text("Ex.: Lucas Mendes") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val pasted = clipboard?.primaryClip
                                            ?.takeIf { it.itemCount > 0 }
                                            ?.getItemAt(0)
                                            ?.coerceToText(context)
                                            ?.toString()
                                            ?.trim()
                                            .orEmpty()
                                        if (pasted.isNotEmpty()) {
                                            friendJsonInput = pasted
                                            importFriendError = null
                                        } else {
                                            importFriendError = "A área de transferência está vazia."
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentPaste,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Colar texto",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        friendJsonInput = ""
                                        importFriendError = null
                                    },
                                    enabled = friendJsonInput.isNotEmpty(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Limpar",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = friendJsonInput,
                                onValueChange = {
                                    friendJsonInput = it
                                    importFriendError = null
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 140.dp, max = 210.dp),
                                label = { Text("Conteúdo .ufopgrade ou JSON") },
                                placeholder = { Text("{\"format\":\"ufopgrade-v1\",\"friendName\":\"...\"}") },
                                minLines = 6,
                                maxLines = 10,
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )

                            if (friendJsonInput.isNotBlank()) {
                                val trimmedPayload = friendJsonInput.trim()
                                val looksLikeJson = trimmedPayload.startsWith("{") && trimmedPayload.endsWith("}")
                                val statusColor = if (looksLikeJson) {
                                    StatusAttendingGreen
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                                val statusText = if (looksLikeJson) {
                                    "✓ Conteúdo carregado (${friendJsonInput.length} caracteres · formato JSON detectado)"
                                } else {
                                    "✓ Conteúdo carregado (${friendJsonInput.length} caracteres · confira se o JSON está completo)"
                                }
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = statusColor.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            if (importFriendError != null) {
                                Text(
                                    text = importFriendError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accessibleTextColor(StatusAbsentRed, isDark),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { showImportFriendDialog = false },
                                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget)
                                ) {
                                    Text(
                                        text = "Cancelar",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val cleanName = friendNameInput.trim()
                                        val cleanJson = friendJsonInput.trim()
                                        if (cleanJson.isEmpty()) {
                                            importFriendError = "Cole o texto da grade compartilhada."
                                        } else {
                                            runCatching {
                                                repository.importFriendScheduleJson(
                                                    friendName = cleanName,
                                                    jsonPayload = cleanJson
                                                )
                                            }.onSuccess { imported ->
                                                selectedFriendId = imported.id
                                                feedbackBannerMessage =
                                                    "Grade de ${imported.friendName} (${imported.semester}) importada!"
                                                showImportFriendDialog = false
                                            }.onFailure { err ->
                                                importFriendError = err.message
                                                    ?: "Não foi possível importar o texto informado."
                                            }
                                        }
                                    },
                                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        text = "Importar",
                                        maxLines = 1,
                                        softWrap = false,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // IHC-02: Confirmação antes de remover grade de um amigo
            friendPendingRemoval?.let { friendToDelete ->
                AlertDialog(
                    onDismissRequest = { friendPendingRemoval = null },
                    title = {
                        Text(
                            text = "Remover grade de ${friendToDelete.friendName}?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "A grade compartilhada de ${friendToDelete.friendName} (${friendToDelete.semester}) será removida da sua lista. Você poderá importá-la novamente quando quiser.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { friendPendingRemoval = null },
                            modifier = Modifier.defaultMinSize(minHeight = minTouchTarget)
                        ) {
                            Text(
                                text = "Cancelar",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (selectedFriendId == friendToDelete.id) {
                                    selectedFriendId = null
                                }
                                repository.removeFriendSchedule(friendToDelete.id)
                                feedbackBannerMessage = "Grade de ${friendToDelete.friendName} removida."
                                friendPendingRemoval = null
                            },
                            modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusAbsentRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "Remover",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                )
            }

            // IHC-05: Diálogo de detalhes completos ao tocar em uma célula ocupada da grade
            selectedCellDetail?.let { detailInfo ->
                val accentColor = parseHexColor(detailInfo.cell.colorHex)
                val readableAccent = accessibleTextColor(accentColor, isDark)
                AlertDialog(
                    onDismissRequest = { selectedCellDetail = null },
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(accentColor)
                                )
                                Text(
                                    text = buildString {
                                        append(detailInfo.cell.departmentName.ifBlank { "UFOP" })
                                        if (detailInfo.cell.code.isNotBlank()) {
                                            append(" · ${detailInfo.cell.code}")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = readableAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = detailInfo.cell.courseName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "${detailInfo.dayFullName} · ${detailInfo.startTime} às ${detailInfo.endTime}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Local: ${detailInfo.fullLocation}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (detailInfo.professor.isNotBlank()) {
                                Text(
                                    text = "Professor(a): ${detailInfo.professor}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { selectedCellDetail = null },
                            modifier = Modifier.defaultMinSize(minHeight = minTouchTarget)
                        ) {
                            Text(
                                text = "Fechar",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                )
            }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(verticalScrollState)
                ) {
                    feedbackBannerMessage?.let { message ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clickable { feedbackBannerMessage = null },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "✕",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    val rowSpacing = if (isReducedMode) 4.dp else 8.dp
                    val colSpacing = if (isReducedMode) 5.dp else 8.dp
                    val gridPadding = if (isReducedMode) 8.dp else 12.dp
                    val headerRowHeight = if (isReducedMode) REDUCED_HEADER_ROW_HEIGHT else HEADER_ROW_HEIGHT
                    val cellRowHeight = if (isReducedMode) {
                        if (a11y.largeTouchTargets) 56.dp else REDUCED_CELL_ROW_HEIGHT
                    } else {
                        if (a11y.largeTouchTargets) 84.dp else CELL_ROW_HEIGHT
                    }
                    val timeColWidth = if (isReducedMode) REDUCED_TIME_COLUMN_WIDTH else TIME_COLUMN_WIDTH

                    // Matriz da Grade: em Paisagem distribui SEG..SÁB em weight(1f); em Retrato permite rolagem horizontal confortável
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(if (isReducedMode) 16.dp else 22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(gridPadding),
                            horizontalArrangement = Arrangement.spacedBy(colSpacing)
                        ) {
                            // Coluna fixa à esquerda para faixas de horário (texto >= 11.sp mesmo em modo compacto)
                            Column(
                                modifier = Modifier.width(timeColWidth),
                                verticalArrangement = Arrangement.spacedBy(rowSpacing)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(headerRowHeight),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = if (isReducedMode) "HORA" else "HORÁRIO",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp
                                        ),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                displayRows.forEach { row ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(cellRowHeight),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (isReducedMode) {
                                            Column(verticalArrangement = Arrangement.Center) {
                                                Text(
                                                    text = row.startTime,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 11.sp,
                                                        lineHeight = 13.sp
                                                    ),
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                                Text(
                                                    text = row.endTime,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 11.sp,
                                                        lineHeight = 13.sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        } else {
                                            Column {
                                                Text(
                                                    text = row.startTime,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = row.endTime,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 6 colunas da semana (SEG..SÁB)
                            val daysRowModifier = if (isLandscape) {
                                Modifier.weight(1f)
                            } else {
                                Modifier
                                    .weight(1f)
                                    .horizontalScroll(horizontalGridScrollState)
                            }
                            Row(
                                modifier = daysRowModifier,
                                horizontalArrangement = Arrangement.spacedBy(colSpacing)
                            ) {
                                displayDays.forEach { (dayNumber, dayShort) ->
                                    val dayColumnModifier = if (isLandscape) {
                                        Modifier.weight(1f)
                                    } else {
                                        Modifier.width(if (isReducedMode) 82.dp else 112.dp)
                                    }
                                    val fullDayName = fullDayNamePt(dayNumber)
                                    Column(
                                        modifier = dayColumnModifier,
                                        verticalArrangement = Arrangement.spacedBy(rowSpacing)
                                    ) {
                                        // Header do dia (SEG, TER, QUA, QUI, SEX, SÁB)
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(headerRowHeight)
                                                .semantics { contentDescription = fullDayName },
                                            shape = RoundedCornerShape(if (isReducedMode) 8.dp else 12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = dayShort,
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontSize = if (isReducedMode) 11.sp else 12.sp
                                                    ),
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        // Células para cada faixa de horário neste dia
                                        displayRows.forEach { row ->
                                            val cell = row.cellsByDay[dayNumber]
                                            if (cell != null) {
                                                OccupiedTimetableCellCard(
                                                    cell = cell,
                                                    dayFullName = fullDayName,
                                                    startTime = row.startTime,
                                                    endTime = row.endTime,
                                                    cellHeight = cellRowHeight,
                                                    isReducedMode = isReducedMode,
                                                    isDark = isDark,
                                                    onClick = {
                                                        openCellDetail(cell, dayNumber, row.startTime, row.endTime)
                                                    }
                                                )
                                            } else {
                                                EmptyTimetableCellCard(
                                                    cellHeight = cellRowHeight,
                                                    isReducedMode = isReducedMode
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Legenda de departamentos sempre visível (inclusive no modo compacto/reduzido)
                    Spacer(modifier = Modifier.height(if (isReducedMode) 10.dp else 14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FlowRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            timetable.legend.forEach { dept: DepartmentColorOption ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(parseHexColor(dept.colorHex))
                                    )
                                    Text(
                                        text = dept.name,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = if (isReducedMode) 11.sp else 12.sp
                                        ),
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "Semestre ${timetable.semester}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = if (isReducedMode) 11.sp else 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Card inferior do amigo ao rolar a grade para baixo (Foto circular + Nome + Curso)
                    if (activeFriend != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        FriendScheduleFooterCard(
                            friend = activeFriend,
                            semester = timetable.semester,
                            minTouchTarget = minTouchTarget,
                            onBackToMyGrade = { selectedFriendId = null }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TimetableToolbarChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    visualHeight: Dp,
    onClickLabel: String,
    contentDescriptionText: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = visualHeight)
            .clip(RoundedCornerShape(50))
            .semantics(mergeDescendants = true) {
                selected = isActive
                role = Role.Button
                if (contentDescriptionText != null) {
                    contentDescription = contentDescriptionText
                }
            }
            .clickable(onClickLabel = onClickLabel, onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 11.dp,
                vertical = if (visualHeight >= 48.dp) 10.dp else 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun FriendAvatarBadge(
    friendName: String,
    avatarBase64: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    avatarFilePath: String? = null
) {
    val cachedBitmap = remember(avatarBase64, avatarFilePath) {
        avatarBase64?.trim()?.takeIf { it.isNotEmpty() }?.let { clean ->
            friendAvatarBitmapCache.get(clean.hashCode())
        } ?: avatarFilePath?.trim()?.takeIf { it.isNotEmpty() }?.let { cleanPath ->
            val file = File(cleanPath)
            if (file.exists()) localAvatarBitmapCache.get("${cleanPath}:${file.lastModified()}") else null
        }
    }
    val decodedBitmap by produceState<Bitmap?>(
        initialValue = cachedBitmap,
        key1 = avatarBase64,
        key2 = avatarFilePath
    ) {
        if (value != null) return@produceState
        if (avatarBase64.isNullOrBlank() && avatarFilePath.isNullOrBlank()) return@produceState
        value = withContext(Dispatchers.IO) {
            if (!avatarBase64.isNullOrBlank()) {
                decodeAvatarBase64(avatarBase64)
            } else {
                decodeLocalAvatarFile(avatarFilePath)
            }
        }
    }
    val imageBitmap = remember(decodedBitmap) {
        decodedBitmap?.asImageBitmap()
    }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "Foto de $friendName",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), CircleShape)
        )
    } else {
        Surface(
            modifier = modifier.size(size),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = buildInitials(friendName),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun FriendScheduleFooterCard(
    friend: FriendScheduleInfo,
    semester: String,
    minTouchTarget: Dp,
    onBackToMyGrade: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FriendAvatarBadge(
                    friendName = friend.friendName,
                    avatarBase64 = friend.avatarBase64,
                    size = 48.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = friend.friendName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = friend.courseName.ifBlank { "Estudante" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Grade compartilhada · Semestre $semester",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            FilledTonalButton(
                onClick = onBackToMyGrade,
                modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Minha grade",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ExpressiveSelectorPill(
    label: String,
    isHighlighted: Boolean,
    leadingAvatarName: String? = null,
    leadingAvatarBase64: String? = null,
    leadingAvatarFilePath: String? = null,
    visualHeight: Dp = 36.dp,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = visualHeight),
        shape = RoundedCornerShape(50),
        color = if (isHighlighted) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.70f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isHighlighted) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = if (visualHeight >= 48.dp) 10.dp else 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!leadingAvatarName.isNullOrBlank()) {
                FriendAvatarBadge(
                    friendName = leadingAvatarName,
                    avatarBase64 = leadingAvatarBase64,
                    size = 20.dp,
                    avatarFilePath = leadingAvatarFilePath
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OccupiedTimetableCellCard(
    cell: TimetableCell,
    dayFullName: String,
    startTime: String,
    endTime: String,
    cellHeight: Dp,
    isReducedMode: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val accentColor = remember(cell.colorHex) { parseHexColor(cell.colorHex) }
    val readableAccent = remember(accentColor, isDark) { accessibleTextColor(accentColor, isDark) }
    val cleanLocation = remember(cell.shortLocation) { formatTimetableShortLocation(cell.shortLocation) }
    val classCountBadge = remember(startTime, endTime) {
        if (startTime.isNotBlank() && endTime.isNotBlank()) {
            formatBlockClassCountBadge(startTime, endTime)
        } else {
            null
        }
    }
    val cellDescription = remember(cell, dayFullName, startTime, endTime, cleanLocation) {
        buildString {
            append(cell.courseName)
            if (cell.code.isNotBlank()) append(", código ${cell.code}")
            append(", $dayFullName, $startTime às $endTime")
            append(", local $cleanLocation")
            append(". Toque para ver detalhes")
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(cellHeight)
            .clip(RoundedCornerShape(if (isReducedMode) 10.dp else 14.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = cellDescription
                role = Role.Button
            }
            .clickable(
                onClickLabel = "Ver detalhes de ${cell.courseName}",
                onClick = onClick
            ),
        shape = RoundedCornerShape(if (isReducedMode) 10.dp else 14.dp),
        color = accentColor.copy(alpha = if (isDark) 0.18f else 0.12f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Barra lateral na cor padronizada do departamento
            Box(
                modifier = Modifier
                    .width(if (isReducedMode) 4.dp else 5.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )

            if (isReducedMode) {
                val shortCode = cell.code.ifBlank { cell.courseName.take(10) }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = shortCode,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            lineHeight = 13.sp
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = cleanLocation,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = cell.courseName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = cleanLocation,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (classCountBadge != null) {
                            Text(
                                text = classCountBadge,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = readableAccent,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTimetableCellCard(
    cellHeight: Dp,
    isReducedMode: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(cellHeight),
        shape = RoundedCornerShape(if (isReducedMode) 10.dp else 14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "—",
                style = if (isReducedMode) {
                    MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
                textAlign = TextAlign.Center
            )
        }
    }
}
