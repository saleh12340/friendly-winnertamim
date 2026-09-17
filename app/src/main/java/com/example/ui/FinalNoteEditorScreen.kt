package com.example.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.NoteItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FinalNoteEditorScreen(viewModel: OmniViewModel, onOpenHistory: () -> Unit, onOpenCustomers: () -> Unit) {
    val note by viewModel.currentNote.collectAsStateWithLifecycle()
    val items by viewModel.currentItems.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val customerNames by viewModel.customerNames.collectAsStateWithLifecycle()
    var customer by remember { mutableStateOf(TextFieldValue("")) }
    var itemName by remember { mutableStateOf(TextFieldValue("")) }
    var qty by remember { mutableStateOf(TextFieldValue("1")) }
    var total by remember { mutableStateOf(TextFieldValue("0")) }
    var showItems by remember { mutableStateOf(false) }
    var showCustomers by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val fieldShape = RoundedCornerShape(16.dp)

    LaunchedEffect(note?.id) { customer = TextFieldValue(note?.customerName.orEmpty()) }
    fun clean(s: String) = s.trim().replace(Regex("\\s+"), " ")
    fun money(v: Double) = if (v.isFinite() && v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(Locale.US, v)
    fun unitPrice(): Double { val q = qty.text.toDoubleOrNull() ?: 0.0; val t = total.text.toDoubleOrNull() ?: 0.0; return if (q > 0) t / q else 0.0 }

    fun printInvoice() {
        val n = note ?: return
        scope.launch {
            val previous = viewModel.customerBalanceSnapshot(n.customerName, n.id)
            val grand = items.sumOf { it.quantity * it.price }
            val after = previous + grand
            val inv = n.invoiceNumber.ifBlank { n.id.toString() }
            val cust = n.customerName.ifBlank { "عميل عام" }
            val date = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(n.timestamp))
            fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
            val html = buildString {
                append("<!doctype html><html dir='rtl' lang='ar'><head><meta charset='UTF-8'><style>")
                append("@page{size:58mm auto;margin:0!important}*{box-sizing:border-box}body{width:58mm;margin:0;padding:2px 3px;font-family:sans-serif;color:#000;direction:rtl}.c{text-align:center}.title{font-size:16px;font-weight:900}.meta{font-size:11px;font-weight:700;line-height:1.45}.balance{font-size:12px;font-weight:900;border:1.5px solid #000;padding:3px;margin:3px 0}.line{border-top:1px dashed #000;margin:4px 0}table{width:100%;border-collapse:collapse;table-layout:fixed}th,td{font-size:10px;padding:3px 1px;border-bottom:1px dotted #000;word-break:break-word}th{font-weight:900}.name{width:43%;text-align:right}.q{width:15%;text-align:center}.p{width:19%;text-align:center}.t{width:23%;text-align:left;font-weight:900}.grand{font-size:15px;font-weight:900;display:flex;justify-content:space-between;border-top:2px solid #000;padding-top:4px;margin-top:3px}.foot{text-align:center;font-size:9px;font-weight:700;margin-top:4px}</style></head><body>")
                append("<div class='title c'>فاتورة مبيعات</div>")
                append("<div class='balance'>الرصيد السابق: ${esc(money(previous))} ${if(previous>0.005) "عليه" else if(previous < -0.005) "له" else "متساوٍ"}</div>")
                append("<div class='meta'>رقم الفاتورة: ${esc(inv)}<br>العميل: ${esc(cust)}<br>التاريخ: ${esc(date)}</div><div class='line'></div>")
                append("<table><thead><tr><th class='name'>الصنف</th><th class='q'>العدد</th><th class='p'>سعر الوحدة</th><th class='t'>الإجمالي</th></tr></thead><tbody>")
                items.forEach { i -> append("<tr><td class='name'>${esc(i.name)}</td><td class='q'>${money(i.quantity)}</td><td class='p'>${money(i.price)}</td><td class='t'>${money(i.quantity*i.price)}</td></tr>") }
                append("</tbody></table><div class='grand'><span>الإجمالي</span><span>${money(grand)}</span></div>")
                append("<div class='balance'>الرصيد بعد الفاتورة: ${esc(money(after))} ${if(after>0.005) "عليه" else if(after < -0.005) "له" else "متساوٍ"}</div><div class='foot'>شكراً لتعاملكم معنا</div></body></html>")
            }
            val web = WebView(context)
            web.settings.defaultTextEncodingName = "UTF-8"
            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val height = ((55 + items.size * 7) * 39.3701).toInt().coerceAtLeast(2559)
                    val attrs = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize("T58", "58mm Roll", 2283, height)).setMinMargins(PrintAttributes.Margins.NO_MARGINS).setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build()
                    pm.print("فاتورة_$inv", view.createPrintDocumentAdapter("فاتورة_$inv"), attrs)
                }
            }
            web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    }

    fun changeFont(delta: Int) = viewModel.updateNoteSettings((note?.fontSize ?: 14) + delta, note?.scrollEnabled ?: true)
    @Composable fun fontButton(icon: androidx.compose.ui.graphics.vector.ImageVector, delta: Int) {
        Box(Modifier.size(42.dp).combinedClickable(onClick={changeFont(delta)},onLongClick={scope.launch { repeat(20) { changeFont(delta); delay(120) } }}),contentAlignment=Alignment.Center) { Icon(icon, contentDescription = null) }
    }

    Scaffold(topBar={
        Column(Modifier.background(MaterialTheme.colorScheme.primaryContainer).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(6.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                SmallTopAppBarButton("مسح"){viewModel.clearCurrentNote()}
                SmallTopAppBarButton("فاتورة جديدة"){viewModel.createNewNote()}
                SmallTopAppBarButton("السجل"){onOpenHistory()}
                SmallTopAppBarButton("العملاء"){onOpenCustomers()}
                SmallTopAppBarButton("طباعة"){printInvoice()}
                SmallTopAppBarButton("حذف"){deleteConfirm=true}
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Row(verticalAlignment=Alignment.CenterVertically){fontButton(Icons.Default.Remove,-1);Text("حجم الخط ${note?.fontSize?:14}",fontWeight=FontWeight.Bold);fontButton(Icons.Default.Add,1)};Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick={viewModel.shareCurrentInvoice()}){Icon(Icons.Default.Share,"مشاركة")};Text("الإجمالي: ${money(items.sumOf{it.quantity*it.price})}",fontWeight=FontWeight.Bold)}}
        }
    }){pad->
        if(deleteConfirm)AlertDialog(onDismissRequest={deleteConfirm=false},title={Text("حذف الفاتورة")},text={Text("هل تريد حذف الفاتورة الحالية؟")},confirmButton={TextButton(onClick={note?.let{viewModel.deleteNote(it)};deleteConfirm=false}){Text("حذف")}},dismissButton={TextButton(onClick={deleteConfirm=false}){Text("إلغاء")}})
        Column(Modifier.padding(pad).fillMaxSize()){
            Card(Modifier.fillMaxWidth().padding(horizontal=7.dp,vertical=6.dp),shape=RoundedCornerShape(20.dp),elevation=CardDefaults.cardElevation(3.dp)){Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Box {
                    OutlinedTextField(
                        value=customer,
                        onValueChange={ customer=it; showCustomers=clean(it.text).isNotEmpty() },
                        label={Text("اسم العميل")},
                        singleLine=true,
                        modifier=Modifier.fillMaxWidth().onFocusChanged { state ->
                            if (state.isFocused) {
                                if (customer.text.isNotEmpty()) customer=customer.copy(selection=TextRange(0,customer.text.length))
                            } else {
                                viewModel.updateCustomerName(customer.text)
                                showCustomers=false
                            }
                        },
                        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Next),
                        keyboardActions=KeyboardActions(onNext={viewModel.updateCustomerName(customer.text)}),
                        textStyle=TextStyle(fontSize=13.sp),shape=fieldShape
                    )
                    val cq=clean(customer.text)
                    val cm=if(cq.isEmpty()) emptyList() else customerNames.filter{clean(it).contains(cq,true)}.take(7)
                    if(showCustomers&&cm.isNotEmpty()) Card(Modifier.fillMaxWidth().padding(top=62.dp),shape=RoundedCornerShape(14.dp)){Column{cm.forEach{c->Text(c,Modifier.fillMaxWidth().clickable{customer=TextFieldValue(c,TextRange(c.length));showCustomers=false;viewModel.updateCustomerName(c)}.padding(11.dp))}}}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    OutlinedTextField(value=total,onValueChange={total=it},label={Text("الإجمالي")},singleLine=true,modifier=Modifier.weight(1.15f).onFocusChanged{if(it.isFocused&&total.text.isNotEmpty())total=total.copy(selection=TextRange(0,total.text.length))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Next),textStyle=TextStyle(fontSize=15.sp,fontWeight=FontWeight.Bold),shape=fieldShape)
                    OutlinedTextField(value=qty,onValueChange={qty=it},label={Text("الكمية")},singleLine=true,modifier=Modifier.weight(.8f).onFocusChanged{if(it.isFocused&&qty.text.isNotEmpty())qty=qty.copy(selection=TextRange(0,qty.text.length))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Next),textStyle=TextStyle(fontSize=13.sp),shape=fieldShape)
                    Box(Modifier.weight(1.7f)){
                        OutlinedTextField(value=itemName,onValueChange={itemName=it;showItems=clean(it.text).isNotEmpty()},label={Text("اسم الصنف")},singleLine=true,modifier=Modifier.fillMaxWidth().onFocusChanged{if(it.isFocused&&itemName.text.isNotEmpty())itemName=itemName.copy(selection=TextRange(0,itemName.text.length))},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),textStyle=TextStyle(fontSize=13.sp),shape=fieldShape)
                        val q=clean(itemName.text)
                        val ms=if(q.isEmpty())emptyList()else suggestions.filter{clean(it.word).contains(q,true)}.take(8)
                        if(showItems&&ms.isNotEmpty())Card(Modifier.fillMaxWidth().padding(top=62.dp),shape=RoundedCornerShape(14.dp)){Column{ms.forEach{s->Text(s.word,Modifier.fillMaxWidth().clickable{itemName=TextFieldValue(s.word,TextRange(s.word.length));showItems=false}.padding(10.dp))}}}
                    }
                }
                Text("سعر الوحدة المحسوب: ${money(unitPrice())}",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick={val q=qty.text.toDoubleOrNull()?:0.0;val t=total.text.toDoubleOrNull()?:0.0;if(itemName.text.isNotBlank()&&q>0){viewModel.addItem(clean(itemName.text),q,t/q,"left");itemName=TextFieldValue("");qty=TextFieldValue("1");total=TextFieldValue("0");showItems=false}},Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.Add,null);Spacer(Modifier.width(5.dp));Text("إضافة الصنف")}
            }}
            Row(Modifier.fillMaxWidth().background(Color.Gray.copy(alpha=.1f)).padding(6.dp),verticalAlignment=Alignment.CenterVertically){CellHeader("الصنف",.36f);CellHeader("الكمية",.14f);CellHeader("سعر الوحدة",.20f);CellHeader("الإجمالي",.20f);Spacer(Modifier.width(42.dp))}
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxSize()){itemsIndexed(items){_,it->FinalItemRow(it,note?.fontSize?:14,{viewModel.updateItem(it)},{viewModel.deleteItem(it)});HorizontalDivider(color=Color.LightGray.copy(alpha=.5f))}}
        }
    }
}

