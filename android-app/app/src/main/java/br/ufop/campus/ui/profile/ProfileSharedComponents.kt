package br.ufop.campus.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ufop.campus.core.ffi.AcademicCalendarUpdatePackage
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.core.ffi.StudentProfile
import br.ufop.campus.core.ffi.UfopCampusInfo
import br.ufop.campus.core.ffi.normalizeUfopPortugueseText
import br.ufop.campus.ui.theme.CampusAccentColor
import br.ufop.campus.ui.theme.CampusCardRadius
import br.ufop.campus.ui.theme.CampusPillRadius
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.accessibleContentColorOn
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.campusAccentTextColor
import br.ufop.campus.ui.theme.campusCardBorder
import br.ufop.campus.ui.theme.campusCardElevation
import br.ufop.campus.ui.theme.tabularNums
import java.util.Locale

internal enum class ProfileDestination(val title: String) {
    METRICS_CRE("Métricas & CRE"),
    SETTINGS("Configurações"),
    UPDATES("Atualizações"),
    FAQ("FAQ & Ajuda")
}
internal enum class LogoutStep {
    NONE,
    BACKUP_PROMPT,
    FINAL_CONFIRM
}
@Composable
internal fun campusSwitchColors(): SwitchColors {
    val isDark = LocalCampusThemeController.current.isDark
    return SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        checkedBorderColor = MaterialTheme.colorScheme.primary,
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor = if (isDark) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
        },
        uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    )
}
@Composable
internal fun campusMinTouchTarget() =
    if (LocalCampusAccessibility.current.settings.largeTouchTargets) 56.dp else 48.dp
