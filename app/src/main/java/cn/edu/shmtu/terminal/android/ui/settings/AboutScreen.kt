package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding)
        ) {
            ListItem(
                headlineContent = { Text("应用名称") },
                supportingContent = { Text("海事终端") }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("版本") },
                supportingContent = { Text("v1.0.0") }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("作者") },
                supportingContent = {
                    Text(
                        "孔昊旻(Haomin Kong)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("开源协议") },
                supportingContent = { Text("MIT License") }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("技术支持") },
                supportingContent = { Text("上海海事大学") }
            )
        }
    }
}
