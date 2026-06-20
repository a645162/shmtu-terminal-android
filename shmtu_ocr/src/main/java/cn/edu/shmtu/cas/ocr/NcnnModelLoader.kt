package cn.edu.shmtu.cas.ocr

import android.content.Context
import android.content.res.AssetManager
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN_Model.ModelVersion
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * SHMTU NCNN 模型的协程封装。
 *
 * 将 [SHMTU_NCNN_Model] 的回调式 API 桥接为 `suspend` 函数，避免调用方写 `runOnUiThread` 模板代码。
 *
 * 加载策略（[ModelVersion] 决定走 v1 还是 v2 路径）：
 * 1. 优先用 `filesDir/ncnn_model/<v1|v2>/` 已下载的模型
 * 2. 否则用 assets 内置模型（仅 v1 支持，v2 不会内置以减小包体）
 * 3. 都没有则返回 false（调用方应引导用户下载模型）
 *
 * **线程安全**：当前实现假设同一进程同一 [SHMTU_NCNN] 实例在同一时刻只被一个协程加载；
 * 与 [SHMTU_NCNN] 原生 `isInit`/`isV2Init` 静态锁协同工作。
 */
object NcnnModelLoader {

    /**
     * 确保 [ncnn] 已加载本地或内置模型。
     *
     * 已加载（v1 看 [SHMTU_NCNN.getModelStatus]；v2 看 [SHMTU_NCNN.getV2ModelStatus]）时直接返回 true。
     *
     * @return 加载成功返回 true；模型不存在或加载失败返回 false
     */
    suspend fun ensureLoaded(
        ncnn: SHMTU_NCNN,
        context: Context,
        version: ModelVersion = ModelVersion.V2,
        useGpu: Boolean = false,
        v2Backbone: String = SHMTU_NCNN_Model.V2_DEFAULT_BACKBONE,
        v2Precision: String = SHMTU_NCNN_Model.V2_DEFAULT_PRECISION,
    ): Boolean {
        if (isVersionLoaded(ncnn, version)) {
            return true
        }
        return if (SHMTU_NCNN_Model.isModelDownloaded(context, version, v2Backbone, v2Precision)) {
            loadFromDir(ncnn, context, version, useGpu, v2Backbone, v2Precision)
        } else if (SHMTU_NCNN_Model.isModelBuiltIn(context.assets, version)) {
            if (version == ModelVersion.V1) {
                loadFromAssets(ncnn, context.assets, useGpu)
            } else {
                // v2 has no built-in model
                false
            }
        } else {
            false
        }
    }

    private fun isVersionLoaded(ncnn: SHMTU_NCNN, version: ModelVersion): Boolean {
        val status = if (version == ModelVersion.V1) ncnn.modelStatus else ncnn.v2ModelStatus
        return status != SHMTU_NCNN.ModelStatus.NOT_LOADED
    }

    /**
     * 从 `filesDir/ncnn_model/<version>/` 加载已下载的模型。
     */
    suspend fun loadFromDir(
        ncnn: SHMTU_NCNN,
        context: Context,
        version: ModelVersion = ModelVersion.V2,
        useGpu: Boolean = false,
        v2Backbone: String = SHMTU_NCNN_Model.V2_DEFAULT_BACKBONE,
        v2Precision: String = SHMTU_NCNN_Model.V2_DEFAULT_PRECISION,
    ): Boolean = suspendCancellableCoroutine { cont ->
        if (version == ModelVersion.V1) {
            SHMTU_NCNN_Model.loadModelFromDirAsync(
                ncnn,
                context,
                useGpu,
                object : SHMTU_NCNN_Model.LoadCallback {
                    override fun onSuccess() {
                        if (cont.isActive) cont.resume(true)
                    }
                    override fun onError(error: String) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
            )
        } else {
            SHMTU_NCNN_Model.loadV2ModelFromDirAsync(
                ncnn,
                context,
                useGpu,
                v2Backbone,
                v2Precision,
                object : SHMTU_NCNN_Model.LoadCallback {
                    override fun onSuccess() {
                        if (cont.isActive) cont.resume(true)
                    }
                    override fun onError(error: String) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
            )
        }
    }

    /**
     * 从 assets 加载内置 v1 模型。
     */
    suspend fun loadFromAssets(
        ncnn: SHMTU_NCNN,
        assetManager: AssetManager,
        useGpu: Boolean = false,
    ): Boolean = suspendCancellableCoroutine { cont ->
        SHMTU_NCNN_Model.loadModelFromAssetsAsync(
            ncnn,
            assetManager,
            useGpu,
            object : SHMTU_NCNN_Model.LoadCallback {
                override fun onSuccess() {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onError(error: String) {
                    if (cont.isActive) cont.resume(false)
                }
            },
        )
    }

    /**
     * 释放当前占用 NCNN 模型占用的资源（v1 与 v2 同时释放，方便切换版本）。
     */
    fun release(ncnn: SHMTU_NCNN) {
        ncnn.releaseModel()
        ncnn.releaseV2Model()
    }
}
