package br.ufop.campus.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.security.AppLockManager
import br.ufop.campus.core.security.CampusCryptoEngine
import br.ufop.campus.core.storage.GradeExportStorage
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.StatusCancelledAmber
import br.ufop.campus.ui.theme.accessibleTextColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SecurityAndPrivacySubView(
    repository: CampusCoreRepository,
    activeSemester: String,
    themeController: br.ufop.campus.ui.theme.CampusThemeController,
    accessibilityController: br.ufop.campus.ui.theme.CampusAccessibilityController,
    onAppSecurityChanged: () -> Unit,
    onBackupRestored: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = LocalCampusThemeController.current.isDark
    val surfaceColor = MaterialTheme.colorScheme.surface
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = campusMinTouchTarget()
    val safeGreenText = accessibleTextColor(StatusAttendingGreen, isDark, surfaceColor)
    val accessibleRed = accessibleTextColor(StatusAbsentRed, isDark, surfaceColor)

    var lockStatus by remember { mutableStateOf(AppLockManager.getStatus(context)) }
    var securityFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var securityFeedbackIsError by remember { mutableStateOf(false) }

    var showEnableLockDialog by remember { mutableStateOf(false) }
    var showDisableLockDialog by remember { mutableStateOf(false) }

    var showExportBackupDialog by remember { mutableStateOf(false) }
    var isExportingBackup by remember { mutableStateOf(false) }
    var lastExportedPath by remember { mutableStateOf<String?>(null) }
    var lastExportedShareUri by remember { mutableStateOf<Uri?>(null) }
    var lastExportedFilename by remember { mutableStateOf("campus_ufop_backup.ufopbackup") }

    var pendingRestorePayload by remember { mutableStateOf<String?>(null) }
    var isReadingBackupFile by remember { mutableStateOf(false) }
    var isRestoringBackup by remember { mutableStateOf(false) }
    var backupFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var backupFeedbackIsError by remember { mutableStateOf(false) }

    val restoreBackupPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isReadingBackupFile = true
        backupFeedbackMessage = null
        backupFeedbackIsError = false
        scope.launch {
            val readResult = withContext(Dispatchers.IO) {
                runCatching {
                    GradeExportStorage.readPayloadFromUriSafely(context, uri)
                }
            }
            isReadingBackupFile = false
            readResult.onSuccess { payload ->
                if (!payload.trim().startsWith(CampusCryptoEngine.BACKUP_PREFIX)) {
                    backupFeedbackIsError = true
                    backupFeedbackMessage =
                        "Arquivo inválido. Selecione um arquivo de backup (.ufopbackup) gerado pelo aplicativo."
                } else {
                    pendingRestorePayload = payload
                }
            }.onFailure { err ->
                backupFeedbackIsError = true
                backupFeedbackMessage = err.message ?: "Não foi possível ler o arquivo selecionado."
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Card de status da proteção ativa
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = StatusAttendingGreen.copy(alpha = if (isDark) 0.14f else 0.08f),
            border = BorderStroke(1.dp, safeGreenText.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        tint = safeGreenText,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Proteção local ativa",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = safeGreenText,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Seus dados acadêmicos ficam salvos e protegidos apenas neste aparelho",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = safeGreenText,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "PDFs importados são apagados automaticamente logo após a leitura",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 2. Controle de Bloqueio do App por Senha e Digital
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = surfaceColor,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bloqueio do App por Senha e Digital",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "Pede sua senha ou digital ao abrir o aplicativo para proteger suas notas, histórico e agenda.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!lockStatus.lockEnabled) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minTouchTarget)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showEnableLockDialog = true },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ativar bloqueio por senha",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (lockStatus.biometricHardwareAvailable) {
                                        "Senha de pelo menos 4 caracteres + opção de Digital / Biometria"
                                    } else {
                                        "Senha de pelo menos 4 caracteres salva no aparelho"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = false,
                                onCheckedChange = { showEnableLockDialog = true },
                                modifier = Modifier.heightIn(min = minTouchTarget),
                                colors = campusSwitchColors()
                            )
                        }
                    }

                    Button(
                        onClick = { showEnableLockDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minTouchTarget),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ativar bloqueio por senha",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = StatusAttendingGreen.copy(alpha = if (isDark) 0.16f else 0.12f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = safeGreenText,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Bloqueio por senha ativo neste aparelho",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = safeGreenText
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minTouchTarget),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = "Desbloqueio por Digital / Biometria",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (lockStatus.biometricHardwareAvailable) {
                                            if (lockStatus.biometricEnabled) "Ativado" else "Desativado"
                                        } else {
                                            "Biometria indisponível ou não cadastrada neste aparelho"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = lockStatus.biometricEnabled,
                                enabled = lockStatus.biometricHardwareAvailable,
                                onCheckedChange = { enableBio ->
                                    val res = AppLockManager.setBiometricEnabled(
                                        context = context,
                                        currentPasswordOrNull = null,
                                        enabled = enableBio
                                    )
                                    res.onSuccess {
                                        lockStatus = AppLockManager.getStatus(context)
                                        securityFeedbackIsError = false
                                        securityFeedbackMessage = if (enableBio) {
                                            "Desbloqueio por Digital / Biometria ativado."
                                        } else {
                                            "Desbloqueio por Digital / Biometria desativado."
                                        }
                                        onAppSecurityChanged()
                                    }.onFailure { err ->
                                        securityFeedbackIsError = true
                                        securityFeedbackMessage = err.message ?: "Não foi possível alterar a biometria."
                                    }
                                },
                                modifier = Modifier.heightIn(min = minTouchTarget),
                                colors = campusSwitchColors()
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showDisableLockDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minTouchTarget),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, accessibleRed.copy(alpha = 0.55f))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LockOpen,
                            contentDescription = null,
                            tint = accessibleRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Desativar bloqueio por senha",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = accessibleRed
                        )
                    }
                }

                if (securityFeedbackMessage != null) {
                    StatusFeedbackBanner(
                        message = securityFeedbackMessage!!,
                        isError = securityFeedbackIsError,
                        onDismiss = { securityFeedbackMessage = null }
                    )
                }
            }
        }

        // 3. Cópia de segurança protegida da conta (.ufopbackup)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = surfaceColor,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Backup protegido da conta (.ufopbackup)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Salve uma cópia completa da sua conta (perfil, disciplinas, faltas, histórico, notas, calendário e preferências) protegida com uma senha escolhida por você para restaurar quando trocar de celular.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { showExportBackupDialog = true },
                    enabled = !isExportingBackup && !isRestoringBackup,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Gerar e baixar backup protegido",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { restoreBackupPickerLauncher.launch(arrayOf("*/*")) },
                    enabled = !isReadingBackupFile && !isRestoringBackup && !isExportingBackup,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    if (isReadingBackupFile) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Restaurar backup protegido",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (lastExportedPath != null && lastExportedShareUri != null) {
                    FilledTonalButton(
                        onClick = {
                            shareBackupUri(context, lastExportedShareUri!!, lastExportedFilename)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minTouchTarget),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Compartilhar backup salvo",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (backupFeedbackMessage != null) {
                    StatusFeedbackBanner(
                        message = backupFeedbackMessage!!,
                        isError = backupFeedbackIsError,
                        onDismiss = { backupFeedbackMessage = null }
                    )
                }
            }
        }
    }

    if (showEnableLockDialog) {
        EnableAppLockDialog(
            biometricAvailable = lockStatus.biometricHardwareAvailable,
            onDismiss = { showEnableLockDialog = false },
            onConfirm = { password, allowBiometric ->
                val result = AppLockManager.enableLock(context, password, allowBiometric)
                result.onSuccess {
                    lockStatus = AppLockManager.getStatus(context)
                    showEnableLockDialog = false
                    securityFeedbackIsError = false
                    securityFeedbackMessage = if (allowBiometric && lockStatus.biometricHardwareAvailable) {
                        "Bloqueio por senha e digital ativado!"
                    } else {
                        "Bloqueio por senha ativado!"
                    }
                    onAppSecurityChanged()
                }.onFailure { err ->
                    securityFeedbackIsError = true
                    securityFeedbackMessage = err.message ?: "Não foi possível ativar o bloqueio."
                }
            }
        )
    }

    if (showDisableLockDialog) {
        DisableAppLockDialog(
            onDismiss = { showDisableLockDialog = false },
            onVerifyAndDisable = { currentPassword ->
                when (val verify = AppLockManager.disableLock(context, currentPassword)) {
                    is AppLockManager.VerifyLockResult.Success -> {
                        lockStatus = AppLockManager.getStatus(context)
                        showDisableLockDialog = false
                        securityFeedbackIsError = false
                        securityFeedbackMessage = "Bloqueio por senha desativado."
                        onAppSecurityChanged()
                        null
                    }
                    is AppLockManager.VerifyLockResult.Failed -> {
                        if (verify.lockoutMs > 0L) {
                            val seconds = (verify.lockoutMs + 999L) / 1000L
                            "Muitas tentativas incorretas. Aguarde ${seconds}s."
                        } else {
                            "Senha incorreta (${verify.attemptsRemaining} tentativas restantes)."
                        }
                    }
                }
            }
        )
    }

    if (showExportBackupDialog) {
        ExportEncryptedBackupDialog(
            isExporting = isExportingBackup,
            onDismiss = { if (!isExportingBackup) showExportBackupDialog = false },
            onConfirm = { password ->
                isExportingBackup = true
                backupFeedbackMessage = null
                backupFeedbackIsError = false
                val filename = "campus_ufop_backup_${activeSemester.replace(".", "_")}.ufopbackup"
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val json = repository.exportFullAccountSnapshotJson()
                            val encrypted = CampusCryptoEngine.encryptBackupPayload(json, password.toCharArray())
                            GradeExportStorage.saveEncryptedBackup(
                                context = context,
                                repository = repository,
                                filename = filename,
                                encryptedPayload = encrypted
                            )
                        }
                    }
                    isExportingBackup = false
                    result.onSuccess { saved ->
                        lastExportedPath = saved.displayPath
                        lastExportedShareUri = saved.shareUri
                        lastExportedFilename = filename
                        showExportBackupDialog = false
                        backupFeedbackIsError = false
                        backupFeedbackMessage = "Backup salvo em: ${saved.displayPath}"
                    }.onFailure { err ->
                        backupFeedbackIsError = true
                        backupFeedbackMessage = err.message ?: "Falha ao gerar o backup."
                    }
                }
            }
        )
    }

    pendingRestorePayload?.let { payload ->
        RestoreEncryptedBackupDialog(
            isRestoring = isRestoringBackup,
            onDismiss = {
                if (!isRestoringBackup) {
                    pendingRestorePayload = null
                }
            },
            onConfirm = { password, onError ->
                isRestoringBackup = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val decryptedJson = CampusCryptoEngine.decryptBackupPayload(
                                encryptedBundle = payload,
                                password = password.toCharArray()
                            )
                            val restored = repository.restoreFullAccountSnapshotJson(decryptedJson)
                            if (!restored) {
                                throw IllegalStateException("Dados do backup inválidos ou incompatíveis.")
                            }
                        }
                    }
                    isRestoringBackup = false
                    result.onSuccess {
                        syncThemeAndA11yAfterBackupRestore(
                            context = context,
                            repository = repository,
                            themeController = themeController,
                            accessibilityController = accessibilityController
                        )
                        onBackupRestored()
                        pendingRestorePayload = null
                        backupFeedbackIsError = false
                        backupFeedbackMessage = "Backup restaurado com sucesso! Dados, tema e acessibilidade sincronizados."
                    }.onFailure { err ->
                        onError(err.message ?: "Senha incorreta ou backup corrompido.")
                    }
                }
            }
        )
    }
}
@Composable
internal fun LogoutAccountFlowDialogs(
    step: LogoutStep,
    backupPassword: String,
    onBackupPasswordChange: (String) -> Unit,
    backupError: String?,
    isExportingBackup: Boolean,
    backupSavedPath: String?,
    backupShareUri: Uri?,
    backupShareFilename: String,
    isClearingAccount: Boolean,
    onDownloadBackupAndContinue: () -> Unit,
    onContinueWithoutBackup: () -> Unit,
    onShareBackup: () -> Unit,
    onConfirmFinalLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    val accessibleRed = accessibleTextColor(StatusAbsentRed, isDark, MaterialTheme.colorScheme.surface)
    val accessibleAmber = accessibleTextColor(StatusCancelledAmber, isDark, MaterialTheme.colorScheme.surface)

    when (step) {
        LogoutStep.NONE -> Unit

        LogoutStep.BACKUP_PROMPT -> {
            var showPassword by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = accessibleAmber
                    )
                },
                title = {
                    Text(
                        text = "Sair da conta · Backup antes de apagar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Ao sair da conta, todos os dados salvos neste aparelho serão apagados (disciplinas, faltas, histórico, agenda, bloqueio e preferências).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Se quiser restaurar sua conta depois, defina uma senha abaixo e baixe um backup protegido (.ufopbackup):",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedTextField(
                            value = backupPassword,
                            onValueChange = onBackupPasswordChange,
                            enabled = !isExportingBackup,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Senha do backup (mín. 4 caracteres)") },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showPassword = !showPassword },
                                    modifier = Modifier.size(minTouchTarget)
                                ) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (showPassword) "Ocultar senha" else "Mostrar senha"
                                    )
                                }
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                        if (backupError != null) {
                            Text(
                                text = backupError,
                                style = MaterialTheme.typography.bodySmall,
                                color = accessibleRed
                            )
                        }

                        Button(
                            onClick = onDownloadBackupAndContinue,
                            enabled = !isExportingBackup,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = minTouchTarget),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            if (isExportingBackup) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Baixar backup protegido (.ufopbackup)")
                        }

                        OutlinedButton(
                            onClick = onContinueWithoutBackup,
                            enabled = !isExportingBackup,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = minTouchTarget),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Text("Continuar sem backup")
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isExportingBackup,
                        modifier = Modifier.heightIn(min = minTouchTarget),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }

        LogoutStep.FINAL_CONFIRM -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                        contentDescription = null,
                        tint = accessibleRed
                    )
                },
                title = {
                    Text(
                        text = "Confirmar saída da conta",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (backupSavedPath != null) {
                            StatusFeedbackBanner(
                                message = "Backup salvo com sucesso: $backupSavedPath",
                                isError = false
                            )
                            if (backupShareUri != null) {
                                TextButton(
                                    onClick = onShareBackup,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = minTouchTarget)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Compartilhar $backupShareFilename antes de sair")
                                }
                            }
                        } else {
                            Text(
                                text = "Backup dispensado: os dados locais não poderão ser recuperados após confirmar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = accessibleAmber,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = "Deseja mesmo sair da conta e fechar o aplicativo?",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onConfirmFinalLogout,
                        enabled = !isClearingAccount,
                        modifier = Modifier.heightIn(min = minTouchTarget),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusAbsentRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        if (isClearingAccount) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Sim, sair e fechar app")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isClearingAccount,
                        modifier = Modifier.heightIn(min = minTouchTarget),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
