package cn.edu.shmtu.terminal.android.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import cn.edu.shmtu.terminal.android.data.local.db.dao.BillDao
import cn.edu.shmtu.terminal.android.data.local.db.entity.BillEntity

@Database(entities = [BillEntity::class], version = 1, exportSchema = false)
abstract class BillDatabase : RoomDatabase() {
    abstract fun billDao(): BillDao
}
