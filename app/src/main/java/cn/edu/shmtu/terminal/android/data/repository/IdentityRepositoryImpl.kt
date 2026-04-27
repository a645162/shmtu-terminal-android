package cn.edu.shmtu.terminal.android.data.repository

import cn.edu.shmtu.terminal.android.data.local.db.dao.AccountDao
import cn.edu.shmtu.terminal.android.data.local.db.dao.IdentityDao
import cn.edu.shmtu.terminal.android.data.local.db.entity.IdentityEntity
import cn.edu.shmtu.terminal.android.data.mapper.EntityMappers
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IdentityRepositoryImpl @Inject constructor(
    private val identityDao: IdentityDao,
    private val accountDao: AccountDao
) : IdentityRepository {

    override fun getAllIdentities(): Flow<List<Identity>> {
        return identityDao.getAllIdentities().combine(
            accountDao.getAccountCounts()
        ) { identities, counts ->
            val countMap = counts.associate { it.identityId to it.count }
            identities.map { it.toDomain(countMap[it.id] ?: 0) }
        }
    }

    override suspend fun getIdentityById(id: Long): Identity? {
        return identityDao.getById(id)?.toDomain()
    }

    override suspend fun addIdentity(name: String, birthday: String, enrollmentDate: String, graduationDate: String): Long {
        return identityDao.insert(IdentityEntity(name = name, birthday = birthday, enrollmentDate = enrollmentDate, graduationDate = graduationDate))
    }

    override suspend fun updateIdentity(id: Long, name: String, birthday: String, enrollmentDate: String, graduationDate: String) {
        identityDao.updateIdentity(id, name, birthday, enrollmentDate, graduationDate)
    }

    override suspend fun deleteIdentity(id: Long) {
        identityDao.deleteById(id)
    }
}

private fun IdentityEntity.toDomain(accountCount: Int = 0) = EntityMappers.run { this@toDomain.toDomain(accountCount) }
