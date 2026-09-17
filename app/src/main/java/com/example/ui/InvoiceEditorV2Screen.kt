package com.example.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.NoteItem
import java.util.Locale

private fun moneyV2(v: Double): String = if (v.isFinite() && v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(Locale.US, v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceEditorV2Screen(
    viewModel: OmniViewModel,
    onOpenHistory: () -> Unit,
    onOpenCustomers: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val note by viewModel.currentNote.collectAsStateWithLifecycle()
    val items by viewModel.currentItems.collectAsStateWithLifecycle()
    val customers by viewModel.customerNames.collectAsStateWithLifecycle()
    val notes by viewModel.allNotes.collectAsStateWithLifecycle()
    val payments by viewModel.allPayments.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var customer by remember { mutableStateOf("") }
    var invoiceNumber by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("1") }
    var itemName by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var showCustomerSuggestions by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf<NoteItem?>(null) }

    LaunchedEffect(note?.id) {
        customer = note?.customerName.orEmpty()
        invoiceNumber = note?.invoiceNumber.orEmpty()
        editingId = null
        totalText = ""
        qtyText = "1"
        itemName = ""
    }

    val price = (totalText.toDoubleOrNull() ?: 0.0).let { t ->
        val q = qtyText.toDoubleOrNull() ?: 0.0
        if (q > 0) t / q else 0.0
    }
    val grandTotal = items.sumOf { it.quantity * it.price }
    val matchingCustomers = customers.filter { it.contains(customer.trim(), ignoreCase = true) }.take(6)
    val customerBalance by produceState(0.0, customer, notes, payments) {
        val name = customer.trim()
        if (name.isBlank()) value = 0.0 else {
            val invoiceSum = notes.filter { it.customerName.trim().equals(name, true) }.sumOf { n -> viewModel.itemsForInvoice(n.id).first().sumOf { it.quantity * it.price } }
            value = invoiceSum - payments.filter { it.customerName.trim().equals(name, true) }.sumOf { it.amount }
        }
    }
    val balanceColor = if (customerBalance > 0.009) Color(0xFFD32F2F) else if (customerBalance < -0.009) Color(0xFF64B5F6) else MaterialTheme.colorScheme.onSurfaceVariant

    fun addOrUpdate() {
        val name = itemName.trim()
        val qty = qtyText.toDoubleOrNull() ?: 0.0
        val total = totalText.toDoubleOrNull() ?: 0.0
        if (name.isBlank() || qty <= 0.0 || total < 0.0) return
        val p = total / qty
        val id = editingId
        if (id == null) viewModel.addItem(name, qty, p, "")
        else viewModel.updateItem(NoteItem(id = id, noteId = note?.id ?: return, name = name, quantity = qty, price = p))
        editingId = null; itemName = ""; totalText = ""; qtyText = "1"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("فاتورة ${invoiceNumber.ifBlank { "جديدة" }}", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenCustomers) { Icon(Icons.Default.People, "حسابات العملاء") }
                    IconButton(onClick = onOpenHistory) { Icon(Icons.Default.History, "السجل") }
                    IconButton(onClick = { viewModel.shareCurrentInvoice() }) { Icon(Icons.Default.Share, "صورة + نص") }
                }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { viewModel.createNewNote() }) { Icon(Icons.Default.Add, "فاتورة جديدة") } }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = invoiceNumber, onValueChange = { invoiceNumber = it; viewModel.updateInvoiceNumber(it) }, label = { Text("رقم الفاتورة") }, modifier = Modifier.width(125.dp), singleLine = true)
                            Box(Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = customer,
                                    onValueChange = { customer = it; showCustomerSuggestions = true; viewModel.updateCustomerName(it) },
                                    label = { Text("اسم العميل") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                DropdownMenu(expanded = showCustomerSuggestions && matchingCustomers.isNotEmpty(), onDismissRequest = { showCustomerSuggestions = false }, modifier = Modifier.fillMaxWidth()) {
                                    matchingCustomers.forEach { name ->
                                        DropdownMenuItem(text = { Text(name, fontWeight = FontWeight.Bold) }, onClick = { customer = name; showCustomerSuggestions = false; viewModel.updateCustomerName(name) })
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (customerBalance > 0.009) "عليه: ${moneyV2(customerBalance)}" else if (customerBalance < -0.009) "له: ${moneyV2(kotlin.math.abs(customerBalance))}" else "الرصيد: 0", color = balanceColor, fontWeight = FontWeight.Bold)
                            Text("إجمالي الفاتورة: ${moneyV2(grandTotal)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (editingId == null) "إضافة صنف" else "تعديل الصنف", fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = totalText,
                                onValueChange = { totalText = it },
                                label = { Text("الإجمالي") },
                                modifier = Modifier.weight(1.05f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = qtyText,
                                onValueChange = { qtyText = it },
                                label = { Text("الكمية") },
                                modifier = Modifier.weight(.72f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = itemName,
                                onValueChange = { itemName = it },
                                label = { Text("اسم الصنف") },
                                modifier = Modifier.weight(1.55f),
                                singleLine = true
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("سعر الوحدة: ${moneyV2(price)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            Button(onClick = ::addOrUpdate, enabled = itemName.isNotBlank() && (qtyText.toDoubleOrNull() ?: 0.0) > 0) {
                                Icon(if (editingId == null) Icons.Default.Add else Icons.Default.Save, null)
                                Spacer(Modifier.width(4.dp)); Text(if (editingId == null) "إضافة" else "حفظ التعديل")
                            }
                        }
                    }
                }
            }
            item { Text("أصناف الفاتورة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(items, key = { it.id }) { item ->
                Card(onClick = { editingId = item.id; itemName = item.name; qtyText = moneyV2(item.quantity); totalText = moneyV2(item.quantity * item.price) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            Text("الكمية: ${moneyV2(item.quantity)}  •  الوحدة: ${moneyV2(item.price)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(moneyV2(item.quantity * item.price), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showDelete = item }) { Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("الإجمالي النهائي", style = MaterialTheme.typography.titleMedium)
                        Text(moneyV2(grandTotal), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { printInvoice(context, note, items, grandTotal) }) { Icon(Icons.Default.Print, null); Spacer(Modifier.width(4.dp)); Text("طباعة") }
                            TextButton(onClick = { onOpenSettings() }) { Text("الإعدادات") }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(90.dp)) }
        }
    }

    showDelete?.let { item ->
        AlertDialog(onDismissRequest = { showDelete = null }, title = { Text("حذف الصنف؟") }, text = { Text("سيتم حذف ${item.name} من الفاتورة الحالية.") }, confirmButton = { TextButton(onClick = { viewModel.deleteItem(item); showDelete = null }) { Text("حذف") } }, dismissButton = { TextButton(onClick = { showDelete = null }) { Text("إلغاء") } })
    }
}

private fun printInvoice(context: Context, note: com.example.data.Note?, items: List<NoteItem>, total: Double) {
    if (note == null) return
    val web = WebView(context)
    val customer = note.customerName.ifBlank { "عميل عام" }
    val number = note.invoiceNumber.ifBlank { note.id.toString() }
    val rows = items.joinToString("") { "<tr><td>${it.name}</td><td>${moneyV2(it.quantity)}</td><td>${moneyV2(it.quantity * it.price)}</td></tr>" }
    val html = "<html dir='rtl'><head><meta charset='UTF-8'><style>@page{size:58mm auto;margin:0}body{width:58mm;margin:0;padding:3mm;font-family:sans-serif;color:#000}table{width:100%;border-collapse:collapse}td,th{font-size:12px;padding:3px;border-bottom:1px dashed #000}.total{font-size:18px;font-weight:bold;margin-top:8px}</style></head><body><h3>فاتورة مبيعات</h3><div>رقم: $number</div><div>العميل: $customer</div><table><tr><th>الصنف</th><th>الكمية</th><th>الإجمالي</th></tr>$rows</table><div class='total'>الإجمالي: ${moneyV2(total)}</div></body></html>"
    web.settings.defaultTextEncodingName = "UTF-8"
    web.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val job = "فاتورة_$number"
            manager.print(job, view.createPrintDocumentAdapter(job), PrintAttributes.Builder().setMinMargins(PrintAttributes.Margins.NO_MARGINS).build())
        }
    }
    web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}
