package com.example.resq

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

internal const val MODEL_NAME = "gemma-4-E2B-it-IQ4_XS.gguf"
internal const val BUNDLED_MODEL_ASSET = "llm/gemma-4-E2B-it-IQ4_XS.gguf"
internal const val EXPECTED_BUNDLED_MODEL_SHA256 =
    "d50db8b4573839fb4a3a5e66342bb9977da4e821992ad722974359504f1d4ed3"
internal const val MODEL_DOWNLOAD_WORK_NAME = "resq_model_download"
internal const val KEY_MODEL_DOWNLOAD_PROGRESS = "progress"
internal const val KEY_MODEL_DOWNLOAD_ERROR = "error"

private const val HF_MODEL_REPO_ID = "unsloth/gemma-4-E2B-it-GGUF"
private const val HF_MODEL_API_URL = "https://huggingface.co/api/models/$HF_MODEL_REPO_ID?blobs=true"
private const val MIN_HF_MODEL_BYTES = 2_000_000_000L
private const val MODEL_DOWNLOAD_NOTIFICATION_ID = 11904
private const val MODEL_DOWNLOAD_CHANNEL_ID = "resq_model_download"
private val HF_MODEL_CANDIDATES = listOf(
    "gemma-4-E2B-it-UD-Q4_K_XL.gguf",
    "gemma-4-E2B-it-IQ4_XS.gguf"
)

class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        var lastNotificationProgress = -1
        return try {
            setForeground(createForegroundInfo(0))
            downloadHuggingFaceModel(applicationContext) { progress ->
                setProgress(workDataOf(KEY_MODEL_DOWNLOAD_PROGRESS to progress))
                if (progress == 100 || progress - lastNotificationProgress >= 5) {
                    lastNotificationProgress = progress
                    setForeground(createForegroundInfo(progress))
                }
            }
            Result.success(workDataOf(KEY_MODEL_DOWNLOAD_PROGRESS to 100))
        } catch (err: CancellationException) {
            throw err
        } catch (err: Exception) {
            val message = err.message ?: "모델 다운로드 실패"
            Log.e("ResQApp-LLM", message, err)
            Result.failure(workDataOf(KEY_MODEL_DOWNLOAD_ERROR to message))
        }
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val notification = createNotification(progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                MODEL_DOWNLOAD_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(MODEL_DOWNLOAD_NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(progress: Int): Notification {
        createNotificationChannel()
        val intent = Intent(applicationContext, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, flags)
        val contentText = if (progress in 1..99) {
            "백그라운드 다운로드 $progress%"
        } else {
            "백그라운드에서 모델을 내려받는 중입니다."
        }

        return NotificationCompat.Builder(applicationContext, MODEL_DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.resq_ic_status_circle)
            .setContentTitle("ResQ 모델 다운로드")
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .apply {
                if (progress in 1..99) {
                    setProgress(100, progress, false)
                } else {
                    setProgress(100, 0, true)
                }
            }
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            MODEL_DOWNLOAD_CHANNEL_ID,
            "ResQ model download",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }
}

internal fun llmModelFile(context: Context): File = File(context.filesDir, MODEL_NAME)

internal fun llmTempModelFile(context: Context): File = File(context.filesDir, "$MODEL_NAME.part")

internal suspend fun downloadHuggingFaceModel(
    context: Context,
    onProgress: suspend (Int) -> Unit
) {
    withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val target = findHuggingFaceModelTarget()
        val connection = openHuggingFaceDownload(target.url)
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("모델 다운로드 실패: ${connection.responseCode}")
            }

            val total = connection.contentLengthLong
            val modelFile = llmModelFile(appContext)
            val tempFile = llmTempModelFile(appContext)
            if (tempFile.exists()) {
                tempFile.delete()
            }

            onProgress(0)
            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = input.read(buffer)
                    var written = 0L
                    var lastProgress = -1
                    while (read >= 0) {
                        coroutineContext.ensureActive()
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val percent = ((written * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastProgress) {
                                lastProgress = percent
                                onProgress(percent)
                            }
                        }
                        read = input.read(buffer)
                    }
                }
            }

            if (modelFile.exists()) {
                modelFile.delete()
            }
            if (!tempFile.renameTo(modelFile)) {
                throw IllegalStateException("모델 파일 저장에 실패했습니다.")
            }
            verifyDownloadedModelFile(modelFile, target.expectedBytes)
            onProgress(100)
        } finally {
            connection.disconnect()
        }
    }
}

