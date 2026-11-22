package com.example.weatherapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.weatherapp.ui.theme.WeatherAppTheme
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

data class BottomNavItem(
    val route: String,
    val title: String,
    val icon: @Composable () -> Unit
)
fun getWeatherImageUrl(iconCode: String?): String {
    return when (iconCode?.take(2)) {
        "01" -> if (iconCode.endsWith("d")) "https://i.gifer.com/XFbw.gif" // Jasno den
        else "https://i.gifer.com/IrS.gif" // Jasno noc
        "02", "03", "04" -> "https://i.gifer.com/1ZvY.gif" // Mraky
        "09", "10", "11" -> "https://i.gifer.com/FRRX.gif" // Déšť
        "13" -> "https://i.gifer.com/YWuH.gif" // Sníh
        else -> "https://i.gifer.com/fyhz.gif" // Výchozí
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeatherAppTheme {
                val viewModel: WeatherViewModel = viewModel()
                val navController = rememberNavController()

                val navItems = listOf(
                    BottomNavItem("current", "Aktuální", { Icon(Icons.Filled.Home, contentDescription = "Home") }),
                    BottomNavItem("forecast", "Předpověď", { Icon(Icons.Filled.DateRange, contentDescription = "Forecast") }),
                    BottomNavItem("graph", "Graf", { Icon(Icons.Filled.Info, contentDescription = "Graph") }),
                    BottomNavItem("favorites", "Oblíbené", { Icon(Icons.Filled.Favorite, contentDescription = "Favorites") })
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination

                            navItems.forEach { item ->
                                NavigationBarItem(
                                    icon = item.icon,
                                    label = { Text(item.title) },
                                    selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                                    onClick = {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    val weather = viewModel.weatherData.value
                    val iconCode = weather?.weather?.firstOrNull()?.icon
                    val backgroundUrl = getWeatherImageUrl(iconCode)

                    val context = LocalContext.current
                    val optimizedBackgroundRequest = remember(backgroundUrl) {
                        coil.request.ImageRequest.Builder(context)
                            .data(backgroundUrl)
                            .size(600)
                            .crossfade(true)
                            .build()
                    }
                    val imageLoader = remember {
                        ImageLoader.Builder(context)
                            .components {
                                if (Build.VERSION.SDK_INT >= 28) {
                                    add(ImageDecoderDecoder.Factory())
                                } else {
                                    add(GifDecoder.Factory())
                                }
                            }
                            .build()
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AsyncImage(
                            model = optimizedBackgroundRequest,
                            imageLoader = imageLoader,
                            contentDescription = "Pozadí",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f))
                        )

                        NavHost(
                            navController = navController,
                            startDestination = "current"
                        ) {
                            composable("current") { CurrentWeatherScreen(viewModel = viewModel) }
                            composable("forecast") { ForecastScreen(viewModel = viewModel) }
                            composable("graph") { GraphScreen(viewModel = viewModel) }
                            composable("favorites") {
                                FavoritesScreen(
                                    viewModel = viewModel,
                                    onCitySelect = { selectedCity ->
                                        viewModel.currentCity.value = selectedCity
                                        viewModel.forecastData.value = null
                                        viewModel.fetchCurrentWeather(selectedCity, "71adbb2df19e5b3d472b3345c9ae482a")
                                        navController.navigate("current") {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CurrentWeatherScreen(
    modifier: Modifier = Modifier,
    viewModel: WeatherViewModel
) {
    var localCityInput by remember { mutableStateOf(viewModel.currentCity.value) }
    val weather = viewModel.weatherData.value
    val error = viewModel.errorMessage.value
    val apiKey = "71adbb2df19e5b3d472b3345c9ae482a"
    val iconCode = weather?.weather?.firstOrNull()?.icon

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        if (weather != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = weather.name,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))

                val isFavorite = viewModel.favoriteCities.contains(weather.name)
                IconButton(
                    onClick = {
                        if (isFavorite) viewModel.removeFavorite(weather.name)
                        else viewModel.addFavorite(weather.name)
                    }
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Přidat do oblíbených",
                        tint = if (isFavorite) Color.Red else Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            if (iconCode != null) {
                AsyncImage(
                    model = "https://openweathermap.org/img/wn/${iconCode}@4x.png",
                    contentDescription = "Weather Icon",
                    modifier = Modifier.size(200.dp)
                )
            }

            Text(
                text = "${weather.main.temp.toInt()} °C",
                fontSize = 72.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )

            Text(
                text = weather.weather.firstOrNull()?.description?.uppercase() ?: "",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }

        if (error != null) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))) {
                Text(text = error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = localCityInput,
                    onValueChange = { localCityInput = it },
                    label = { Text("Zadejte město", color = Color.White) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.forecastData.value = null
                        viewModel.currentCity.value = localCityInput
                        viewModel.fetchCurrentWeather(localCityInput, apiKey)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Načíst počasí")
                }
            }
        }
    }
}

@Composable
fun ForecastScreen(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier
) {
    val forecast = viewModel.forecastData.value
    val error = viewModel.errorMessage.value
    val apiKey = "71adbb2df19e5b3d472b3345c9ae482a"
    val cityToFetch = viewModel.currentCity.value

    LaunchedEffect(cityToFetch) {
        if (cityToFetch.isNotBlank() && forecast == null) {
            viewModel.fetchForecast(cityToFetch, apiKey)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = if (cityToFetch.isNotBlank()) "Předpověď pro $cityToFetch" else "Zadejte město v záložce Aktuální",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (error != null) {
            Text(text = error, color = MaterialTheme.colorScheme.error)
        } else if (forecast == null && cityToFetch.isNotBlank()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = Color.White)
        } else if (forecast != null) {
            LazyColumn {
                items(forecast.list) { item ->
                    ForecastItemRow(item)
                }
            }
        }
    }
}

@Composable
fun ForecastItemRow(item: ForecastItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = item.dt_txt, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "${item.main.temp} °C", color = Color.White)
                Text(text = item.weather.firstOrNull()?.description ?: "", color = Color.LightGray)
            }

            val iconCode = item.weather.firstOrNull()?.icon
            if (iconCode != null) {
                AsyncImage(
                    model = "https://openweathermap.org/img/wn/${iconCode}@2x.png",
                    contentDescription = "Weather Icon",
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }
}

@Composable
fun GraphScreen(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier
) {
    val forecast = viewModel.forecastData.value
    val error = viewModel.errorMessage.value
    val cityToFetch = viewModel.currentCity.value
    val apiKey = "71adbb2df19e5b3d472b3345c9ae482a"

    LaunchedEffect(cityToFetch) {
        if (cityToFetch.isNotBlank() && forecast == null) {
            viewModel.fetchForecast(cityToFetch, apiKey)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = if (cityToFetch.isNotBlank()) "Přehled pro $cityToFetch" else "Zadejte město v záložce Aktuální",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (error != null) {
            Text(text = error, color = MaterialTheme.colorScheme.error)
        } else if (forecast == null && cityToFetch.isNotBlank()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = Color.White)
        } else if (forecast != null) {
            val forecastItems = forecast.list.take(10)
            val temps = forecastItems.map { it.main.temp.toFloat() }
            val humidities = forecastItems.map { it.main.humidity.toFloat() }
            val times = forecastItems.map { it.dt_txt.substring(11, 16) }

            WeatherLineChart(
                title = "Teplota",
                data = temps,
                times = times,
                lineColor = Color(0xFFFF5722),
                dotColor = Color(0xFFFF5722),
                unit = "°C"
            )

            WeatherLineChart(
                title = "Vlhkost",
                data = humidities,
                times = times,
                lineColor = Color(0xFF2196F3),
                dotColor = Color(0xFF03A9F4),
                unit = "%"
            )
        }
    }
}

@Composable
fun WeatherLineChart(
    title: String,
    data: List<Float>,
    times: List<String>,
    lineColor: Color,
    dotColor: Color,
    unit: String
) {
    if (data.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val maxVal = data.maxOrNull() ?: 0f
    val minVal = data.minOrNull() ?: 0f
    val chartWidthDp = (data.size * 80).dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Canvas(
                    modifier = Modifier
                        .width(chartWidthDp)
                        .height(220.dp)
                ) {
                    val paddingHorizontal = 50f
                    val paddingTop = 60f
                    val paddingBottom = 60f
                    val drawWidth = size.width - (paddingHorizontal * 2)
                    val drawHeight = size.height - paddingTop - paddingBottom
                    val range = (maxVal - minVal).coerceAtLeast(1f)
                    val xStep = drawWidth / (data.size - 1).coerceAtLeast(1)
                    val path = Path()

                    data.forEachIndexed { index, value ->
                        val x = paddingHorizontal + index * xStep
                        val y = paddingTop + drawHeight - ((value - minVal) / range) * drawHeight
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    drawPath(path = path, color = lineColor, style = Stroke(width = 4.dp.toPx()))

                    data.forEachIndexed { index, value ->
                        val x = paddingHorizontal + index * xStep
                        val y = paddingTop + drawHeight - ((value - minVal) / range) * drawHeight

                        drawCircle(color = dotColor, radius = 6.dp.toPx(), center = Offset(x, y))

                        val valueTextLayout = textMeasurer.measure(
                            text = "${value.toInt()}$unit",
                            style = TextStyle(fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                        )
                        drawText(
                            textLayoutResult = valueTextLayout,
                            topLeft = Offset(x - (valueTextLayout.size.width / 2), y - valueTextLayout.size.height - 25f)
                        )

                        val timeTextLayout = textMeasurer.measure(
                            text = times[index],
                            style = TextStyle(fontSize = 13.sp, color = Color.LightGray, fontWeight = FontWeight.Medium)
                        )
                        drawText(
                            textLayoutResult = timeTextLayout,
                            topLeft = Offset(x - (timeTextLayout.size.width / 2), size.height - paddingBottom + 20f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(
    viewModel: WeatherViewModel,
    onCitySelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val favorites = viewModel.favoriteCities
    val favoriteWeathers = viewModel.favoriteWeathers
    val apiKey = "71adbb2df19e5b3d472b3345c9ae482a"

    LaunchedEffect(favorites.size) {
        viewModel.refreshFavoritesWeathers(apiKey)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "Oblíbená města", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        if (favorites.isEmpty()) {
            Text(text = "Zatím tu nemáte žádná města. Přidejte si je pomocí srdíčka na úvodní obrazovce.", color = Color.LightGray)
        } else {
            LazyColumn {
                items(favorites) { city ->
                    val cityWeather = favoriteWeathers[city]

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
                        onClick = { onCitySelect(city) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = city, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                                if (cityWeather != null) {
                                    Text(text = cityWeather.weather.firstOrNull()?.description ?: "", color = Color.LightGray)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (cityWeather != null) {
                                    Text(
                                        text = "${cityWeather.main.temp.toInt()} °C",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = Color.White
                                    )
                                    val iconCode = cityWeather.weather.firstOrNull()?.icon
                                    AsyncImage(
                                        model = "https://openweathermap.org/img/wn/${iconCode}@2x.png",
                                        contentDescription = "Ikona",
                                        modifier = Modifier.size(50.dp)
                                    )
                                } else {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                                }

                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = { viewModel.removeFavorite(city) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Smazat", tint = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}