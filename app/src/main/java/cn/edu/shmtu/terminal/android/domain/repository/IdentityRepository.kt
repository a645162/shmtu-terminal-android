package cn.edu.shmtu.terminal.android.domain.repository

import cn.edu.shmtu.terminal.android.domain.model.Identity
import kotlinx.coroutines.flow.Flow

interface IdentityRepository {
    fun getAllIdentities(): Flow<List<Identity>>
    suspend fun getIdentityById(id: Long): Identity?
    suspend fun addIdentity(name: String, birthday: String = "", enrollmentDate: String = "", graduationDate: String = ""): Long
    suspend fun updateIdentity(id: Long, name: String, birthday: String, enrollmentDate: String, graduationDate: String)
    suspend fun deleteIdentity(id: Long)
}
