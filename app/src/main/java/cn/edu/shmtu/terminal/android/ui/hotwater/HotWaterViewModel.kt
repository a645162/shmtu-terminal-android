package cn.edu.shmtu.terminal.android.ui.hotwater

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.data.local.datastore.SecureStorage
import cn.edu.shmtu.terminal.android.data.remote.WechatAuthAdapter
import cn.edu.shmtu.terminal.android.domain.model.HotWaterBuilding
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.repository.HotWaterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HotWaterUiState(
    val isLoading: Boolean = false,
    val buildings: List<HotWaterBuilding> = emptyList(),
    val followedBuildings: List<Int> = emptyList(),
    val showCaptchaDialog: Boolean = false,
    val captchaImage: ByteArray? = null,
    val error: String? = null
)

@HiltViewModel
class HotWaterViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val hotWaterRepository: HotWaterRepository,
    private val wechatAuthAdapter: WechatAuthAdapter,
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val TAG = "HotWaterViewModel"
    private val _uiState = MutableStateFlow(HotWaterUiState())
    val uiState: StateFlow<HotWaterUiState> = _uiState.asStateFlow()

    private var pendingAccountId: Long = 0

    init {
        viewModelScope.launch {
            hotWaterRepository.getFollowedBuildings().collect { followed ->
                _uiState.value = _uiState.value.copy(followedBuildings = followed)
            }
        }
    }

    fun loadHotWater(accountId: Long) {
        pendingAccountId = accountId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val result = hotWaterRepository.fetchHotWaterData(accountId)

            result.onSuccess { buildings ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    buildings = buildings
                )
            }.onFailure { e ->
                if (e.message == "Session expired") {
                    tryLogin(accountId)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    private fun tryLogin(accountId: Long) {
        viewModelScope.launch {
            try {
                // 测试登录状态
                val testResult = wechatAuthAdapter.testLoginStatus(accountId)
                
                if (testResult.isSuccess && testResult.getOrNull() == true) {
                    // 已登录，重试
                    loadHotWater(accountId)
                    return@launch
                }

                // 需要登录，获取验证码
                val challengeResult = wechatAuthAdapter.prepareChallenge(accountId)
                
                if (challengeResult.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "获取验证码失败"
                    )
                    return@launch
                }

                val challenge = challengeResult.getOrNull()
                if (challenge == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "获取验证码失败"
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    showCaptchaDialog = true,
                    captchaImage = challenge.captchaImage
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "获取验证码失败: ${e.message}"
                )
            }
        }
    }

    fun submitCaptcha(captchaCode: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                showCaptchaDialog = false
            )

            try {
                val account = accountRepository.getAccountById(pendingAccountId)
                val password = secureStorage.getPassword(pendingAccountId)

                if (account == null || password == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "账号信息不完整"
                    )
                    return@launch
                }

                // 提交登录
                val submitResult = wechatAuthAdapter.submitLogin(
                    pendingAccountId,
                    account.userId,
                    password,
                    captchaCode
                )

                if (submitResult.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "登录异常: ${submitResult.exceptionOrNull()?.message}"
                    )
                    return@launch
                }

                val submitValue = submitResult.getOrNull()
                if (submitValue !is cn.edu.shmtu.cas.session.LoginSubmitResult.Success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "登录失败，验证码错误或已过期"
                    )
                    return@launch
                }

                // 登录成功，重新加载热水数据
                loadHotWater(pendingAccountId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "登录异常: ${e.message}"
                )
            }
        }
    }

    fun dismissCaptchaDialog() {
        _uiState.value = _uiState.value.copy(showCaptchaDialog = false)
    }

    fun toggleFollow(buildingNumber: Int) {
        viewModelScope.launch {
            val isFollowed = _uiState.value.followedBuildings.contains(buildingNumber)
            if (isFollowed) {
                hotWaterRepository.unfollowBuilding(buildingNumber)
            } else {
                hotWaterRepository.followBuilding(buildingNumber)
            }
            _uiState.value = _uiState.value.copy(
                buildings = _uiState.value.buildings.map {
                    if (it.buildingNumber == buildingNumber) it.copy(isFollowed = !isFollowed) else it
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
