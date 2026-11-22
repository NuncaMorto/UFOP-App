package br.ufop.campus.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import br.ufop.campus.BuildConfig
import br.ufop.campus.core.security.CampusCryptoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Envio de feedback com prints de tela via HTTPS multipart/form-data em segundo plano.
 *
 * Nenhum compositor de e-mail é aberto e o destinatário NUNCA é exibido na interface,
 * em mensagens de status ou em erros.
 */

private const val FEEDBACK_APP_VERSION = "1.9.10"
private const val MAX_SCREENSHOTS = 3
private const val MAX_SCREENSHOT_SIDE_PX = 1280
private const val SCREENSHOT_JPEG_QUALITY = 75
private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 25_000

internal const val FEEDBACK_MAX_SCREENSHOTS = MAX_SCREENSHOTS

private fun feedbackEndpoint(): String = BuildConfig.FEEDBACK_ENDPOINT.trim()

data class FeedbackSendResult(
    val sentImmediately: Boolean,
    val queuedOffline: Boolean,
    val statusMessage: String
) {
    val isError: Boolean get() = !sentImmediately && !queuedOffline
}

private class FeedbackPayload(
    val category: String,
    val message: String,
    val senderName: String,
    val campusLabel: String,
    val device: String,
    val screenshots: List<ByteArray>
)

private fun queueDir(context: Context): File =
    File(context.filesDir, "feedback_queue").apply { if (!exists()) mkdirs() }

/** Reduz o print para no máximo 1280px no maior lado e comprime em JPEG 75%. */
internal fun compressScreenshotToJpeg(context: Context, uri: Uri): ByteArray? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        if (maxDim <= 0) return null
        var sampleSize = 1
        while ((maxDim / sampleSize) > MAX_SCREENSHOT_SIDE_PX) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, SCREENSHOT_JPEG_QUALITY, out)
        bitmap.recycle()
        out.toByteArray()
    } catch (_: Throwable) {
        null
    }
}

private fun deviceDescription(): String =
    "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"

private fun buildMultipartBody(payload: FeedbackPayload, boundary: String): ByteArray {
    val out = ByteArrayOutputStream()
    fun writeText(text: String) = out.write(text.toByteArray(Charsets.UTF_8))
    fun field(name: String, value: String) {
        writeText("--$boundary\r\n")
        writeText("Content-Disposition: form-data; name=\"$name\"\r\n")
        writeText("Content-Type: text/plain; charset=UTF-8\r\n\r\n")
        writeText(value)
        writeText("\r\n")
    }

    field("_subject", "[Vida Acadêmica] Feedback: ${payload.category}")
    field("_captcha", "false")
    field("_template", "table")
    field("categoria", payload.category)
    field("mensagem", payload.message)
    field("remetente", payload.senderName.ifBlank { "Anônimo" })
    field("versao_app", FEEDBACK_APP_VERSION)
    field("dispositivo", payload.device)
    field("campus", payload.campusLabel)

    payload.screenshots.take(MAX_SCREENSHOTS).forEachIndexed { index, bytes ->
        val fieldName = if (index == 0) "attachment" else "attachment${index + 1}"
        writeText("--$boundary\r\n")
        writeText("Content-Disposition: form-data; name=\"$fieldName\"; filename=\"print_${index + 1}.jpg\"\r\n")
        writeText("Content-Type: image/jpeg\r\n\r\n")
        out.write(bytes)
        writeText("\r\n")
    }
    writeText("--$boundary--\r\n")
    return out.toByteArray()
}

/** Retorna true se o servidor aceitou o envio; lança IOException em falhas de rede/servidor. */
private fun postPayload(payload: FeedbackPayload) {
    val endpoint = feedbackEndpoint()
    if (endpoint.isBlank()) {
        throw IOException("Envio de feedback não configurado nesta compilação.")
    }
    val boundary = "----VidaAcademica${UUID.randomUUID().toString().replace("-", "")}"
    val body = buildMultipartBody(payload, boundary)
    val connection = (URL(endpoint).openConnection() as HttpURLConnection)
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.doOutput = true
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "VidaAcademica-Android/$FEEDBACK_APP_VERSION")
        connection.setRequestProperty("Origin", "https://nuncamorto.github.io")
        connection.setRequestProperty("Referer", "https://nuncamorto.github.io/Vida-Academica/")
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.setFixedLengthStreamingMode(body.size)
        connection.outputStream.use { it.write(body) }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
        if (code !in 200..299) {
            throw IOException("Servidor de feedback indisponível (HTTP $code).")
        }
        if (responseText.contains("\"success\":\"false\"", ignoreCase = true) ||
            responseText.contains("\"success\":false", ignoreCase = true)
        ) {
            throw IOException("Servidor de feedback recusou o envio.")
        }
    } finally {
        connection.disconnect()
    }
}

