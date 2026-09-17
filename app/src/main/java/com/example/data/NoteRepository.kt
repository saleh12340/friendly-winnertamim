package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.dataStore by preferencesDataStore(name = "settings")

class NoteRepository(private val noteDao: NoteDao, private val context: Context) {
    val allNotes = noteDao.getAllNotes()
    val suggestions = noteDao.getSuggestions()
    val allPayments = noteDao.getAllPayments()
    fun getItemsForNote(noteId: Long) = noteDao.getItemsForNote(noteId)
    fun invoiceTotal(noteId: Long): Flow<Double> = noteDao.getInvoiceTotal(noteId)
    fun paymentsForCustomer(customerName: String) = noteDao.getPaymentsForCustomer(customerName.trim())

    suspend fun saveNote(note: Note) = noteDao.insertNote(note)
    suspend fun deleteNote(note: Note) = noteDao.deleteNote(note)
    suspend fun savePayment(payment: CustomerPayment) = noteDao.insertPayment(payment)
    suspend fun deletePayment(payment: CustomerPayment) = noteDao.deletePayment(payment)

    suspend fun saveItem(item: NoteItem) {
        noteDao.insertItem(item)
        val normalizedPhrase = item.name.trim().replace(Regex("\\s+"), " ")
        if (normalizedPhrase.length > 1) {
            val existingPhrase = noteDao.getSuggestionByWord(normalizedPhrase)
            if (existingPhrase != null) noteDao.insertSuggestion(existingPhrase.copy(count = existingPhrase.count + 1))
            else noteDao.insertSuggestion(Suggestion(word = normalizedPhrase))
        }
        normalizedPhrase.split(" ").forEach { word ->
            if (word.length > 1) {
                val existing = noteDao.getSuggestionByWord(word)
                if (existing != null) noteDao.insertSuggestion(existing.copy(count = existing.count + 1))
                else noteDao.insertSuggestion(Suggestion(word = word))
            }
        }
    }

    suspend fun deleteItem(item: NoteItem) = noteDao.deleteItem(item)
    suspend fun clearNote(noteId: Long) = noteDao.clearItemsForNote(noteId)

    private val LAST_NOTE_ID = longPreferencesKey("last_note_id")
    val lastNoteId: Flow<Long?> = context.dataStore.data.map { it[LAST_NOTE_ID] }
    suspend fun setLastNoteId(id: Long) { context.dataStore.edit { it[LAST_NOTE_ID] = id } }

    suspend fun createFullBackup(): String = BackupManager(context).createBackup(noteDao.getAllNotesSnapshot(), noteDao.getAllItemsSnapshot(), noteDao.getAllSuggestionsSnapshot(), noteDao.getAllPaymentsSnapshot(), lastNoteId.first())

    suspend fun restoreFullBackup(text: String): Long? {
        val backup = BackupManager(context).parseBackup(text)
        noteDao.deleteAllItems(); noteDao.deleteAllNotes(); noteDao.deleteAllSuggestions(); noteDao.deleteAllPayments()
        if (backup.notes.isNotEmpty()) noteDao.insertNotes(backup.notes)
        if (backup.items.isNotEmpty()) noteDao.insertItems(backup.items)
        if (backup.suggestions.isNotEmpty()) noteDao.insertSuggestions(backup.suggestions)
        if (backup.payments.isNotEmpty()) backup.payments.forEach { noteDao.insertPayment(it) }
        backup.lastNoteId?.let { setLastNoteId(it) }
        return backup.lastNoteId
    }

    suspend fun saveInvoiceFile(note: Note, items: List<NoteItem>): Boolean = BackupManager(context).saveOrUpdateInvoiceFile("New-Tamim-invoices/Invoices", note, receiptText(note, items))

    suspend fun shareInvoiceReceipt(note: Note, items: List<NoteItem>) {
        val total = items.sumOf { it.quantity * it.price }
        val customerBalance = customerBalance(note.customerName, note.id) + total
        val text = receiptText(note, items, customerBalance)
        val title = "فاتورة_${note.invoiceNumber.ifBlank { note.id.toString() }}_${note.customerName.ifBlank { "عميل" }}"
        try {
            val imageFile = createReceiptImage(note, items, total, customerBalance)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"; putExtra(Intent.EXTRA_SUBJECT, title); putExtra(Intent.EXTRA_TEXT, text); putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                clipData = android.content.ClipData.newRawUri("إيصال الفاتورة", uri)
            }
            context.startActivity(Intent.createChooser(intent, "مشاركة الفاتورة: صورة + نص").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        } catch (_: Exception) { BackupManager(context).shareReceipt(title, text) }
    }

