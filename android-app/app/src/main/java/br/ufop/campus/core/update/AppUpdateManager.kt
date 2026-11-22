package br.ufop.campus.core.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
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
 * - Não envia notificações externas na barra do sistema Android.
 */
object AppUpdateManager {

    private const val PREFS_NAME = "campus_app_updater_prefs"
    private const val KEY_LAST_CHECK_MS = "last_check_epoch_ms"
    private const val KEY_DISMISSED_TAG = "dismissed_tag"
    private const val KEY_BETA_CHANNEL = "include_beta_channel"
    private const val KEY_PENDING_RELAUNCH = "pending_auto_relaunch"
    private const val KEY_PENDING_APK_PATH = "pending_install_apk_path"

    private const val AUTO_CHECK_INTERVAL_MS = 2L * 60L * 60L * 1000L // 2 horas
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_REDIRECTS = 6

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeCheckJob: Job? = null
    private var activeDownloadJob: Job? = null

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

    fun onAppResumed(context: Context) {
        val appContext = context.applicationContext
        val current = _state.value
        if (current is AppUpdateUiState.NeedsInstallPermission &&
            appContext.packageManager.canRequestPackageInstalls()
        ) {
            val apkFile = File(current.apkFilePath)
            if (apkFile.isFile) {
                scope.launch {
                    triggerInstallOrRequestPermission(appContext, current.info, apkFile)
                }
            } else {
                _state.value = AppUpdateUiState.Available(info = current.info, bannerDismissed = false)
            }
        }
    }

    fun retryInstallIfPermitted(context: Context) {
        val appContext = context.applicationContext
        val current = _state.value
        if (current is AppUpdateUiState.NeedsInstallPermission) {
            val apkFile = File(current.apkFilePath)
            if (apkFile.isFile && appContext.packageManager.canRequestPackageInstalls()) {
                scope.launch {
                    triggerInstallOrRequestPermission(appContext, current.info, apkFile)
                }
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

    fun retryFromError(context: Context) {
        val current = _state.value
        if (current is AppUpdateUiState.Error && current.info != null) {
            startBackgroundDownloadAndInstall(context, current.info)
        } else {
            checkForUpdates(context, force = true)
        }
    }

    fun purgeCachedApks(context: Context) {
        val current = _state.value
        if (current is AppUpdateUiState.Downloading ||
            current is AppUpdateUiState.Verifying ||
            current is AppUpdateUiState.Installing ||
            current is AppUpdateUiState.NeedsInstallPermission
        ) {
            return
        }
        scope.launch {
            purgeDirectoryFiles(updatesCacheDir(context))
        }
    }

    internal fun consumePendingAutoRelaunch(context: Context): Boolean {
        val p = prefs(context)
        val pending = p.getBoolean(KEY_PENDING_RELAUNCH, false)
        p.edit()
            .putBoolean(KEY_PENDING_RELAUNCH, false)
            .remove(KEY_PENDING_APK_PATH)
            .apply()
        return pending
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
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching {
                        appContext.startActivity(confirmIntent)
                    }.onFailure {
                        fallbackInstallPendingApk(appContext, currentInfo)
                    }
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                prefs(appContext).edit()
                    .putBoolean(KEY_PENDING_RELAUNCH, false)
                    .remove(KEY_PENDING_APK_PATH)
                    .apply()
                _state.value = AppUpdateUiState.Idle
                _isDialogOpen.value = false
                purgeDirectoryFiles(updatesCacheDir(appContext))
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                prefs(appContext).edit().putBoolean(KEY_PENDING_RELAUNCH, false).apply()
                if (currentInfo != null) {
                    _state.value = AppUpdateUiState.Available(info = currentInfo, bannerDismissed = false)
                } else {
                    _state.value = AppUpdateUiState.Idle
                }
            }

            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                prefs(appContext).edit().putBoolean(KEY_PENDING_RELAUNCH, false).apply()
                _state.value = AppUpdateUiState.Error(
                    message = "A assinatura do novo APK difere da versão instalada. Seus dados continuam intactos; exporte um backup (.ufopbackup) antes de reinstalar.",
                    info = currentInfo
                )
                _isDialogOpen.value = true
            }

            else -> {
                prefs(appContext).edit().putBoolean(KEY_PENDING_RELAUNCH, false).apply()
                val detail = statusMessage?.takeIf { it.isNotBlank() } ?: "código $status"
                _state.value = AppUpdateUiState.Error(
                    message = "Não foi possível concluir a instalação automática ($detail).",
                    info = currentInfo
                )
                _isDialogOpen.value = true
            }
        }
    }

    private fun triggerInstallOrRequestPermission(
        appContext: Context,
        info: AppUpdateReleaseInfo,
        apkFile: File
    ) {
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            prefs(appContext).edit().putString(KEY_PENDING_APK_PATH, apkFile.absolutePath).apply()
            _state.value = AppUpdateUiState.NeedsInstallPermission(
                info = info,
                apkFilePath = apkFile.absolutePath
            )
            _isDialogOpen.value = true
            return
        }

        prefs(appContext).edit()
            .putBoolean(KEY_PENDING_RELAUNCH, true)
            .putString(KEY_PENDING_APK_PATH, apkFile.absolutePath)
            .apply()
        _state.value = AppUpdateUiState.Installing(info)

        runCatching {
            val packageInstaller = appContext.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(appContext.packageName)
                setSize(apkFile.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
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
                val callbackIntent = Intent(appContext, AppUpdateInstallReceiver::class.java)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val pendingIntent = PendingIntent.getBroadcast(appContext, sessionId, callbackIntent, flags)
                session.commit(pendingIntent.intentSender)
            }
        }.onFailure {
            fallbackInstallPendingApk(appContext, info)
        }
    }

    private fun fallbackInstallPendingApk(appContext: Context, info: AppUpdateReleaseInfo?) {
        val path = prefs(appContext).getString(KEY_PENDING_APK_PATH, null) ?: return
        val file = File(path)
        if (!file.isFile) return
        val opened = openInstallFallbackViaFileProvider(appContext, file)
        if (!opened) {
            _state.value = AppUpdateUiState.Error(
                message = "Falha ao abrir o instalador do sistema.",
                info = info
            )
            _isDialogOpen.value = true
        }
    }

    fun openInstallFallbackViaFileProvider(context: Context, apkFile: File): Boolean {
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

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
