package cn.edu.shmtu.terminal.android.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cn.edu.shmtu.terminal.android.ui.theme.ShmtuterminalandroidTheme

/**
 * 启动锁 Activity - 拦截 MainActivity, 校验启动密码。
 * 流程: 启动时读 feature_settings SharedPreferences 的 startup_protection + startup_password_hash;
 *   禁用或未设则直接放行; 否则弹密码输入。
 * Manifest 需注册为 Theme.Translucent.NoTitleBar 以保持透明。
 */
class StartupLockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sp = getSharedPreferences("feature_settings", Context.MODE_PRIVATE)
        val enabled = sp.getBoolean("startup_protection", false)
        val expectedHash = sp.getString("startup_password_hash", null)

        if (!enabled || expectedHash.isNullOrBlank()) {
            goToMain()
            return
        }

        setContent {
            ShmtuterminalandroidTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    StartupLockScreen(expectedHash = expectedHash, onUnlocked = { goToMain() })
                }
            }
        }
    }

    private fun goToMain() {
        val intent = Intent(this, cn.edu.shmtu.terminal.android.MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}

@Composable
private fun StartupLockScreen(expectedHash: String, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val sp = remember { context.getSharedPreferences("feature_settings", Context.MODE_PRIVATE) }

    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("海事终端", style = MaterialTheme.typography.headlineMedium)
            Text("请输入启动密码", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("启动密码") }
            )
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    val computed = hash(input)
                    if (computed == expectedHash) onUnlocked() else error = "密码错误"
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("解锁") }
            Button(
                onClick = {
                    sp.edit().putBoolean("startup_protection", false).apply()
                    Toast.makeText(context, "已临时关闭启动保护", Toast.LENGTH_SHORT).show()
                    onUnlocked()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("忘记密码 (临时关闭)") }
        }
    }
}

private fun hash(input: String): String {
    val bytes = java.security.MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}
