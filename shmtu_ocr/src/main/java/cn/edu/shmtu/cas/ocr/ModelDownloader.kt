package cn.edu.shmtu.cas.ocr

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ModelDownloader {

    companion object {
        private const val TAG = "ModelDownloader"
        private const val MAX_DOWNLOAD_ATTEMPTS = 3
    }

    interface DownloadProgressListener {
        fun onProgress(
            fileIndex: Int,
            totalFiles: Int,
            currentFileName: String,
            currentFileProgress: Int,
            overallProgress: Int
        )
        fun onSuccess()
        fun onError(error: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun fetchChecksums(
        primarySource: SHMTU_NCNN_Model.ModelSource,
        fallbackSource: SHMTU_NCNN_Model.ModelSource
    ): Map<String, String>? {
        val sources = arrayOf(primarySource, fallbackSource)
        for (source in sources) {
            try {
                val url = SHMTU_NCNN_Model.buildChecksumUrl(source)
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body
                        val content = body.string()
                        val checksums = mutableMapOf<String, String>()
                        for (line in content.lines()) {
                            val trimmed = line.trim()
                            if (trimmed.isEmpty()) continue
                            // Format: <64-char-hex>  <filename>
                            // Two or more spaces separate hash from filename
                            val parts = trimmed.split(Regex("\\s+"), limit = 2)
                            if (parts.size == 2 && parts[0].length == 64) {
                                checksums[parts[1]] = parts[0].lowercase()
                            }
                        }
                        if (checksums.isNotEmpty()) {
                            return checksums
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch checksums from $source: ${e.message}")
            }
        }
        return null
    }

    private fun computeSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifyDownloadedModels(context: Context): Result<String> {
        val checksums = fetchChecksums(
            primarySource = SHMTU_NCNN_Model.ModelSource.GITEE,
            fallbackSource = SHMTU_NCNN_Model.ModelSource.GITHUB
        ) ?: return Result.failure(IllegalStateException("无法获取 SHA256 校验清单"))

        val modelDir = SHMTU_NCNN_Model.getModelDir(context)
        val mismatches = mutableListOf<String>()
        var verifiedCount = 0

        SHMTU_NCNN_Model.MODEL_FILES.forEach { fileName ->
            val file = File(modelDir + fileName)
            if (!file.exists() || file.length() <= 0L) {
                mismatches += "$fileName 不存在或为空"
                return@forEach
            }
            val expectedHash = checksums[fileName]
            if (expectedHash == null) {
                mismatches += "$fileName 缺少远端校验值"
                return@forEach
            }
            val actualHash = computeSHA256(file)
            if (actualHash != expectedHash) {
                mismatches += "$fileName 校验失败"
                return@forEach
            }
            verifiedCount++
        }

        return if (mismatches.isEmpty()) {
            Result.success("SHA256 校验通过，已验证 $verifiedCount 个模型文件")
        } else {
            Result.failure(IllegalStateException(mismatches.joinToString("；")))
        }
    }

    private fun downloadFile(
        urlStr: String,
        file: File,
        fileIndex: Int,
        totalFiles: Int,
        completedFilesBefore: Int,
        listener: DownloadProgressListener
    ): Boolean {
        try {
            mainHandler.post {
                listener.onProgress(
                    fileIndex = fileIndex,
                    totalFiles = totalFiles,
                    currentFileName = file.name,
                    currentFileProgress = 0,
                    overallProgress = ((completedFilesBefore * 100f) / totalFiles).toInt()
                )
            }

            val request = Request.Builder()
                .url(urlStr)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return false
                }
                val body = response.body
                val contentLength = body.contentLength()
                var bytesRead: Long = 0

                FileOutputStream(file).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            if (contentLength > 0) {
                                val progress = ((bytesRead * 100) / contentLength).toInt()
                                val overallProgress = (((completedFilesBefore * 100L) + progress) / totalFiles).toInt()
                                mainHandler.post {
                                    listener.onProgress(
                                        fileIndex = fileIndex,
                                        totalFiles = totalFiles,
                                        currentFileName = file.name,
                                        currentFileProgress = progress,
                                        overallProgress = overallProgress
                                    )
                                }
                            }
                        }
                    }
                }
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download error for ${file.name}: ${e.message}")
            return false
        }
    }

    fun download(source: SHMTU_NCNN_Model.ModelSource, context: Context, listener: DownloadProgressListener) {
        Thread {
            val modelDir = SHMTU_NCNN_Model.getModelDir(context)
            val dir = File(modelDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val primarySource = source
            val fallbackSource = if (source == SHMTU_NCNN_Model.ModelSource.GITEE)
                SHMTU_NCNN_Model.ModelSource.GITHUB
            else
                SHMTU_NCNN_Model.ModelSource.GITEE

            val urls = SHMTU_NCNN_Model.buildModelUrls(primarySource)
            val fallbackUrls = SHMTU_NCNN_Model.buildModelUrls(fallbackSource)

            // Fetch checksums before downloading model files
            var checksums: Map<String, String>? = null
            try {
                checksums = fetchChecksums(primarySource, fallbackSource)
                if (checksums != null) {
                    Log.i(TAG, "Loaded ${checksums.size} checksum entries for verification")
                } else {
                    Log.w(TAG, "Could not fetch checksum file, continuing without verification")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching checksums: ${e.message}, continuing without verification")
            }

            val totalFiles = SHMTU_NCNN_Model.MODEL_FILES.size
            var downloadedFiles = 0

            for (i in 0 until totalFiles) {
                val fileName = SHMTU_NCNN_Model.MODEL_FILES[i]
                val file = File(modelDir + fileName)
                val fileIndex = i + 1

                if (file.exists()) {
                    mainHandler.post {
                        listener.onProgress(
                            fileIndex = fileIndex,
                            totalFiles = totalFiles,
                            currentFileName = fileName,
                            currentFileProgress = 100,
                            overallProgress = (((i + 1) * 100f) / totalFiles).toInt()
                        )
                    }
                    downloadedFiles++
                    continue
                }

                var success = false
                var lastError: String? = null
                val expectedHash = checksums?.get(fileName)

                // Up to MAX_DOWNLOAD_ATTEMPTS attempts, cycling through primary/fallback sources
                for (attempt in 0 until MAX_DOWNLOAD_ATTEMPTS) {
                    val urlStr = if (attempt % 2 == 0) urls[i] else fallbackUrls[i]
                    val sourceLabel = if (attempt % 2 == 0) "primary" else "fallback"

                    val downloadOk = downloadFile(
                        urlStr = urlStr,
                        file = file,
                        fileIndex = fileIndex,
                        totalFiles = totalFiles,
                        completedFilesBefore = i,
                        listener = listener
                    )
                    if (!downloadOk) {
                        lastError = "HTTP download failed from $sourceLabel"
                        if (file.exists()) file.delete()
                        continue
                    }

                    // Verify checksum if available
                    if (expectedHash != null) {
                        val actualHash = computeSHA256(file)
                        if (actualHash != expectedHash) {
                            Log.w(TAG, "SHA256 mismatch for $fileName (attempt ${attempt + 1}): expected=$expectedHash actual=$actualHash")
                            lastError = "SHA256 checksum mismatch"
                            file.delete()
                            continue
                        }
                        Log.i(TAG, "SHA256 verified for $fileName")
                    }

                    success = true
                    break
                }

                if (success) {
                    downloadedFiles++
                    mainHandler.post {
                        listener.onProgress(
                            fileIndex = fileIndex,
                            totalFiles = totalFiles,
                            currentFileName = fileName,
                            currentFileProgress = 100,
                            overallProgress = (((i + 1) * 100f) / totalFiles).toInt()
                        )
                    }
                } else {
                    mainHandler.post {
                        listener.onError("Download failed: $fileName - $lastError")
                    }
                    return@Thread
                }
            }

            mainHandler.post {
                listener.onSuccess()
            }
        }.start()
    }

    fun release() {
        client.dispatcher.executorService.shutdownNow()
        client.connectionPool.evictAll()
    }
}
