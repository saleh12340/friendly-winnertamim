package com.example.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

@Serializable
data class AppBackup(
    val version: Int = 2,
    val createdAt: Long = System.currentTimeMillis(),
    val notes: List<Note>,
    val items: List<NoteItem>,
    val suggestions: List<Suggestion>,
    val payments: List<CustomerPayment> = emptyList(),
    val lastNoteId: Long? = null
)

class BackupManager(private val context: Context) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun createBackup(notes: List<Note>, items: List<NoteItem>, suggestions: List<Suggestion>, payments: List<CustomerPayment>, lastNoteId: Long?): String =
        json.encodeToString(AppBackup.serializer(), AppBackup(notes = notes, items = items, suggestions = suggestions, payments = payments, lastNoteId = lastNoteId))

    fun parseBackup(text: String): AppBackup = json.decodeFromString(AppBackup.serializer(), text)

    fun saveTextFile(relativeFolder: String, displayName: String, text: String): Boolean {
        return try {
            val resolver = context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + relativeFolder)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0); resolver.update(uri, values, null, null)
                true
            } else {
                val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), relativeFolder)
                if (!targetDir.exists()) targetDir.mkdirs()
                FileOutputStream(File(targetDir, displayName)).use { it.write(text.toByteArray(Charsets.UTF_8)) }
                true
            }
        } catch (_: Exception) { false }
    }

    fun saveOrUpdateInvoiceFile(relativeFolder: String, note: Note, text: String): Boolean {
        val invNum = note.invoiceNumber.trim().ifEmpty { note.id.toString() }
        val customer = note.customerName.trim().ifEmpty { "بدون_عميل" }.replace(Regex("[^\\p{L}\\p{N}_-]"), "_")
        val displayName = "فاتورة_${invNum}_${customer}.txt"
        val prefix = "فاتورة_${invNum}_"
        return try {
            val resolver = context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
                val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND (${MediaStore.Downloads.DISPLAY_NAME} = ? OR ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?)"
                val args = arrayOf("%$relativeFolder%", displayName, "$prefix%")
                var existingUri: Uri? = null
                var oldName = false
                resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                        existingUri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                        oldName = c.getString(c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)) != displayName
                    }
                }
                if (existingUri != null) {
                    if (oldName) resolver.update(existingUri!!, ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, displayName) }, null, null)
                    resolver.openOutputStream(existingUri!!, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    true
                } else {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, displayName); put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + relativeFolder); put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                    resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0); resolver.update(uri, values, null, null); true
                }
            } else {
                val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), relativeFolder)
                if (!targetDir.exists()) targetDir.mkdirs()
                targetDir.listFiles { _, name -> name.startsWith(prefix) && name.endsWith(".txt") }?.forEach { if (it.name != displayName) it.delete() }
                FileOutputStream(File(targetDir, displayName)).use { it.write(text.toByteArray(Charsets.UTF_8)) }
                true
            }
        } catch (_: Exception) { false }
    }

    fun shareReceipt(title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, title); putExtra(Intent.EXTRA_TEXT, text); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(Intent.createChooser(intent, "مشاركة الفاتورة كنص").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}
