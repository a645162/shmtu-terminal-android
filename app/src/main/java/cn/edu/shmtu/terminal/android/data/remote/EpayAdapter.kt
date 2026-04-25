package cn.edu.shmtu.terminal.android.data.remote

import cn.edu.shmtu.cas.auth.EpayAuth
import cn.edu.shmtu.cas.parser.BillParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpayAdapter @Inject constructor() {

    private val instances = mutableMapOf<Long, EpayAuth>()

    fun getEpayAuth(accountId: Long): EpayAuth {
        return instances.getOrPut(accountId) { EpayAuth() }
    }

    suspend fun testLoginStatus(accountId: Long): Boolean = withContext(Dispatchers.IO) {
        getEpayAuth(accountId).testLoginStatus()
    }

    suspend fun login(accountId: Long, username: String, password: String): Boolean = withContext(Dispatchers.IO) {
        getEpayAuth(accountId).login(username, password)
    }

    suspend fun fetchBillPage(accountId: Long, pageNo: Int): Triple<Int, String, String> = withContext(Dispatchers.IO) {
        getEpayAuth(accountId).getBill(pageNo = pageNo.toString())
    }

    fun parseBillList(html: String): List<Map<String, String>> {
        val parser = BillParser()
        return parser.getBillTr(html).getBillList().map { it as Map<String, String> }
    }

    fun getPageCount(html: String): Int {
        return BillParser().getPageCount(html)
    }
}
