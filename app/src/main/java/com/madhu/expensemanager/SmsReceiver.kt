package com.madhu.expensemanager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.NotificationCompat

/** Wakes up whenever an SMS arrives (even if the app is closed) and adds bank alerts when auto-add is on. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        // long messages arrive in several parts: join the parts that share a sender
        val bySender = LinkedHashMap<String, StringBuilder>()
        for (m in msgs) {
            bySender.getOrPut(m.originatingAddress ?: "") { StringBuilder() }.append(m.messageBody ?: "")
        }
        val now = System.currentTimeMillis()
        for ((sender, body) in bySender) {
            try {
                AutoCapture.handle(context.applicationContext, sender, body.toString(), now)
            } catch (e: Exception) {
                // never crash the receiver because of one odd message
            }
        }
    }
}

/** "Undo" button on the notification: removes the transaction that was just added. */
class UndoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val txId = intent.getLongExtra("txId", -1L)
        if (txId > 0) {
            val prefs = appPrefs(context)
            writeTransactions(prefs, readTransactions(prefs).filter { it.id != txId })
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(intent.getIntExtra("notifId", 0))
    }
}

object AutoCapture {
    private const val CHANNEL = "captured_sms"

    fun handle(context: Context, sender: String, body: String, dateMillis: Long) {
        val prefs = appPrefs(context)
        if (!prefs.getBoolean("smsAuto", false)) return
        if (!sender.any { it.isLetter() }) return // plain phone numbers are people, not banks

        val p = parseSms(body) ?: return
        val key = smsKey(sender, body, dateMillis)
        if (key in readSmsKeys(prefs)) return

        val cats = readCategories(prefs)
        val cand = SmsCandidate(
            key = key,
            sender = sender,
            body = body.trim(),
            dateMillis = dateMillis,
            amount = p.amount,
            type = p.type,
            note = smsNote(p, sender),
            method = p.method,
            catName = suggestCategory(p, body, cats, readSmsRules(prefs)),
        )
        val tx = appendSmsItems(prefs, listOf(cand)).firstOrNull() ?: return
        notifyAdded(context, tx, cats)
    }

    private fun notifyAdded(context: Context, tx: Tx, cats: List<Cat>) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Captured SMS transactions", NotificationManager.IMPORTANCE_DEFAULT)
            )
            val id = (tx.id % Int.MAX_VALUE).toInt()
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            val openIntent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val open = PendingIntent.getActivity(context, id, openIntent, flags)

            val undoIntent = Intent(context, UndoReceiver::class.java)
                .putExtra("txId", tx.id)
                .putExtra("notifId", id)
            val undo = PendingIntent.getBroadcast(context, id, undoIntent, flags)

            val cat = parseCat(tx.category, cats)
            val sign = if (tx.type == TxType.EXPENSE) "-" else "+"
            val title = sign + fmt(tx.amount) + if (tx.note.isNotEmpty()) " • " + tx.note else ""
            val text = "Added to ${cat.emoji} ${cat.name} via ${tx.method}. Tap to review."

            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(0, "Undo", undo)
                .build()
            nm.notify(id, n)
        } catch (e: Exception) {
            // notifications may be switched off; the transaction is already saved
        }
    }
}
