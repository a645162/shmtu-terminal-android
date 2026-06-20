package cn.edu.shmtu.terminal.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.data.local.datastore.CaptchaMode
import cn.edu.shmtu.terminal.android.data.local.datastore.OcrServerType
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val captchaMode: StateFlow<CaptchaMode> = settingsDataStore.captchaMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CaptchaMode.MANUAL)

    val useLocalOcr: StateFlow<Boolean> = settingsDataStore.useLocalOcr
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val ocrServerType: StateFlow<OcrServerType> = settingsDataStore.ocrServerType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OcrServerType.HTTP)

    val ocrServerUrl: StateFlow<String> = settingsDataStore.ocrServerUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "127.0.0.1:21601")

    val ocrHttpServerUrl: StateFlow<String> = settingsDataStore.ocrHttpServerUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OcrServerType.DEFAULT_HTTP_URL)

    fun setCaptchaMode(mode: CaptchaMode) {
        viewModelScope.launch {
            settingsDataStore.setCaptchaMode(mode)
        }
    }

    fun setUseLocalOcr(value: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setUseLocalOcr(value)
        }
    }

    fun setOcrServerType(type: OcrServerType) {
        viewModelScope.launch {
            settingsDataStore.setOcrServerType(type)
        }
    }

    fun setOcrServerUrl(url: String) {
        viewModelScope.launch {
            settingsDataStore.setOcrServerUrl(url)
        }
    }

    fun setOcrHttpServerUrl(url: String) {
        viewModelScope.launch {
            settingsDataStore.setOcrHttpServerUrl(url)
        }
    }
}
