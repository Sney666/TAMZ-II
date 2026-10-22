package com.example.cv3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aay.compose.barChart.BarChart
import com.aay.compose.barChart.model.BarParameters
import com.aay.compose.donutChart.DonutChart
import com.aay.compose.donutChart.model.PieChartData
import com.example.cv3.ui.theme.Cv3Theme
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                var aktualniObrazovka by remember { mutableStateOf("kalkulacka") }
                var menuRozbaleno by remember { mutableStateOf(false) }
                var vybranyTypGrafu by remember { mutableStateOf("sloupcovy") }
                var maxVkladNastaveni by remember { mutableFloatStateOf(100000f) }
                var maxMesicniVkladNastaveni by remember { mutableFloatStateOf(10000f) }
                var maxUrokNastaveni by remember { mutableFloatStateOf(20f) }
                var maxObdobiNastaveni by remember { mutableFloatStateOf(30f) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text("Spořící kalkulačka") },
                            actions = {
                                Box {
                                    IconButton(onClick = { menuRozbaleno = true }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Menu"
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuRozbaleno,
                                        onDismissRequest = { menuRozbaleno = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Kalkulačka") },
                                            onClick = {
                                                aktualniObrazovka = "kalkulacka"
                                                menuRozbaleno = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Chart-Type") },
                                            onClick = {
                                                aktualniObrazovka = "grafy"
                                                menuRozbaleno = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Settings") },
                                            onClick = {
                                                aktualniObrazovka = "nastaveni"
                                                menuRozbaleno = false
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    when (aktualniObrazovka) {
                        "kalkulacka" -> KalkulackaScreen(
                            modifier = Modifier.padding(innerPadding),
                            typGrafu = vybranyTypGrafu,
                            maxVklad = maxVkladNastaveni,
                            maxMesicniVklad = maxMesicniVkladNastaveni,
                            maxUrok = maxUrokNastaveni,
                            maxObdobi = maxObdobiNastaveni
                        )
                        "nastaveni" -> NastaveniScreen(
                            modifier = Modifier.padding(innerPadding),
                            aktualniMaxVklad = maxVkladNastaveni,
                            aktualniMaxMesicniVklad = maxMesicniVkladNastaveni,
                            aktualniMaxUrok = maxUrokNastaveni,
                            aktualniMaxObdobi = maxObdobiNastaveni,
                            onMaxVkladZmena = { novaHodnota -> maxVkladNastaveni = novaHodnota },
                            onMaxMesicniVkladZmena = { novaHodnota -> maxMesicniVkladNastaveni = novaHodnota },
                            onMaxUrokZmena = { novaHodnota -> maxUrokNastaveni = novaHodnota },
                            onMaxObdobiZmena = { novaHodnota -> maxObdobiNastaveni = novaHodnota }
                        )
                        "grafy" -> GrafyScreen(
                            modifier = Modifier.padding(innerPadding),
                            onGrafVybran = { vybranyGraf ->
                                vybranyTypGrafu = vybranyGraf
                                aktualniObrazovka = "kalkulacka"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KalkulackaScreen(modifier: Modifier = Modifier, typGrafu: String, maxVklad: Float, maxMesicniVklad: Float, maxUrok: Float, maxObdobi: Float) {
    var vklad by remember { mutableFloatStateOf(10000f) }
    var mesicne by remember { mutableFloatStateOf(1000f) }
    var urok by remember { mutableFloatStateOf(5f) }
    var obdobi by remember { mutableFloatStateOf(10f) }
    var zapocitatDan by remember { mutableStateOf(false) }

    val mesicniUrok = (urok / 100) / 12
    val pocetMesicu = obdobi * 12

    val fvVkladu = vklad * (1 + mesicniUrok).pow(pocetMesicu)

    val fvMesicne = if (mesicniUrok > 0) {
        mesicne * (((1 + mesicniUrok).pow(pocetMesicu) - 1) / mesicniUrok)
    } else {
        mesicne * pocetMesicu
    }

    val celkemVlozeno = vklad + (mesicne * pocetMesicu)
    var hrubeUroky = (fvVkladu + fvMesicne) - celkemVlozeno

    if (zapocitatDan) {
        hrubeUroky *= 0.85f
    }

    val nasporeno = celkemVlozeno + hrubeUroky
    val celkoveUroky = hrubeUroky

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFB0A796))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCA816))){
            Column(modifier = Modifier.padding(10.dp)) {
                Text(text = "Naspořená suma: ${nasporeno.toInt()}", color = Color.Black, fontSize = 20.sp)
                Text(text = "Z toho úroky: ${celkoveUroky.toInt()}", color = Color.Black, fontSize = 20.sp)
            }
        }
        Card(modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCA816))){
            Column(modifier = Modifier.padding(10.dp)) {
                if(typGrafu == "sloupcovy"){
                    DynamickyGraf(vklad, celkoveUroky)
                } else {
                    DynamickyGraf2(vklad, celkoveUroky)
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCA816))){
            Column(modifier = Modifier.padding(10.dp)) {
                Text(text = "Vklad: ${vklad.toInt()}", color = Color(0xFF3E362E))
                Slider(
                    value = vklad,
                    onValueChange = { vklad = it },
                    valueRange = 0f..maxVklad,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF3E362E),
                        activeTrackColor = Color(0xFF95483F),
                        inactiveTrackColor = Color.LightGray
                    )
                )
                Text(text = "Mesicne: ${mesicne.toInt()}", color = Color(0xFF3E362E))
                Slider(
                    value = mesicne,
                    onValueChange = { mesicne = it },
                    valueRange = 0f..maxMesicniVklad,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF3E362E),
                        activeTrackColor = Color(0xFF95483F),
                        inactiveTrackColor = Color.LightGray
                    )
                )
                Text(text = "Urok: $urok", color = Color(0xFF3E362E))
                Slider(
                    value = urok,
                    onValueChange = { urok = it },
                    valueRange = 0f..maxUrok,
                    steps = 39,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF3E362E),
                        activeTrackColor = Color(0xFF566C73),
                        inactiveTrackColor = Color.LightGray
                    )
                )
                Text(text = "Období: ${obdobi.toInt()}", color = Color(0xFF3E362E))
                Slider(
                    value = obdobi,
                    onValueChange = { obdobi = it },
                    //valueRange = 0f..maxOf(30f, maxObdobi),
                    valueRange = 0f..maxObdobi,
                    steps = 28,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF3E362E),
                        activeTrackColor = Color(0xFF566C73),
                        inactiveTrackColor = Color.LightGray
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Odečíst daň z úroků (15 %)", color = Color(0xFF3E362E))
                    Switch(
                        checked = zapocitatDan,
                        onCheckedChange = { zapocitatDan = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFB0A796),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.LightGray
                    )
                    )
                }
            }
        }
    }
}

