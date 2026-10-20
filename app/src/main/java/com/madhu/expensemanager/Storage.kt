package com.madhu.expensemanager

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/*
 * Everything the app saves lives in one SharedPreferences file. The screen (ViewModel) and the
 * background SMS receiver both go through these functions, so they always see the same data.
 */

fun appPrefs(context: Context): SharedPreferences =
    context.applicationContext.getSharedPreferences("expenses", Context.MODE_PRIVATE)

fun readTransactions(prefs: SharedPreferences): List<Tx> = try {
    val arr = JSONArray(prefs.getString("transactions", "[]"))
    (0 until arr.length()).mapNotNull { txFromJson(arr.optJSONObject(it)) }.distinctBy { it.id }
} catch (e: Exception) {
    emptyList()
}

fun writeTransactions(prefs: SharedPreferences, list: List<Tx>) {
    val arr = JSONArray()
    list.forEach { arr.put(it.toJson()) }
    prefs.edit().putString("transactions", arr.toString()).apply()
}

fun readCategories(prefs: SharedPreferences): List<Cat> {
    try {
        val s = prefs.getString("categories", null)
        if (s != null) {
            val arr = JSONArray(s)
            val clean = (0 until arr.length()).mapNotNull { catFromJson(arr.optJSONObject(it)) }
            if (clean.isNotEmpty()) return normalizeCats(clean)
        }
    } catch (e: Exception) {
        // fall through to defaults
    }
    return normalizeCats(DEFAULT_CATS)
}

fun writeCategories(prefs: SharedPreferences, list: List<Cat>) {
    val arr = JSONArray()
    list.forEach { arr.put(it.toJson()) }
    prefs.edit().putString("categories", arr.toString()).apply()
}

/** keys of SMS that were already added, so the same message is never added twice */
fun readSmsKeys(prefs: SharedPreferences): Set<String> =
    prefs.getStringSet("smsKeys", null)?.toSet() ?: emptySet()

/** merchant -> category choices remembered from earlier imports */
fun readSmsRules(prefs: SharedPreferences): Map<String, String> = try {
    val o = JSONObject(prefs.getString("smsRules", "{}") ?: "{}")
    o.keys().asSequence().associateWith { o.optString(it) }
} catch (e: Exception) {
    emptyMap()
}

/** Adds SMS-based transactions, remembers their keys and merchant->category choices. Returns the new transactions. */
fun appendSmsItems(prefs: SharedPreferences, items: List<SmsCandidate>): List<Tx> {
    if (items.isEmpty()) return emptyList()
    val existing = readTransactions(prefs)
    val cats = readCategories(prefs)
    val used = existing.map { it.id }.toMutableSet()
    var next = System.currentTimeMillis()
    val added = items.map { c ->
        while (next in used) next++
        used.add(next)
        val cat = cats.firstOrNull { it.name == c.catName } ?: cats.last()
        Tx(next, c.amount, c.type, cat.label, c.method, c.note, c.dateMillis)
    }
    writeTransactions(prefs, added + existing)

    val keys = readSmsKeys(prefs).toMutableSet()
    items.forEach { keys.add(it.key) }
    val rules = try {
        JSONObject(prefs.getString("smsRules", "{}") ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }
    items.forEach { c ->
        if (c.note.isNotBlank() && c.catName != OTHERS) rules.put(c.note.lowercase(), c.catName)
    }
    prefs.edit().putStringSet("smsKeys", keys).putString("smsRules", rules.toString()).apply()
    return added
}
