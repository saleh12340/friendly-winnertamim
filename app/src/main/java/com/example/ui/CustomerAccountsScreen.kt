package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CustomerPayment
import com.example.data.Note
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun money(value: Double): String = if (value.isFinite() && value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(Locale.US, value)
private fun dateTime(time: Long): String = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(time))
private fun balanceColor(balance: Double) = if (balance > 0.009) Color(0xFFD32F2F) else if (balance < -0.009) Color(0xFF64B5F6) else Color.Unspecified

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerAccountsScreen(viewModel: OmniViewModel, onBack: () -> Unit, onOpenInvoice: (Long) -> Unit, selectedCustomer: String? = null) {
    val notes by viewModel.allNotes.collectAsStateWithLifecycle()
    val payments by viewModel.allPayments.collectAsStateWithLifecycle()
    val customers by viewModel.customerNames.collectAsStateWithLifecycle()
    val groupedNotes = notes.groupBy { it.customerName.trim() }.filterKeys { it.isNotBlank() }
    var selected by remember(selectedCustomer) { mutableStateOf(selectedCustomer) }
    var showPayment by remember { mutableStateOf(false) }

    if (selected != null) {
        CustomerAccountDetail(selected!!, groupedNotes[selected!!].orEmpty(), payments.filter { it.customerName.equals(selected!!, true) }, viewModel, { selected = null }, onOpenInvoice) { showPayment = true }
        if (showPayment) PaymentDialog(selected!!, { showPayment = false }) { amount, details -> viewModel.addCustomerPayment(selected!!, amount, details); showPayment = false }
        return
    }
    Scaffold(topBar = { TopAppBar(title = { Text("حسابات العملاء", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "رجوع") } }) }) { pad ->
        if (customers.isEmpty()) Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Person, null, Modifier.size(52.dp)); Spacer(Modifier.height(8.dp)); Text("لا يوجد عملاء مسجلون بعد"); Text("اكتب اسم العميل في الفاتورة وسيُحفظ تلقائياً", style = MaterialTheme.typography.bodySmall) } }
        else LazyColumn(Modifier.fillMaxSize().padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(customers, key = { it.lowercase() }) { name ->
                var total by remember(name, notes) { mutableStateOf(0.0) }
                LaunchedEffect(name, notes) { total = groupedNotes[name].orEmpty().fold(0.0) { acc, n -> acc + viewModel.invoiceTotal(n.id).first() } }
                val paid = payments.filter { it.customerName.equals(name, true) }.sumOf { it.amount }
                CustomerCard(name, groupedNotes[name].orEmpty().size, total - paid) { selected = name }
            }
        }
    }
}

@Composable
private fun CustomerCard(name: String, invoiceCount: Int, balance: Double, onClick: () -> Unit) {
    val color = balanceColor(balance).takeUnless { it == Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Card(onClick = onClick, Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Person, null, Modifier.size(34.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold); Text("$invoiceCount فاتورة", style = MaterialTheme.typography.bodySmall) }
        Column(horizontalAlignment = Alignment.End) { Text("الرصيد", style = MaterialTheme.typography.bodySmall); Text(money(kotlin.math.abs(balance)), color = color, fontWeight = FontWeight.Bold); Text(if (balance > 0.009) "عليه" else if (balance < -0.009) "له" else "مسدد", color = color, style = MaterialTheme.typography.labelSmall) }
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerAccountDetail(customerName: String, notes: List<Note>, payments: List<CustomerPayment>, viewModel: OmniViewModel, onBack: () -> Unit, onOpenInvoice: (Long) -> Unit, onAddPayment: () -> Unit) {
    var totals by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    LaunchedEffect(notes) { totals = notes.associate { note -> note.id to viewModel.invoiceTotal(note.id).first() } }
    val invoiceTotal = totals.values.sum()
    val paid = payments.sumOf { it.amount }
    val balance = invoiceTotal - paid
    val color = balanceColor(balance).takeUnless { it == Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Scaffold(topBar = { TopAppBar(title = { Text(customerName, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "رجوع") } }, actions = { IconButton(onClick = { viewModel.shareCustomerStatement(customerName) }) { Icon(Icons.Default.Share, "مشاركة كشف الحساب") } }) }, floatingActionButton = { FloatingActionButton(onClick = onAddPayment) { Icon(Icons.Default.Add, "إضافة دفعة") } }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.fillMaxWidth().padding(16.dp)) { Text("كشف حساب العميل", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("إجمالي الفواتير"); Text(money(invoiceTotal), fontWeight = FontWeight.Bold) }; Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("إجمالي المدفوع"); Text(money(paid), fontWeight = FontWeight.Bold) }; HorizontalDivider(Modifier.padding(vertical = 6.dp)); Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(if (balance > 0.009) "الرصيد عليه" else if (balance < -0.009) "الرصيد له" else "الرصيد مسدد", fontWeight = FontWeight.Bold); Text(money(kotlin.math.abs(balance)), color = color, fontWeight = FontWeight.Bold) } } } }
            item { Text("فواتير العميل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(notes.sortedByDescending { it.timestamp }, key = { it.id }) { note ->
                val count by viewModel.itemsForInvoice(note.id).collectAsStateWithLifecycle(initialValue = emptyList())
                Card(onClick = { onOpenInvoice(note.id) }, Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("فاتورة ${note.invoiceNumber.ifBlank { note.id.toString() }}", fontWeight = FontWeight.Bold); Text(money(totals[note.id] ?: 0.0), fontWeight = FontWeight.Bold) }; Text(dateTime(note.timestamp), style = MaterialTheme.typography.bodySmall); Text("الأصناف: ${count.size}", style = MaterialTheme.typography.bodySmall) } }
            }
            if (payments.isNotEmpty()) { item { Text("الدفعات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }; items(payments, key = { it.id }) { payment -> Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween) { Column { Text(payment.details); Text(dateTime(payment.timestamp), style = MaterialTheme.typography.bodySmall) }; Text(money(payment.amount), color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold) } } } }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun PaymentDialog(customerName: String, onDismiss: () -> Unit, onSave: (Double, String) -> Unit) {
    var amount by remember { mutableStateOf("") }; var details by remember { mutableStateOf("دفعة") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إضافة دفعة — $customerName") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(amount, { amount = it }, label = { Text("المبلغ") }, singleLine = true); OutlinedTextField(details, { details = it }, label = { Text("البيان") }, singleLine = true) } }, confirmButton = { TextButton(onClick = { amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { onSave(it, details) } }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}
