package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.repository.WeatherRepository
import com.example.data.model.WeatherData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WeatherUiState {
    object Loading : WeatherUiState
    data class Success(val weather: WeatherData, val isFallbackOrCached: Boolean) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

class WeatherViewModel(private val repository: WeatherRepository) : ViewModel() {
    private val TAG = "WeatherViewModel"

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeCity = MutableStateFlow("Tokyo")
    val activeCity: StateFlow<String> = _activeCity.asStateFlow()

    val favoriteCities: StateFlow<List<String>> = repository.favoriteCities
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isCurrentCityFavorited: StateFlow<Boolean> = combine(activeCity, favoriteCities) { active, favorites ->
        favorites.any { it.equals(active, ignoreCase = true) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val isApiKeyPresent: Boolean
        get() {
            val key = BuildConfig.GEMINI_API_KEY
            return key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
        }

    init {
        // Load the default city first
        fetchWeather(_activeCity.value)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun fetchWeather(city: String, forceRefresh: Boolean = false) {
        if (city.isBlank()) return
        
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            try {
                _activeCity.value = city
                val (weatherData, isFallback) = repository.getWeatherData(city, forceRefresh)
                _uiState.value = WeatherUiState.Success(weatherData, isFallback)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading weather for $city: ${e.message}", e)
                _uiState.value = WeatherUiState.Error(e.localizedMessage ?: "Failed to retrieve weather reports.")
            }
        }
    }

    fun toggleFavorite() {
        val city = _activeCity.value
        viewModelScope.launch {
            val isFav = isCurrentCityFavorited.value
            if (isFav) {
                repository.removeFavoriteCity(city)
            } else {
                repository.addFavoriteCity(city)
            }
        }
    }

    class Factory(private val repository: WeatherRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
                return WeatherViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
