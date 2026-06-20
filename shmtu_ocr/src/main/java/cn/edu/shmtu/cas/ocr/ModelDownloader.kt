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
         * maxMinor < 0 表示"不限 minor,只锁 major"。
         * 客户端用 Int.MIN_VALUE 传参。
         */
        private const val UNBOUNDED_MINOR: Int = Int.MIN_VALUE

        /**
         * 解析 `v{major}.{minor}.{patch}` 格式的 tag。失败返回 null。
         */
        private val SEMVER_PATTERN = Regex("""^v(\d+)\.(\d+)\.(\d+)$""")

        /**
         * 校验 tag 是否满足最小版本约束。
         *
         * 返回 `true` 表示通过,`false` 表示低于最小版本或无法解析。
         * 调用方应据此决定是否拒绝手动指定的 tag。
         */
        fun isTagAboveMinVersion(tag: String): Boolean {
            val m = SEMVER_PATTERN.matchEntire(tag) ?: return false
            val (mj, mn, pt) = m.destructured.toList().map { it.toInt() }
            return mj > SHMTU_NCNN_Model.V2_MIN_SUPPORTED_MAJOR ||
                (mj == SHMTU_NCNN_Model.V2_MIN_SUPPORTED_MAJOR &&
                    (mn > SHMTU_NCNN_Model.V2_MIN_SUPPORTED_MINOR ||
                        (mn == SHMTU_NCNN_Model.V2_MIN_SUPPORTED_MINOR &&
                            pt >= SHMTU_NCNN_Model.V2_MIN_SUPPORTED_PATCH)))
        }

        /**
         * 列出 GitHub releases,选 v{maxMajor}.{<=maxMinor}.x 中最新 patch。
         * maxMinor < 0 表示不限 minor,只锁 major。
         * 同时过滤低于 V2_MIN_SUPPORTED_* 的 tag。
         * 失败时返回 fallback。仅用于 v2 模型;v1 不再更新。
         */
        fun resolveLatestV2Tag(
            client: OkHttpClient,
            maxMajor: Int = SHMTU_NCNN_Model.V2_MAX_SUPPORTED_MAJOR,
            maxMinor: Int = SHMTU_NCNN_Model.V2_MAX_SUPPORTED_MINOR,
            fallback: String = SHMTU_NCNN_Model.V2_DEFAULT_TAG,
        ): String {
            val minMajor = SHMTU_NCNN_Model.V2_MIN_SUPPORTED_MAJOR
            val minMinor = SHMTU_NCNN_Model.V2_MIN_SUPPORTED_MINOR
            val minPatch = SHMTU_NCNN_Model.V2_MIN_SUPPORTED_PATCH
            // Try Gitee first, then fall back to GitHub
            val apiUrls = listOf(
                "${SHMTU_NCNN_Model.GITEE_RELEASES_API}?per_page=100" to false,
                "${SHMTU_NCNN_Model.GITHUB_RELEASES_API}?per_page=100" to true,
            )
            for ((url, isGithub) in apiUrls) {
                try {
                    val reqBuilder = Request.Builder()
                        .url(url)
                        .header("User-Agent", "shmtu-cas-ocr-android/1.0")
                    if (isGithub) {
                        reqBuilder.header("Accept", "application/vnd.github+json")
                    }
                    val req = reqBuilder.build()
                    val result = client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            Log.w(TAG, "list releases from ${if (isGithub) "GitHub" else "Gitee"} failed: HTTP ${resp.code}")
                            null
                        } else {
                            val body = resp.body.string()
                            val arr = JSONArray(body)
                            val candidates = mutableListOf<Triple<IntArray, String, Int>>()
                            for (i in 0 until arr.length()) {
                                val rel = arr.getJSONObject(i)
                                if (rel.optBoolean("draft", false)) continue
                                if (rel.optBoolean("prerelease", false)) continue
                                val tag = rel.optString("tag_name", "")
                                val m = SEMVER_PATTERN.matchEntire(tag) ?: continue
                                val (mj, mn, pt) = m.destructured.toList().map { it.toInt() }
                                if (mj != maxMajor) continue
                                if (maxMinor != UNBOUNDED_MINOR && mn > maxMinor) continue
                                if (mj < minMajor) continue
                                if (mj == minMajor && mn < minMinor) continue
                                if (mj == minMajor && mn == minMinor && pt < minPatch) continue
                                candidates.add(Triple(intArrayOf(mj, mn, pt), tag, i))
                            }
                            if (candidates.isEmpty()) {
                                val filter = if (maxMinor == UNBOUNDED_MINOR) "v$maxMajor.x.x" else "v$maxMajor.$maxMinor.x"
                                Log.w(TAG, "no release matched $filter from ${if (isGithub) "GitHub" else "Gitee"}")
                                null
                            } else {
                                candidates.sortByDescending { it.first[0] * 1_000_000 + it.first[1] * 1_000 + it.first[2] }
                                candidates[0].second
                            }
                        }
                    }
                    if (result != null) {
                        Log.i(TAG, "resolved latest v2 tag: $result (from ${if (isGithub) "GitHub" else "Gitee"})")
                        return result
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "resolveLatestV2Tag from ${if (isGithub) "GitHub" else "Gitee"} failed: ${e.message}")
                }
            }
            Log.w(TAG, "all sources failed; fallback=$fallback")
            return fallback
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
     *  2. Parse the manifest via [parseReleaseManifest].
     *  3. Locate the artifact matching `engine=="ncnn"`, `precision` and `backbone` within
     *     the selected model. When `assetStem` is null, picks the first model with an ncnn
     *     artifact matching the requested (backbone, precision).
     *  4. Download each file referenced in that artifact's `files[]`, with SHA256 verification.
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
        assetStem: String? = null,
    ) {
        Thread {
            try {
                val modelDir = SHMTU_NCNN_Model.getModelDir(context, ModelVersion.V2)
                val dir = File(modelDir)
                if (!dir.exists()) dir.mkdirs()

                val primary = source
                val fallback = if (source == ModelSource.GITEE) ModelSource.GITHUB else ModelSource.GITEE

                val resolvedTag = tag?.also {
                    // 手动指定 tag 时校验最小版本
                    if (!isTagAboveMinVersion(it)) {
                        mainHandler.post {
                            listener.onError("tag $it 低于最低支持版本 v${SHMTU_NCNN_Model.V2_MIN_SUPPORTED_MAJOR}.${SHMTU_NCNN_Model.V2_MIN_SUPPORTED_MINOR}.${SHMTU_NCNN_Model.V2_MIN_SUPPORTED_PATCH}")
                        }
                        return@Thread
                    }
                } ?: resolveLatestV2Tag(client)

                val manifest = fetchV2Manifest(primary, fallback, resolvedTag)
                if (manifest == null) {
                    mainHandler.post { listener.onError("无法获取 v2 manifest (tag=$resolvedTag)") }
                    return@Thread
                }

                val parsed = parseReleaseManifest(manifest.toString())
                val artifact = selectV2Artifact(parsed, backbone, precision, assetStem)
                if (artifact == null) {
                    mainHandler.post {
                        listener.onError("v2 manifest 中找不到 engine=ncnn backbone=$backbone precision=$precision 的产物")
                    }
                    return@Thread
                }

                val totalFiles = artifact.files.size
                if (totalFiles == 0) {
                    mainHandler.post { listener.onError("v2 manifest 产物中没有 files 列表") }
                    return@Thread
                }

                for (i in 0 until totalFiles) {
                    val file = artifact.files[i]
                    val releaseAssetName = file.releaseAssetName
                    val expectedHash = file.sha256?.takeIf { it.isNotEmpty() }
                    val fileOnDisk = File(modelDir + releaseAssetName)
                    val fileIndex = i + 1

                    if (fileOnDisk.exists() && fileOnDisk.length() > 0L) {
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
                            file = fileOnDisk,
                            fileIndex = fileIndex,
                            totalFiles = totalFiles,
                            completedFilesBefore = i,
                            listener = listener
                        )
                        if (!downloadOk) {
                            lastError = "HTTP download failed from $sourceLabel"
                            if (fileOnDisk.exists()) fileOnDisk.delete()
                            continue
                        }
                        if (expectedHash != null) {
                            val actualHash = computeSHA256(fileOnDisk)
                            if (actualHash != expectedHash.lowercase()) {
                                Log.w(TAG, "SHA256 mismatch for $releaseAssetName (attempt ${attempt + 1})")
                                lastError = "SHA256 checksum mismatch"
                                fileOnDisk.delete()
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
                        val text = response.body.string().takeIf { it.isNotEmpty() } ?: continue
                        return JSONObject(text)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch v2 manifest from $source: ${e.message}")
            }
        }
        return null
    }

    /**
     * 解析 `model-assets.json` 字符串为强类型的 [V2ReleaseManifest]。
     *
     * 所有字段使用 `opt*` 系列方法,缺失时安全降级 (例如 `model_size_m` 缺失 → null,
     * `metrics` 缺失 → null)。
     */
    fun parseReleaseManifest(jsonString: String): V2ReleaseManifest {
        val root = JSONObject(jsonString)
        val schemaVersion = root.optInt("schema_version", 2)
        val explicitModelCount = root.optInt("model_count", 0)

        val modelList = mutableListOf<String>()
        val modellistArr = root.optJSONArray("modellist")
        if (modellistArr != null) {
            for (i in 0 until modellistArr.length()) {
                val s = modellistArr.optString(i, "").takeIf { it.isNotEmpty() }
                if (s != null) modelList.add(s)
            }
        }

        val modelsArr = root.optJSONArray("models")
        val parsedModels = mutableListOf<OcrModelInfo>()
        if (modelsArr != null && modelsArr.length() > 0) {
            for (i in 0 until modelsArr.length()) {
                val m = modelsArr.optJSONObject(i) ?: continue
                parsedModels.add(parseModelEntry(m))
                // 若顶层 modellist 缺失,顺便用 models[].asset_stem 补全
                if (modelList.size <= i) {
                    val stem = m.optString("asset_stem", "").takeIf { it.isNotEmpty() }
                    if (stem != null) modelList.add(stem)
                }
            }
        }

        val modelCount = if (explicitModelCount > 0) explicitModelCount else parsedModels.size

        return V2ReleaseManifest(
            schemaVersion = schemaVersion,
            modelCount = modelCount,
            modelList = modelList,
            models = parsedModels,
        )
    }

    /**
     * 返回 manifest 中所有模型条目 (按 schema 中 `models[]` 顺序)。
     */
    fun listModelsFromManifest(manifest: V2ReleaseManifest): List<OcrModelInfo> = manifest.models

    /**
     * 在指定 [model] 的分组 artifacts 中查找 `(engine, precision)` 匹配的条目。
     */
    fun findArtifactInModel(model: OcrModelInfo, engine: String, precision: String): OcrArtifactInfo? {
        val byPrecision = model.artifactsByEngine[engine] ?: return null
        return byPrecision[precision]
    }

    /**
     * 选择匹配 (backbone, precision, assetStem?) 的 v2 artifact。
     *
     * 查找顺序:
     *  1. 若 [assetStem] 非空,先按 asset_stem 精确匹配模型;
     *  2. 若未指定或第一步失败,按 backbone 字段匹配;
     *  3. 在匹配的模型里,按 engine=ncnn / precision 找 artifact。
     */
    fun selectV2Artifact(
        manifest: V2ReleaseManifest,
        backbone: String,
        precision: String,
        assetStem: String? = null,
    ): OcrArtifactInfo? {
        val candidates = manifest.models
        if (candidates.isEmpty()) return null

        val resolved = candidates.firstOrNull { m ->
            (assetStem == null || m.assetStem == assetStem) &&
                m.backbone == backbone
        } ?: candidates.firstOrNull { m ->
            m.backbone == backbone
        } ?: candidates.firstOrNull()

        if (resolved != null) {
            val ncnn = findArtifactInModel(resolved, "ncnn", precision)
            if (ncnn != null) return ncnn
        }
        for (m in candidates) {
            val ncnn = findArtifactInModel(m, "ncnn", precision)
            if (ncnn != null && m.backbone == backbone) return ncnn
        }
        return null
    }

    // ---- private manifest parsing helpers ----

    private fun parseModelEntry(obj: JSONObject): OcrModelInfo {
        val assetStem = obj.optString("asset_stem", "")
        val backbone = obj.optString("backbone", "")
        val version = obj.optString("version", "")
        val family = obj.optString("family", "")
        // 优先用 manifest 提供的 display_name (由 trainer 的 friendly_model_name 注入)
        // fallback 到客户端翻译 (assetStem → "MobileNetV3-Small + TriSlot Decoder + v2.0")
        val manifestDisplay = obj.optString("display_name", "")
        val displayName = when {
            manifestDisplay.isNotBlank() && manifestDisplay != "CAS OCR TriSlot Decoder" -> manifestDisplay
            assetStem.isNotEmpty() -> friendlyModelName(assetStem, backbone, family)
            else -> manifestDisplay.ifEmpty { backbone }
        }

        val modelSizeM: Double? = if (obj.isNull("model_size_m")) null else obj.optDouble("model_size_m", Double.NaN).takeIf { !it.isNaN() }

        val supportedBackbones = mutableListOf<String>()
        val sbArr = obj.optJSONArray("supported_backbones")
        if (sbArr != null) {
            for (i in 0 until sbArr.length()) {
                val s = sbArr.optString(i, "").takeIf { it.isNotEmpty() }
                if (s != null) supportedBackbones.add(s)
            }
        }

        val metrics = obj.optJSONObject("metrics")?.let(::parseMetrics)

        val artifacts = obj.optJSONObject("artifacts") ?: JSONObject()
        val byEngine = parseArtifactsByEngine(artifacts)

        return OcrModelInfo(
            assetStem = assetStem,
            displayName = displayName,
            backbone = backbone,
            version = version,
            family = family,
            modelSizeM = modelSizeM,
            metrics = metrics,
            supportedBackbones = supportedBackbones,
            artifactsByEngine = byEngine,
        )
    }

    private fun parseMetrics(obj: JSONObject): OcrModelMetrics {
        fun readDouble(name: String): Double? {
            if (obj.isNull(name)) return null
            val d = obj.optDouble(name, Double.NaN)
            return if (d.isNaN()) null else d
        }
        return OcrModelMetrics(
            valAccExpression = readDouble("val_acc_expression"),
            valLoss = readDouble("val_loss"),
            testAccExpression = readDouble("test_acc_expression"),
            testLoss = readDouble("test_loss"),
        )
    }

    private fun parseArtifactsByEngine(obj: JSONObject): Map<String, Map<String, OcrArtifactInfo>> {
        val out = mutableMapOf<String, Map<String, OcrArtifactInfo>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val engine = keys.next()
            val byPrecisionObj = obj.optJSONObject(engine) ?: continue
            val byPrecision = mutableMapOf<String, OcrArtifactInfo>()
            val pKeys = byPrecisionObj.keys()
            while (pKeys.hasNext()) {
                val precision = pKeys.next()
                val a = byPrecisionObj.optJSONObject(precision) ?: continue
                byPrecision[precision] = parseArtifactObject(a, engine, precision)
            }
            out[engine] = byPrecision
        }
        return out
    }

    private fun parseArtifactObject(
        obj: JSONObject,
        engineOverride: String? = null,
        precisionOverride: String? = null,
    ): OcrArtifactInfo {
        val engine = engineOverride ?: obj.optString("engine", "")
        val precision = precisionOverride ?: obj.optString("precision", "")
        val format = obj.optString("format", "").takeIf { it.isNotEmpty() }
        val filesArr = obj.optJSONArray("files") ?: JSONArray()
        val files = mutableListOf<OcrAssetFile>()
        for (j in 0 until filesArr.length()) {
            val f = filesArr.optJSONObject(j) ?: continue
            val path = f.optString("path", "").takeIf { it.isNotEmpty() } ?: ""
            val releaseAssetName = f.optString("release_asset_name", "").takeIf { it.isNotEmpty() } ?: path
            val sha = f.optString("sha256", "").takeIf { it.isNotEmpty() }
            files.add(
                OcrAssetFile(
                    path = path,
                    releaseAssetName = releaseAssetName,
                    sha256 = sha,
                )
            )
        }
        return OcrArtifactInfo(
            engine = engine,
            precision = precision,
            format = format,
            files = files,
        )
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

/**
 * 把 manifest 中的 `asset_stem` 翻译成人类可读名称。
 *
 * asset_stem 约定: `<backbone>.<family>.<version>`
 * 例: `mobilenet_v3_small.trislot_decoder.v2_0`
 *
 * 用户看到的: `MobileNetV3-Small · TriSlot Decoder · v2.0`
 *
 * 同时支持从 backbone 字段补充（兼容历史 manifest），
 * 最后 fallback 到 PascalCase 通用转换。
 */
internal fun friendlyModelName(assetStem: String, backbone: String = "", family: String = ""): String {
    val parts = assetStem.split(".").filter { it.isNotBlank() }
    if (parts.isEmpty()) {
        val bb = friendlyBackbone(backbone)
        val fa = friendlyFamily(family)
        return listOfNotNull(bb.takeIf { it.isNotBlank() }, fa.takeIf { it.isNotBlank() }).joinToString(" · ")
    }

    // 从后往前连续合并 version token
    var verEnd = parts.size
    for (i in parts.lastIndex downTo 0) {
        if (isVersionToken(parts[i])) {
            verEnd = i
        } else break
    }

    val backbonePart: String
    val familyPart: String
    val versionPart: String

    if (verEnd < parts.size) {
        backbonePart = if (verEnd > 1) parts[0] else backbone
        familyPart = when {
            verEnd > 1 -> parts.subList(1, verEnd).joinToString(".")
            verEnd == 1 -> parts[1]
            else -> family
        }
        versionPart = parts.subList(verEnd, parts.size).joinToString(".")
    } else if (parts.size >= 2) {
        backbonePart = parts[0]
        familyPart = parts.subList(1, parts.size).joinToString(".")
        versionPart = ""
    } else {
        backbonePart = parts[0]
        familyPart = family
        versionPart = ""
    }

    val bb = friendlyBackbone(backbonePart)
    val fa = friendlyFamily(familyPart)
    val ver = friendlyVersion(versionPart)
    return listOfNotNull(
        bb.takeIf { it.isNotBlank() },
        fa.takeIf { it.isNotBlank() },
        ver.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
}

/**
 * Backbone 字段值 → 人类可读标签
 * 例: `mobilenet_v3_small` → `MobileNetV3-Small`
 */
internal fun friendlyBackbone(backbone: String): String {
    if (backbone.isBlank()) return ""
    val canonical = canonicalBackboneLabels[backbone.lowercase()]
    if (canonical != null) return canonical
    return backbone.toPascalCase()
}

/**
 * Family 字段值 → 人类可读标签
 * 例: `trislot_decoder` → `TriSlot Decoder`
 */
internal fun friendlyFamily(family: String): String {
    if (family.isBlank()) return ""
    val canonical = canonicalFamilyLabels[family.lowercase()]
    if (canonical != null) return canonical
    return family.toPascalCase().replace("Decoder", " Decoder").trim()
}

/**
 * Version 字段值 → 人类可读 (v2_0 → v2.0)
 */
internal fun friendlyVersion(version: String): String {
    if (version.isBlank()) return ""
    val cleaned = version.trimStart('v', 'V').replace('_', '.')
    return if (cleaned.isBlank()) "" else "v$cleaned"
}

/**
 * 判断字符串是否是 version token: 纯数字 / v开头的数字 / 数字开头纯数字下划线点
 */
internal fun isVersionToken(s: String): Boolean {
    if (s.isBlank()) return false
    // 纯数字
    if (s.all { it.isDigit() }) return true
    // v/V 开头 + 数字
    if (s.length >= 2 && (s[0] == 'v' || s[0] == 'V') && s[1].isDigit()) return true
    // 数字开头, 只含数字/下划线/点
    if (s[0].isDigit() && s.all { it.isDigit() || it == '_' || it == '.' }) return true
    return false
}

/** snake_case → PascalCase (回退转换) */
internal fun String.toPascalCase(): String {
    if (isBlank()) return ""
    return split('_', '-', ' ').filter { it.isNotBlank() }
        .joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
}

/**
 * 已知的 backbone 字段值翻译表 (与 shmtu-cas-ocr-model 仓库 trainer 训练产物对齐)。
 * 新增 backbone 时同步更新此表。
 */
private val canonicalBackboneLabels: Map<String, String> = mapOf(
    // MobileNet 系列
    "mobilenet_v3_small" to "MobileNetV3-Small",
    "mobilenet_v3_large" to "MobileNetV3-Large",
    "mobilenetv4_conv_small" to "MobileNetV4-Conv-Small",
    "mobilenetv4_conv_medium" to "MobileNetV4-Conv-Medium",
    "mobilenetv4_conv_large" to "MobileNetV4-Conv-Large",
    "mobilenetv4_hybrid_medium" to "MobileNetV4-Hybrid-Medium",
    // EfficientNet 系列
    "efficientnet_b0" to "EfficientNet-B0",
    "efficientnet_b1" to "EfficientNet-B1",
    "efficientnet_b2" to "EfficientNet-B2",
    "efficientnet_b3" to "EfficientNet-B3",
    "efficientnetv2_s" to "EfficientNetV2-S",
    "efficientnetv2_m" to "EfficientNetV2-M",
    // ResNet 系列 (v1 历史模型)
    "resnet18" to "ResNet-18",
    "resnet34" to "ResNet-34",
    "resnet50" to "ResNet-50",
    "resnet101" to "ResNet-101",
    // ConvNeXt 系列
    "convnext_tiny" to "ConvNeXt-Tiny",
    "convnext_small" to "ConvNeXt-Small",
    "convnext_base" to "ConvNeXt-Base",
    // ViT 系列
    "vit_small_patch16_224" to "ViT-S/16",
    "vit_base_patch16_224" to "ViT-B/16",
)

/** 已知的 family 字段值翻译表 */
private val canonicalFamilyLabels: Map<String, String> = mapOf(
    "trislot_decoder" to "TriSlot Decoder",
    "single_head" to "Single-Head",
    "multi_head" to "Multi-Head",
    "ctc" to "CTC",
)
