package br.ufop.campus.ui.profile

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
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.accessibleTextColor

@Composable
internal fun EnableAppLockDialog(
    biometricAvailable: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (password: String, allowBiometric: Boolean) -> Unit
) {
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var allowBiometric by remember { mutableStateOf(biometricAvailable) }
    var showPassword by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Ativar bloqueio por senha",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Defina uma senha com pelo menos 4 caracteres para proteger a abertura do aplicativo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        validationError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nova senha (mín. 4 caracteres)") },
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

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        validationError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirmar senha") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    shape = RoundedCornerShape(16.dp)
                )

                if (biometricAvailable) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minTouchTarget)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { allowBiometric = !allowBiometric }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Liberar Digital / Biometria",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Usar leitor biométrico para desbloquear",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = allowBiometric,
                            onCheckedChange = { allowBiometric = it },
                            modifier = Modifier.heightIn(min = minTouchTarget),
                            colors = campusSwitchColors()
                        )
                    }
                }

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = accessibleTextColor(StatusAbsentRed, isDark, MaterialTheme.colorScheme.surface)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        password.trim().length < 4 -> {
                            validationError = "A senha deve ter pelo menos 4 caracteres."
                        }
                        password != confirmPassword -> {
                            validationError = "As senhas informadas não coincidem."
                        }
                        else -> {
                            onConfirm(password, allowBiometric && biometricAvailable)
                        }
                    }
                },
                modifier = Modifier.heightIn(min = minTouchTarget),
                shape = RoundedCornerShape(22.dp)
            ) {
                Text("Ativar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
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
@Composable
internal fun DisableAppLockDialog(
    onDismiss: () -> Unit,
    onVerifyAndDisable: (currentPassword: String) -> String?
) {
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    var currentPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Desativar bloqueio por senha",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Confirme sua senha atual para remover o bloqueio do aplicativo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        errorText = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Senha atual") },
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
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = accessibleTextColor(StatusAbsentRed, isDark, MaterialTheme.colorScheme.surface)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentPassword.isBlank()) {
                        errorText = "Informe sua senha atual."
                    } else {
                        errorText = onVerifyAndDisable(currentPassword)
                    }
                },
                modifier = Modifier.heightIn(min = minTouchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusAbsentRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(22.dp)
            ) {
                Text("Desativar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
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
@Composable
internal fun ExportEncryptedBackupDialog(
    isExporting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit
) {
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Gerar backup protegido",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Crie uma senha (mín. 4 caracteres) para proteger seu arquivo de backup (.ufopbackup). Guarde essa senha para restaurar seus dados depois.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorText = null
                    },
                    enabled = !isExporting,
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
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorText = null
                    },
                    enabled = !isExporting,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirmar senha do backup") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    shape = RoundedCornerShape(16.dp)
                )
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = accessibleTextColor(StatusAbsentRed, isDark, MaterialTheme.colorScheme.surface)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        password.trim().length < 4 -> {
                            errorText = "A senha deve ter pelo menos 4 caracteres."
                        }
                        password != confirmPassword -> {
                            errorText = "As senhas não coincidem."
                        }
                        else -> onConfirm(password)
                    }
                },
                enabled = !isExporting,
                modifier = Modifier.heightIn(min = minTouchTarget),
                shape = RoundedCornerShape(22.dp)
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("Gerar e baixar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isExporting,
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
@Composable
internal fun RestoreEncryptedBackupDialog(
    isRestoring: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (password: String, onError: (String) -> Unit) -> Unit
) {
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Restaurar backup protegido",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Informe a senha usada quando este arquivo .ufopbackup foi gerado. Os dados atuais do aparelho serão substituídos pelos do backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorText = null
                    },
                    enabled = !isRestoring,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Senha do backup") },
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
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = accessibleTextColor(StatusAbsentRed, isDark, MaterialTheme.colorScheme.surface)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password.isBlank()) {
                        errorText = "Informe a senha do backup."
                    } else {
                        onConfirm(password) { msg -> errorText = msg }
                    }
                },
                enabled = !isRestoring,
                modifier = Modifier.heightIn(min = minTouchTarget),
                shape = RoundedCornerShape(22.dp)
            ) {
                if (isRestoring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("Restaurar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isRestoring,
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
