package cn.edu.shmtu.terminal.android.data.local.db

import android.content.Context
import androidx.room.Room
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillDatabaseManager @Inject constructor(
    private val context: Context
) {
    private val accountDatabases = ConcurrentHashMap<Long, BillDatabase>()
    private val identityDatabases = ConcurrentHashMap<Long, BillDatabase>()

    fun getAccountDatabase(accountId: Long): BillDatabase =
        accountDatabases.getOrPut(accountId) {
            Room.databaseBuilder(
                context, BillDatabase::class.java,
                "account_${accountId}_bills"
            ).build()
        }

    fun getIdentityDatabase(identityId: Long): BillDatabase =
        identityDatabases.getOrPut(identityId) {
            Room.databaseBuilder(
                context, BillDatabase::class.java,
                "identity_${identityId}_bills"
            ).build()
        }

    fun closeAccountDatabase(accountId: Long) {
        accountDatabases.remove(accountId)?.close()
    }

    fun closeIdentityDatabase(identityId: Long) {
        identityDatabases.remove(identityId)?.close()
    }

    suspend fun deleteAccountDatabase(accountId: Long) {
        closeAccountDatabase(accountId)
        context.deleteDatabase("account_${accountId}_bills")
    }

    suspend fun deleteIdentityDatabase(identityId: Long) {
        closeIdentityDatabase(identityId)
        context.deleteDatabase("identity_${identityId}_bills")
    }
}
