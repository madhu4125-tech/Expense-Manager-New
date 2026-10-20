package com.madhu.expensemanager

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("en-IN"))

private fun trimAmount(a: Double): String = if (a % 1.0 == 0.0) a.toLong().toString() else a.toString()

@Composable
internal fun FieldLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
internal fun PickerField(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier) {
        FieldLabel(label)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(cs.surfaceVariant)
                .border(1.dp, cs.outline, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(14.dp)
        ) {
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onSurface)
        }
    }
}

@Composable
internal fun SheetTitle(title: String, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

/* =====================================================================
   Add / edit transaction
   ===================================================================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionSheet(vm: ExpenseViewModel, editId: Long?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val existing = remember(editId) { editId?.let { id -> vm.transactions.firstOrNull { it.id == id } } }

    var type by remember { mutableStateOf(existing?.type ?: TxType.EXPENSE) }
    var amountText by remember { mutableStateOf(existing?.let { trimAmount(it.amount) } ?: "") }
    var catName by remember { mutableStateOf(existing?.let { catNameOf(it.category) } ?: vm.categories.first().name) }
    var method by remember { mutableStateOf(if (existing != null) existing.method else vm.lastMethod) }
    var date by remember {
        mutableStateOf(existing?.let { Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate() } ?: LocalDate.now())
    }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var showDate by remember { mutableStateOf(false) }
    var catMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (existing == null) {
            delay(350)
            runCatching { focus.requestFocus() }
        }
    }

    // category options; keep a category that no longer exists selectable so editing does not silently change it
    val options = vm.categories.map { it.name to it.label }.toMutableList()
    if (existing != null && options.none { it.first == catName }) {
        options.add(catName to existing.category)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = cs.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            SheetTitle(if (existing != null) "Edit transaction" else "New transaction", close)

            SegmentedControl(
                items = listOf("Expense", "Income"),
                selectedIndex = if (type == TxType.EXPENSE) 0 else 1,
                onSelect = { type = if (it == 0) TxType.EXPENSE else TxType.INCOME },
                track = cs.onSurface.copy(alpha = 0.08f),
                selectedBg = cs.surface,
                selectedFg = cs.onSurface,
                unselectedFg = cs.onSurfaceVariant,
                selectedFgFor = { i -> if (i == 0) ExpenseRed else IncomeGreen },
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { v -> if (v.isEmpty() || v.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amountText = v },
                label = { Text("Amount (₹)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    PickerField("Category", options.firstOrNull { it.first == catName }?.second ?: catName) { catMenu = true }
                    DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                        options.forEach { (name, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    catName = name
                                    catMenu = false
                                },
                            )
                        }
                    }
                }
                PickerField("Date", date.format(dateFmt), Modifier.weight(1f)) { showDate = true }
            }
            Spacer(Modifier.height(16.dp))

            FieldLabel("Paid via")
            SegmentedControl(
                items = METHODS.map { "${it.emoji} ${it.name}" },
                selectedIndex = METHODS.indexOfFirst { it.name == method },
                onSelect = { method = METHODS[it].name },
                track = cs.onSurface.copy(alpha = 0.08f),
                selectedBg = cs.surface,
                selectedFg = cs.primary,
                unselectedFg = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                placeholder = { Text("What was this for?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))

            PrimaryButton("Save transaction") {
                val amount = amountText.toDoubleOrNull()
                if (amount == null || amount <= 0.0) {
                    toast(context, "Enter an amount greater than 0")
                    return@PrimaryButton
                }
                val label = options.firstOrNull { it.first == catName }?.second ?: "📦 $OTHERS"
                val time = existing?.let { Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalTime() } ?: LocalTime.now()
                val millis = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                vm.saveTransaction(existing?.id, type, amount, label, method, note.trim(), millis)
                toast(context, if (existing != null) "Transaction updated" else "Transaction saved")
                close()
            }

            if (existing != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = cs.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Delete", color = cs.error, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showDate) {
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this transaction?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteTransaction(existing.id)
                    toast(context, "Deleted")
                    close()
                }) { Text("Delete", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/* =====================================================================
   Manage categories
   ===================================================================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySheet(vm: ExpenseViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    var editIndex by remember { mutableStateOf<Int?>(null) } // null = list, -1 = new, 0+ = editing
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(COLOR_CHOICES[0]) }
    var deleteIndex by remember { mutableStateOf<Int?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    fun openForm(i: Int) {
        val c = vm.categories.getOrNull(i)
        editIndex = i
        name = c?.name ?: ""
        emoji = c?.emoji ?: ""
        color = c?.color ?: (COLOR_CHOICES.firstOrNull { col -> vm.categories.none { it.color == col } } ?: COLOR_CHOICES[0])
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = cs.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            val idx = editIndex
            if (idx == null) {
                /* ----- list ----- */
                SheetTitle("Categories", close)
                Text(
                    "Tap a category to rename it or change its emoji and color. Deleting one moves its transactions to Others.",
                    fontSize = 14.sp,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                val counts = vm.transactions.groupingBy { catNameOf(it.category) }.eachCount()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    vm.categories.forEachIndexed { i, c ->
                        val n = counts[c.name] ?: 0
                        val isOthers = c.name == OTHERS
                        val sub = "$n transaction${if (n == 1) "" else "s"}" + if (isOthers) " • Cannot be deleted" else ""
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(cs.surfaceVariant)
                                .clickable { openForm(i) }
                                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(cs.background)
                                    .border(3.dp, Color(c.color), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Text(c.emoji, fontSize = 20.sp) }
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, fontWeight = FontWeight.Bold, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(sub, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { openForm(i) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit ${c.name}", tint = cs.primary)
                            }
                            if (!isOthers) {
                                IconButton(onClick = { deleteIndex = i }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete ${c.name}", tint = cs.error.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                PrimaryButton("Add category") { openForm(-1) }
            } else {
                /* ----- add / edit form ----- */
                val locked = vm.categories.getOrNull(idx)?.name == OTHERS
                SheetTitle(if (idx >= 0) "Edit category" else "New category", close)

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 20) name = it },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Groceries") },
                    singleLine = true,
                    enabled = !locked,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = emoji,
                    onValueChange = { if (it.length <= 8) emoji = it },
                    label = { Text("Emoji") },
                    placeholder = { Text("Type one or pick below") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                EMOJI_CHOICES.chunked(8).forEach { chunk ->
                    Row(Modifier.fillMaxWidth()) {
                        chunk.forEach { e ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clickable { emoji = e },
                                contentAlignment = Alignment.Center,
                            ) { Text(e, fontSize = 22.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                FieldLabel("Color")
                COLOR_CHOICES.chunked(5).forEach { chunk ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        chunk.forEach { c ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(c))
                                        .clickable { color = c },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (color == c) {
                                        Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                PrimaryButton("Save category") {
                    val err = vm.saveCategory(idx, name, emoji, color)
                    if (err != null) {
                        toast(context, err)
                    } else {
                        toast(context, if (idx >= 0) "Category updated" else "Category added")
                        editIndex = null
                    }
                }
                TextButton(onClick = { editIndex = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel", fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant)
                }
            }
        }
    }

    val di = deleteIndex
    val delCat = if (di != null) vm.categories.getOrNull(di) else null
    if (di != null && delCat != null) {
        val n = vm.transactions.count { catNameOf(it.category) == delCat.name }
        AlertDialog(
            onDismissRequest = { deleteIndex = null },
            title = { Text("Delete \"${delCat.name}\"?") },
            text = {
                Text(if (n > 0) "Its $n transaction${if (n == 1) "" else "s"} will move to $OTHERS." else "This category has no transactions.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCategory(di)
                    deleteIndex = null
                    toast(context, "Category deleted")
                }) { Text("Delete", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { deleteIndex = null }) { Text("Cancel") } },
        )
    }
}

/* =====================================================================
   Backup & restore
   ===================================================================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSheet(vm: ExpenseViewModel, onSms: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var pending by remember { mutableStateOf<ImportResult.Ready?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val text = readUriText(context, uri)
            if (text == null) {
                toast(context, "Could not read that file")
            } else {
                when (val r = vm.prepareImport(text)) {
                    is ImportResult.Error -> toast(context, r.message)
                    is ImportResult.Ready -> pending = r
                }
            }
        }
    }

    val stamp = LocalDate.now().toString()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = cs.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            SheetTitle("Backup & import", close)
            Text(
                "Your data lives only on this phone. Export a backup and save it to Drive or WhatsApp so you can restore it later.",
                fontSize = 14.sp,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            ActionRow("💬", "Import from SMS", "Scan bank and UPI alerts for a date range, review them, then add") {
                onSms()
            }
            Spacer(Modifier.height(12.dp))
            ActionRow("📤", "Export backup (JSON)", "Includes your categories. Use this to restore the app later") {
                if (vm.transactions.isEmpty()) toast(context, "Nothing to export yet")
                else shareText(context, "expense-backup-$stamp.json", vm.buildBackupJson(), "application/json")
            }
            Spacer(Modifier.height(12.dp))
            ActionRow("📊", "Export for Excel (CSV)", "Open in Excel or Google Sheets") {
                if (vm.transactions.isEmpty()) toast(context, "Nothing to export yet")
                else shareText(context, "expense-report-$stamp.csv", vm.buildCsv(), "text/csv")
            }
            Spacer(Modifier.height(12.dp))
            ActionRow("📥", "Import backup (JSON)", "Adds transactions and categories you don't already have. Works with backups from the old web app too") {
                picker.launch(arrayOf("*/*"))
            }
        }
    }

    val p = pending
    if (p != null) {
        val parts = mutableListOf<String>()
        if (p.fresh.isNotEmpty()) parts.add("${p.fresh.size} new transaction${if (p.fresh.size == 1) "" else "s"}")
        if (p.newCats.isNotEmpty()) parts.add("${p.newCats.size} new categor${if (p.newCats.size == 1) "y" else "ies"}")
        val summary = parts.joinToString(" and ")
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Import backup?") },
            text = { Text("Add $summary from this backup? Your current data stays as it is.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.applyImport(p)
                    pending = null
                    toast(context, "Imported $summary")
                    close()
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ActionRow(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cs.surfaceVariant,
        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.size(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = cs.onSurface)
                Text(subtitle, fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
        }
    }
}
