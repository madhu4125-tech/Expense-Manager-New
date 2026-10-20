package com.madhu.expensemanager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val smsDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("en-IN"))
private val smsRowDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.forLanguageTag("en-IN"))

private fun hasSmsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickDateDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtc
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsImportSheet(vm: ExpenseViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    var granted by remember { mutableStateOf(hasSmsPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }

    val today = LocalDate.now()
    var fromDate by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var toDate by remember { mutableStateOf(today) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }

    var scanning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    val items = remember { mutableStateListOf<SmsCandidate>() }

    fun scan() {
        if (scanning) return
        scanning = true
        val from = fromDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val toExclusive = toDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val cats = vm.categories
        val done = vm.importedSmsKeys()
        val rules = vm.smsRules()
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { scanSms(context, from, toExclusive, cats, done, rules) }
                result = r
                items.clear()
                items.addAll(r.items)
            } catch (e: SecurityException) {
                granted = hasSmsPermission(context)
                denied = true
                toast(context, "SMS access is not allowed yet")
            } catch (e: Exception) {
                toast(context, "Could not read SMS: " + (e.message ?: "unknown error"))
            } finally {
                scanning = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = cs.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            SheetTitle("Import from SMS", close)
            Text(
                "Reads bank and UPI alerts from your inbox on this phone. Nothing is uploaded, and nothing is added until you review it.",
                fontSize = 14.sp,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            if (!granted) {
                PrimaryButton("Allow SMS access") { permLauncher.launch(Manifest.permission.READ_SMS) }
                if (denied) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Android blocked SMS access. On Android 13 and newer, apps installed from outside the Play Store need one extra step: " +
                            "open App info, tap the ⋮ menu, choose Allow restricted settings, then Permissions > SMS > Allow.",
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant,
                    )
                    Row {
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            )
                        }) { Text("Open app settings") }
                        TextButton(onClick = { granted = hasSmsPermission(context) }) { Text("Check again") }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PickerField("From", fromDate.format(smsDateFmt), Modifier.weight(1f)) { pickFrom = true }
                    PickerField("To", toDate.format(smsDateFmt), Modifier.weight(1f)) { pickTo = true }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { fromDate = today.minusDays(6); toDate = today }) { Text("7 days") }
                    TextButton(onClick = { fromDate = today.minusDays(29); toDate = today }) { Text("30 days") }
                    TextButton(onClick = { fromDate = today.withDayOfMonth(1); toDate = today }) { Text("This month") }
                }
                Spacer(Modifier.height(8.dp))
                PrimaryButton(if (scanning) "Scanning..." else "Scan messages") { scan() }

                val r = result
                if (r != null) {
                    Spacer(Modifier.height(16.dp))
                    if (items.isEmpty()) {
                        Text(
                            "No new bank messages found. Scanned ${r.scanned} message${if (r.scanned == 1) "" else "s"}" +
                                (if (r.alreadyImported > 0) ", ${r.alreadyImported} already imported." else "."),
                            color = cs.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                    } else {
                        val selected = items.count { it.selected }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${items.size} found, $selected selected", fontWeight = FontWeight.Bold, color = cs.onSurface)
                            TextButton(onClick = {
                                val all = selected != items.size
                                for (i in items.indices) items[i] = items[i].copy(selected = all)
                            }) { Text(if (selected == items.size) "Select none" else "Select all") }
                        }
                        if (r.alreadyImported > 0) {
                            Text(
                                "${r.alreadyImported} already imported earlier and hidden.",
                                fontSize = 12.sp,
                                color = cs.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        PrimaryButton(if (selected == 0) "Select messages to import" else "Import $selected selected") {
                            val chosen = items.filter { it.selected }
                            if (chosen.isEmpty()) {
                                toast(context, "Select at least one message")
                            } else {
                                vm.addSmsTransactions(chosen)
                                toast(context, "Imported ${chosen.size} transaction${if (chosen.size == 1) "" else "s"}")
                                close()
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Check each row: tap the type, payment method or category to change it. Your category choice is remembered for that merchant next time.",
                            fontSize = 12.sp,
                            color = cs.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items.forEachIndexed { i, c ->
                                key(c.key) {
                                    SmsRow(c, vm.categories) { items[i] = it }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pickFrom) {
        PickDateDialog(fromDate, onPick = {
            fromDate = it
            if (toDate.isBefore(it)) toDate = it
        }, onDismiss = { pickFrom = false })
    }
    if (pickTo) {
        PickDateDialog(toDate, onPick = {
            toDate = it
            if (fromDate.isAfter(it)) fromDate = it
        }, onDismiss = { pickTo = false })
    }
}

@Composable
private fun SmsChip(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
private fun SmsRow(c: SmsCandidate, cats: List<Cat>, onChange: (SmsCandidate) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var catMenu by remember { mutableStateOf(false) }
    val isExp = c.type == TxType.EXPENSE
    val cat = cats.firstOrNull { it.name == c.catName } ?: cats.last()
    val date = Instant.ofEpochMilli(c.dateMillis).atZone(zone).format(smsRowDateFmt)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cs.surfaceVariant,
        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 10.dp, end = 12.dp)) {
            Checkbox(checked = c.selected, onCheckedChange = { onChange(c.copy(selected = it)) })
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        (if (isExp) "-" else "+") + fmt(c.amount),
                        fontWeight = FontWeight.Bold,
                        color = if (isExp) ExpenseRed else IncomeGreen,
                    )
                    Text(date, fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
                Text(c.note, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmsChip(
                        if (isExp) "Expense" else "Income",
                        if (isExp) ExpenseRed else IncomeGreen,
                    ) { onChange(c.copy(type = if (isExp) TxType.INCOME else TxType.EXPENSE)) }
                    SmsChip(c.method, cs.onSurface) {
                        val i = METHODS.indexOfFirst { it.name == c.method }
                        onChange(c.copy(method = METHODS[(i + 1).mod(METHODS.size)].name))
                    }
                    Box {
                        SmsChip(cat.label, cs.onSurface) { catMenu = true }
                        DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                            cats.forEach { k ->
                                DropdownMenuItem(
                                    text = { Text(k.label) },
                                    onClick = {
                                        onChange(c.copy(catName = k.name))
                                        catMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(c.body, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
