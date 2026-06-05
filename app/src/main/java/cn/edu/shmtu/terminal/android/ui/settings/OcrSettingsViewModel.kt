package cn.edu.shmtu.terminal.android.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.cas.ocr.ModelDownloader
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN_Model
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OcrSettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val featureStore: FeatureSettingsStore
) : ViewModel() {

    private val shmtuNcnn = SHMTU_NCNN()
    private val modelDownloader = ModelDownloader()

    private val _uiState = MutableStateFlow(OcrSettingsUiState())
    val uiState: StateFlow<OcrSettingsUiState> = _uiState.asStateFlow()

    /** 验证码错误重试次数 (对齐 Tauri `ocr_retry_count`)。 */
    val ocrRetryCount: StateFlow<Int> = featureStore.ocrRetryCount

    fun setOcrRetryCount(n: Int) = featureStore.setOcrRetryCount(n)

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        _uiState.value = _uiState.value.copy(
            hasBuiltInModel = SHMTU_NCNN_Model.isModelBuiltIn(context.assets),
            hasDownloadedModel = SHMTU_NCNN_Model.isModelDownloaded(context),
            modelStatus = shmtuNcnn.modelStatus,
            gpuSupported = shmtuNcnn.isVulkanSupported
        )
    }

    fun loadModel(fromAssets: Boolean, useGpu: Boolean) {
        if (_uiState.value.isLoadingModel) return
        _uiState.value = _uiState.value.copy(isLoadingModel = true, message = null)

        if (useGpu && !shmtuNcnn.isVulkanSupported) {
            _uiState.value = _uiState.value.copy(
                isLoadingModel = false,
                message = "设备不支持 GPU，已回退到 CPU"
            )
            doLoadModel(fromAssets, false)
            return
        }

        doLoadModel(fromAssets, useGpu)
    }

    private fun doLoadModel(fromAssets: Boolean, useGpu: Boolean) {
        val callback = object : SHMTU_NCNN_Model.LoadCallback {
            override fun onSuccess() {
                refreshStatus()
                _uiState.value = _uiState.value.copy(
                    isLoadingModel = false,
                    message = "模型加载成功"
                )
            }

            override fun onError(error: String) {
                refreshStatus()
                _uiState.value = _uiState.value.copy(
                    isLoadingModel = false,
                    message = "加载失败: $error"
                )
            }
        }

        if (fromAssets) {
            SHMTU_NCNN_Model.loadModelFromAssetsAsync(shmtuNcnn, context.assets, useGpu, callback)
        } else {
            SHMTU_NCNN_Model.loadModelFromDirAsync(shmtuNcnn, context, useGpu, callback)
        }
    }

    fun releaseModel() {
        shmtuNcnn.releaseModel()
        refreshStatus()
        _uiState.value = _uiState.value.copy(message = "模型已释放")
    }

    fun downloadModel(source: SHMTU_NCNN_Model.ModelSource) {
        if (_uiState.value.isDownloading) return
        _uiState.value = _uiState.value.copy(
            isDownloading = true,
            overallDownloadProgress = 0,
            currentFileProgress = 0,
            downloadCurrentFile = 0,
            downloadTotalFiles = SHMTU_NCNN_Model.MODEL_FILES.size,
            downloadCurrentFileName = null,
            message = null
        )

        modelDownloader.download(source, context, object : ModelDownloader.DownloadProgressListener {
            override fun onProgress(
                fileIndex: Int,
                totalFiles: Int,
                currentFileName: String,
                currentFileProgress: Int,
                overallProgress: Int
            ) {
                _uiState.value = _uiState.value.copy(
                    downloadCurrentFile = fileIndex,
                    downloadTotalFiles = totalFiles,
                    downloadCurrentFileName = currentFileName,
                    currentFileProgress = currentFileProgress,
                    overallDownloadProgress = overallProgress
                )
            }

            override fun onSuccess() {
                refreshStatus()
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    overallDownloadProgress = 100,
                    currentFileProgress = 100,
                    message = "模型下载成功"
                )
            }

            override fun onError(error: String) {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    message = "下载失败: $error"
                )
            }
        })
    }

    fun deleteDownloadedModels() {
        if (_uiState.value.isDownloading || _uiState.value.isLoadingModel) return
        shmtuNcnn.releaseModel()
        val deleted = SHMTU_NCNN_Model.deleteDownloadedModels(context)
        refreshStatus()
        _uiState.value = _uiState.value.copy(
            message = if (deleted > 0) {
                "已删除 $deleted 个本地模型文件"
            } else {
                "当前没有可删除的本地模型文件"
            },
            overallDownloadProgress = 0,
            currentFileProgress = 0,
            downloadCurrentFile = 0,
            downloadCurrentFileName = null
        )
    }

    fun verifyDownloadedModels() {
        val current = _uiState.value
        if (current.isDownloading || current.isLoadingModel || current.isVerifyingSha256) return
        _uiState.value = current.copy(
            isVerifyingSha256 = true,
            message = null
        )
        viewModelScope.launch(Dispatchers.IO) {
            val result = modelDownloader.verifyDownloadedModels(context)
            _uiState.value = _uiState.value.copy(
                isVerifyingSha256 = false,
                message = result.getOrElse { "SHA256 校验失败: ${it.message}" }
            )
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun setUseLocalOcr(value: Boolean) {
        settingsDataStore.setUseLocalOcr(value)
    }

    fun setOcrServerUrl(url: String) {
        settingsDataStore.setOcrServerUrl(url)
    }

    override fun onCleared() {
        super.onCleared()
        modelDownloader.release()
    }
}

data class OcrSettingsUiState(
    val hasBuiltInModel: Boolean = false,
    val hasDownloadedModel: Boolean = false,
    val modelStatus: SHMTU_NCNN.ModelStatus = SHMTU_NCNN.ModelStatus.NOT_LOADED,
    val gpuSupported: Boolean = false,
    val isLoadingModel: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadCurrentFile: Int = 0,
    val downloadTotalFiles: Int = 0,
    val downloadCurrentFileName: String? = null,
    val currentFileProgress: Int = 0,
    val overallDownloadProgress: Int = 0,
    val isVerifyingSha256: Boolean = false,
    val message: String? = null
)
