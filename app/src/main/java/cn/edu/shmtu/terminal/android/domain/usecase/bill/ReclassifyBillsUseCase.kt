package cn.edu.shmtu.terminal.android.domain.usecase.bill

import cn.edu.shmtu.terminal.android.domain.repository.BillRepository
import cn.edu.shmtu.terminal.android.domain.repository.ReclassifyProgress
import cn.edu.shmtu.terminal.android.domain.repository.ReclassifyResult
import javax.inject.Inject

/**
 * 包装 [BillRepository.reclassifyAllBills],把"重算历史账单"作为独立 use case 暴露给 UI。
 *
 * 用途: 之前 [cn.edu.shmtu.terminal.android.data.remote.EpayAdapter.positionTranslator]
 * 加载顺序错误(rules.toml 优先于 position.toml),老数据里"海馨第一/二/三/四食堂"等
 * 仅出现在 position.toml 的规则被静默丢失。该 use case 用修复后的 classifier +
 * positionTranslator 把数据库里所有账单重算并写回,不需要重新走 CAS 登录。
 */
class ReclassifyBillsUseCase @Inject constructor(
    private val billRepository: BillRepository,
) {
    suspend operator fun invoke(
        onProgress: (ReclassifyProgress) -> Unit = {},
    ): ReclassifyResult = billRepository.reclassifyAllBills(onProgress)
}
