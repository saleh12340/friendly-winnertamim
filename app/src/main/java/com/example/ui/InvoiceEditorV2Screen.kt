package com.example.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.NoteItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private fun moneyV2(v: Double): String =
    if (v.isFinite() && v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(Locale.US, v)

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
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val notes by viewModel.allNotes.collectAsStateWithLifecycle()
    val payments by viewModel.allPayments.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val fieldShape = RoundedCornerShape(18.dp)
    val cardShape = RoundedCornerShape(20.dp)

    var customer by remember { mutableStateOf("") }
    var invoiceNumber by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("1") }
    var itemName by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var showCustomerSuggestions by remember { mutableStateOf(false) }
    var showItemSuggestions by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf<NoteItem?>(null) }

    LaunchedEffect(note?.id) {
        customer = note?.customerName.orEmpty()
        invoiceNumber = note?.invoiceNumber.orEmpty()
        editingId = null
        totalText = ""
        qtyText = "1"
        itemName = ""
        showCustomerSuggestions = false
        showItemSuggestions = false
    }

    val price = (totalText.toDoubleOrNull() ?: 0.0).let { t ->
        val q = qtyText.toDoubleOrNull() ?: 0.0
        if (q > 0) t / q else 0.0
    }
    val grandTotal = items.sumOf { it.quantity * it.price }

    val cleanCustomer = customer.trim()
    val matchingCustomers = remember(cleanCustomer, customers) {
        if (cleanCustomer.isBlank()) emptyList()
        else customers.filter { it.contains(cleanCustomer, ignoreCase = true) }.take(6)
    }

    val cleanItem = itemName.trim()
    val matchingItems = remember(cleanItem, suggestions) {
        if (cleanItem.isBlank()) emptyList()
        else suggestions.map { it.word }.filter { it.contains(cleanItem, ignoreCase = true) }.take(6)
    }

    val customerBalance by produceState(0.0, cleanCustomer, notes, payments) {
        if (cleanCustomer.isBlank()) {
            value = 0.0
        } else {
            value = withContext(Dispatchers.IO) {
                val invoiceSum = notes
                    .filter { it.customerName.trim().equals(cleanCustomer, ignoreCase = true) }
                    .sumOf { n -> viewModel.itemsForInvoice(n.id).first().sumOf { it.quantity * it.price } }
                val paidSum = payments
                    .filter { it.customerName.trim().equals(cleanCustomer, ignoreCase = true) }
                    .sumOf { it.amount }
                invoiceSum - paidSum
            }
        }
    }

    val balanceColor = if (customerBalance > 0.009) Color(0xFFD32F2F)
    else if (customerBalance < -0.009) Color(0xFF1976D2)
    else MaterialTheme.colorScheme.onSurfaceVariant

    fun addOrUpdate() {
        val name = itemName.trim()
        val qty = qtyText.toDoubleOrNull() ?: 0.0
        val total = totalText.toDoubleOrNull() ?: 0.0
        if (name.isBlank() || qty <= 0.0 || total < 0.0) return
        val p = total / qty
        val id = editingId
        if (id == null) {
            viewModel.addItem(name, qty, p, "")
        } else {
            viewModel.updateItem(
                NoteItem(id = id, noteId = note?.id ?: return, name = name, quantity = qty, price = p)
            )
        }
        editingId = null
        itemName = ""
        totalText = ""
        qtyText = "1"
        showItemSuggestions = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (invoiceNumber.isNotBlank()) "فاتورة $invoiceNumber" else "فاتورة جديدة",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onOpenCustomers) {
                        Icon(Icons.Default.People, contentDescription = "حسابات العملاء")
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, contentDescription = "سجل الفواتير")
                    }
                    IconButton(onClick = { viewModel.shareCurrentInvoice() }) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "الإعدادات")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.createNewNote() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("فاتورة جديدة", fontWeight = FontWeight.Bold) },
                shape = RoundedCornerShape(18.dp)
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // بطاقة بيانات الفاتورة
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = invoiceNumber,
                                onValueChange = {
                                    invoiceNumber = it
                                    viewModel.updateInvoiceNumber(it)
                                },
                                label = { Text("رقم الفاتورة") },
                                leadingIcon = {
                                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                shape = fieldShape,
                                modifier = Modifier.width(135.dp),
                                singleLine = true
                            )

                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = customer,
                                    onValueChange = {
                                        customer = it
                                        showCustomerSuggestions = true
                                        viewModel.updateCustomerName(it)
                                    },
                                    label = { Text("اسم العميل") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                                    },
                                    trailingIcon = {
                                        if (customer.isNotEmpty()) {
                                            IconButton(onClick = {
                                                customer = ""
                                                showCustomerSuggestions = false
                                                viewModel.updateCustomerName("")
                                            }) {
                                                Icon(Icons.Default.Clear, contentDescription = "مسح", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    shape = fieldShape,
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                // قائمة الاقتراحات مع خاصية focusable = false حتى لا يختفي الكيبورد أبداً
                                val shouldShowDropdown = showCustomerSuggestions &&
                                        matchingCustomers.isNotEmpty() &&
                                        !matchingCustomers.any { it.equals(customer.trim(), ignoreCase = true) }

                                DropdownMenu(
                                    expanded = shouldShowDropdown,
                                    onDismissRequest = { showCustomerSuggestions = false },
                                    properties = PopupProperties(
                                        focusable = false,
                                        dismissOnBackPress = true,
                                        dismissOnClickOutside = true
                                    ),
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    matchingCustomers.forEach { name ->
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(Icons.Default.Person, null, modifier = Modifier.size(18.dp))
                                            },
                                            text = { Text(name, fontWeight = FontWeight.SemiBold) },
                                            onClick = {
                                                customer = name
                                                showCustomerSuggestions = false
                                                viewModel.updateCustomerName(name)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // شريط رصيد العميل وإجمالي الفاتورة
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = balanceColor.copy(alpha = 0.12f),
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (cleanCustomer.isBlank()) "العميل: غير محدد"
                                    else if (customerBalance > 0.009) "عليه: ${moneyV2(customerBalance)}"
                                    else if (customerBalance < -0.009) "له: ${moneyV2(kotlin.math.abs(customerBalance))}"
                                    else "الحساب: متزن (0)",
                                    color = balanceColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }

                            Text(
                                text = "الإجمالي: ${moneyV2(grandTotal)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // بطاقة إضافة وتعديل الأصناف
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    if (editingId == null) Icons.Default.AddShoppingCart else Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    if (editingId == null) "إضافة صنف للفاتورة" else "تعديل الصنف المحدد",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (editingId != null) {
                                TextButton(
                                    onClick = {
                                        editingId = null
                                        itemName = ""
                                        totalText = ""
                                        qtyText = "1"
                                    }
                                ) {
                                    Text("إلغاء التعديل")
                                }
                            }
                        }

                        // اسم الصنف مع اقتراحات لا تغلق الكيبورد
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = itemName,
                                onValueChange = {
                                    itemName = it
                                    showItemSuggestions = true
                                },
                                label = { Text("اسم الصنف") },
                                leadingIcon = {
                                    Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                shape = fieldShape,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            val shouldShowItemDropdown = showItemSuggestions &&
                                    matchingItems.isNotEmpty() &&
                                    !matchingItems.any { it.equals(itemName.trim(), ignoreCase = true) }

                            DropdownMenu(
                                expanded = shouldShowItemDropdown,
                                onDismissRequest = { showItemSuggestions = false },
                                properties = PopupProperties(
                                    focusable = false,
                                    dismissOnBackPress = true,
                                    dismissOnClickOutside = true
                                ),
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                matchingItems.forEach { name ->
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Default.ShoppingBag, null, modifier = Modifier.size(16.dp)) },
                                        text = { Text(name) },
                                        onClick = {
                                            itemName = name
                                            showItemSuggestions = false
                                        }
                                    )
                                }
                            }
                        }

                        // مربعات الإجمالي والكمية منظمة بدقة مع حواف دائرية
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = totalText,
                                onValueChange = { totalText = it },
                                label = { Text("الإجمالي") },
                                leadingIcon = {
                                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                shape = fieldShape,
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = qtyText,
                                onValueChange = { qtyText = it },
                                label = { Text("الكمية") },
                                leadingIcon = {
                                    Icon(Icons.Default.Numbers, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                shape = fieldShape,
                                modifier = Modifier.weight(0.9f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                        }

                        // شريط سعر الوحدة وزر الإضافة / الحفظ
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "سعر الوحدة: ${moneyV2(price)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }

                            Button(
                                onClick = ::addOrUpdate,
                                enabled = itemName.isNotBlank() && (qtyText.toDoubleOrNull() ?: 0.0) > 0,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    if (editingId == null) Icons.Default.Add else Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (editingId == null) "إضافة الصنف" else "حفظ التعديل",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // قائمة الأصناف المضافة
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "أصناف الفاتورة (${items.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (items.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "لا توجد أصناف مضافة بعد. أضف الأصناف أعلاه.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            items(items, key = { it.id }) { item ->
                Card(
                    onClick = {
                        editingId = item.id
                        itemName = item.name
                        qtyText = moneyV2(item.quantity)
                        totalText = moneyV2(item.quantity * item.price)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "الكمية: ${moneyV2(item.quantity)}  •  الوحدة: ${moneyV2(item.price)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            moneyV2(item.quantity * item.price),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(
                            onClick = { showDelete = item },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "حذف",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // بطاقة الإجمالي النهائي والطباعة
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("الإجمالي النهائي", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    moneyV2(grandTotal),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Button(
                                onClick = { printInvoice(context, note, items, grandTotal) },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("طباعة الفاتورة", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    showDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { showDelete = null },
            title = { Text("حذف الصنف؟") },
            text = { Text("سيتم حذف \"${item.name}\" من الفاتورة الحالية.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteItem(item)
                    showDelete = null
                }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

private fun printInvoice(
    context: Context,
    note: com.example.data.Note?,
    items: List<NoteItem>,
    total: Double
) {
    if (note == null) return
    val web = WebView(context)
    val customer = note.customerName.ifBlank { "عميل عام" }
    val number = note.invoiceNumber.ifBlank { note.id.toString() }
    val rows = items.joinToString("") {
        "<tr><td>${it.name}</td><td>${moneyV2(it.quantity)}</td><td>${moneyV2(it.price)}</td><td>${moneyV2(it.quantity * it.price)}</td></tr>"
    }
    val html = """
        <html dir='rtl'>
        <head>
        <meta charset='UTF-8'>
        <style>
          @page { size: 58mm auto; margin: 0; }
          body { width: 58mm; margin: 0; padding: 3mm; font-family: sans-serif; color: #000; direction: rtl; }
          .center { text-align: center; }
          table { width: 100%; border-collapse: collapse; margin-top: 4px; }
          td, th { font-size: 11px; padding: 3px 1px; border-bottom: 1px dashed #000; text-align: center; }
          th { font-weight: bold; }
          .name { text-align: right; }
          .total { font-size: 16px; font-weight: bold; margin-top: 8px; text-align: left; }
          .divider { border-top: 1px solid #000; margin: 4px 0; }
        </style>
        </head>
        <body>
          <h3 class='center' style='margin:0;'>فاتورة مبيعات</h3>
          <div class='divider'></div>
          <div style='font-size:12px;'>رقم الفاتورة: $number</div>
          <div style='font-size:12px;'>العميل: $customer</div>
          <div class='divider'></div>
          <table>
            <tr>
              <th class='name'>الصنف</th>
              <th>الكمية</th>
              <th>السعر</th>
              <th>الإجمالي</th>
            </tr>
            $rows
          </table>
          <div class='divider'></div>
          <div class='total'>الإجمالي: ${moneyV2(total)}</div>
          <div class='center' style='font-size:10px; margin-top:6px;'>شكراً لتعاملكم معنا</div>
        </body>
        </html>
    """.trimIndent()

    web.settings.defaultTextEncodingName = "UTF-8"
    web.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val job = "فاتورة_$number"
            manager.print(
                job,
                view.createPrintDocumentAdapter(job),
                PrintAttributes.Builder().setMinMargins(PrintAttributes.Margins.NO_MARGINS).build()
            )
        }
    }
    web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}
