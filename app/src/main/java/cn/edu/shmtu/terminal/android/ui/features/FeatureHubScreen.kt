package cn.edu.shmtu.terminal.android.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shower
import androidx.compose.material.icons.outlined.Stadium
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class FeatureItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val available: Boolean,
    val accent: Color,
    val route: String? = null
)

private val features = listOf(
    FeatureItem("账单统计", "多维度账单分析、趋势和异常提醒", Icons.Outlined.BarChart, true, Color(0xFF1E88E5), "bill_statistics"),
    FeatureItem("数据传输", "导入导出、快照和跨端迁移", Icons.Outlined.ImportExport, true, Color(0xFF00897B), "data_transfer"),
    FeatureItem("热水查询", "宿舍热水温度与水位，账号入口进入", Icons.Outlined.Shower, false, Color(0xFF00ACC1)),
    FeatureItem("电费查询", "电费余额与宿舍用电走势", Icons.Outlined.Bolt, false, Color(0xFFFB8C00)),
    FeatureItem("课表查询", "课表总览与课程提醒", Icons.Outlined.CalendarMonth, false, Color(0xFF8E24AA)),
    FeatureItem("成绩查询", "成绩、学分和 GPA 统计", Icons.Outlined.School, false, Color(0xFF3949AB)),
    FeatureItem("研究生系统", "研究生教务相关入口", Icons.Outlined.AccountBalance, false, Color(0xFF6D4C41)),
    FeatureItem("场地预约", "校园场地预约和记录查询", Icons.Outlined.Stadium, false, Color(0xFFD81B60))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureHubScreen(
    onNavigateToBillStatistics: () -> Unit,
    onNavigateToDataTransfer: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("功能大全") }
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                FeatureHeroCard()
            }
            items(features) { feature ->
                FeatureTile(
                    feature = feature,
                    onClick = {
                        if (feature.available) {
                            when (feature.route) {
                                "bill_statistics" -> onNavigateToBillStatistics()
                                "data_transfer" -> onNavigateToDataTransfer()
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FeatureHeroCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF0F5BD8),
                            Color(0xFF2F80ED),
                            Color(0xFF5EA8FF)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "校园服务入口",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "把高频功能集中在一页里。已上线入口优先突出，未开放功能保持可见但不过度干扰。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroPill("2 个已上线")
                    HeroPill("6 个规划中")
                }
            }
        }
    }
}

@Composable
private fun HeroPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}

@Composable
private fun FeatureTile(
    feature: FeatureItem,
    onClick: () -> Unit
) {
    val enabledBg = Brush.verticalGradient(
        listOf(
            feature.accent.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.surface
        )
    )
    val disabledBg = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            MaterialTheme.colorScheme.surface
        )
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .then(if (feature.available) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        tonalElevation = if (feature.available) 2.dp else 0.dp,
        shadowElevation = if (feature.available) 6.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(if (feature.available) enabledBg else disabledBg)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (feature.available) feature.accent.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = feature.icon,
                        contentDescription = feature.title,
                        tint = if (feature.available) feature.accent else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                }
                StatusTag(
                    text = if (feature.available) "已上线" else "规划中",
                    containerColor = if (feature.available) feature.accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (feature.available) feature.accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (feature.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = feature.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (feature.available) "立即进入" else "暂不可用",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (feature.available) feature.accent else MaterialTheme.colorScheme.outline
                )
                if (feature.available) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(feature.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowOutward,
                            contentDescription = null,
                            tint = feature.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTag(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
