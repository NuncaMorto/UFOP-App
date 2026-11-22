package br.ufop.campus.core.ffi

import android.content.Context
import android.content.SharedPreferences
import br.ufop.campus.core.curriculum.UfopRoomAllocationCatalog
import br.ufop.campus.core.security.AppLockManager
import br.ufop.campus.core.security.SecurePdfProcessor
import br.ufop.campus.ui.theme.CampusAccentColor
import br.ufop.campus.ui.theme.CampusAccessibilitySettings
import br.ufop.campus.widget.VidaAcademicaWidgetProvider
import java.io.File

/**
 * Holder singleton thread-safe para o [CampusCoreRepository] e preferências síncronas de tema/cor,
 * acessibilidade, tour guiado, versão de lançamento e limpeza total de dados de sessão.
 * Garante única instância compartilhada entre UI e integrações, além de notificar o widget
 * automaticamente após cada gravação persistida em disco.
 */
object CampusRepositoryHolder {

    const val PREFS_NAME = "vida_academica_prefs"
    const val CURRENT_APP_VERSION = "1.9.8"
    const val KEY_DARK_THEME_MODE = "dark_theme_mode"
    const val KEY_ACCENT_COLOR = "accent_color_key"
    const val KEY_GUIDED_TOUR_COMPLETED = "guided_tour_completed"
    const val KEY_LAST_RELEASE_VERSION = "last_seen_release_version"
    const val KEY_A11Y_SCREEN_READER = "a11y_screen_reader_optimized"
    const val KEY_A11Y_FONT_SCALE = "a11y_font_scale_multiplier"
    const val KEY_A11Y_HIGH_CONTRAST = "a11y_high_contrast_mode"
    const val KEY_A11Y_REDUCE_MOTION = "a11y_reduce_motion"
    const val KEY_A11Y_LARGE_TOUCH_TARGETS = "a11y_large_touch_targets"
    const val KEY_A11Y_BOLD_TEXT = "a11y_bold_text_boost"
    const val KEY_TIMETABLE_ORIENTATION = "timetable_orientation_pref"
    const val KEY_DISCIPLINES_LAYOUT_MODE = "disciplines_layout_mode"
    const val KEY_DYNAMIC_GRID_ORIENTATION = "dynamic_grid_orientation_pref"

    @Volatile
    private var instance: CampusCoreRepository? = null

