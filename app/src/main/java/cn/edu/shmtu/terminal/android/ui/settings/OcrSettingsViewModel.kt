package cn.edu.shmtu.terminal.android.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.cas.ocr.ModelDownloader
import cn.edu.shmtu.cas.ocr.NcnnModelLoader
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN_Model
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN_Model.ModelSource
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN_Model.ModelVersion
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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

    val ocrModelVersion: StateFlow<ModelVersion>
        get() = _ocrModelVersion

    init {
        viewModelScope.launch {
            settingsDataStore.ocrModelVersion.collect { v ->
                _ocrModelVersion.value = v
                refreshStatus()
            }
        }
    }

    private val _ocrModelVersion = MutableStateFlow(SHMTU_NCNN_Model.ModelVersion.V2)

    fun refreshStatus() {
        val version = _ocrModelVersion.value
        _uiState.value = _uiState.value.copy(
            ocrModelVersion = version,
            hasBuiltInModel = SHMTU_NCNN_Model.isModelBuiltIn(context.assets, version),
            hasDownloadedModel = SHMTU_NCNN_Model.isModelDownloaded(context, version),
            modelStatus = currentStatus(version),
            v2ModelStatus = shmtuNcnn.v2ModelStatus,
            gpuSupported = shmtuNcnn.isVulkanSupported
        )
    }

    private fun currentStatus(version: ModelVersion): SHMTU_NCNN.ModelStatus =
        if (version == ModelVersion.V1) shmtuNcnn.modelStatus else shmtuNcnn.v2ModelStatus

    fun setOcrModelVersion(version: ModelVersion) {
        if (_ocrModelVersion.value == version) return
        // Release whatever is currently loaded so the switch is clean.
        shmtuNcnn.releaseModel()
        shmtuNcnn.releaseV2Model()
        _ocrModelVersion.value = version
        settingsDataStore.setOcrModelVersion(version)
        refreshStatus()
        _uiState.value = _uiState.value.copy(
            message = "已切换到 ${version.name}，请重新加载模型"
        )
    }

    fun loadModel(fromAssets: Boolean, useGpu: Boolean) {
        loadModelForVersion(_ocrModelVersion.value, fromAssets, useGpu)
    }

    fun loadModelForVersion(version: ModelVersion, fromAssets: Boolean, useGpu: Boolean) {
        if (_uiState.value.isLoadingModel) return
        _uiState.value = _uiState.value.copy(isLoadingModel = true, message = null)

        if (useGpu && !shmtuNcnn.isVulkanSupported) {
            _uiState.value = _uiState.value.copy(
                isLoadingModel = false,
                message = "设备不支持 GPU，已回退到 CPU"
            )
            doLoadModel(version, fromAssets, false)
            return
        }

        doLoadModel(version, fromAssets, useGpu)
    }

    private fun doLoadModel(version: ModelVersion, fromAssets: Boolean, useGpu: Boolean) {
        val callback = object : SHMTU_NCNN_Model.LoadCallback {
            override fun onSuccess() {
                refreshStatus()
                _uiState.value = _uiState.value.copy(
                    isLoadingModel = false,
                    message = "模型加载成功 (${version.name})"
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

        when (version) {
            ModelVersion.V1 -> {
                if (fromAssets) {
                    SHMTU_NCNN_Model.loadModelFromAssetsAsync(shmtuNcnn, context.assets, useGpu, callback)
                } else {
                    SHMTU_NCNN_Model.loadModelFromDirAsync(shmtuNcnn, context, useGpu, callback)
                }
            }
            ModelVersion.V2 -> {
                // v2 is download-only; the assets path falls back to dir.
                SHMTU_NCNN_Model.loadV2ModelFromDirAsync(shmtuNcnn, context, useGpu, callback)
            }
        }
    }

    fun releaseModel() {
        shmtuNcnn.releaseModel()
        shmtuNcnn.releaseV2Model()
        refreshStatus()
        _uiState.value = _uiState.value.copy(message = "模型已释放")
    }

    fun downloadModel(source: ModelSource) {
        val version = _ocrModelVersion.value
        if (_uiState.value.isDownloading) return
        if (version == ModelVersion.V1) {
            downloadV1(source)
        } else {
            downloadV2(source)
        }
    }

    private fun downloadV1(source: ModelSource) {
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
                    message = "v1 模型下载成功"
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

    private fun downloadV2(source: ModelSource) {
        val files = SHMTU_NCNN_Model.getV2ModelFiles(
            SHMTU_NCNN_Model.V2_DEFAULT_BACKBONE,
            SHMTU_NCNN_Model.V2_DEFAULT_PRECISION
        )
        _uiState.value = _uiState.value.copy(
            isDownloading = true,
            overallDownloadProgress = 0,
            currentFileProgress = 0,
            downloadCurrentFile = 0,
            downloadTotalFiles = files.size,
            downloadCurrentFileName = null,
            message = null
        )

        modelDownloader.downloadV2(
            source = source,
            context = context,
            listener = object : ModelDownloader.DownloadProgressListener {
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
                        message = "v2 模型下载成功"
                    )
                }

                override fun onError(error: String) {
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        message = "下载失败: $error"
                    )
                }
            }
        )
    }

    fun deleteDownloadedModels() {
        if (_uiState.value.isDownloading || _uiState.value.isLoadingModel) return
        shmtuNcnn.releaseModel()
        shmtuNcnn.releaseV2Model()
        val version = _ocrModelVersion.value
        val deleted = SHMTU_NCNN_Model.deleteDownloadedModels(context, version)
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
    val v2ModelStatus: SHMTU_NCNN.ModelStatus = SHMTU_NCNN.ModelStatus.NOT_LOADED,
    val gpuSupported: Boolean = false,
    val isLoadingModel: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadCurrentFile: Int = 0,
    val downloadTotalFiles: Int = 0,
    val downloadCurrentFileName: String? = null,
    val currentFileProgress: Int = 0,
    val overallDownloadProgress: Int = 0,
    val isVerifyingSha256: Boolean = false,
    val message: String? = null,
    val ocrModelVersion: SHMTU_NCNN_Model.ModelVersion = SHMTU_NCNN_Model.ModelVersion.V2,
)
