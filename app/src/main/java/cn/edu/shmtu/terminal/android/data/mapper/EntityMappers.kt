package cn.edu.shmtu.terminal.android.data.mapper

import cn.edu.shmtu.terminal.android.data.local.db.entity.AccountEntity
import cn.edu.shmtu.terminal.android.data.local.db.entity.BillEntity
import cn.edu.shmtu.terminal.android.data.local.db.entity.IdentityEntity
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.model.AccountType
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.model.LoginStatus

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
        status = status
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
        status = status
    )

    /**
     * 把 lib 域的 [cn.edu.shmtu.cas.datatype.BillItem] 转成 Room 实体。
     * 供 [cn.edu.shmtu.terminal.android.data.sync.RoomBillStore] 在 lib→app 边界使用。
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
}
