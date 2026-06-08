package cn.edu.shmtu.terminal.android.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import cn.edu.shmtu.terminal.android.data.local.db.dao.AccountDao
import cn.edu.shmtu.terminal.android.data.local.db.dao.FollowedBuildingDao
import cn.edu.shmtu.terminal.android.data.local.db.dao.IdentityDao
import cn.edu.shmtu.terminal.android.data.local.db.dao.PersonAccountDao
import cn.edu.shmtu.terminal.android.data.local.db.dao.SessionDao
import cn.edu.shmtu.terminal.android.data.local.db.entity.AccountEntity
import cn.edu.shmtu.terminal.android.data.local.db.entity.FollowedBuildingEntity
import cn.edu.shmtu.terminal.android.data.local.db.entity.IdentityEntity
import cn.edu.shmtu.terminal.android.data.local.db.entity.PersonAccountEntity
import cn.edu.shmtu.terminal.android.data.local.db.entity.SessionEntity

@Database(
    entities = [
        IdentityEntity::class,
        AccountEntity::class,
        FollowedBuildingEntity::class,
        SessionEntity::class,
        PersonAccountEntity::class,
    ],
    version = 6,
    exportSchema = false
)
abstract class MainDatabase : RoomDatabase() {
    abstract fun identityDao(): IdentityDao
    abstract fun accountDao(): AccountDao
    abstract fun followedBuildingDao(): FollowedBuildingDao
    abstract fun sessionDao(): SessionDao
    abstract fun personAccountDao(): PersonAccountDao
}
