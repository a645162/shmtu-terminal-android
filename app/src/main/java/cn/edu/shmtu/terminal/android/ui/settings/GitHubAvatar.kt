package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage

/**
 * Lightweight composable that loads a GitHub avatar URL via Coil
 * and displays it in a circle. Falls back to initials on failure.
 *
 * 使用 Coil 替代手写的 OkHttp + BitmapFactory 流程,自动处理:
 * - 内存/磁盘缓存
 * - 加载/失败占位
 * - 跨配置变更保持
 */
@Composable
fun GitHubAvatar(
    username: String,
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val initials = displayName.split(" ")
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifEmpty { "?" }

    val avatarUrl = "https://avatars.githubusercontent.com/$username?s=128"

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        // 首字母 fallback(底层)——加载中/失败时可见
        Text(
            text = initials,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        // Coil 异步加载头像(顶层)——加载成功时盖在首字母之上
        SubcomposeAsyncImage(
            model = avatarUrl,
            contentDescription = displayName,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            loading = { /* 占位:底层首字母可见 */ },
            error = { /* 失败:底层首字母可见 */ },
        )
    }
}
