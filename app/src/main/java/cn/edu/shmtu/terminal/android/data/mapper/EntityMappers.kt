package cn.edu.shmtu.terminal.android.data.mapper

import cn.edu.shmtu.cas.parser.PersonAccountInfo
import cn.edu.shmtu.terminal.android.data.local.db.entity.AccountEntity
import cn.edu.shmtu.terminal.android.data.local.db.entity.BillEntity
import cn.edu.shmtu.terminal.android.data.local.db.entity.IdentityEntity
import cn.edu.shmtu.terminal.android.data.local.db.entity.PersonAccountEntity
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.model.AccountType
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.model.LoginStatus
import cn.edu.shmtu.terminal.android.domain.model.PersonAccount

/**
 * 推断身份证号性别: 第 17 位数字 奇数=男性, 偶数=女性, 不足或非数字返回空字符串.
 */
internal fun guessGenderFromIdNumber(idNumber: String): String {
    if (idNumber.length < 17) return ""
    val ch = idNumber[16]
    if (!ch.isDigit()) return ""
    val digit = ch.digitToInt()
    return if (digit % 2 == 1) "男性" else "女性"
}

object EntityMappers {

    fun IdentityEntity.toDomain(accountCount: Int = 0): Identity = Identity(
        id = id,
        username = username,
        remark = remark,
        birthday = birthday,
        enrollmentDate = enrollmentDate,
        graduationDate = graduationDate,
        displayOrder = displayOrder,
        accountCount = accountCount
    )

    fun Identity.toEntity(): IdentityEntity = IdentityEntity(
        id = id,
        username = username,
        remark = remark,
        birthday = birthday,
        enrollmentDate = enrollmentDate,
        graduationDate = graduationDate,
        displayOrder = displayOrder
    )

    fun AccountEntity.toDomain(): Account = Account(
        id = id,
        identityId = identityId,
        label = label,
        userId = userId,
        accountType = AccountType.valueOf(accountType),
        loginStatus = LoginStatus.valueOf(loginStatus),
        lastSyncTime = lastSyncTime
    )

    fun Account.toEntity(): AccountEntity = AccountEntity(
        id = id,
        identityId = identityId,
        label = label,
        userId = userId,
        accountType = accountType.name,
        loginStatus = loginStatus.name,
        lastSyncTime = lastSyncTime
    )

    fun BillEntity.toDomain(): BillItem = BillItem(
        id = id,
        accountId = accountId,
        accountLabel = accountLabel,
        dateTimeStrFormat = dateTimeStrFormat,
        type = type,
        transactionNo = transactionNo,
        targetUser = targetUser,
        money = money,
        method = method,
        status = status,
        // 落库时已经计算并写入,直接带过去
        position = position ?: building,
        room = room,
        category = category,
        building = building,
        mergedTransactionNos = mergedTransactionNos,
        mergedDateTimes = mergedDateTimes,
        isMerged = isMerged,
        mergedBillCount = mergedBillCount
    )

    fun BillItem.toEntity(): BillEntity = BillEntity(
        id = id,
        accountId = accountId,
        accountLabel = accountLabel,
        dateStr = "",
        timeStr = "",
        dateTimeStrFormat = dateTimeStrFormat,
        type = type,
        transactionNo = transactionNo,
        targetUser = targetUser,
        money = money,
        method = method,
        status = status,
        position = position,
        room = room,
        category = category,
        building = building,
        mergedTransactionNos = mergedTransactionNos,
        mergedDateTimes = mergedDateTimes,
        isMerged = isMerged,
        mergedBillCount = mergedBillCount
    )

    /**
     * 把 lib 域的 [cn.edu.shmtu.cas.datatype.BillItem] 转成 Room 实体。
     * 供 [cn.edu.shmtu.terminal.android.data.sync.RoomBillStore] 在 lib→app 边界使用。
     *
     * 注意: 该函数仅做字段搬运,**不再计算** category/building/room —
     * 分类与位置翻译由 [RoomBillStore] 在拿到整个 batch 后统一跑一次,
     * 保证 (type, targetUser) → (category, position, room, building) 走 cas_lib 加载的 TOML 规则,
     * 与 Tauri Rust 端 `BillClassifier.classify` + `PositionTranslator.translate` 完全一致。
     */
    fun cn.edu.shmtu.cas.datatype.BillItem.toEntity(accountId: Long, accountLabel: String): BillEntity = BillEntity(
        id = 0,                              // 由 Room autoGenerate
        accountId = accountId,
        accountLabel = accountLabel,
        dateStr = dateStr,
        timeStr = timeStr,
        dateTimeStrFormat = dateTimeFormat,
        type = billType,
        transactionNo = transactionNo,
        targetUser = targetUser,
        money = amount,
        method = paymentMethod,
        status = status.name,
    )

    fun PersonAccountEntity.toDomain(): PersonAccount = PersonAccount(
        realName = realName,
        realNameAuthStatus = realNameAuthStatus,
        cashBalance = cashBalance,
        cashBalanceRaw = cashBalanceRaw,
        securityQuestionStatus = securityQuestionStatus,
        registerDate = registerDate,
        studentId = studentId,
        email = email,
        nickname = nickname,
        gender = gender,
        className = className,
        phoneNum = phoneNum,
        genderFromId = genderFromId,
        idType = idType,
        idNumber = idNumber,
        remark = remark,
        userType = userType,
        updatedAt = updatedAt,
    )

    fun PersonAccountInfo.toEntity(accountId: Long, now: Long = System.currentTimeMillis()): PersonAccountEntity = PersonAccountEntity(
        accountId = accountId,
        realName = realName,
        realNameAuthStatus = realNameAuthStatus,
        cashBalance = cashBalance,
        cashBalanceRaw = cashBalanceRaw,
        securityQuestionStatus = securityQuestionStatus,
        registerDate = registerDate,
        studentId = studentId,
        email = email,
        nickname = nickname,
        gender = gender,
        className = className,
        phoneNum = phoneNum,
        // cas_lib 的 PersonAccountInfo 没有 genderFromId 字段, 这里从 idNumber 推断
        genderFromId = guessGenderFromIdNumber(idNumber),
        idType = idType,
        idNumber = idNumber,
        remark = remark,
        userType = userType,
        updatedAt = now,
    )
}
