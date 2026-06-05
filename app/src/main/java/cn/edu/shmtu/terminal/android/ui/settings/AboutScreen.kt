package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    embedded: Boolean = false
) {
    SettingsDetailScreen(
        title = "关于",
        onBack = onBack,
        embedded = embedded
    ) {
        SettingsCard {
            Text("应用名称")
            Text("海事终端", style = MaterialTheme.typography.titleLarge)
        }
        SettingsCard {
            Text("版本")
            Text("v1.0.0", style = MaterialTheme.typography.titleLarge)
        }
        SettingsCard {
            Text("作者")
            Text("孔昊旻 (Haomin Kong)", style = MaterialTheme.typography.titleMedium)
        }
        SettingsCard {
            Text("开源协议")
            Text("MIT License", style = MaterialTheme.typography.titleMedium)
        }
        SettingsCard {
            Text("技术支持")
            Text("上海海事大学", style = MaterialTheme.typography.titleMedium)
        }
    }
}
