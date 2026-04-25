package cn.edu.shmtu.terminal.android.di

import android.content.Context
import cn.edu.shmtu.terminal.android.data.local.datastore.SecureStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideSecureStorage(@ApplicationContext context: Context): SecureStorage {
        return SecureStorage(context)
    }
}
