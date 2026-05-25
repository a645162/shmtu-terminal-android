package cn.edu.shmtu.terminal.android.di

import cn.edu.shmtu.terminal.android.data.repository.AccountRepositoryImpl
import cn.edu.shmtu.terminal.android.data.repository.BillRepositoryImpl
import cn.edu.shmtu.terminal.android.data.repository.HotWaterRepositoryImpl
import cn.edu.shmtu.terminal.android.data.repository.IdentityRepositoryImpl
import cn.edu.shmtu.terminal.android.data.repository.SessionRepositoryImpl
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.repository.BillRepository
import cn.edu.shmtu.terminal.android.domain.repository.HotWaterRepository
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import cn.edu.shmtu.terminal.android.domain.repository.SessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindIdentityRepository(impl: IdentityRepositoryImpl): IdentityRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindBillRepository(impl: BillRepositoryImpl): BillRepository

    @Binds
    @Singleton
    abstract fun bindHotWaterRepository(impl: HotWaterRepositoryImpl): HotWaterRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository
}
