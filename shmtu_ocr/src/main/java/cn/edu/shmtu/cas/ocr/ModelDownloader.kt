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
                        val m = SEMVER_PATTERN.matchEntire(tag) ?: continue
                        val (mj, mn, pt) = m.destructured.toList().map { it.toInt() }
                        if (mj != maxMajor) continue
                        // maxMinor == Int.MIN_VALUE 表示不限 minor,只锁 major。
                        if (maxMinor != UNBOUNDED_MINOR && mn > maxMinor) continue
                        // 过滤低于最小版本的 tag
                        if (mj < minMajor) continue
                        if (mj == minMajor && mn < minMinor) continue
                        if (mj == minMajor && mn == minMinor && pt < minPatch) continue
                        candidates.add(Triple(intArrayOf(mj, mn, pt), tag, i))
                    }
                    if (candidates.isEmpty()) {
                        val filter = if (maxMinor == UNBOUNDED_MINOR) "v$maxMajor.x.x" else "v$maxMajor.$maxMinor.x"
                        Log.w(TAG, "no release matched $filter; fallback=$fallback")
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
        val displayName = obj.optString("display_name", "").ifEmpty { assetStem }

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
