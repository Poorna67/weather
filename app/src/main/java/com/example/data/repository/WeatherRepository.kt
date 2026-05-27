package com.example.data.repository

import android.util.Log
import com.example.data.local.CityEntity
import com.example.data.local.WeatherCacheEntity
import com.example.data.local.WeatherDao
import com.example.data.model.AiInsights
import com.example.data.model.HourlyForecast
import com.example.data.model.WeatherCondition
import com.example.data.model.WeatherData
import com.example.data.model.WeeklyForecast
import com.example.data.remote.GeminiWeatherService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class WeatherRepository(private val weatherDao: WeatherDao) {
    private val TAG = "WeatherRepository"

    val favoriteCities: Flow<List<String>> = weatherDao.getFavoriteCities().map { entities ->
        entities.map { it.name }
    }

    suspend fun addFavoriteCity(city: String) {
        weatherDao.insertFavoriteCity(CityEntity(city.trim().capitalizeWord()))
    }

    suspend fun removeFavoriteCity(city: String) {
        weatherDao.deleteFavoriteCity(city.trim().capitalizeWord())
    }

    suspend fun isCityFavorited(city: String): Boolean {
        return weatherDao.getFavoriteCityByName(city.trim().capitalizeWord()) != null
    }

    suspend fun getWeatherData(city: String, forceRefresh: Boolean = false): Pair<WeatherData, Boolean> {
        val normalizedCity = city.trim().capitalizeWord()

        // 1. Try Cache first if not forcing refresh
        if (!forceRefresh) {
            val cached = weatherDao.getWeatherCache(normalizedCity)
            if (cached != null) {
                try {
                    val weather = deserializeWeatherData(cached.weatherJson, cached.timestamp)
                    Log.d(TAG, "Loaded cached weather for $normalizedCity")
                    return Pair(weather, true) // returned from cache
                } catch (e: Exception) {
                    Log.e(TAG, "Error deserializing cache for $normalizedCity: ${e.message}")
                }
            }
        }

        // 2. Try Remote Fetch from Gemini
        val remoteWeather = GeminiWeatherService.getForecast(normalizedCity)
        if (remoteWeather != null) {
            try {
                // Save to cache
                val jsonStr = serializeWeatherData(remoteWeather)
                weatherDao.insertWeatherCache(
                    WeatherCacheEntity(
                        city = normalizedCity,
                        weatherJson = jsonStr,
                        timestamp = System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "Successfully fetched and cached remote weather for $normalizedCity")
                return Pair(remoteWeather, false) // fetched fresh online
            } catch (e: Exception) {
                Log.e(TAG, "Error caching fetched weather: ${e.message}")
            }
        }

        // 3. Last Resort Fallback (Cached fallback, or newly simulated fallback if no cache exists)
        val cached = weatherDao.getWeatherCache(normalizedCity)
        if (cached != null) {
            try {
                val weather = deserializeWeatherData(cached.weatherJson, cached.timestamp)
                return Pair(weather, true)
            } catch (e: Exception) {}
        }

        // Generate incredibly beautiful simulated climate fallback for seamless offline testing
        val simulated = generateSimulatedWeather(normalizedCity)
        return Pair(simulated, true) // treated as local simulated
    }

    private fun serializeWeatherData(data: WeatherData): String {
        val root = JSONObject().apply {
            put("city", data.city)
            put("temperature", data.temperature)
            put("thermalSensation", data.thermalSensation)
            put("humidity", data.humidity)
            put("windSpeed", data.windSpeed)
            put("uvIndex", data.uvIndex)
            put("condition", data.condition.name)
            put("description", data.description)

            // Hourly
            val hourlyArray = JSONArray()
            data.hourlyForecast.forEach { h ->
                hourlyArray.put(JSONObject().apply {
                    put("time", h.time)
                    put("temperature", h.temperature)
                    put("condition", h.condition.name)
                })
            }
            put("hourlyForecast", hourlyArray)

            // Weekly
            val weeklyArray = JSONArray()
            data.weeklyForecast.forEach { w ->
                weeklyArray.put(JSONObject().apply {
                    put("day", w.day)
                    put("minTemp", w.minTemp)
                    put("maxTemp", w.maxTemp)
                    put("condition", w.condition.name)
                    put("description", w.description)
                })
            }
            put("weeklyForecast", weeklyArray)

            // Insights
            put("aiInsights", JSONObject().apply {
                put("summary", data.aiInsights.summary)
                put("clothingShort", data.aiInsights.clothingShort)
                put("activitiesScore", data.aiInsights.activitiesScore)
                put("activitiesAdvice", data.aiInsights.activitiesAdvice)
                put("poeticQuotes", data.aiInsights.poeticQuotes)
            })
        }
        return root.toString()
    }

    private fun deserializeWeatherData(jsonStr: String, timestamp: Long): WeatherData {
        val obj = JSONObject(jsonStr)
        val hourlyList = mutableListOf<HourlyForecast>()
        val hourlyArray = obj.getJSONArray("hourlyForecast")
        for (i in 0 until hourlyArray.length()) {
            val h = hourlyArray.getJSONObject(i)
            hourlyList.add(
                HourlyForecast(
                    time = h.getString("time"),
                    temperature = h.getDouble("temperature").toFloat(),
                    condition = WeatherCondition.valueOf(h.getString("condition"))
                )
            )
        }

        val weeklyList = mutableListOf<WeeklyForecast>()
        val weeklyArray = obj.getJSONArray("weeklyForecast")
        for (i in 0 until weeklyArray.length()) {
            val w = weeklyArray.getJSONObject(i)
            weeklyList.add(
                WeeklyForecast(
                    day = w.getString("day"),
                    minTemp = w.getDouble("minTemp").toFloat(),
                    maxTemp = w.getDouble("maxTemp").toFloat(),
                    condition = WeatherCondition.valueOf(w.getString("condition")),
                    description = w.getString("description")
                )
            )
        }

        val insightsObj = obj.getJSONObject("aiInsights")
        val insights = AiInsights(
            summary = insightsObj.getString("summary"),
            clothingShort = insightsObj.getString("clothingShort"),
            activitiesScore = insightsObj.getInt("activitiesScore"),
            activitiesAdvice = insightsObj.optString("activitiesAdvice", "Stay hydrated and active!"),
            poeticQuotes = insightsObj.getString("poeticQuotes")
        )

        return WeatherData(
            city = obj.getString("city"),
            temperature = obj.getDouble("temperature").toFloat(),
            thermalSensation = obj.getDouble("thermalSensation").toFloat(),
            humidity = obj.getInt("humidity"),
            windSpeed = obj.getDouble("windSpeed").toFloat(),
            uvIndex = obj.getInt("uvIndex"),
            condition = WeatherCondition.valueOf(obj.getString("condition")),
            description = obj.getString("description"),
            hourlyForecast = hourlyList,
            weeklyForecast = weeklyList,
            aiInsights = insights,
            lastUpdateTimestamp = timestamp
        )
    }

    private fun generateSimulatedWeather(city: String): WeatherData {
        val conditions = listOf(
            WeatherCondition.SUNNY,
            WeatherCondition.CLOUDY,
            WeatherCondition.RAINY,
            WeatherCondition.FOGGY,
            WeatherCondition.STORMY,
            WeatherCondition.SNOWY
        )

        // Deterministic seeding based on city name length and characters
        val hash = city.hashCode()
        val cIndex = kotlin.math.abs(hash) % conditions.size
        val condition = conditions[cIndex]

        val baseTemp = when (condition) {
            WeatherCondition.SUNNY -> 26.5f
            WeatherCondition.CLOUDY -> 20.0f
            WeatherCondition.RAINY -> 15.5f
            WeatherCondition.STORMY -> 18.0f
            WeatherCondition.SNOWY -> -1.5f
            WeatherCondition.FOGGY -> 12.0f
        } + (hash % 4) // adjust offset slightly

        val feelsLike = baseTemp - if (condition == WeatherCondition.RAINY || condition == WeatherCondition.STORMY) 2f else -0.5f
        val humidity = when (condition) {
            WeatherCondition.SUNNY -> 42
            WeatherCondition.CLOUDY -> 68
            WeatherCondition.RAINY -> 89
            WeatherCondition.STORMY -> 95
            WeatherCondition.SNOWY -> 78
            WeatherCondition.FOGGY -> 98
        }
        val windSpeed = when (condition) {
            WeatherCondition.STORMY -> 38.4f
            WeatherCondition.SUNNY -> 8.5f
            else -> 15.0f
        }
        val uv = when (condition) {
            WeatherCondition.SUNNY -> 8
            WeatherCondition.CLOUDY -> 4
            else -> 1
        }

        val desc = when (condition) {
            WeatherCondition.SUNNY -> "Brilliant clear horizons with golden light"
            WeatherCondition.CLOUDY -> "Overlapping silver clouds layered in heights"
            WeatherCondition.RAINY -> "Soft rhythmic misting showers under cool winds"
            WeatherCondition.STORMY -> "Heavy rumbling electric clouds with wind gusts"
            WeatherCondition.SNOWY -> "Crisp tranquil snowfall draping of clean white"
            WeatherCondition.FOGGY -> "Ethereal dense morning dew drifting gently"
        }

        // Daily hourly forecast
        val hours = listOf("08:00 AM", "11:00 AM", "02:00 PM", "05:00 PM", "08:00 PM", "11:00 PM")
        val hourly = hours.mapIndexed { idx, hour ->
            val factor = when(idx) {
                0 -> -3f
                1 -> -1f
                2 -> 1.5f
                3 -> 0.5f
                4 -> -2f
                else -> -4f
            }
            HourlyForecast(
                time = hour,
                temperature = baseTemp + factor,
                condition = if (idx == 2 && condition == WeatherCondition.CLOUDY) WeatherCondition.SUNNY else condition
            )
        }

        // 5-day forecast
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
        val weekly = days.mapIndexed { idx, d ->
            val dev = (hash + idx) % 3
            WeeklyForecast(
                day = d,
                minTemp = baseTemp - 5 + dev,
                maxTemp = baseTemp + 3 + dev,
                condition = conditions[(cIndex + idx) % conditions.size],
                description = "Pleasant conditions expected"
            )
        }

        val insights = when (condition) {
            WeatherCondition.SUNNY -> AiInsights(
                summary = "A gorgeous sunlit envelope drapes $city today, offering maximum clarity and sky-high atmospheric warmth.",
                clothingShort = "Wear highly breathable fabrics, clear-rimmed sunglasses, a clean linen shirt, and UV sunscreen guard.",
                activitiesScore = 95,
                activitiesAdvice = "Flawless day for open-air runs, local community park visits, or a sunny botanical garden tour.",
                poeticQuotes = "The golden warmth reminds us that today holds fresh canvas beginnings."
            )
            WeatherCondition.CLOUDY -> AiInsights(
                summary = "A serene blanket of light slate clouds settles over $city, filtering the sun for a cool, cozy shade.",
                clothingShort = "Opt for comfortable layered casualwear: soft knitted sweaters, cotton bottoms, and casual white canvas shoes.",
                activitiesScore = 80,
                activitiesAdvice = "Wonderful afternoon for a brisk stroll, taking landscape photographs, or visiting an artisan indoor cafe.",
                poeticQuotes = "Clouds are but thoughts drifting lazily across the grand theater of the sky."
            )
            WeatherCondition.RAINY -> AiInsights(
                summary = "Rhythmic, soothing rainfall sweeps into $city today, perfect for replenishing the flora and softening the soil.",
                clothingShort = "Wear water-resistant outerwear, structured combat boots, and bring a clean minimalist automatic umbrella.",
                activitiesScore = 38,
                activitiesAdvice = "Ideal day for reading in library nooks, brewing premium chai tea, or settling down with cinema.",
                poeticQuotes = "Some people feel the rain; others just get wet."
            )
            WeatherCondition.STORMY -> AiInsights(
                summary = "Electric storm currents charge the atmosphere over $city, bringing dramatic cloud formations and wind gusts.",
                clothingShort = "Choose strong windproof trench coats and high-collar warmers. Stay completely dry and sheltered inside.",
                activitiesScore = 15,
                activitiesAdvice = "Best to schedule indoor gym exercises, creative writing, or hot cooking tasks at home today.",
                poeticQuotes = "Storms do not come to destroy; they clear outdated pathways for clean air."
            )
            WeatherCondition.SNOWY -> AiInsights(
                summary = "Tranquil arctic temperatures lock $city in an elegant ivory dreamscape, with clean snow crystals drifting.",
                clothingShort = "Layer with thick thermal insulations: fleece cardigans, sturdy padded downs, knitted scarfs, and warm gloves.",
                activitiesScore = 65,
                activitiesAdvice = "Fantastic for enjoying a winter hot-cocoa, snow activities, or walking along frosted streets.",
                poeticQuotes = "Snowflake whispers fall with stunning grace, tucking the active world into a quiet slumber."
            )
            WeatherCondition.FOGGY -> AiInsights(
                summary = "A soft silver mist drapes over $city this morning, creating a mystical atmosphere with low visibility and cool breezes.",
                clothingShort = "Wear warm fleece-lined wind jackets, light comfortable mufflers, and high-visibility light clothing.",
                activitiesScore = 55,
                activitiesAdvice = "Enjoy atmospheric morning coffee walks, quiet reading sessions, or indoor museum exhibits.",
                poeticQuotes = "Fog is but a low cloud kissing the earth with damp morning grace."
            )
        }

        return WeatherData(
            city = city,
            temperature = baseTemp,
            thermalSensation = feelsLike,
            humidity = humidity,
            windSpeed = windSpeed,
            uvIndex = uv,
            condition = condition,
            description = desc,
            hourlyForecast = hourly,
            weeklyForecast = weekly,
            aiInsights = insights
        )
    }

    private fun String.capitalizeWord(): String {
        return split(" ").joinToString(" ") { word ->
            if (word.isNotEmpty()) {
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            } else {
                word
            }
        }
    }
}
