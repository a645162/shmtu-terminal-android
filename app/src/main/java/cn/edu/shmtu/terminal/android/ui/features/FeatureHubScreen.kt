package cn.edu.shmtu.terminal.android.ui.features

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import cn.edu.shmtu.terminal.android.R

private data class FeatureItem(
    val title: String,
    val description: String,
    val iconRes: Int,
    val available: Boolean,
    val route: String? = null
)

private val features = listOf(
    FeatureItem("账单统计", "多维度账单分析与可视化", R.drawable.ic_bill, true, "bill_statistics"),
    FeatureItem("数据传输", "导入导出账单数据与快照管理", R.drawable.ic_bill, true, "data_transfer"),
    FeatureItem("热水查询", "宿舍楼热水温度与水位（从账号管理进入）", R.drawable.ic_home, false),
    FeatureItem("电费查询", "宿舍电费余额与用电记录", R.drawable.ic_home, false),
    FeatureItem("课表查询", "本学期课程表与上课提醒", R.drawable.ic_favorite, false),
    FeatureItem("成绩查询", "历年成绩与GPA统计", R.drawable.ic_account_box, false),
    FeatureItem("研究生系统", "研究生教务系统功能", R.drawable.ic_account_box, false),
    FeatureItem("场地预约", "校园场地在线预约", R.drawable.ic_home, false)
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
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(features) { feature ->
                FeatureCard(
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
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun FeatureCard(
    feature: FeatureItem,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (feature.available) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (feature.available)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(feature.iconRes),
                contentDescription = feature.title,
                modifier = Modifier.size(40.dp),
                tint = if (feature.available)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (feature.available)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = feature.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (feature.available)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
            if (!feature.available) {
                Text(
                    text = "敬请期待",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
