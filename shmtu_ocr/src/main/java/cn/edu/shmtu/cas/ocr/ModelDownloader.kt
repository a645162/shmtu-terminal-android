package cn.edu.shmtu.cas.ocr

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN_Model.ModelSource
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN_Model.ModelVersion
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ModelDownloader {

    companion object {
        private const val TAG = "ModelDownloader"
        private const val MAX_DOWNLOAD_ATTEMPTS = 3
        private val SEMVER_TAG_REGEX = Regex("""^v(\d+)\.(\d+)\.(\d+)$""")

        /**
         * 列出 GitHub releases,选 v{maxMajor}.{<=maxMinor}.x 中最新 patch。
         * 失败时返回 fallback。仅用于 v2 模型;v1 不再更新。
         */
        fun resolveLatestV2Tag(
            client: OkHttpClient,
            maxMajor: Int = SHMTU_NCNN_Model.V2_MAX_SUPPORTED_MAJOR,
            maxMinor: Int = SHMTU_NCNN_Model.V2_MAX_SUPPORTED_MINOR,
            fallback: String = SHMTU_NCNN_Model.V2_DEFAULT_TAG,
        ): String {
            return try {
                val url = "${SHMTU_NCNN_Model.GITHUB_RELEASES_API}?per_page=100"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "shmtu-cas-ocr-android/1.0")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "list releases failed: HTTP ${resp.code}; fallback=$fallback")
                        return fallback
                    }
                    val body = resp.body.string()
                    val arr = JSONArray(body)
                    val candidates = mutableListOf<Triple<IntArray, String, Int>>()
                    for (i in 0 until arr.length()) {
                        val rel = arr.getJSONObject(i)
                        if (rel.optBoolean("draft", false)) continue
                        if (rel.optBoolean("prerelease", false)) continue
                        val tag = rel.optString("tag_name", "")
                        val m = SEMVER_TAG_REGEX.matchEntire(tag) ?: continue
                        val (mj, mn, pt) = m.destructured.toList().map { it.toInt() }
                        if (mj == maxMajor && mn <= maxMinor) {
                            candidates.add(Triple(intArrayOf(mj, mn, pt), tag, i))
                        }
                    }
                    if (candidates.isEmpty()) {
                        Log.w(TAG, "no v$maxMajor.$maxMinor.x release; fallback=$fallback")
                        return fallback
                    }
                    candidates.sortByDescending { it.first[0] * 1_000_000 + it.first[1] * 1_000 + it.first[2] }
                    val chosen = candidates[0].second
                    Log.i(TAG, "resolved latest v2 tag: $chosen (${candidates.size} candidates)")
                    chosen
                }
            } catch (e: Exception) {
                Log.w(TAG, "resolveLatestV2Tag failed: ${e.message}; fallback=$fallback")
                fallback
            }
        }
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

    // ===================== v1 download helpers (unchanged) =====================

    private fun fetchChecksums(
        primarySource: ModelSource,
        fallbackSource: ModelSource
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
            primarySource = ModelSource.GITEE,
            fallbackSource = ModelSource.GITHUB
        ) ?: return Result.failure(IllegalStateException("无法获取 SHA256 校验清单"))

        val modelDir = SHMTU_NCNN_Model.getModelDir(context, ModelVersion.V1)
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

    fun download(source: ModelSource, context: Context, listener: DownloadProgressListener) {
        Thread {
            val modelDir = SHMTU_NCNN_Model.getModelDir(context, ModelVersion.V1)
            val dir = File(modelDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val primarySource = source
            val fallbackSource = if (source == ModelSource.GITEE) ModelSource.GITHUB else ModelSource.GITEE

            val urls = SHMTU_NCNN_Model.buildModelUrls(primarySource)
            val fallbackUrls = SHMTU_NCNN_Model.buildModelUrls(fallbackSource)

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

    // ===================== v2 download (manifest-driven) =====================

    /**
     * v2 model download entry point.
     *
     * Flow:
     *  1. Fetch `<base>/<tag>/model-assets.json` from primary, falling back to the other source.
     *  2. Locate the `artifacts[]` entry matching `engine=="ncnn"`, `precision` and `backbone`.
     *  3. Download each file referenced in that artifact's `files[]`, with SHA256 verification.
     *
     * File names follow the convention produced by [SHMTU_NCNN_Model.getV2ModelFiles] and are
     * matched against the `release_asset_name` of each manifest entry.
     */
    fun downloadV2(
        source: ModelSource,
        context: Context,
        listener: DownloadProgressListener,
        tag: String? = null,
        backbone: String = SHMTU_NCNN_Model.V2_DEFAULT_BACKBONE,
        precision: String = SHMTU_NCNN_Model.V2_DEFAULT_PRECISION,
    ) {
        Thread {
            try {
                val modelDir = SHMTU_NCNN_Model.getModelDir(context, ModelVersion.V2)
                val dir = File(modelDir)
                if (!dir.exists()) dir.mkdirs()

                val primary = source
                val fallback = if (source == ModelSource.GITEE) ModelSource.GITHUB else ModelSource.GITEE

                val resolvedTag = tag ?: resolveLatestV2Tag(client)

                val manifest = fetchV2Manifest(primary, fallback, resolvedTag)
                if (manifest == null) {
                    mainHandler.post { listener.onError("无法获取 v2 manifest (tag=$resolvedTag)") }
                    return@Thread
                }

                val artifact = selectV2Artifact(manifest, backbone, precision)
                if (artifact == null) {
                    mainHandler.post {
                        listener.onError("v2 manifest 中找不到 engine=ncnn backbone=$backbone precision=$precision 的产物")
                    }
                    return@Thread
                }

                val files = artifact.getJSONArray("files")
                val totalFiles = files.length()
                if (totalFiles == 0) {
                    mainHandler.post { listener.onError("v2 manifest 产物中没有 files 列表") }
                    return@Thread
                }

                for (i in 0 until totalFiles) {
                    val entry = files.getJSONObject(i)
                    val releaseAssetName = entry.getString("release_asset_name")
                    val expectedHash = entry.takeIf { !it.isNull("sha256") }?.optString("sha256")?.takeIf { it.isNotEmpty() }
                    val file = File(modelDir + releaseAssetName)
                    val fileIndex = i + 1

                    if (file.exists() && file.length() > 0L) {
                        mainHandler.post {
                            listener.onProgress(
                                fileIndex = fileIndex,
                                totalFiles = totalFiles,
                                currentFileName = releaseAssetName,
                                currentFileProgress = 100,
                                overallProgress = (((i + 1) * 100f) / totalFiles).toInt()
                            )
                        }
                        continue
                    }

                    var success = false
                    var lastError: String? = null
                    for (attempt in 0 until MAX_DOWNLOAD_ATTEMPTS) {
                        val useSource = if (attempt % 2 == 0) primary else fallback
                        val sourceLabel = if (attempt % 2 == 0) "primary" else "fallback"
                        val urlStr = buildV2FileUrl(useSource, resolvedTag, releaseAssetName)
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
                        if (expectedHash != null) {
                            val actualHash = computeSHA256(file)
                            if (actualHash != expectedHash.lowercase()) {
                                Log.w(TAG, "SHA256 mismatch for $releaseAssetName (attempt ${attempt + 1})")
                                lastError = "SHA256 checksum mismatch"
                                file.delete()
                                continue
                            }
                            Log.i(TAG, "SHA256 verified for $releaseAssetName")
                        }
                        success = true
                        break
                    }

                    if (!success) {
                        mainHandler.post { listener.onError("Download failed: $releaseAssetName - $lastError") }
                        return@Thread
                    }

                    mainHandler.post {
                        listener.onProgress(
                            fileIndex = fileIndex,
                            totalFiles = totalFiles,
                            currentFileName = releaseAssetName,
                            currentFileProgress = 100,
                            overallProgress = (((i + 1) * 100f) / totalFiles).toInt()
                        )
                    }
                }

                mainHandler.post { listener.onSuccess() }
            } catch (e: Exception) {
                Log.w(TAG, "v2 download failed: ${e.message}", e)
                mainHandler.post { listener.onError("v2 download failed: ${e.message}") }
            }
        }.start()
    }

    private fun fetchV2Manifest(primary: ModelSource, fallback: ModelSource, tag: String): JSONObject? {
        val sources = arrayOf(primary, fallback)
        for (source in sources) {
            val url = buildV2FileUrl(source, tag, SHMTU_NCNN_Model.V2_MANIFEST_FILENAME)
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val text = response.body?.string() ?: continue
                        return JSONObject(text)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch v2 manifest from $source: ${e.message}")
            }
        }
        return null
    }

    private fun selectV2Artifact(manifest: JSONObject, backbone: String, precision: String): JSONObject? {
        val artifacts = manifest.optJSONArray("artifacts") ?: return null
        for (i in 0 until artifacts.length()) {
            val a = artifacts.getJSONObject(i)
            if (a.optString("engine") == "ncnn"
                && a.optString("precision") == precision
                && a.optString("backbone") == backbone) {
                return a
            }
        }
        return null
    }

    private fun buildV2FileUrl(source: ModelSource, tag: String, fileName: String): String {
        val prefix = if (source == ModelSource.GITHUB)
            SHMTU_NCNN_Model.V2_URL_MODEL_PREFIX_GITHUB
        else
            SHMTU_NCNN_Model.V2_URL_MODEL_PREFIX_GITEE
        return "$prefix$tag/$fileName"
    }

    fun release() {
        client.dispatcher.executorService.shutdownNow()
        client.connectionPool.evictAll()
    }
}
