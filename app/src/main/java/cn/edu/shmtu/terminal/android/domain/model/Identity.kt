package cn.edu.shmtu.terminal.android.domain.model

/**
 * 身份模型
 *
 * @property id 数据库主键
 * @property username 主学号（用于命名数据库文件）
 * @property remark 备注名称（用户可自定义的显示名，如"张三"）
 * @property birthday 生日
 * @property enrollmentDate 入学日期
 * @property graduationDate 毕业日期
 * @property displayOrder 显示顺序
 * @property accountCount 关联账号数量
 */
data class Identity(
    val id: Long = 0,
    val username: String,  // 主学号，用于命名数据库文件
    val remark: String = "",  // 备注名称（原 name 字段）
    val birthday: String = "",
    val enrollmentDate: String = "",
    val graduationDate: String = "",
    val displayOrder: Int = 0,
    val accountCount: Int = 0
)