@Composable private fun SmallTopAppBarButton(text:String,onClick:()->Unit)=Surface(onClick=onClick,color=MaterialTheme.colorScheme.primary,shape=RoundedCornerShape(10.dp),modifier=Modifier.height(34.dp)){Box(Modifier.padding(horizontal=7.dp),contentAlignment=Alignment.Center){Text(text,color=Color.White,fontSize=10.sp,fontWeight=FontWeight.Bold)}}
@Composable private fun RowScope.CellHeader(text:String,w:Float){Text(text,Modifier.weight(w),textAlign=TextAlign.Center,fontSize=10.sp,fontWeight=FontWeight.Bold)}
@Composable private fun FinalItemRow(item:NoteItem,font:Int,onUpdate:(NoteItem)->Unit,onDelete:(NoteItem)->Unit){Row(Modifier.fillMaxWidth().clickable{onUpdate(item)},verticalAlignment=Alignment.CenterVertically){Text(item.name,Modifier.weight(.36f).padding(4.dp),fontSize=font.coerceAtMost(14).sp,maxLines=2);Text(fmt(item.quantity),Modifier.weight(.14f),textAlign=TextAlign.Center,fontSize=font.coerceAtMost(14).sp);Text(fmt(item.price),Modifier.weight(.20f),textAlign=TextAlign.Center,fontSize=font.coerceAtMost(14).sp);Text(fmt(item.quantity*item.price),Modifier.weight(.20f),textAlign=TextAlign.Center,fontSize=font.coerceAtMost(14).sp,fontWeight=FontWeight.Bold);IconButton(onClick={onDelete(item)},modifier=Modifier.size(42.dp)){Icon(Icons.Default.Delete,null,tint=Color.Red)}}}
private fun fmt(v:Double)=if(v.isFinite()&&v%1.0==0.0)v.toLong().toString() else "%.2f".format(Locale.US,v)
