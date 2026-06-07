package cn.edu.shmtu.terminal.android.di

import cn.edu.shmtu.terminal.android.data.p2p.P2PManager
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object P2PModule {

    // P2PManager is already @Singleton with @Inject constructor,
    // so Hilt can provide it without an explicit @Provides method.
    // This module exists as a logical grouping point for future P2P bindings.
}
