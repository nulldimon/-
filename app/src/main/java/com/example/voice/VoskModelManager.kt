package com.example.voice

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

sealed class ModelInstallState {
    data object NotInstalled : ModelInstallState()
    data class Downloading(val progressPercent: Int, val downloadedMb: Float, val totalMb: Float) : ModelInstallState()
    data class Extracting(val message: String) : ModelInstallState()
    data class Ready(val modelPath: String) : ModelInstallState()
    data class Error(val message: String) : ModelInstallState()
}

class VoskModelManager(private val context: Context) {

    private val _state = MutableStateFlow<ModelInstallState>(ModelInstallState.NotInstalled)
    val state: StateFlow<ModelInstallState> = _state.asStateFlow()

    private val modelDir: File
        get() = File(context.filesDir, "vosk-model-ru")

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    companion object {
        private val MODEL_URLS = listOf(
            "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip",
            "https://alphacephei.com/kaldi/models/vosk-model-small-ru-0.22.zip"
        )
    }

    init {
        refreshStatus()
    }

    fun refreshStatus(): Boolean {
        val ready = isModelValid(modelDir)
        _state.value = if (ready) {
            ModelInstallState.Ready(modelDir.absolutePath)
        } else {
            ModelInstallState.NotInstalled
        }
        return ready
    }

    fun getReadyModelDir(): File? {
        return if (isModelValid(modelDir)) modelDir else null
    }

    private fun isModelValid(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        val hasConf = File(dir, "conf").isDirectory || File(dir, "model.conf").exists()
        val hasAm = File(dir, "am").isDirectory || File(dir, "final.mdl").exists()
        val hasGraph = File(dir, "graph").isDirectory
        return hasConf && (hasAm || hasGraph)
    }

    suspend fun downloadRussianModel(): Result<File> = withContext(Dispatchers.IO) {
        val tempZip = File(context.cacheDir, "vosk-model-small-ru-0.22.zip")
        var lastException: Exception? = null

        for (url in MODEL_URLS) {
            try {
                _state.value = ModelInstallState.Downloading(0, 0f, 45f)
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IOException("Пустой ответ сервера")
                    val contentLength = body.contentLength()
                    val totalMb = if (contentLength > 0) contentLength / (1024f * 1024f) else 45f

                    body.byteStream().use { input ->
                        FileOutputStream(tempZip).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var totalRead = 0L
                            var lastPercent = -1
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                totalRead += read
                                val percent = if (contentLength > 0) {
                                    ((totalRead * 100) / contentLength).toInt().coerceIn(0, 100)
                                } else {
                                    ((totalRead / (45f * 1024f * 1024f)) * 100f).toInt().coerceIn(0, 99)
                                }
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    _state.value = ModelInstallState.Downloading(
                                        progressPercent = percent,
                                        downloadedMb = totalRead / (1024f * 1024f),
                                        totalMb = totalMb
                                    )
                                }
                            }
                        }
                    }
                }

                _state.value = ModelInstallState.Extracting("Распаковка русской голосовой модели...")
                tempZip.inputStream().use { extractZipStream(it) }
                tempZip.delete()

                if (isModelValid(modelDir)) {
                    _state.value = ModelInstallState.Ready(modelDir.absolutePath)
                    return@withContext Result.success(modelDir)
                } else {
                    throw IOException("Распакованный архив не содержит файлов модели Vosk")
                }
            } catch (e: Exception) {
                lastException = e
                tempZip.delete()
            }
        }

        val msg = "Не удалось скачать модель (${lastException?.message ?: "ошибка сети"}). Можно загрузить ZIP вручную."
        _state.value = ModelInstallState.Error(msg)
        Result.failure(lastException ?: IOException(msg))
    }

    suspend fun importFromUri(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        try {
            _state.value = ModelInstallState.Extracting("Импорт русской модели из ZIP-файла...")
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Не удалось открыть выбранный файл")
            input.use { extractZipStream(it) }
            if (isModelValid(modelDir)) {
                _state.value = ModelInstallState.Ready(modelDir.absolutePath)
                Result.success(modelDir)
            } else {
                val msg = "Выбранный архив не является моделью Vosk (нужны папки am и conf)"
                _state.value = ModelInstallState.Error(msg)
                Result.failure(IOException(msg))
            }
        } catch (e: Exception) {
            val msg = "Ошибка импорта модели: ${e.message}"
            _state.value = ModelInstallState.Error(msg)
            Result.failure(e)
        }
    }

    suspend fun removeModel() = withContext(Dispatchers.IO) {
        if (modelDir.exists()) {
            modelDir.deleteRecursively()
        }
        _state.value = ModelInstallState.NotInstalled
    }

    private fun extractZipStream(inputStream: InputStream) {
        val stagingDir = File(context.cacheDir, "vosk-model-staging")
        if (stagingDir.exists()) stagingDir.deleteRecursively()
        stagingDir.mkdirs()

        val canonicalStaging = stagingDir.canonicalPath + File.separator
        ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val entry = zis.nextEntry ?: break
                val outFile = File(stagingDir, entry.name)
                if (!outFile.canonicalPath.startsWith(canonicalStaging)) {
                    throw SecurityException("Небезопасный путь в архиве: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        while (true) {
                            val count = zis.read(buffer)
                            if (count == -1) break
                            fos.write(buffer, 0, count)
                        }
                    }
                }
                zis.closeEntry()
            }
        }

        // Flatten if archive has a single root folder (e.g. vosk-model-small-ru-0.22/)
        val sourceRoot = if (isModelValid(stagingDir)) {
            stagingDir
        } else {
            stagingDir.listFiles()?.firstOrNull { it.isDirectory && isModelValid(it) }
                ?: stagingDir
        }

        if (modelDir.exists()) modelDir.deleteRecursively()
        if (!sourceRoot.renameTo(modelDir)) {
            sourceRoot.copyRecursively(modelDir, overwrite = true)
        }
        stagingDir.deleteRecursively()
    }
}
