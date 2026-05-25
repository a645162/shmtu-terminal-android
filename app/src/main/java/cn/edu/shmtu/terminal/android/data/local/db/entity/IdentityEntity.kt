package cn.edu.shmtu.terminal.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 身份实体
 *
 * @property id 数据库主键
 * @property username 主学号（唯一，用于命名数据库文件）
 * @property remark 备注名称
 * @property birthday 生日
 * @property enrollmentDate 入学日期
 * @property graduationDate 毕业日期
 * @property displayOrder 显示顺序
 * @property createdAt 创建时间
 */
@Entity(
    tableName = "identities",
    indices = [Index(value = ["username"], unique = true)]
)
data class IdentityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,  // 主学号，唯一
    val remark: String = "",
    val birthday: String = "",
    val enrollmentDate: String = "",
    val graduationDate: String = "",
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
