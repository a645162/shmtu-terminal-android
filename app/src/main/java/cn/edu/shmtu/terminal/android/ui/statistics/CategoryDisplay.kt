package cn.edu.shmtu.terminal.android.ui.statistics

import androidx.compose.ui.graphics.Color

/**
 * 分类显示名 + 颜色 - 对齐 Tauri utils/translation.ts 的 CATEGORY_DISPLAY_NAMES / CATEGORY_COLORS
 *
 * 键名(deposit/electricity/bath/...)与 Rust 端完全一致,
 * 统计接口返回的分类名(BillItem.type 经 classifyBill 分类)就是这些 key。
 */
object CategoryDisplay {

    private val CATEGORY_DISPLAY_NAMES: Map<String, String> = mapOf(
        "deposit" to "充值",
        "electricity" to "电费",
        "bath" to "淋浴",
        "hot_water" to "热水",
        "cake" to "西点",
        "canteen" to "食堂",
        "library" to "图书馆",
        "hospital" to "校医院",
        "shop" to "超市",
        "laundry" to "洗衣",
        "network" to "网络",
        "transport" to "交通",
        "other" to "其他",
    )

    private val CATEGORY_COLORS: Map<String, Color> = mapOf(
        "deposit" to Color(0xFF17A34A),
        "electricity" to Color(0xFFE25555),
        "bath" to Color(0xFF2F80ED),
        "hot_water" to Color(0xFF06B6D4),
        "cake" to Color(0xFFF59E0B),
        "canteen" to Color(0xFF8B5CF6),
        "library" to Color(0xFFEAB308),
        "hospital" to Color(0xFFEF4444),
        "shop" to Color(0xFF0EA5A4),
        "laundry" to Color(0xFFD946EF),
        "network" to Color(0xFF65A30D),
        "transport" to Color(0xFFF97316),
        "other" to Color(0xFF64748B),
    )

    private val FALLBACK_COLOR = Color(0xFF8E8E8E)

    /** 显示名:key -> 中文名(找不到则返回原 key) */
    fun displayName(key: String): String = CATEGORY_DISPLAY_NAMES[key] ?: key

    /** 颜色:key -> Color(找不到则灰色) */
    fun color(key: String): Color = CATEGORY_COLORS[key] ?: FALLBACK_COLOR

    /** 给定一组 key,返回对应的 [显示名, 颜色] 配对列表(保持输入顺序,去重) */
    fun render(keys: List<String>): List<Pair<String, Color>> {
        val seen = LinkedHashSet<String>()
        val out = mutableListOf<Pair<String, Color>>()
        for (k in keys) {
            if (seen.add(k)) out.add(displayName(k) to color(k))
        }
        return out
    }
}
