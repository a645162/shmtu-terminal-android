package cn.edu.shmtu.cas

import cn.edu.shmtu.cas.demo.HotWaterDemo
import cn.edu.shmtu.cas.demo.BillDemo
import cn.edu.shmtu.cas.captcha.Captcha

fun main() {
    // Get From Environment
    val userId = System.getenv("SHMTU_USER_ID")
    val password = System.getenv("SHMTU_PASSWORD")

    println("userId: $userId password: $password")

    // 热水数据获取测试
    HotWaterDemo.testHotWater()

    // 账单数据获取测试
    BillDemo.testBill(userId, password)

    // 验证码识别测试
    Captcha.testLocalTcpServerOcrMultiThread(1)
}
