package cn.edu.shmtu.terminal.android.data.repository

import cn.edu.shmtu.terminal.android.data.local.datastore.SecureStorage
import cn.edu.shmtu.terminal.android.data.local.db.dao.FollowedBuildingDao
import cn.edu.shmtu.terminal.android.data.local.db.entity.FollowedBuildingEntity
import cn.edu.shmtu.terminal.android.data.remote.WechatAuthAdapter
import cn.edu.shmtu.terminal.android.domain.model.HotWaterBuilding
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.repository.HotWaterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HotWaterRepositoryImpl @Inject constructor(
    private val wechatAuthAdapter: WechatAuthAdapter,
    private val accountRepository: AccountRepository,
    private val secureStorage: SecureStorage,
    private val followedBuildingDao: FollowedBuildingDao
) : HotWaterRepository {

    override suspend fun fetchHotWaterData(accountId: Long): Result<List<HotWaterBuilding>> {
        return try {
            val result = wechatAuthAdapter.fetchHotWater(accountId)

            if (result.isFailure) {
                return Result.failure(result.exceptionOrNull() ?: Exception("未知错误"))
            }

            val html = result.getOrNull() ?: ""
            val parsed = wechatAuthAdapter.parseHotWaterList(html)
            val followed = followedBuildingDao.getAll().first().map { it.buildingNumber }.toSet()

            val buildings = parsed.map { (temp, level, building) ->
                HotWaterBuilding(
                    buildingNumber = building,
                    temperature = temp,
                    waterLevel = level,
                    isFollowed = followed.contains(building)
                )
            }

            Result.success(buildings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getFollowedBuildings(): Flow<List<Int>> {
        return followedBuildingDao.getAll().map { list ->
            list.map { it.buildingNumber }
        }
    }

    override suspend fun followBuilding(buildingNumber: Int) {
        followedBuildingDao.insert(FollowedBuildingEntity(buildingNumber = buildingNumber))
    }

    override suspend fun unfollowBuilding(buildingNumber: Int) {
        followedBuildingDao.delete(buildingNumber)
    }
}
