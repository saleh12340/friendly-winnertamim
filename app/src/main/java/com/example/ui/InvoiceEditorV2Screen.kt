package com.example.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val itemSuggestionsList by viewModel.itemSuggestions.collectAsStateWithLifecycle()
    val notes by viewModel.allNotes.collectAsStateWithLifecycle()
    val payments by viewModel.allPayments.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // State with TextFieldValue to support auto-selecting existing text on focus
    var customerValue by remember { mutableStateOf(TextFieldValue("")) }
    var invoiceNumber by remember { mutableStateOf("") }
    var totalValue by remember { mutableStateOf(TextFieldValue("0")) }
    var qtyValue by remember { mutableStateOf(TextFieldValue("1")) }
    var itemNameValue by remember { mutableStateOf(TextFieldValue("")) }

    var editingId by remember { mutableStateOf<Long?>(null) }
    var showCustomerSuggestions by remember { mutableStateOf(false) }
    var showItemSuggestions by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<NoteItem?>(null) }
    var showInvoiceNumberDialog by remember { mutableStateOf(false) }

    LaunchedEffect(note?.id) {
        val cName = note?.customerName.orEmpty()
        customerValue = TextFieldValue(cName, selection = TextRange(cName.length))
        invoiceNumber = note?.invoiceNumber?.ifBlank { note?.id?.toString().orEmpty() } ?: note?.id?.toString().orEmpty()
        editingId = null
        totalValue = TextFieldValue("0", selection = TextRange(0, 1))
        qtyValue = TextFieldValue("1", selection = TextRange(0, 1))
        itemNameValue = TextFieldValue("")
        showCustomerSuggestions = false
        showItemSuggestions = false
    }

    val grandTotal = items.sumOf { it.quantity * it.price }
    val cleanCustomer = customerValue.text.trim()

    // Matching customer suggestions: if field is blank, show recent/all customers (up to 10), else filter
    val matchingCustomers = remember(cleanCustomer, customers) {
        if (cleanCustomer.isBlank()) {
            customers.take(10)
        } else {
            val filtered = customers.filter { it.contains(cleanCustomer, ignoreCase = true) }
            if (filtered.isEmpty()) emptyList() else filtered.take(10)
        }
    }

    // Matching item suggestions: if blank, show popular/starter items (up to 15), else filter
    val cleanItem = itemNameValue.text.trim()
    val matchingItems = remember(cleanItem, itemSuggestionsList) {
        if (cleanItem.isBlank()) {
            itemSuggestionsList.take(15)
        } else {
            val filtered = itemSuggestionsList.filter { it.contains(cleanItem, ignoreCase = true) }
            if (filtered.isEmpty()) emptyList() else filtered.take(15)
        }
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

    fun addOrUpdate() {
        val name = itemNameValue.text.trim()
        val total = totalValue.text.toDoubleOrNull() ?: 0.0
        val qty = qtyValue.text.toDoubleOrNull() ?: 0.0
        if (name.isBlank() || qty <= 0.0 || total < 0.0) return

        // Compute price per unit: Price = Total / Quantity
        val unitPrice = total / qty
        val id = editingId
        if (id == null) {
            viewModel.addItem(name, qty, unitPrice, "")
        } else {
            viewModel.updateItem(
                NoteItem(id = id, noteId = note?.id ?: return, name = name, quantity = qty, price = unitPrice)
            )
        }
        editingId = null
        itemNameValue = TextFieldValue("")
        totalValue = TextFieldValue("0", selection = TextRange(0, 1))
        qtyValue = TextFieldValue("1", selection = TextRange(0, 1))
        showItemSuggestions = false
        focusManager.clearFocus()
    }

    // Vibrant color palette
    val vibrantGreen = Color(0xFF43A047)
    val vibrantGreenDark = Color(0xFF2E7D32)
    val vibrantRed = Color(0xFFE53935)
    val vibrantBlue = Color(0xFF1976D2)
    val tableHeaderBg = Color(0xFFECEFF1)
    val cardBorderColor = Color(0xFFCFD8DC)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("فاتورة المبيعات", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        IconButton(onClick = onOpenCustomers) {
                            Icon(Icons.Default.People, contentDescription = "حسابات العملاء", tint = vibrantBlue)
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
            bottomBar = {
                // Bottom bar displaying Grand Total, New Invoice and Print Button
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("المجموع الكلي", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                Text(
                                    moneyV2(grandTotal),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.createNewNote() },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, vibrantBlue),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = vibrantBlue)
                                    Spacer(Modifier.width(4.dp))
                                    Text("فاتورة جديدة", color = vibrantBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                Button(
                                    onClick = { printInvoice(context, note, items, grandTotal) },
                                    colors = ButtonDefaults.buttonColors(containerColor = vibrantBlue),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("طباعة", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }

                        if (cleanCustomer.isNotBlank()) {
                            val balanceText = if (customerBalance > 0.009) "على العميل: ${moneyV2(customerBalance)}"
                            else if (customerBalance < -0.009) "له دائن: ${moneyV2(kotlin.math.abs(customerBalance))}"
                            else "الحساب متزن (0)"

                            val balanceColor = if (customerBalance > 0.009) vibrantRed
                            else if (customerBalance < -0.009) vibrantGreenDark
                            else Color.Gray

                            Text(
                                text = "حساب $cleanCustomer: $balanceText",
                                color = balanceColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Top Input Box exactly matching the user's uploaded image
                item {
                    Spacer(Modifier.height(4.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, cardBorderColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Row 1: Invoice number badge on Right, Customer Name on Left
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Rightmost badge: رقم الفاتورة
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFFF1F3F5),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier
                                        .height(54.dp)
                                        .clickable { showInvoiceNumberDialog = true }
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 14.dp)
                                    ) {
                                        Text(
                                            text = "رقم الفاتورة: $invoiceNumber",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color(0xFF1E293B)
                                        )
                                    }
                                }

                                // Left: Customer Name input box
                                OutlinedTextField(
                                    value = customerValue,
                                    onValueChange = { newVal ->
                                        customerValue = newVal
                                        showCustomerSuggestions = true
                                        viewModel.updateCustomerName(newVal.text)
                                    },
                                    placeholder = { Text("اسم العميل", color = Color.Gray) },
                                    trailingIcon = {
                                        if (customerValue.text.isNotEmpty()) {
                                            IconButton(onClick = {
                                                customerValue = TextFieldValue("")
                                                viewModel.updateCustomerName("")
                                                showCustomerSuggestions = true
                                            }) {
                                                Icon(Icons.Default.Close, contentDescription = "مسح", modifier = Modifier.size(18.dp), tint = Color.Gray)
                                            }
                                        } else {
                                            IconButton(onClick = { showCustomerSuggestions = !showCustomerSuggestions }) {
                                                Icon(
                                                    if (showCustomerSuggestions) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                                    contentDescription = "عرض العملاء",
                                                    tint = vibrantBlue
                                                )
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .onFocusChanged { state ->
                                            if (state.isFocused) {
                                                showCustomerSuggestions = true
                                                if (customerValue.text.isNotEmpty()) {
                                                    customerValue = customerValue.copy(
                                                        selection = TextRange(0, customerValue.text.length)
                                                    )
                                                }
                                            }
                                        },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(
                                        textAlign = TextAlign.Start,
                                        fontSize = 15.sp
                                    )
                                )
                            }

                            // Customer Suggestions Bar (Chips)
                            AnimatedVisibility(
                                visible = showCustomerSuggestions && matchingCustomers.isNotEmpty(),
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF8FAFC), shape = RoundedCornerShape(12.dp))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (cleanCustomer.isBlank()) "العملاء المسجلين (اضغط للاختيار):" else "اقتراحات العملاء:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = vibrantBlue,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "إغلاق ✕",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            modifier = Modifier
                                                .clickable { showCustomerSuggestions = false }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        contentPadding = PaddingValues(vertical = 2.dp)
                                    ) {
                                        items(matchingCustomers) { cName ->
                                            SuggestionChip(
                                                onClick = {
                                                    customerValue = TextFieldValue(cName, selection = TextRange(cName.length))
                                                    showCustomerSuggestions = false
                                                    viewModel.updateCustomerName(cName)
                                                },
                                                label = { Text(cName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                                icon = {
                                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = vibrantBlue)
                                                },
                                                colors = SuggestionChipDefaults.suggestionChipColors(
                                                    containerColor = vibrantBlue.copy(alpha = 0.08f),
                                                    labelColor = Color(0xFF0D47A1)
                                                ),
                                                border = BorderStroke(1.dp, vibrantBlue.copy(alpha = 0.3f)),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Row 2: 3 Input boxes: الإجمالي (Right), الكمية (Middle), اسم الصنف (Left)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Rightmost: الإجمالي (Total)
                                OutlinedTextField(
                                    value = totalValue,
                                    onValueChange = { newVal ->
                                        // If existing was "0" and user typed a digit, overwrite the "0"
                                        if (totalValue.text == "0" && newVal.text.length == 2 && newVal.text.startsWith("0")) {
                                            val next = newVal.text.substring(1)
                                            totalValue = TextFieldValue(next, selection = TextRange(next.length))
                                        } else if (totalValue.text == "0" && newVal.text.length == 2 && newVal.text.endsWith("0")) {
                                            val next = newVal.text.substring(0, 1)
                                            totalValue = TextFieldValue(next, selection = TextRange(next.length))
                                        } else {
                                            totalValue = newVal
                                        }
                                    },
                                    label = { Text("الإجمالي", fontSize = 12.sp) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .onFocusChanged { state ->
                                            if (state.isFocused) {
                                                showItemSuggestions = false
                                                showCustomerSuggestions = false
                                                if (totalValue.text.isNotEmpty()) {
                                                    totalValue = totalValue.copy(
                                                        selection = TextRange(0, totalValue.text.length)
                                                    )
                                                }
                                            }
                                        },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Next
                                    ),
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                )

                                // 2. Middle: الكمية (Quantity)
                                OutlinedTextField(
                                    value = qtyValue,
                                    onValueChange = { newVal ->
                                        // If existing was "1" and user typed a digit, overwrite "1"
                                        if (qtyValue.text == "1" && newVal.text.length == 2 && newVal.text.startsWith("1")) {
                                            val next = newVal.text.substring(1)
                                            qtyValue = TextFieldValue(next, selection = TextRange(next.length))
                                        } else if (qtyValue.text == "1" && newVal.text.length == 2 && newVal.text.endsWith("1")) {
                                            val next = newVal.text.substring(0, 1)
                                            qtyValue = TextFieldValue(next, selection = TextRange(next.length))
                                        } else {
                                            qtyValue = newVal
                                        }
                                    },
                                    label = { Text("الكمية", fontSize = 12.sp) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .onFocusChanged { state ->
                                            if (state.isFocused) {
                                                showItemSuggestions = false
                                                showCustomerSuggestions = false
                                                if (qtyValue.text.isNotEmpty()) {
                                                    qtyValue = qtyValue.copy(
                                                        selection = TextRange(0, qtyValue.text.length)
                                                    )
                                                }
                                            }
                                        },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Next
                                    ),
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                )

                                // 3. Leftmost: اسم الصنف (Item Name)
                                OutlinedTextField(
                                    value = itemNameValue,
                                    onValueChange = { newVal ->
                                        itemNameValue = newVal
                                        showItemSuggestions = true
                                    },
                                    placeholder = { Text("اسم الصنف", fontSize = 13.sp) },
                                    trailingIcon = {
                                        if (itemNameValue.text.isNotEmpty()) {
                                            IconButton(onClick = {
                                                itemNameValue = TextFieldValue("")
                                                showItemSuggestions = true
                                            }) {
                                                Icon(Icons.Default.Close, contentDescription = "مسح", modifier = Modifier.size(16.dp), tint = Color.Gray)
                                            }
                                        } else {
                                            IconButton(onClick = { showItemSuggestions = !showItemSuggestions }) {
                                                Icon(
                                                    if (showItemSuggestions) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                                    contentDescription = "عرض الأصناف",
                                                    tint = vibrantGreenDark
                                                )
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1.8f)
                                        .onFocusChanged { state ->
                                            if (state.isFocused) {
                                                showItemSuggestions = true
                                                showCustomerSuggestions = false
                                                if (itemNameValue.text.isNotEmpty()) {
                                                    itemNameValue = itemNameValue.copy(
                                                        selection = TextRange(0, itemNameValue.text.length)
                                                    )
                                                }
                                            }
                                        },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { addOrUpdate() }
                                    ),
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(
                                        textAlign = TextAlign.Start,
                                        fontSize = 14.sp
                                    )
                                )
                            }

                            // Item Suggestions Bar (Chips)
                            AnimatedVisibility(
                                visible = showItemSuggestions && matchingItems.isNotEmpty(),
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF1F8E9), shape = RoundedCornerShape(12.dp))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (cleanItem.isBlank()) "أصناف سريعة ومقترحة (اضغط للاختيار):" else "اقتراحات الأصناف:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = vibrantGreenDark,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "إغلاق ✕",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            modifier = Modifier
                                                .clickable { showItemSuggestions = false }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        contentPadding = PaddingValues(vertical = 2.dp)
                                    ) {
                                        items(matchingItems) { itemWord ->
                                            SuggestionChip(
                                                onClick = {
                                                    itemNameValue = TextFieldValue(itemWord, selection = TextRange(itemWord.length))
                                                    showItemSuggestions = false
                                                },
                                                label = { Text(itemWord, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                                icon = {
                                                    Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(14.dp), tint = vibrantGreenDark)
                                                },
                                                colors = SuggestionChipDefaults.suggestionChipColors(
                                                    containerColor = Color.White,
                                                    labelColor = Color(0xFF1B5E20)
                                                ),
                                                border = BorderStroke(1.dp, vibrantGreen.copy(alpha = 0.4f)),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Row 3: Big Vibrant Green Button "+ إضافة الصنف"
                            Button(
                                onClick = ::addOrUpdate,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (editingId == null) vibrantGreen else vibrantBlue
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                            ) {
                                Icon(
                                    if (editingId == null) Icons.Default.Add else Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (editingId == null) "إضافة الصنف" else "حفظ تعديل الصنف",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Table Header matching the uploaded image exactly
                item {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                        color = tableHeaderBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Column 1: اسم الصنف (Right)
                            Text(
                                text = "اسم الصنف",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.weight(2f)
                            )

                            // Column 2: الكمية (Middle-Right)
                            Text(
                                text = "الكمية",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(55.dp)
                            )

                            // Column 3: السعر (Middle)
                            Text(
                                text = "السعر",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(75.dp)
                            )

                            // Column 4: الإجمالي (Middle-Left)
                            Text(
                                text = "الإجمالي",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(85.dp)
                            )

                            // Column 5: Spacer for delete button (Far-Left)
                            Box(modifier = Modifier.width(42.dp))
                        }
                    }
                    HorizontalDivider(color = Color(0xFFCFD8DC), thickness = 1.dp)
                }

                // Table Rows
                if (items.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لم تتم إضافة أي أصناف بعد\nأدخل بيانات الصنف أعلاه ثم اضغط على زر إضافة الصنف",
                                textAlign = TextAlign.Center,
                                color = Color.Gray,
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                } else {
                    items(items, key = { it.id }) { item ->
                        val itemTotal = item.quantity * item.price
                        val isEditingThis = editingId == item.id

                        Surface(
                            color = if (isEditingThis) vibrantGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editingId = item.id
                                    itemNameValue = TextFieldValue(item.name, selection = TextRange(0, item.name.length))
                                    qtyValue = TextFieldValue(moneyV2(item.quantity), selection = TextRange(0, moneyV2(item.quantity).length))
                                    totalValue = TextFieldValue(moneyV2(itemTotal), selection = TextRange(0, moneyV2(itemTotal).length))
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Column 1: اسم الصنف
                                Text(
                                    text = item.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1E293B),
                                    textAlign = TextAlign.Start,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(2f)
                                )

                                // Column 2: الكمية
                                Text(
                                    text = moneyV2(item.quantity),
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFF334155),
                                    modifier = Modifier.width(55.dp)
                                )

                                // Column 3: السعر
                                Text(
                                    text = moneyV2(item.price),
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFF334155),
                                    modifier = Modifier.width(75.dp)
                                )

                                // Column 4: الإجمالي
                                Text(
                                    text = moneyV2(itemTotal),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = Color.Black,
                                    modifier = Modifier.width(85.dp)
                                )

                                // Column 5: Red Trash can icon (حذف)
                                IconButton(
                                    onClick = { showDeleteDialog = item },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "حذف الصنف",
                                        tint = vibrantRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)
                    }
                }

                item {
                    Spacer(Modifier.height(30.dp))
                }
            }
        }

        // Delete Confirmation Dialog
        showDeleteDialog?.let { item ->
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text("حذف الصنف؟", fontWeight = FontWeight.Bold) },
                text = { Text("هل تريد بالتأكيد حذف \"${item.name}\" من الفاتورة؟") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteItem(item)
                            if (editingId == item.id) {
                                editingId = null
                                itemNameValue = TextFieldValue("")
                                totalValue = TextFieldValue("0", selection = TextRange(0, 1))
                                qtyValue = TextFieldValue("1", selection = TextRange(0, 1))
                            }
                            showDeleteDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = vibrantRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("حذف", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) {
                        Text("إلغاء")
                    }
                }
            )
        }

        // Change Invoice Number Dialog
        if (showInvoiceNumberDialog) {
            var tempNumber by remember { mutableStateOf(invoiceNumber) }
            AlertDialog(
                onDismissRequest = { showInvoiceNumberDialog = false },
                title = { Text("تعديل رقم الفاتورة", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = tempNumber,
                        onValueChange = { tempNumber = it },
                        label = { Text("رقم الفاتورة") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            invoiceNumber = tempNumber
                            viewModel.updateInvoiceNumber(tempNumber)
                            showInvoiceNumberDialog = false
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("حفظ")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showInvoiceNumberDialog = false }) {
                        Text("إلغاء")
                    }
                }
            )
        }
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
        "<tr><td style='text-align:right;'>${it.name}</td><td>${moneyV2(it.quantity)}</td><td>${moneyV2(it.price)}</td><td style='font-weight:bold;'>${moneyV2(it.quantity * it.price)}</td></tr>"
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
          th { font-weight: bold; background: #f0f0f0; }
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
              <th style='text-align:right;'>الصنف</th>
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
