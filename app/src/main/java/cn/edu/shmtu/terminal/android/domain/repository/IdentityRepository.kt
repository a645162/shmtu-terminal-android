package cn.edu.shmtu.terminal.android.domain.repository

import cn.edu.shmtu.terminal.android.domain.model.Identity
import kotlinx.coroutines.flow.Flow

interface IdentityRepository {
    fun getAllIdentities(): Flow<List<Identity>>
    suspend fun getIdentityById(id: Long): Identity?
    suspend fun addIdentity(name: String): Long
    suspend fun deleteIdentity(id: Long)
}