private fun enqueuePayload(context: Context, payload: FeedbackPayload): Boolean {
    return try {
        val dir = File(queueDir(context), "fb_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}")
        dir.mkdirs()
        val meta = JSONObject()
            .put("category", payload.category)
            .put("message", payload.message)
            .put("sender", payload.senderName)
            .put("campus", payload.campusLabel)
            .put("device", payload.device)
            .put("screenshots", payload.screenshots.size)
        payload.screenshots.forEachIndexed { index, bytes ->
            File(dir, "img$index.jpg").writeBytes(CampusCryptoEngine.encryptBytes(bytes))
        }
        File(dir, "meta.json").writeBytes(
            CampusCryptoEngine.encryptBytes(meta.toString().toByteArray(Charsets.UTF_8))
        )
        true
    } catch (_: Throwable) {
        false
    }
}

private fun readQueuedPayload(dir: File): FeedbackPayload? {
    return try {
        val metaFile = File(dir, "meta.json")
        if (!metaFile.exists()) return null
        val decryptedMetaBytes = CampusCryptoEngine.decryptBytesIfNeeded(metaFile.readBytes())
        val meta = JSONObject(decryptedMetaBytes.toString(Charsets.UTF_8))
        val count = meta.optInt("screenshots", 0).coerceIn(0, MAX_SCREENSHOTS)
        val shots = (0 until count).mapNotNull { index ->
            File(dir, "img$index.jpg").takeIf { it.exists() }?.readBytes()?.let { raw ->
                CampusCryptoEngine.decryptBytesIfNeeded(raw)
            }
        }
        FeedbackPayload(
            category = meta.optString("category", "Outro"),
            message = meta.optString("message"),
            senderName = meta.optString("sender"),
            campusLabel = meta.optString("campus"),
            device = meta.optString("device", deviceDescription()),
            screenshots = shots
        )
    } catch (_: Throwable) {
        null
    }
}

fun getPendingFeedbackCount(context: Context): Int {
    val dirs = File(context.filesDir, "feedback_queue").listFiles() ?: return 0
    return dirs.count { it.isDirectory && File(it, "meta.json").exists() }
}

/** Envia o feedback (com até 3 prints comprimidos). Em caso de falha de rede, enfileira localmente. */
suspend fun sendFeedback(
    context: Context,
    category: String,
    message: String,
    senderName: String,
    campusLabel: String,
    screenshotUris: List<Uri>
): FeedbackSendResult = withContext(Dispatchers.IO) {
    if (feedbackEndpoint().isBlank()) {
        return@withContext FeedbackSendResult(
            sentImmediately = false,
            queuedOffline = false,
            statusMessage = "Envio de feedback não configurado nesta compilação."
        )
    }
    val appContext = context.applicationContext
    val screenshots = screenshotUris.take(MAX_SCREENSHOTS).mapNotNull {
        compressScreenshotToJpeg(appContext, it)
    }
    val payload = FeedbackPayload(
        category = category,
        message = message.trim(),
        senderName = senderName.trim(),
        campusLabel = campusLabel,
        device = deviceDescription(),
        screenshots = screenshots
    )
    try {
        postPayload(payload)
        FeedbackSendResult(
            sentImmediately = true,
            queuedOffline = false,
            statusMessage = "Feedback enviado! Obrigado por ajudar a melhorar o app."
        )
    } catch (_: Throwable) {
        if (enqueuePayload(appContext, payload)) {
            FeedbackSendResult(
                sentImmediately = false,
                queuedOffline = true,
                statusMessage = "Sem conexão agora. Feedback salvo no aparelho e pode ser reenviado depois em \"Reenviar pendentes\"."
            )
        } else {
            FeedbackSendResult(
                sentImmediately = false,
                queuedOffline = false,
                statusMessage = "Não foi possível enviar nem salvar o feedback. Tente novamente."
            )
        }
    }
}

/** Reenvia todos os relatórios pendentes na fila local. */
suspend fun retryPendingFeedback(context: Context): FeedbackSendResult = withContext(Dispatchers.IO) {
    val dirs = File(context.applicationContext.filesDir, "feedback_queue")
        .listFiles()
        ?.filter { it.isDirectory }
        .orEmpty()
    if (dirs.isEmpty()) {
        return@withContext FeedbackSendResult(true, false, "Nenhum feedback pendente.")
    }
    var sent = 0
    var failed = 0
    for (dir in dirs) {
        val payload = readQueuedPayload(dir)
        if (payload == null) {
            dir.deleteRecursively()
            continue
        }
        try {
            postPayload(payload)
            dir.deleteRecursively()
            sent++
        } catch (_: Throwable) {
            failed++
        }
    }
    when {
        failed == 0 -> FeedbackSendResult(
            sentImmediately = true,
            queuedOffline = false,
            statusMessage = "$sent feedback(s) pendente(s) enviado(s) com sucesso!"
        )
        else -> FeedbackSendResult(
            sentImmediately = false,
            queuedOffline = true,
            statusMessage = "$sent enviado(s); $failed ainda pendente(s). Verifique sua conexão e tente novamente."
        )
    }
}
