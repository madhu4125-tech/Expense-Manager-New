package com.madhu.expensemanager

import android.content.Context
import android.provider.Telephony

data class SmsMsg(val sender: String, val body: String, val dateMillis: Long)

data class ParsedSms(val amount: Double, val type: TxType, val merchant: String, val method: String)

/** One bank SMS turned into a proposed transaction, shown in the review list. */
data class SmsCandidate(
    val key: String,
    val sender: String,
    val body: String,
    val dateMillis: Long,
    val amount: Double,
    val type: TxType,
    val note: String,
    val method: String,
    val catName: String,
    val selected: Boolean = true,
)

data class ScanResult(val items: List<SmsCandidate>, val alreadyImported: Int, val scanned: Int)

/* ---------- reading the inbox ---------- */

private const val MAX_MESSAGES = 3000

fun readInbox(context: Context, fromMillis: Long, toMillisExclusive: Long): List<SmsMsg> {
    val out = mutableListOf<SmsMsg>()
    val cols = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
    val cursor = context.contentResolver.query(
        Telephony.Sms.Inbox.CONTENT_URI,
        cols,
        "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} < ?",
        arrayOf(fromMillis.toString(), toMillisExclusive.toString()),
        "${Telephony.Sms.DATE} DESC",
    ) ?: return out
    cursor.use { c ->
        val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
        val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
        val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
        while (c.moveToNext() && out.size < MAX_MESSAGES) {
            out.add(SmsMsg(c.getString(iAddr) ?: "", c.getString(iBody) ?: "", c.getLong(iDate)))
        }
    }
    return out
}

/* ---------- parsing bank / UPI messages ---------- */

private val I = RegexOption.IGNORE_CASE

