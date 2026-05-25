package cn.edu.shmtu.terminal.android.domain.repository

import cn.edu.shmtu.terminal.android.domain.model.Identity
import kotlinx.coroutines.flow.Flow

interface IdentityRepository {
    fun getAllIdentities(): Flow<List<Identity>>
    fun getIdentityByIdFlow(id: Long): Flow<Identity?>
    suspend fun getIdentityById(id: Long): Identity?
    suspend fun addIdentity(username: String, remark: String = "", birthday: String = "", enrollmentDate: String = "", graduationDate: String = ""): Long
    suspend fun updateIdentity(identity: Identity)
    suspend fun deleteIdentity(id: Long)
}
