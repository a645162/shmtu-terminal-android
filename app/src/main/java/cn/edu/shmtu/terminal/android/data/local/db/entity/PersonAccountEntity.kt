package cn.edu.shmtu.terminal.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一卡通个人账户详情实体
 *
 * 每个账号(account)对应一行,记录 [cn.edu.shmtu.cas.parser.PersonAccountInfo] 解析结果。
 * 用于在没网 / 未登录时给 UI 提供缓存数据,避免每次都要重新登录拉取。
 */
@Entity(
    tableName = "person_accounts",
    indices = [Index(value = ["accountId"], unique = true)]
)
data class PersonAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 关联的 [AccountEntity.id] */
    val accountId: Long,

    // 头部
    val realName: String = "",
    val realNameAuthStatus: String = "",

    // 资金&安全信息
    val cashBalance: Double = 0.0,
    val cashBalanceRaw: String = "",
    val securityQuestionStatus: String = "",
    val registerDate: String = "",

    // 基本信息
    val studentId: String = "",
    val email: String = "",
    val nickname: String = "",
    val gender: String = "",
    val className: String = "",
    val mobile: String = "",
    val fixedLine: String = "",
    val idType: String = "",
    val idNumber: String = "",
    val remark: String = "",
    val userType: String = "",

    /** 最近一次拉取/缓存的时间戳 (millis) */
    val updatedAt: Long = System.currentTimeMillis()
)
