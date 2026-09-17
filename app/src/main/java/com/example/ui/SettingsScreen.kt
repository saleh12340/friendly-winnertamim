package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.BackupManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: OmniViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingUri = uri
            showRestoreConfirm = true
        }
    }

    fun restoreFromUri(uri: Uri) {
        scope.launch {
            busy = true
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (text.isNullOrBlank()) {
                    message = "ملف النسخة الاحتياطية فارغ أو غير صالح"
                } else {
                    viewModel.restoreBackupJson(text)
                    message = "تم استرجاع كامل البيانات بنجاح"
                }
            } catch (_: Exception) {
                message = "تعذر استرجاع النسخة الاحتياطية"
            } finally {
                busy = false
            }
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false; pendingUri = null },
            title = { Text("تأكيد الاسترجاع") },
            text = { Text("سيتم استبدال البيانات الحالية ببيانات النسخة الاحتياطية. هل تريد المتابعة؟") },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingUri
                    showRestoreConfirm = false
                    pendingUri = null
                    if (uri != null) restoreFromUri(uri)
                }) { Text("استرجاع") }
            },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = false; pendingUri = null }) { Text("إلغاء") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "رجوع") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("البيانات والنسخ الاحتياطي", style = MaterialTheme.typography.titleLarge)
            Text("احفظ أو استرجع الفواتير والأصناف والاقتراحات وبيانات التطبيق كاملة.", style = MaterialTheme.typography.bodyMedium)

            Button(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val json = viewModel.createBackupJson()
                            val name = "backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.json"
                            val ok = BackupManager(context).saveTextFile("New-Tamim-invoices/Backups", name, json)
                            message = if (ok) "تم إنشاء النسخة وحفظها في مجلد التنزيلات/New-Tamim-invoices/Backups" else "تعذر حفظ النسخة الاحتياطية"
                        } catch (_: Exception) {
                            message = "تعذر إنشاء النسخة الاحتياطية"
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Icon(Icons.Default.Backup, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("إنشاء نسخة احتياطية")
            }

            OutlinedButton(
                enabled = !busy,
                onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("استرجاع نسخة احتياطية")
            }

            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("جاري التنفيذ...")
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
