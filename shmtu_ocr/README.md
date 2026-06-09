# shmtu_ocr (Android)

Android 端 NCNN 推理库，封装上海海事大学 CAS 验证码 OCR 识别。支持 v1（legacy）与 v2（**默认**）两套模型。

## 模型版本

| 版本 | 模型数量 | Backbone | 引擎 | 标签 | 默认 |
|---|---|---|---|---|---|
| v1 | 3 | resnet18 / resnet34 | NCNN | `v1.0-NCNN` | 否 |
| **v2** | **1** | `mobilenet_v3_small` | NCNN | `v2.0.x` | **是** |

详细对比与下载策略见根仓库 [Documents/docs/ocr-model-versions.md](../../../Documents/docs/ocr-model-versions.md)。

## 快速开始

### Kotlin

```kotlin
val ocr = SHMTU_NCNN_Model(
    context = applicationContext,
    modelDir = File(filesDir, "models"),
    version = ModelVersion.V2,        // 默认 V2
    useGpu = false
)

// 缺失则自动从 GitHub / Gitee release 下载
ocr.ensureModels(object : ModelDownloader.DownloadProgressListener {
    override fun onProgress(fileIndex: Int, total: Int, name: String, current: Int, overall: Int) {
        // 进度回调
    }
    override fun onSuccess() { /* 模型准备就绪 */ }
    override fun onError(error: String) { /* 失败处理 */ }
})

// 加载到 NCNN
ocr.loadModel()

// 推理
val result: OcrResult = ocr.predictValidateCode(bitmap)
println("${result.expr} = ${result.result}")
```

### 切换到 v1

```kotlin
val ocr = SHMTU_NCNN_Model(
    context = applicationContext,
    modelDir = File(filesDir, "models"),
    version = ModelVersion.V1,        // 走老的 3 模型 ResNet 路径
    useGpu = false
)
```

设置面板中暴露的选项会持久化到 `SettingsDataStore`，重启 App 后保持上次选择。

## 下载策略

- **v1**：下载 6 个权重文件（`.param` / `.bin` × 3）+ `SHA256SUMS.txt` 校验。
- **v2**：通过 release 根目录的 `model-assets.json` 清单按 `{tag, backbone, precision, engine}` 维度匹配资产并下载，使用清单内嵌的 `sha256` 校验。

GitHub 与 Gitee 互为 fallback，单个文件最多重试 3 次。

## NCNN 依赖

`shmtu_ocr` 依赖原生库 NCNN（已在父级 build 中通过 `scripts/install_lib.py` 下载到 `3rdparty/NCNN/`）以及 OpenCV Mobile。详见 [根 README 文档](../../CLAUDE.md#captchaocr) 与 [Android CLAUDE.md](../../CLAUDE.md)。

## 返回值

`OcrResult`：

- `result: Int` -- 算式结果
- `expr: String` -- 完整算式字符串
- `equalSymbol: Int` -- v1 时 `0` = 中文等号 / `1` = 标准等号；v2 时为 `-1`
- `operator: Int` -- 运算符类型编码
- `digit1: Int` / `digit2: Int` -- 两个数字

## 相关链接

- 根仓库 OCR 总览：[Documents/docs/ocr-model-versions.md](../../../Documents/docs/ocr-model-versions.md)
- 模型训练与导出：[shmtu-cas-ocr-model V2 文档](https://a645162.github.io/shmtu-cas-ocr-model/usage/v2-quickstart)
