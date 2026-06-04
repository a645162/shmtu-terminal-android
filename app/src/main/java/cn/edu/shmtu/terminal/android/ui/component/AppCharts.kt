package cn.edu.shmtu.terminal.android.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.koalaplot.core.Symbol
import io.github.koalaplot.core.line.LinePlot2
import io.github.koalaplot.core.pie.DefaultSlice
import io.github.koalaplot.core.pie.PieChart
import io.github.koalaplot.core.style.LineStyle
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.xygraph.AxisContent
import io.github.koalaplot.core.xygraph.CategoryAxisModel
import io.github.koalaplot.core.xygraph.DefaultPoint
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.rememberAxisStyle
import io.github.koalaplot.core.xygraph.rememberFloatLinearAxisModel
import io.github.koalaplot.core.xygraph.rememberGridStyle
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

data class AppLineSeries(
    val color: Color,
    val values: List<Float>,
)

data class AppDonutSlice(
    val label: String,
    val value: Float,
    val color: Color,
)

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
fun AppLineChart(
    labels: List<String>,
    series: List<AppLineSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
) {
    val pointCount = minOf(
        labels.size,
        series.minOfOrNull { it.values.size } ?: 0,
    )
    if (pointCount <= 0 || series.isEmpty()) return

    val trimmedLabels = remember(labels, pointCount) { labels.take(pointCount) }
    val trimmedSeries = remember(series, pointCount) {
        series.map { it.copy(values = it.values.take(pointCount)) }
    }
    val maxValue = trimmedSeries.maxOf { line -> line.values.maxOrNull() ?: 0f }.coerceAtLeast(1f)
    val axisMax = remember(maxValue) { computeAxisMax(maxValue) }
    val axisColor = MaterialTheme.colorScheme.outline
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
    val xAxisStyle = rememberAxisStyle(color = axisColor)
    val yAxisStyle = rememberAxisStyle(color = axisColor)

    XYGraph(
        xAxisModel = remember(trimmedLabels) { CategoryAxisModel(trimmedLabels) },
        yAxisModel = rememberFloatLinearAxisModel(0f..axisMax, minimumMajorTickSpacing = 40.dp, minorTickCount = 0),
        xAxisContent = AxisContent(
            labels = { value: String ->
                Text(
                    text = value,
                    color = axisColor,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            },
            title = {},
            style = xAxisStyle,
        ),
        yAxisContent = AxisContent(
            labels = { value: Float ->
                Text(
                    text = "¥%,.0f".format(value),
                    color = axisColor,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(end = 4.dp),
                )
            },
            title = {},
            style = yAxisStyle,
        ),
        gridStyle = rememberGridStyle(
            horizontalMajorStyle = LineStyle(brush = SolidColor(gridColor), strokeWidth = 1.dp),
            horizontalMinorStyle = null,
            verticalMajorStyle = null,
            verticalMinorStyle = null,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        trimmedSeries.forEach { line ->
            LinePlot2(
                data = trimmedLabels.mapIndexed { index, label ->
                    DefaultPoint(label, line.values[index])
                },
                lineStyle = LineStyle(
                    brush = SolidColor(line.color),
                    strokeWidth = 2.dp,
                ),
                symbol = {
                    Symbol(
                        modifier = Modifier.size(7.dp),
                        shape = CircleShape,
                        fillBrush = SolidColor(line.color),
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
fun AppDonutChart(
    slices: List<AppDonutSlice>,
    modifier: Modifier = Modifier,
    holeTitle: String = "总额",
    totalFormatter: (Float) -> String = { "¥%,.2f".format(it) },
) {
    if (slices.isEmpty()) return

    val safeValues = remember(slices) { slices.map { max(it.value, 0f) } }
    val totalValue = remember(safeValues) { safeValues.sum().coerceAtLeast(0f) }

    PieChart(
        values = safeValues,
        modifier = modifier,
        slice = { index ->
            DefaultSlice(
                color = slices[index].color,
                border = BorderStroke(2.dp, slices[index].color.copy(alpha = 0.82f)),
            )
        },
        label = {},
        labelConnector = {},
        holeSize = 0.68f,
        holeContent = { contentPadding: PaddingValues ->
            DonutHoleContent(
                title = holeTitle,
                value = totalFormatter(totalValue),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
        },
        maxPieDiameter = Dp.Infinity,
        forceCenteredPie = true,
    )
}

@Composable
private fun DonutHoleContent(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun computeAxisMax(maxValue: Float): Float {
    if (maxValue <= 1f) return 1f
    val magnitude = 10f.pow(floor(log10(maxValue.toDouble())).toFloat())
    return ceil(maxValue / magnitude) * magnitude
}
