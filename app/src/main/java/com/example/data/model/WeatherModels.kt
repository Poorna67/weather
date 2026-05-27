package com.example.data.model

data class WeatherData(
    val city: String,
    val temperature: Float,
    val thermalSensation: Float,
    val humidity: Int,
    val windSpeed: Float,
    val uvIndex: Int,
    val condition: WeatherCondition,
    val description: String,
    val hourlyForecast: List<HourlyForecast>,
    val weeklyForecast: List<WeeklyForecast>,
    val aiInsights: AiInsights,
    val lastUpdateTimestamp: Long = System.currentTimeMillis()
)

data class HourlyForecast(
    val time: String,      // e.g. "09:00 AM", "12:00 PM"
    val temperature: Float,
    val condition: WeatherCondition
)

data class WeeklyForecast(
    val day: String,       // e.g. "Mon", "Tue"
    val minTemp: Float,
    val maxTemp: Float,
    val condition: WeatherCondition,
    val description: String
)

data class AiInsights(
    val summary: String,              // Concise, friendly visual summary of the weather
    val clothingShort: String,         // Best outfits or items to wear/carry today
    val activitiesScore: Int,         // Out of 100, score for outdoor wellness/exercise
    val activitiesAdvice: String,      // Short advice on of what to do
    val poeticQuotes: String          // An inspirational or stylish weather-related quip
)

enum class WeatherCondition {
    SUNNY,
    CLOUDY,
    RAINY,
    STORMY,
    SNOWY,
    FOGGY;

    companion object {
        fun fromString(value: String): WeatherCondition {
            return when (value.uppercase().trim()) {
                "SUNNY", "CLEAR", "FINE" -> SUNNY
                "CLOUDY", "OVERCAST", "MISTY_CLOUDY" -> CLOUDY
                "RAINY", "RAIN", "DRIZZLE", "SHOWER" -> RAINY
                "STORMY", "THUNDERSTORM", "STORM", "LIGHTNING" -> STORMY
                "SNOWY", "SNOW", "HAIL", "ICE" -> SNOWY
                "FOGGY", "FOG", "MIST", "HAZE", "SMOKE" -> FOGGY
                else -> {
                    // Try contains matches
                    val upper = value.uppercase()
                    if (upper.contains("SUN") || upper.contains("CLEAR")) SUNNY
                    else if (upper.contains("RAIN") || upper.contains("DRIZZLE") || upper.contains("SHOWER")) RAINY
                    else if (upper.contains("STORM") || upper.contains("THUNDER")) STORMY
                    else if (upper.contains("SNOW") || upper.contains("FREEZE") || upper.contains("ICE")) SNOWY
                    else if (upper.contains("FOG") || upper.contains("MIST") || upper.contains("HAZE")) FOGGY
                    else CLOUDY // default fallback
                }
            }
        }
    }
}
