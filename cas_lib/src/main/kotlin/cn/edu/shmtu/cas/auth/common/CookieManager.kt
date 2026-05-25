package cn.edu.shmtu.cas.auth.common

import java.util.logging.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Cookie 管理器
 *
 * 管理 HTTP 会话 cookies，支持 JSON 格式的序列化/反序列化
 * 对齐 Rust 版本的 CookieJar
 *
 * JSON 格式：
 * ```json
 * {"JSESSIONID": {"value": "xxx"}}
 * ```
 */
class CookieManager {

    private companion object {
        val log = Logger.getLogger(CookieManager::class.java.name)
    }

    /**
     * Cookie 条目数据结构（用于 JSON 序列化）
     */
    @kotlinx.serialization.Serializable
    private data class CookieEntry(
        val value: String,
        val domain: String? = null
    )

    /**
     * 内部 cookie 存储，格式："name1=value1; name2=value2"
     */
    private val cookieString = StringBuilder()

    /**
     * 从 JSON 字符串恢复 cookies
     *
     * JSON 格式：
     * ```json
     * {"JSESSIONID": {"value": "xxx", "domain": null}}
     * ```
     *
     * @param json JSON 格式的 cookie 字符串
     * @return Result.success(Unit) 或 Result.failure(error)
     */
    fun restore(json: String): Result<Unit> {
        return try {
            if (json.isBlank()) {
                return Result.success(Unit)
            }

            val trimmed = json.trim()
            val map = mutableMapOf<String, String>()

            // 尝试使用 kotlinx.serialization 解析
            try {
                val entries: Map<String, CookieEntry> = Json.decodeFromString(trimmed)
                entries.forEach { (key, entry) ->
                    map[key] = entry.value
                }
            } catch (e: Exception) {
                // 兼容格式: "key1=value1; key2=value2"
                log.fine("[CookieManager] restore: falling back to legacy format")
                trimmed.split(";").map { it.trim() }.filter { it.contains("=") }.forEach {
                    val parts = it.split("=", limit = 2)
                    if (parts.size == 2) {
                        map[parts[0].trim()] = parts[1].trim()
                    }
                }
            }

            cookieString.clear()
            cookieString.append(map.entries.joinToString("; ") { "${it.key}=${it.value}" })

            log.info("[CookieManager] restore: loaded ${map.size} cookies")
            Result.success(Unit)
        } catch (e: Exception) {
            log.warning("[CookieManager] restore: failed to parse JSON: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 提取当前 cookies 为 JSON 字符串
     *
     * @return JSON 格式的 cookie 字符串
     */
    fun extract(): String {
        val map = mutableMapOf<String, CookieEntry>()

        for (pair in cookieString.toString().split(";")) {
            val pair = pair.trim()
            if (pair.contains("=")) {
                val idx = pair.indexOf("=")
                val key = pair.substring(0, idx).trim()
                val value = pair.substring(idx + 1).trim()
                if (key.isNotEmpty()) {
                    map[key] = CookieEntry(value = value)
                }
            }
        }

        return try {
            Json.encodeToString(map)
        } catch (e: Exception) {
            log.warning("[CookieManager] extract: failed to serialize: ${e.message}")
            "{}"
        }
    }

    /**
     * 从 Set-Cookie header 值追加 cookie
     *
     * 格式："name=value; path=...; domain=...; ..."
     *
     * @param headerVal Set-Cookie header 完整值
     */
    fun addFromSetCookie(headerVal: String) {
        val trimmed = headerVal.trim()

        // 提取 name=value 部分
        val nameValuePart = trimmed.split(";").firstOrNull()?.trim() ?: return

        val eqIdx = nameValuePart.indexOf("=")
        if (eqIdx <= 0) return

        val name = nameValuePart.substring(0, eqIdx).trim()
        val value = nameValuePart.substring(eqIdx + 1).trim()

        if (name.isEmpty() || value.isEmpty()) return

        // 移除同名 cookie，追加新值
        val currentCookies = cookieString.toString()
            .split(";")
            .map { it.trim() }
            .filter { !it.startsWith("$name=") && it.isNotEmpty() }

        cookieString.clear()
        if (currentCookies.isEmpty()) {
            cookieString.append("$name=$value")
        } else {
            cookieString.append(currentCookies.joinToString("; ") + "; $name=$value")
        }

        log.fine("[CookieManager] addFromSetCookie: added $name")
    }

    /**
     * 获取当前 cookie 字符串
     *
     * @return cookie 字符串，格式："name1=value1; name2=value2"
     */
    fun get(): String {
        return cookieString.toString()
    }

    /**
     * 检查是否为空
     *
     * @return true 如果没有 cookie
     */
    fun isEmpty(): Boolean {
        return cookieString.isEmpty() || cookieString.toString().isBlank()
    }

    /**
     * 清空所有 cookies
     */
    fun clear() {
        cookieString.clear()
    }

    /**
     * 从响应 headers 的 Set-Cookie 批量添加 cookies
     *
     * @param setCookieHeaders Set-Cookie header 列表
     */
    fun addAllFromSetCookieHeaders(setCookieHeaders: List<String>) {
        setCookieHeaders.forEach { addFromSetCookie(it) }
    }
}