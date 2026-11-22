package br.ufop.campus.ui.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.security.AppLockManager
import kotlinx.coroutines.delay

/**
 * Tela cheia de bloqueio Material 3 exibida enquanto o aplicativo estiver travado por senha/biometria.
 * Impede visualização ou navegação no conteúdo subjacente até validação bem-sucedida.
 */
@Composable
fun AppLockOverlay(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var status by remember { mutableStateOf(AppLockManager.getStatus(context)) }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var lockoutRemainingMs by remember { mutableLongStateOf(status.lockoutRemainingMs) }
    var biometricAutoLaunched by rememberSaveable { mutableStateOf(false) }

    val isLockedOut = lockoutRemainingMs > 0L
    val lockoutSeconds = ((lockoutRemainingMs + 999L) / 1000L).coerceAtLeast(0L)
    val canUseBiometric = status.biometricEnabled && status.biometricHardwareAvailable && activity != null

    // Bloqueia botão Voltar físico/gesto enquanto o app estiver trancado.
    BackHandler(enabled = true) { /* Mantém bloqueado */ }

    fun triggerBiometric() {
        val hostActivity = activity ?: return
        errorMessage = null
        AppLockManager.launchBiometricPrompt(
            activity = hostActivity,
            onSuccess = {
                password = ""
                errorMessage = null
                onUnlocked()
            },
            onFailure = { message ->
                errorMessage = message
            },
            onCancel = {
                // Usuário optou por digitar a senha manualmente.
            }
        )
    }

    fun attemptPasswordUnlock() {
        if (isLockedOut) return
        if (password.isEmpty()) {
            errorMessage = "Digite sua senha para desbloquear."
            return
        }
        when (val result = AppLockManager.verifyPassword(context, password)) {
            is AppLockManager.VerifyLockResult.Success -> {
                password = ""
                errorMessage = null
                lockoutRemainingMs = 0L
                onUnlocked()
            }
            is AppLockManager.VerifyLockResult.Failed -> {
                status = AppLockManager.getStatus(context)
                password = ""
                if (result.lockoutMs > 0L) {
                    lockoutRemainingMs = result.lockoutMs
                    val seconds = ((result.lockoutMs + 999L) / 1000L).coerceAtLeast(1L)
                    errorMessage = "Muitas tentativas incorretas. Aguarde ${formatCooldown(seconds)} para tentar novamente."
                } else {
                    errorMessage = if (result.attemptsRemaining > 0) {
                        val plural = if (result.attemptsRemaining == 1) "tentativa restante" else "tentativas restantes"
                        "Senha incorreta. ${result.attemptsRemaining} $plural antes do bloqueio temporário."
                    } else {
                        "Senha incorreta. Tente novamente."
                    }
                }
            }
        }
    }

    // Contagem regressiva em tempo real do cooldown após tentativas incorretas.
    LaunchedEffect(lockoutRemainingMs > 0L) {
        while (lockoutRemainingMs > 0L) {
            delay(1_000L)
            val updated = AppLockManager.getStatus(context)
            status = updated
            lockoutRemainingMs = updated.lockoutRemainingMs
            if (updated.lockoutRemainingMs <= 0L) {
                errorMessage = null
            }
        }
    }

    // Dispara automaticamente o leitor biométrico ao exibir a tela de bloqueio, se habilitado.
    LaunchedEffect(canUseBiometric, isLockedOut) {
        if (canUseBiometric && !isLockedOut && !biometricAutoLaunched) {
            biometricAutoLaunched = true
            triggerBiometric()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Aplicativo bloqueado",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Text(
                    text = "Vida Acadêmica bloqueado",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )

                Text(
                    text = "Confirme sua identidade para visualizar sua grade, histórico e documentos acadêmicos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                if (!isLockedOut) errorMessage = null
                            },
                            label = { Text("Senha do aplicativo") },
                            placeholder = { Text("Digite sua senha") },
                            singleLine = true,
                            enabled = !isLockedOut,
                            isError = errorMessage != null,
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    modifier = Modifier.semantics {
                                        contentDescription = if (passwordVisible) {
                                            "Ocultar senha"
                                        } else {
                                            "Mostrar senha"
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (passwordVisible) {
                                            Icons.Rounded.VisibilityOff
                                        } else {
                                            Icons.Rounded.Visibility
                                        },
                                        contentDescription = null
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { attemptPasswordUnlock() }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription = "Campo de senha para desbloquear o aplicativo"
                                }
                        )

                        val feedbackText = when {
                            isLockedOut -> "Bloqueio temporário ativo. Tente novamente em ${formatCooldown(lockoutSeconds)}."
                            !errorMessage.isNullOrBlank() -> errorMessage
                            else -> null
                        }

                        if (feedbackText != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        liveRegion = LiveRegionMode.Polite
                                        contentDescription = feedbackText
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = feedbackText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { attemptPasswordUnlock() },
                            enabled = !isLockedOut && password.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .semantics {
                                    contentDescription = "Desbloquear aplicativo com senha"
                                }
                        ) {
                            Text(
                                text = if (isLockedOut) {
                                    "Aguarde ${formatCooldown(lockoutSeconds)}"
                                } else {
                                    "Desbloquear"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (canUseBiometric) {
                            OutlinedButton(
                                onClick = { triggerBiometric() },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .semantics {
                                        contentDescription = "Desbloquear com Digital ou Biometria"
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Fingerprint,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Desbloquear com Digital / Biometria",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatCooldown(seconds: Long): String {
    val mins = seconds / 60L
    val secs = seconds % 60L
    return if (mins > 0L) {
        "${mins}min ${secs}s"
    } else {
        "${secs}s"
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext?.findActivity()
    else -> null
}
