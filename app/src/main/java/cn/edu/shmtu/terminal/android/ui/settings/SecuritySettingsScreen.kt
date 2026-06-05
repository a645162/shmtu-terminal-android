package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SecuritySettingsScreen(
    onBack: () -> Unit,
    embedded: Boolean = false
) {
    val store = LocalFeatureStore.current
    val enabled = store.enableStartupProtection.value
    val hash = store.startupPasswordHash.value
    var showPasswordDialog by remember { mutableStateOf(false) }

    SettingsDetailScreen(
        title = "安全设置",
        onBack = onBack,
        embedded = embedded
    ) {
        SettingsCard {
            Text("启动保护")
            Text("启用后，每次打开应用都要先输入启动密码。", style = MaterialTheme.typography.bodyMedium)
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (enabled) "当前已启用" else "当前未启用",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        store.setEnableStartupProtection(it)
                        if (it && hash.isNullOrBlank()) {
                            showPasswordDialog = true
                        }
                    }
                )
            }
        }

        SettingsCard(emphasized = enabled) {
            Text("启动密码")
            Text(
                if (hash.isNullOrBlank()) "尚未设置启动密码。" else "已设置密码摘要：${hash.take(8)}...",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = { showPasswordDialog = true }) {
                Text(if (hash.isNullOrBlank()) "设置密码" else "修改密码")
            }
            if (!hash.isNullOrBlank()) {
                TextButton(onClick = { store.setStartupPasswordHash(null) }) {
                    Text("清除密码")
                }
            }
        }

        SettingsCard {
            Text("说明")
            Text(
                "启动密码校验由 StartupLockActivity 在主流程前拦截处理。忘记密码时，仍可在解锁页临时关闭启动保护。",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    if (showPasswordDialog) {
        StartupPasswordDialog(
            onConfirm = { password ->
                store.setStartupPasswordHash(hashPassword(password))
                store.setEnableStartupProtection(true)
                showPasswordDialog = false
            },
            onDismiss = {
                showPasswordDialog = false
                if (store.startupPasswordHash.value.isNullOrBlank()) {
                    store.setEnableStartupProtection(false)
                }
            }
        )
    }
}

@Composable
private fun StartupPasswordDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = password.isNotBlank() && password == confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置启动密码") },
        text = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("输入密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("确认密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                if (confirm.isNotEmpty() && password != confirm) {
                    Text("两次输入不一致", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = valid
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun hashPassword(input: String): String {
    val bytes = java.security.MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}
