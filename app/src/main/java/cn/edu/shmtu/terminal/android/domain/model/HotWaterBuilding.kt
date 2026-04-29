package cn.edu.shmtu.terminal.android.domain.model

data class HotWaterBuilding(
    val buildingNumber: Int,
    val temperature: Float,
    val waterLevel: Float,
    val isFollowed: Boolean = false
)
