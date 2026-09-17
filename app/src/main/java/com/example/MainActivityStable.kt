package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.NoteRepository
import com.example.ui.*
import com.example.ui.theme.AppTheme

class MainActivityStable : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "notes_db_v2")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        val repository = NoteRepository(db.noteDao(), applicationContext)
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return OmniViewModel(repository) as T
            }
        }
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var showExit by remember { mutableStateOf(false) }
                    if (showExit) AlertDialog(
                        onDismissRequest = { showExit = false },
                        title = { Text(stringResource(R.string.exit_confirm_title)) },
                        text = { Text(stringResource(R.string.exit_confirm_msg)) },
                        confirmButton = { TextButton(onClick = { finish() }) { Text(stringResource(R.string.confirm)) } },
                        dismissButton = { TextButton(onClick = { showExit = false }) { Text(stringResource(R.string.cancel)) } }
                    )
                    BackHandler { showExit = true }
                    val vm: OmniViewModel = viewModel(factory = factory)
                    AppNavigationStable(vm)
                }
            }
        }
    }
}

@Composable
private fun AppNavigationStable(viewModel: OmniViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    Box(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = "editor", modifier = Modifier.fillMaxSize()) {
            composable("editor") { InvoiceEditorV2Screen(viewModel, onOpenHistory = { nav.navigate("history") }, onOpenCustomers = { nav.navigate("customers") }, onOpenSettings = { nav.navigate("settings") }) }
            composable("history") { HistoryScreen(viewModel, onBack = { nav.popBackStack() }) }
            composable("customers") {
                CustomerAccountsScreen(viewModel, onBack = { nav.popBackStack() }, onOpenInvoice = { id -> viewModel.selectNote(id); nav.navigate("editor") })
            }
            composable("smart") { SmartDashboardScreen(viewModel, onBack = { nav.popBackStack() }, onOpenHistory = { nav.navigate("history") }) }
            composable("settings") { SettingsScreen(viewModel, onBack = { nav.popBackStack() }) }
        }
        if (route == "editor") {
            Row(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallFloatingActionButton(onClick = { nav.navigate("customers") }, containerColor = MaterialTheme.colorScheme.secondaryContainer) { Icon(Icons.Default.People, "حسابات العملاء") }
                SmallFloatingActionButton(onClick = { nav.navigate("settings") }, containerColor = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Default.Settings, "الإعدادات") }
            }
        }
    }
}
