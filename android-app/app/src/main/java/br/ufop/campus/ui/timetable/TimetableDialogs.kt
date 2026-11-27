package br.ufop.campus.ui.timetable

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.FriendScheduleInfo
import br.ufop.campus.core.ffi.UfopGradeCodec
import br.ufop.campus.core.storage.GradeExportStorage
import br.ufop.campus.ui.theme.CampusCardRadius
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.campusCardBorder
import br.ufop.campus.ui.theme.campusCardElevation
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.accessibleTextColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ShareGradeDialog(
    repository: CampusCoreRepository,
    effectiveSemester: String,
    pendingExportJson: String,
    pendingSuggestedFilename: String,
    minTouchTarget: Dp,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    onFeedback: (String) -> Unit,
    onOpenMyQrCode: () -> Unit = {}
) {
    val context = LocalContext.current
    var isExportingGrade by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isExportingGrade) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 620.dp),
            shape = RoundedCornerShape(CampusCardRadius),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = campusCardElevation(4.dp),
            border = campusCardBorder()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Compartilhar minha grade (.ufopgrade)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Inclui seu nome, curso, foto em miniatura e horários do semestre $effectiveSemester ($pendingSuggestedFilename)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (isExportingGrade) return@Button
                            coroutineScope.launch {
                                isExportingGrade = true
                                val payload = pendingExportJson.ifBlank {
                                    repository.exportMyGradeBundleJson(effectiveSemester)
                                }
                                val saveResult = runCatching {
                                    withContext(Dispatchers.IO) {
                                        GradeExportStorage.saveGradeBundle(
                                            context = context,
                                            repository = repository,
                                            filename = pendingSuggestedFilename,
                                            jsonPayload = payload
                                        )
                                    }
                                }
                                isExportingGrade = false
                                onDismiss()
                                saveResult.onSuccess { saved ->
                                    onFeedback("Arquivo salvo em ${saved.displayPath}")
                                }.onFailure { err ->
                                    onFeedback(
                                        err.message
                                            ?: "Erro ao salvar arquivo .ufopgrade na pasta padrão."
                                    )
                                }
                            }
                        },
                        enabled = !isExportingGrade,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = minTouchTarget),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isExportingGrade) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Salvar na pasta padrão",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (isExportingGrade) return@FilledTonalButton
                                coroutineScope.launch {
                                    isExportingGrade = true
                                    val payload = pendingExportJson.ifBlank {
                                        repository.exportMyGradeBundleJson(effectiveSemester)
                                    }
                                    val shareUriResult = runCatching {
                                        withContext(Dispatchers.IO) {
                                            GradeExportStorage.createShareableFileUri(
                                                context = context,
                                                filename = pendingSuggestedFilename,
                                                jsonPayload = payload
                                            )
                                        }
                                    }
                                    isExportingGrade = false
                                    shareUriResult.onSuccess { shareUri ->
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/octet-stream"
                                            putExtra(Intent.EXTRA_SUBJECT, pendingSuggestedFilename)
                                            putExtra(Intent.EXTRA_STREAM, shareUri)
                                            clipData = ClipData.newRawUri(pendingSuggestedFilename, shareUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        val chooser = Intent.createChooser(
                                            sendIntent,
                                            "Enviar $pendingSuggestedFilename"
                                        ).apply {
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching {
                                            context.startActivity(chooser)
                                        }.onSuccess {
                                            onDismiss()
                                            onFeedback("Arquivo $pendingSuggestedFilename pronto para envio!")
                                        }.onFailure { err ->
                                            onDismiss()
                                            onFeedback(
                                                err.message
                                                    ?: "Não foi possível abrir o compartilhamento do arquivo."
                                            )
                                        }
                                    }.onFailure { err ->
                                        onDismiss()
                                        onFeedback(
                                            err.message
                                                ?: "Erro ao preparar arquivo .ufopgrade para envio."
                                        )
                                    }
                                }
                            },
                            enabled = !isExportingGrade,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = minTouchTarget),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Enviar arquivo",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val payload = pendingExportJson.ifBlank {
                                    repository.exportMyGradeBundleJson(effectiveSemester)
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(
                                    ClipData.newPlainText("Grade Acadêmica (.ufopgrade)", payload)
                                )
                                onDismiss()
                                onFeedback("Código .ufopgrade copiado para a área de transferência!")
                            },
                            enabled = !isExportingGrade,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = minTouchTarget),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Copiar JSON",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            onOpenMyQrCode()
                        },
                        enabled = !isExportingGrade,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = minTouchTarget),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.QrCode2,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "QR Code",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isExportingGrade,
                        modifier = Modifier.defaultMinSize(minHeight = minTouchTarget)
                    ) {
                        Text(
                            text = "Fechar",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ImportFriendGradeDialog(
    repository: CampusCoreRepository,
    isDark: Boolean,
    minTouchTarget: Dp,
    onDismiss: () -> Unit,
    onImported: (FriendScheduleInfo) -> Unit
) {
    val context = LocalContext.current
    var friendNameInput by remember { mutableStateOf("") }
    var friendJsonInput by remember { mutableStateOf("") }
    var importFriendError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 620.dp)
                .heightIn(max = 520.dp)
                .imePadding(),
            shape = RoundedCornerShape(CampusCardRadius),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = campusCardElevation(4.dp),
            border = campusCardBorder()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Colar texto da grade",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Cole o texto compartilhado de um amigo (nome, curso e foto são lidos automaticamente) ou informe o nome manualmente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = friendNameInput,
                    onValueChange = {
                        friendNameInput = it
                        importFriendError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome do amigo (opcional para .ufopgrade)") },
                    placeholder = { Text("Ex.: Lucas Mendes") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val pasted = clipboard?.primaryClip
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)
                                ?.coerceToText(context)
                                ?.toString()
                                ?.trim()
                                .orEmpty()
                            if (pasted.isNotEmpty()) {
                                friendJsonInput = pasted
                                importFriendError = null
                            } else {
                                importFriendError = "A área de transferência está vazia."
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = minTouchTarget),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Colar texto",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            friendJsonInput = ""
                            importFriendError = null
                        },
                        enabled = friendJsonInput.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = minTouchTarget),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Limpar",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                OutlinedTextField(
                    value = friendJsonInput,
                    onValueChange = {
                        friendJsonInput = it
                        importFriendError = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 210.dp),
                    label = { Text("Conteúdo .ufopgrade, JSON ou QR") },
                    placeholder = { Text("{\"format\":\"ufopgrade-v1\",\"friendName\":\"...\"} ou UFOPG1:...") },
                    minLines = 6,
                    maxLines = 10,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    shape = RoundedCornerShape(16.dp)
                )

                if (friendJsonInput.isNotBlank()) {
                    val trimmedPayload = friendJsonInput.trim()
                    val looksLikeJson = trimmedPayload.startsWith("{") && trimmedPayload.endsWith("}")
                    val looksLikeQrCode = trimmedPayload.startsWith(UfopGradeCodec.QR_PREFIX)
                    val recognizedFormat = looksLikeJson || looksLikeQrCode
                    val statusColor = if (recognizedFormat) {
                        StatusAttendingGreen
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    val statusText = when {
                        looksLikeQrCode ->
                            "✓ Código QR UFOP detectado (${friendJsonInput.length} caracteres)"
                        looksLikeJson ->
                            "✓ Conteúdo carregado (${friendJsonInput.length} caracteres · formato JSON detectado)"
                        else ->
                            "✓ Conteúdo carregado (${friendJsonInput.length} caracteres · confira se o JSON ou código QR está completo)"
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = statusColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                if (importFriendError != null) {
                    Text(
                        text = importFriendError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = accessibleTextColor(StatusAbsentRed, isDark),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.defaultMinSize(minHeight = minTouchTarget)
                    ) {
                        Text(
                            text = "Cancelar",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cleanName = friendNameInput.trim()
                            val cleanJson = friendJsonInput.trim()
                            if (cleanJson.isEmpty()) {
                                importFriendError = "Cole o texto ou código QR da grade compartilhada."
                            } else {
                                runCatching {
                                    val payloadToImport = UfopGradeCodec.parseAndValidatePreview(
                                        rawText = cleanJson,
                                        sourceLabel = "Texto colado"
                                    ).fold(
                                        onSuccess = { preview -> preview.rawJsonPayload },
                                        onFailure = { err ->
                                            if (cleanJson.startsWith(UfopGradeCodec.QR_PREFIX)) {
                                                throw err
                                            }
                                            cleanJson
                                        }
                                    )
                                    repository.importFriendScheduleJson(
                                        friendName = cleanName,
                                        jsonPayload = payloadToImport
                                    )
                                }.onSuccess { imported ->
                                    onImported(imported)
                                    onDismiss()
                                }.onFailure { err ->
                                    importFriendError = err.message
                                        ?: "Não foi possível importar o texto informado."
                                }
                            }
                        },
                        modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Importar",
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
