package cn.edu.shmtu.cas.ocr

/**
 * 在客户端层面描述一个 OCR v2 模型条目 (来自 model-assets.json 的 `models[]`)。
 *
 * 新版 manifest (schema_version >= 2) 使用按模型分组、引擎/精度嵌套的
 * artifacts 结构:每个模型自带一个 `artifacts` 对象,key 为 engine
 * (pytorch / onnx / ncnn),value 又是一个按 precision (fp32 / fp16) 嵌套
 * 的 `OcrArtifactInfo`。本文件的数据类即对应这种分层结构。
 */

/**
 * 一个 OCR 模型的训练/验证指标。所有字段均为可选,可能缺失。
 *
 * 字段语义:
 *  - [valAccExpression] / [testAccExpression]: 验证集/测试集上 CAS
 *    验证码数学表达式 (例如 "3+5=8") 的整体识别准确率。
 *  - [valLoss] / [testLoss]: 验证集/测试集的 loss 值。
 */
data class OcrModelMetrics(
    val valAccExpression: Double?,
    val valLoss: Double?,
    val testAccExpression: Double?,
    val testLoss: Double?,
)

/**
 * 描述 manifest 中一个具体产物的文件列表。
 *
 * 通常对应一个 `engine` + `precision` + `format` 组合,例如
 * `pytorch / fp32 / checkpoint` 或 `ncnn / fp32 / ncnn_files`。
 */
data class OcrArtifactInfo(
    val engine: String,
    val precision: String,
    val format: String?,
    val files: List<OcrAssetFile>,
)

/**
 * 描述一个产物的单个文件条目,对应 manifest `files[]` 数组中的一项。
 */
data class OcrAssetFile(
    val path: String,
    val releaseAssetName: String,
    val sha256: String?,
)

/**
 * 描述一个 OCR 模型 (一组互相关联的 artifacts)。
 *
 * 关键字段:
 *  - [assetStem]: 模型文件名的词干 (用于在文件系统中定位和匹配 v2 文件,
 *    例如 `mobilenet_v3_small.trislot_decoder.v2_0`)。
 *  - [backbone]: 主干网络名称 (例如 `mobilenet_v3_small`)。
 *  - [family]: 模型族 (例如 `trislot_decoder`)。
 *  - [supportedBackbones]: 该模型条目声称可兼容的 backbone 列表。
 *  - [artifactsByEngine]: 按 engine -> precision -> artifact 分组的 artifacts。
 *  - [modelSizeM]: 模型参数量 (单位:百万),可能为 null。
 *  - [metrics]: 训练/验证/测试指标,可能为 null。
 */
data class OcrModelInfo(
    val assetStem: String,
    val displayName: String,
    val backbone: String,
    val version: String,
    val family: String,
    val modelSizeM: Double?,
    val metrics: OcrModelMetrics?,
    val supportedBackbones: List<String>,
    val artifactsByEngine: Map<String, Map<String, OcrArtifactInfo>>,
)

/**
 * 完整解析后的 v2 release manifest。
 *
 * - [schemaVersion]: manifest 的 schema 版本。当前实现假设 `>= 2`。
 * - [modelCount]: 模型条目数,通常等于 [models].size。
 * - [modelList]: 模型 assetStem 列表,顺序与 [models] 一致。
 * - [models]: 按模型分组的清单。
 */
data class V2ReleaseManifest(
    val schemaVersion: Int,
    val modelCount: Int,
    val modelList: List<String>,
    val models: List<OcrModelInfo>,
)
