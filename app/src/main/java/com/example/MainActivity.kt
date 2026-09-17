package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.NoteRepository
import com.example.ui.*
import com.example.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "notes_db")
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
                    var showExitDialog by remember { mutableStateOf(false) }
                    if (showExitDialog) {
                        AlertDialog(
                            onDismissRequest = { showExitDialog = false },
                            title = { Text(stringResource(R.string.exit_confirm_title)) },
                            text = { Text(stringResource(R.string.exit_confirm_msg)) },
                            confirmButton = { TextButton(onClick = { finish() }) { Text(stringResource(R.string.confirm)) } },
                            dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text(stringResource(R.string.cancel)) } }
                        )
                    }
                    BackHandler { showExitDialog = true }
                    val viewModel: OmniViewModel = viewModel(factory = factory)
                    AppNavigation(viewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: OmniViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "editor", modifier = Modifier.fillMaxSize()) {
        composable("editor") {
            FinalNoteEditorScreen(
                viewModel = viewModel,
                onOpenHistory = { navController.navigate("history") },
                onOpenCustomers = { navController.navigate("customers") }
            )
        }
        composable("history") { HistoryScreen(viewModel, onBack = { navController.popBackStack() }) }
        composable("customers") { CustomerAccountsScreen(viewModel, onBack = { navController.popBackStack() }) }
        composable("smart") { SmartDashboardScreen(viewModel, onBack = { navController.popBackStack() }, onOpenHistory = { navController.navigate("history") }) }
        composable("settings") { SettingsScreen(viewModel, onBack = { navController.popBackStack() }) }
    }
}