    private suspend fun customerBalance(customerName: String, excludeInvoiceId: Long? = null): Double {
        val name = customerName.trim()
        if (name.isBlank()) return 0.0
        val notes = allNotes.first().filter { it.customerName.trim().equals(name, true) && it.id != excludeInvoiceId }
        val invoices = notes.sumOf { invoiceTotal(it.id).first() }
        val paid = paymentsForCustomer(name).first().sumOf { it.amount }
        return invoices - paid
    }

    suspend fun shareCustomerStatement(customerName: String) {
        val notes = allNotes.first().filter { it.customerName.trim().equals(customerName.trim(), true) }.sortedByDescending { it.timestamp }
        val payments = paymentsForCustomer(customerName).first()
        val lines = buildString {
            appendLine("كشف حساب العميل")
            appendLine("العميل: $customerName")
            appendLine("==============================")
            var totalInvoices = 0.0
            notes.forEach { note -> val total = invoiceTotal(note.id).first(); totalInvoices += total; appendLine("فاتورة ${note.invoiceNumber.ifBlank { note.id.toString() }} | ${dateTime(note.timestamp)} | ${formatMoney(total)}") }
            val totalPaid = payments.sumOf { it.amount }
            appendLine("------------------------------")
            appendLine("إجمالي الفواتير: ${formatMoney(totalInvoices)}")
            appendLine("إجمالي المدفوع: ${formatMoney(totalPaid)}")
            appendLine("الرصيد عليه/له: ${formatMoney(totalInvoices - totalPaid)}")
        }
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "كشف حساب $customerName"); putExtra(Intent.EXTRA_TEXT, lines); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(Intent.createChooser(intent, "مشاركة كشف الحساب").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }

    private suspend fun receiptText(note: Note, items: List<NoteItem>, balanceAfter: Double? = null): String {
        val total = items.sumOf { it.quantity * it.price }
        val invNum = note.invoiceNumber.trim().ifEmpty { note.id.toString() }
        val customer = note.customerName.trim().ifEmpty { "عميل عام" }
        return buildString {
            appendLine("فاتورة مبيعات"); appendLine("رقم الفاتورة: $invNum"); appendLine("العميل: $customer"); appendLine("التاريخ: ${dateTime(note.timestamp)}"); appendLine("------------------------------")
            items.forEach { item -> appendLine("${item.name} × ${formatMoney(item.quantity)} = ${formatMoney(item.quantity * item.price)}") }
            appendLine("------------------------------"); appendLine("الإجمالي: ${formatMoney(total)}")
            if (balanceAfter != null && note.customerName.isNotBlank()) appendLine("رصيد العميل بعد الفاتورة: ${formatMoney(balanceAfter)} ${if (balanceAfter > 0.005) "عليه" else if (balanceAfter < -0.005) "له" else "متساوٍ"}")
            appendLine("شكراً لتعاملكم معنا")
        }
    }

    private fun createReceiptImage(note: Note, items: List<NoteItem>, total: Double, balanceAfter: Double? = null): File {
        val width = 720
        val text = "${receiptTextSync(note, items)}${if (balanceAfter != null && note.customerName.isNotBlank()) "\nرصيد العميل بعد الفاتورة: ${formatMoney(balanceAfter)} ${if (balanceAfter > 0.005) "عليه" else if (balanceAfter < -0.005) "له" else "متساوٍ"}" else ""}"
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 30f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width - 48).setAlignment(Layout.Alignment.ALIGN_NORMAL).setTextDirection(TextDirectionHeuristics.RTL).setIncludePad(true).build()
        val bitmap = Bitmap.createBitmap(width, layout.height + 80, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(android.graphics.Color.WHITE); layout.draw(canvas)
        val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 38f; typeface = Typeface.DEFAULT_BOLD }
        canvas.drawText("الإجمالي: ${formatMoney(total)}", 48f, layout.height + 58f, totalPaint)
        val dir = File(context.filesDir, "receipts").apply { mkdirs() }
        val file = File(dir, "receipt_${note.id}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    private fun receiptTextSync(note: Note, items: List<NoteItem>): String {
        val total = items.sumOf { it.quantity * it.price }
        return buildString { appendLine("فاتورة مبيعات"); appendLine("رقم الفاتورة: ${note.invoiceNumber.ifBlank { note.id.toString() }}"); appendLine("العميل: ${note.customerName.ifBlank { "عميل عام" }}"); appendLine("التاريخ: ${dateTime(note.timestamp)}"); appendLine("------------------------------"); items.forEach { appendLine("${it.name} × ${formatMoney(it.quantity)} = ${formatMoney(it.quantity * it.price)}") }; appendLine("------------------------------"); appendLine("الإجمالي: ${formatMoney(total)}") }
    }

    private fun dateTime(time: Long): String = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(time))
    private fun formatMoney(value: Double): String = if (value.isFinite() && value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(Locale.US, value)
}
