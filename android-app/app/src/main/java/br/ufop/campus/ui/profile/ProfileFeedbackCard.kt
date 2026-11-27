package br.ufop.campus.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.ufop.campus.ui.theme.CampusCardRadius
import br.ufop.campus.ui.theme.CampusPillRadius
import br.ufop.campus.ui.theme.DepartmentMathBlue
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.campusCardBorder
import br.ufop.campus.ui.theme.campusCardElevation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FeedbackSupportCard(
    campusLabel: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = LocalCampusThemeController.current.isDark
    val minTouchTarget = campusMinTouchTarget()
    val accessibleBlue = accessibleTextColor(DepartmentMathBlue, isDark, MaterialTheme.colorScheme.surface)
    val categories = listOf("Bug / Erro", "Sugestão", "Visual", "Outro")

    var selectedCategory by rememberSaveable { mutableStateOf(categories.first()) }
    var senderName by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    val screenshots = remember { mutableStateListOf<Uri>() }
    var isSendingFeedback by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var pendingFeedbackCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        pendingFeedbackCount = withContext(Dispatchers.IO) { getPendingFeedbackCount(context) }
    }

    val screenshotPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        val room = FEEDBACK_MAX_SCREENSHOTS - screenshots.size
        uris.filter { it !in screenshots }.take(room.coerceAtLeast(0)).forEach { screenshots.add(it) }
        if (uris.size > room) {
            statusIsError = false
            statusMessage = "Máximo de $FEEDBACK_MAX_SCREENSHOTS prints por feedback."
        }
    }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SupportAgent,
                    contentDescription = null,
                    tint = accessibleBlue,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Feedback & Suporte",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Relate um problema ou sugira melhorias. O envio é feito direto pelo app, sem abrir e-mail.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.chunked(2).forEach { rowCategories ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowCategories.forEach { category ->
                            val isSelected = selectedCategory == category
                            Surface(
                                onClick = { selectedCategory = category },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = minTouchTarget)
                                    .semantics { selected = isSelected },
                                shape = RoundedCornerShape(CampusPillRadius),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                border = campusCardBorder()
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = category,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = senderName,
                onValueChange = { senderName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Seu nome (opcional)") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Mensagem") },
                placeholder = { Text("Descreva o que aconteceu ou sua ideia...") },
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(18.dp)
            )

            OutlinedButton(
                onClick = { screenshotPickerLauncher.launch("image/*") },
                enabled = !isSendingFeedback && screenshots.size < FEEDBACK_MAX_SCREENSHOTS,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minTouchTarget),
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Anexar prints (até 3)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            if (screenshots.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    screenshots.toList().forEach { uri ->
                        FeedbackScreenshotThumbnail(
                            uri = uri,
                            enabled = !isSendingFeedback,
                            onRemove = { screenshots.remove(uri) }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    isSendingFeedback = true
                    statusMessage = null
                    val picked = screenshots.toList()
                    scope.launch {
                        val result = sendFeedback(
                            context = context,
                            category = selectedCategory,
                            message = message,
                            senderName = senderName,
                            campusLabel = campusLabel,
                            screenshotUris = picked
                        )
                        isSendingFeedback = false
                        statusIsError = result.isError
                        statusMessage = result.statusMessage
                        if (result.sentImmediately || result.queuedOffline) {
                            message = ""
                            screenshots.clear()
                        }
                        pendingFeedbackCount = withContext(Dispatchers.IO) { getPendingFeedbackCount(context) }
                    }
                },
                enabled = !isSendingFeedback && message.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minTouchTarget),
                shape = RoundedCornerShape(22.dp)
            ) {
                if (isSendingFeedback) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enviando...",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enviar feedback",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (pendingFeedbackCount > 0) {
                OutlinedButton(
                    onClick = {
                        isSendingFeedback = true
                        statusMessage = null
                        scope.launch {
                            val result = retryPendingFeedback(context)
                            isSendingFeedback = false
                            statusIsError = false
                            statusMessage = result.statusMessage
                            pendingFeedbackCount = withContext(Dispatchers.IO) { getPendingFeedbackCount(context) }
                        }
                    },
                    enabled = !isSendingFeedback,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reenviar pendentes ($pendingFeedbackCount)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (statusMessage != null) {
                StatusFeedbackBanner(
                    message = statusMessage!!,
                    isError = statusIsError,
                    onDismiss = { statusMessage = null }
                )
            }
        }
    }
}
@Composable
private fun FeedbackScreenshotThumbnail(
    uri: Uri,
    enabled: Boolean,
    onRemove: () -> Unit
) {
    val minTouchTarget = campusMinTouchTarget()
    val thumbnail = rememberUriThumbnail(uri, maxSidePx = 160)
    Box(modifier = Modifier.size(width = 104.dp, height = 108.dp)) {
        Surface(
            modifier = Modifier
                .size(88.dp)
                .align(Alignment.BottomStart),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = "Print anexado",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
        IconButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier
                .size(minTouchTarget)
                .align(Alignment.TopEnd)
        ) {
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = StatusAbsentRed,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remover print anexado",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}
