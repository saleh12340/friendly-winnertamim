package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class OmniViewModel(private val repository: NoteRepository) : ViewModel() {
    private val _currentNoteId = MutableStateFlow<Long?>(null)
    val currentNoteId: StateFlow<Long?> = _currentNoteId.asStateFlow()
    private var customerUpdateJob: Job? = null
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentNote: StateFlow<Note?> = _currentNoteId.flatMapLatest { id -> if (id == null) flowOf(null) else repository.allNotes.map { notes -> notes.find { it.id == id } } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentItems: StateFlow<List<NoteItem>> = _currentNoteId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.getItemsForNote(id) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allNotes: StateFlow<List<Note>> = repository.allNotes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val suggestions: StateFlow<List<Suggestion>> = repository.suggestions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allPayments: StateFlow<List<CustomerPayment>> = repository.allPayments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val customerNames: StateFlow<List<String>> = allNotes.map { notes -> notes.map { it.customerName.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.sortedWith(String.CASE_INSENSITIVE_ORDER) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    init { viewModelScope.launch { repository.lastNoteId.collect { id -> if (id != null && _currentNoteId.value == null) _currentNoteId.value = id else if (_currentNoteId.value == null) createNewNote() } } }
    fun selectNote(id: Long) { _currentNoteId.value = id; viewModelScope.launch { repository.setLastNoteId(id) } }
    fun createNewNote() { viewModelScope.launch { val notes = repository.allNotes.first(); val nextNumber = if (notes.isEmpty()) "1" else ((notes.mapNotNull { it.invoiceNumber.toIntOrNull() }.maxOrNull() ?: 0) + 1).toString(); selectNote(repository.saveNote(Note(title = "فاتورة $nextNumber", invoiceNumber = nextNumber))) } }
    fun addItem(name: String, quantity: Double, price: Double, section: String) { val id = _currentNoteId.value ?: return; viewModelScope.launch { repository.saveItem(NoteItem(noteId = id, name = name, quantity = quantity, price = price, section = section)); repository.allNotes.first().find { it.id == id }?.let { repository.saveInvoiceFile(it, repository.getItemsForNote(id).first()) } } }
    fun updateCustomerName(name: String) { val note = currentNote.value ?: return; customerUpdateJob?.cancel(); customerUpdateJob = viewModelScope.launch { delay(180); val normalized = name.trim().replace(Regex("\\s+"), " "); val updated = note.copy(customerName = normalized); repository.saveNote(updated); repository.saveInvoiceFile(updated, currentItems.value) } }
    fun updateInvoiceNumber(number: String) { val note = currentNote.value ?: return; viewModelScope.launch { val updated = note.copy(invoiceNumber = number); repository.saveNote(updated); repository.saveInvoiceFile(updated, currentItems.value) } }
    fun deleteItem(item: NoteItem) { viewModelScope.launch { repository.deleteItem(item); repository.allNotes.first().find { it.id == item.noteId }?.let { repository.saveInvoiceFile(it, repository.getItemsForNote(item.noteId).first()) } } }
    fun updateItem(item: NoteItem) { viewModelScope.launch { repository.saveItem(item); repository.allNotes.first().find { it.id == item.noteId }?.let { repository.saveInvoiceFile(it, repository.getItemsForNote(item.noteId).first()) } } }
    fun clearCurrentNote() { _currentNoteId.value?.let { id -> viewModelScope.launch { repository.clearNote(id); currentNote.value?.let { repository.saveInvoiceFile(it, emptyList()) } } } }
    fun updateNoteSettings(fontSize: Int, scrollEnabled: Boolean) { val note = currentNote.value ?: return; viewModelScope.launch { repository.saveNote(note.copy(fontSize = fontSize.coerceIn(10, 14), scrollEnabled = scrollEnabled)) } }
    fun deleteNote(note: Note) { viewModelScope.launch { repository.deleteNote(note); if (_currentNoteId.value == note.id) { val remaining = repository.allNotes.first(); if (remaining.isNotEmpty()) selectNote(remaining.first().id) else createNewNote() } } }
    fun addCustomerPayment(customerName: String, amount: Double, details: String) { val name = customerName.trim().replace(Regex("\\s+"), " "); if (name.isBlank() || amount <= 0.0) return; viewModelScope.launch { repository.savePayment(CustomerPayment(customerName = name, amount = amount, details = details.ifBlank { "دفعة" })) } }
    fun deleteCustomerPayment(payment: CustomerPayment) { viewModelScope.launch { repository.deletePayment(payment) } }
    fun paymentsForCustomer(customerName: String): Flow<List<CustomerPayment>> = repository.paymentsForCustomer(customerName)
    fun itemsForInvoice(noteId: Long): Flow<List<NoteItem>> = repository.getItemsForNote(noteId)
    fun invoiceTotal(noteId: Long): Flow<Double> = repository.invoiceTotal(noteId)
    fun shareCustomerStatement(customerName: String) { viewModelScope.launch { repository.shareCustomerStatement(customerName) } }
    suspend fun customerBalanceSnapshot(customerName: String, excludeInvoiceId: Long? = null): Double { val name = customerName.trim(); if (name.isBlank()) return 0.0; val notes = repository.allNotes.first().filter { it.customerName.trim().equals(name, true) && it.id != excludeInvoiceId }; val invoiceTotal = notes.sumOf { invoiceTotal(it.id).first() }; val paid = repository.paymentsForCustomer(name).first().sumOf { it.amount }; return invoiceTotal - paid }
    suspend fun customerInvoiceTotalSnapshot(customerName: String): Double { val name = customerName.trim(); return repository.allNotes.first().filter { it.customerName.trim().equals(name, true) }.sumOf { invoiceTotal(it.id).first() } }
    suspend fun createBackupJson(): String = repository.createFullBackup()
    suspend fun restoreBackupJson(text: String): Long? { val restoredId = repository.restoreFullBackup(text); val restoredNotes = repository.allNotes.first(); val selectedId = restoredId?.takeIf { id -> restoredNotes.any { it.id == id } } ?: restoredNotes.firstOrNull()?.id; if (selectedId != null) selectNote(selectedId) else createNewNote(); return selectedId }
    suspend fun saveCurrentInvoiceFile(): Boolean { val note = currentNote.value ?: return false; return repository.saveInvoiceFile(note, currentItems.value) }
    fun shareCurrentInvoice() { val note = currentNote.value ?: return; viewModelScope.launch { repository.shareInvoiceReceipt(note, currentItems.value) } }
}
