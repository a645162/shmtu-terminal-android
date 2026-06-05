package cn.edu.shmtu.terminal.android.ui.settings

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch

private const val APP_GITHUB = "https://github.com/a645162/shmtu-terminal"
private const val APP_RELEASES = "https://github.com/a645162/shmtu-terminal/releases"
private const val APP_ISSUES = "https://github.com/a645162/shmtu-terminal/issues"

private data class RepoLink(
    val name: String,
    val description: String,
    val url: String
)

private object GitHubRepos {
    val all: List<RepoLink> = buildList {
        add(RepoLink(
            name = "shmtu-terminal",
            description = "主仓库:Android、Tauri、桌面端、数据库规则与文档",
            url = "https://github.com/a645162/shmtu-terminal"
        ))
        add(RepoLink(
            name = "shmtu-terminal-tauri",
            description = "Tauri v2 桌面客户端 (Rust + React/TypeScript)",
            url = "https://github.com/a645162/shmtu-terminal-tauri"
        ))
        add(RepoLink(
            name = "shmtu-terminal-desktop",
            description = ".NET 8 桌面客户端 (CAS/OCR 库 + WinUI)",
            url = "https://github.com/a645162/shmtu-terminal-desktop"
        ))
        add(RepoLink(
            name = "shmtu-terminal-android",
            description = "Android 客户端 (Kotlin + Jetpack Compose)",
            url = "https://github.com/a645162/shmtu-terminal-android"
        ))

        add(RepoLink(
            name = "shmtu-cas-ocr-server",
            description = "C++ OCR 服务 (Drogon + ncnn, CPU/Vulkan)",
            url = "https://github.com/a645162/shmtu-cas-ocr-server"
        ))
        add(RepoLink(
            name = "shmtu-service-monitor",
            description = "服务监控",
            url = "https://github.com/a645162/shmtu-service-monitor"
        ))
        add(RepoLink(
            name = "shmtu-server-unofficial",
            description = "Spring Boot 后端 (Kotlin JVM, 引用 shmtu-cas-kotlin)",
            url = "https://github.com/a645162/shmtu-server-unofficial"
        ))
        add(RepoLink(
            name = "smu-badminton",
            description = "羽毛球场预约系统 (FastAPI + NCNN OCR)",
            url = "https://github.com/YahelLiu/smu-badminton"
        ))

        add(RepoLink(
            name = "shmtu-cas-ocr-model",
            description = "验证码 OCR 模型与训练代码",
            url = "https://github.com/a645162/shmtu-cas-ocr-model"
        ))
        add(RepoLink(
            name = "shmtu-cas-python",
            description = "CAS 认证 Python 客户端库",
            url = "https://github.com/a645162/shmtu-cas-python"
        ))
        add(RepoLink(
            name = "shmtu-cas-kotlin",
            description = "CAS 认证 Kotlin/JVM 库 (Android + Server 共享)",
            url = "https://github.com/a645162/shmtu-cas-kotlin"
        ))
        add(RepoLink(
            name = "shmtu-cas-rs",
            description = "CAS 认证 + OCR Rust 库 (Tauri 端嵌套使用)",
            url = "https://github.com/a645162/shmtu-cas-rs"
        ))
        add(RepoLink(
            name = "shmtu-dotnet-lib",
            description = "CAS/OCR .NET 库 (桌面端嵌套使用)",
            url = "https://github.com/a645162/shmtu-dotnet-lib"
        ))
        add(RepoLink(
            name = "shmtu-cas-ocr-crx",
            description = "浏览器扩展 (Chrome, CAS 验证码自动识别)",
            url = "https://github.com/a645162/shmtu-cas-ocr-crx"
        ))
    }
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    embedded: Boolean = false
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val packageInfo = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: "unknown"
    val versionCode = packageInfo?.longVersionCode?.toString() ?: "unknown"

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun copyText(label: String, value: String) {
        scope.launch {
            clipboard.setClipEntry(ClipData.newPlainText(label, value).toClipEntry())
        }
    }

    SettingsDetailScreen(
        title = "关于",
        onBack = onBack,
        embedded = embedded
    ) {
        SettingsCard(emphasized = true) {
            Text("海事终端", style = MaterialTheme.typography.headlineSmall)
            Text(
                "上海海事大学校园服务客户端。当前 Android 端聚焦账单同步、位置分类、统计分析、OCR 与规则调试。",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SettingsCard {
            Text("应用信息")
            KeyValueRow("应用 ID", context.packageName)
            KeyValueRow("版本", "v$versionName ($versionCode)")
            KeyValueRow("渠道", "Android")
            KeyValueRow("作者", "孔昊旻 (Haomin Kong)")
            KeyValueRow("许可证", "MIT License")
        }

        SettingsCard {
            Text("主仓库")
            Text(
                "Android、Tauri、桌面端、数据库规则与文档都在同一个主仓库维护。反馈规则问题、查看提交和发布版本都以这里为准。",
                style = MaterialTheme.typography.bodyMedium
            )
            LinkRow(
                title = "GitHub 仓库",
                value = APP_GITHUB,
                onOpen = { openUrl(APP_GITHUB) },
                onCopy = { copyText("github-repo", APP_GITHUB) }
            )
            LinkRow(
                title = "Releases",
                value = APP_RELEASES,
                onOpen = { openUrl(APP_RELEASES) },
                onCopy = { copyText("github-releases", APP_RELEASES) }
            )
            LinkRow(
                title = "Issues",
                value = APP_ISSUES,
                onOpen = { openUrl(APP_ISSUES) },
                onCopy = { copyText("github-issues", APP_ISSUES) }
            )
        }

        SettingsCard {
            Text("所有子仓库 (${GitHubRepos.all.size})")
            Text(
                "项目按客户端 / 服务端 / 共享库与模型 / 工具与浏览器扩展拆分维护。下列链接覆盖主仓库下所有 git 子仓库,含嵌套子模块 (Tauri 内部的 shmtu-cas-rs、Android 与 Server 共享的 shmtu-cas-kotlin、桌面端内部的 shmtu-dotnet-lib)。",
                style = MaterialTheme.typography.bodyMedium
            )
            GitHubRepos.all.forEach { repo ->
                LinkRow(
                    title = repo.name,
                    subtitle = repo.description,
                    value = repo.url,
                    onOpen = { openUrl(repo.url) },
                    onCopy = { copyText("github-${repo.name}", repo.url) }
                )
            }
        }

        SettingsCard {
            Text("反馈建议")
            Text(
                "如果是规则误判或位置未命中,优先在“分类规则设置”里重算历史账单并复制未命中诊断,再附上这里的 GitHub Issues 链接提交反馈。",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "建议反馈内容:targetUser 原文、建议的 building/room、截图或原始账单样本。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun KeyValueRow(title: String, value: String) {
    Text(title, style = MaterialTheme.typography.bodyMedium)
    Text(
        value,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LinkRow(
    title: String,
    value: String,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    subtitle: String? = null
) {
    Text(title, style = MaterialTheme.typography.bodyMedium)
    if (subtitle != null) {
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Text(
        value,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onOpen) { Text("打开") }
        TextButton(onClick = onCopy) { Text("复制") }
    }
}
