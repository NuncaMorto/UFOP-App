package br.ufop.campus.ui.security

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.core.ffi.PersistenceLoadErrorState
import br.ufop.campus.core.security.CampusCryptoEngine
import br.ufop.campus.core.storage.GradeExportStorage
import br.ufop.campus.ui.profile.syncThemeAndA11yAfterBackupRestore
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tela de recuperação exibida quando os arquivos locais salvos existem, mas nenhum pôde ser
 * descriptografado ou decodificado na inicialização.
 * Enquanto estiver ativa, a persistência permanece travada para impedir sobrescrita acidental.
 */
@Composable
fun PersistenceRecoveryScreen(
    repository: CampusCoreRepository,
    errorState: PersistenceLoadErrorState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeController = LocalCampusThemeController.current
    val accessibilityController = LocalCampusAccessibility.current
    val minTouchTarget = if (accessibilityController.settings.largeTouchTargets) 56.dp else 48.dp

    var isBusy by remember { mutableStateOf(false) }
    var feedbackMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var feedbackIsError by rememberSaveable { mutableStateOf(false) }

    var pendingBackupUri by remember { mutableStateOf<Uri?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordVisible by remember { mutableStateOf(false) }
    var backupErrorMessage by remember { mutableStateOf<String?>(null) }
    var showResetConfirmDialog by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = true) { /* Mantém na tela de recuperação até escolha explícita */ }

    val backupPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pendingBackupUri = uri
        backupPassword = ""
        backupPasswordVisible = false
        backupErrorMessage = null
        feedbackMessage = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Não foi possível abrir seus dados salvos",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = errorState.reason
                        ?: "Seus arquivos originais foram preservados intactos para evitar perda de informações.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Proteção contra sobrescrita ativa",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Nenhuma alteração será gravada por cima dos seus dados até você tentar novamente, restaurar um backup ou optar por recomeçar do zero.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (errorState.quarantinedFiles.isNotEmpty()) {
                            Text(
                                text = "Cópias preservadas: ${errorState.quarantinedFiles.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (!feedbackMessage.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = if (feedbackIsError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Text(
                        text = feedbackMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (feedbackIsError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (isBusy) return@Button
                        isBusy = true
                        feedbackMessage = null
                        scope.launch {
                            val recovered = withContext(Dispatchers.IO) {
                                runCatching { repository.retryLoadPersistedState() }.getOrDefault(false)
                            }
                            isBusy = false
                            if (!recovered) {
                                feedbackIsError = true
                                feedbackMessage =
                                    "Ainda não foi possível abrir os dados salvos. Tente novamente após desbloquear o aparelho ou restaure um arquivo .ufopbackup."
                            }
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Tentar novamente")
                }

                OutlinedButton(
                    onClick = {
                        if (!isBusy) {
                            backupPickerLauncher.launch(arrayOf("*/*"))
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restaurar backup (.ufopbackup)")
                }

                TextButton(
                    onClick = {
                        if (!isBusy) {
                            showResetConfirmDialog = true
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTouchTarget),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Recomeçar do zero")
                }
            }
        }
    }

    val backupUri = pendingBackupUri
    if (backupUri != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isBusy) {
                    pendingBackupUri = null
                    backupPassword = ""
                    backupErrorMessage = null
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.LockReset,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Restaurar backup (.ufopbackup)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Digite a senha usada ao criar o backup para recuperar seu perfil, disciplinas, horários e histórico.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = {
                            backupPassword = it
                            backupErrorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Senha do backup") },
                        singleLine = true,
                        enabled = !isBusy,
                        isError = backupErrorMessage != null,
                        visualTransformation = if (backupPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { backupPasswordVisible = !backupPasswordVisible },
                                modifier = Modifier.size(minTouchTarget)
                            ) {
                                Icon(
                                    imageVector = if (backupPasswordVisible) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = if (backupPasswordVisible) {
                                        "Ocultar senha"
                                    } else {
                                        "Mostrar senha"
                                    }
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(14.dp)
                    )
                    if (!backupErrorMessage.isNullOrBlank()) {
                        Text(
                            text = backupErrorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAbsentRed
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isBusy && backupPassword.isNotBlank(),
                    modifier = Modifier.heightIn(min = minTouchTarget),
                    onClick = {
                        isBusy = true
                        backupErrorMessage = null
                        val passwordAttempt = backupPassword
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val rawPayload = GradeExportStorage.readPayloadFromUriSafely(
                                        context = context,
                                        uri = backupUri
                                    )
                                    if (!rawPayload.trim().startsWith(CampusCryptoEngine.BACKUP_PREFIX)) {
                                        throw IllegalArgumentException(
                                            "Arquivo inválido. Selecione um backup (.ufopbackup) gerado pelo aplicativo."
                                        )
                                    }
                                    val chars = passwordAttempt.toCharArray()
                                    val decryptedJson = try {
                                        CampusCryptoEngine.decryptBackupPayload(rawPayload, chars)
                                    } finally {
                                        chars.fill('\u0000')
                                    }
                                    val restored = repository.restoreFullAccountSnapshotJson(decryptedJson)
                                    if (!restored) {
                                        throw IllegalStateException(
                                            "Não foi possível aplicar ou gravar o backup selecionado."
                                        )
                                    }
                                }
                            }
                            isBusy = false
                            result.onSuccess {
                                syncThemeAndA11yAfterBackupRestore(
                                    context = context,
                                    repository = repository,
                                    themeController = themeController,
                                    accessibilityController = accessibilityController
                                )
                                val profile = repository.getStudentProfile()
                                if (!profile.onboardingCompleted) {
                                    repository.saveStudentProfile(
                                        profile.copy(onboardingCompleted = true)
                                    )
                                }
                                if (repository.getLastSeenReleaseVersion().isBlank()) {
                                    CampusRepositoryHolder.setLastSeenReleaseVersion(
                                        context = context,
                                        repository = repository,
                                        version = CampusRepositoryHolder.CURRENT_APP_VERSION
                                    )
                                }
                                pendingBackupUri = null
                                backupPassword = ""
                                backupErrorMessage = null
                            }.onFailure { err ->
                                backupErrorMessage = err.message
                                    ?: "Senha incorreta ou arquivo de backup inválido."
                            }
                        }
                    }
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Restaurar")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isBusy,
                    modifier = Modifier.heightIn(min = minTouchTarget),
                    onClick = {
                        pendingBackupUri = null
                        backupPassword = ""
                        backupErrorMessage = null
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isBusy) {
                    showResetConfirmDialog = false
                }
            },
            title = {
                Text(
                    text = "Recomeçar do zero?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Isso irá limpar os arquivos principais com erro para iniciar um novo cadastro. As cópias de segurança preservadas permanecerão salvas na pasta do app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    enabled = !isBusy,
                    modifier = Modifier.heightIn(min = minTouchTarget),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    onClick = {
                        isBusy = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                repository.resetStateAfterLoadErrorAndUnlockPersistence()
                            }
                            isBusy = false
                            showResetConfirmDialog = false
                        }
                    }
                ) {
                    Text("Confirmar e recomeçar")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isBusy,
                    modifier = Modifier.heightIn(min = minTouchTarget),
                    onClick = { showResetConfirmDialog = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}
