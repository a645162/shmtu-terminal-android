package cn.edu.shmtu.terminal.android.domain.usecase.bill

import android.util.Base64
import cn.edu.shmtu.cas.auth.EpayAuth
import cn.edu.shmtu.cas.captcha.Captcha
import cn.edu.shmtu.cas.captcha.CaptchaAnswer
import cn.edu.shmtu.cas.captcha.CaptchaAnswerKind
import cn.edu.shmtu.cas.captcha.CaptchaResolver
import cn.edu.shmtu.cas.session.LoginSubmitResult
import cn.edu.shmtu.cas.session.ManualCaptchaRequiredException
import cn.edu.shmtu.cas.sync.AccountContext
import cn.edu.shmtu.cas.sync.SyncOptions
import cn.edu.shmtu.cas.sync.SyncProgress as LibSyncProgress
import cn.edu.shmtu.cas.sync.SyncRangePreset
import cn.edu.shmtu.cas.sync.SyncStatus as LibSyncStatus
import cn.edu.shmtu.cas.sync.incrementalSync
import cn.edu.shmtu.cas.sync.syncAccount
import cn.edu.shmtu.terminal.android.data.local.datastore.CaptchaMode
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import cn.edu.shmtu.terminal.android.data.remote.EpayAdapter
import cn.edu.shmtu.terminal.android.data.sync.RoomBillStore
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.model.SyncProgress
import cn.edu.shmtu.terminal.android.domain.model.SyncResult
import cn.edu.shmtu.terminal.android.domain.model.SyncStatus
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * 增量同步单账号账单 - 包装 lib [syncAccount]
 *
 * 行为对齐之前的 [SyncAccountBillsUseCase]：
 * - 手动模式抛 [CaptchaRequiredException] 给 UI
 * - 远端 OCR 模式自动重试 3 次（由 lib [EpayAuth] 内部循环保证）
 * - 同步后更新 lastSyncTime + loginStatus
 *
 * 进度回调：[LibSyncProgress] → [SyncProgress]（app 域）
 */
