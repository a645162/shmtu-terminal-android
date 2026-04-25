package cn.edu.shmtu.cas.demo

import cn.edu.shmtu.cas.auth.WechatAuth

class HotWaterDemo {

    companion object {

        fun testHotWater() {
            val wechatAuth = WechatAuth()
            wechatAuth.login("", "")
            val hotWaterResult =
                wechatAuth.getHotWater()
            println(hotWaterResult.first)
            println(hotWaterResult.second)
        }

    }

}
