package br.ufop.campus

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import br.ufop.campus.core.curriculum.UfopCurriculumCatalog
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.core.ffi.IncomingGradePreview
import br.ufop.campus.core.ffi.UfopGradeCodec
import br.ufop.campus.core.security.AppLockManager
import br.ufop.campus.core.security.SecurePdfProcessor
import br.ufop.campus.core.storage.GradeExportStorage
import br.ufop.campus.core.update.AppUpdateManager
import br.ufop.campus.ui.navigation.CampusNavigation
import br.ufop.campus.ui.navigation.CampusTab
import br.ufop.campus.ui.security.AppLockOverlay
import br.ufop.campus.ui.security.PersistenceRecoveryScreen
import br.ufop.campus.ui.theme.CampusAccentColor
import br.ufop.campus.ui.theme.CampusTheme
import br.ufop.campus.widget.VidaAcademicaWidgetProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var isAppLockedState by mutableStateOf(false)
    private var lastBackgroundElapsedMs: Long = 0L
    private var pendingIncomingGrade by mutableStateOf<IncomingGradePreview?>(null)
    private var incomingGradeError by mutableStateOf<String?>(null)
    private var pendingWidgetTargetTab by mutableStateOf<CampusTab?>(null)
    private var pendingWidgetNavNonce by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        @Suppress("DEPRECATION")
        window.statusBarColor = AndroidColor.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = AndroidColor.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        syncWindowSecureFlag()

        isAppLockedState = if (savedInstanceState != null) {
            savedInstanceState.getBoolean(
                STATE_KEY_APP_LOCKED,
                AppLockManager.isLockEnabled(applicationContext)
            )
        } else {
            AppLockManager.isLockEnabled(applicationContext)
        }
        lastBackgroundElapsedMs = savedInstanceState?.getLong(STATE_KEY_LAST_BG_MS, 0L) ?: 0L

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                PDFBoxResourceLoader.init(applicationContext)
            }
            runCatching {
                SecurePdfProcessor.purgeResidualPdfFiles(applicationContext)
            }
            runCatching {
                AppUpdateManager.purgeCachedApks(applicationContext)
                AppUpdateManager.checkForUpdates(applicationContext, force = false)
            }
        }

        setContent {
            val systemDark = isSystemInDarkTheme()
            val repository: CampusCoreRepository = remember {
                CampusRepositoryHolder.getOrCreate(applicationContext)
            }
            val mutationVersion by repository.mutationVersion.collectAsState()
            val persistenceLoadError by repository.persistenceLoadError.collectAsState()

            // Pré-aquece o catálogo de salas e o catálogo de cursos UFOP (~595 KB) em I/O fora da Main Thread.
            LaunchedEffect(repository) {
                launch(Dispatchers.IO) {
                    runCatching {
                        CampusRepositoryHolder.warmUpRoomAllocationsOnIo(
                            context = applicationContext,
                            repository = repository
                        )
                    }
                }
                launch(Dispatchers.IO) {
                    runCatching {
                        UfopCurriculumCatalog.getAllCourses(applicationContext)
                    }
                }
            }

            var isDarkTheme by rememberSaveable {
                mutableStateOf(
                    CampusRepositoryHolder.loadSavedDarkTheme(
                        context = applicationContext,
                        repository = repository,
                        systemDark = systemDark
                    )
                )
            }
            var accentColorKey by rememberSaveable {
                mutableStateOf(
                    CampusRepositoryHolder.loadSavedAccentColor(
                        context = applicationContext,
                        repository = repository
                    )
                )
            }
            var accessibilitySettings by remember {
                mutableStateOf(
                    CampusRepositoryHolder.loadAccessibilitySettings(
                        context = applicationContext,
                        repository = repository
                    )
                )
            }

            // Mantém tema, cor, acessibilidade e FLAG_SECURE sincronizados após restaurações de backup ou logout.
            LaunchedEffect(mutationVersion) {
                val syncedDark = CampusRepositoryHolder.loadSavedDarkTheme(
                    context = applicationContext,
                    repository = repository,
                    systemDark = systemDark
                )
                if (isDarkTheme != syncedDark) {
                    isDarkTheme = syncedDark
                }
                val syncedAccent = CampusRepositoryHolder.loadSavedAccentColor(
                    context = applicationContext,
                    repository = repository
                )
                if (accentColorKey != syncedAccent) {
                    accentColorKey = syncedAccent
                }
                val syncedA11y = CampusRepositoryHolder.loadAccessibilitySettings(
                    context = applicationContext,
                    repository = repository
                )
                if (accessibilitySettings != syncedA11y) {
                    accessibilitySettings = syncedA11y
                }
                syncWindowSecureFlag()
            }

            val accentColor = remember(accentColorKey) {
                CampusAccentColor.fromKey(accentColorKey)
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    insetsController.isAppearanceLightStatusBars = !isDarkTheme
                    insetsController.isAppearanceLightNavigationBars = !isDarkTheme
                    @Suppress("DEPRECATION")
                    window.statusBarColor = AndroidColor.TRANSPARENT
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = AndroidColor.TRANSPARENT
                }
            }

            CampusTheme(
                darkTheme = isDarkTheme,
                accentColor = accentColor,
                accessibility = accessibilitySettings,
                onToggleDarkTheme = {
                    val nextDark = !isDarkTheme
                    isDarkTheme = nextDark
                    CampusRepositoryHolder.saveDarkTheme(
                        context = applicationContext,
                        repository = repository,
                        isDark = nextDark
                    )
                },
                onSelectAccentColor = { selected ->
                    accentColorKey = selected.storageKey
                    CampusRepositoryHolder.saveAccentColor(
                        context = applicationContext,
                        repository = repository,
                        accentKey = selected.storageKey
                    )
                },
                onUpdateAccessibility = { updated ->
                    accessibilitySettings = updated
                    CampusRepositoryHolder.saveAccessibilitySettings(
                        context = applicationContext,
                        repository = repository,
                        settings = updated
                    )
                }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val saveableStateHolder = rememberSaveableStateHolder()
                    if (isAppLockedState) {
                        AppLockOverlay(
                            onUnlocked = {
                                lastBackgroundElapsedMs = 0L
                                isAppLockedState = false
                            }
                        )
                    } else if (persistenceLoadError.hasError) {
                        PersistenceRecoveryScreen(
                            repository = repository,
                            errorState = persistenceLoadError
                        )
                    } else {
                        saveableStateHolder.SaveableStateProvider(key = "campus_main_content") {
                            CampusNavigation(
                                repository = repository,
                                pendingIncomingGrade = if (isAppLockedState) null else pendingIncomingGrade,
                                incomingGradeError = if (isAppLockedState) null else incomingGradeError,
                                pendingTargetTab = if (isAppLockedState) null else pendingWidgetTargetTab,
                                pendingTargetTabNonce = pendingWidgetNavNonce,
                                onConsumeTargetTab = { pendingWidgetTargetTab = null },
                                onConsumeIncomingGrade = { pendingIncomingGrade = null },
                                onDismissIncomingGradeError = { incomingGradeError = null },
                                onAppSecurityChanged = {
                                    syncWindowSecureFlag()
                                    if (!AppLockManager.isLockEnabled(applicationContext)) {
                                        isAppLockedState = false
                                    }
                                },
                                onExitApp = { finishAffinity() }
                            )
                        }
                    }
                }
            }
        }

        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_KEY_APP_LOCKED, isAppLockedState)
        outState.putLong(STATE_KEY_LAST_BG_MS, lastBackgroundElapsedMs)
    }

    override fun onStart() {
        super.onStart()
        syncWindowSecureFlag()
        if (!AppLockManager.isLockEnabled(applicationContext)) {
            isAppLockedState = false
        } else if (!isChangingConfigurations) {
            val backgroundStartedAt = lastBackgroundElapsedMs
            if (backgroundStartedAt > 0L) {
                val elapsedBackgroundMs = SystemClock.elapsedRealtime() - backgroundStartedAt
                if (elapsedBackgroundMs >= RELOCK_BACKGROUND_THRESHOLD_MS) {
                    isAppLockedState = true
                }
            }
        }
        lastBackgroundElapsedMs = 0L
    }

    override fun onResume() {
        super.onResume()
        runCatching {
            VidaAcademicaWidgetProvider.requestWidgetUpdate(applicationContext)
        }
        runCatching {
            AppUpdateManager.onActivityResumed(this)
        }
    }

    override fun onPause() {
        runCatching {
            AppUpdateManager.onActivityPaused(this)
        }
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && AppLockManager.isLockEnabled(applicationContext)) {
            lastBackgroundElapsedMs = SystemClock.elapsedRealtime()
        }
        runCatching {
            VidaAcademicaWidgetProvider.requestWidgetUpdate(applicationContext)
        }
    }

    private fun handleIncomingIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return
        if ((incomingIntent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return

        val widgetTarget = incomingIntent.getStringExtra(EXTRA_WIDGET_TARGET_TAB)
        if (!widgetTarget.isNullOrBlank()) {
            incomingIntent.removeExtra(EXTRA_WIDGET_TARGET_TAB)
            pendingWidgetTargetTab = when (widgetTarget.trim().uppercase()) {
                WIDGET_TARGET_ACTIVITIES -> CampusTab.ACTIVITIES
                WIDGET_TARGET_AGENDA -> CampusTab.AGENDA
                else -> CampusTab.AGENDA
            }
            pendingWidgetNavNonce += 1
        }

        when (incomingIntent.action) {
            Intent.ACTION_VIEW -> {
                val uri = incomingIntent.data ?: return
                setIntent(Intent(Intent.ACTION_MAIN))
                launchIncomingGradeLoad(uri = uri, inlineText = null, inlineSourceLabel = null)
            }
            Intent.ACTION_SEND -> {
                val streamUri = IntentCompat.getParcelableExtra(
                    incomingIntent,
                    Intent.EXTRA_STREAM,
                    Uri::class.java
                )
                if (streamUri != null) {
                    setIntent(Intent(Intent.ACTION_MAIN))
                    launchIncomingGradeLoad(uri = streamUri, inlineText = null, inlineSourceLabel = null)
                    return
                }
                val sharedText = incomingIntent.getStringExtra(Intent.EXTRA_TEXT)
                    ?.takeIf { it.isNotBlank() }
                    ?: return
                setIntent(Intent(Intent.ACTION_MAIN))
                launchIncomingGradeLoad(
                    uri = null,
                    inlineText = sharedText,
                    inlineSourceLabel = "Texto compartilhado"
                )
            }
        }
    }

    private fun launchIncomingGradeLoad(
        uri: Uri?,
        inlineText: String?,
        inlineSourceLabel: String?
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            val previewResult = runCatching {
                val rawText: String
                val sourceLabel: String
                if (uri != null) {
                    rawText = GradeExportStorage.readPayloadFromUriSafely(applicationContext, uri)
                    sourceLabel = GradeExportStorage.resolveDisplayName(applicationContext, uri)
                        ?: "Arquivo .ufopgrade"
                } else {
                    rawText = inlineText.orEmpty()
                    sourceLabel = inlineSourceLabel ?: "Texto compartilhado"
                }
                UfopGradeCodec.parseAndValidatePreview(
                    rawText = rawText,
                    sourceLabel = sourceLabel
                ).getOrThrow()
            }
            withContext(Dispatchers.Main) {
                previewResult
                    .onSuccess { preview ->
                        incomingGradeError = null
                        pendingIncomingGrade = preview
                    }
                    .onFailure { err ->
                        pendingIncomingGrade = null
                        incomingGradeError = err.message?.takeIf { it.isNotBlank() }
                            ?: "Não foi possível ler o arquivo .ufopgrade recebido."
                    }
            }
        }
    }

    private fun syncWindowSecureFlag() {
        val lockEnabled = AppLockManager.isLockEnabled(applicationContext)
        if (lockEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    companion object {
        const val STATE_KEY_APP_LOCKED = "state_app_locked"
        const val STATE_KEY_LAST_BG_MS = "state_last_bg_elapsed_ms"
        const val RELOCK_BACKGROUND_THRESHOLD_MS = 10_000L
        const val EXTRA_WIDGET_TARGET_TAB = "br.ufop.campus.EXTRA_WIDGET_TARGET_TAB"
        const val WIDGET_TARGET_AGENDA = "AGENDA"
        const val WIDGET_TARGET_ACTIVITIES = "ACTIVITIES"
    }
}