@Composable
fun NastaveniScreen(
    modifier: Modifier = Modifier,
    aktualniMaxVklad: Float,
    aktualniMaxMesicniVklad: Float,
    aktualniMaxUrok: Float,
    aktualniMaxObdobi: Float,
    onMaxVkladZmena: (Float) -> Unit,
    onMaxMesicniVkladZmena: (Float) -> Unit,
    onMaxUrokZmena: (Float) -> Unit,
    onMaxObdobiZmena: (Float) -> Unit
) {
    var vkladText by remember { mutableStateOf(aktualniMaxVklad.toInt().toString()) }
    var mesicniVkladText by remember { mutableStateOf(aktualniMaxMesicniVklad.toInt().toString()) }
    var urokText by remember { mutableStateOf(aktualniMaxUrok.toInt().toString()) }
    var obdobiText by remember { mutableStateOf(aktualniMaxObdobi.toInt().toString()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFB0A796))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF95483F))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Maximální rozsah pro vklad", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFDCA816))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = vkladText,
                    onValueChange = { novyText ->
                        if (novyText.all { it.isDigit() }) {
                            vkladText = novyText
                            onMaxVkladZmena(novyText.toFloatOrNull() ?: 1000000f)
                        }
                    },
                    label = { Text("Limit vkladu (Kč)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF95483F))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Maximální rozsah pro měsíční vklad", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFDCA816))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = mesicniVkladText,
                    onValueChange = { novyText ->
                        if (novyText.all { it.isDigit() }) {
                            mesicniVkladText = novyText
                            onMaxMesicniVkladZmena(novyText.toFloatOrNull() ?: 10000f)
                        }
                    },
                    label = { Text("Limit měsíčně (Kč)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF566C73))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Maximální rozsah pro úrok", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFDCA816))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = urokText,
                    onValueChange = { novyText ->
                        if (novyText.all { it.isDigit() }) {
                            urokText = novyText
                            onMaxUrokZmena(novyText.toFloatOrNull() ?: 20f)
                        }
                    },
                    label = { Text("Limit úroku (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF566C73))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Maximální rozsah pro období", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFDCA816))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = obdobiText,
                    onValueChange = { novyText ->
                        if (novyText.all { it.isDigit() }) {
                            obdobiText = novyText
                            onMaxObdobiZmena(novyText.toFloatOrNull() ?: 30f)
                        }
                    },
                    label = { Text("Limit období (Roky)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun GrafyScreen(modifier: Modifier = Modifier, onGrafVybran: (String) -> Unit) {
    Column(
        modifier = modifier.fillMaxSize()
            .background(Color(0xFFB0A796))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onGrafVybran("sloupcovy") },
            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCA816))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Sloupcový graf", fontWeight = FontWeight.Bold, color = Color.Black)
                DynamickyGraf(vklad = 50000f, uroky = 16911f)
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onGrafVybran("kolacovy") },
            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCA816))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Koláčový graf", fontWeight = FontWeight.Bold, color = Color.Black)
                DynamickyGraf2(vklad = 50000f, uroky = 16911f)
            }
        }
    }
}

