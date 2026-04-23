package cn.edu.shmtu.cas.demo

import cn.edu.shmtu.cas.auth.EpayAuth

class BillDemo {

    companion object {

        fun testBill(userId: String, password: String) {
            val epayAuth = EpayAuth()
            val isSuccess =
                epayAuth.login(userId, password)
            println(isSuccess)
            if (!isSuccess) {
                println("Login failed!")
                return
            }
            val billResult =
                epayAuth.getBill(pageNo = "1")
            println(billResult.first)
            println(billResult.second)
        }

    }

}