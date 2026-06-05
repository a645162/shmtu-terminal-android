package cn.edu.shmtu.cas.ocr

import android.content.Context
import android.content.res.AssetManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * SHMTU NCNN 模型的协程封装。
 *
 * 将 [SHMTU_NCNN_Model] 的回调式 API 桥接为 `suspend` 函数，避免调用方写 `runOnUiThread` 模板代码。
 *
 * 加载策略：
 * 1. 优先用 `filesDir/ncnn_model/` 已下载的模型（[SHMTU_NCNN_Model.isModelDownloaded]）
 * 2. 否则用 assets 内置模型（[SHMTU_NCNN_Model.isModelBuiltIn]）
 * 3. 都没有则返回 false（调用方应引导用户下载模型）
 *
 * **线程安全**：当前实现假设同一进程同一 [SHMTU_NCNN] 实例在同一时刻只被一个协程加载；
 * 与 [SHMTU_NCNN] 原生 `isInit` 静态锁协同工作。
 */
object NcnnModelLoader {

    /**
     * 确保 [ncnn] 已加载本地或内置模型。
     *
     * 已加载（[SHMTU_NCNN.modelStatus] != NOT_LOADED）时直接返回 true，不重复 Init。
     *
     * @return 加载成功返回 true；模型不存在或加载失败返回 false
     */
    suspend fun ensureLoaded(
        ncnn: SHMTU_NCNN,
        context: Context,
        useGpu: Boolean = false,
    ): Boolean {
        if (ncnn.modelStatus != SHMTU_NCNN.ModelStatus.NOT_LOADED) {
            return true
        }
        return if (SHMTU_NCNN_Model.isModelDownloaded(context)) {
            loadFromDir(ncnn, context, useGpu)
        } else if (SHMTU_NCNN_Model.isModelBuiltIn(context.assets)) {
            loadFromAssets(ncnn, context.assets, useGpu)
        } else {
            false
        }
    }

    /**
     * 从 `filesDir/ncnn_model/` 加载已下载的模型。
     */
    suspend fun loadFromDir(
        ncnn: SHMTU_NCNN,
        context: Context,
        useGpu: Boolean = false,
    ): Boolean = suspendCancellableCoroutine { cont ->
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
    }

    /**
     * 从 assets 加载内置模型。
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
     * 释放 NCNN 模型占用的资源。
     *
     * 同步操作（[SHMTU_NCNN.releaseModel] 内部调用 native `ReleaseModel`）。
     */
    fun release(ncnn: SHMTU_NCNN) {
        ncnn.releaseModel()
    }
}
