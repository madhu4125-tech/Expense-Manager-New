package com.madhu.expensemanager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("en-IN"))

@Composable
fun HomeScreen(vm: ExpenseViewModel, dark: Boolean) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    var showAdd by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Long?>(null) }
    var showCats by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var showSms by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<Long?>(null) }

    val list = remember(vm.transactions, vm.filter) { filterTx(vm.transactions, vm.filter) }
    val income = list.filter { it.type == TxType.INCOME }.sumOf { it.amount }
    val expense = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    val rows = remember(list, vm.categories) { buildSummary(list, vm.categories) }

    Scaffold(
        containerColor = cs.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editId = null
                    showAdd = true
                },
                modifier = Modifier.navigationBarsPadding(),
                shape = CircleShape,
                containerColor = cs.primary,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add transaction")
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                Header(
                    dark = dark,
                    filter = vm.filter,
                    onFilter = { vm.updateFilter(it) },
                    income = income,
                    expense = expense,
                    onTheme = { vm.setDark(!dark) },
                    onCats = { showCats = true },
                    onBackup = { showBackup = true },
                    onSms = { showSms = true },
                )
            }

            if (rows.isNotEmpty()) {
                item {
                    SummaryCard(
                        rows = rows,
                        view = vm.summaryView,
                        onView = { vm.updateSummaryView(it) },
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp),
                    )
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Transactions", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = cs.onSurface)
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(cs.errorContainer)
                            .clickable { showReset = true }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Reset data", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = cs.onErrorContainer)
                    }
                }
            }

            if (list.isEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("🧾", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No transactions found for this period.", color = cs.onSurfaceVariant)
                    }
                }
            } else {
                items(list, key = { it.id }) { t ->
                    TxRow(
                        t = t,
                        cats = vm.categories,
                        onClick = {
                            editId = t.id
                            showAdd = true
                        },
                        onDelete = { deleteId = t.id },
                    )
                }
            }
        }
    }

    if (showAdd) {
        TransactionSheet(vm, editId) { showAdd = false }
    }
    if (showCats) {
        CategorySheet(vm) { showCats = false }
    }
    if (showBackup) {
        BackupSheet(vm, onSms = {
            showBackup = false
            showSms = true
        }) { showBackup = false }
    }
    if (showSms) {
        SmsImportSheet(vm) { showSms = false }
    }

    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete this transaction?") },
            confirmButton = {
                TextButton(onClick = {
                    deleteId?.let { vm.deleteTransaction(it) }
                    deleteId = null
                    toast(context, "Deleted")
                }) { Text("Delete", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } },
        )
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Start fresh?") },
            text = {
                Text(
                    "This will permanently delete all your transactions. Your categories stay. " +
                        "Export a backup first if you might need them. You cannot undo this action."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetTransactions()
                    showReset = false
                    toast(context, "All transactions deleted")
                }) { Text("Delete all", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(
    dark: Boolean,
    filter: String,
    onFilter: (String) -> Unit,
    income: Double,
    expense: Double,
    onTheme: () -> Unit,
    onCats: () -> Unit,
    onBackup: () -> Unit,
    onSms: () -> Unit,
) {
    val bg = if (dark) Color(0xFF1E40AF) else Color(0xFF2563EB)
    val soft = Color(0xFFDBEAFE)
    val balance = income - expense

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(bg)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("My Expenses", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderIcon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "Toggle dark mode", onTheme)
                HeaderIcon(Icons.Default.Sms, "Import from SMS", onSms)
                HeaderIcon(Icons.Default.Category, "Manage categories", onCats)
                HeaderIcon(Icons.Default.CloudUpload, "Backup and restore", onBackup)
            }
        }

        Spacer(Modifier.height(16.dp))
        val filters = listOf("week", "month", "all")
        SegmentedControl(
            items = listOf("Week", "Month", "All time"),
            selectedIndex = filters.indexOf(filter).coerceAtLeast(0),
            onSelect = { onFilter(filters[it]) },
            track = Color.White.copy(alpha = 0.2f),
            selectedBg = Color.White,
            selectedFg = Color(0xFF1D4ED8),
            unselectedFg = soft,
        )

        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.2f))
                .padding(16.dp)
        ) {
            Text("Total balance", color = soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                (if (balance < 0) "-" else "") + fmt(abs(balance)),
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("↓ ", Color(0xFF86EFAC), "Income", fmt(income), Modifier.weight(1f))
            StatCard("↑ ", Color(0xFFFCA5A5), "Expense", fmt(expense), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(arrow: String, arrowColor: Color, label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(12.dp)
    ) {
        Row {
            Text(arrow, color = arrowColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Color(0xFFDBEAFE), fontSize = 12.sp)
        }
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TxRow(t: Tx, cats: List<Cat>, onClick: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val cat = parseCat(t.category, cats)
    val isExp = t.type == TxType.EXPENSE
    val date = Instant.ofEpochMilli(t.dateMillis).atZone(zone).toLocalDate().format(dayFmt)
    val sub = date +
        (if (t.method.isNotEmpty()) " • ${t.method}" else "") +
        (if (t.note.isNotEmpty()) " • ${t.note}" else "")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = cs.surfaceVariant,
        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.5f)),
    ) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(cs.background),
                contentAlignment = Alignment.Center,
            ) { Text(cat.emoji, fontSize = 20.sp) }

            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(cat.name, fontWeight = FontWeight.Bold, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.size(8.dp))
            Text(
                (if (isExp) "-" else "+") + fmt(t.amount),
                fontWeight = FontWeight.Bold,
                color = if (isExp) ExpenseRed else IncomeGreen,
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = cs.error.copy(alpha = 0.6f))
            }
        }
    }
}
