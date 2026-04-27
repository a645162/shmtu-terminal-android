package cn.edu.shmtu.terminal.android.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import cn.edu.shmtu.terminal.android.data.local.db.dao.AccountDao
import cn.edu.shmtu.terminal.android.data.local.db.dao.IdentityDao
import cn.edu.shmtu.terminal.android.data.local.db.entity.AccountEntity
import cn.edu.shmtu.terminal.android.data.local.db.entity.IdentityEntity

@Database(
    entities = [IdentityEntity::class, AccountEntity::class],
    version = 3,
    exportSchema = false
)
abstract class MainDatabase : RoomDatabase() {
    abstract fun identityDao(): IdentityDao
    abstract fun accountDao(): AccountDao
}
