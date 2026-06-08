package cn.edu.shmtu.terminal.android.di

import android.content.Context
import androidx.room.Room
import cn.edu.shmtu.terminal.android.data.local.db.BillDatabaseManager
import cn.edu.shmtu.terminal.android.data.local.db.MainDatabase
import cn.edu.shmtu.terminal.android.data.local.db.dao.AccountDao
import cn.edu.shmtu.terminal.android.data.local.db.dao.FollowedBuildingDao
import cn.edu.shmtu.terminal.android.data.local.db.dao.IdentityDao
import cn.edu.shmtu.terminal.android.data.local.db.dao.PersonAccountDao
import cn.edu.shmtu.terminal.android.data.local.db.dao.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMainDatabase(@ApplicationContext context: Context): MainDatabase {
        return Room.databaseBuilder(
            context,
            MainDatabase::class.java,
            "shmtu_terminal"
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideIdentityDao(database: MainDatabase): IdentityDao {
        return database.identityDao()
    }

    @Provides
    fun provideAccountDao(database: MainDatabase): AccountDao {
        return database.accountDao()
    }

    @Provides
    fun provideFollowedBuildingDao(database: MainDatabase): FollowedBuildingDao {
        return database.followedBuildingDao()
    }

    @Provides
    fun provideSessionDao(database: MainDatabase): SessionDao {
        return database.sessionDao()
    }

    @Provides
    fun providePersonAccountDao(database: MainDatabase): PersonAccountDao {
        return database.personAccountDao()
    }

    @Provides
    @Singleton
    fun provideBillDatabaseManager(@ApplicationContext context: Context): BillDatabaseManager {
        return BillDatabaseManager(context)
    }
}
