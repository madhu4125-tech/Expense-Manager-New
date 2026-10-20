package com.madhu.expensemanager

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant
import java.util.Locale

sealed class ImportResult {
    data class Error(val message: String) : ImportResult()
    data class Ready(val fresh: List<Tx>, val newCats: List<Cat>) : ImportResult()
}

class ExpenseViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs: SharedPreferences = appPrefs(app)

    // the background SMS receiver writes straight to storage; reload when that happens
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "transactions") transactions = readTransactions(prefs)
    }

    var transactions by mutableStateOf(loadTransactions())
        private set
    var categories by mutableStateOf(loadCategories())
        private set
    var filter by mutableStateOf(prefs.getString("filter", "month") ?: "month")
        private set
    var summaryView by mutableStateOf(prefs.getString("summaryView", "pie") ?: "pie")
        private set
    var darkMode by mutableStateOf<Boolean?>(prefs.getString("theme", null)?.let { it == "dark" })
        private set
    var lastMethod by mutableStateOf(prefs.getString("lastMethod", METHODS[0].name) ?: METHODS[0].name)
        private set
    var smsAuto by mutableStateOf(prefs.getBoolean("smsAuto", false))
        private set

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onCleared()
    }

    fun updateSmsAuto(on: Boolean) {
        smsAuto = on
        prefs.edit().putBoolean("smsAuto", on).apply()
    }

    /* ---------- load / save ---------- */

    private fun loadTransactions(): List<Tx> = readTransactions(prefs)

    private fun loadCategories(): List<Cat> = readCategories(prefs)

    private fun persistTransactions() = writeTransactions(prefs, transactions)

    private fun persistCategories() = writeCategories(prefs, categories)

    /* ---------- settings ---------- */

    fun updateFilter(f: String) {
        filter = f
        prefs.edit().putString("filter", f).apply()
    }

    fun updateSummaryView(v: String) {
        summaryView = v
        prefs.edit().putString("summaryView", v).apply()
    }

    fun setDark(d: Boolean) {
        darkMode = d
        prefs.edit().putString("theme", if (d) "dark" else "light").apply()
    }

    /* ---------- transactions ---------- */

    private fun newId(): Long {
        var id = System.currentTimeMillis()
        val used = transactions.map { it.id }.toSet()
        while (id in used) id++
        return id
    }

    fun saveTransaction(
        id: Long?,
        type: TxType,
        amount: Double,
        categoryLabel: String,
        method: String,
        note: String,
        dateMillis: Long,
    ) {
        if (id != null) {
            transactions = transactions.map {
                if (it.id == id) it.copy(
                    type = type,
                    amount = amount,
                    category = categoryLabel,
                    method = method,
                    note = note,
                    dateMillis = dateMillis,
                ) else it
            }
        } else {
            val tx = Tx(newId(), amount, type, categoryLabel, method, note, dateMillis)
            transactions = listOf(tx) + transactions
            if (method.isNotEmpty()) {
                lastMethod = method
                prefs.edit().putString("lastMethod", method).apply()
            }
        }
        persistTransactions()
    }

    fun deleteTransaction(id: Long) {
        transactions = transactions.filter { it.id != id }
        persistTransactions()
    }

    fun resetTransactions() {
        transactions = emptyList()
        persistTransactions()
        prefs.edit().remove("smsKeys").apply()
    }

    /* ---------- categories ---------- */

    /** editIndex = -1 adds a new category. Returns an error message, or null on success. */
    fun saveCategory(editIndex: Int, rawName: String, rawEmoji: String, color: Long): String? {
        val editing = categories.getOrNull(editIndex)
        val name = if (editing?.name == OTHERS) OTHERS else rawName.trim().take(20)
        if (name.isEmpty()) return "Enter a category name"
        val clash = categories.withIndex().any { (i, c) -> i != editIndex && c.name.equals(name, ignoreCase = true) }
        if (clash) return "That category already exists"

        val next = Cat(name, firstGrapheme(rawEmoji).ifEmpty { "📦" }, color)
        if (editing != null) {
            val old = editing.name
            val list = categories.toMutableList()
            list[editIndex] = next
            categories = normalizeCats(list)
            // rename / emoji change flows through to existing transactions
            transactions = transactions.map {
                if (catNameOf(it.category) == old) it.copy(category = next.label) else it
            }
            persistTransactions()
        } else {
            categories = normalizeCats(categories + next)
        }
        persistCategories()
        return null
    }

    fun deleteCategory(index: Int) {
        val c = categories.getOrNull(index) ?: return
        if (c.name == OTHERS) return
        val others = categories.first { it.name == OTHERS }
        transactions = transactions.map {
            if (catNameOf(it.category) == c.name) it.copy(category = others.label) else it
        }
        categories = categories.filterIndexed { i, _ -> i != index }
        persistTransactions()
        persistCategories()
    }

    /* ---------- SMS import ---------- */

    fun importedSmsKeys(): Set<String> = readSmsKeys(prefs)

    fun smsRules(): Map<String, String> = readSmsRules(prefs)

    fun addSmsTransactions(items: List<SmsCandidate>) {
        appendSmsItems(prefs, items)
        transactions = readTransactions(prefs)
    }

    /* ---------- backup / import ---------- */

    fun buildBackupJson(): String {
        val cats = JSONArray()
        categories.forEach { cats.put(it.toJson()) }
        val txs = JSONArray()
        transactions.forEach { txs.put(it.toJson()) }
        return JSONObject()
            .put("app", "expense-manager")
            .put("version", 2)
            .put("exportedAt", Instant.now().toString())
            .put("categories", cats)
            .put("transactions", txs)
            .toString(2)
    }

    private fun csvCell(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    fun buildCsv(): String {
        val lines = mutableListOf("Date,Type,Category,Amount,Note,Method")
        transactions.sortedBy { it.dateMillis }.forEach { t ->
            val date = Instant.ofEpochMilli(t.dateMillis).atZone(zone).toLocalDate().toString()
            val row = listOf(
                date,
                t.type.key,
                parseCat(t.category, categories).name,
                String.format(Locale.US, "%.2f", t.amount),
                t.note,
                t.method,
            )
            lines.add(row.joinToString(",") { csvCell(it) })
        }
        return "\uFEFF" + lines.joinToString("\r\n")
    }

    fun prepareImport(rawText: String): ImportResult {
        val root: Any = try {
            JSONTokener(rawText.trimStart('\uFEFF')).nextValue()
        } catch (e: Exception) {
            return ImportResult.Error("That file is not a valid backup")
        }
        val txArr: JSONArray?
        val catArr: JSONArray?
        when (root) {
            is JSONArray -> {
                txArr = root
                catArr = null
            }
            is JSONObject -> {
                txArr = root.optJSONArray("transactions")
                catArr = root.optJSONArray("categories")
            }
            else -> {
                txArr = null
                catArr = null
            }
        }
        if (txArr == null) return ImportResult.Error("No transactions found in that file")

        val clean = (0 until txArr.length()).mapNotNull { txFromJson(txArr.optJSONObject(it)) }.distinctBy { it.id }
        val fileCats = catArr?.let { a -> (0 until a.length()).mapNotNull { catFromJson(a.optJSONObject(it)) } } ?: emptyList()

        val have = categories.map { it.name.lowercase() }.toMutableSet()
        val newCats = fileCats.filter { have.add(it.name.lowercase()) }
        val existing = transactions.map { it.id }.toSet()
        val fresh = clean.filter { it.id !in existing }

        if (clean.isEmpty() && newCats.isEmpty()) return ImportResult.Error("No valid transactions found")
        if (fresh.isEmpty() && newCats.isEmpty()) return ImportResult.Error("Already up to date, nothing new to add")
        return ImportResult.Ready(fresh, newCats)
    }

    fun applyImport(r: ImportResult.Ready) {
        if (r.newCats.isNotEmpty()) {
            categories = normalizeCats(categories + r.newCats)
            persistCategories()
        }
        if (r.fresh.isNotEmpty()) {
            transactions = transactions + r.fresh
            persistTransactions()
        }
    }
}