private val AMOUNT_RE = Regex("""(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", I)
private val DEBIT_RE = Regex("""\b(debited|debit|spent|paid|withdrawn|withdrawal|purchase|purchased|sent|deducted)\b""", I)
private val CREDIT_RE = Regex("""\b(credited|credit|received|deposited|refund|refunded)\b""", I)
private val CARD_RE = Regex("""\b(credit|debit)\s+card""", I)

// OTPs, offers, reminders and "will be debited" style alerts are not real transactions
private val SKIP_RE = Regex(
    """\b(otp|one[- ]time|passcode|offer|cashback|loan|emi|overdue|reminder|statement|pre-?approved|kyc|click|apply|expires?|will be|requested|declined|failed|unsuccessful)\b""",
    I,
)

private val VPA_RE = Regex("""\bvpa\s+([\w.\-]+@\w+)\s*(?:\(([^)]{2,30})\))?""", I)
private val DEBIT_KEY_RE = Regex("""\b(?:to|at|towards|for)\s+(?=[A-Za-z0-9])""", I)
private val CREDIT_KEY_RE = Regex("""\b(?:from|by)\s+(?=[A-Za-z0-9])""", I)
private val NAME_CHARS_RE = Regex("""^[A-Za-z0-9][A-Za-z0-9 &.'@\-]{1,30}""")
private val CUT_RE = Regex(
    """\s+(?:on|ref|upi|via|avl|bal|available|if|not|call|using|dated?|from|for|txn|trf|info|is|has|was|successful|a/c|ac|acct|card|ending|with)\b.*$""",
    I,
)
private val BAD_START_RE = Regex("""^(your|a/c|ac|account|xx+|the|my|ur|ref|rs|inr|\d)""", I)
private val GENERIC = setOf("upi", "imps", "neft", "rtgs", "atm", "bank", "a/c", "account")

private val UPI_RE = Regex("""\b(upi|vpa|gpay|phonepe|paytm|bhim)\b""", I)
private val PETROL_RE = Regex("""petrol|fuel|hpcl|bpcl|iocl|indian ?oil|hindustan petroleum|bharat petroleum|nayara|reliance bp|\bshell\b""", I)
private val INVEST_RE = Regex("""zerodha|groww|upstox|mutual fund|\bsip\b|nsdl|cdsl|kuvera|smallcase|angel one|\bnps\b|\bppf\b""", I)

private fun cleanMerchant(raw: String): String {
    var s = raw.replace(CUT_RE, "").trim().trim('.', ',', '-', ' ')
    if (s.contains('@') && !s.contains(' ')) s = s.substringBefore('@')
    if (s.length > 3 && s == s.uppercase() && s.any { it.isLetter() }) {
        s = s.split(' ').joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
    }
    return s.take(30).trim()
}

private fun validMerchant(s: String): Boolean =
    s.length >= 2 && !BAD_START_RE.containsMatchIn(s) && s.lowercase() !in GENERIC

private fun extractMerchant(text: String, type: TxType): String {
    VPA_RE.find(text)?.let { m ->
        val named = m.groupValues[2].trim()
        val c = cleanMerchant(if (named.isNotEmpty()) named else m.groupValues[1])
        if (validMerchant(c)) return c
    }
    // look at the text after every "to / at / from / by" so that "by UPI from john@bank" still finds john
    val keyRe = if (type == TxType.EXPENSE) DEBIT_KEY_RE else CREDIT_KEY_RE
    for (m in keyRe.findAll(text)) {
        val tail = text.substring(m.range.last + 1)
        val raw = NAME_CHARS_RE.find(tail)?.value ?: continue
        val c = cleanMerchant(raw)
        if (validMerchant(c)) return c
    }
    return ""
}

fun parseSms(body: String): ParsedSms? {
    val t = body.replace(CARD_RE, "card").replace('\n', ' ')
    if (SKIP_RE.containsMatchIn(t)) return null

    // first amount that is not a balance / limit figure
    val amountMatch = AMOUNT_RE.findAll(t).firstOrNull { m ->
        val before = t.substring(maxOf(0, m.range.first - 25), m.range.first).lowercase()
        !(before.contains("bal") || before.contains("avl") || before.contains("avail") || before.contains("limit"))
    } ?: return null
    val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
    if (amount <= 0.0) return null

    val d = DEBIT_RE.find(t)?.range?.first ?: Int.MAX_VALUE
    val c = CREDIT_RE.find(t)?.range?.first ?: Int.MAX_VALUE
    if (d == Int.MAX_VALUE && c == Int.MAX_VALUE) return null
    val type = if (d < c) TxType.EXPENSE else TxType.INCOME

    return ParsedSms(amount, type, extractMerchant(t, type), if (UPI_RE.containsMatchIn(t)) "UPI" else "Bank")
}

fun suggestCategory(p: ParsedSms, body: String, cats: List<Cat>, rules: Map<String, String>): String {
    fun has(n: String) = cats.any { it.name == n }
    if (p.merchant.isNotEmpty()) {
        rules[p.merchant.lowercase()]?.let { if (has(it)) return it }
    }
    if (p.type == TxType.INCOME) return OTHERS
    val t = p.merchant + " " + body
    if (has("Petrol") && PETROL_RE.containsMatchIn(t)) return "Petrol"
    if (has("Investment") && INVEST_RE.containsMatchIn(t)) return "Investment"
    return OTHERS
}

/** Same message => same key, whether it was captured live or found later by a scan (day-level, so clock differences don't matter). */
fun smsKey(sender: String, body: String, dateMillis: Long): String {
    val day = java.time.Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate()
    return "$sender|${body.trim().hashCode()}|$day"
}

fun smsNote(p: ParsedSms, sender: String): String =
    p.merchant.ifEmpty { sender.replace(Regex("^[A-Za-z]{2}-"), "") }

fun scanSms(
    context: Context,
    fromMillis: Long,
    toMillisExclusive: Long,
    cats: List<Cat>,
    importedKeys: Set<String>,
    rules: Map<String, String>,
): ScanResult {
    val msgs = readInbox(context, fromMillis, toMillisExclusive)
    var already = 0
    val seen = HashSet<String>()
    val items = mutableListOf<SmsCandidate>()
    for (m in msgs) {
        // bank / UPI alerts come from named senders like "VM-HDFCBK"; plain phone numbers are people
        if (!m.sender.any { it.isLetter() }) continue
        val p = parseSms(m.body) ?: continue
        val key = smsKey(m.sender, m.body, m.dateMillis)
        if (!seen.add(key)) continue
        if (key in importedKeys) {
            already++
            continue
        }
        val note = smsNote(p, m.sender)
        items.add(
            SmsCandidate(
                key = key,
                sender = m.sender,
                body = m.body.trim(),
                dateMillis = m.dateMillis,
                amount = p.amount,
                type = p.type,
                note = note,
                method = p.method,
                catName = suggestCategory(p, m.body, cats, rules),
            )
        )
    }
    return ScanResult(items, already, msgs.size)
}
