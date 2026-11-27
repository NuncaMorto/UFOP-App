package br.ufop.campus.ui.profile

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.ufop.campus.BuildConfig
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.ui.theme.CampusCardRadius
import br.ufop.campus.ui.theme.CampusPillRadius
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.campusCardBorder
import br.ufop.campus.ui.theme.campusCardElevation

@Composable
internal fun ProfileUpdatesSubView(
    onCheckForUpdates: () -> Unit
) {
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    val greenCheckTint = accessibleTextColor(StatusAttendingGreen, isDark, MaterialTheme.colorScheme.surface)
    val appUpdateState by br.ufop.campus.core.update.AppUpdateManager.state.collectAsState()
    val statusText = when (val st = appUpdateState) {
        is br.ufop.campus.core.update.AppUpdateUiState.Checking ->
            "Consultando o GitHub Releases para verificar novas versões..."
        is br.ufop.campus.core.update.AppUpdateUiState.Available ->
            "Nova versão ${st.info.tagName} disponível para download e instalação."
        is br.ufop.campus.core.update.AppUpdateUiState.Downloading ->
            "Baixando atualização ${st.info.tagName} (${(st.progress * 100f).toInt()}%)..."
        is br.ufop.campus.core.update.AppUpdateUiState.Verifying ->
            "Verificando integridade SHA-256 de ${st.info.tagName}..."
        is br.ufop.campus.core.update.AppUpdateUiState.NeedsInstallPermission ->
            "Permissão de instalação necessária para concluir ${st.info.tagName}."
        is br.ufop.campus.core.update.AppUpdateUiState.ReadyToInstall ->
            "Pacote ${st.info.tagName} baixado e verificado. Pronto para instalar."
        is br.ufop.campus.core.update.AppUpdateUiState.Installing ->
            "Instalando ${st.info.tagName}..."
        is br.ufop.campus.core.update.AppUpdateUiState.UpToDate ->
            "Você já está na versão mais recente (v${CampusRepositoryHolder.CURRENT_APP_VERSION})."
        is br.ufop.campus.core.update.AppUpdateUiState.Error ->
            st.message
        else ->
            "Versão instalada: v${CampusRepositoryHolder.CURRENT_APP_VERSION} (build ${BuildConfig.VERSION_CODE})."
    }
    val actionLabel = when (val st = appUpdateState) {
        is br.ufop.campus.core.update.AppUpdateUiState.Checking -> "Verificando atualizações..."
        is br.ufop.campus.core.update.AppUpdateUiState.Available -> "Baixar ${st.info.tagName}"
        is br.ufop.campus.core.update.AppUpdateUiState.Downloading -> "Baixando ${st.info.tagName} (${(st.progress * 100f).toInt()}%)"
        is br.ufop.campus.core.update.AppUpdateUiState.Verifying -> "Verificando pacote..."
        is br.ufop.campus.core.update.AppUpdateUiState.NeedsInstallPermission -> "Autorizar instalação de ${st.info.tagName}"
        is br.ufop.campus.core.update.AppUpdateUiState.ReadyToInstall -> "Instalar ${st.info.tagName}"
        is br.ufop.campus.core.update.AppUpdateUiState.Installing -> "Instalando..."
        is br.ufop.campus.core.update.AppUpdateUiState.UpToDate -> "Verificar novamente (v${CampusRepositoryHolder.CURRENT_APP_VERSION})"
        else -> "Verificar atualizações (GitHub)"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CampusCardRadius),
            color = MaterialTheme.colorScheme.surface,
            border = campusCardBorder(),
            shadowElevation = campusCardElevation(1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Atualizações do Aplicativo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Surface(
                        shape = RoundedCornerShape(CampusPillRadius),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "v${CampusRepositoryHolder.CURRENT_APP_VERSION}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onCheckForUpdates,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(CampusPillRadius)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Text(
            text = "HISTÓRICO DE PATCHES & NOTAS DE VERSÃO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .semantics { heading() }
        )

        CampusReleaseNotes.versions.forEach { entry ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CampusCardRadius),
                color = MaterialTheme.colorScheme.surface,
                border = campusCardBorder(),
                shadowElevation = campusCardElevation(1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${entry.version} · ${entry.title}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(CampusPillRadius),
                            color = if (entry.isLatest) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ) {
                            Text(
                                text = entry.badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (entry.isLatest) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    entry.items.forEach { bullet ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = greenCheckTint,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(15.dp)
                            )
                            Text(
                                text = bullet,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
