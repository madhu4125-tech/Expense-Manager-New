package com.madhu.expensemanager

import org.json.JSONObject
import java.text.BreakIterator
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.random.Random

const val OTHERS = "Others"
const val GRAY = 0xFF6B7280L

enum class TxType(val key: String) { EXPENSE("expense"), INCOME("income") }

data class Tx(
    val id: Long,
    val amount: Double,
    val type: TxType,
    val category: String, // stored as "emoji name", same format as the web app backup
    val method: String,   // "UPI", "Cash", "Bank" or "" for old entries
    val note: String,
    val dateMillis: Long,
)

data class Cat(val name: String, val emoji: String, val color: Long) {
    val label: String get() = "$emoji $name"
}

data class Method(val name: String, val emoji: String)

val METHODS = listOf(
    Method("UPI", "📱"),
    Method("Cash", "💵"),
    Method("Bank", "🏦"),
)

val DEFAULT_CATS = listOf(
    Cat("Petrol", "⛽", 0xFFF59E0B),
    Cat("Investment", "📈", 0xFF10B981),
    Cat("Home", "🏠", 0xFF3B82F6),
    Cat("Hak", "👤", 0xFFEC4899),
    Cat("Siddu", "👤", 0xFF8B5CF6),
    Cat("Madhu", "👤", 0xFF06B6D4),
    Cat(OTHERS, "📦", GRAY),
)

val COLOR_CHOICES: List<Long> = listOf(
    0xFFEF4444, 0xFFF97316, 0xFFF59E0B, 0xFF84CC16, 0xFF10B981,
    0xFF06B6D4, 0xFF3B82F6, 0xFF8B5CF6, 0xFFEC4899, GRAY,
)

val EMOJI_CHOICES = listOf(
    "🍔", "☕", "🛒", "🚗", "💡", "📱", "🎬", "🎁",
    "💊", "👕", "📚", "🎓", "💼", "💰", "🐶", "🍎",
)

val zone: ZoneId get() = ZoneId.systemDefault()

/* ---------- formatting ---------- */

private fun inFormat(decimals: Int): NumberFormat =
    NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }

fun fmt(n: Double): String = "₹" + inFormat(2).format(n)
fun fmtShort(n: Double): String = "₹" + inFormat(0).format(n)

/* ---------- categories ---------- */

fun catNameOf(label: String): String {
    val i = label.indexOf(' ')
    val n = (if (i == -1) label else label.substring(i + 1)).trim()
    return n.ifEmpty { OTHERS }
}

fun parseCat(label: String, cats: List<Cat>): Cat {
    val name = catNameOf(label)
    cats.firstOrNull { it.name == name }?.let { return it }
    val i = label.indexOf(' ')
    return Cat(name, if (i == -1) "📦" else label.substring(0, i), GRAY)
}

fun firstGrapheme(s: String): String {
    val t = s.filter { !it.isWhitespace() }
    if (t.isEmpty()) return ""
    val bi = BreakIterator.getCharacterInstance()
    bi.setText(t)
    val end = bi.next()
    return if (end == BreakIterator.DONE) t else t.substring(0, end)
}

/** removes duplicate names (case-insensitive) and keeps "Others" as the last item */
fun normalizeCats(list: List<Cat>): List<Cat> {
    val seen = HashSet<String>()
    val out = mutableListOf<Cat>()
    list.forEach { if (seen.add(it.name.lowercase())) out.add(it) }
    val idx = out.indexOfFirst { it.name.equals(OTHERS, ignoreCase = true) }
    val others = if (idx == -1) Cat(OTHERS, "📦", GRAY) else out.removeAt(idx).copy(name = OTHERS)
    out.add(others)
    return out
}

fun colorToHex(c: Long): String = "#%06x".format(c and 0xFFFFFFL)

fun hexToColor(s: String?): Long? {
    if (s == null || !Regex("^#[0-9a-fA-F]{6}$").matches(s)) return null
    return 0xFF000000L or s.substring(1).toLong(16)
}

/* ---------- JSON (same format as the web app backups) ---------- */

private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

fun parseDate(v: Any?): Long? {
    return try {
        when (v) {
            is Number -> v.toLong()
            is String -> {
                val s = v.trim()
                try {
                    Instant.parse(s).toEpochMilli()
                } catch (e: Exception) {
                    try {
                        OffsetDateTime.parse(s).toInstant().toEpochMilli()
                    } catch (e2: Exception) {
                        LocalDate.parse(s.take(10)).atStartOfDay(zone).toInstant().toEpochMilli()
                    }
                }
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

fun Tx.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("amount", amount)
    .put("type", type.key)
    .put("category", category)
    .put("note", note)
    .put("method", method)
    .put("date", Instant.ofEpochMilli(dateMillis).toString())

fun txFromJson(o: JSONObject?): Tx? {
    if (o == null) return null
    val amount = o.optDouble("amount", Double.NaN)
    if (amount.isNaN() || amount <= 0.0) return null
    val type = when (o.str("type")) {
        "income" -> TxType.INCOME
        "expense" -> TxType.EXPENSE
        else -> return null
    }
    val millis = parseDate(if (o.isNull("date")) null else o.opt("date")) ?: return null
    val rawId = if (o.isNull("id")) 0L else o.optLong("id", 0L)
    val method = o.str("method").let { m -> if (METHODS.any { it.name == m }) m else "" }
    return Tx(
        id = if (rawId > 0) rawId else System.currentTimeMillis() + Random.nextInt(100000),
        amount = amount,
        type = type,
        category = o.str("category").ifBlank { "📦 $OTHERS" },
        method = method,
        note = o.str("note"),
        dateMillis = millis,
    )
}

fun Cat.toJson(): JSONObject = JSONObject()
    .put("name", name)
    .put("emoji", emoji)
    .put("color", colorToHex(color))

fun catFromJson(o: JSONObject?): Cat? {
    if (o == null) return null
    val name = o.str("name").trim().take(20)
    if (name.isEmpty()) return null
    return Cat(name, firstGrapheme(o.str("emoji")).ifEmpty { "📦" }, hexToColor(o.str("color")) ?: GRAY)
}

/* ---------- filtering ---------- */

fun filterTx(list: List<Tx>, filter: String): List<Tx> {
    val today = LocalDate.now()
    val nowMs = System.currentTimeMillis()
    return list.filter { t ->
        when (filter) {
            "month" -> {
                val d = Instant.ofEpochMilli(t.dateMillis).atZone(zone).toLocalDate()
                d.month == today.month && d.year == today.year
            }
            "week" -> ceil(abs(nowMs - t.dateMillis) / 86_400_000.0) <= 7
            else -> true
        }
    }.sortedByDescending { it.dateMillis }
}
