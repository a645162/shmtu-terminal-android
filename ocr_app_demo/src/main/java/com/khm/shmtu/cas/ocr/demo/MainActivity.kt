package com.khm.shmtu.cas.ocr.demo

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import cn.edu.shmtu.cas.captcha.Captcha
import cn.edu.shmtu.cas.captcha.RemoteOcrCaptchaResolver
import cn.edu.shmtu.cas.captcha.RemoteOcrHttpCaptchaResolver
import cn.edu.shmtu.cas.ocr.ImageUtils
import cn.edu.shmtu.cas.ocr.ModelDownloader
import cn.edu.shmtu.cas.ocr.OcrModelInfo
import cn.edu.shmtu.cas.ocr.OcrV2TagCatalog
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN_Model
import com.google.android.material.button.MaterialButtonToggleGroup
import cn.edu.shmtu.cas.captcha.CaptchaAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    private enum class RemoteMode {
        TCP,
        REST
    }

    private companion object {
        const val TAG = "OcrDemo"
        const val PREFS_NAME = "remote_ocr_prefs"
        const val KEY_SERVER_IP = "server_ip"
        const val KEY_SERVER_PORT = "server_port"
        const val KEY_SERVER_BASE_URL = "server_base_url"
        const val KEY_REMOTE_MODE = "remote_mode"
        const val KEY_MODEL_VERSION = "model_version"
        const val KEY_V2_BACKBONE = "v2_backbone"
        const val KEY_V2_PRECISION = "v2_precision"
        const val KEY_V2_TAG = "v2_tag"
        const val DEFAULT_TCP_PORT = "21601"
        const val DEFAULT_HTTP_BASE_URL = "http://127.0.0.1:21600"

        /** Known backbones with parameter counts (millions) for display purposes. */
        val KNOWN_BACKBONES = listOf(
            BackboneInfo("mobilenet_v3_small", "MobileNetV3 Small", 2.5),
            BackboneInfo("mobilenetv4_conv_small", "MobileNetV4 Conv Small", 3.7),
            BackboneInfo("repvgg_a0", "RepVGG A0", 8.3),
            BackboneInfo("resnet18", "ResNet-18", 11.2),
        )
    }

    data class BackboneInfo(
        val key: String,
        val displayName: String,
        val paramsM: Double,
    )

    private val shmtuNcnn = SHMTU_NCNN()
    private val modelDownloader = ModelDownloader()
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Currently selected model version. */
    private var selectedVersion: SHMTU_NCNN_Model.ModelVersion = SHMTU_NCNN_Model.ModelVersion.V2
    /** v2 backbone (e.g. "mobilenet_v3_small"). */
    private var selectedBackbone: String = SHMTU_NCNN_Model.V2_DEFAULT_BACKBONE
    /** v2 precision (e.g. "fp16"). */
    private var selectedPrecision: String = SHMTU_NCNN_Model.V2_DEFAULT_PRECISION
    /** v2 release tag (null means auto-resolve latest). */
    private var selectedTag: String? = null

    // ---- views ----
    private lateinit var imageView: ImageView
    private lateinit var infoResult: TextView
    private lateinit var tvResultMeta: TextView
    private lateinit var tvModelStatus: TextView
    private lateinit var tvCurrentModel: TextView
    private lateinit var tvRemoteStatus: TextView
    private lateinit var tvDownloadStatus: TextView
    private lateinit var tvImageMeta: TextView
    private lateinit var progressBarOverall: ProgressBar
    private lateinit var progressBarCurrent: ProgressBar
    private lateinit var editTextIp: EditText
    private lateinit var editTextPort: EditText
    private lateinit var editTextBaseUrl: EditText
    private lateinit var remoteModeToggleGroup: MaterialButtonToggleGroup
    private lateinit var modelVersionToggleGroup: MaterialButtonToggleGroup
    private lateinit var remoteTcpPanel: View
    private lateinit var remoteHttpPanel: View

    private var innerBitmap: Bitmap? = null
    private var isDownloading = false
    private var selectedRemoteMode: RemoteMode = RemoteMode.TCP

    // ---- cached v2 manifest data for the advanced dialog ----
    private var cachedTagEntries: List<OcrV2TagCatalog.CatalogEntry> = emptyList()
    private var currentManifestModels: List<OcrModelInfo> = emptyList()

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            return@registerForActivityResult
        }
        try {
            val selectedImage = result.data?.data ?: return@registerForActivityResult
            val bitmap = ImageUtils.decodeUri(this, selectedImage) ?: return@registerForActivityResult
            setSelectedBitmap(bitmap, "本地图册")
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "Selected image not found", e)
            Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected image picker error", e)
            Toast.makeText(this, "载入图片失败", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activate_main)
        bindViews()
        supportActionBar?.hide()
        restorePreferences()
        initWidget()
        updateModelStatusText()
        updateCurrentModelDisplay()
        updateRemoteModeUi()
        renderEmptyResult()
        loadTagCacheInBackground()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
        modelDownloader.release()
    }

    // ==================== view binding ====================

    private fun bindViews() {
        imageView = findViewById(R.id.imageView)
        infoResult = findViewById(R.id.infoResult)
        tvResultMeta = findViewById(R.id.tv_result_meta)
        tvModelStatus = findViewById(R.id.tv_model_status)
        tvCurrentModel = findViewById(R.id.tv_current_model)
        tvRemoteStatus = findViewById(R.id.tv_remote_status)
        tvDownloadStatus = findViewById(R.id.tv_download_status)
        tvImageMeta = findViewById(R.id.tv_image_meta)
        progressBarOverall = findViewById(R.id.progressBarOverall)
        progressBarCurrent = findViewById(R.id.progressBarCurrent)
        editTextIp = findViewById(R.id.editText_Ip)
        editTextPort = findViewById(R.id.editText_Port)
        editTextBaseUrl = findViewById(R.id.editText_BaseUrl)
        remoteModeToggleGroup = findViewById(R.id.toggle_group_remote_mode)
        modelVersionToggleGroup = findViewById(R.id.toggle_group_model_version)
        remoteTcpPanel = findViewById(R.id.remote_tcp_panel)
        remoteHttpPanel = findViewById(R.id.remote_http_panel)
    }

    // ==================== preferences ====================

    private fun restorePreferences() {
        restoreRemoteServerInfo()
        selectedVersion = SHMTU_NCNN_Model.ModelVersion.fromString(
            prefs.getString(KEY_MODEL_VERSION, SHMTU_NCNN_Model.ModelVersion.V2.toStorageString())
        )
        selectedBackbone = prefs.getString(KEY_V2_BACKBONE, SHMTU_NCNN_Model.V2_DEFAULT_BACKBONE)
            ?: SHMTU_NCNN_Model.V2_DEFAULT_BACKBONE
        selectedPrecision = prefs.getString(KEY_V2_PRECISION, SHMTU_NCNN_Model.V2_DEFAULT_PRECISION)
            ?: SHMTU_NCNN_Model.V2_DEFAULT_PRECISION
        selectedTag = prefs.getString(KEY_V2_TAG, null)

        // Sync toggle button state
        if (selectedVersion == SHMTU_NCNN_Model.ModelVersion.V1) {
            modelVersionToggleGroup.check(R.id.button_version_v1)
        } else {
            modelVersionToggleGroup.check(R.id.button_version_v2)
        }
    }

    private fun saveModelPreferences() {
        prefs.edit()
            .putString(KEY_MODEL_VERSION, selectedVersion.toStorageString())
            .putString(KEY_V2_BACKBONE, selectedBackbone)
            .putString(KEY_V2_PRECISION, selectedPrecision)
            .also { if (selectedTag != null) it.putString(KEY_V2_TAG, selectedTag) else it.remove(KEY_V2_TAG) }
            .apply()
    }

    // ==================== widget initialization ====================

    private fun initWidget() {
        // --- image source buttons ---
        findViewById<Button>(R.id.buttonGetFromNet).setOnClickListener {
            launch(Dispatchers.IO) {
                try {
                    val bitmap = ImageUtils.downloadImageFromURL("https://cas.shmtu.edu.cn/cas/captcha")
                    runOnUiThread { setSelectedBitmap(bitmap, "校园验证码接口") }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch captcha from network", e)
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "网络获取失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        findViewById<Button>(R.id.buttonInner1).setOnClickListener {
            val bitmap = ImageUtils.getBitmapFromAssets(this, "test1_20240102160004_server.png")
            if (bitmap != null) {
                setSelectedBitmap(bitmap, "内置样本 A")
            } else {
                Toast.makeText(this, "读取内置样本失败", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.buttonInner2).setOnClickListener {
            val bitmap = ImageUtils.getBitmapFromAssets(this, "test2_20240102160811_server.png")
            if (bitmap != null) {
                setSelectedBitmap(bitmap, "内置样本 B")
            } else {
                Toast.makeText(this, "读取内置样本失败", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.buttonSelectImageFromLocal).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            imagePickerLauncher.launch(intent)
        }

        // --- OCR buttons ---
        findViewById<Button>(R.id.buttonDetect).setOnClickListener { doLocalOcrDemo() }
        findViewById<Button>(R.id.button_ocr_server).setOnClickListener { ocrViaRemoteServer() }
        findViewById<Button>(R.id.button_remote_health).setOnClickListener { healthCheckRemoteServer() }

        // --- legacy model buttons (still work with current version selection) ---
        findViewById<Button>(R.id.button_load_model).setOnClickListener { showLoadModelDialog() }
        findViewById<Button>(R.id.button_download_model).setOnClickListener { showDownloadModelDialog() }
        findViewById<Button>(R.id.button_check_status).setOnClickListener { showModelStatusDialog() }
        findViewById<Button>(R.id.button_release_model).setOnClickListener {
            val loaded = if (selectedVersion == SHMTU_NCNN_Model.ModelVersion.V1)
                shmtuNcnn.modelStatus != SHMTU_NCNN.ModelStatus.NOT_LOADED
            else
                shmtuNcnn.v2ModelStatus != SHMTU_NCNN.ModelStatus.NOT_LOADED
            if (!loaded) {
                Toast.makeText(this, "模型未加载", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            releaseModel()
        }

        // --- new OCR settings card ---
        findViewById<Button>(R.id.button_download_v2_quick).setOnClickListener { quickDownloadModel() }
        findViewById<Button>(R.id.button_advanced_settings).setOnClickListener { showAdvancedSettingsDialog() }

        // --- version toggle ---
        modelVersionToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedVersion = if (checkedId == R.id.button_version_v1)
                SHMTU_NCNN_Model.ModelVersion.V1
            else
                SHMTU_NCNN_Model.ModelVersion.V2
            saveModelPreferences()
            updateCurrentModelDisplay()
            updateModelStatusText()
        }

        // --- remote mode toggle (unchanged) ---
        remoteModeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedRemoteMode = if (checkedId == R.id.button_mode_rest) RemoteMode.REST else RemoteMode.TCP
            updateRemoteModeUi()
        }
    }

    // ==================== background tag loading ====================

    private fun loadTagCacheInBackground() {
        launch(Dispatchers.IO) {
            cachedTagEntries = OcrV2TagCatalog.loadFromCache(this@MainActivity) ?: emptyList()
        }
    }

    // ==================== quick download ====================

    private fun quickDownloadModel() {
        if (isDownloading) {
            Toast.makeText(this, "正在下载中", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("选择下载源")
            .setItems(arrayOf("从 Gitee 下载", "从 GitHub 下载")) { _, which ->
                val source = if (which == 0) SHMTU_NCNN_Model.ModelSource.GITEE else SHMTU_NCNN_Model.ModelSource.GITHUB
                startDownload(source)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun startDownload(source: SHMTU_NCNN_Model.ModelSource) {
        isDownloading = true
        progressBarOverall.visibility = View.VISIBLE
        progressBarCurrent.visibility = View.VISIBLE
        progressBarOverall.progress = 0
        progressBarCurrent.progress = 0
        val sourceName = if (source == SHMTU_NCNN_Model.ModelSource.GITEE) "Gitee" else "GitHub"
        tvDownloadStatus.text = "下载状态：准备从 $sourceName 获取模型"

        if (selectedVersion == SHMTU_NCNN_Model.ModelVersion.V1) {
            modelDownloader.download(source, this, createDownloadListener(sourceName))
        } else {
            modelDownloader.downloadV2(
                source = source,
                context = this,
                listener = createDownloadListener(sourceName),
                tag = selectedTag,
                backbone = selectedBackbone,
                precision = selectedPrecision,
            )
        }
    }

    private fun createDownloadListener(sourceName: String) = object : ModelDownloader.DownloadProgressListener {
        override fun onProgress(
            fileIndex: Int, totalFiles: Int, currentFileName: String,
            currentFileProgress: Int, overallProgress: Int
        ) {
            runOnUiThread {
                tvDownloadStatus.text = "下载状态：第 $fileIndex / $totalFiles 个文件 ($currentFileName)"
                progressBarOverall.max = 100
                progressBarOverall.progress = overallProgress
                progressBarCurrent.progress = currentFileProgress
            }
        }

        override fun onSuccess() {
            runOnUiThread {
                isDownloading = false
                progressBarOverall.visibility = View.GONE
                progressBarCurrent.visibility = View.GONE
                tvDownloadStatus.text = "下载状态：完成 (${selectedVersion.toStorageString()})"
                Toast.makeText(this@MainActivity, "模型下载成功", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onError(error: String) {
            runOnUiThread {
                isDownloading = false
                progressBarOverall.visibility = View.GONE
                progressBarCurrent.visibility = View.GONE
                tvDownloadStatus.text = "下载状态：失败"
                Toast.makeText(this@MainActivity, "下载失败: $error", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ==================== advanced settings dialog ====================

    private fun showAdvancedSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_advanced_settings, null)
        val spinnerTag = dialogView.findViewById<Spinner>(R.id.spinner_tag)
        val spinnerBackbone = dialogView.findViewById<Spinner>(R.id.spinner_backbone)
        val tvBackboneInfo = dialogView.findViewById<TextView>(R.id.tv_backbone_info)
        val togglePrecision = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggle_group_precision)
        val btnRefresh = dialogView.findViewById<Button>(R.id.button_refresh_tags)

        // ---- populate tag spinner ----
        fun refreshTagSpinner(entries: List<OcrV2TagCatalog.CatalogEntry>) {
            val items = if (entries.isEmpty()) {
                listOf(SHMTU_NCNN_Model.V2_DEFAULT_TAG + " (默认)")
            } else {
                entries.map { "${it.tag}${if (it.isPrerelease) " [pre]" else ""}" }
            }
            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, items)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerTag.adapter = adapter

            // Try to select the current tag
            val currentTag = selectedTag ?: SHMTU_NCNN_Model.V2_DEFAULT_TAG
            val idx = entries.indexOfFirst { it.tag == currentTag }
            if (idx >= 0) spinnerTag.setSelection(idx)
        }
        refreshTagSpinner(cachedTagEntries)

        // ---- populate backbone spinner ----
        val backboneNames = KNOWN_BACKBONES.map { "${it.displayName} (${it.key})" }
        val backboneAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, backboneNames)
        backboneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBackbone.adapter = backboneAdapter
        val backboneIdx = KNOWN_BACKBONES.indexOfFirst { it.key == selectedBackbone }.coerceAtLeast(0)
        spinnerBackbone.setSelection(backboneIdx)

        // ---- set precision toggle ----
        togglePrecision.check(
            if (selectedPrecision == "fp32") R.id.button_precision_fp32 else R.id.button_precision_fp16
        )

        // ---- backbone info display ----
        fun updateBackboneInfo() {
            val idx = spinnerBackbone.selectedItemPosition
            if (idx in KNOWN_BACKBONES.indices) {
                val bi = KNOWN_BACKBONES[idx]
                tvBackboneInfo.text = "参数量: ~${bi.paramsM}M"
            } else {
                tvBackboneInfo.text = ""
            }
        }
        spinnerBackbone.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateBackboneInfo()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        updateBackboneInfo()

        // ---- refresh tags button ----
        btnRefresh.setOnClickListener {
            btnRefresh.isEnabled = false
            btnRefresh.text = "刷新中…"
            launch(Dispatchers.IO) {
                try {
                    val fresh = OcrV2TagCatalog.fetchFromNetwork(okHttpClient)
                    OcrV2TagCatalog.saveToCache(this@MainActivity, fresh)
                    cachedTagEntries = fresh
                    runOnUiThread {
                        refreshTagSpinner(fresh)
                        btnRefresh.isEnabled = true
                        btnRefresh.text = getString(R.string.advanced_settings_refresh_tags)
                        Toast.makeText(this@MainActivity, "Tag 列表已刷新 (${fresh.size} 个)", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to refresh tags", e)
                    runOnUiThread {
                        btnRefresh.isEnabled = true
                        btnRefresh.text = getString(R.string.advanced_settings_refresh_tags)
                        Toast.makeText(this@MainActivity, "刷新失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.advanced_settings_apply) { _, _ ->
                // Read tag
                val tagPos = spinnerTag.selectedItemPosition
                selectedTag = if (tagPos >= 0 && tagPos < cachedTagEntries.size) {
                    cachedTagEntries[tagPos].tag
                } else {
                    null // default fallback
                }
                // Read backbone
                val bbIdx = spinnerBackbone.selectedItemPosition
                if (bbIdx in KNOWN_BACKBONES.indices) {
                    selectedBackbone = KNOWN_BACKBONES[bbIdx].key
                }
                // Read precision
                selectedPrecision = if (togglePrecision.checkedButtonId == R.id.button_precision_fp32) "fp32" else "fp16"
                saveModelPreferences()
                updateCurrentModelDisplay()
                Toast.makeText(this@MainActivity, "模型配置已更新", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.advanced_settings_cancel, null)
            .show()
    }

    // ==================== legacy dialogs (adapted for v1/v2) ====================

    private fun showLoadModelDialog() {
        val loaded = if (selectedVersion == SHMTU_NCNN_Model.ModelVersion.V1)
            shmtuNcnn.modelStatus != SHMTU_NCNN.ModelStatus.NOT_LOADED
        else
            shmtuNcnn.v2ModelStatus != SHMTU_NCNN.ModelStatus.NOT_LOADED
        if (loaded) {
            val statusText = if (selectedVersion == SHMTU_NCNN_Model.ModelVersion.V1)
                shmtuNcnn.modelStatus else shmtuNcnn.v2ModelStatus
            Toast.makeText(this, "模型已加载: $statusText", Toast.LENGTH_SHORT).show()
            return
        }

        val options = mutableListOf<String>()
        if (SHMTU_NCNN_Model.isModelBuiltIn(assets, selectedVersion)) {
            options.add("从内置资源加载")
        }
        options.add("从本地已下载模型加载")

        AlertDialog.Builder(this)
            .setTitle("选择加载方式 (${selectedVersion.toStorageString()})")
            .setItems(options.toTypedArray()) { _, which ->
                showDeviceSelectionDialog { useGpu ->
                    when {
                        options[which].contains("内置") -> loadModelFromAssets(useGpu)
                        else -> loadModelFromDownloaded(useGpu)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeviceSelectionDialog(onSelected: (Boolean) -> Unit) {
        if (!shmtuNcnn.isVulkanSupported) {
            onSelected(false)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("选择运行设备")
            .setItems(arrayOf("CPU", "GPU")) { _, which ->
                onSelected(which == 1)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDownloadModelDialog() {
        if (isDownloading) {
            Toast.makeText(this, "正在下载中", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("选择下载源 (${selectedVersion.toStorageString()})")
            .setItems(arrayOf("从 Gitee 下载", "从 GitHub 下载")) { _, which ->
                val source = if (which == 0) SHMTU_NCNN_Model.ModelSource.GITEE else SHMTU_NCNN_Model.ModelSource.GITHUB
                startDownload(source)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showModelStatusDialog() {
        updateModelStatusText()
        val downloaded = SHMTU_NCNN_Model.isModelDownloaded(this, selectedVersion)
        val modelInfo = SHMTU_NCNN_Model.getDownloadedModelInfo(this, selectedVersion)
        val status = if (downloaded) "已下载" else "未下载"
        AlertDialog.Builder(this)
            .setTitle("模型状态 (${selectedVersion.toStorageString()})")
            .setMessage("本地模型：$status\n\n版本：${selectedVersion.toStorageString()}\nBackbone：$selectedBackbone\n精度：$selectedPrecision\nTag：${selectedTag ?: "(自动)"}\n\n$modelInfo")
            .setPositiveButton("确定", null)
            .show()
    }

    // ==================== model status UI ====================

    private fun updateCurrentModelDisplay() {
        val text = if (selectedVersion == SHMTU_NCNN_Model.ModelVersion.V1) {
            "v1 (ResNet)"
        } else {
            "v2 · $selectedBackbone · $selectedPrecision"
        }
        tvCurrentModel.text = text
    }

    private fun updateModelStatusText() {
        val loadedStatus = if (selectedVersion == SHMTU_NCNN_Model.ModelVersion.V1) {
            shmtuNcnn.modelStatus
        } else {
            shmtuNcnn.v2ModelStatus
        }

        val statusText = when (loadedStatus) {
            SHMTU_NCNN.ModelStatus.NOT_LOADED -> "模型未加载"
            SHMTU_NCNN.ModelStatus.LOADED_CPU -> "模型已加载 · CPU"
            SHMTU_NCNN.ModelStatus.LOADED_GPU -> "模型已加载 · GPU"
        }
        val gpuSupport = if (shmtuNcnn.isVulkanSupported) "支持 Vulkan" else "仅 CPU"
        val ver = selectedVersion.toStorageString()
        tvModelStatus.text = "$ver | $statusText\n$gpuSupport"
    }

    // ==================== image helpers ====================

    private fun renderEmptyResult() {
        infoResult.text = getString(R.string.result_placeholder)
        infoResult.setTextColor(ContextCompat.getColor(this, R.color.demo_ink))
        tvResultMeta.text = getString(R.string.result_meta_placeholder)
        tvImageMeta.text = getString(R.string.image_meta_placeholder)
        updateRemoteStatus("待连接", "可选择 TCP 服务或 REST API 进行远程识别")
    }

    private fun setSelectedBitmap(bitmap: Bitmap, source: String) {
        imageView.setImageBitmap(bitmap)
        val rgba = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        innerBitmap = Bitmap.createScaledBitmap(rgba, 400, 140, false)
        rgba.recycle()
        tvImageMeta.text = "当前图片：$source | ${bitmap.width} x ${bitmap.height}"
        tvResultMeta.text = "已就绪，可执行本地或远程识别。"
        infoResult.text = "READY"
        infoResult.setTextColor(ContextCompat.getColor(this, R.color.demo_ink))
    }

    // ==================== local OCR ====================

    private fun doLocalOcrDemo() {
        val bitmap = innerBitmap
        if (bitmap == null) {
            Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show()
            return
        }

        val loaded = if (selectedVersion == SHMTU_NCNN_Model.ModelVersion.V1) {
            shmtuNcnn.modelStatus != SHMTU_NCNN.ModelStatus.NOT_LOADED
        } else {
            shmtuNcnn.v2ModelStatus != SHMTU_NCNN.ModelStatus.NOT_LOADED
        }

        if (!loaded) {
            AlertDialog.Builder(this)
                .setTitle("模型未加载")
                .setMessage("请先加载${selectedVersion.toStorageString()}模型后再进行本地识别")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        var resultObj: Array<Any?>? = null
        val duration = measureTimeMillis {
            resultObj = if (selectedVersion == SHMTU_NCNN_Model.ModelVersion.V1) {
                shmtuNcnn.predict_validate_code(bitmap)
            } else {
                shmtuNcnn.predict_validate_code_v2(bitmap)
            }
        }

        if (resultObj == null || resultObj.size < 2) {
            Toast.makeText(this, "识别失败", Toast.LENGTH_SHORT).show()
            return
        }

        val rawResult = resultObj[1] as? String ?: ""
        if (rawResult.isBlank()) {
            Toast.makeText(this, "识别结果为空", Toast.LENGTH_SHORT).show()
            return
        }

        renderResult(rawResult, "本地模型 (${selectedVersion.toStorageString()})", duration)
    }

    // ==================== model load / release ====================

    private fun loadModelFromAssets(useGpu: Boolean) {
        if (!SHMTU_NCNN_Model.isModelBuiltIn(assets, selectedVersion)) {
            Toast.makeText(this, "内置模型不存在 (${selectedVersion.toStorageString()})", Toast.LENGTH_SHORT).show()
            return
        }
        if (useGpu && !shmtuNcnn.isVulkanSupported) {
            Toast.makeText(this, "当前设备不支持 GPU", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "正在加载内置模型", Toast.LENGTH_SHORT).show()

        SHMTU_NCNN_Model.loadModelFromAssetsAsync(
            shmtuNcnn, assets, useGpu,
            object : SHMTU_NCNN_Model.LoadCallback {
                override fun onSuccess() {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "内置模型加载成功", Toast.LENGTH_SHORT).show()
                        updateModelStatusText()
                    }
                }
                override fun onError(error: String) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "加载失败: $error", Toast.LENGTH_LONG).show()
                        updateModelStatusText()
                    }
                }
            }
        )
    }

    private fun loadModelFromDownloaded(useGpu: Boolean) {
        if (!SHMTU_NCNN_Model.isModelDownloaded(this, selectedVersion)) {
            Toast.makeText(this, "本地未下载模型，请先下载 (${selectedVersion.toStorageString()})", Toast.LENGTH_SHORT).show()
            return
        }
        if (useGpu && !shmtuNcnn.isVulkanSupported) {
            Toast.makeText(this, "当前设备不支持 GPU", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "正在从本地加载${selectedVersion.toStorageString()}模型", Toast.LENGTH_SHORT).show()

        if (selectedVersion == SHMTU_NCNN_Model.ModelVersion.V1) {
            SHMTU_NCNN_Model.loadModelFromDirAsync(
                shmtuNcnn, this, useGpu,
                object : SHMTU_NCNN_Model.LoadCallback {
                    override fun onSuccess() {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "v1 模型加载成功", Toast.LENGTH_SHORT).show()
                            updateModelStatusText()
                        }
                    }
                    override fun onError(error: String) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "加载失败: $error", Toast.LENGTH_LONG).show()
                            updateModelStatusText()
                        }
                    }
                }
            )
        } else {
            SHMTU_NCNN_Model.loadV2ModelFromDirAsync(
                shmtuNcnn, this, useGpu,
                object : SHMTU_NCNN_Model.LoadCallback {
                    override fun onSuccess() {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "v2 模型加载成功", Toast.LENGTH_SHORT).show()
                            updateModelStatusText()
                        }
                    }
                    override fun onError(error: String) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "加载失败: $error", Toast.LENGTH_LONG).show()
                            updateModelStatusText()
                        }
                    }
                }
            )
        }
    }

    private fun releaseModel() {
        if (selectedVersion == SHMTU_NCNN_Model.ModelVersion.V1) {
            shmtuNcnn.releaseModel()
        } else {
            shmtuNcnn.releaseV2Model()
        }
        updateModelStatusText()
        Toast.makeText(this, "模型已释放", Toast.LENGTH_SHORT).show()
    }

    // ==================== remote OCR (unchanged) ====================

    private fun updateRemoteModeUi() {
        remoteModeToggleGroup.check(
            if (selectedRemoteMode == RemoteMode.REST) R.id.button_mode_rest else R.id.button_mode_tcp
        )
        remoteTcpPanel.visibility = if (selectedRemoteMode == RemoteMode.TCP) View.VISIBLE else View.GONE
        remoteHttpPanel.visibility = if (selectedRemoteMode == RemoteMode.REST) View.VISIBLE else View.GONE
        updateRemoteStatus(
            if (selectedRemoteMode == RemoteMode.TCP) "TCP 模式" else "REST 模式",
            if (selectedRemoteMode == RemoteMode.TCP) "适合局域网直连，默认端口 $DEFAULT_TCP_PORT"
            else "使用 /api/health 与 /api/ocr，适合服务化部署"
        )
    }

    private fun healthCheckRemoteServer() {
        when (selectedRemoteMode) {
            RemoteMode.TCP -> {
                val host = editTextIp.text?.toString()?.trim().orEmpty()
                val port = editTextPort.text?.toString()?.trim().orEmpty()
                if (host.isBlank() || !Captcha.validatePort(port)) {
                    Toast.makeText(this, "请填写有效的 TCP 地址和端口", Toast.LENGTH_SHORT).show()
                    return
                }
                updateRemoteStatus("TCP 检查中", "$host:$port")
                launch(Dispatchers.IO) {
                    val ok = RemoteOcrCaptchaResolver(host, port.toInt()).healthCheck()
                    runOnUiThread {
                        if (ok) {
                            saveRemoteServerInfo()
                            updateRemoteStatus("TCP 可达", "$host:$port")
                            Toast.makeText(this@MainActivity, "TCP 服务可达", Toast.LENGTH_SHORT).show()
                        } else {
                            updateRemoteStatus("TCP 不可达", "$host:$port")
                            Toast.makeText(this@MainActivity, "无法连接 TCP 服务", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            RemoteMode.REST -> {
                val baseUrl = normalizeBaseUrl(editTextBaseUrl.text?.toString())
                if (baseUrl == null) {
                    Toast.makeText(this, "请输入有效的 REST 基础地址", Toast.LENGTH_SHORT).show()
                    return
                }
                updateRemoteStatus("REST 检查中", baseUrl)
                launch(Dispatchers.IO) {
                    val ok = RemoteOcrHttpCaptchaResolver(baseUrl).healthCheck()
                    runOnUiThread {
                        if (ok) {
                            saveRemoteServerInfo()
                            updateRemoteStatus("REST 在线", baseUrl)
                            Toast.makeText(this@MainActivity, "REST API 连通正常", Toast.LENGTH_SHORT).show()
                        } else {
                            updateRemoteStatus("REST 异常", baseUrl)
                            Toast.makeText(this@MainActivity, "REST API 健康检查失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun ocrViaRemoteServer() {
        val bitmap = innerBitmap
        if (bitmap == null) {
            Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show()
            return
        }
        val imageData = CaptchaAndroid.AndroidBitmapToByteArray(bitmap)
        when (selectedRemoteMode) {
            RemoteMode.TCP -> {
                val host = editTextIp.text?.toString()?.trim().orEmpty()
                val portStr = editTextPort.text?.toString()?.trim().orEmpty()
                val port = portStr.toIntOrNull()
                if (host.isBlank() || port == null || !Captcha.validatePort(port)) {
                    Toast.makeText(this, "请填写有效的 TCP 地址和端口", Toast.LENGTH_SHORT).show()
                    return
                }
                updateRemoteStatus("TCP 识别中", "$host:$port")
                launch(Dispatchers.IO) {
                    try {
                        var result = ""
                        val duration = measureTimeMillis {
                            result = Captcha.ocrByRemoteTcpServerAutoRetry(host, port, imageData)
                        }
                        runOnUiThread {
                            if (result.isBlank()) {
                                updateRemoteStatus("TCP 失败", "$host:$port")
                                Toast.makeText(this@MainActivity, "远程 TCP OCR 失败", Toast.LENGTH_SHORT).show()
                            } else {
                                saveRemoteServerInfo()
                                updateRemoteStatus("TCP 完成", "$host:$port")
                                renderResult(result, "远程 TCP", duration)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Remote TCP OCR failed", e)
                        runOnUiThread {
                            updateRemoteStatus("TCP 异常", e.message ?: "未知错误")
                            Toast.makeText(this@MainActivity, "远程 TCP OCR 异常", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            RemoteMode.REST -> {
                val baseUrl = normalizeBaseUrl(editTextBaseUrl.text?.toString())
                if (baseUrl == null) {
                    Toast.makeText(this, "请输入有效的 REST 基础地址", Toast.LENGTH_SHORT).show()
                    return
                }
                updateRemoteStatus("REST 识别中", baseUrl)
                launch(Dispatchers.IO) {
                    runOnUiThread { updateRemoteStatus("REST 请求中", baseUrl) }
                    var result: Result<cn.edu.shmtu.cas.captcha.CaptchaAnswer>? = null
                    val duration = measureTimeMillis {
                        result = RemoteOcrHttpCaptchaResolver(baseUrl).resolve(imageData)
                    }
                    runOnUiThread {
                        result?.onSuccess { payload ->
                            saveRemoteServerInfo()
                            updateRemoteStatus("REST 完成", baseUrl)
                            renderResult(payload.value, "REST API", duration)
                        }?.onFailure { error ->
                            Log.e(TAG, "Remote REST OCR failed", error)
                            updateRemoteStatus("REST 失败", error.message ?: "未知错误")
                            Toast.makeText(this@MainActivity, "REST OCR 失败: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun renderResult(rawResult: String, source: String, durationMs: Long) {
        infoResult.text = rawResult
        infoResult.setTextColor(ContextCompat.getColor(this, R.color.demo_ink))
        val finalAnswer = Captcha.getExprResultByExprString(rawResult).ifBlank { "未解析" }
        tvResultMeta.text = "来源：$source | 结果：$finalAnswer | 耗时：${durationMs}ms"
    }

    private fun updateRemoteStatus(title: String, detail: String) {
        tvRemoteStatus.text = "$title\n$detail"
    }

    // ==================== remote preferences ====================

    private fun saveRemoteServerInfo() {
        prefs.edit()
            .putString(KEY_SERVER_IP, editTextIp.text?.toString()?.trim().orEmpty())
            .putString(KEY_SERVER_PORT, editTextPort.text?.toString()?.trim().orEmpty())
            .putString(KEY_SERVER_BASE_URL, editTextBaseUrl.text?.toString()?.trim().orEmpty())
            .putString(KEY_REMOTE_MODE, selectedRemoteMode.name)
            .apply()
    }

    private fun restoreRemoteServerInfo() {
        editTextIp.setText(prefs.getString(KEY_SERVER_IP, ""))
        editTextPort.setText(prefs.getString(KEY_SERVER_PORT, DEFAULT_TCP_PORT))
        editTextBaseUrl.setText(prefs.getString(KEY_SERVER_BASE_URL, DEFAULT_HTTP_BASE_URL))
        selectedRemoteMode = runCatching {
            RemoteMode.valueOf(prefs.getString(KEY_REMOTE_MODE, RemoteMode.TCP.name) ?: RemoteMode.TCP.name)
        }.getOrDefault(RemoteMode.TCP)
    }

    private fun normalizeBaseUrl(raw: String?): String? {
        val candidate = raw?.trim()?.trimEnd('/').orEmpty()
        return if (RemoteOcrHttpCaptchaResolver.isValidBaseUrl(candidate)) candidate else null
    }
}
