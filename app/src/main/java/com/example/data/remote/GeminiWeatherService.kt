package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AiInsights
import com.example.data.model.HourlyForecast
import com.example.data.model.WeatherCondition
import com.example.data.model.WeatherData
import com.example.data.model.WeeklyForecast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiWeatherService {
    private const val TAG = "GeminiWeatherService"
    private const val MODEL = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun getForecast(city: String): WeatherData? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API Key is not set or is placeholder")
            return@withContext null
        }

        val prompt = """
            Fetch the current weather, 24-hour hourly forecast spanning today, and 5-day weekly forecast for the city of "$city".
            Your response MUST be a single raw JSON object matching this schema exactly without any markdown backticks or explanation:
            {
              "city": "string (the exact name of the city, capitalized)",
              "temperature": float (current temperature in Celsius),
              "thermalSensation": float (feels-like temperature in Celsius),
              "humidity": int (percentage 0-100),
              "windSpeed": float (in km/h),
              "uvIndex": int (0-11 index),
              "condition": "SUNNY", "CLOUDY", "RAINY", "STORMY", "SNOWY", or "FOGGY" (pick the best matching keyword),
              "description": "string (brief elegant description of current weather, e.g. 'Light showers with cool evening breeze')",
              "hourlyForecast": [
                { "time": "string (hour, e.g. '09:00 AM', '12:00 PM', '03:00 PM', '06:00 PM', '09:00 PM')", "temperature": float, "condition": "SUNNY" }
              ],
              "weeklyForecast": [
                { "day": "string (abbreviated day of week, e.g. 'Mon', 'Tue')", "minTemp": float, "maxTemp": float, "condition": "SUNNY", "description": "string (very short summary of that day's weather)" }
              ],
              "aiInsights": {
                "summary": "string (a warm, elegant, personal style weather summary. Max 2 sentences)",
                "clothingShort": "string (practical outfit list, gear to pack, accessories to wear today. Max 2 sentences)",
                "activitiesScore": int (0-100 score for outdoor activities/wellness),
                "activitiesAdvice": "string (short activity advice or guide)",
                "poeticQuotes": "string (a beautiful, stylized weather quote or poetic perspective of today's morning mood. Max 1 sentence)"
              }
            }
            Provide and generate realistic, highly sensible weather figures and forecast trends for "$city" based on typical weather conditions during the current month.
        """.trimIndent()

        // Build Gemini request body
        val jsonRequest = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", prompt)
                }))
            }))
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.7)
            })
        }

        val requestBody = jsonRequest.toString().toRequestBody("application/json".toMediaType())
        val url = "$BASE_URL?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Request failed with code: ${response.code}. Body: ${response.body?.string()}")
                    return@withContext null
                }

                val responseBodyStr = response.body?.string() ?: return@withContext null
                val rootJson = JSONObject(responseBodyStr)
                Log.d(TAG, "Response fetched successfully")

                // Extract candidates[0].content.parts[0].text
                val candidates = rootJson.getJSONArray("candidates")
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.getJSONObject("content")
                val parts = content.getJSONArray("parts")
                val text = parts.getJSONObject(0).getString("text")

                // Parse the nested json response containing the actual forecast details
                val forecastJson = JSONObject(text.trim())

                // Parse models
                val cityName = forecastJson.optString("city", city)
                val temperature = forecastJson.optDouble("temperature", 20.0).toFloat()
                val feelsLike = forecastJson.optDouble("thermalSensation", temperature.toDouble()).toFloat()
                val humidity = forecastJson.optInt("humidity", 50)
                val windSpeed = forecastJson.optDouble("windSpeed", 10.0).toFloat()
                val uvIndex = forecastJson.optInt("uvIndex", 3)
                val conditionStr = forecastJson.optString("condition", "CLOUDY")
                val condition = WeatherCondition.fromString(conditionStr)
                val description = forecastJson.optString("description", "Vibrant, comfortable day.")

                // Hourly
                val hourlyList = mutableListOf<HourlyForecast>()
                val hourlyArray = forecastJson.optJSONArray("hourlyForecast")
                if (hourlyArray != null) {
                    for (i in 0 until hourlyArray.length()) {
                        val hr = hourlyArray.getJSONObject(i)
                        hourlyList.add(
                            HourlyForecast(
                                time = hr.optString("time", "12:00 PM"),
                                temperature = hr.optDouble("temperature", temperature.toDouble()).toFloat(),
                                condition = WeatherCondition.fromString(hr.optString("condition", "CLOUDY"))
                            )
                        )
                    }
                }

                // Weekly
                val weeklyList = mutableListOf<WeeklyForecast>()
                val weeklyArray = forecastJson.optJSONArray("weeklyForecast")
                if (weeklyArray != null) {
                    for (i in 0 until weeklyArray.length()) {
                        val wk = weeklyArray.getJSONObject(i)
                        weeklyList.add(
                            WeeklyForecast(
                                day = wk.optString("day", "Day"),
                                minTemp = wk.optDouble("minTemp", (temperature - 4).toDouble()).toFloat(),
                                maxTemp = wk.optDouble("maxTemp", (temperature + 4).toDouble()).toFloat(),
                                condition = WeatherCondition.fromString(wk.optString("condition", "CLOUDY")),
                                description = wk.optString("description", "Fair weather")
                            )
                        )
                    }
                }

                // AI insights
                val insightsObj = forecastJson.optJSONObject("aiInsights")
                val insights = if (insightsObj != null) {
                    AiInsights(
                        summary = insightsObj.optString("summary", "A gorgeous day ahead! Enjoy the comfortable atmospheric conditions and fresh breeze."),
                        clothingShort = insightsObj.optString("clothingShort", "Fitted jeans, a breathable cotton shirt, and dynamic sneakers. Bring a light pullover just in case."),
                        activitiesScore = insightsObj.optInt("activitiesScore", 80),
                        activitiesAdvice = insightsObj.optString("activitiesAdvice", "Superb weather for an outdoor walk or cycling around the local neighborhoods."),
                        poeticQuotes = insightsObj.optString("poeticQuotes", "The sky spreads like a fresh canvas of hope.")
                    )
                } else {
                    AiInsights(
                        summary = "A standard, balanced day in the local climate, clear air and temperate feelings.",
                        clothingShort = "Regular comfortable casual attire. Check the skies before leaving.",
                        activitiesScore = 75,
                        activitiesAdvice = "Great for regular daily activities and a swift outdoor stroll.",
                        poeticQuotes = "Quiet clouds glide across the horizon."
                    )
                }

                WeatherData(
                    city = cityName,
                    temperature = temperature,
                    thermalSensation = feelsLike,
                    humidity = humidity,
                    windSpeed = windSpeed,
                    uvIndex = uvIndex,
                    condition = condition,
                    description = description,
                    hourlyForecast = hourlyList,
                    weeklyForecast = weeklyList,
                    aiInsights = insights
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from Gemini API: ${e.message}", e)
            null
        }
    }
}