    fun getOrCreate(context: Context): CampusCoreRepository {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: run {
                val appContext = context.applicationContext
                val created = CampusEngineBridgeRepository(
                    dbPath = File(appContext.filesDir, "campus_ufop.db").absolutePath,
                    stateFile = File(appContext.filesDir, "campus_ufop_state.json"),
                    onStatePersisted = {
                        runCatching {
                            VidaAcademicaWidgetProvider.requestWidgetUpdate(appContext)
                        }
                    }
                )
                runCatching {
                    val index = UfopRoomAllocationCatalog.loadFromAssets(appContext)
                    created.applyRoomAllocationIndex(index, enrichExisting = true)
                }
                instance = created
                created
            }
        }
    }

    internal fun loadSavedDarkThemeFromPrefs(
        prefs: SharedPreferences?,
        repository: CampusCoreRepository,
        systemDark: Boolean
    ): Boolean {
        val prefsMode = runCatching {
            prefs?.getString(KEY_DARK_THEME_MODE, null)
        }.getOrNull()?.trim()?.uppercase()

        when (prefsMode) {
            "DARK" -> return true
            "LIGHT" -> return false
        }

        val repoMode = runCatching { repository.getDarkThemePreference() }
            .getOrNull()
            ?.trim()
            ?.uppercase()

        return when (repoMode) {
            "DARK" -> true
            "LIGHT" -> false
            else -> systemDark
        }
    }

    fun loadSavedDarkTheme(
        context: Context,
        repository: CampusCoreRepository,
        systemDark: Boolean
    ): Boolean {
        val prefs = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
        return loadSavedDarkThemeFromPrefs(prefs, repository, systemDark)
    }

    fun loadSavedDarkTheme(context: Context, systemDark: Boolean): Boolean =
        loadSavedDarkTheme(context, getOrCreate(context), systemDark)

    internal fun saveDarkThemeToPrefs(
        prefs: SharedPreferences?,
        repository: CampusCoreRepository,
        isDark: Boolean,
        onWidgetUpdate: () -> Unit = {}
    ) {
        val mode = if (isDark) "DARK" else "LIGHT"
        runCatching {
            prefs?.edit()
                ?.putString(KEY_DARK_THEME_MODE, mode)
                ?.commit()
        }
        repository.setDarkThemePreference(mode)
        runCatching { onWidgetUpdate() }
    }

    fun saveDarkTheme(
        context: Context,
        repository: CampusCoreRepository,
        isDark: Boolean
    ) {
        val appContext = context.applicationContext
        val prefs = runCatching {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
        saveDarkThemeToPrefs(
            prefs = prefs,
            repository = repository,
            isDark = isDark,
            onWidgetUpdate = {
                VidaAcademicaWidgetProvider.requestWidgetUpdate(appContext)
            }
        )
    }

    internal fun loadSavedAccentColorFromPrefs(
        prefs: SharedPreferences?,
        repository: CampusCoreRepository
    ): String {
        val rawPrefs = runCatching {
            prefs?.getString(KEY_ACCENT_COLOR, null)
        }.getOrNull()?.trim()

        if (!rawPrefs.isNullOrEmpty()) {
            val matched = CampusAccentColor.entries.firstOrNull {
                it.storageKey.equals(rawPrefs, ignoreCase = true)
            }
            if (matched != null) {
                return matched.storageKey
            }
        }

        val repoAccent = runCatching { repository.getAccentColorPreference() }
            .getOrNull()
            ?.trim()

        return CampusAccentColor.fromKey(repoAccent).storageKey
    }

    fun loadSavedAccentColor(
        context: Context,
        repository: CampusCoreRepository
    ): String {
        val prefs = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
        return loadSavedAccentColorFromPrefs(prefs, repository)
    }

    fun loadSavedAccentColor(context: Context): String =
        loadSavedAccentColor(context, getOrCreate(context))

    internal fun saveAccentColorToPrefs(
        prefs: SharedPreferences?,
        repository: CampusCoreRepository,
        accentKey: String,
        onWidgetUpdate: () -> Unit = {}
    ): String {
        val normalized = CampusAccentColor.fromKey(accentKey).storageKey
        runCatching {
            prefs?.edit()
                ?.putString(KEY_ACCENT_COLOR, normalized)
                ?.commit()
        }
        repository.setAccentColorPreference(normalized)
        runCatching { onWidgetUpdate() }
        return normalized
    }

    fun saveAccentColor(
        context: Context,
        repository: CampusCoreRepository,
        accentKey: String
    ) {
        val appContext = context.applicationContext
        val prefs = runCatching {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
        saveAccentColorToPrefs(
            prefs = prefs,
            repository = repository,
            accentKey = accentKey,
            onWidgetUpdate = {
                VidaAcademicaWidgetProvider.requestWidgetUpdate(appContext)
            }
        )
    }

    internal fun loadAccessibilitySettingsFromPrefs(
        prefs: SharedPreferences?,
        repository: CampusCoreRepository
    ): CampusAccessibilitySettings {
        val repoSettings = runCatching { repository.getAccessibilitySettings() }
            .getOrElse { CampusAccessibilitySettings() }
        if (prefs == null) return repoSettings

        val hasAnyKey = runCatching {
            prefs.contains(KEY_A11Y_SCREEN_READER) ||
                prefs.contains(KEY_A11Y_FONT_SCALE) ||
                prefs.contains(KEY_A11Y_HIGH_CONTRAST) ||
                prefs.contains(KEY_A11Y_REDUCE_MOTION) ||
                prefs.contains(KEY_A11Y_LARGE_TOUCH_TARGETS) ||
                prefs.contains(KEY_A11Y_BOLD_TEXT)
        }.getOrDefault(false)
        if (!hasAnyKey) return repoSettings

        val rawScale = runCatching {
            if (prefs.contains(KEY_A11Y_FONT_SCALE)) {
                prefs.getFloat(KEY_A11Y_FONT_SCALE, repoSettings.fontScaleMultiplier)
            } else {
                repoSettings.fontScaleMultiplier
            }
        }.getOrDefault(repoSettings.fontScaleMultiplier)
        val safeScale = if (rawScale.isFinite()) rawScale.coerceIn(0.85f, 2.0f) else 1.0f

        return CampusAccessibilitySettings(
            screenReaderOptimized = runCatching {
                if (prefs.contains(KEY_A11Y_SCREEN_READER)) {
                    prefs.getBoolean(KEY_A11Y_SCREEN_READER, repoSettings.screenReaderOptimized)
                } else {
                    repoSettings.screenReaderOptimized
                }
            }.getOrDefault(repoSettings.screenReaderOptimized),
            fontScaleMultiplier = safeScale,
            highContrastMode = runCatching {
                if (prefs.contains(KEY_A11Y_HIGH_CONTRAST)) {
                    prefs.getBoolean(KEY_A11Y_HIGH_CONTRAST, repoSettings.highContrastMode)
                } else {
                    repoSettings.highContrastMode
                }
            }.getOrDefault(repoSettings.highContrastMode),
            reduceMotion = runCatching {
                if (prefs.contains(KEY_A11Y_REDUCE_MOTION)) {
                    prefs.getBoolean(KEY_A11Y_REDUCE_MOTION, repoSettings.reduceMotion)
                } else {
                    repoSettings.reduceMotion
                }
            }.getOrDefault(repoSettings.reduceMotion),
            largeTouchTargets = runCatching {
                if (prefs.contains(KEY_A11Y_LARGE_TOUCH_TARGETS)) {
                    prefs.getBoolean(KEY_A11Y_LARGE_TOUCH_TARGETS, repoSettings.largeTouchTargets)
                } else {
                    repoSettings.largeTouchTargets
                }
            }.getOrDefault(repoSettings.largeTouchTargets),
            boldTextBoost = runCatching {
                if (prefs.contains(KEY_A11Y_BOLD_TEXT)) {
                    prefs.getBoolean(KEY_A11Y_BOLD_TEXT, repoSettings.boldTextBoost)
                } else {
                    repoSettings.boldTextBoost
                }
            }.getOrDefault(repoSettings.boldTextBoost)
        )
    }

    fun loadAccessibilitySettings(
        context: Context,
        repository: CampusCoreRepository
    ): CampusAccessibilitySettings {
        val prefs = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
        return loadAccessibilitySettingsFromPrefs(prefs, repository)
    }

    fun loadAccessibilitySettings(context: Context): CampusAccessibilitySettings =
        loadAccessibilitySettings(context, getOrCreate(context))

    internal fun saveAccessibilitySettingsToPrefs(
        prefs: SharedPreferences?,
        repository: CampusCoreRepository,
        settings: CampusAccessibilitySettings,
        onWidgetUpdate: () -> Unit = {}
    ): CampusAccessibilitySettings {
        val safeScale = if (settings.fontScaleMultiplier.isFinite()) {
            settings.fontScaleMultiplier.coerceIn(0.85f, 2.0f)
        } else {
            1.0f
        }
        val normalized = settings.copy(fontScaleMultiplier = safeScale)
        runCatching {
            prefs?.edit()
                ?.putBoolean(KEY_A11Y_SCREEN_READER, normalized.screenReaderOptimized)
                ?.putFloat(KEY_A11Y_FONT_SCALE, normalized.fontScaleMultiplier)
                ?.putBoolean(KEY_A11Y_HIGH_CONTRAST, normalized.highContrastMode)
                ?.putBoolean(KEY_A11Y_REDUCE_MOTION, normalized.reduceMotion)
                ?.putBoolean(KEY_A11Y_LARGE_TOUCH_TARGETS, normalized.largeTouchTargets)
                ?.putBoolean(KEY_A11Y_BOLD_TEXT, normalized.boldTextBoost)
                ?.commit()
        }
        repository.setAccessibilitySettings(normalized)
        runCatching { onWidgetUpdate() }
        return normalized
    }

    fun saveAccessibilitySettings(
        context: Context,
        repository: CampusCoreRepository,
        settings: CampusAccessibilitySettings
    ) {
        val appContext = context.applicationContext
        val prefs = runCatching {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
        saveAccessibilitySettingsToPrefs(
            prefs = prefs,
            repository = repository,
            settings = settings,
            onWidgetUpdate = {
                VidaAcademicaWidgetProvider.requestWidgetUpdate(appContext)
            }
        )
    }

    fun saveAccessibilitySettings(context: Context, settings: CampusAccessibilitySettings) =
        saveAccessibilitySettings(context, getOrCreate(context), settings)

    internal fun isGuidedTourCompletedFromPrefs(
        prefs: SharedPreferences?,
        repository: CampusCoreRepository
    ): Boolean {
        val hasPref = runCatching { prefs?.contains(KEY_GUIDED_TOUR_COMPLETED) == true }.getOrDefault(false)
        if (hasPref && prefs != null) {
            return runCatching { prefs.getBoolean(KEY_GUIDED_TOUR_COMPLETED, false) }.getOrDefault(false)
        }
        return runCatching { repository.isGuidedTourCompleted() }.getOrDefault(false)
    }

    fun isGuidedTourCompleted(
        context: Context,
        repository: CampusCoreRepository
    ): Boolean {
        val prefs = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
        return isGuidedTourCompletedFromPrefs(prefs, repository)
    }

    fun isGuidedTourCompleted(context: Context): Boolean =
        isGuidedTourCompleted(context, getOrCreate(context))

    internal fun setGuidedTourCompletedToPrefs(
        prefs: SharedPreferences?,
        repository: CampusCoreRepository,
        completed: Boolean
    ) {
        runCatching {
            prefs?.edit()
                ?.putBoolean(KEY_GUIDED_TOUR_COMPLETED, completed)
                ?.commit()
        }
        repository.setGuidedTourCompleted(completed)
    }

    fun setGuidedTourCompleted(
        context: Context,
        repository: CampusCoreRepository,
        completed: Boolean
    ) {
        val prefs = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
        setGuidedTourCompletedToPrefs(prefs, repository, completed)
    }

    fun setGuidedTourCompleted(context: Context, completed: Boolean) =
        setGuidedTourCompleted(context, getOrCreate(context), completed)

    internal fun getLastSeenReleaseVersionFromPrefs(
        prefs: SharedPreferences?,
        repository: CampusCoreRepository
    ): String {
        val fromPrefs = runCatching {
            prefs?.getString(KEY_LAST_RELEASE_VERSION, null)
        }.getOrNull()?.trim()
        if (!fromPrefs.isNullOrEmpty()) {
            return fromPrefs
        }
        return runCatching { repository.getLastSeenReleaseVersion() }.getOrDefault("")
    }

    fun getLastSeenReleaseVersion(
        context: Context,
        repository: CampusCoreRepository
    ): String {
        val prefs = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
        return getLastSeenReleaseVersionFromPrefs(prefs, repository)
    }

    fun getLastSeenReleaseVersion(context: Context): String =
        getLastSeenReleaseVersion(context, getOrCreate(context))

    internal fun setLastSeenReleaseVersionToPrefs(
        prefs: SharedPreferences?,
        repository: CampusCoreRepository,
        version: String
    ) {
        val cleanVersion = version.trim()
        runCatching {
            prefs?.edit()
                ?.putString(KEY_LAST_RELEASE_VERSION, cleanVersion)
                ?.commit()
        }
        repository.setLastSeenReleaseVersion(cleanVersion)
    }

    fun setLastSeenReleaseVersion(
        context: Context,
        repository: CampusCoreRepository,
        version: String
    ) {
        val prefs = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
        setLastSeenReleaseVersionToPrefs(prefs, repository, version)
    }

    fun setLastSeenReleaseVersion(context: Context, version: String) =
        setLastSeenReleaseVersion(context, getOrCreate(context), version)

    internal fun loadTimetableIsLandscapeFromPrefs(
        prefs: SharedPreferences?
    ): Boolean {
        if (prefs == null) return true
        val rawString = runCatching {
            prefs.getString(KEY_TIMETABLE_ORIENTATION, null)
        }.getOrNull()?.trim()?.uppercase()
        return when (rawString) {
            "PORTRAIT" -> false
            "LANDSCAPE" -> true
            else -> runCatching {
                if (prefs.contains(KEY_TIMETABLE_ORIENTATION)) {
                    prefs.getBoolean(KEY_TIMETABLE_ORIENTATION, true)
                } else {
                    true
                }
            }.getOrDefault(true)
        }
    }

    fun loadTimetableIsLandscape(context: Context): Boolean {
        val prefs = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
        return loadTimetableIsLandscapeFromPrefs(prefs)
    }

    internal fun saveTimetableIsLandscapeToPrefs(
        prefs: SharedPreferences?,
        isLandscape: Boolean
    ): Boolean {
        val mode = if (isLandscape) "LANDSCAPE" else "PORTRAIT"
        runCatching {
            prefs?.edit()
                ?.putString(KEY_TIMETABLE_ORIENTATION, mode)
                ?.commit()
        }
        return isLandscape
    }

    fun saveTimetableIsLandscape(context: Context, isLandscape: Boolean) {
        val prefs = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
        saveTimetableIsLandscapeToPrefs(prefs, isLandscape)
    }

    internal fun loadDisciplinesLayoutModeFromPrefs(
        prefs: SharedPreferences?
    ): String {
        val raw = runCatching {
            prefs?.getString(KEY_DISCIPLINES_LAYOUT_MODE, null)
        }.getOrNull()?.trim()?.uppercase()
        return if (raw == "LIST") "LIST" else "GRID"
    }

    fun loadDisciplinesLayoutMode(context: Context): String {
        val prefs = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
        return loadDisciplinesLayoutModeFromPrefs(prefs)
    }

    internal fun saveDisciplinesLayoutModeToPrefs(
        prefs: SharedPreferences?,
        mode: String
    ): String {
        val normalized = if (mode.trim().equals("LIST", ignoreCase = true)) "LIST" else "GRID"
        runCatching {
            prefs?.edit()
                ?.putString(KEY_DISCIPLINES_LAYOUT_MODE, normalized)
                ?.commit()
        }
        return normalized
    }

    fun saveDisciplinesLayoutMode(context: Context, mode: String): String {
        val prefs = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
        return saveDisciplinesLayoutModeToPrefs(prefs, mode)
    }

    internal fun loadDynamicGridIsHorizontalFromPrefs(
        prefs: SharedPreferences?
    ): Boolean {
        if (prefs == null) return true
        val rawString = runCatching {
            prefs.getString(KEY_DYNAMIC_GRID_ORIENTATION, null)
        }.getOrNull()?.trim()?.uppercase()
        return when (rawString) {
            "VERTICAL" -> false
            "HORIZONTAL" -> true
            else -> runCatching {
                if (prefs.contains(KEY_DYNAMIC_GRID_ORIENTATION)) {
                    prefs.getBoolean(KEY_DYNAMIC_GRID_ORIENTATION, true)
                } else {
                    true
                }
            }.getOrDefault(true)
        }
    }

    fun loadDynamicGridIsHorizontal(context: Context): Boolean {
        val prefs = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
        return loadDynamicGridIsHorizontalFromPrefs(prefs)
    }

    internal fun saveDynamicGridIsHorizontalToPrefs(
        prefs: SharedPreferences?,
        isHorizontal: Boolean
    ): Boolean {
        val mode = if (isHorizontal) "HORIZONTAL" else "VERTICAL"
        runCatching {
            prefs?.edit()
                ?.putString(KEY_DYNAMIC_GRID_ORIENTATION, mode)
                ?.commit()
        }
        return isHorizontal
    }

    fun saveDynamicGridIsHorizontal(context: Context, isHorizontal: Boolean) {
        val prefs = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
        saveDynamicGridIsHorizontalToPrefs(prefs, isHorizontal)
    }

    internal fun clearAllLocalPreferencesFromPrefs(prefs: SharedPreferences?) {
        runCatching {
            prefs?.edit()
                ?.remove(KEY_DARK_THEME_MODE)
                ?.remove(KEY_ACCENT_COLOR)
                ?.remove(KEY_GUIDED_TOUR_COMPLETED)
                ?.remove(KEY_LAST_RELEASE_VERSION)
                ?.remove(KEY_A11Y_SCREEN_READER)
                ?.remove(KEY_A11Y_FONT_SCALE)
                ?.remove(KEY_A11Y_HIGH_CONTRAST)
                ?.remove(KEY_A11Y_REDUCE_MOTION)
                ?.remove(KEY_A11Y_LARGE_TOUCH_TARGETS)
                ?.remove(KEY_A11Y_BOLD_TEXT)
                ?.remove(KEY_TIMETABLE_ORIENTATION)
                ?.remove(KEY_DISCIPLINES_LAYOUT_MODE)
                ?.remove(KEY_DYNAMIC_GRID_ORIENTATION)
                ?.clear()
                ?.commit()
        }
    }

    fun clearAllLocalPreferences(context: Context) {
        val prefs = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
        clearAllLocalPreferencesFromPrefs(prefs)
    }

    fun clearAllUserDataAndPrefs(
        context: Context,
        repository: CampusCoreRepository
    ) {
        val appContext = context.applicationContext
        clearAllLocalPreferences(appContext)
        runCatching { AppLockManager.clearAllLockData(appContext) }
        runCatching { repository.wipeAllUserDataForLogout() }
        runCatching { File(appContext.filesDir, "profile_avatar.jpg").delete() }
        runCatching { File(appContext.cacheDir, "profile_avatar.jpg").delete() }
        runCatching { File(appContext.filesDir, "feedback_queue").deleteRecursively() }
        runCatching { File(appContext.cacheDir, "shared_grades").deleteRecursively() }
        runCatching { File(appContext.filesDir, "shared_grades").deleteRecursively() }
        runCatching { File(appContext.cacheDir, "shared_backups").deleteRecursively() }
        runCatching { File(appContext.filesDir, "shared_backups").deleteRecursively() }
        runCatching { SecurePdfProcessor.purgeResidualPdfFiles(appContext) }
        runCatching { VidaAcademicaWidgetProvider.requestWidgetUpdate(appContext) }
    }

    fun clearAllUserDataAndPrefs(context: Context) =
        clearAllUserDataAndPrefs(context, getOrCreate(context))
}
