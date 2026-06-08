package cn.edu.shmtu.terminal.android.domain.usecase.bill

import android.util.Log
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
import cn.edu.shmtu.cas.sync.fullSync as libFullSync
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
import cn.edu.shmtu.terminal.android.domain.usecase.bill.Purpose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
import cn.edu.shmtu.terminal.android.data.sync.BillMergeService
class SyncAccountBillsUseCase @Inject constructor(
    private val epayAdapter: EpayAdapter,
    private val accountRepository: AccountRepository,
    private val settingsDataStore: SettingsDataStore,
    private val billMergeService: BillMergeService,
) {
    private val tag = "SyncAccountBills"

    suspend operator fun invoke(account: Account): SyncResult = invoke(account, SyncRangePreset.Month) {}

    suspend operator fun invoke(
        account: Account,
        onProgress: (SyncProgress) -> Unit,
    ): SyncResult = invoke(account, SyncRangePreset.Month, onProgress)

    suspend operator fun invoke(
        account: Account,
        syncRange: SyncRangePreset,
        onProgress: (SyncProgress) -> Unit = {},
    ): SyncResult = runAccountSync(
        account = account,
        syncRange = syncRange,
        fullSync = false,
        onProgress = onProgress,
    )

    suspend fun fullSync(
        account: Account,
        syncRange: SyncRangePreset,
        onProgress: (SyncProgress) -> Unit = {},
    ): SyncResult = runAccountSync(
        account = account,
        syncRange = syncRange,
        fullSync = true,
        onProgress = onProgress,
    )

    suspend fun fullSync(
        account: Account,
        onProgress: (SyncProgress) -> Unit,
    ): SyncResult = fullSync(account, SyncRangePreset.All, onProgress)

    /** 使用验证码完成登录后继续同步 - 对齐 Rust 版 sync_with_captcha */
    suspend fun syncWithCaptcha(
        account: Account,
        captchaCode: String,
        execution: String,
        syncRange: SyncRangePreset,
        fullSync: Boolean,
        onProgress: (SyncProgress) -> Unit,
    ): SyncResult = withContext(Dispatchers.IO) {
        val auth = epayAdapter.getEpayAuth(account.id)
        val password = accountRepository.getPassword(account.id).orEmpty()
        try {
            Log.d(
                tag,
                "syncWithCaptcha start accountId=${account.id} label=${account.label} fullSync=$fullSync range=$syncRange execution=${execution.take(16)}..."
            )
            onProgress(SyncProgress(status = SyncStatus.LoggingIn, accountLabel = account.label))
            if (password.isBlank()) {
                Log.w(tag, "syncWithCaptcha missing password for accountId=${account.id}")
                return@withContext SyncResult(0, false, "未找到密码，请重新保存账号密码")
            }
            val submitResult = auth.submitLogin(account.userId, password, captchaCode, execution)
            when (val r = submitResult.getOrNull()) {
                is LoginSubmitResult.Success -> {
                    Log.d(tag, "syncWithCaptcha login success accountId=${account.id}, continue fullSync=$fullSync")
                    epayAdapter.saveSessionCookies(account.id, auth)
                    val store = createStore(account)
                    val libResult = if (fullSync) {
                        libFullSync(
                            auth = auth,
                            store = store,
                            options = SyncOptions.full(syncRange),
                            onProgress = { p -> onProgress(p.toDomain()) },
                        )
                    } else {
                        incrementalSync(
                            auth = auth,
                            store = store,
                            options = SyncOptions.incremental(syncRange),
                            onProgress = { p -> onProgress(p.toDomain()) },
                        )
                    }
                    accountRepository.updateLastSyncTime(account.id)
                    accountRepository.updateLoginStatus(account.id, "LOGGED_IN")
                    Log.d(
                        tag,
                        "syncWithCaptcha completed accountId=${account.id} success=${libResult.isSuccess} newCount=${libResult.getOrNull()?.newCount ?: 0}"
                    )
                    SyncResult(
                        newCount = libResult.getOrNull()?.newCount ?: 0,
                        success = libResult.isSuccess,
                        errorMessage = libResult.exceptionOrNull()?.message,
                    )
                }
                is LoginSubmitResult.ValidateCodeError -> {
                    Log.w(tag, "syncWithCaptcha captcha invalid accountId=${account.id}")
                    val challenge = auth.prepareChallenge().getOrNull()
                    if (challenge != null) {
                        val b64 = Base64.encodeToString(challenge.captchaImage, Base64.NO_WRAP)
                        throw CaptchaRequiredException(
                            captchaImageBase64 = b64,
                            execution = challenge.execution,
                            accountId = account.id,
                            accountLabel = account.label,
                            syncRange = syncRange,
                            isFullSync = fullSync,
                            purpose = Purpose.SYNC,
                        )
                    }
                    SyncResult(0, false, "验证码错误")
                }
                is LoginSubmitResult.PasswordError -> {
                    Log.w(tag, "syncWithCaptcha password error accountId=${account.id}")
                    SyncResult(0, false, "用户名或密码错误")
                }
                is LoginSubmitResult.Failure -> {
                    Log.w(tag, "syncWithCaptcha login failure accountId=${account.id} message=${r.message}")
                    SyncResult(0, false, r.message)
                }
                else -> {
                    Log.w(tag, "syncWithCaptcha unknown login result accountId=${account.id}")
                    SyncResult(0, false, "登录失败")
                }
            }
        } catch (e: CaptchaRequiredException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "syncWithCaptcha exception accountId=${account.id}", e)
            onProgress(SyncProgress(status = SyncStatus.Failed(e.message ?: e.javaClass.simpleName), accountLabel = account.label))
            SyncResult(0, false, e.message)
        }
    }

    /** 刷新验证码 - 对齐 Rust 版 refresh_captcha */
    suspend fun refreshCaptcha(accountId: Long): CaptchaRequiredException? {
        val auth = epayAdapter.getEpayAuth(accountId)
        val challenge = auth.prepareChallenge().getOrNull() ?: return null
        val account = accountRepository.getAccountById(accountId) ?: return null
        val imgB64 = Base64.encodeToString(challenge.captchaImage, Base64.NO_WRAP)
        return CaptchaRequiredException(
            captchaImageBase64 = imgB64,
            execution = challenge.execution,
            accountId = accountId,
            accountLabel = account.label,
            syncRange = SyncRangePreset.Month,
            isFullSync = false,
            purpose = Purpose.SYNC,
        )
    }

    private suspend fun runAccountSync(
        account: Account,
        syncRange: SyncRangePreset,
        fullSync: Boolean,
        onProgress: (SyncProgress) -> Unit,
    ): SyncResult = withContext(Dispatchers.IO) {
        val captchaMode = settingsDataStore.captchaMode.first()
        val resolver: CaptchaResolver? = when (captchaMode) {
            CaptchaMode.MANUAL -> null
            CaptchaMode.AUTO_OCR -> autoOcrResolver()
        }
        val password = accountRepository.getPassword(account.id).orEmpty()
        Log.d(
            tag,
            "runAccountSync start accountId=${account.id} label=${account.label} fullSync=$fullSync range=$syncRange captchaMode=$captchaMode hasPassword=${password.isNotBlank()}"
        )

        if (fullSync) {
            epayAdapter.invalidateSession(account.id)
            Log.d(tag, "runAccountSync invalidated session for full sync accountId=${account.id}")
        }
        val auth = epayAdapter.getEpayAuth(account.id)

        try {
            val libResult = syncAccount(
                auth = auth,
                store = createStore(account),
                context = AccountContext(
                    accountId = account.id.toString(),
                    accountLabel = account.label,
                ),
                resolver = resolver,
                username = account.userId,
                password = password,
                options = if (fullSync) SyncOptions.full(syncRange) else SyncOptions.incremental(syncRange),
                fullSync = fullSync,
                onProgress = { p -> onProgress(p.toDomain()) },
            )
            accountRepository.updateLastSyncTime(account.id)
            accountRepository.updateLoginStatus(account.id, "LOGGED_IN")
            Log.d(
                tag,
                "runAccountSync success accountId=${account.id} fullSync=$fullSync newCount=${libResult.getOrNull()?.newCount ?: 0}"
            )
            SyncResult(
                newCount = libResult.getOrNull()?.newCount ?: 0,
                success = libResult.isSuccess,
                errorMessage = libResult.exceptionOrNull()?.message,
            )
        } catch (e: ManualCaptchaRequiredException) {
            Log.w(tag, "runAccountSync captcha required accountId=${account.id} fullSync=$fullSync")
            val imgB64 = e.captchaImageBase64.ifBlank {
                Base64.encodeToString(e.captchaImageBytes, Base64.NO_WRAP)
            }
            onProgress(SyncProgress(status = SyncStatus.GettingCaptcha, accountLabel = account.label))
            throw CaptchaRequiredException(
                captchaImageBase64 = imgB64,
                execution = e.execution,
                accountId = account.id,
                accountLabel = account.label,
                syncRange = syncRange,
                isFullSync = fullSync,
                purpose = Purpose.SYNC,
            )
        } catch (e: Exception) {
            Log.e(tag, "runAccountSync failure accountId=${account.id} fullSync=$fullSync", e)
            onProgress(SyncProgress(status = SyncStatus.Failed(e.message ?: e.javaClass.simpleName), accountLabel = account.label))
            SyncResult(0, false, e.message)
        }
    }

    private fun createStore(account: Account) = RoomBillStore(
        billDbManager = epayAdapter.billDbManager,
        billMergeService = billMergeService,
        accountId = account.id,
        studentId = account.userId,
        identityId = account.identityId,
    ).also {
        // 把 Tauri 兼容的 TOML 规则 (assets/bill/*.toml) 注入到 store,
        // merge 时即时计算 category / position / room / building,落库持久化。
        it.classifier = epayAdapter.classifier
        it.positionTranslator = epayAdapter.positionTranslator
    }

    /**
     * 构造自动 OCR 验证码解析器，复用 [Captcha.ocrByRemoteTcpServerAutoRetry]。
     */
    private fun autoOcrResolver(): CaptchaResolver {
        return object : CaptchaResolver {
            override suspend fun resolve(imageData: ByteArray): Result<cn.edu.shmtu.cas.captcha.CaptchaAnswer> {
                val serverUrl = settingsDataStore.ocrServerUrl.first()
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
