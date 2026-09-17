package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.NoteItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(viewModel: OmniViewModel, onOpenHistory: () -> Unit) {
    val currentNote by viewModel.currentNote.collectAsStateWithLifecycle()
    val currentItems by viewModel.currentItems.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()

    var itemName by remember { mutableStateOf(TextFieldValue("")) }
    var quantity by remember { mutableStateOf(TextFieldValue("1")) }
    var price by remember { mutableStateOf(TextFieldValue("0")) }
    var totalInput by remember { mutableStateOf(TextFieldValue("0")) }
    var customerName by remember { mutableStateOf(TextFieldValue("")) }
    var invoiceNumber by remember { mutableStateOf(TextFieldValue("")) }
    var showSuggestions by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun formatNumber(value: Double): String =
        if (value.isFinite() && value % 1.0 == 0.0) value.toLong().toString()
        else "%.2f".format(java.util.Locale.US, value)

    fun selectAllOnFocus(value: TextFieldValue, setValue: (TextFieldValue) -> Unit) {
        scope.launch {
            delay(80)
            if (value.text.isNotEmpty()) setValue(value.copy(selection = TextRange(0, value.text.length)))
        }
    }

    fun updatePriceFromTotal(total: String, qty: String) {
        val t = total.toDoubleOrNull() ?: 0.0
        val q = qty.toDoubleOrNull() ?: 0.0
        price = TextFieldValue(if (q > 0.0) formatNumber(t / q) else "0")
    }

    fun normalizedQuery(value: String): String = value.trim().replace(Regex("\\s+"), " ")

    LaunchedEffect(currentNote?.id) {
        customerName = TextFieldValue(currentNote?.customerName ?: "")
        invoiceNumber = TextFieldValue(currentNote?.invoiceNumber ?: "")
    }

    val context = LocalContext.current
    val appNameLabel = stringResource(R.string.app_name)
    val customerNameLabel = stringResource(R.string.customer_name)
    val itemNameLabel = stringResource(R.string.item_name)
    val quantityLabel = stringResource(R.string.quantity)
    val priceLabel = stringResource(R.string.price)
    val totalLabel = stringResource(R.string.total)
    val invoiceNumberLabel = stringResource(R.string.invoice_number)

    fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    fun printNote() {
        val grandTotal = currentItems.sumOf { it.quantity * it.price }
        val dateFormatted = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(currentNote?.timestamp ?: System.currentTimeMillis()))
        val invNum = currentNote?.invoiceNumber.orEmpty().ifBlank { currentNote?.id?.toString() ?: "1" }
        val custName = currentNote?.customerName.orEmpty().ifBlank { "عميل عام" }

        val html = buildString {
            append("<!DOCTYPE html><html dir='rtl' lang='ar'><head><meta charset='UTF-8'>")
            append("<meta name='viewport' content='width=384, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>")
            append("<style>")
            append("@page { size: 58mm auto; margin: 0 !important; padding: 0 !important; }")
            append("@media print {")
            append("  html, body { width: 58mm !important; max-width: 58mm !important; margin: 0 !important; padding: 0 !important; }")
            append("}")
            append("* { box-sizing: border-box; margin: 0; padding: 0; -webkit-print-color-adjust: exact !important; print-color-adjust: exact !important; }")
            append("html, body { width: 100%; max-width: 58mm; background: #fff !important; color: #000 !important; font-family: sans-serif; direction: rtl; text-align: right; }")
            append(".receipt { width: 100%; max-width: 58mm; padding: 2px 3px 0 3px; margin: 0; }")
            append(".center { text-align: center; }")
            append(".store-title { font-size: 16px; font-weight: 900; margin-bottom: 2px; }")
            append(".meta-box { font-size: 11px; font-weight: bold; line-height: 1.3; margin: 2px 0; }")
            append(".meta-row { display: flex; justify-content: space-between; }")
            append(".thick-divider { border-top: 1.5px solid #000; margin: 3px 0; }")
            append(".dashed-divider { border-top: 1px dashed #000; margin: 3px 0; }")
            append("table { width: 100%; border-collapse: collapse; table-layout: fixed; margin: 2px 0; }")
            append("th { font-size: 11px; font-weight: 900; padding: 3px 1px; border-bottom: 1.5px solid #000; text-align: center; }")
            append("td { font-size: 12px; font-weight: bold; padding: 3px 1px; border-bottom: 1px dotted #000; word-break: break-word; line-height: 1.2; }")
            append(".c-name  { width: 44%; text-align: right; }")
            append(".c-qty   { width: 16%; text-align: center; }")
            append(".c-price { width: 18%; text-align: center; }")
            append(".c-total { width: 22%; text-align: left; font-weight: 900; }")
            append(".grand-total-box { display: flex; justify-content: space-between; font-size: 16px; font-weight: 900; margin-top: 3px; padding-top: 3px; border-top: 2px solid #000; }")
            append(".footer { text-align: center; font-size: 10px; font-weight: bold; margin-top: 3px; }")
            append("</style></head><body><div class='receipt'>")
            append("<div class='center store-title'>${escapeHtml(appNameLabel)}</div>")
            append("<div class='meta-box'>")
            append("<div class='meta-row'><span><b>${escapeHtml(invoiceNumberLabel)}:</b> ${escapeHtml(invNum)}</span><span>${escapeHtml(dateFormatted)}</span></div>")
            append("<div><b>${escapeHtml(customerNameLabel)}:</b> ${escapeHtml(custName)}</div>")
            append("</div>")
            append("<div class='thick-divider'></div>")
            append("<table><thead><tr>")
            append("<th class='c-name'>${escapeHtml(itemNameLabel)}</th>")
            append("<th class='c-qty'>${escapeHtml(quantityLabel)}</th>")
            append("<th class='c-price'>${escapeHtml(priceLabel)}</th>")
            append("<th class='c-total'>${escapeHtml(totalLabel)}</th>")
            append("</tr></thead><tbody>")
            currentItems.forEach { item ->
                val total = item.quantity * item.price
                append("<tr>")
                append("<td class='c-name'>${escapeHtml(item.name)}</td>")
                append("<td class='c-qty'>${formatNumber(item.quantity)}</td>")
                append("<td class='c-price'>${formatNumber(item.price)}</td>")
                append("<td class='c-total'>${formatNumber(total)}</td>")
                append("</tr>")
            }
            append("</tbody></table>")
            append("<div class='grand-total-box'><span>${escapeHtml(totalLabel)}:</span><span>${formatNumber(grandTotal)}</span></div>")
            append("<div class='dashed-divider'></div>")
            append("<div class='footer'>شكراً لتعاملكم معنا</div>")
            append("</div></body></html>")
        }

        val webView = android.webkit.WebView(context)
        webView.settings.apply {
            defaultTextEncodingName = "UTF-8"
            useWideViewPort = false
            loadWithOverviewMode = false
            textZoom = 100
        }
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView, url: String) {
                val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
                val calculatedHeightMm = (42 + currentItems.size * 7 + 28).coerceAtLeast(65)
                val heightMils = (calculatedHeightMm * 39.3701).toInt()
                val printAttributes = android.print.PrintAttributes.Builder()
                    .setMediaSize(android.print.PrintAttributes.MediaSize("ISO_58MM_ROLL", "58mm Roll", 2283, heightMils))
                    .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                    .setColorMode(android.print.PrintAttributes.COLOR_MODE_MONOCHROME)
                    .setResolution(android.print.PrintAttributes.Resolution("thermal_pos_203", "203 DPI", 203, 203))
                    .build()
                val printJobName = "فاتورة_${invNum}"
                printManager.print(printJobName, view.createPrintDocumentAdapter(printJobName), printAttributes)
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    Scaffold(
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.primaryContainer).statusBarsPadding().padding(top = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ActionChip(stringResource(R.string.clear_page), Color(0xFFFF9800)) { viewModel.clearCurrentNote() }
                    ActionChip(stringResource(R.string.new_note), Color(0xFF2196F3)) { viewModel.createNewNote() }
                    ActionChip(stringResource(R.string.history), Color(0xFF9C27B0)) { onOpenHistory() }
                    ActionChip(stringResource(R.string.smart_print), Color(0xFF4CAF50)) { printNote() }
                    ActionChip(stringResource(R.string.delete), Color(0xFFF44336)) { showDeleteConfirm = true }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.updateNoteSettings((currentNote?.fontSize ?: 14) - 1, currentNote?.scrollEnabled ?: true) }) { Icon(Icons.Default.Remove, null) }
                        Text("${stringResource(R.string.font_size)} ${currentNote?.fontSize ?: 14}")
                        IconButton(onClick = { viewModel.updateNoteSettings((currentNote?.fontSize ?: 14) + 1, currentNote?.scrollEnabled ?: true) }) { Icon(Icons.Default.Add, null) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.shareCurrentInvoice() }) {
                            Icon(Icons.Default.Share, contentDescription = "مشاركة الفاتورة / بلوتوث", tint = MaterialTheme.colorScheme.primary)
                        }
                        Text("${stringResource(R.string.total)}: ${formatNumber(currentItems.sumOf { it.quantity * it.price })}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
        }
    ) { pad ->
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(stringResource(R.string.delete)) },
                text = { Text(stringResource(R.string.confirm_delete)) },
                confirmButton = { TextButton(onClick = { currentNote?.let { viewModel.deleteNote(it) }; showDeleteConfirm = false }) { Text(stringResource(R.string.confirm)) } },
                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) } }
            )
        }
        Column(Modifier.padding(pad).fillMaxSize()) {
            Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${stringResource(R.string.invoice_number)}: ${invoiceNumber.text}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color.LightGray.copy(alpha = 0.3f), MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp))
                        OutlinedTextField(
                            value = customerName,
                            onValueChange = { customerName = it; viewModel.updateCustomerName(it.text) },
                            placeholder = { Text(customerNameLabel, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f).height(48.dp).onFocusChanged { if (it.isFocused) selectAllOnFocus(customerName) { customerName = it } },
                            textStyle = TextStyle(fontSize = 12.sp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White),
                            singleLine = true
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = totalInput,
                            onValueChange = { totalInput = it; updatePriceFromTotal(it.text, quantity.text) },
                            label = { Text(totalLabel, fontSize = 10.sp) },
                            modifier = Modifier.weight(0.25f).onFocusChanged { if (it.isFocused) selectAllOnFocus(totalInput) { totalInput = it } },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
                        )
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { quantity = it; updatePriceFromTotal(totalInput.text, it.text) },
                            label = { Text(quantityLabel, fontSize = 10.sp) },
                            modifier = Modifier.weight(0.20f).onFocusChanged { if (it.isFocused) selectAllOnFocus(quantity) { quantity = it } },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
                        )
                        Box(Modifier.weight(0.55f)) {
                            OutlinedTextField(
                                value = itemName,
                                onValueChange = { itemName = it; showSuggestions = normalizedQuery(it.text).isNotEmpty() },
                                label = { Text(itemNameLabel, fontSize = 10.sp) },
                                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) selectAllOnFocus(itemName) { itemName = it } },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
                            )
                            val query = normalizedQuery(itemName.text)
                            val matchingSuggestions = if (query.isEmpty()) emptyList() else suggestions.filter { normalizedQuery(it.word).contains(query, ignoreCase = true) }.distinctBy { normalizedQuery(it.word).lowercase() }.take(8)
                            if (showSuggestions && matchingSuggestions.isNotEmpty()) {
                                Card(Modifier.fillMaxWidth().padding(top = 60.dp), elevation = CardDefaults.cardElevation(8.dp)) {
                                    Column {
                                        matchingSuggestions.forEach { sug ->
                                            Text(sug.word, Modifier.fillMaxWidth().clickable { itemName = TextFieldValue(sug.word, TextRange(sug.word.length)); showSuggestions = false }.padding(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            val q = quantity.text.toDoubleOrNull() ?: 0.0
                            val total = totalInput.text.toDoubleOrNull() ?: 0.0
                            if (itemName.text.isNotBlank() && q > 0.0) {
                                viewModel.addItem(itemName.text.trim(), q, total / q, "left")
                                itemName = TextFieldValue("")
                                quantity = TextFieldValue("1")
                                price = TextFieldValue("0")
                                totalInput = TextFieldValue("0")
                                showSuggestions = false
                            }
                        },
                        Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.save_item))
                    }
                }
            }
            val fontSize = ((currentNote?.fontSize ?: 14).coerceAtMost(12)).sp
            Column(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                Row(Modifier.fillMaxWidth().background(Color.Gray.copy(alpha = 0.1f)).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(itemNameLabel, Modifier.weight(0.35f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(quantityLabel, Modifier.weight(0.15f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(priceLabel, Modifier.weight(0.20f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(totalLabel, Modifier.weight(0.20f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Spacer(Modifier.width(42.dp))
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(currentItems) { _, item ->
                        NoteItemRow(item, fontSize, Modifier.fillMaxWidth(), onUpdate = { viewModel.updateItem(it) }, onDelete = { viewModel.deleteItem(it) })
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
fun NoteItemRow(item: NoteItem, fontSize: TextUnit, modifier: Modifier, onUpdate: (NoteItem) -> Unit, onDelete: (NoteItem) -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var editName by remember { mutableStateOf(TextFieldValue(item.name)) }
    var editQty by remember { mutableStateOf(TextFieldValue(if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString())) }
    var editPrice by remember { mutableStateOf(TextFieldValue(if (item.price % 1.0 == 0.0) item.price.toInt().toString() else "%.2f".format(java.util.Locale.US, item.price))) }
    var editTotal by remember { mutableStateOf(TextFieldValue(if ((item.quantity * item.price) % 1.0 == 0.0) (item.quantity * item.price).toInt().toString() else "%.2f".format(java.util.Locale.US, item.quantity * item.price))) }

    fun formatEdit(value: Double): String = if (value.isFinite() && value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(java.util.Locale.US, value)
    fun updateEditPriceFromTotal(total: String, qty: String) {
        val t = total.toDoubleOrNull() ?: 0.0
        val q = qty.toDoubleOrNull() ?: 0.0
        editPrice = TextFieldValue(if (q > 0.0) formatEdit(t / q) else "0")
    }

    if (isEditing) {
        AlertDialog(
            onDismissRequest = { isEditing = false },
            title = { Text(stringResource(R.string.edit_item)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(
                        value = editTotal,
                        onValueChange = { editTotal = it; updateEditPriceFromTotal(it.text, editQty.text) },
                        label = { Text(stringResource(R.string.total)) },
                        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) scope.launch { delay(80); editTotal = editTotal.copy(selection = TextRange(0, editTotal.text.length)) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editQty,
                        onValueChange = { editQty = it; updateEditPriceFromTotal(editTotal.text, it.text) },
                        label = { Text(stringResource(R.string.quantity)) },
                        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) scope.launch { delay(80); editQty = editQty.copy(selection = TextRange(0, editQty.text.length)) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(stringResource(R.string.item_name)) },
                        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) scope.launch { delay(80); editName = editName.copy(selection = TextRange(0, editName.text.length)) } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editPrice,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text(stringResource(R.string.price)) },
                        supportingText = { Text("${stringResource(R.string.price)} = ${stringResource(R.string.total)} ÷ ${stringResource(R.string.quantity)}") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val q = editQty.text.toDoubleOrNull() ?: 1.0
                    val total = editTotal.text.toDoubleOrNull() ?: 0.0
                    if (editName.text.isNotBlank() && q > 0.0) {
                        onUpdate(item.copy(name = editName.text.trim(), quantity = q, price = total / q))
                    }
                    isEditing = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { isEditing = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Row(modifier.padding(vertical = 3.dp).clickable {
        editName = TextFieldValue(item.name)
        editQty = TextFieldValue(if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString())
        editTotal = TextFieldValue(formatEdit(item.quantity * item.price))
        editPrice = TextFieldValue(formatEdit(item.price))
        isEditing = true
    }, verticalAlignment = Alignment.CenterVertically) {
        CompactCell(item.name, Modifier.weight(0.35f), fontSize, TextAlign.Start)
        CompactCell(formatEdit(item.quantity), Modifier.weight(0.15f), fontSize, TextAlign.Center)
        CompactCell(formatEdit(item.price), Modifier.weight(0.20f), fontSize, TextAlign.Center)
        CompactCell(formatEdit(item.quantity * item.price), Modifier.weight(0.20f), fontSize, TextAlign.Center, FontWeight.Bold)
        IconButton(onClick = { onDelete(item) }, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun CompactCell(text: String, modifier: Modifier, fontSize: TextUnit, align: TextAlign, weight: FontWeight = FontWeight.Normal) {
    Box(modifier.padding(horizontal = 3.dp, vertical = 2.dp).heightIn(min = 30.dp, max = 54.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            fontSize = fontSize,
            fontWeight = weight,
            textAlign = align,
            maxLines = 3,
            softWrap = true,
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip
        )
    }
}

@Composable
fun ActionChip(text: String, color: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, color = color, shape = MaterialTheme.shapes.small, modifier = Modifier.height(36.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}