package cn.edu.shmtu.terminal.android.ui.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    accountId: Long,
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var captchaInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(accountId) {
        viewModel.initialize(accountId)
    }

    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) {
            onLoginSuccess()
        }
    }

    LaunchedEffect(uiState.recognizedText) {
        uiState.recognizedText?.let {
            captchaInput = it
            viewModel.clearRecognizedText()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("输入验证码") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("返回")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.initialize(accountId) }) {
                        Text("刷新")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading && uiState.captchaImage == null) {
                CircularProgressIndicator()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "请输入下方验证码计算结果",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 验证码错误重试提示
                    if (uiState.isCaptchaRetry) {
                        LoginErrorBanner(
                            message = "上次验证码输入错误，已刷新验证码，请重新输入",
                            type = LoginErrorType.CAPTCHA
                        )
                    }

                    // 通用错误提示（非验证码重试场景）
                    val currentError = uiState.error
                    if (currentError != null && !uiState.isCaptchaRetry) {
                        LoginErrorBanner(
                            message = currentError,
                            type = uiState.errorType ?: LoginErrorType.UNKNOWN
                        )
                    }

                    uiState.captchaImage?.let { imageData ->
                        val bitmap = remember(imageData) {
                            android.graphics.BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                        }
                        bitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "验证码图片",
                                modifier = Modifier.height(80.dp)
                            )
                        }
                    }

                    TextButton(
                        onClick = { viewModel.recognizeCaptcha() },
                        enabled = uiState.captchaImage != null && !uiState.isRecognizing && !uiState.isLoading
                    ) {
                        if (uiState.isRecognizing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(16.dp)
                                    .width(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("识别验证码")
                        }
                    }

                    OutlinedTextField(
                        value = captchaInput,
                        onValueChange = { captchaInput = it },
                        label = { Text("计算结果（如：3+5=8 则输入 8）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                            }
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        TextButton(onClick = onBack) {
                            Text("取消")
                        }
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                if (captchaInput.isNotBlank()) {
                                    viewModel.submitCaptcha(captchaInput)
                                }
                            },
                            enabled = captchaInput.isNotBlank() && !uiState.isLoading
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .height(16.dp)
                                        .width(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("登录")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 根据错误类型差异化展示的内联错误横幅
 */
@Composable
private fun LoginErrorBanner(
    message: String,
    type: LoginErrorType
) {
    val (icon, containerColor, contentColor, iconTint) = when (type) {
        LoginErrorType.NETWORK -> Tuple4(
            Icons.Filled.SignalWifiOff,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.error
        )
        LoginErrorType.CAPTCHA -> Tuple4(
            Icons.Filled.ErrorOutline,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.error
        )
        LoginErrorType.PASSWORD -> Tuple4(
            Icons.Filled.ErrorOutline,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.error
        )
        LoginErrorType.ACCOUNT -> Tuple4(
            Icons.Filled.ErrorOutline,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.error
        )
        LoginErrorType.OCR -> Tuple4(
            Icons.Filled.WarningAmber,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiary
        )
        LoginErrorType.SERVER -> Tuple4(
            Icons.Filled.WarningAmber,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiary
        )
        LoginErrorType.UNKNOWN -> Tuple4(
            Icons.Filled.Info,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.secondary
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = containerColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor
        )
    }
}

/** 简单的四元组，用于解构颜色配置 */
private data class Tuple4<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
