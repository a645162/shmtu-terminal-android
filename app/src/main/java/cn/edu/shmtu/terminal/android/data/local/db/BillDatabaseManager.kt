package cn.edu.shmtu.terminal.android.data.local.db

import android.content.Context
import androidx.room.Room
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 账单数据库管理器
 *
 * 统一命名规则（对齐 Rust/C#）：
 * - 全局数据库: shmtu.terminal.sqlite
 * - 身份数据库: identity_{id}.sqlite
 * - 账号数据库: account_{studentId}.sqlite
 *
 * 数据结构：
 * - 身份数据库: 合并所有账号账单，用于跨账号统计
 * - 账号数据库: 原始账单记录
 */
@Singleton
class BillDatabaseManager @Inject constructor(
    private val context: Context
) {
    // 缓存已打开的数据库连接
    private val accountDatabases = ConcurrentHashMap<String, BillDatabase>()  // key: studentId
    private val identityDatabases = ConcurrentHashMap<Long, BillDatabase>()  // key: identityId

    /**
     * 对外暴露的 Application Context 访问器(只读),
     * 供 BillRepositoryImpl 在懒加载账单规则 TOML 时使用。
     */
    @Suppress("unused")
    val appContext: Context get() = context.applicationContext

    /**
     * 获取账号原始数据库
     * 文件名: account_{studentId}.sqlite
     */
    fun getAccountDatabase(studentId: String): BillDatabase =
        accountDatabases.getOrPut(studentId) {
            Room.databaseBuilder(
                context, BillDatabase::class.java,
                "account_${studentId}.sqlite"
            )
                // BillEntity 字段调整后, 旧库 schema hash 不再匹配;
                // 启用 destructive migration 让旧库自动重建, 用户重新同步账单即可。
                .fallbackToDestructiveMigration(false)
                .build()
        }

    /**
     * 获取身份合并数据库
     * 文件名: identity_{identityId}.sqlite
     */
    fun getIdentityDatabase(identityId: Long): BillDatabase =
        identityDatabases.getOrPut(identityId) {
            Room.databaseBuilder(
                context, BillDatabase::class.java,
                "identity_${identityId}.sqlite"
            )
                .fallbackToDestructiveMigration(false)
                .build()
        }

    fun closeAccountDatabase(studentId: String) {
        accountDatabases.remove(studentId)?.close()
    }

    fun closeIdentityDatabase(identityId: Long) {
        identityDatabases.remove(identityId)?.close()
    }

    suspend fun deleteAccountDatabase(studentId: String) {
        closeAccountDatabase(studentId)
        context.deleteDatabase("account_${studentId}.sqlite")
    }

    suspend fun deleteIdentityDatabase(identityId: Long) {
        closeIdentityDatabase(identityId)
        context.deleteDatabase("identity_${identityId}.sqlite")
    }
}
