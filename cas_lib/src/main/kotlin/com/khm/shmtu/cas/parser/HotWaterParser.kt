package com.khm.shmtu.cas.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element


class HotWaterParser(htmlCode: String? = null) {

    private var liElementList = mutableListOf<Element>()

    init {
        if (!htmlCode.isNullOrBlank()) {
            getHotWaterUl(htmlCode)
        }
    }

    /**
     * 获取热水信息的 ul元素
     * @param htmlCode HTML代码
     * @return HotWaterParser 可以直接返回本Class对象
     */
    fun getHotWaterUl(htmlCode: String): HotWaterParser {
        val document: Document = Jsoup.parse(htmlCode)

        // 找到 ul 元素
        val ulElement: Element? =
            document.selectFirst("#tab1 > div > div > ul")

        if (ulElement == null) {
            println("ul 元素不存在")
            return this
        }

        liElementList.clear()

        for (liElement in ulElement.children()) {
            liElementList.add(liElement)
        }

        return this
    }

    private fun String.onlyDigits(): String {
        return this.replace("[^\\d]".toRegex(), "")
    }

    private fun String.onlyFloatDigits(): String {
        return this.replace("[^\\d.]".toRegex(), "")
    }

    /**
     * 获取热水信息列表
     * @return MutableList<Triple<Float, Float, Int>> 三元组(摄氏温度，水位百分比，楼号)列表
     */
    fun getHotWaterList()
            : MutableList<Triple<Float, Float, Int>> {

        val hotWaterList =
            mutableListOf<Triple<Float, Float, Int>>()

        for (liElement in liElementList) {
            val divElement =
                liElement.selectFirst("div.bagreen") ?: continue

            val childElement = divElement.children()

            if (childElement.count() != 3) {
                continue
            }

            // 分离 3 部分
            val temperature =
                childElement[0]
                    .text()
                    .replace("℃", "")
                    .onlyFloatDigits()
                    .trim()
            val stage =
                childElement[1]
                    .text()
                    .replace("水位", "")
                    .replace("%", "")
                    .onlyFloatDigits()
                    .trim()
            val building =
                childElement[2]
                    .text()
                    .onlyDigits()
                    .trim()

            try {
                val temperatureFloat = temperature.toFloat()
                val stageFloat = stage.toFloat()
                val buildingInt = building.toInt()

                hotWaterList.add(
                    Triple(
                        temperatureFloat,
                        stageFloat,
                        buildingInt
                    )
                )
            } catch (e: NumberFormatException) {
                continue
            }
        }

        return hotWaterList
    }

}
