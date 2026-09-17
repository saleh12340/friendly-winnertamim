package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartDashboardScreen(
    viewModel: OmniViewModel,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val notes by viewModel.allNotes.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val currentItems by viewModel.currentItems.collectAsStateWithLifecycle()
    val currentNote by viewModel.currentNote.collectAsStateWithLifecycle()

    val customerCount = notes.map { it.customerName.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .size
    val numberedInvoices = notes.mapNotNull { it.invoiceNumber.toIntOrNull() }
    val latestNumber = numberedInvoices.maxOrNull() ?: 0
    val hasDraftWarning = currentNote != null && currentItems.isEmpty()
    val recent = notes.take(5)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${"مركز ذكي"} • فاتورة مبيعات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, contentDescription = "سجل الفواتير")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("ملخص فوري من بيانات الجهاز", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("تحليل محلي بدون إرسال بيانات الفواتير إلى أي خدمة خارجية.", style = MaterialTheme.typography.bodySmall)
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("الفواتير", notes.size.toString(), Modifier.weight(1f))
                    StatCard("العملاء", customerCount.toString(), Modifier.weight(1f))
                    StatCard("اقتراحات", suggestions.size.toString(), Modifier.weight(1f))
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null)
                        Column {
                            Text("اقتراحات ذكية", fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    hasDraftWarning -> "الفاتورة الحالية فارغة من الأصناف؛ أضف صنفاً قبل الطباعة أو المشاركة."
                                    latestNumber > 0 -> "آخر رقم فاتورة رقمي ظاهر في السجل هو $latestNumber."
                                    else -> "أنشئ أول فاتورة للبدء ببناء سجل المبيعات."
                                }
                            )
                        }
                    }
                }
            }

            item {
                Text("آخر الفواتير", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            items(recent, key = { it.id }) { note ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            note.customerName.ifBlank { "عميل عام" },
                            fontWeight = FontWeight.Bold
                        )
                        Text("الفاتورة: ${note.invoiceNumber.ifBlank { note.id.toString() }}")
                        Text(
                            java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(note.timestamp)),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Text("الأصناف المقترحة الأكثر استخداماً", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(suggestions.take(10), key = { it.id }) { suggestion ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(suggestion.word)
                    Text("${suggestion.count}", fontWeight = FontWeight.Bold)
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}
