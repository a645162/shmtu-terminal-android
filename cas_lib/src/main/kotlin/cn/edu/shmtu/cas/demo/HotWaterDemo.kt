package com.khm.shmtu.cas.demo

import com.khm.shmtu.cas.auth.WechatAuth

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