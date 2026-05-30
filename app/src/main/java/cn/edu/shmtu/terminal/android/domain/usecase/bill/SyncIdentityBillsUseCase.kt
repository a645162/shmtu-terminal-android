package cn.edu.shmtu.terminal.android.domain.usecase.bill

import cn.edu.shmtu.terminal.android.domain.model.SyncProgress
import cn.edu.shmtu.terminal.android.domain.model.SyncResult
import cn.edu.shmtu.terminal.android.domain.model.SyncStatus
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 增量同步身份下所有账号 - 支持细粒度进度回调
 * 对齐 Rust 版 incremental_sync
 */
class SyncIdentityBillsUseCase @Inject constructor(
    private val syncAccountBillsUseCase: SyncAccountBillsUseCase,
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(identityId: Long): SyncResult {
        return invoke(identityId) {}
    }

    /**
     * 带进度的增量同步
     *
     * 对齐 Rust 版的多账号进度：
     * - 每个账号同步时发送 accountIndex/accountTotal
     * - 包含每页进度
     */
    suspend operator fun invoke(identityId: Long, onProgress: (SyncProgress) -> Unit): SyncResult {
        val accountList = accountRepository.getAccountsByIdentity(identityId).first()
        val total = accountList.size
        var totalNew = 0
        var hasError = false
        var errorMsg: String? = null

        accountList.forEachIndexed { index, account ->
            val result = syncAccountBillsUseCase(account) { progress ->
                // 包装为身份级进度，附加账号索引信息
                onProgress(progress.copy(
                    accountIndex = index,
                    accountTotal = total
                ))
            }
            totalNew += result.newCount
            if (!result.success) {
                hasError = true
                errorMsg = result.errorMessage
            }
        }

        return SyncResult(totalNew, !hasError, errorMsg)
    }
}
