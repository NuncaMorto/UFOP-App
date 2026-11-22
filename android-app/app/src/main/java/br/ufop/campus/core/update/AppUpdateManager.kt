package br.ufop.campus.core.update

import android.app.Activity
import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import br.ufop.campus.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState
    data object Checking : AppUpdateUiState
    data class UpToDate(val checkedAtEpochMs: Long, val isManualCheck: Boolean) : AppUpdateUiState
    data class Available(
        val info: AppUpdateReleaseInfo,
        val bannerDismissed: Boolean = false
    ) : AppUpdateUiState
    data class Downloading(
        val info: AppUpdateReleaseInfo,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val minimized: Boolean = false
    ) : AppUpdateUiState
    data class Verifying(
        val info: AppUpdateReleaseInfo,
        val minimized: Boolean = false
    ) : AppUpdateUiState
    data class NeedsInstallPermission(
        val info: AppUpdateReleaseInfo,
        val apkFilePath: String
    ) : AppUpdateUiState
    data class ReadyToInstall(
        val info: AppUpdateReleaseInfo,
        val apkFilePath: String,
        val installerOpened: Boolean = false
    ) : AppUpdateUiState
    data class Installing(val info: AppUpdateReleaseInfo) : AppUpdateUiState
    data class Error(
        val message: String,
        val info: AppUpdateReleaseInfo? = null
    ) : AppUpdateUiState
}

/**
 * Gerenciador de atualizações internas via GitHub Releases.
 *
 * - Executa downloads e verificação SHA-256 em segundo plano (`Dispatchers.IO`) sem bloquear a UI.
 * - Utiliza cache temporário exclusivo em `cacheDir/app_updates/` e preferências isoladas
 *   (`campus_app_updater_prefs`), sem alterar ou apagar o banco SQLite / JSON criptografado do aluno.
 * - Entrega a instalação primariamente à tela nativa do instalador do Android (`ACTION_VIEW` + `FileProvider`
 *   com `FLAG_ACTIVITY_NEW_TASK`) para preservar barra de progresso real e botão "Abrir", com fallback
 *   para sessão `PackageInstaller` (`USER_ACTION_REQUIRED`) e diagnósticos persistidos.
 * - Não envia notificações externas na barra do sistema Android.
 */
object AppUpdateManager {

    internal const val ACTION_INSTALL_SESSION_RESULT =
        "io.github.nuncamorto.vidaacademica.ACTION_INSTALL_SESSION_RESULT"
    internal const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    private const val PREFS_NAME = "campus_app_updater_prefs"
    private const val KEY_LAST_CHECK_MS = "last_check_epoch_ms"
    private const val KEY_DISMISSED_TAG = "dismissed_tag"
    private const val KEY_BETA_CHANNEL = "include_beta_channel"
    private const val KEY_PENDING_RELAUNCH = "pending_auto_relaunch"
    private const val KEY_PENDING_APK_PATH = "pending_install_apk_path"
    private const val KEY_PENDING_TARGET_VERSION_CODE = "pending_install_target_version_code"
    private const val KEY_LAST_INSTALL_ERROR = "last_install_error_message"

    private const val AUTO_CHECK_INTERVAL_MS = 2L * 60L * 60L * 1000L // 2 horas
    private const val PENDING_APK_MAX_AGE_MS = 24L * 60L * 60L * 1000L // 24 horas
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_REDIRECTS = 6

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeCheckJob: Job? = null
    private var activeDownloadJob: Job? = null
    private var foregroundActivityRef: WeakReference<Activity>? = null

    private val _state = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()

    private val _isDialogOpen = MutableStateFlow(false)
    val isDialogOpen: StateFlow<Boolean> = _isDialogOpen.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun updatesCacheDir(context: Context): File =
        File(context.applicationContext.cacheDir, "app_updates").apply { if (!exists()) mkdirs() }

