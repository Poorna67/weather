package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.DependencyProvider
import com.example.ui.screens.WeatherDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.WeatherViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize our lightweight dependency provider and database cache
        DependencyProvider.initialize(this)
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Instantiates our custom WeatherViewModel using our repository
                val viewModel: WeatherViewModel = viewModel(
                    factory = WeatherViewModel.Factory(DependencyProvider.getRepository())
                )
                
                WeatherDashboard(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

