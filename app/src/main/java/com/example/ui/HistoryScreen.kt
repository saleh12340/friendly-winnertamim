package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.CustomerPayment
import com.example.data.Note
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: OmniViewModel, onBack: () -> Unit) {
    val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.history)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { pad ->
        if (noteToDelete != null) AlertDialog(onDismissRequest = { noteToDelete = null }, title = { Text(stringResource(R.string.delete)) }, text = { Text(stringResource(R.string.confirm_delete)) }, confirmButton = { TextButton(onClick = { noteToDelete?.let { viewModel.deleteNote(it) }; noteToDelete = null }) { Text(stringResource(R.string.confirm)) } }, dismissButton = { TextButton(onClick = { noteToDelete = null }) { Text(stringResource(R.string.cancel)) } })
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(8.dp)) {
            items(allNotes, key = { it.id }) { note ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { viewModel.selectNote(note.id); onBack() }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (note.customerName.isNotBlank()) note.customerName else note.title, style = MaterialTheme.typography.titleMedium)
                            if (note.invoiceNumber.isNotBlank()) Text("${stringResource(R.string.invoice_number)}: ${note.invoiceNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            Text(SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(note.timestamp)), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { noteToDelete = note }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerAccountsScreen(viewModel: OmniViewModel, onBack: () -> Unit) {
    val notes by viewModel.allNotes.collectAsStateWithLifecycle()
    val payments by viewModel.allPayments.collectAsStateWithLifecycle()
    val customerNames by viewModel.customerNames.collectAsStateWithLifecycle()
    var selectedName by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf(emptyList<CustomerAccountRow>()) }
    var showPayment by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(notes, payments, customerNames) {
        rows = customerNames.map { name ->
            val customerNotes = notes.filter { it.customerName.trim().equals(name, true) }.sortedByDescending { it.timestamp }
            val total = customerNotes.sumOf { viewModel.invoiceTotal(it.id).first() }
            val paid = payments.filter { it.customerName.trim().equals(name, true) }.sumOf { it.amount }
            CustomerAccountRow(name, customerNotes, total, paid)
        }
    }
    val selected = rows.firstOrNull { it.name.equals(selectedName, true) }

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (selected == null) "حسابات العملاء" else "حساب العميل: ${selected.name}") }, navigationIcon = { IconButton(onClick = { if (selected == null) onBack() else selectedName = null }) { Icon(Icons.Default.ArrowBack, null) } }, actions = { if (selected != null) IconButton(onClick = { viewModel.shareCustomerStatement(selected.name) }) { Icon(Icons.Default.Share, "مشاركة كشف الحساب") } })
    }) { pad ->
        if (showPayment && selected != null) {
            var amount by remember { mutableStateOf("") }
            var details by remember { mutableStateOf("دفعة") }
            AlertDialog(onDismissRequest = { showPayment = false }, title = { Text("إضافة دفعة") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("العميل: ${selected.name}"); OutlinedTextField(amount, { amount = it }, label = { Text("المبلغ") }, singleLine = true); OutlinedTextField(details, { details = it }, label = { Text("البيان") }, singleLine = true) } }, confirmButton = { TextButton(onClick = { viewModel.addCustomerPayment(selected.name, amount.toDoubleOrNull() ?: 0.0, details); showPayment = false }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = { showPayment = false }) { Text("إلغاء") } })
        }
        if (selected == null) {
            LazyColumn(Modifier.padding(pad).fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { it.name }) { row ->
                    val color = balanceColor(row.balance)
                    Card(Modifier.fillMaxWidth().clickable { selectedName = row.name }) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(row.name, style = MaterialTheme.typography.titleMedium); Text("${row.invoices.size} فاتورة • إجمالي ${money(row.invoiceTotal)}", style = MaterialTheme.typography.bodySmall); Text("الرصيد: ${money(kotlin.math.abs(row.balance))} ${balanceLabel(row.balance)}", color = color) }; Icon(Icons.Default.ChevronLeft, null) } }
                }
                if (rows.isEmpty()) item { Text("لا توجد حسابات عملاء بعد", Modifier.padding(24.dp)) }
            }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { val color = balanceColor(selected!!.balance); Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(selected.name, style = MaterialTheme.typography.titleLarge); Text("إجمالي الفواتير: ${money(selected.invoiceTotal)}"); Text("إجمالي المدفوع: ${money(selected.paid)}"); Text("الرصيد: ${money(kotlin.math.abs(selected.balance))} ${balanceLabel(selected.balance)}", color = color, style = MaterialTheme.typography.titleMedium); Button(onClick = { showPayment = true }, Modifier.fillMaxWidth()) { Text("إضافة دفعة") } } } }
                item { Text("فواتير العميل", style = MaterialTheme.typography.titleMedium) }
                items(selected!!.invoices, key = { it.id }) { note ->
                    val total by viewModel.invoiceTotal(note.id).collectAsStateWithLifecycle(initialValue = 0.0)
                    Card(Modifier.fillMaxWidth().clickable { viewModel.selectNote(note.id); onBack() }) { Column(Modifier.padding(14.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("فاتورة ${note.invoiceNumber.ifBlank { note.id.toString() }}", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold); Text(money(total), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }; Text(dateFormat.format(Date(note.timestamp)), style = MaterialTheme.typography.bodySmall) } }
                }
                item {
                    val customerPayments = payments.filter { it.customerName.trim().equals(selected!!.name, true) }.sortedByDescending { it.timestamp }
                    if (customerPayments.isNotEmpty()) { Text("الدفعات", style = MaterialTheme.typography.titleMedium); customerPayments.forEach { payment -> ListItem(headlineContent = { Text("دفعة: ${money(payment.amount)}") }, supportingContent = { Text("${dateFormat.format(Date(payment.timestamp))} — ${payment.details}") }, trailingContent = { IconButton(onClick = { viewModel.deleteCustomerPayment(payment) }) { Icon(Icons.Default.Delete, "حذف") } }) } }
                }
            }
        }
    }
}

data class CustomerAccountRow(val name: String, val invoices: List<Note>, val invoiceTotal: Double, val paid: Double) { val balance: Double get() = invoiceTotal - paid }
private fun money(value: Double): String = if (value.isFinite() && value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(Locale.US, value)
private fun balanceColor(value: Double): Color = if (value > 0.005) Color.Red else if (value < -0.005) Color(0xFF64B5F6) else Color.Unspecified
private fun balanceLabel(value: Double): String = if (value > 0.005) "عليه" else if (value < -0.005) "له" else "متساوٍ"
