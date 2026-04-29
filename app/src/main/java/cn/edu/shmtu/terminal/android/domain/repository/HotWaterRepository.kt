package cn.edu.shmtu.terminal.android.domain.repository

import cn.edu.shmtu.terminal.android.domain.model.HotWaterBuilding
import kotlinx.coroutines.flow.Flow

interface HotWaterRepository {
    suspend fun fetchHotWaterData(accountId: Long): Result<List<HotWaterBuilding>>
    fun getFollowedBuildings(): Flow<List<Int>>
    suspend fun followBuilding(buildingNumber: Int)
    suspend fun unfollowBuilding(buildingNumber: Int)
}
