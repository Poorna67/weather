package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AiInsights
import com.example.data.model.HourlyForecast
import com.example.data.model.WeatherCondition
import com.example.data.model.WeatherData
import com.example.data.model.WeeklyForecast
import com.example.ui.viewmodel.WeatherUiState
import com.example.ui.viewmodel.WeatherViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// premium structural bento design constants
val BentoBackground = Color(0xFFF2F0F4)
val BentoTextPrimary = Color(0xFF1B1B1F)
val BentoTextSecondary = Color(0xFF44474E)

val BentoWeeklyBg = Color(0xFFDDE1FF)
val BentoWeeklyText = Color(0xFF001452)

val BentoUvBg = Color(0xFFFFE088)
val BentoUvText = Color(0xFF5F4D12)

val BentoWindBg = Color(0xFFE1E2EC)
val BentoWindText = Color(0xFF1B1B1F)

val BentoHumidityBg = Color(0xFFD9E3D8)
val BentoHumidityAccent = Color(0xFF386B3B)

val BentoAiBg = Color(0xFFF6E3FF)
val BentoAiText = Color(0xFF4A154B)

val BentoChipBg = Color(0xFFE3E1E9)

@Composable
fun WeatherDashboard(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val favoritedCities by viewModel.favoriteCities.collectAsStateWithLifecycle()
    val isFavorited by viewModel.isCurrentCityFavorited.collectAsStateWithLifecycle()
    
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val currentCondition = when (val state = uiState) {
        is WeatherUiState.Success -> state.weather.condition
        else -> WeatherCondition.CLOUDY
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BentoBackground,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("dashboard_scroll_container")
            ) {
                // Header location card with Bento theme
                BentoHeaderBar(
                    weatherData = (uiState as? WeatherUiState.Success)?.weather,
                    onRefreshClick = {
                        val active = viewModel.activeCity.value
                        viewModel.fetchWeather(active, forceRefresh = true)
                    }
                )

                // Search and quick action chips
                SearchAndFavoritesBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    onSearchSubmit = {
                        if (searchQuery.isNotBlank()) {
                            viewModel.fetchWeather(searchQuery)
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    },
                    popularCities = listOf("Tokyo", "Paris", "London", "New York", "Reykjavik", "Sydney"),
                    favoritedCities = favoritedCities,
                    onCityClick = { city ->
                        viewModel.fetchWeather(city)
                        viewModel.updateSearchQuery("")
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                )

                // Bento grid modules
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (val state = uiState) {
                        is WeatherUiState.Loading -> {
                            LoadingAnimation(message = "Synchronizing Bento units...")
                        }
                        is WeatherUiState.Error -> {
                            ErrorDisplay(
                                errorMessage = state.message,
                                onRetryClick = { viewModel.fetchWeather(viewModel.activeCity.value) }
                            )
                        }
                        is WeatherUiState.Success -> {
                            BentoGridContent(
                                weather = state.weather,
                                isFavorited = isFavorited,
                                isSimulated = state.isFallbackOrCached,
                                onFavoriteToggle = { viewModel.toggleFavorite() },
                                isApiKeyPresent = viewModel.isApiKeyPresent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BentoHeaderBar(
    weatherData: WeatherData?,
    onRefreshClick: () -> Unit
) {
    val dateStr = SimpleDateFormat("EEE, d MMMM", Locale.getDefault()).format(Date())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Launcher Icon Pill
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(BentoChipBg)
                .clickable { onRefreshClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Sync",
                tint = BentoTextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Center Location Details
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = "Location Pin",
                    tint = BentoTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = weatherData?.city ?: "Aura Weather",
                    color = BentoTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = dateStr.uppercase(),
                color = BentoTextSecondary.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // Dummy Settings indicator icon pill
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(BentoChipBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Widgets,
                contentDescription = "Bento Widgets Dashboard",
                tint = BentoTextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchAndFavoritesBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    popularCities: List<String>,
    favoritedCities: List<String>,
    onCityClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Flat Elegant Bento TextField
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search city atmospheric forecast...", color = BentoTextSecondary.copy(alpha = 0.5f)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("city_search_input"),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search icon",
                    tint = BentoTextSecondary
                )
            },
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear text",
                                tint = BentoTextSecondary
                            )
                        }
                    }
                    IconButton(
                        onClick = onSearchSubmit,
                        modifier = Modifier.testTag("search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowForward,
                            contentDescription = "Submit search",
                            tint = BentoWeeklyText
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = BentoTextPrimary,
                unfocusedTextColor = BentoTextPrimary,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = BentoChipBg.copy(alpha = 0.5f),
                focusedBorderColor = BentoWeeklyText,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = BentoTextPrimary
            ),
            shape = RoundedCornerShape(18.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Bento quick navigation pills
        val quickPills = (favoritedCities + popularCities).distinct().take(6)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickPills.forEach { city ->
                val isSaved = favoritedCities.contains(city)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSaved) BentoWeeklyBg else BentoChipBg
                        )
                        .clickable { onCityClick(city) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("popular_city_tag_$city"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSaved) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = "Favorited",
                            tint = BentoWeeklyText,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = city,
                        color = if (isSaved) BentoWeeklyText else BentoTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun BentoGridContent(
    weather: WeatherData,
    isFavorited: Boolean,
    isSimulated: Boolean,
    onFavoriteToggle: () -> Unit,
    isApiKeyPresent: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Sleek minimal hero status
        item {
            BentoHeroCard(
                weather = weather,
                isFavorited = isFavorited,
                onFavoriteToggle = onFavoriteToggle
            )
        }

        // 2. Bento Core Row: Weekly Forecast (Tall Card, col-span-1) & UV Index / Wind Speed column (col-span-1)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Column - Tall Weekly Forecast Card
                BentoWeeklyCard(
                    weeklyForecast = weather.weeklyForecast,
                    modifier = Modifier
                        .weight(1f)
                        .height(290.dp)
                )

                // Right Column - Stacked UV index & Wind Speed
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(290.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BentoUvCard(
                        uvIndex = weather.uvIndex,
                        modifier = Modifier.weight(1f)
                    )
                    BentoWindCard(
                        windSpeed = weather.windSpeed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Wide Bento Module - Humidity
        item {
            BentoHumidityCard(humidity = weather.humidity)
        }

        // 4. Wide Bento Module - AI Aura Insights Guide
        item {
            BentoAiInsightsCard(insights = weather.aiInsights)
        }

        // 5. Sleek Horizontally Scrolling Hourly snapshot scroller
        item {
            BentoHourlyScroller(hourlyForecast = weather.hourlyForecast)
        }

        // 6. Simulation banner alert
        if (isSimulated || !isApiKeyPresent) {
            item {
                BentoSimulationBanner(isKeyPresent = isApiKeyPresent)
            }
        }
    }
}

@Composable
fun BentoHeroCard(
    weather: WeatherData,
    isFavorited: Boolean,
    onFavoriteToggle: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Active weather title pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(BentoChipBg.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = getConditionIcon(weather.condition),
                        contentDescription = null,
                        tint = BentoTextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = weather.condition.name,
                        color = BentoTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Add to favorites toggle star
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .size(36.dp)
                        .background(BentoChipBg.copy(alpha = 0.5f), CircleShape)
                        .testTag("favorite_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isFavorited) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (isFavorited) BentoWeeklyText else BentoTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Temperature with huge modern thin/light layout
            Text(
                text = "${weather.temperature.toInt()}°",
                color = BentoTextPrimary,
                fontSize = 72.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-2).sp
            )

            Text(
                text = weather.description,
                color = BentoTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // High & Low specs
            val minTemp = weather.weeklyForecast.firstOrNull()?.minTemp ?: (weather.temperature - 4)
            val maxTemp = weather.weeklyForecast.firstOrNull()?.maxTemp ?: (weather.temperature + 4)
            Text(
                text = "H: ${maxTemp.toInt()}°  •  L: ${minTemp.toInt()}°  •  Feels feels like ${weather.thermalSensation.toInt()}°",
                color = BentoTextSecondary.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BentoWeeklyCard(
    weeklyForecast: List<WeeklyForecast>,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = BentoWeeklyBg
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WEEKLY",
                    color = BentoWeeklyText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = BentoWeeklyText,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Days checklist (show top 4 days dynamically due to layout bounds)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                weeklyForecast.take(4).forEach { dayForecast ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = dayForecast.day,
                            color = BentoWeeklyText.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(36.dp)
                        )
                        Icon(
                            imageVector = getConditionIcon(dayForecast.condition),
                            contentDescription = dayForecast.condition.name,
                            tint = BentoWeeklyText,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${dayForecast.maxTemp.toInt()}°",
                            color = BentoWeeklyText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(28.dp)
                        )
                    }
                }
            }

            // Custom "VIEW ALL" outlook status pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.4f))
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AURA OUTLOOK",
                    color = BentoWeeklyText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun BentoUvCard(
    uvIndex: Int,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = BentoUvBg
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UV INDEX",
                    color = BentoUvText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Icon(
                    imageVector = Icons.Rounded.WbTwilight,
                    contentDescription = null,
                    tint = BentoUvText,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column {
                Text(
                    text = "$uvIndex",
                    color = BentoUvText,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = getUvAdvice(uvIndex),
                    color = BentoUvText.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun getUvAdvice(index: Int): String {
    return when {
        index <= 2 -> "Low hazard. Safe outer sync."
        index <= 5 -> "Moderate hazard. Sunscreen recommended."
        index <= 7 -> "High hazard. Seek cool shadows."
        else -> "Extreme risk. Shield sensitive skin."
    }
}

@Composable
fun BentoWindCard(
    windSpeed: Float,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = BentoWindBg
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WIND",
                    color = BentoWindText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Icon(
                    imageVector = Icons.Rounded.Air,
                    contentDescription = null,
                    tint = BentoWindText,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column {
                Text(
                    text = "${windSpeed.toInt()} km/h",
                    color = BentoWindText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Atmospheric flow guidance active.",
                    color = BentoWindText.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun BentoHumidityCard(
    humidity: Int
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = BentoHumidityBg
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "HUMIDITY STATUS",
                    color = BentoHumidityAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "$humidity%",
                    color = BentoTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Bento linear slider indicator representation of humidity
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.05f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(humidity / 100f)
                        .background(BentoHumidityAccent)
                )
            }

            Icon(
                imageVector = Icons.Rounded.WaterDrop,
                contentDescription = null,
                tint = BentoHumidityAccent,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun BentoAiInsightsCard(
    insights: AiInsights
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = BentoAiBg
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header with spark logo & vibe badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = BentoAiText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AURA AI LIFESTYLE SYNC",
                        color = BentoAiText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // Vibe gauge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BentoAiText.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${insights.activitiesScore}/100 VIBE",
                        color = BentoAiText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Serif Quote Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.4f))
                    .padding(12.dp)
            ) {
                Text(
                    text = insights.poeticQuotes,
                    color = BentoAiText,
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Guidance texts
            Text(
                text = "Atmospheric Aura:",
                color = BentoAiText.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = insights.summary,
                color = BentoTextPrimary,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Apparel curation
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Rounded.Checkroom,
                    contentDescription = null,
                    tint = BentoAiText,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Apparel Curator:",
                        color = BentoAiText.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = insights.clothingShort,
                        color = BentoTextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Outdoor guidelines
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Rounded.DirectionsRun,
                    contentDescription = null,
                    tint = BentoAiText,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Suggested Activities:",
                        color = BentoAiText.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = insights.activitiesAdvice,
                        color = BentoTextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BentoHourlyScroller(
    hourlyForecast: List<HourlyForecast>
) {
    if (hourlyForecast.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "HOURLY SNAPSHOT",
            color = BentoTextSecondary.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            hourlyForecast.forEach { slice ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.width(76.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = slice.time.replace(" AM", "a").replace(" PM", "p"),
                            color = BentoTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BentoChipBg.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getConditionIcon(slice.condition),
                                contentDescription = slice.condition.name,
                                tint = BentoTextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${slice.temperature.toInt()}°",
                            color = BentoTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BentoSimulationBanner(
    isKeyPresent: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1B1B1F)
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isKeyPresent) "AURA WEATHER ARCHIVE ACTIVE" else "INTELLIGENT SIMULATION ACTIVE",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isKeyPresent) {
                    "Displaying local weather cache of favorited parameters. Fresh parameter updates synchronize transparently when online connectivity resolves."
                } else {
                    "Configure your direct GEMINI_API_KEY inside the Secrets panel of AI Studio to fetch live physical realworld coordinates and fully power real-time AI forecasts."
                },
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun LoadingAnimation(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = BentoWeeklyText,
            strokeWidth = 3.dp,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = message,
            color = BentoTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ErrorDisplay(
    errorMessage: String,
    onRetryClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .background(BentoChipBg, CircleShape)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = BentoTextPrimary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Atmospheric Node Disruption",
            color = BentoTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = errorMessage,
            color = BentoTextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRetryClick,
            colors = ButtonDefaults.buttonColors(containerColor = BentoWeeklyBg)
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                tint = BentoWeeklyText,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Re-establish sync", color = BentoWeeklyText, fontWeight = FontWeight.Bold)
        }
    }
}

fun getConditionIcon(condition: WeatherCondition): ImageVector {
    return when (condition) {
        WeatherCondition.SUNNY -> Icons.Rounded.WbSunny
        WeatherCondition.CLOUDY -> Icons.Rounded.Cloud
        WeatherCondition.RAINY -> Icons.Rounded.Umbrella
        WeatherCondition.STORMY -> Icons.Rounded.Bolt
        WeatherCondition.SNOWY -> Icons.Rounded.AcUnit
        WeatherCondition.FOGGY -> Icons.Rounded.Grain
    }
}
