package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var useLocalOcr by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("设置") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding)
        ) {
            ListItem(
                headlineContent = { Text("OCR 设置") },
                supportingContent = { Text("本地模型 / 远端服务器") }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("使用本地 OCR 模型") },
                supportingContent = { Text("优先使用本地 NCNN 模型识别验证码") },
                trailingContent = {
                    Switch(
                        checked = useLocalOcr,
                        onCheckedChange = { useLocalOcr = it }
                    )
                }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("远端 OCR 服务器") },
                supportingContent = { Text("127.0.0.1:21601") }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("关于") },
                supportingContent = { Text("海事终端 v1.0") }
            )
        }
    }
}
