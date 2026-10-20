package com.madhu.expensemanager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

data class SummaryRow(val cat: Cat, val sum: Double)

fun buildSummary(list: List<Tx>, cats: List<Cat>): List<SummaryRow> {
    val map = LinkedHashMap<String, SummaryRow>()
    list.filter { it.type == TxType.EXPENSE }.forEach { t ->
        val c = parseCat(t.category, cats)
        val cur = map[c.name]
        map[c.name] = SummaryRow(cur?.cat ?: c, (cur?.sum ?: 0.0) + t.amount)
    }
    return map.values.sortedByDescending { it.sum }
}

/** Simple pill-style toggle used for Week/Month/All, Pie/Bars, Expense/Income and payment method. */
@Composable
fun SegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    track: Color,
    selectedBg: Color,
    selectedFg: Color,
    unselectedFg: Color,
    modifier: Modifier = Modifier,
    selectedFgFor: ((Int) -> Color)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(track)
            .padding(4.dp)
    ) {
        items.forEachIndexed { i, label ->
            val sel = i == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (sel) selectedBg else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (sel) (selectedFgFor?.invoke(i) ?: selectedFg) else unselectedFg,
                )
            }
        }
    }
}

@Composable
fun SummaryCard(
    rows: List<SummaryRow>,
    view: String,
    onView: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val total = rows.sumOf { it.sum }
    var open by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<String?>(null) }
    val sel = selected?.takeIf { s -> rows.any { it.cat.name == s } }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = cs.surfaceVariant,
        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { open = !open },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Spending by category", fontWeight = FontWeight.Bold, color = cs.onSurface)
                Text(if (open) "▾" else "▸", color = cs.onSurfaceVariant)
            }

            if (open && total > 0) {
                Spacer(Modifier.height(16.dp))
                SegmentedControl(
                    items = listOf("Pie", "Bars"),
                    selectedIndex = if (view == "bars") 1 else 0,
                    onSelect = { onView(if (it == 1) "bars" else "pie") },
                    track = cs.onSurface.copy(alpha = 0.08f),
                    selectedBg = cs.background,
                    selectedFg = cs.primary,
                    unselectedFg = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                if (view == "bars") {
                    BarList(rows, total)
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        DonutChart(rows, total, sel) { selected = it }
                    }
                    Spacer(Modifier.height(20.dp))
                    Legend(rows, total, sel) { name -> selected = if (selected == name) null else name }
                }
            }
        }
    }
}

@Composable
private fun DonutChart(
    rows: List<SummaryRow>,
    total: Double,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val track = cs.onSurface.copy(alpha = 0.08f)
    val selRow = rows.firstOrNull { it.cat.name == selected }

    Box(
        Modifier
            .size(220.dp)
            .pointerInput(rows, selected) {
                detectTapGestures { off ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val dx = off.x - cx
                    val dy = off.y - cy
                    val dist = hypot(dx, dy)
                    val outer = size.width / 2f
                    val inner = outer * 0.55f
                    if (dist < inner || dist > outer) {
                        onSelect(null)
                        return@detectTapGestures
                    }
                    var ang = Math.toDegrees(atan2(dy, dx).toDouble()) + 90.0
                    if (ang < 0) ang += 360.0
                    var acc = 0.0
                    for (r in rows) {
                        acc += r.sum / total * 360.0
                        if (ang <= acc) {
                            onSelect(if (selected == r.cat.name) null else r.cat.name)
                            return@detectTapGestures
                        }
                    }
                    onSelect(null)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeW = size.minDimension * 0.14f
            val maxStroke = strokeW * 1.15f
            val arcSize = Size(size.width - maxStroke, size.height - maxStroke)
            val topLeft = Offset(maxStroke / 2f, maxStroke / 2f)

            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW),
            )

            var start = -90f
            rows.forEach { r ->
                val sweep = (r.sum / total * 360.0).toFloat()
                val gap = if (rows.size > 1) 1.5f else 0f
                val isSel = r.cat.name == selected
                val dim = selected != null && !isSel
                drawArc(
                    color = Color(r.cat.color).copy(alpha = if (dim) 0.3f else 1f),
                    startAngle = start + gap / 2f,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = if (isSel) maxStroke else strokeW),
                )
                start += sweep
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (selRow != null) {
                Text(
                    selRow.cat.name,
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 100.dp),
                )
                Text(fmtShort(selRow.sum), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                Text("${(selRow.sum / total * 100).roundToInt()}%", fontSize = 13.sp, color = cs.onSurfaceVariant)
            } else {
                Text("Total spent", fontSize = 13.sp, color = cs.onSurfaceVariant)
                Text(fmtShort(total), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
            }
        }
    }
}

@Composable
private fun Legend(
    rows: List<SummaryRow>,
    total: Double,
    selected: String?,
    onTap: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEach { r ->
            val dim = selected != null && selected != r.cat.name
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onTap(r.cat.name) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(r.cat.color).copy(alpha = if (dim) 0.4f else 1f))
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    "${r.cat.emoji} ${r.cat.name}",
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = cs.onSurface.copy(alpha = if (dim) 0.4f else 1f),
                )
                Text(
                    "${fmt(r.sum)} · ${(r.sum / total * 100).roundToInt()}%",
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BarList(rows: List<SummaryRow>, total: Double) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { r ->
            val frac = (r.sum / total).toFloat().coerceIn(0f, 1f)
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${r.cat.emoji} ${r.cat.name}",
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = cs.onSurface,
                    )
                    Text(
                        "${fmt(r.sum)} · ${(frac * 100).roundToInt()}%",
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(cs.onSurface.copy(alpha = 0.1f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(frac)
                            .fillMaxHeight()
                            .background(Color(r.cat.color))
                    )
                }
            }
        }
    }
}
