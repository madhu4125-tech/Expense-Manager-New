package com.madhu.expensemanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

fun toast(context: Context, msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}

fun shareText(context: Context, filename: String, text: String, mime: String) {
    try {
        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        val file = File(dir, filename)
        file.writeText(text, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Save or send file"))
    } catch (e: Exception) {
        toast(context, "Export failed: " + (e.message ?: "unknown error"))
    }
}

fun readUriText(context: Context, uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
} catch (e: Exception) {
    null
}
