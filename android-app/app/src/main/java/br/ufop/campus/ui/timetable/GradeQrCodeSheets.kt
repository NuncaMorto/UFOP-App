package br.ufop.campus.ui.timetable

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.ufop.campus.core.ffi.UfopGradeCodec
import br.ufop.campus.ui.theme.CampusCardRadius
import br.ufop.campus.ui.theme.CampusSheetRadius
import br.ufop.campus.ui.theme.campusCardBorder
import br.ufop.campus.ui.theme.campusCardElevation
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface QrGenerationState {
    data object Loading : QrGenerationState
    data class Ready(val qrPayload: String, val bitmap: ImageBitmap) : QrGenerationState
    data class Error(val message: String) : QrGenerationState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyGradeQrSheet(
    studentName: String,
    semester: String,
    bundleJson: String,
    minTouchTarget: Dp,
    onDismiss: () -> Unit,
    onCopiedFeedback: (String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val displayName = studentName.ifBlank { "Estudante UFOP" }
    var copiedInline by remember(bundleJson) { mutableStateOf(false) }

    val qrState by produceState<QrGenerationState>(
        initialValue = QrGenerationState.Loading,
        key1 = bundleJson
    ) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val qrText = UfopGradeCodec.encodeCompactQr(bundleJson)
                val hints = mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                    EncodeHintType.MARGIN to 2
                )
                val matrix = QRCodeWriter().encode(
                    qrText,
                    BarcodeFormat.QR_CODE,
                    720,
                    720,
                    hints
                )
                val width = matrix.width
                val height = matrix.height
                val pixels = IntArray(width * height)
                for (y in 0 until height) {
                    val rowOffset = y * width
                    for (x in 0 until width) {
                        pixels[rowOffset + x] = if (matrix.get(x, y)) {
                            AndroidColor.BLACK
                        } else {
                            AndroidColor.WHITE
                        }
                    }
                }
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
                QrGenerationState.Ready(
                    qrPayload = qrText,
                    bitmap = bitmap.asImageBitmap()
                )
            }.getOrElse { err ->
                QrGenerationState.Error(
                    err.message ?: "Não foi possível gerar o QR Code da sua grade."
                )
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = CampusSheetRadius, topEnd = CampusSheetRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.QrCode2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "QR Code da minha grade",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "$displayName · Semestre $semester",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            when (val state = qrState) {
                is QrGenerationState.Loading -> {
                    Surface(
                        modifier = Modifier
                            .size(240.dp),
                        shape = RoundedCornerShape(CampusCardRadius),
                        color = Color.White,
                        border = campusCardBorder(),
                        shadowElevation = campusCardElevation(2.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }

                is QrGenerationState.Ready -> {
                    Surface(
                        shape = RoundedCornerShape(CampusCardRadius),
                        color = Color.White,
                        border = campusCardBorder(),
                        tonalElevation = 0.dp,
                        shadowElevation = 2.dp
                    ) {
                        Image(
                            bitmap = state.bitmap,
                            contentDescription = "QR Code da grade de $displayName",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .padding(18.dp)
                                .sizeIn(maxWidth = 240.dp, maxHeight = 240.dp)
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        )
                    }
                }

                is QrGenerationState.Error -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            Text(
                text = "100% offline · compatível com Android e iOS (sem foto para leitura rápida)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (copiedInline) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "✓ Código QR copiado para a área de transferência",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            val readyState = qrState as? QrGenerationState.Ready
            FilledTonalButton(
                onClick = {
                    val payload = readyState?.qrPayload ?: return@FilledTonalButton
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(
                        ClipData.newPlainText("Código QR da Grade UFOP", payload)
                    )
                    copiedInline = true
                    onCopiedFeedback("Código QR copiado para a área de transferência!")
                },
                enabled = readyState != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = minTouchTarget),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Copiar código QR",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = minTouchTarget),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Fechar",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GradeQrScannerSheet(
    minTouchTarget: Dp,
    onQrScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var invalidQrWarning by remember { mutableStateOf<String?>(null) }
    var cameraInitError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
    }

    // Atualiza o estado da permissão se o usuário retornar das Configurações do sistema.
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameraPermissionGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = CampusSheetRadius, topEnd = CampusSheetRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ler QR Code de colega",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Aponte para o QR Code de um colega (Android ou iOS)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!cameraPermissionGranted) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CampusCardRadius),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = campusCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Permissão de câmera necessária",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "A câmera é usada exclusivamente offline no seu aparelho para ler o QR Code da grade compartilhada.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = minTouchTarget),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Permitir câmera",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = {
                        runCatching {
                            val settingsIntent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(settingsIntent)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = minTouchTarget),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Abrir configurações",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            } else {
                val previewView = remember(context) {
                    PreviewView(context).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                }
                val hasHandledResult = remember { AtomicBoolean(false) }

                DisposableEffect(lifecycleOwner, cameraPermissionGranted, previewView) {
                    if (!cameraPermissionGranted) {
                        return@DisposableEffect onDispose {}
                    }
                    val analysisExecutor = Executors.newSingleThreadExecutor()
                    val qrReader = QRCodeReader()
                    val decodeHints = mapOf(
                        DecodeHintType.TRY_HARDER to true,
                        DecodeHintType.CHARACTER_SET to "UTF-8"
                    )
                    val mainExecutor = ContextCompat.getMainExecutor(context)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    var boundProvider: ProcessCameraProvider? = null
                    var isDisposed = false

                    cameraProviderFuture.addListener(
                        {
                            if (isDisposed) return@addListener
                            runCatching {
                                val cameraProvider = cameraProviderFuture.get()
                                boundProvider = cameraProvider
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { analysis ->
                                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                            analyzeQrFrame(
                                                imageProxy = imageProxy,
                                                qrReader = qrReader,
                                                decodeHints = decodeHints,
                                                hasHandledResult = hasHandledResult,
                                                onValidQrFound = { rawText ->
                                                    mainExecutor.execute {
                                                        if (!isDisposed) {
                                                            onQrScanned(rawText)
                                                        }
                                                    }
                                                },
                                                onInvalidQrFound = {
                                                    mainExecutor.execute {
                                                        if (!isDisposed) {
                                                            invalidQrWarning =
                                                                "QR Code reconhecido, mas não é uma grade do Vida Acadêmica."
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                                cameraInitError = null
                            }.onFailure { err ->
                                cameraInitError = err.message
                                    ?: "Não foi possível iniciar a câmera neste dispositivo."
                            }
                        },
                        mainExecutor
                    )

                    onDispose {
                        isDisposed = true
                        runCatching { boundProvider?.unbindAll() }
                        analysisExecutor.shutdown()
                    }
                }

                Box(
                    modifier = Modifier
                        .widthIn(max = 340.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(22.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.68f)
                            .border(
                                width = 2.dp,
                                color = Color.White.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(18.dp)
                            )
                    )
                }

                if (cameraInitError != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = cameraInitError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (invalidQrWarning != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = invalidQrWarning.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = minTouchTarget),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Fechar",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private fun analyzeQrFrame(
    imageProxy: ImageProxy,
    qrReader: QRCodeReader,
    decodeHints: Map<DecodeHintType, Any>,
    hasHandledResult: AtomicBoolean,
    onValidQrFound: (String) -> Unit,
    onInvalidQrFound: () -> Unit
) {
    try {
        if (hasHandledResult.get()) return
        val planes = imageProxy.planes
        if (planes.isEmpty()) return

        val width = imageProxy.width
        val height = imageProxy.height
        if (width <= 0 || height <= 0) return

        val yPlane = planes[0]
        val yBuffer = yPlane.buffer
        yBuffer.rewind()
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        val yBytes = ByteArray(width * height)

        if (pixelStride == 1 && rowStride == width) {
            yBuffer.get(yBytes, 0, minOf(yBytes.size, yBuffer.remaining()))
        } else {
            val rowData = ByteArray(rowStride)
            for (row in 0 until height) {
                val remaining = yBuffer.remaining()
                if (remaining <= 0) break
                val bytesToRead = minOf(rowStride, remaining)
                yBuffer.get(rowData, 0, bytesToRead)
                val destOffset = row * width
                if (pixelStride == 1) {
                    System.arraycopy(rowData, 0, yBytes, destOffset, minOf(width, bytesToRead))
                } else {
                    var srcIdx = 0
                    for (col in 0 until width) {
                        if (srcIdx >= bytesToRead) break
                        yBytes[destOffset + col] = rowData[srcIdx]
                        srcIdx += pixelStride
                    }
                }
            }
        }

        val source = PlanarYUVLuminanceSource(
            yBytes,
            width,
            height,
            0,
            0,
            width,
            height,
            false
        )

        val decodedText = try {
            qrReader.decode(BinaryBitmap(HybridBinarizer(source)), decodeHints).text
        } catch (_: Throwable) {
            qrReader.reset()
            try {
                qrReader.decode(BinaryBitmap(GlobalHistogramBinarizer(source)), decodeHints).text
            } catch (_: Throwable) {
                null
            }
        } finally {
            qrReader.reset()
        }?.trim()

        if (!decodedText.isNullOrEmpty()) {
            val isValidGrade = UfopGradeCodec.parseAndValidatePreview(
                rawText = decodedText,
                sourceLabel = "QR Code"
            ).isSuccess
            if (isValidGrade) {
                if (hasHandledResult.compareAndSet(false, true)) {
                    onValidQrFound(decodedText)
                }
            } else {
                onInvalidQrFound()
            }
        }
    } finally {
        imageProxy.close()
    }
}
