package cn.edu.shmtu.cas.demo

import cn.edu.shmtu.cas.captcha.Captcha

fun main() {
    val userId = System.getenv("SHMTU_USER_ID")
    val password = System.getenv("SHMTU_PASSWORD")

    println("userId: $userId password: $password")

    HotWaterDemo.testHotWater()

    BillDemo.testBill(userId, password)

    CaptchaTest.testLocalTcpServerOcrMultiThread(1)
}
