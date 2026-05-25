package cn.edu.shmtu.cas.classifier

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream

/**
 * 位置信息
 * 将原始的 targetUser（如"A食堂1楼大餐厅"）翻译为具体建筑和餐厅名
 */
@Serializable
data class PositionInfo(
    val position: String,
    val room: String
)

@Serializable
private data class PositionRuleFile(
    val field: String,
    val keywords: Map<String, PositionInfo>
)

/**
 * 对方账户翻译器
 *
 * 根据 position.json 中的规则，将原始 targetUser 翻译为建筑和房间信息。
 * 例如："A食堂1楼大餐厅" → PositionInfo(position="海馨楼", room="海馨第1食堂")
 *
 * 对齐 Rust 版本的 PositionTranslator。
 */
class PositionTranslator private constructor(
    private val keywords: Map<String, PositionInfo>
) {

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * 从 position.json 字符串加载规则
         */
        fun fromJson(jsonStr: String): PositionTranslator {
            val rule = json.decodeFromString<PositionRuleFile>(jsonStr)
            return PositionTranslator(rule.keywords)
        }

        /**
         * 从 JSON 文件路径加载
         */
        fun fromFile(path: String): PositionTranslator {
            return fromJson(File(path).readText())
        }

        /**
         * 从输入流加载
         */
        fun fromInputStream(inputStream: InputStream): PositionTranslator {
            return fromJson(inputStream.bufferedReader().readText())
        }
    }

    /**
     * 翻译对方账户
     *
     * @param targetUser 原始对方账户字符串（如"A食堂1楼大餐厅"）
     * @return 翻译后的位置信息，没有匹配返回 null
     */
    fun translate(targetUser: String): PositionInfo? {
        return keywords[targetUser]
    }

    /**
     * 获取所有关键字映射
     */
    fun getAllKeywords(): Map<String, PositionInfo> = keywords
}
