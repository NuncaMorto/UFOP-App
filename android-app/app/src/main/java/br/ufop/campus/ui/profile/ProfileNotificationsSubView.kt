package br.ufop.campus.ui.profile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.core.ffi.NotificationPreferences
import br.ufop.campus.core.notifications.CampusNotificationChannels
import br.ufop.campus.ui.theme.CampusCardRadius
import br.ufop.campus.ui.theme.CampusPillRadius
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.StatusCancelledAmber
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.campusCardBorder
import br.ufop.campus.ui.theme.tabularNums

@Composable
internal fun ProfileNotificationsSubView(
    repository: CampusCoreRepository,
    mutationVersion: Long
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()

    var prefs by remember(repository) {
        mutableStateOf(CampusRepositoryHolder.loadNotificationPreferences(context, repository))
    }
    var hasSystemPermission by remember {
        mutableStateOf(CampusNotificationChannels.hasRuntimeNotificationPermission(context))
    }
    var requestedPermissionInSession by remember { mutableStateOf(false) }

    LaunchedEffect(mutationVersion) {
        val latest = CampusRepositoryHolder.loadNotificationPreferences(context, repository)
        if (prefs != latest) {
            prefs = latest
        }
        hasSystemPermission = CampusNotificationChannels.hasRuntimeNotificationPermission(context)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasSystemPermission = CampusNotificationChannels.hasRuntimeNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        requestedPermissionInSession = true
        hasSystemPermission = granted && CampusNotificationChannels.hasRuntimeNotificationPermission(context)
        if (hasSystemPermission) {
            CampusRepositoryHolder.saveNotificationPreferences(context, repository, prefs)
        }
    }

    val updatePrefs: (NotificationPreferences) -> Unit = { next ->
        val saved = CampusRepositoryHolder.saveNotificationPreferences(context, repository, next)
        prefs = saved
    }

    val openSystemNotificationSettings = {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    val safeAmber = accessibleTextColor(StatusCancelledAmber, isDark, MaterialTheme.colorScheme.surface)
    val safeGreen = accessibleTextColor(StatusAttendingGreen, isDark, MaterialTheme.colorScheme.surfaceContainerHigh)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!hasSystemPermission) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CampusCardRadius),
                color = StatusCancelledAmber.copy(alpha = if (isDark) 0.14f else 0.10f),
                border = BorderStroke(1.dp, safeAmber.copy(alpha = 0.45f))
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
                            imageVector = Icons.Outlined.NotificationsOff,
                            contentDescription = null,
                            tint = safeAmber,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Permissão de notificações desativada",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                    }

                    Text(
                        text = "Permita notificações para receber avisos de aula, contagem regressiva na tela de bloqueio, prazos e alertas de falta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !requestedPermissionInSession) {
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = minTouchTarget),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Permitir notificações",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { openSystemNotificationSettings() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minTouchTarget),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Text(
                            text = "Abrir ajustes do sistema",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Grupo 1 — Lembretes de aula
        SettingsCategoryGroup(title = "Lembretes de aula") {
            SettingsMenuRow(
                icon = Icons.Outlined.Alarm,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Aviso antes da aula",
                subtitle = "Com sala e botões rápidos de presença",
                onClick = { updatePrefs(prefs.copy(classReminderEnabled = !prefs.classReminderEnabled)) },
                trailing = {
                    Switch(
                        checked = prefs.classReminderEnabled,
                        onCheckedChange = { enabled ->
                            updatePrefs(prefs.copy(classReminderEnabled = enabled))
                        },
                        modifier = Modifier
                            .heightIn(min = minTouchTarget)
                            .semantics { contentDescription = "Aviso antes da aula" },
                        colors = campusSwitchColors()
                    )
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .alpha(if (prefs.classReminderEnabled || prefs.liveActivityEnabled) 1f else 0.55f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Antecedência",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(10, 15, 30).forEach { minutes ->
                        val isSelected = prefs.classLeadMinutes == minutes
                        val pillBg = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                        val pillText = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(CampusPillRadius))
                                .clickable(
                                    role = Role.RadioButton,
                                    onClick = {
                                        updatePrefs(prefs.copy(classLeadMinutes = minutes))
                                    }
                                )
                                .semantics {
                                    selected = isSelected
                                    contentDescription = "Avisar $minutes minutos antes"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(CampusPillRadius),
                                color = pillBg,
                                border = campusCardBorder(1.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$minutes min",
                                        style = MaterialTheme.typography.labelLarge.tabularNums(),
                                        fontWeight = FontWeight.Bold,
                                        color = pillText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            )

            SettingsMenuRow(
                icon = Icons.Outlined.Timer,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Cartão ao vivo (Live Update)",
                subtitle = "Contagem regressiva na tela de bloqueio",
                onClick = { updatePrefs(prefs.copy(liveActivityEnabled = !prefs.liveActivityEnabled)) },
                trailing = {
                    Switch(
                        checked = prefs.liveActivityEnabled,
                        onCheckedChange = { enabled ->
                            updatePrefs(prefs.copy(liveActivityEnabled = enabled))
                        },
                        modifier = Modifier
                            .heightIn(min = minTouchTarget)
                            .semantics { contentDescription = "Cartão ao vivo" },
                        colors = campusSwitchColors()
                    )
                }
            )
        }

        // Grupo 2 — Prazos e acompanhamento
        SettingsCategoryGroup(title = "Prazos e acompanhamento") {
            SettingsMenuRow(
                icon = Icons.AutoMirrored.Outlined.EventNote,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Provas e entregas",
                subtitle = "Avisar 3 dias e 1 dia antes às 09:00",
                onClick = { updatePrefs(prefs.copy(deadlineReminderEnabled = !prefs.deadlineReminderEnabled)) },
                trailing = {
                    Switch(
                        checked = prefs.deadlineReminderEnabled,
                        onCheckedChange = { enabled ->
                            updatePrefs(prefs.copy(deadlineReminderEnabled = enabled))
                        },
                        modifier = Modifier
                            .heightIn(min = minTouchTarget)
                            .semantics { contentDescription = "Provas e entregas" },
                        colors = campusSwitchColors()
                    )
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            )

            SettingsMenuRow(
                icon = Icons.Outlined.WarningAmber,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Alerta de limite de faltas",
                subtitle = "Somente ao passar de 65% ou 85%",
                onClick = { updatePrefs(prefs.copy(absenceAlertEnabled = !prefs.absenceAlertEnabled)) },
                trailing = {
                    Switch(
                        checked = prefs.absenceAlertEnabled,
                        onCheckedChange = { enabled ->
                            updatePrefs(prefs.copy(absenceAlertEnabled = enabled))
                        },
                        modifier = Modifier
                            .heightIn(min = minTouchTarget)
                            .semantics { contentDescription = "Alerta de limite de faltas" },
                        colors = campusSwitchColors()
                    )
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            )

            SettingsMenuRow(
                icon = Icons.Outlined.WbTwilight,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Resumo matinal",
                subtitle = "Dias com aula às 07:30 · silêncio no fim de semana",
                onClick = { updatePrefs(prefs.copy(morningSummaryEnabled = !prefs.morningSummaryEnabled)) },
                trailing = {
                    Switch(
                        checked = prefs.morningSummaryEnabled,
                        onCheckedChange = { enabled ->
                            updatePrefs(prefs.copy(morningSummaryEnabled = enabled))
                        },
                        modifier = Modifier
                            .heightIn(min = minTouchTarget)
                            .semantics { contentDescription = "Resumo matinal" },
                        colors = campusSwitchColors()
                    )
                }
            )
        }

        // Card informativo offline (Surface 2, 20dp)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = campusCardBorder()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = safeGreen,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "100% offline no aparelho: todos os lembretes são agendados localmente a partir do seu SQLite — sem servidor, sem push externo e sem rastreamento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
