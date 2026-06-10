package cn.edu.shmtu.cas.ocr

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * 候选 v2 release tag 目录:
 * - 启动时 (或用户点刷新) 调 GitHub API 列所有 release
 * - 过滤 v{MAX_SUPPORTED_MAJOR}.{<=MAX_SUPPORTED_MINOR}.x 范围
 * - 结果持久化到 app 私有目录 filesDir/ocr/v2_candidate_tags.json
 * - 启动默认不刷网络,缓存有就用缓存
 */
object OcrV2TagCatalog {

    private const val CACHE_FILENAME = "v2_candidate_tags.json"
    private const val MAX_CACHED_ENTRIES = 50

    data class CatalogEntry(
        val tag: String,
        val publishedAt: String?,
        val isPrerelease: Boolean,
    )

    private fun cacheFile(context: Context): File {
        val dir = File(context.filesDir, "ocr")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, CACHE_FILENAME)
    }

    fun loadFromCache(context: Context): List<CatalogEntry>? {
        val f = cacheFile(context)
        if (!f.exists() || f.length() == 0L) return null
        return try {
            val arr = JSONArray(f.readText(Charsets.UTF_8))
            val list = mutableListOf<CatalogEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    CatalogEntry(
                        tag = o.getString("tag"),
                        publishedAt = o.optString("published_at").takeIf { it.isNotEmpty() },
                        isPrerelease = o.optBoolean("prerelease", false),
                    )
                )
            }
            list.take(MAX_CACHED_ENTRIES)
        } catch (_: Exception) {
            null
        }
    }

    fun saveToCache(context: Context, entries: List<CatalogEntry>) {
        val arr = JSONArray()
        entries.take(MAX_CACHED_ENTRIES).forEach { e ->
            arr.put(
                JSONObject()
                    .put("tag", e.tag)
                    .put("published_at", e.publishedAt ?: JSONObject.NULL)
                    .put("prerelease", e.isPrerelease)
            )
        }
        val tmp = File(cacheFile(context).parentFile, "$CACHE_FILENAME.tmp")
        try {
            tmp.writeText(arr.toString(2), Charsets.UTF_8)
            if (!tmp.renameTo(cacheFile(context))) {
                cacheFile(context).writeText(tmp.readText(Charsets.UTF_8))
                tmp.delete()
            }
        } catch (_: IOException) {
        }
    }

    fun fetchFromNetwork(
        client: OkHttpClient,
        maxMajor: Int = SHMTU_NCNN_Model.V2_MAX_SUPPORTED_MAJOR,
        maxMinor: Int = SHMTU_NCNN_Model.V2_MAX_SUPPORTED_MINOR,
    ): List<CatalogEntry> {
        val minMajor = SHMTU_NCNN_Model.V2_MIN_SUPPORTED_MAJOR
        val minMinor = SHMTU_NCNN_Model.V2_MIN_SUPPORTED_MINOR
        val minPatch = SHMTU_NCNN_Model.V2_MIN_SUPPORTED_PATCH
        val url = "${SHMTU_NCNN_Model.GITHUB_RELEASES_API}?per_page=100"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "shmtu-cas-ocr-android/1.0")
            .header("Accept", "application/vnd.github+json")
            .build()
        val resp = client.newCall(req).execute()
        return resp.use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
            val body = it.body.string()
            val arr = JSONArray(body)
            val pattern = Regex("""^v(\d+)\.(\d+)\.(\d+)$""")
            val out = mutableListOf<CatalogEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optBoolean("draft", false)) continue
                val tag = o.optString("tag_name", "")
                val m = pattern.matchEntire(tag) ?: continue
                val (mj, mn, pt) = m.destructured.toList().map { it.toInt() }
                if (mj != maxMajor) continue
                if (maxMinor != Integer.MIN_VALUE && mn > maxMinor) continue
                // 过滤低于最小版本的 tag
                if (mj < minMajor) continue
                if (mj == minMajor && mn < minMinor) continue
                if (mj == minMajor && mn == minMinor && pt < minPatch) continue
                out.add(
                    CatalogEntry(
                        tag = tag,
                        publishedAt = o.optString("published_at", "").takeIf { it.isNotEmpty() },
                        isPrerelease = o.optBoolean("prerelease", false),
                    )
                )
            }
            out.take(MAX_CACHED_ENTRIES)
        }
    }
}
