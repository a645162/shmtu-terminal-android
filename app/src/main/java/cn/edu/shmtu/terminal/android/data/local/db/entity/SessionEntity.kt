package cn.edu.shmtu.terminal.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Session 实体 - 存储账号的登录会话（cookies），加密存储
 *
 * 对齐 Rust 版本的 SessionInfo
 * 存储位置：MainDatabase (主数据库)
 * 表名：session_info
 */
@Entity(
    tableName = "session_info",
    indices = [Index(value = ["accountId"], unique = true)]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 学号（唯一） */
    val accountId: String,
    /** Cookies 数据（加密存储） */
    val cookies: String,
    /** 登录时间（ISO 8601） */
    val loginTime: String? = null,
    /** 预估过期时间（ISO 8601） */
    val expireTime: String? = null,
    /** 是否仍有效 */
    val isValid: Boolean = true
)
