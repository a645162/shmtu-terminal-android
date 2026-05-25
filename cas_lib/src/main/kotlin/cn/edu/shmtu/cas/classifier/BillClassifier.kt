package cn.edu.shmtu.cas.classifier

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalTime

/**
 * 账单类型规则
 * 匹配规则可以是 name（账单类型名）或 target（对方账户名）
 */
@Serializable
data class TypeRule(
    val name: List<String>? = null,
    val target: List<String>? = null
)

/**
 * 用餐时段
 */
@Serializable
data class MealPeriod(
    val name: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String
)

/**
 * 规则有效期
 */
@Serializable
data class ScheduleDate(
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String
)

/**
 * 时间表规则
 */
@Serializable
data class ScheduleRule(
    @SerialName("valid_date") val validDate: ScheduleDate,
    val timetable: Map<String, MealPeriod>
)

/**
 * 分类结果
 */
data class ClassificationResult(
    val typeLabel: String,
    val building: String,
    val room: String,
    val meal: String
)

/**
 * 账单分类器
 *
 * 根据 type.json 规则对账单进行分类（消费类型），
 * 根据 position.json 翻译位置，
 * 根据 schedule.json 判断用餐时段。
 *
 * 分类优先级：
 * 1. 先按 name（账单类型名）匹配
 * 2. 再按 target（对方账户名）匹配
 * 3. 均不匹配则归为 "other"
 *
 * 对齐 Rust 版本的 BillClassifier。
 *
 * @param typeRules 类型规则映射（分类名 → 规则）
 * @param positionTranslator 位置翻译器
 * @param schedules 用餐时间表
 */
class BillClassifier(
    private val typeRules: Map<String, TypeRule>,
    private val positionTranslator: PositionTranslator,
    private val schedules: List<ScheduleRule> = emptyList()
) {

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * 从 JSON 字符串加载所有规则
         *
         * 支持运行时动态加载：
         * ```kotlin
         * val classifier = BillClassifier.fromJson(typeJson, positionJson, scheduleJson)
         * ```
         */
        fun fromJson(typeJson: String, positionJson: String, scheduleJson: String): BillClassifier {
            val typeRules = json.decodeFromString<Map<String, TypeRule>>(typeJson)
            val translator = PositionTranslator.fromJson(positionJson)
            val schedules = json.decodeFromString<List<ScheduleRule>>(scheduleJson)
            return BillClassifier(typeRules, translator, schedules)
        }

        /**
         * 从 JSON 文件路径加载
         */
        fun fromFiles(typePath: String, positionPath: String, schedulePath: String): BillClassifier {
            return fromJson(
                File(typePath).readText(),
                File(positionPath).readText(),
                File(schedulePath).readText()
            )
        }
    }

    /**
     * 分类一笔账单
     *
     * @param itemType 账单类型（如"消费"、"中行云充值"等）
     * @param targetUser 对方账户（如"A食堂1楼大餐厅"）
     * @param dateTimeStr 日期时间字符串（格式：yyyy-MM-dd HH:mm:ss），用于判断用餐时段
     * @return ClassificationResult 包含分类标签、建筑、房间、用餐时段
     */
    fun classify(itemType: String, targetUser: String, dateTimeStr: String? = null): ClassificationResult {
        val typeLabel = classifyType(itemType, targetUser)
        val positionInfo = positionTranslator.translate(targetUser)
        val meal = classifyMeal(dateTimeStr)
        return ClassificationResult(
            typeLabel = typeLabel,
            building = positionInfo?.position ?: "",
            room = positionInfo?.room ?: "",
            meal = meal
        )
    }

    /**
     * 获取位置翻译器
     */
    fun getPositionTranslator(): PositionTranslator = positionTranslator

    // ========== 私有方法 ==========

    /**
     * 分类消费类型
     * 先匹配 name（账单类型名），再匹配 target（对方账户名）
     */
    private fun classifyType(itemType: String, targetUser: String): String {
        for ((category, rule) in typeRules) {
            // 先按 name 匹配
            rule.name?.forEach { name ->
                if (itemType.contains(name)) {
                    return category
                }
            }
            // 再按 target 匹配
            rule.target?.forEach { target ->
                if (targetUser.contains(target)) {
                    return category
                }
            }
        }
        return "other"
    }

    /**
     * 判断用餐时段
     *
     * @param dateTimeStr 格式：yyyy-MM-dd HH:mm:ss
     * @return 用餐时段名称（如"早餐"、"午餐"、"晚餐"、"夜宵"），无法判断返回空字符串
     */
    private fun classifyMeal(dateTimeStr: String?): String {
        if (dateTimeStr.isNullOrBlank() || dateTimeStr.length < 19) return ""

        val timePart = dateTimeStr.substring(11, 19)
        val datePart = dateTimeStr.substring(0, 10)

        val time = try {
            LocalTime.parse(timePart)
        } catch (e: Exception) {
            return ""
        }

        val normalizedDate = normalizeDate(datePart)

        for (schedule in schedules) {
            if (!isDateValid(schedule, normalizedDate)) continue
            for ((_, period) in schedule.timetable) {
                val start = parseTime(period.startTime)
                val end = parseTime(period.endTime)
                if (start != null && end != null) {
                    if (!time.isBefore(start) && time.isBefore(end)) {
                        return period.name
                    }
                }
            }
        }
        return ""
    }

    /**
     * 解析时间字符串（支持 "H:mm" 和 "HH:mm:ss" 格式）
     */
    private fun parseTime(timeStr: String): LocalTime? {
        return try {
            val parts = timeStr.split(":")
            when (parts.size) {
                2 -> LocalTime.of(parts[0].toInt(), parts[1].toInt())
                3 -> LocalTime.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 检查日期是否在规则有效期内
     */
    private fun isDateValid(schedule: ScheduleRule, dateStr: String): Boolean {
        if (schedule.validDate.endDate == "now") return true
        val normalizedStart = normalizeDate(schedule.validDate.startDate)
        val normalizedEnd = normalizeDate(schedule.validDate.endDate)
        return dateStr >= normalizedStart && dateStr <= normalizedEnd
    }

    /**
     * 规范化日期格式
     * 将 "2019.9.1" 转换为 "2019-09-01"
     */
    private fun normalizeDate(dateStr: String): String {
        return try {
            if (dateStr.contains(".")) {
                val parts = dateStr.split(".")
                String.format("%04d-%02d-%02d", parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            } else {
                dateStr
            }
        } catch (e: Exception) {
            dateStr
        }
    }
}
