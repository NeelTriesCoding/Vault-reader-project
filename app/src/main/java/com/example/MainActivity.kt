package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.VaultRepository
import com.example.ui.VaultApp
import com.example.ui.VaultViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Room database with explicit, non-destructive migrations (see AppDatabase).
        val database = AppDatabase.getInstance(applicationContext)

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

