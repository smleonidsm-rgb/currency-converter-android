package com.leonidsm.rsdconverter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leonidsm.rsdconverter.ui.CurrencyViewModel
import com.leonidsm.rsdconverter.ui.MainScreen
import com.leonidsm.rsdconverter.ui.theme.RsdConverterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: CurrencyViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()
            RsdConverterTheme(useDynamicColor = uiState.useDynamicColor) {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
