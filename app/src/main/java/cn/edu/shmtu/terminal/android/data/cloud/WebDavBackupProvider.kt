package cn.edu.shmtu.terminal.android.data.cloud

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebDAV 备份 Provider
 *
 * 兼容标准 WebDAV 协议：Nextcloud / 坚果云 / 自建 NAS / Apache mod_dav
 */
@Singleton
class WebDavBackupProvider @Inject constructor(
    private val baseOkHttpClient: OkHttpClient
) : CloudBackupProvider {

    override val providerId: String = "webdav"
    override val displayName: String = "WebDAV"

    private val tag = "WebDavBackup"
    private var config: WebDavConfig? = null

    private val client: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun configure(config: WebDavConfig) {
        this.config = config
    }

    private fun requireConfig(): WebDavConfig =
        config ?: throw IllegalStateException("WebDAV not configured. Call configure() first.")

    private fun authHeader(): String =
        Credentials.basic(requireConfig().username, requireConfig().password)

    private fun baseUrl(): String = requireConfig().serverUrl.trimEnd('/')

    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl()}/${requireConfig().backupRoot.trimStart('/')}"
            val request = Request.Builder()
                .url(url)
                .method("PROPFIND", null)
                .header("Authorization", authHeader())
                .header("Depth", "0")
                .build()
            client.newCall(request).execute().use { resp ->
                resp.isSuccessful || resp.code == 207
            }
        } catch (e: Exception) {
            Log.w(tag, "testConnection failed", e)
            false
        }
    }

    override suspend fun upload(remotePath: String, data: ByteArray): CloudUploadResult =
        withContext(Dispatchers.IO) {
            val fullUrl = "${baseUrl()}/${remotePath.trimStart('/')}"
            val parentDir = remotePath.substringBeforeLast('/')
            ensureDir(parentDir)

            val request = Request.Builder()
                .url(fullUrl)
                .put(data.toRequestBody("application/octet-stream".toMediaType()))
                .header("Authorization", authHeader())
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw RuntimeException("Upload failed: HTTP ${resp.code}")
                }
                CloudUploadResult(
                    remotePath = remotePath,
                    remoteUrl = fullUrl,
                    bytes = data.size.toLong(),
                    uploadedAt = System.currentTimeMillis()
                )
            }
        }

    override suspend fun download(remotePath: String): ByteArray = withContext(Dispatchers.IO) {
        val fullUrl = "${baseUrl()}/${remotePath.trimStart('/')}"
        val request = Request.Builder()
            .url(fullUrl)
            .get()
            .header("Authorization", authHeader())
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Download failed: HTTP ${resp.code}")
            resp.body?.bytes() ?: throw RuntimeException("Empty response body")
        }
    }

    override suspend fun list(prefix: String): List<CloudBackupMeta> = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/${prefix.trimStart('/')}"
        val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:getlastmodified/><d:getcontentlength/><d:resourcetype/></d:prop></d:propfind>"""
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", body.toRequestBody("application/xml".toMediaType()))
            .header("Authorization", authHeader())
            .header("Depth", "1")
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 207) {
                throw RuntimeException("List failed: HTTP ${resp.code}")
            }
            val xml = resp.body?.string() ?: return@withContext emptyList()
            parsePropfindResponse(xml)
        }
    }

    override suspend fun delete(remotePath: String): Boolean = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/${remotePath.trimStart('/')}"
        val request = Request.Builder()
            .url(url)
            .delete()
            .header("Authorization", authHeader())
            .build()
        client.newCall(request).execute().use { resp -> resp.isSuccessful }
    }

    private suspend fun ensureDir(dirPath: String) {
        val url = "${baseUrl()}/${dirPath.trimStart('/')}"
        val request = Request.Builder()
            .url(url)
            .method("MKCOL", null)
            .header("Authorization", authHeader())
            .build()
        try { client.newCall(request).execute().use { /* swallow */ } } catch (_: Exception) {}
    }

    private fun parsePropfindResponse(xml: String): List<CloudBackupMeta> {
        val results = mutableListOf<CloudBackupMeta>()
        val responsePattern = Regex("<D:response[^>]*>(.*?)</D:response>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val hrefPattern = Regex("<D:href[^>]*>(.*?)</D:href>", RegexOption.IGNORE_CASE)
        val sizePattern = Regex("<D:getcontentlength[^>]*>(\\d+)</D:getcontentlength>", RegexOption.IGNORE_CASE)
        val modPattern = Regex("<D:getlastmodified[^>]*>(.*?)</D:getlastmodified>", RegexOption.IGNORE_CASE)
        val isCollection = Regex("<D:resourcetype[^>]*>.*<D:collection.*?</D:resourcetype>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

        for (match in responsePattern.findAll(xml)) {
            val body = match.groupValues[1]
            val href = hrefPattern.find(body)?.groupValues?.get(1) ?: continue
            if (isCollection.containsMatchIn(body)) continue
            val size = sizePattern.find(body)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val mod = parseHttpDate(modPattern.find(body)?.groupValues?.get(1)) ?: 0L
            val name = href.substringAfterLast('/')
            if (name.isBlank()) continue
            results.add(CloudBackupMeta(
                remotePath = href.trimStart('/'),
                name = name,
                size = size,
                lastModified = mod
            ))
        }
        return results.sortedByDescending { it.lastModified }
    }

    private fun parseHttpDate(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val formats = listOf("EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, d MMM yyyy HH:mm:ss zzz")
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.ENGLISH)
                return sdf.parse(text)?.time
            } catch (_: Exception) {}
        }
        return null
    }
}

data class WebDavConfig(
    val serverUrl: String,
    val username: String,
    val password: String,
    val backupRoot: String = "shmtu-backup",
    val encryptionKey: String? = null
)