@Composable
fun DynamickyGraf(vklad: Float, uroky: Float) {
    val barParameters = listOf(
        BarParameters(
            dataName = "Vklad",
            data = listOf(vklad.toDouble()),
            barColor = Color(0xFF95483F)
        ),
        BarParameters(
            dataName = "Úroky",
            data = listOf(uroky.toDouble()),
            barColor = Color(0xFF566C73)
        )
    )

    Box(Modifier.fillMaxWidth().height(300.dp).padding(16.dp)) {
        BarChart(
            chartParameters = barParameters,
            gridColor = Color.LightGray,
            xAxisData = listOf("Souhrn"),
            isShowGrid = true,
            animateChart = true,
            showGridWithSpacer = true,
            yAxisStyle = TextStyle(fontSize = 12.sp, color = Color.DarkGray),
            xAxisStyle = TextStyle(fontSize = 12.sp, color = Color.DarkGray),
            yAxisRange = 6,
            barWidth = 60.dp
        )
    }
}

@Composable
fun DynamickyGraf2(vklad: Float, uroky: Float) {
    val pieChartData = listOf(
        PieChartData(
            partName = "Vklad",
            data = vklad.toDouble(),
            color = Color(0xFF95483F)
        ),
        PieChartData(
            partName = "Úroky",
            data = uroky.toDouble(),
            color = Color(0xFF566C73),
        )
    )

    Box(Modifier.fillMaxWidth().height(300.dp).padding(16.dp)) {
        DonutChart(
            modifier = Modifier.fillMaxSize(),
            pieChartData = pieChartData,
            centerTitle = "Podíl",
            centerTitleStyle = TextStyle(color = Color.Black, fontSize = 16.sp),
            outerCircularColor = Color.LightGray,
            innerCircularColor = Color.Transparent,
            ratioLineColor = Color.Black,
            descriptionStyle = TextStyle(color = Color.Black, fontSize = 18.sp)
        )
    }
}