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
        name = name,
        birthday = birthday,
        enrollmentDate = enrollmentDate,
        graduationDate = graduationDate,
        displayOrder = displayOrder,
        accountCount = accountCount
    )

    fun Identity.toEntity(): IdentityEntity = IdentityEntity(
        id = id,
        name = name,
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
}
