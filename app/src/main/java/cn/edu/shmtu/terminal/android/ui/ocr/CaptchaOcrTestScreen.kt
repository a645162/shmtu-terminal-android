package cn.edu.shmtu.terminal.android.ui.ocr

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN_Model
import cn.edu.shmtu.terminal.android.data.local.datastore.OcrServerType
import cn.edu.shmtu.terminal.android.ui.settings.SettingsCard
import cn.edu.shmtu.terminal.android.ui.settings.SettingsDetailScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptchaOcrTestScreen(
    onBack: () -> Unit,
    viewModel: CaptchaOcrTestViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState
    val imageBytes = uiState.imageBytes
    val ocrResult = uiState.result
    val verification = uiState.verification
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.loadImage(uri)
        }
    }

    SettingsDetailScreen(
        title = "验证码识别测试",
        onBack = onBack,
        actions = {
            IconButton(onClick = viewModel::loadDefaultsFromSettings) {
                Icon(Icons.Filled.Refresh, contentDescription = "从设置恢复默认")
            }
        },
    ) {
        SettingsCard(emphasized = true) {
            Text("验证码测试台", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "支持四种测试动作：手动输入、本地 OCR、远程 OCR、云端验证。"
                    + " 本地测试直接走主 app 当前接入的 NCNN 推理代码，v2 会调用 `predict_validate_code_v2`。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        SettingsCard {
            Text("图片来源", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = viewModel::refreshCloudChallenge,
                    enabled = !uiState.isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    if (uiState.isBusy && uiState.busyAction == OcrBusyAction.FetchingChallenge) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("刷新云端验证码", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                OutlinedButton(
                    onClick = { imagePicker.launch(arrayOf("image/*")) },
                    enabled = !uiState.isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.ImageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("选择本地图片", modifier = Modifier.padding(start = 8.dp))
                }
            }

            SourceBadge(uiState.imageSource, uiState.challengeExecution != null)

            if (imageBytes == null) {
                EmptyPreview()
            } else {
                val bitmap = remember(imageBytes) {
                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "验证码预览",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text("图片预览失败", color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(uiState.imageLabel.ifBlank { "当前图片" }, fontWeight = FontWeight.Medium)
                Text(
                    uiState.imageMeta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingsCard {
            Text("手动输入与云端校验", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = uiState.manualAnswer,
                onValueChange = viewModel::setManualAnswer,
                label = { Text("验证码答案") },
                placeholder = { Text("例如 8") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isBusy,
            )
            Text(
                "云端校验会把当前答案提交给真实 challenge。返回“密码错误”代表验证码正确。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = viewModel::verifyCurrentAnswer,
                enabled = !uiState.isBusy && uiState.manualAnswer.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isBusy && uiState.busyAction == OcrBusyAction.CloudVerify) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("云端验证当前答案")
                }
            }
        }

        SettingsCard {
            Text("本地 OCR", style = MaterialTheme.typography.titleMedium)
            Text(
                "这里的本地参数只影响测试页，不会自动覆盖设置页。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = uiState.localModelVersion == SHMTU_NCNN_Model.ModelVersion.V1,
                    onClick = { viewModel.setLocalModelVersion(SHMTU_NCNN_Model.ModelVersion.V1) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("v1") }
                SegmentedButton(
                    selected = uiState.localModelVersion == SHMTU_NCNN_Model.ModelVersion.V2,
                    onClick = { viewModel.setLocalModelVersion(SHMTU_NCNN_Model.ModelVersion.V2) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("v2") }
            }
            if (uiState.localModelVersion == SHMTU_NCNN_Model.ModelVersion.V2) {
                OutlinedTextField(
                    value = uiState.localBackbone,
                    onValueChange = viewModel::setLocalBackbone,
                    label = { Text("Backbone") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isBusy,
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PrecisionChoice(
                        label = "fp16",
                        selected = uiState.localPrecision == "fp16",
                        onClick = { viewModel.setLocalPrecision("fp16") },
                    )
                    PrecisionChoice(
                        label = "fp32",
                        selected = uiState.localPrecision == "fp32",
                        onClick = { viewModel.setLocalPrecision("fp32") },
                    )
                }
                Text(
                    "当前设置默认 tag: ${uiState.localTag.ifBlank { "自动 / 最新" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = viewModel::runLocalOcr,
                enabled = !uiState.isBusy && uiState.imageBytes != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isBusy && uiState.busyAction == OcrBusyAction.LocalOcr) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("执行本地识别")
                }
            }
        }

        SettingsCard {
            Text("远程 OCR", style = MaterialTheme.typography.titleMedium)
            Text(
                "远程参数同样是测试页独立值，可直接覆盖设置里的远程地址做临时实验。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = uiState.remoteServerType == OcrServerType.HTTP,
                    onClick = { viewModel.setRemoteServerType(OcrServerType.HTTP) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("HTTP") }
                SegmentedButton(
                    selected = uiState.remoteServerType == OcrServerType.TCP,
                    onClick = { viewModel.setRemoteServerType(OcrServerType.TCP) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("TCP") }
            }
            OutlinedTextField(
                value = uiState.remoteServerAddress,
                onValueChange = viewModel::setRemoteServerAddress,
                label = {
                    Text(if (uiState.remoteServerType == OcrServerType.HTTP) "HTTP Base URL" else "TCP host:port")
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isBusy,
                singleLine = true,
            )
            Button(
                onClick = viewModel::runRemoteOcr,
                enabled = !uiState.isBusy && uiState.imageBytes != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isBusy && uiState.busyAction == OcrBusyAction.RemoteOcr) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("执行远程识别")
                }
            }
        }

        SettingsCard {
            Text("结果面板", style = MaterialTheme.typography.titleMedium)
            when {
                verification != null -> {
                    VerificationBlock(verification)
                    ocrResult?.let {
                        HorizontalDivider()
                        ResultBlock(it)
                    }
                }
                ocrResult != null -> ResultBlock(ocrResult)
                else -> Text(
                    "本地或远程识别完成后，会自动把答案写回手动输入框，方便继续做云端验证。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            uiState.statusMessage?.let {
                HorizontalDivider()
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SourceBadge(source: CaptchaImageSource, cloudReady: Boolean) {
    val text = when (source) {
        CaptchaImageSource.None -> "尚未载入图片"
        CaptchaImageSource.File -> "本地图片，仅支持识别测试"
        CaptchaImageSource.Cloud -> if (cloudReady) "云端 challenge，可继续做正确性验证" else "云端图片，当前 challenge 已消费"
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun PrecisionChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun ResultBlock(result: OcrTestResult) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ResultLine("来源", result.source)
        ResultLine("表达式", result.expression)
        ResultLine("答案", result.answer)
        ResultLine("耗时", "${result.durationMs} ms")
        Text(
            result.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VerificationBlock(result: CloudVerificationResult) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            result.title,
            style = MaterialTheme.typography.titleSmall,
            color = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            result.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ResultLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EmptyPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "点击“刷新云端验证码”或“选择本地图片”开始测试",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
