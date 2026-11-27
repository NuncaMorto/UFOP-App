package br.ufop.campus.ui.timetable

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.FriendScheduleInfo
import br.ufop.campus.core.ffi.StudentProfile
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.accessibleTextColor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun GradeSelectorDropdown(
    selectedGradeLabel: String,
    activeFriend: FriendScheduleInfo?,
    studentProfile: StudentProfile,
    friendSchedules: List<FriendScheduleInfo>,
    isDark: Boolean,
    toolbarVisualHeight: Dp,
    onSelectMyGrade: () -> Unit,
    onSelectFriend: (String) -> Unit,
    onRequestRemoveFriend: (FriendScheduleInfo) -> Unit,
    onImportFile: () -> Unit,
    onOpenImportTextDialog: () -> Unit,
    onOpenQrScanner: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        val userDisplayName = studentProfile.fullName.ifBlank { "Você" }
        val selectorAvatar = resolveGradeSelectorAvatarSource(
            activeFriend = activeFriend,
            studentProfile = studentProfile
        )
        ExpressiveSelectorPill(
            label = selectedGradeLabel,
            isHighlighted = activeFriend != null,
            leadingAvatarName = selectorAvatar.displayName,
            leadingAvatarBase64 = selectorAvatar.avatarBase64,
            leadingAvatarFilePath = selectorAvatar.avatarFilePath,
            visualHeight = toolbarVisualHeight,
            onClick = { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            val isViewingMyGrade = activeFriend == null
            DropdownMenuItem(
                leadingIcon = {
                    FriendAvatarBadge(
                        friendName = userDisplayName,
                        avatarBase64 = null,
                        avatarFilePath = studentProfile.avatarPath,
                        size = 28.dp
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Minha grade",
                            fontWeight = if (isViewingMyGrade) {
                                FontWeight.Bold
                            } else {
                                FontWeight.SemiBold
                            },
                            color = if (isViewingMyGrade) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                accessibleTextColor(MaterialTheme.colorScheme.primary, isDark)
                            }
                        )
                        val mySubtitle = when {
                            !isViewingMyGrade && studentProfile.fullName.isNotBlank() ->
                                "${studentProfile.fullName} · Voltar para minha grade"
                            !isViewingMyGrade ->
                                "Voltar para minha grade"
                            studentProfile.fullName.isNotBlank() ->
                                studentProfile.fullName
                            else ->
                                "Sua grade atual"
                        }
                        Text(
                            text = mySubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                trailingIcon = {
                    if (isViewingMyGrade) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selecionado",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Voltar",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                },
                onClick = {
                    onSelectMyGrade()
                    expanded = false
                }
            )

            if (friendSchedules.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                friendSchedules.forEach { friend ->
                    val isSelected = activeFriend?.id == friend.id
                    DropdownMenuItem(
                        leadingIcon = {
                            FriendAvatarBadge(
                                friendName = friend.friendName,
                                avatarBase64 = friend.avatarBase64,
                                size = 28.dp
                            )
                        },
                        text = {
                            Column {
                                Text(
                                    text = friend.friendName,
                                    fontWeight = if (isSelected) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Medium
                                    }
                                )
                                val subtitleText = if (friend.courseName.isNotBlank()) {
                                    "${friend.courseName} · Semestre ${friend.semester}"
                                } else {
                                    "Semestre ${friend.semester}"
                                }
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    expanded = false
                                    onRequestRemoveFriend(friend)
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Remover grade de ${friend.friendName}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        onClick = {
                            onSelectFriend(friend.id)
                            expanded = false
                        }
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                text = {
                    Text(
                        text = "Importar grade",
                        color = accessibleTextColor(MaterialTheme.colorScheme.primary, isDark),
                        fontWeight = FontWeight.Bold
                    )
                },
                onClick = {
                    expanded = false
                    onImportFile()
                }
            )

            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                text = {
                    Text(
                        text = "Ler QR Code",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                onClick = {
                    expanded = false
                    onOpenQrScanner()
                }
            )

            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                },
                text = {
                    Text(
                        text = "Colar texto da grade",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                },
                onClick = {
                    expanded = false
                    onOpenImportTextDialog()
                }
            )
        }
    }
}

@Composable
internal fun FriendAvatarBadge(
    friendName: String,
    avatarBase64: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    avatarFilePath: String? = null
) {
    val normalizedBase64 = remember(avatarBase64) {
        avatarBase64?.trim()?.takeIf { it.isNotEmpty() }
    }
    val normalizedFilePath = remember(avatarFilePath) {
        avatarFilePath?.trim()?.takeIf { it.isNotEmpty() }
    }
    val cachedBitmap = remember(normalizedBase64, normalizedFilePath) {
        normalizedBase64?.let { clean ->
            friendAvatarBitmapCache.get(clean)
        } ?: normalizedFilePath?.let { cleanPath ->
            val file = File(cleanPath)
            if (file.exists()) localAvatarBitmapCache.get("${cleanPath}:${file.lastModified()}") else null
        }
    }
    var decodedBitmap by remember(normalizedBase64, normalizedFilePath) {
        mutableStateOf<Bitmap?>(cachedBitmap)
    }
    LaunchedEffect(normalizedBase64, normalizedFilePath) {
        if (decodedBitmap != null) return@LaunchedEffect
        if (normalizedBase64 == null && normalizedFilePath == null) {
            decodedBitmap = null
            return@LaunchedEffect
        }
        decodedBitmap = withContext(Dispatchers.IO) {
            if (normalizedBase64 != null) {
                decodeAvatarBase64(normalizedBase64)
            } else {
                decodeLocalAvatarFile(normalizedFilePath)
            }
        }
    }
    val imageBitmap = remember(decodedBitmap) {
        decodedBitmap?.asImageBitmap()
    }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "Foto de $friendName",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), CircleShape)
        )
    } else {
        Surface(
            modifier = modifier.size(size),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = buildInitials(friendName),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
internal fun FriendScheduleFooterCard(
    friend: FriendScheduleInfo,
    semester: String,
    minTouchTarget: Dp,
    onBackToMyGrade: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        border = br.ufop.campus.ui.theme.campusCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FriendAvatarBadge(
                    friendName = friend.friendName,
                    avatarBase64 = friend.avatarBase64,
                    size = 48.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = friend.friendName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = friend.courseName.ifBlank { "Estudante" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Grade compartilhada · Semestre $semester",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            FilledTonalButton(
                onClick = onBackToMyGrade,
                modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Minha grade",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
internal fun RemoveFriendConfirmDialog(
    friendToDelete: FriendScheduleInfo,
    minTouchTarget: Dp,
    onDismiss: () -> Unit,
    onConfirmRemove: () -> Unit
) {
    // IHC-02: Confirmação antes de remover grade de um amigo
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Remover grade de ${friendToDelete.friendName}?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "A grade compartilhada de ${friendToDelete.friendName} (${friendToDelete.semester}) será removida da sua lista. Você poderá importá-la novamente quando quiser.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = minTouchTarget)
            ) {
                Text(
                    text = "Cancelar",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmRemove,
                modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusAbsentRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "Remover",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}