class SyncAccountBillsUseCase @Inject constructor(
    private val epayAdapter: EpayAdapter,
    private val accountRepository: AccountRepository,
    private val settingsDataStore: SettingsDataStore,
) {
    suspend operator fun invoke(account: Account): SyncResult = invoke(account) {}

    suspend operator fun invoke(account: Account, onProgress: (SyncProgress) -> Unit): SyncResult {
        val auth = epayAdapter.getEpayAuth(account.id)
        val store = RoomBillStore(
            billDbManager = epayAdapter.billDbManager,
            accountId = account.id,
            studentId = account.userId,
            identityId = account.identityId,
        )

        val captchaMode = runBlocking { settingsDataStore.captchaMode.first() }
        val resolver: CaptchaResolver? = when (captchaMode) {
            CaptchaMode.MANUAL -> null
            CaptchaMode.AUTO_OCR -> autoOcrResolver()
        }

        val translatedOnProgress: (LibSyncProgress) -> Unit = { p ->
            onProgress(p.toDomain())
        }

        return try {
            syncAccount(
                auth = auth,
                store = RoomBillStore(
                    billDbManager = epayAdapter.billDbManager,
                    accountId = account.id,
                    studentId = account.userId,
                    identityId = account.identityId,
                ),
                context = AccountContext(
                    accountId = account.id.toString(),
                    accountLabel = account.label,
                ),
                resolver = resolver,
                range = null,  // 增量
                onProgress = translatedOnProgress,
            )
            accountRepository.updateLastSyncTime(account.id)
            accountRepository.updateLoginStatus(account.id, "LOGGED_IN")
            SyncResult(newCount = 0, success = true)
        } catch (e: ManualCaptchaRequiredException) {
            val imgB64 = e.captchaImageBase64.ifBlank {
                Base64.encodeToString(e.captchaImageBytes, Base64.NO_WRAP)
            }
            onProgress(SyncProgress(status = SyncStatus.GettingCaptcha, accountLabel = account.label))
            throw CaptchaRequiredException(imgB64, e.execution, account.id, account.label)
        } catch (e: Exception) {
            onProgress(SyncProgress(status = SyncStatus.Failed(e.message ?: e.javaClass.simpleName), accountLabel = account.label))
            SyncResult(0, false, e.message)
        }
    }

    /** 使用验证码完成登录后继续同步 - 对齐 Rust 版 sync_with_captcha */
    suspend fun syncWithCaptcha(account: Account, captchaCode: String, execution: String, onProgress: (SyncProgress) -> Unit): SyncResult {
        val auth = epayAdapter.getEpayAuth(account.id)
        return try {
            onProgress(SyncProgress(status = SyncStatus.LoggingIn, accountLabel = account.label))
            val submitResult = auth.submitLogin("", "", captchaCode, execution)
            when (val r = submitResult.getOrNull()) {
                is LoginSubmitResult.Success -> {
                    epayAdapter.saveSessionCookies(account.id, auth)
                    val store = RoomBillStore(
                        billDbManager = epayAdapter.billDbManager,
                        accountId = account.id,
                        studentId = account.userId,
                        identityId = account.identityId,
                    )
                    val libResult = incrementalSync(
                        auth = auth,
                        store = store,
                        options = SyncOptions.incremental(SyncRangePreset.Month),
                        onProgress = { p -> onProgress(p.toDomain()) },
                    )
                    accountRepository.updateLastSyncTime(account.id)
                    accountRepository.updateLoginStatus(account.id, "LOGGED_IN")
                    SyncResult(
                        newCount = libResult.getOrNull()?.newCount ?: 0,
                        success = libResult.isSuccess,
                        errorMessage = libResult.exceptionOrNull()?.message,
                    )
                }
                is LoginSubmitResult.ValidateCodeError -> {
                    val challenge = auth.prepareChallenge().getOrNull()
                    if (challenge != null) {
                        val b64 = Base64.encodeToString(challenge.captchaImage, Base64.NO_WRAP)
                        throw CaptchaRequiredException(b64, challenge.execution, account.id, account.label)
                    }
                    SyncResult(0, false, "验证码错误")
                }
                is LoginSubmitResult.PasswordError -> SyncResult(0, false, "用户名或密码错误")
                is LoginSubmitResult.Failure -> SyncResult(0, false, r.message)
                else -> SyncResult(0, false, "登录失败")
            }
        } catch (e: CaptchaRequiredException) { throw e }
        catch (e: Exception) { SyncResult(0, false, e.message) }
    }

    /** 刷新验证码 - 对齐 Rust 版 refresh_captcha */
    suspend fun refreshCaptcha(accountId: Long): CaptchaRequiredException? {
        val auth = epayAdapter.getEpayAuth(accountId)
        val challenge = auth.prepareChallenge().getOrNull() ?: return null
        val account = accountRepository.getAccountById(accountId) ?: return null
        val imgB64 = Base64.encodeToString(challenge.captchaImage, Base64.NO_WRAP)
        return CaptchaRequiredException(imgB64, challenge.execution, accountId, account.label)
    }

    /**
     * 构造自动 OCR 验证码解析器，复用 [Captcha.ocrByRemoteTcpServerAutoRetry]。
     */
    private fun autoOcrResolver(): CaptchaResolver {
        return object : CaptchaResolver {
            override suspend fun resolve(imageData: ByteArray): Result<cn.edu.shmtu.cas.captcha.CaptchaAnswer> {
                val serverUrl = runBlocking { settingsDataStore.ocrServerUrl.first() }
                val parts = serverUrl.split(":")
                return if (parts.size == 2) {
                    val port = parts[1].toIntOrNull()
                    if (port != null) {
                        val answer = Captcha.ocrByRemoteTcpServerAutoRetry(parts[0], port, imageData)
                        if (answer.isNotBlank()) {
                            Result.success(cn.edu.shmtu.cas.captcha.CaptchaAnswer(answer, cn.edu.shmtu.cas.captcha.CaptchaAnswerKind.ANSWER))
                        } else {
                            Result.failure(Exception("OCR 识别失败"))
                        }
                    } else Result.failure(Exception("OCR 配置端口无效"))
                } else Result.failure(Exception("OCR 配置无效（需 host:port 格式）"))
            }
        }
    }
}

/**
 * lib SyncProgress → app SyncProgress 的翻译
 */
private fun LibSyncProgress.toDomain(): SyncProgress {
    val status: SyncStatus = when (val s = this.status) {
        LibSyncStatus.ProbingLogin -> SyncStatus.ProbingLogin
        LibSyncStatus.GettingCaptcha -> SyncStatus.GettingCaptcha
        LibSyncStatus.LoggingIn -> SyncStatus.LoggingIn
        is LibSyncStatus.Syncing -> SyncStatus.Syncing(page = s.page, total = s.total, newCount = newCount)
        LibSyncStatus.Persisting -> SyncStatus.Persisting(totalNew = totalNewCount)
        LibSyncStatus.Completed -> SyncStatus.Completed(totalNew = totalNewCount)
        is LibSyncStatus.Failed -> SyncStatus.Failed(s.error)
    }
    return SyncProgress(
        status = status,
        accountIndex = accountIndex,
        accountTotal = totalAccounts,
        accountLabel = currentAccount,
    )
}
