package br.ufop.campus.ui.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.ufop.campus.BuildConfig
import br.ufop.campus.core.update.AppUpdateManager
import br.ufop.campus.core.update.AppUpdateReleaseInfo
import br.ufop.campus.core.update.AppUpdateUiState
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import java.io.File
import java.util.Locale

@Composable
private fun updaterMinTouchTarget() =
    if (LocalCampusAccessibility.current.settings.largeTouchTargets) 56.dp else 48.dp

/**
 * Faixa discreta dentro do próprio app quando há atualização ou download em segundo plano.
 */
@Composable
fun InAppUpdateBanner(
    state: AppUpdateUiState,
    onOpenDetails: () -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val minTouch = updaterMinTouchTarget()

    when (state) {
        is AppUpdateUiState.Available -> {
            if (state.bannerDismissed) return
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenDetails),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.info.isPrerelease) {
                                "Atualização Beta ${state.info.tagName} disponível"
                            } else {
                                "Nova versão ${state.info.tagName} disponível"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Toque para baixar e instalar sem perder seus dados.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = onOpenDetails,
                        modifier = Modifier.heightIn(min = minTouch)
                    ) {
                        Text(
                            text = "Atualizar",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onDismissBanner,
                        modifier = Modifier
                            .size(minTouch)
                            .semantics { contentDescription = "Dispensar aviso de atualização" }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        is AppUpdateUiState.Downloading -> {
            if (!state.minimized) return
            val pct = (state.progress * 100f).toInt().coerceIn(0, 100)
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenDetails),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Baixando ${state.info.tagName} em segundo plano...",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (state.totalBytes > 0L) "$pct%" else formatBytes(state.downloadedBytes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (state.totalBytes > 0L) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )
                    }
                }
            }
        }

        is AppUpdateUiState.Verifying -> {
            if (!state.minimized) return
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenDetails),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Verificando integridade SHA-256 de ${state.info.tagName}...",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        else -> Unit
    }
}

/**
 * Diálogo completo de atualização com progresso, checagem SHA-256 e instalação direta.
 */
@Composable
fun AppUpdateDialog(
    state: AppUpdateUiState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val minTouch = updaterMinTouchTarget()

    Dialog(
        onDismissRequest = {
            when (state) {
                is AppUpdateUiState.Downloading,
                is AppUpdateUiState.Verifying -> AppUpdateManager.minimizeProgressToBanner()
                else -> onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Column {
                            Text(
                                text = "Atualização do App",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Versão atual: v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (state is AppUpdateUiState.Downloading || state is AppUpdateUiState.Verifying) {
                                AppUpdateManager.minimizeProgressToBanner()
                            } else {
                                onDismiss()
                            }
                        },
                        modifier = Modifier.size(minTouch)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Fechar janela de atualização"
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                when (state) {
                    is AppUpdateUiState.Checking -> {
                        Text(
                            text = "Consultando lançamentos no GitHub...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )
                    }

                    is AppUpdateUiState.UpToDate -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Você já está na versão mais recente disponível (v${BuildConfig.VERSION_NAME}).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = minTouch),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Fechar", fontWeight = FontWeight.Bold)
                        }
                    }

                    is AppUpdateUiState.Available -> {
                        ReleaseMetadataHeader(info = state.info)
                        SecurityPreservationNote()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { AppUpdateManager.dismissBannerForCurrentRelease(context) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = minTouch),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text("Mais tarde", fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = {
                                    AppUpdateManager.startBackgroundDownloadAndInstall(context, state.info)
                                },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .heightIn(min = minTouch),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text("Baixar e instalar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is AppUpdateUiState.Downloading -> {
                        ReleaseMetadataHeader(info = state.info)
                        val pct = (state.progress * 100f).toInt().coerceIn(0, 100)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Baixando pacote seguro...",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (state.totalBytes > 0L) {
                                        "$pct% (${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)})"
                                    } else {
                                        formatBytes(state.downloadedBytes)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (state.totalBytes > 0L) {
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { AppUpdateManager.minimizeProgressToBanner() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = minTouch),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Continuar usando o app em segundo plano")
                        }
                    }

                    is AppUpdateUiState.Verifying -> {
                        Text(
                            text = "Validando assinatura e checksum SHA-256 de ${state.info.apkName}...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )
                    }

                    is AppUpdateUiState.NeedsInstallPermission -> {
                        Text(
                            text = "Falta apenas autorizar \"Instalar apps desconhecidos\" para o Vida Acadêmica instalar a atualização ${state.info.tagName} diretamente.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        SecurityPreservationNote()
                        Button(
                            onClick = { AppUpdateManager.openUnknownSourcesSettings(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = minTouch),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Autorizar instalação nas Configurações", fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val file = File(state.apkFilePath)
                                    if (file.isFile) {
                                        AppUpdateManager.openInstallFallbackViaFileProvider(context, file)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = minTouch),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text("Abrir instalador")
                            }
                            Button(
                                onClick = { AppUpdateManager.retryInstallIfPermitted(context) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = minTouch),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text("Já autorizei")
                            }
                        }
                    }

                    is AppUpdateUiState.Installing -> {
                        Text(
                            text = "Instalando ${state.info.tagName}...\nO aplicativo será reiniciado ao concluir mantendo todos os seus dados.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )
                    }

                    is AppUpdateUiState.Error -> {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = minTouch),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text("Fechar")
                            }
                            Button(
                                onClick = { AppUpdateManager.retryFromError(context) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = minTouch),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text("Tentar novamente", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is AppUpdateUiState.Idle -> Unit
                }
            }
        }
    }
}

@Composable
private fun ReleaseMetadataHeader(info: AppUpdateReleaseInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = info.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                Text(
                    text = if (info.isPrerelease) "BETA · ${info.tagName}" else info.tagName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        if (info.apkSizeBytes > 0L) {
            Text(
                text = "Pacote: ${info.apkName} (${formatBytes(info.apkSizeBytes)})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (info.releaseNotes.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 170.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = info.releaseNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

@Composable
private fun SecurityPreservationNote() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(18.dp)
                    .padding(top = 1.dp)
            )
            Text(
                text = "Integridade verificada via SHA-256 antes de instalar. Suas disciplinas, faltas, notas e histórico criptografado são preservados e o app reinicia ao finalizar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}
