package com.example.weatherapp

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateMapOf

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    val weatherData = mutableStateOf<CurrentWeatherResponse?>(null)
    val forecastData = mutableStateOf<ForecastResponse?>(null)
    val errorMessage = mutableStateOf<String?>(null)
    val currentCity = mutableStateOf("")

    private val prefs = application.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)

    val favoriteCities = mutableStateListOf<String>()

    val favoriteWeathers = mutableStateMapOf<String, CurrentWeatherResponse>()

    fun refreshFavoritesWeathers(apiKey: String) {
        viewModelScope.launch {
            favoriteCities.forEach { city ->
                try {
                    val response = RetrofitInstance.api.getCurrentWeather(city, apiKey)
                    favoriteWeathers[city] = response
                } catch (e: Exception) {
                }
            }
        }
    }

    init {
        val savedFavorites = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        favoriteCities.addAll(savedFavorites.sorted())
    }

    fun addFavorite(city: String) {
        val trimmedCity = city.trim()
        if (trimmedCity.isNotBlank() && !favoriteCities.contains(trimmedCity)) {
            favoriteCities.add(trimmedCity)
            saveFavorites()
        }
    }

    fun removeFavorite(city: String) {
        favoriteCities.remove(city)
        saveFavorites()
    }

    private fun saveFavorites() {
        prefs.edit().putStringSet("favorites", favoriteCities.toSet()).apply()
    }

    fun fetchCurrentWeather(city: String, apiKey: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitInstance.api.getCurrentWeather(city, apiKey)
                weatherData.value = response
                errorMessage.value = null
            } catch (e: Exception) {
                errorMessage.value = "Chyba: Zkontrolujte název města nebo připojení."
                weatherData.value = null
            }
        }
    }

    fun fetchForecast(city: String, apiKey: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitInstance.api.getForecast(city, apiKey)
                forecastData.value = response
                errorMessage.value = null
            } catch (e: Exception) {
                errorMessage.value = "Chyba při načítání předpovědi."
                forecastData.value = null
            }
        }
    }
}