package com.example.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<Note>>
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): Note?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<Note>)
    @Delete
    suspend fun deleteNote(note: Note)
    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()
    @Query("SELECT * FROM notes")
    suspend fun getAllNotesSnapshot(): List<Note>

    @Query("SELECT * FROM note_items WHERE noteId = :noteId ORDER BY timestamp ASC")
    fun getItemsForNote(noteId: Long): Flow<List<NoteItem>>
    @Query("SELECT COALESCE(SUM(quantity * price), 0.0) FROM note_items WHERE noteId = :noteId")
    fun getInvoiceTotal(noteId: Long): Flow<Double>
    @Query("SELECT * FROM note_items")
    suspend fun getAllItemsSnapshot(): List<NoteItem>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: NoteItem)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<NoteItem>)
    @Delete
    suspend fun deleteItem(item: NoteItem)
    @Query("DELETE FROM note_items WHERE noteId = :noteId")
    suspend fun clearItemsForNote(noteId: Long)
    @Query("DELETE FROM note_items")
    suspend fun deleteAllItems()

    @Query("SELECT * FROM suggestions ORDER BY count DESC LIMIT 50")
    fun getSuggestions(): Flow<List<Suggestion>>
    @Query("SELECT * FROM suggestions")
    suspend fun getAllSuggestionsSnapshot(): List<Suggestion>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuggestion(suggestion: Suggestion)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuggestions(items: List<Suggestion>)
    @Delete
    suspend fun deleteSuggestion(suggestion: Suggestion)
    @Query("DELETE FROM suggestions")
    suspend fun deleteAllSuggestions()
    @Query("SELECT * FROM suggestions WHERE word = :word")
    suspend fun getSuggestionByWord(word: String): Suggestion?

    @Query("SELECT * FROM customer_payments ORDER BY timestamp DESC")
    fun getAllPayments(): Flow<List<CustomerPayment>>
    @Query("SELECT * FROM customer_payments WHERE customerName = :customerName ORDER BY timestamp DESC")
    fun getPaymentsForCustomer(customerName: String): Flow<List<CustomerPayment>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: CustomerPayment): Long
    @Delete
    suspend fun deletePayment(payment: CustomerPayment)
    @Query("DELETE FROM customer_payments")
    suspend fun deleteAllPayments()
    @Query("SELECT * FROM customer_payments")
    suspend fun getAllPaymentsSnapshot(): List<CustomerPayment>
}

@Database(entities = [Note::class, NoteItem::class, Suggestion::class, CustomerPayment::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS customer_payments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, customerName TEXT NOT NULL, amount REAL NOT NULL, details TEXT NOT NULL, timestamp INTEGER NOT NULL)")
            }
        }
    }
}
