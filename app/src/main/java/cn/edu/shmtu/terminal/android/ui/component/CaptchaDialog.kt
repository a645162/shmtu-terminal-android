package cn.edu.shmtu.terminal.android.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

@Composable
fun CaptchaDialog(
    captchaImageData: ByteArray?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("输入验证码") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (captchaImageData != null) {
                    val bitmap = remember(captchaImageData) {
                        BitmapFactory.decodeByteArray(captchaImageData, 0, captchaImageData.size)
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "验证码",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("计算结果") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (input.isNotBlank()) onConfirm(input) },
                enabled = input.isNotBlank()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
