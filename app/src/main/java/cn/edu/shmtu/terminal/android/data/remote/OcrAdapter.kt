package cn.edu.shmtu.terminal.android.data.remote

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN_Model
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class OcrAdapter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ncnn = SHMTU_NCNN()
    private var isModelLoaded = false

    suspend fun ensureModelLoaded(): Boolean = withContext(Dispatchers.IO) {
        if (isModelLoaded && ncnn.modelStatus != SHMTU_NCNN.ModelStatus.NOT_LOADED) {
            return@withContext true
        }

        if (SHMTU_NCNN_Model.isModelDownloaded(context)) {
            loadModelFromDir()
        } else if (SHMTU_NCNN_Model.isModelBuiltIn(context.assets)) {
            loadModelFromAssets(context.assets)
        } else {
            false
        }
    }

    private suspend fun loadModelFromDir(): Boolean = suspendCancellableCoroutine { cont ->
        SHMTU_NCNN_Model.loadModelFromDirAsync(ncnn, context, false, object : SHMTU_NCNN_Model.LoadCallback {
            override fun onSuccess() {
                isModelLoaded = true
                cont.resume(true)
            }
            override fun onError(error: String) {
                cont.resume(false)
            }
        })
    }

    private suspend fun loadModelFromAssets(assetManager: AssetManager): Boolean = suspendCancellableCoroutine { cont ->
        SHMTU_NCNN_Model.loadModelFromAssetsAsync(ncnn, assetManager, false, object : SHMTU_NCNN_Model.LoadCallback {
            override fun onSuccess() {
                isModelLoaded = true
                cont.resume(true)
            }
            override fun onError(error: String) {
                cont.resume(false)
            }
        })
    }

    suspend fun recognizeCaptcha(imageData: ByteArray): String? = withContext(Dispatchers.IO) {
        if (!isModelLoaded) {
            val loaded = ensureModelLoaded()
            if (!loaded) return@withContext null
        }

        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size) ?: return@withContext null
        val result = ncnn.predict_validate_code(bitmap) ?: return@withContext null

        if (result.size != 6) return@withContext null

        val op1 = result[2] as Int
        val operatorIndex = result[0] as Int
        val op2 = result[3] as Int
        val op3 = result[4] as Int
        val op4 = result[5] as Int

        val operator = when (operatorIndex) {
            0 -> "+"
            1 -> "-"
            2 -> "*"
            else -> return@withContext null
        }

        val expr = "$op1$operator$op2"
        val answer = when (operator) {
            "+" -> op1 + op2
            "-" -> op1 - op2
            "*" -> op1 * op2
            else -> return@withContext null
        }

        "$expr=$answer"
    }
}