@Composable
internal fun CampusAnimatedVisibility(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    if (LocalCampusAccessibility.current.settings.reduceMotion) {
        if (visible) content()
    } else {
        AnimatedVisibility(visible = visible) {
            content()
        }
    }
}
internal enum class SettingsSection(val title: String) {
    PERSONALIZATION("Personalização"),
    SECURITY("Segurança & Privacidade"),
    ACCESSIBILITY("Acessibilidade"),
    CAMPUS_WEATHER("Campus & Clima Online"),
    DOCUMENTS_PDF("Documentos Acadêmicos (PDF)"),
    ACADEMIC_CALENDAR("Calendário Acadêmico"),
    STORAGE_SHARE("Armazenamento & Compartilhamento"),
    FEEDBACK("Feedback & Suporte")
}
internal fun shareBackupUri(context: Context, uri: Uri, filename: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Backup Criptografado Vida Acadêmica ($filename)")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(sendIntent, "Compartilhar backup criptografado")
        )
    }
}
internal fun syncThemeAndA11yAfterBackupRestore(
    context: Context,
    repository: CampusCoreRepository,
    themeController: br.ufop.campus.ui.theme.CampusThemeController,
    accessibilityController: br.ufop.campus.ui.theme.CampusAccessibilityController
) {
    val restoredTheme = repository.getDarkThemePreference().trim().uppercase()
    if (restoredTheme == "DARK" || restoredTheme == "LIGHT") {
        val targetDark = restoredTheme == "DARK"
        CampusRepositoryHolder.saveDarkTheme(context, repository, targetDark)
        if (themeController.isDark != targetDark) {
            themeController.toggleTheme()
        }
    }
    val restoredAccent = CampusAccentColor.fromKey(repository.getAccentColorPreference())
    CampusRepositoryHolder.saveAccentColor(context, repository, restoredAccent.storageKey)
    themeController.selectAccentColor(restoredAccent)

    val restoredAttendanceStyle = CampusRepositoryHolder.saveAttendanceIndicatorStyle(
        context = context,
        repository = repository,
        style = repository.getAttendanceIndicatorStyle()
    )
    themeController.selectAttendanceIndicatorStyle(restoredAttendanceStyle)

    val restoredA11y = repository.getAccessibilitySettings()
    CampusRepositoryHolder.saveAccessibilitySettings(context, repository, restoredA11y)
    accessibilityController.updateSettings(restoredA11y)

    CampusRepositoryHolder.setGuidedTourCompleted(context, repository, repository.isGuidedTourCompleted())
    val seenRelease = repository.getLastSeenReleaseVersion()
    if (seenRelease.isNotBlank()) {
        CampusRepositoryHolder.setLastSeenReleaseVersion(context, repository, seenRelease)
    }
}
internal fun readUriBytes(context: Context, uri: Uri): ByteArray {
    return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("Não foi possível ler o arquivo selecionado.")
}
internal fun readUriText(context: Context, uri: Uri): String {
    return context.contentResolver.openInputStream(uri)
        ?.bufferedReader(Charsets.UTF_8)
        ?.use { it.readText() }
        ?: throw IllegalStateException("Não foi possível ler o arquivo selecionado.")
}
internal fun formatScore(value: Double, decimals: Int): String {
    return String.format(Locale.US, "%.${decimals}f", value)
}
@Composable
internal fun PendingCalendarUpdateBanner(
    pkg: AcademicCalendarUpdatePackage,
    onApply: () -> Unit
) {
    val minTouchTarget = campusMinTouchTarget()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
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
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Atualização do Calendário Acadêmico disponível — Semestre ${pkg.semester} · ${pkg.events.size} eventos",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Text(
                text = "${pkg.publishedBy} · Versão ${pkg.version}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )

            Button(
                onClick = onApply,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minTouchTarget),
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Atualizar meu calendário",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
@Composable
internal fun AcademicIdentityCard(
    studentProfile: StudentProfile,
    activeSemester: String,
    activeCampus: UfopCampusInfo,
    avatarBitmap: ImageBitmap?,
    onEditProfile: () -> Unit
) {
    val minTouchTarget = campusMinTouchTarget()
    val displayCourseName = studentProfile.courseName.trim().let {
        if (it.isBlank()) "Curso não informado" else normalizeUfopPortugueseText(it)
    }
    val regLabel = studentProfile.registrationNumber.trim()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CampusCardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = campusCardBorder(),
        shadowElevation = campusCardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap,
                        contentDescription = "Foto de perfil",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        val initials = extractInitials(studentProfile.fullName)
                        if (initials.isNotEmpty()) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = "Foto de perfil",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = studentProfile.fullName.ifBlank { "Estudante" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = displayCourseName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (regLabel.isNotEmpty()) {
                    Text(
                        text = regLabel,
                        style = MaterialTheme.typography.labelSmall.tabularNums(),
                        fontWeight = FontWeight.SemiBold,
                        color = campusAccentTextColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                onClick = onEditProfile,
                modifier = Modifier
                    .size(minTouchTarget)
                    .semantics { contentDescription = "Editar perfil" },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = campusCardBorder()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
@Composable
internal fun SettingsCategoryGroup(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .semantics { heading() }
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CampusCardRadius),
            color = MaterialTheme.colorScheme.surface,
            border = campusCardBorder(),
            shadowElevation = campusCardElevation(1.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}
@Composable
internal fun SettingsMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    val rowBackground = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        Color.Transparent
    }
    val accessibleTint = accessibleTextColor(
        iconTint,
        isDark,
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minTouchTarget)
            .background(rowBackground)
            .semantics {
                if (isSelected) {
                    selected = true
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = RoundedCornerShape(12.dp),
            color = iconTint.copy(alpha = if (isDark) 0.18f else 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accessibleTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Abrir",
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun CampusAccentPalettePreviewAndGrid(
    isDark: Boolean,
    selectedAccent: CampusAccentColor,
    onSelectAccent: (CampusAccentColor) -> Unit,
    modifier: Modifier = Modifier
) {
    val previewPalette = remember(selectedAccent, isDark) { selectedAccent.palette(isDark) }
    val swatchRows = remember { CampusAccentColor.entries.chunked(3) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Mini-Preview Ao Vivo da escada tonal ativa
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CampusCardRadius),
            color = previewPalette.canvas,
            border = campusCardBorder(),
            shadowElevation = campusCardElevation(1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Palette,
                            contentDescription = null,
                            tint = if (isDark) previewPalette.highlight else previewPalette.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "PRÉ-VISUALIZAÇÃO · ${selectedAccent.displayName.uppercase(Locale.getDefault())}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = previewPalette.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(CampusPillRadius),
                        color = previewPalette.primary
                    ) {
                        Text(
                            text = if (isDark) "Escuro" else "Claro",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = previewPalette.onPrimary,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = previewPalette.surface1,
                    shadowElevation = if (isDark) 0.dp else 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = previewPalette.surface2
                        ) {
                            Text(
                                text = "14:00",
                                style = MaterialTheme.typography.labelMedium.tabularNums(),
                                fontWeight = FontWeight.Bold,
                                color = previewPalette.highlight,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Cálculo I · ICEB",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = previewPalette.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Em andamento · Frequência 92%",
                                style = MaterialTheme.typography.labelSmall.tabularNums(),
                                color = previewPalette.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(CampusPillRadius),
                            color = previewPalette.surface2
                        ) {
                            Text(
                                text = "AGORA",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = previewPalette.highlight,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Grade 3x3 de Esferas Cromáticas
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                swatchRows.forEach { rowAccents ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowAccents.forEach { accent ->
                            val isSelected = selectedAccent == accent
                            val palette = accent.palette(isDark)
                            val swatchColor = palette.primary
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .minimumInteractiveComponentSize()
                                    .clickable { onSelectAccent(accent) }
                                    .semantics { selected = isSelected }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .then(
                                            if (isSelected) {
                                                Modifier
                                                    .border(
                                                        width = 2.dp,
                                                        color = if (isDark) palette.highlight else swatchColor,
                                                        shape = CircleShape
                                                    )
                                                    .padding(4.dp)
                                            } else {
                                                Modifier.padding(4.dp)
                                            }
                                        )
                                        .clip(CircleShape)
                                        .background(swatchColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selecionada",
                                            tint = accessibleContentColorOn(swatchColor),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = accent.displayName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
internal fun SettingsSubViewHeader(
    title: String,
    backLabel: String = "Voltar",
    showBackButton: Boolean = true,
    onBack: () -> Unit = {}
) {
    val a11y = LocalCampusAccessibility.current.settings
    val buttonVisualSize = if (a11y.largeTouchTargets) 48.dp else 40.dp

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showBackButton) {
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .clickable(onClickLabel = backLabel, onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(buttonVisualSize),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = campusCardBorder()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = backLabel,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
        }
    }
}
@Composable
internal fun StatusFeedbackBanner(
    message: String,
    isError: Boolean,
    onDismiss: (() -> Unit)? = null
) {
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    val baseAccent = if (isError) StatusAbsentRed else StatusAttendingGreen
    val textAccent = accessibleTextColor(baseAccent, isDark, MaterialTheme.colorScheme.surface)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = baseAccent.copy(alpha = if (isDark) 0.16f else 0.12f),
        border = if (onDismiss != null) BorderStroke(1.dp, textAccent.copy(alpha = 0.4f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = if (onDismiss != null) 4.dp else 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = textAccent,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(minTouchTarget)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar aviso",
                        tint = textAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
@Composable
internal fun CampusSelectorOptionRow(
    campus: UfopCampusInfo,
    onSelect: () -> Unit
) {
    val minTouchTarget = campusMinTouchTarget()
    val label = when (campus.id) {
        "op_cruzeiro" -> "Ouro Preto · Morro do Cruzeiro"
        "mar_ichs" -> "Mariana · ICHS / ICSA"
        "jm_icea" -> "João Monlevade · ICEA"
        else -> "${campus.city} · ${campus.name}"
    }

    Surface(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minTouchTarget),
        shape = RoundedCornerShape(18.dp),
        color = if (campus.isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (campus.isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${campus.weatherTempC} °C · ${campus.weatherCondition}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (campus.isActive) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Campus ativo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(14.dp)
                    )
                }
            }
        }
    }
}
@Composable
internal fun StatSummaryBox(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
