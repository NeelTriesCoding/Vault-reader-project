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
        
        // Initialize Room Database
        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "vault_database"
        )
        .fallbackToDestructiveMigration()
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

