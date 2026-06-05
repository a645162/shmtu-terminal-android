package cn.edu.shmtu.terminal.android.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun UpdateSettingsScreen(
    onBack: () -> Unit,
    embedded: Boolean = false
) {
    val store = LocalFeatureStore.current
    val context = LocalContext.current

    SettingsDetailScreen(
        title = "更新设置",
        onBack = onBack,
        embedded = embedded
    ) {
        SettingsCard {
            Text("自动检查更新")
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "开启后会按设定周期自动检查版本。",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = store.autoCheckUpdate.value,
                    onCheckedChange = { store.setAutoCheckUpdate(it) }
                )
            }
            if (store.autoCheckUpdate.value) {
                Text("检查间隔：${store.checkIntervalHours.value} 小时", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = store.checkIntervalHours.value.toFloat(),
                    onValueChange = { store.setCheckIntervalHours(it.toInt()) },
                    valueRange = 1f..168f
                )
            }
        }

        SettingsCard(emphasized = true) {
            Text("手动查看版本")
            Text("在浏览器中打开 GitHub Releases 页面。", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/a645162/shmtu-terminal/releases"))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }) {
                Text("打开发布页")
            }
        }
    }
}
