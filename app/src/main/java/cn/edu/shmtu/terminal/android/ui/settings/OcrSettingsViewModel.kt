package cn.edu.shmtu.terminal.android.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.cas.ocr.ModelDownloader
import cn.edu.shmtu.cas.ocr.NcnnModelLoader
import cn.edu.shmtu.cas.ocr.OcrModelInfo
import cn.edu.shmtu.cas.ocr.OcrV2TagCatalog
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
    private val okHttpClient = okhttp3.OkHttpClient()

    private val _uiState = MutableStateFlow(OcrSettingsUiState())
    val uiState: StateFlow<OcrSettingsUiState> = _uiState.asStateFlow()

    /** 验证码错误重试次数 (对齐 Tauri `ocr_retry_count`)。 */
    val ocrRetryCount: StateFlow<Int> = featureStore.ocrRetryCount

    fun setOcrRetryCount(n: Int) = featureStore.setOcrRetryCount(n)

    val ocrModelVersion: StateFlow<ModelVersion>
        get() = _ocrModelVersion

    private val _ocrModelVersion = MutableStateFlow(SHMTU_NCNN_Model.ModelVersion.V2)

    init {
        viewModelScope.launch {
            settingsDataStore.ocrModelVersion.collect { v ->
                _ocrModelVersion.value = v
                refreshStatus()
            }
        }
        // Load persisted v2 tag/backbone/precision and initialize model list
        viewModelScope.launch(Dispatchers.IO) {
            val tag = settingsDataStore.getOcrV2ModelTagNow().ifEmpty {
                SHMTU_NCNN_Model.V2_DEFAULT_TAG
            }
            val backbone = settingsDataStore.getOcrV2BackboneNow()
            val precision = settingsDataStore.getOcrV2PrecisionNow()
            _uiState.value = _uiState.value.copy(
                selectedTag = tag,
                selectedBackbone = backbone,
                selectedPrecision = precision,
            )
            // Try loading cached tags
            val cached = OcrV2TagCatalog.loadFromCache(context)
            if (cached != null) {
                _uiState.value = _uiState.value.copy(tags = cached)
            }
            // Try loading manifest for current tag
            loadModelsForTag(tag)
        }
    }

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
        val state = _uiState.value
        val tag = state.selectedTag.ifEmpty { null }
        val backbone = state.selectedBackbone
        val precision = state.selectedPrecision

        _uiState.value = _uiState.value.copy(
            isDownloading = true,
            overallDownloadProgress = 0,
            currentFileProgress = 0,
            downloadCurrentFile = 0,
            downloadTotalFiles = SHMTU_NCNN_Model.getV2ModelFiles(backbone, precision).size,
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
                        message = "v2 模型下载成功 (tag=$tag, backbone=$backbone, precision=$precision)"
                    )
                }

                override fun onError(error: String) {
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        message = "下载失败: $error"
                    )
                }
            },
            tag = tag,
            backbone = backbone,
            precision = precision,
        )
    }

    // ===================== v2 model selection: tag / model / precision =====================

    /** Refresh the list of candidate v2 release tags from GitHub. */
    fun refreshTags() {
        if (_uiState.value.isTagsLoading) return
        _uiState.value = _uiState.value.copy(isTagsLoading = true, tagsError = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tags = OcrV2TagCatalog.fetchFromNetwork(okHttpClient)
                OcrV2TagCatalog.saveToCache(context, tags)
                _uiState.value = _uiState.value.copy(
                    tags = tags,
                    isTagsLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTagsLoading = false,
                    tagsError = e.message ?: "未知错误",
                )
            }
        }
    }

    /** Select a tag and load its model manifest. */
    fun selectTag(tag: String) {
        if (_uiState.value.selectedTag == tag) return
        settingsDataStore.setOcrV2ModelTag(tag)
        _uiState.value = _uiState.value.copy(
            selectedTag = tag,
            models = emptyList(),
            modelsError = null,
        )
        viewModelScope.launch(Dispatchers.IO) {
            loadModelsForTag(tag)
        }
    }

    /** Select a backbone (model). */
    fun selectBackbone(backbone: String) {
        settingsDataStore.setOcrV2Backbone(backbone)
        _uiState.value = _uiState.value.copy(selectedBackbone = backbone)
    }

    /** Select a precision. */
    fun selectPrecision(precision: String) {
        settingsDataStore.setOcrV2Precision(precision)
        _uiState.value = _uiState.value.copy(selectedPrecision = precision)
    }

    /** Fetch manifest for [tag] and parse into model list. */
    private fun loadModelsForTag(tag: String) {
        _uiState.value = _uiState.value.copy(isModelsLoading = true, modelsError = null)
        try {
            val primary = ModelSource.GITEE
            val fallback = ModelSource.GITHUB
            val manifestJson = fetchV2ManifestJson(primary, fallback, tag)
            if (manifestJson == null) {
                _uiState.value = _uiState.value.copy(
                    isModelsLoading = false,
                    modelsError = "无法获取 manifest (tag=$tag)",
                )
                return
            }
            val manifest = modelDownloader.parseReleaseManifest(manifestJson)
            val models = modelDownloader.listModelsFromManifest(manifest)
            _uiState.value = _uiState.value.copy(
                isModelsLoading = false,
                models = models,
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isModelsLoading = false,
                modelsError = e.message ?: "解析 manifest 失败",
            )
        }
    }

    private fun fetchV2ManifestJson(primary: ModelSource, fallback: ModelSource, tag: String): String? {
        val sources = arrayOf(primary, fallback)
        for (source in sources) {
            val prefix = if (source == ModelSource.GITHUB)
                SHMTU_NCNN_Model.V2_URL_MODEL_PREFIX_GITHUB
            else
                SHMTU_NCNN_Model.V2_URL_MODEL_PREFIX_GITEE
            val url = "${prefix}$tag/${SHMTU_NCNN_Model.V2_MANIFEST_FILENAME}"
            try {
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val text = response.body.string().takeIf { it.isNotEmpty() }
                        if (text != null) return text
                    }
                }
            } catch (_: Exception) {
                // try next source
            }
        }
        return null
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
        // evictAll() and shutdown may close SSL sockets (network I/O),
        // which crashes on the main thread under StrictMode.
        Thread {
            okHttpClient.connectionPool.evictAll()
            okHttpClient.dispatcher.executorService.shutdownNow()
        }.start()
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
    // v2 model selection
    val tags: List<OcrV2TagCatalog.CatalogEntry> = emptyList(),
    val isTagsLoading: Boolean = false,
    val tagsError: String? = null,
    val selectedTag: String = SHMTU_NCNN_Model.V2_DEFAULT_TAG,
    val models: List<OcrModelInfo> = emptyList(),
    val isModelsLoading: Boolean = false,
    val modelsError: String? = null,
    val selectedBackbone: String = SHMTU_NCNN_Model.V2_DEFAULT_BACKBONE,
    val selectedPrecision: String = SHMTU_NCNN_Model.V2_DEFAULT_PRECISION,
)
