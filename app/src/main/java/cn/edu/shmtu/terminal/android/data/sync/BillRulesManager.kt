package cn.edu.shmtu.terminal.android.data.sync

import android.content.Context
import android.util.Log
import cn.edu.shmtu.terminal.android.ui.settings.FeatureSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 账单规则文件管理器 — 与 Tauri `DatabaseFileManager` 行为完全一致。
 *
 * 数据源:
 *   GitHub raw URL: https://raw.githubusercontent.com/a645162/shmtu-terminal/main/database/bill
 *   4 个文件: rules.toml / type.toml / position.toml / schedule.toml
 *
 * 本地缓存目录: context.filesDir/bill/ (私有存储, 与 assets/bill/ 内的出厂默认分离)
 *
 * 行为:
 *   1) [ensureLocalFiles] — 启动时调用,本地缺失则自动从 GitHub 补齐;网络失败时降级用 assets 出厂值
 *   2) [downloadAll]      — 用户手动触发, 强制刷新全部 4 个文件;写盘前备份为 .bak
 *   3) [readFile]         — 优先读 filesDir/bill/ 本地缓存, 缺失回退到 assets/bill/
 *   4) [localDir]         — 暴露本地缓存目录, 供其他模块查询文件元信息
 *
 * 调用方:
 *   - [cn.edu.shmtu.terminal.android.data.remote.EpayAdapter] 在懒加载 classifier / positionTranslator
 *     时调用 [readFile] 拿到最新规则
 *   - 设置页"同步规则"按钮调用 [downloadAll]
 *   - 启动时 [com.shmtu.terminal.App.onCreate] 调用 [ensureLocalFiles]
 */
@Singleton
class BillRulesManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val featureStore: FeatureSettingsStore
) {

    private val TAG = "BillRulesManager"

    /** GitHub raw URL base 默认值 — 与 Tauri `database/mod.rs:18` 完全一致 */
    private val DEFAULT_GITHUB_RAW_BASE =
        "https://raw.githubusercontent.com/a645162/shmtu-terminal/main/database/bill"

    /** 4 个文件名 — 与 Tauri `database/mod.rs:21` 完全一致 */
    val DB_FILES = listOf("rules.toml", "type.toml", "position.toml", "schedule.toml")

    /** 本地缓存目录: filesDir/bill/ */
    val localDir: File
        get() = File(context.filesDir, "bill").apply { if (!exists()) mkdirs() }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 当前生效的规则远程 base URL。
     * 优先使用用户在「设置 → 分类规则」中自定义的 URL;为空时回退到默认 GitHub。
     */
    fun currentRemoteBase(): String {
        val custom = runCatching { featureStore.rulesUpdateUrl.value }
            .getOrNull()
            ?.trim()
            .orEmpty()
        return if (custom.isNotEmpty()) custom.removeSuffix("/") else DEFAULT_GITHUB_RAW_BASE
    }

    /**
     * 启动时调用,确保本地 4 个文件都存在;缺失则从 GitHub 下载补齐。
     * 网络失败时**不会**抛异常 — 由后续 [readFile] 走 assets 回退。
     */
    suspend fun ensureLocalFiles(): EnsureResult = withContext(Dispatchers.IO) {
        val results = LinkedHashMap<String, Boolean>()
        localDir.mkdirs()
        for (filename in DB_FILES) {
            val local = File(localDir, filename)
            if (local.exists() && local.length() > 0L) {
                results[filename] = true
                continue
            }
            // 缺失 → 从远程 base 下载
            val ok = runCatching { downloadFileInternal(filename) }.isSuccess
            results[filename] = ok
            if (ok) Log.d(TAG, "$filename: downloaded from ${currentRemoteBase()}")
            else Log.w(TAG, "$filename: download failed, will fallback to assets at read time")
        }
        EnsureResult(results)
    }

    /**
     * 强制从远程 base 同步全部 4 个文件到本地缓存。
     * 写盘前把旧文件备份为 <name>.bak(若旧文件存在)。
     */
    suspend fun downloadAll(): DownloadResult = withContext(Dispatchers.IO) {
        val results = LinkedHashMap<String, DownloadFileResult>()
        var allOk = true
        for (filename in DB_FILES) {
            val r = runCatching { downloadFileInternal(filename) }
            if (r.isSuccess) {
                results[filename] = DownloadFileResult.Success(r.getOrNull()!!.length())
            } else {
                allOk = false
                val err = r.exceptionOrNull()
                results[filename] = DownloadFileResult.Failure(err?.message ?: err?.javaClass?.simpleName ?: "unknown")
                Log.w(TAG, "$filename: download failed: $err")
            }
        }
        DownloadResult(allOk, results)
    }

    /**
     * 读取本地缓存文件;缺失或空时回退到 assets/bill/<name>。
     * 抛出异常表示本地与 assets 都不可用。
     */
    fun readFile(filename: String): String {
        val local = File(localDir, filename)
        if (local.exists() && local.length() > 0L) {
            return local.readText(Charsets.UTF_8)
        }
        // 回退到 assets 内的出厂默认
        val assetPath = "bill/$filename"
        return context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /** 当前本地缓存文件是否存在(非空) */
    fun hasLocalFile(filename: String): Boolean {
        val local = File(localDir, filename)
        return local.exists() && local.length() > 0L
    }

    /**
     * 从 [currentRemoteBase] 下载单个文件,写盘前备份旧文件为 .bak。
     * 内部方法,出错抛异常由调用方 [ensureLocalFiles] / [downloadAll] 捕获。
     */
    private fun downloadFileInternal(filename: String): File {
        val url = "${currentRemoteBase()}/$filename"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}: $url")
            }
            val body = resp.body?.string()
                ?: throw IllegalStateException("Empty body: $url")
            // 备份旧文件
            val local = File(localDir, filename)
            if (local.exists()) {
                val backup = File(localDir, "$filename.bak")
                runCatching { local.copyTo(backup, overwrite = true) }
            }
            local.writeText(body, Charsets.UTF_8)
            Log.d(TAG, "$filename: downloaded ${body.length} bytes -> ${local.absolutePath}")
            return local
        }
    }

    data class EnsureResult(val perFile: Map<String, Boolean>) {
        val allReady: Boolean get() = perFile.values.all { it }
    }

    sealed class DownloadFileResult {
        data class Success(val bytes: Long) : DownloadFileResult()
        data class Failure(val reason: String) : DownloadFileResult()
    }

    data class DownloadResult(
        val allOk: Boolean,
        val perFile: Map<String, DownloadFileResult>
    )
}
