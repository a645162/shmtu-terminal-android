package com.khm.shmtu.cas.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class BillParser {

    private var htmlCode = ""
    private var trElementList = mutableListOf<Element>()

    init {
        if (htmlCode.isNotBlank()) {
            getBillTr(htmlCode)
        }
    }

    /**
     * 获取热水信息的 ul元素
     * @param htmlCode HTML代码
     * @return HotWaterParser 可以直接返回本Class对象
     */
    fun getBillTr(htmlCode: String): BillParser {
        val document: Document = Jsoup.parse(htmlCode)

        // 找到 ul 元素
        val tbodyElement: Element? =
            document.selectFirst("#aazone\\.zone_show_box_1 > table > tbody")

        if (tbodyElement == null) {
            println("tbody 元素不存在")
            return this
        }

        trElementList.clear()

        for (trElement in tbodyElement.children()) {
            trElementList.add(trElement)
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
    fun getBillList()
            : MutableList<HashMap<String, String>> {

        val billList =
            mutableListOf<HashMap<String, String>>()

        for (trElement in trElementList) {

            val childElement = trElement.children()

            if (childElement.count() != 7) {
                continue
            }

            val dateStr =
                childElement[0].children()[0].text()
                    .trim()
            val timeStr =
                childElement[0].children()[1].text()
                    .trim()

            val type =
                childElement[1].children()[0].text()
                    .trim()
            val number =
                childElement[1].children()[1].text()
                    .replace("交易号：", "")
                    .onlyDigits()
                    .trim()

            val targetUser =
                childElement[2].text()
                    .trim()
            val money =
                childElement[3].text()
                    .onlyFloatDigits()
                    .trim()
            val method =
                childElement[4].text()
                    .trim()
            val status =
                childElement[5].text()
                    .trim()

            val timeStrFormat =
                timeStr.replace(Regex("(\\d{2})(\\d{2})(\\d{2})"), "$1:$2:$3")

            val dateTimeStrFormat = "$dateStr $timeStrFormat"

            val currentItem =
                HashMap<String, String>().apply {
                    put("dateStr", dateStr)
                    put("timeStr", timeStr)
                    put("timeStrFormat", timeStrFormat)
                    put("dateTimeStrFormat", dateTimeStrFormat)
                    put("type", type)
                    put("number", number)
                    put("targetUser", targetUser)
                    put("money", money)
                    put("method", method)
                    put("status", status)
                }

            billList.add(currentItem)
        }

        return billList
    }

    fun getPageCount(htmlCode: String): Int {
        val document: Document = Jsoup.parse(htmlCode)
        val pageElement: Element =
            document.selectFirst("#aazone\\.zone_show_box_1 > div > table > tbody") ?: return -1

        var pageStr = pageElement.text()
        if (
            !pageStr.contains("/") ||
            !pageStr.contains("首页")
        ) {
            return -1
        }

        pageStr =
            pageStr.substring(
                pageStr.indexOf("/") + 1,
                pageStr.indexOf("首页")
            )
                .replace("页", "")
                .trim()

        return if (pageStr.isNotBlank()) {
            pageStr.toInt()
        } else {
            -1
        }
    }

}