internal fun bundledModelSize(context: Context): Long {
    return try {
        context.assets.openFd(BUNDLED_MODEL_ASSET).use { descriptor ->
            descriptor.length.takeIf { it > 0L } ?: -1L
        }
    } catch (_: Exception) {
        -1L
    }
}

internal fun verifyBundledModelFile(file: File) {
    val actual = sha256(file)
    if (!actual.equals(EXPECTED_BUNDLED_MODEL_SHA256, ignoreCase = true)) {
        file.delete()
        throw IllegalStateException("모델 파일 검증에 실패했습니다. 다시 다운로드해 주세요.")
    }
}

private data class ModelDownloadTarget(
    val fileName: String,
    val url: URL,
    val expectedBytes: Long?
)

private fun findHuggingFaceModelTarget(): ModelDownloadTarget {
    return try {
        val connection = (URL(HF_MODEL_API_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30000
            readTimeout = 30000
            setRequestProperty("User-Agent", "ResQ/1.0")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val siblings = JSONObject(body).optJSONArray("siblings") ?: JSONArray()
            for (candidate in HF_MODEL_CANDIDATES) {
                for (index in 0 until siblings.length()) {
                    val sibling = siblings.optJSONObject(index) ?: continue
                    if (sibling.optString("rfilename") == candidate) {
                        return ModelDownloadTarget(
                            fileName = candidate,
                            url = huggingFaceResolveUrl(candidate),
                            expectedBytes = sibling.optLong("size", -1L).takeIf { it > 0L }
                        )
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        fallbackHuggingFaceModelTarget()
    } catch (err: Exception) {
        Log.w("ResQApp-LLM", "Hugging Face 모델 목록 조회 실패, 기본 파일로 다운로드합니다: ${err.message}")
        fallbackHuggingFaceModelTarget()
    }
}

private fun fallbackHuggingFaceModelTarget(): ModelDownloadTarget {
    val fileName = HF_MODEL_CANDIDATES.first()
    return ModelDownloadTarget(fileName, huggingFaceResolveUrl(fileName), null)
}

private fun huggingFaceResolveUrl(fileName: String): URL {
    return URL("https://huggingface.co/$HF_MODEL_REPO_ID/resolve/main/$fileName")
}

private fun openHuggingFaceDownload(initialUrl: URL): HttpURLConnection {
    var currentUrl = initialUrl
    var redirects = 0
    while (redirects < 8) {
        val connection = (currentUrl.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 60000
            readTimeout = 120000
            setRequestProperty("User-Agent", "ResQ/1.0")
            setRequestProperty("Accept", "application/octet-stream")
        }
        val code = connection.responseCode
        if (code in 300..399) {
            val location = connection.getHeaderField("Location")
                ?: throw IllegalStateException("Hugging Face 리다이렉트 위치가 없습니다.")
            connection.disconnect()
            currentUrl = URL(currentUrl, location)
            redirects += 1
            continue
        }
        return connection
    }
    throw IllegalStateException("Hugging Face 모델 다운로드 리다이렉트가 너무 많습니다.")
}

private fun verifyDownloadedModelFile(file: File, expectedBytes: Long?) {
    val minimumBytes = expectedBytes?.let { (it * 9L) / 10L } ?: MIN_HF_MODEL_BYTES
    if (file.length() < minimumBytes) {
        file.delete()
        throw IllegalStateException("Hugging Face 모델 파일이 너무 작습니다. 다시 다운로드해 주세요.")
    }
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1024 * 1024)
        var read = input.read(buffer)
        while (read >= 0) {
            digest.update(buffer, 0, read)
            read = input.read(buffer)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
