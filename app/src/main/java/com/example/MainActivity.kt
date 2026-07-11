package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.VaultRepository
import com.example.ui.VaultApp
import com.example.ui.VaultViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Room Database. v2 → v3 migrates additively (radial-map
        // columns); only prehistoric v1 dev installs fall back destructively.
        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "vault_database"
        )
        .addMigrations(AppDatabase.MIGRATION_2_3)
        .fallbackToDestructiveMigrationFrom(1)
        .build()
        
        val repository = VaultRepository(database)
        
        // Instantiate the ViewModel
        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VaultViewModel(application, repository) as T
            }
        })[VaultViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                VaultApp(viewModel = viewModel)
            }
        }
    }
}