    fun isBetaChannelEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BETA_CHANNEL, true)

    fun setBetaChannelEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BETA_CHANNEL, enabled).apply()
    }

    fun openUpdateDialog() {
        _isDialogOpen.value = true
    }

    fun closeUpdateDialog() {
        _isDialogOpen.value = false
    }

    fun dismissBannerForCurrentRelease(context: Context) {
        val current = _state.value
        if (current is AppUpdateUiState.Available) {
            prefs(context).edit().putString(KEY_DISMISSED_TAG, current.info.tagName).apply()
            _state.value = current.copy(bannerDismissed = true)
        }
        _isDialogOpen.value = false
    }

    fun minimizeProgressToBanner() {
        when (val current = _state.value) {
            is AppUpdateUiState.Downloading -> _state.value = current.copy(minimized = true)
            is AppUpdateUiState.Verifying -> _state.value = current.copy(minimized = true)
            else -> Unit
        }
        _isDialogOpen.value = false
    }

    fun checkForUpdates(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        val currentState = _state.value
        if (currentState is AppUpdateUiState.Downloading ||
            currentState is AppUpdateUiState.Verifying ||
            currentState is AppUpdateUiState.NeedsInstallPermission ||
            currentState is AppUpdateUiState.ReadyToInstall ||
            currentState is AppUpdateUiState.Installing
        ) {
            if (force) _isDialogOpen.value = true
            return
        }

        val now = System.currentTimeMillis()
        val lastCheck = prefs(appContext).getLong(KEY_LAST_CHECK_MS, 0L)
        if (!force && (now - lastCheck) < AUTO_CHECK_INTERVAL_MS && currentState is AppUpdateUiState.Idle) {
            return
        }

        if (activeCheckJob?.isActive == true && !force) return
        activeCheckJob?.cancel()

        if (force) {
            _state.value = AppUpdateUiState.Checking
        }

        activeCheckJob = scope.launch {
            runCatching {
                val jsonBody = fetchTrustedHttpsText(GitHubReleaseParser.RELEASES_API_URL)
                val includeBeta = isBetaChannelEnabled(appContext)
                val available = GitHubReleaseParser.parseLatestApplicableRelease(
                    releasesJson = jsonBody,
                    currentVersionName = BuildConfig.VERSION_NAME,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    includePrereleases = includeBeta
                )

                prefs(appContext).edit().putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis()).apply()

                if (available != null) {
                    val dismissedTag = prefs(appContext).getString(KEY_DISMISSED_TAG, null)
                    val bannerDismissed = !force && dismissedTag == available.tagName
                    _state.value = AppUpdateUiState.Available(
                        info = available,
                        bannerDismissed = bannerDismissed
                    )
                    if (force) {
                        _isDialogOpen.value = true
                    }
                } else {
                    _state.value = AppUpdateUiState.UpToDate(
                        checkedAtEpochMs = System.currentTimeMillis(),
                        isManualCheck = force
                    )
                }
            }.onFailure { err ->
                if (force) {
                    _state.value = AppUpdateUiState.Error(
                        message = friendlyNetworkError(err),
                        info = null
                    )
                } else {
                    if (_state.value is AppUpdateUiState.Checking) {
                        _state.value = AppUpdateUiState.Idle
                    }
                }
            }
        }
    }

    fun startBackgroundDownloadAndInstall(context: Context, info: AppUpdateReleaseInfo) {
        val appContext = context.applicationContext
        if (activeDownloadJob?.isActive == true) return

        activeDownloadJob = scope.launch {
            val cacheDir = updatesCacheDir(appContext)
            purgeDirectoryFiles(cacheDir)
            prefs(appContext).edit()
                .remove(KEY_LAST_INSTALL_ERROR)
                .remove(KEY_PENDING_APK_PATH)
                .remove(KEY_PENDING_TARGET_VERSION_CODE)
                .apply()

            val safeName = info.apkName.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
            val partFile = File(cacheDir, "$safeName.part")
            val finalApkFile = File(cacheDir, safeName)

            _state.value = AppUpdateUiState.Downloading(
                info = info,
                progress = 0f,
                downloadedBytes = 0L,
                totalBytes = info.apkSizeBytes,
                minimized = false
            )

            var conn: HttpURLConnection? = null
            try {
                conn = openTrustedConnectionWithRedirects(info.apkDownloadUrl, acceptHeader = "application/octet-stream")
                val status = conn.responseCode
                if (status !in 200..299) {
                    throw IllegalStateException("Servidor retornou HTTP $status ao baixar APK.")
                }

                val reportedTotal = conn.contentLengthLong.takeIf { it > 0L } ?: info.apkSizeBytes
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                var lastUiEmitMs = 0L

                conn.inputStream.use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (now - lastUiEmitMs >= 100L || (reportedTotal > 0L && downloaded >= reportedTotal)) {
                                lastUiEmitMs = now
                                val progress = if (reportedTotal > 0L) {
                                    (downloaded.toFloat() / reportedTotal.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                                val currentMinimized = (_state.value as? AppUpdateUiState.Downloading)?.minimized ?: false
                                _state.value = AppUpdateUiState.Downloading(
                                    info = info,
                                    progress = progress,
                                    downloadedBytes = downloaded,
                                    totalBytes = reportedTotal,
                                    minimized = currentMinimized
                                )
                            }
                        }
                        output.fd.sync()
                    }
                }

                val wasMinimized = (_state.value as? AppUpdateUiState.Downloading)?.minimized ?: false
                _state.value = AppUpdateUiState.Verifying(info = info, minimized = wasMinimized)

                val actualSha256 = digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
                val expectedSha256 = resolveExpectedSha256(info)
                if (expectedSha256 == null || !GitHubReleaseParser.verifySha256Hex(actualSha256, expectedSha256)) {
                    partFile.delete()
                    _state.value = AppUpdateUiState.Error(
                        message = "Falha na verificação de segurança SHA-256. O arquivo baixado foi descartado por precaução.",
                        info = info
                    )
                    _isDialogOpen.value = true
                    return@launch
                }

                if (finalApkFile.exists()) finalApkFile.delete()
                if (!partFile.renameTo(finalApkFile)) {
                    partFile.copyTo(finalApkFile, overwrite = true)
                    partFile.delete()
                }

                triggerInstallOrRequestPermission(appContext, info, finalApkFile)
            } catch (t: Throwable) {
                partFile.delete()
                finalApkFile.delete()
                _state.value = AppUpdateUiState.Error(
                    message = "Não foi possível concluir o download: ${friendlyNetworkError(t)}",
                    info = info
                )
                _isDialogOpen.value = true
            } finally {
                conn?.disconnect()
            }
        }
    }

    fun onActivityResumed(activity: Activity) {
        foregroundActivityRef = WeakReference(activity)
        val appContext = activity.applicationContext
        val p = prefs(appContext)
        val pendingTargetVc = p.getInt(KEY_PENDING_TARGET_VERSION_CODE, -1)
        val installedVc = BuildConfig.VERSION_CODE

        if (pendingTargetVc > 0 && installedVc >= pendingTargetVc) {
            onPackageReplacedCleanup(appContext)
            purgeCachedApks(appContext)
            if (_state.value is AppUpdateUiState.ReadyToInstall ||
                _state.value is AppUpdateUiState.Installing ||
                _state.value is AppUpdateUiState.NeedsInstallPermission
            ) {
                _state.value = AppUpdateUiState.Idle
                _isDialogOpen.value = false
            }
        } else {
            val persistedErr = p.getString(KEY_LAST_INSTALL_ERROR, null)?.takeIf { it.isNotBlank() }
            if (persistedErr != null) {
                p.edit().remove(KEY_LAST_INSTALL_ERROR).apply()
                _state.value = AppUpdateUiState.Error(
                    message = persistedErr,
                    info = extractInfoFromState(_state.value)
                )
                _isDialogOpen.value = true
            }
        }

        onAppResumed(activity)
    }

    fun onActivityPaused(activity: Activity) {
        if (foregroundActivityRef?.get() === activity) {
            foregroundActivityRef = null
        }
    }

    fun onAppResumed(context: Context) {
        val appContext = context.applicationContext
        val current = _state.value
        val launchContext = if (context is Activity) context else aliveForegroundActivity() ?: context

        when (current) {
            is AppUpdateUiState.NeedsInstallPermission -> {
                if (appContext.packageManager.canRequestPackageInstalls()) {
                    val apkFile = File(current.apkFilePath)
                    if (apkFile.isFile) {
                        _state.value = AppUpdateUiState.ReadyToInstall(
                            info = current.info,
                            apkFilePath = apkFile.absolutePath,
                            installerOpened = false
                        )
                        _isDialogOpen.value = true
                        launchSystemInstaller(launchContext, current.info, apkFile)
                    } else {
                        _state.value = AppUpdateUiState.Available(info = current.info, bannerDismissed = false)
                    }
                }
            }

            is AppUpdateUiState.ReadyToInstall -> {
                if (!current.installerOpened && appContext.packageManager.canRequestPackageInstalls()) {
                    val apkFile = File(current.apkFilePath)
                    if (apkFile.isFile) {
                        launchSystemInstaller(launchContext, current.info, apkFile)
                    } else {
                        _state.value = AppUpdateUiState.Available(info = current.info, bannerDismissed = false)
                    }
                }
            }

            else -> Unit
        }
    }

    fun retryInstallIfPermitted(context: Context) {
        val appContext = context.applicationContext
        val current = _state.value
        if (current is AppUpdateUiState.NeedsInstallPermission) {
            val apkFile = File(current.apkFilePath)
            if (apkFile.isFile && appContext.packageManager.canRequestPackageInstalls()) {
                val launchContext = aliveForegroundActivity() ?: context
                launchSystemInstaller(launchContext, current.info, apkFile)
            } else {
                openUnknownSourcesSettings(appContext)
            }
        }
    }

    fun openUnknownSourcesSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun hasPendingVerifiedApk(context: Context): Boolean = resolvePendingApkFile(context) != null

    fun installDownloadedApkWithSystemUi(context: Context) {
        val appContext = context.applicationContext
        val currentInfo = extractInfoFromState(_state.value)
        val apkFile = resolvePendingApkFile(appContext)

        if (apkFile == null) {
            _state.value = AppUpdateUiState.Error(
                message = "O pacote verificado não está mais em cache. Toque em \"Tentar novamente\" para baixar novamente.",
                info = currentInfo
            )
            _isDialogOpen.value = true
            return
        }

        if (!appContext.packageManager.canRequestPackageInstalls()) {
            if (currentInfo != null) {
                _state.value = AppUpdateUiState.NeedsInstallPermission(
                    info = currentInfo,
                    apkFilePath = apkFile.absolutePath
                )
            }
            _isDialogOpen.value = true
            return
        }

        val launchContext = aliveForegroundActivity() ?: context
        launchSystemInstaller(launchContext, currentInfo, apkFile)
    }

    fun installDownloadedApkWithPackageInstallerSession(context: Context) {
        val appContext = context.applicationContext
        val currentInfo = extractInfoFromState(_state.value)
        val apkFile = resolvePendingApkFile(appContext)

        if (apkFile == null) {
            _state.value = AppUpdateUiState.Error(
                message = "O pacote verificado não está mais em cache. Toque em \"Tentar novamente\" para baixar novamente.",
                info = currentInfo
            )
            _isDialogOpen.value = true
            return
        }

        if (!appContext.packageManager.canRequestPackageInstalls()) {
            if (currentInfo != null) {
                _state.value = AppUpdateUiState.NeedsInstallPermission(
                    info = currentInfo,
                    apkFilePath = apkFile.absolutePath
                )
            }
            _isDialogOpen.value = true
            return
        }

        val editor = prefs(appContext).edit()
            .putBoolean(KEY_PENDING_RELAUNCH, true)
            .putString(KEY_PENDING_APK_PATH, apkFile.absolutePath)
            .remove(KEY_LAST_INSTALL_ERROR)
        if (currentInfo?.versionCode != null) {
            editor.putInt(KEY_PENDING_TARGET_VERSION_CODE, currentInfo.versionCode)
        }
        editor.apply()

        if (currentInfo != null) {
            _state.value = AppUpdateUiState.Installing(currentInfo)
        }
        _isDialogOpen.value = true

        scope.launch {
            runCatching {
                abandonStaleSessions(appContext)
                val packageInstaller = appContext.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(appContext.packageName)
                    setSize(apkFile.length())
                    setInstallReason(PackageManager.INSTALL_REASON_USER)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                    }
                }
                val sessionId = packageInstaller.createSession(params)
                packageInstaller.openSession(sessionId).use { session ->
                    session.openWrite("ufop_update.apk", 0, apkFile.length()).use { out ->
                        FileInputStream(apkFile).use { input ->
                            input.copyTo(out, bufferSize = 16 * 1024)
                        }
                        session.fsync(out)
                    }
                    val callbackIntent = Intent(appContext, AppUpdateInstallReceiver::class.java).apply {
                        action = ACTION_INSTALL_SESSION_RESULT
                        `package` = appContext.packageName
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                    val pendingIntent = PendingIntent.getBroadcast(appContext, sessionId, callbackIntent, flags)
                    session.commit(pendingIntent.intentSender)
                }
            }.onFailure { err ->
                val msg = "Não foi possível iniciar a sessão de instalação (${err.javaClass.simpleName}). Use \"Abrir instalador\" abaixo."
                prefs(appContext).edit()
                    .putBoolean(KEY_PENDING_RELAUNCH, false)
                    .putString(KEY_LAST_INSTALL_ERROR, msg)
                    .apply()
                _state.value = AppUpdateUiState.Error(message = msg, info = currentInfo)
                _isDialogOpen.value = true
            }
        }
    }

    fun retryFromError(context: Context) {
        prefs(context).edit().remove(KEY_LAST_INSTALL_ERROR).apply()
        val current = _state.value
        if (current is AppUpdateUiState.Error && current.info != null) {
            startBackgroundDownloadAndInstall(context, current.info)
        } else {
            checkForUpdates(context, force = true)
        }
    }

    fun purgeCachedApks(context: Context) {
        val current = _state.value
        val isActiveState = isActiveUpdateState(current)
        if (isActiveState) return

        val appContext = context.applicationContext
        scope.launch {
            abandonStaleSessions(appContext)
            val cacheDir = updatesCacheDir(appContext)
            if (!cacheDir.exists() || !cacheDir.isDirectory) return@launch

            val p = prefs(appContext)
            val pendingPath = p.getString(KEY_PENDING_APK_PATH, null)
            val pendingTargetVc = p.getInt(KEY_PENDING_TARGET_VERSION_CODE, -1).takeIf { it > 0 }
            val installedVc = BuildConfig.VERSION_CODE
            val nowMs = System.currentTimeMillis()

            var retainedPending = false
            cacheDir.listFiles()?.forEach { file ->
                val isPending = pendingPath != null && file.absolutePath == pendingPath
                val shouldPurge = shouldPurgeUpdateApk(
                    fileLastModifiedMs = file.lastModified(),
                    nowMs = nowMs,
                    isActiveState = isActiveUpdateState(_state.value),
                    isPendingApk = isPending && file.isFile,
                    installedVersionCode = installedVc,
                    pendingTargetVersionCode = pendingTargetVc
                )
                if (shouldPurge) {
                    runCatching {
                        if (file.isDirectory) file.deleteRecursively() else file.delete()
                    }
                } else if (isPending) {
                    retainedPending = true
                }
            }

            if (!retainedPending && pendingPath != null) {
                p.edit()
                    .remove(KEY_PENDING_APK_PATH)
                    .remove(KEY_PENDING_TARGET_VERSION_CODE)
                    .apply()
            }
        }
    }

    internal fun onPackageReplacedCleanup(context: Context) {
        prefs(context).edit()
            .remove(KEY_LAST_INSTALL_ERROR)
            .remove(KEY_PENDING_APK_PATH)
            .remove(KEY_PENDING_TARGET_VERSION_CODE)
            .apply()
    }

    internal fun consumePendingAutoRelaunch(context: Context): Boolean {
        val p = prefs(context)
        val pending = p.getBoolean(KEY_PENDING_RELAUNCH, false)
        p.edit()
            .putBoolean(KEY_PENDING_RELAUNCH, false)
            .remove(KEY_PENDING_APK_PATH)
            .remove(KEY_PENDING_TARGET_VERSION_CODE)
            .remove(KEY_LAST_INSTALL_ERROR)
            .apply()
        return pending
    }

    internal fun shouldPurgeUpdateApk(
        fileLastModifiedMs: Long,
        nowMs: Long,
        isActiveState: Boolean,
        isPendingApk: Boolean,
        installedVersionCode: Int,
        pendingTargetVersionCode: Int?
    ): Boolean {
        if (isActiveState) return false
        if (!isPendingApk) return true
        if (pendingTargetVersionCode != null && installedVersionCode >= pendingTargetVersionCode) {
            return true
        }
        return (nowMs - fileLastModifiedMs) > PENDING_APK_MAX_AGE_MS
    }

    internal fun mapInstallFailureMessage(status: Int, statusMessage: String?): String? {
        return when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> null
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                "A assinatura do novo APK difere da versão instalada. Seus dados continuam intactos; exporte um backup (.ufopbackup) antes de reinstalar."
            PackageInstaller.STATUS_FAILURE_STORAGE ->
                "Espaço insuficiente no aparelho para concluir a instalação da atualização."
            PackageInstaller.STATUS_FAILURE_BLOCKED ->
                "A instalação foi bloqueada pelas configurações de segurança ou Play Protect do aparelho. Tente usar \"Abrir instalador\"."
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                "Este pacote não é compatível com a versão do Android ou arquitetura deste aparelho."
            PackageInstaller.STATUS_FAILURE_INVALID ->
                "O pacote de atualização parece corrompido ou inválido. Tente baixar novamente."
            else -> {
                val detail = statusMessage?.takeIf { it.isNotBlank() } ?: "código $status"
                "Não foi possível concluir a instalação automática ($detail). Tente usar \"Abrir instalador\"."
            }
        }
    }

    internal fun onInstallStatusFromReceiver(
        context: Context,
        status: Int,
        statusMessage: String?,
        confirmIntent: Intent?
    ) {
        val appContext = context.applicationContext
        val currentInfo = extractInfoFromState(_state.value)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (confirmIntent != null) {
                    val liveActivity = aliveForegroundActivity()
                    val startedFromActivity = if (liveActivity != null) {
                        val copy = Intent(confirmIntent)
                        runCatching { liveActivity.startActivity(copy) }.isSuccess
                    } else {
                        false
                    }
                    if (!startedFromActivity) {
                        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching {
                            appContext.startActivity(confirmIntent)
                        }.onFailure {
                            fallbackInstallPendingApk(appContext, currentInfo)
                        }
                    }
                } else {
                    fallbackInstallPendingApk(appContext, currentInfo)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                onPackageReplacedCleanup(appContext)
                prefs(appContext).edit().putBoolean(KEY_PENDING_RELAUNCH, false).apply()
                _state.value = AppUpdateUiState.Idle
                _isDialogOpen.value = false
                purgeDirectoryFiles(updatesCacheDir(appContext))
            }

            else -> {
                val errorMessage = mapInstallFailureMessage(status, statusMessage)
                if (errorMessage == null) {
                    // Usuário cancelou (STATUS_FAILURE_ABORTED): manter APK verificado disponível para nova tentativa.
                    prefs(appContext).edit().putBoolean(KEY_PENDING_RELAUNCH, false).apply()
                    val pendingApk = resolvePendingApkFile(appContext)
                    _state.value = when {
                        pendingApk != null && currentInfo != null ->
                            AppUpdateUiState.ReadyToInstall(
                                info = currentInfo,
                                apkFilePath = pendingApk.absolutePath,
                                installerOpened = true
                            )
                        currentInfo != null ->
                            AppUpdateUiState.Available(info = currentInfo, bannerDismissed = false)
                        else -> AppUpdateUiState.Idle
                    }
                } else {
                    prefs(appContext).edit()
                        .putBoolean(KEY_PENDING_RELAUNCH, false)
                        .putString(KEY_LAST_INSTALL_ERROR, errorMessage)
                        .apply()
                    _state.value = AppUpdateUiState.Error(
                        message = errorMessage,
                        info = currentInfo
                    )
                    _isDialogOpen.value = true
                }
            }
        }
    }

    private fun triggerInstallOrRequestPermission(
        appContext: Context,
        info: AppUpdateReleaseInfo,
        apkFile: File
    ) {
        val editor = prefs(appContext).edit()
            .putString(KEY_PENDING_APK_PATH, apkFile.absolutePath)
            .remove(KEY_LAST_INSTALL_ERROR)
        if (info.versionCode != null) {
            editor.putInt(KEY_PENDING_TARGET_VERSION_CODE, info.versionCode)
        } else {
            editor.remove(KEY_PENDING_TARGET_VERSION_CODE)
        }
        editor.apply()

        if (!appContext.packageManager.canRequestPackageInstalls()) {
            _state.value = AppUpdateUiState.NeedsInstallPermission(
                info = info,
                apkFilePath = apkFile.absolutePath
            )
            _isDialogOpen.value = true
            return
        }

        _state.value = AppUpdateUiState.ReadyToInstall(
            info = info,
            apkFilePath = apkFile.absolutePath,
            installerOpened = false
        )
        _isDialogOpen.value = true

        if (aliveForegroundActivity() != null) {
            mainScope.launch {
                val activeActivity = aliveForegroundActivity() ?: return@launch
                launchSystemInstaller(activeActivity, info, apkFile)
            }
        }
    }

    private fun launchSystemInstaller(
        launchContext: Context,
        info: AppUpdateReleaseInfo?,
        apkFile: File
    ) {
        val appContext = launchContext.applicationContext
        if (!apkFile.isFile) {
            _state.value = AppUpdateUiState.Error(
                message = "O arquivo da atualização não está mais em cache. Toque em \"Tentar novamente\" para baixar.",
                info = info
            )
            _isDialogOpen.value = true
            return
        }

        if (!appContext.packageManager.canRequestPackageInstalls()) {
            val resolvedInfo = info ?: extractInfoFromState(_state.value)
            if (resolvedInfo != null) {
                _state.value = AppUpdateUiState.NeedsInstallPermission(
                    info = resolvedInfo,
                    apkFilePath = apkFile.absolutePath
                )
            }
            _isDialogOpen.value = true
            return
        }

        val result = openSystemApkInstaller(launchContext, apkFile)
        if (result.isSuccess) {
            prefs(appContext).edit()
                .putBoolean(KEY_PENDING_RELAUNCH, true)
                .remove(KEY_LAST_INSTALL_ERROR)
                .apply()
            val resolvedInfo = info ?: extractInfoFromState(_state.value)
            if (resolvedInfo != null) {
                _state.value = AppUpdateUiState.ReadyToInstall(
                    info = resolvedInfo,
                    apkFilePath = apkFile.absolutePath,
                    installerOpened = true
                )
            }
            _isDialogOpen.value = true
        } else {
            installDownloadedApkWithPackageInstallerSession(appContext)
        }
    }

    private fun openSystemApkInstaller(context: Context, apkFile: File): Result<Unit> {
        val appContext = context.applicationContext
        return runCatching {
            val apkUri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, APK_MIME_TYPE)
                clipData = ClipData.newRawUri("vida_academica_update", apkUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolvedHandlers = appContext.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            resolvedHandlers.forEach { resolveInfo ->
                val targetPkg = resolveInfo.activityInfo?.packageName
                if (!targetPkg.isNullOrBlank()) {
                    runCatching {
                        appContext.grantUriPermission(
                            targetPkg,
                            apkUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
            }
            context.startActivity(intent)
        }
    }

    private fun fallbackInstallPendingApk(appContext: Context, info: AppUpdateReleaseInfo?) {
        val file = resolvePendingApkFile(appContext)
        if (file == null) {
            val msg = "O instalador foi fechado e o APK em cache não está mais disponível. Baixe novamente."
            prefs(appContext).edit().putString(KEY_LAST_INSTALL_ERROR, msg).apply()
            _state.value = AppUpdateUiState.Error(message = msg, info = info)
            _isDialogOpen.value = true
            return
        }

        val launchContext = aliveForegroundActivity() ?: appContext
        val result = openSystemApkInstaller(launchContext, file)
        if (result.isSuccess) {
            val resolvedInfo = info ?: extractInfoFromState(_state.value)
            if (resolvedInfo != null) {
                _state.value = AppUpdateUiState.ReadyToInstall(
                    info = resolvedInfo,
                    apkFilePath = file.absolutePath,
                    installerOpened = true
                )
            }
            _isDialogOpen.value = true
        } else {
            val msg = "Falha ao abrir o instalador do sistema."
            prefs(appContext).edit().putString(KEY_LAST_INSTALL_ERROR, msg).apply()
            _state.value = AppUpdateUiState.Error(message = msg, info = info)
            _isDialogOpen.value = true
        }
    }

    fun openInstallFallbackViaFileProvider(context: Context, apkFile: File): Boolean =
        openSystemApkInstaller(context, apkFile).isSuccess

    private fun resolvePendingApkFile(context: Context): File? {
        val pathFromState = when (val st = _state.value) {
            is AppUpdateUiState.ReadyToInstall -> st.apkFilePath
            is AppUpdateUiState.NeedsInstallPermission -> st.apkFilePath
            else -> null
        }
        if (!pathFromState.isNullOrBlank()) {
            val f = File(pathFromState)
            if (f.isFile) return f
        }
        val pathFromPrefs = prefs(context).getString(KEY_PENDING_APK_PATH, null)
        if (!pathFromPrefs.isNullOrBlank()) {
            val f = File(pathFromPrefs)
            if (f.isFile) return f
        }
        return null
    }

    private fun abandonStaleSessions(appContext: Context) {
        runCatching {
            val pi = appContext.packageManager.packageInstaller
            pi.mySessions.forEach { info ->
                runCatching { pi.abandonSession(info.sessionId) }
            }
        }
    }

    private fun aliveForegroundActivity(): Activity? =
        foregroundActivityRef?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }

    private fun isActiveUpdateState(state: AppUpdateUiState): Boolean =
        state is AppUpdateUiState.Downloading ||
            state is AppUpdateUiState.Verifying ||
            state is AppUpdateUiState.NeedsInstallPermission ||
            state is AppUpdateUiState.ReadyToInstall ||
            state is AppUpdateUiState.Installing

    private fun resolveExpectedSha256(info: AppUpdateReleaseInfo): String? {
        info.expectedSha256?.let { return it }
        val checksumUrl = info.checksumAssetUrl ?: return null
        return runCatching {
            val text = fetchTrustedHttpsText(checksumUrl)
            GitHubReleaseParser.extractSha256FromChecksumText(text, info.apkName)
        }.getOrNull()
    }

    private fun fetchTrustedHttpsText(rawUrl: String): String {
        val conn = openTrustedConnectionWithRedirects(rawUrl, acceptHeader = "application/vnd.github+json")
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("GitHub HTTP $code")
            }
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun openTrustedConnectionWithRedirects(
        startUrl: String,
        acceptHeader: String
    ): HttpURLConnection {
        var currentUrl = startUrl
        for (hop in 0 until MAX_REDIRECTS) {
            if (!GitHubReleaseParser.isTrustedGitHubUrl(currentUrl)) {
                throw SecurityException("URL não confiável bloqueada: $currentUrl")
            }
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", acceptHeader)
                setRequestProperty("User-Agent", "VidaAcademica-Android/${BuildConfig.VERSION_NAME}")
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 ||
                code == 308
            ) {
                val location = conn.getHeaderField("Location")?.trim().orEmpty()
                conn.disconnect()
                if (location.isEmpty()) {
                    throw IllegalStateException("Redirecionamento HTTP sem destino.")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
                continue
            }
            return conn
        }
        throw IllegalStateException("Muitos redirecionamentos ao acessar o GitHub.")
    }

    private fun purgeDirectoryFiles(dir: File) {
        if (!dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { file ->
            runCatching {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
        }
    }

    private fun extractInfoFromState(state: AppUpdateUiState): AppUpdateReleaseInfo? = when (state) {
        is AppUpdateUiState.Available -> state.info
        is AppUpdateUiState.Downloading -> state.info
        is AppUpdateUiState.Verifying -> state.info
        is AppUpdateUiState.NeedsInstallPermission -> state.info
        is AppUpdateUiState.ReadyToInstall -> state.info
        is AppUpdateUiState.Installing -> state.info
        is AppUpdateUiState.Error -> state.info
        else -> null
    }

    private fun friendlyNetworkError(err: Throwable): String {
        val msg = err.message.orEmpty()
        return when {
            msg.contains("403") -> "Limite temporário de consultas ao GitHub atingido. Tente novamente em instantes."
            msg.contains("404") -> "Nenhuma release pública encontrada no repositório."
            else -> "Sem conexão ou falha ao consultar atualizações (${err.javaClass.simpleName})."
        }
    }
}
