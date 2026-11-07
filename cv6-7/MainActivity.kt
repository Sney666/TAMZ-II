package com.example.sokoban

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import android.util.Log

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SokobanApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

data class SokobanLevel(
    val id: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val grid: List<Int>
)

enum class AppScreen {
    MENU, GAME, EDITOR
}

fun parseSokobanLevels(rawText: String, startId: Int = 1, namePrefix: String = "Level - "): List<SokobanLevel> {
    val levels = mutableListOf<SokobanLevel>()
    val currentLines = mutableListOf<String>()
    var currentName = ""
    var nextId = startId

    fun flushCurrentLevel() {
        if (currentLines.isNotEmpty()) {
            val height = currentLines.size
            val width = currentLines.maxOf { it.length }
            val grid = IntArray(width * height) { 0 }

            for (y in 0 until height) {
                val line = currentLines[y]
                for (x in line.indices) {
                    grid[y * width + x] = when (line[x]) {
                        '#' -> 1 // Zeď
                        '$' -> 2 // Bedna
                        '.' -> 3 // Cíl
                        '@' -> 4 // Hráč
                        '*' -> 5 // Bedna na cíli
                        '+' -> 6 // Hráč na cíli
                        else -> 0 // Prázdno
                    }
                }
            }
            // prefix + číslo (např. Custom - 1)
            val title = currentName.ifBlank { "$namePrefix$nextId" }

            val cleanTitle = title.replace("'", "").trim()

            levels.add(SokobanLevel(nextId, cleanTitle, width, height, grid.toList()))
            nextId++
            currentLines.clear()
            currentName = ""
        }
    }

    for (rawLine in rawText.lines()) {
        val line = rawLine.trimEnd('\r')
        val isMapRow = line.isNotEmpty() && line.contains('#') && line.all { it in "# $@.*+-_" }

        if (isMapRow) {
            currentLines.add(line)
        } else {
            if (currentLines.isNotEmpty()) {
                flushCurrentLevel()
            }
            val trimmed = line.trim().removePrefix(";").trim()
            if (trimmed.isNotEmpty()) {
                currentName = trimmed
            }
        }
    }
    flushCurrentLevel()
    return levels
}

// Načtení ze složky assets + vlastních levelů z editoru
fun loadAllLevels(context: Context): List<SokobanLevel> {
    val textFromAssets = try {
        context.assets.open("levels.txt").bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        ""
    }
    val baseLevels = parseSokobanLevels(textFromAssets, startId = 1, namePrefix = "Level - ")

    val prefs = context.getSharedPreferences("sokoban_prefs", Context.MODE_PRIVATE)
    val customText = prefs.getString("custom_levels", "") ?: ""
    val customLevels = if (customText.isNotBlank()) {
        // Zde řekneme parseru, aby pojmenoval vlastní levely jako "Custom - X"
        parseSokobanLevels(customText, startId = baseLevels.size + 1, namePrefix = "Custom - ")
    } else {
        emptyList()
    }
    return baseLevels + customLevels
}

fun getBestScore(context: Context, levelId: Int): Int? {
    val prefs = context.getSharedPreferences("sokoban_prefs", Context.MODE_PRIVATE)
    val score = prefs.getInt("best_score_$levelId", -1)
    return if (score != -1) score else null
}

fun saveBestScore(context: Context, levelId: Int, moves: Int) {
    val prefs = context.getSharedPreferences("sokoban_prefs", Context.MODE_PRIVATE)
    val currentBest = prefs.getInt("best_score_$levelId", -1)
    if (currentBest == -1 || moves < currentBest) {
        prefs.edit().putInt("best_score_$levelId", moves).apply()
    }
}

fun saveInProgressLevel(context: Context, levelId: Int, grid: List<Int>, moves: Int) {
    val prefs = context.getSharedPreferences("sokoban_prefs", Context.MODE_PRIVATE)
    prefs.edit()
        .putString("saved_grid_$levelId", grid.joinToString(","))
        .putInt("saved_moves_$levelId", moves)
        .apply()
}

fun loadInProgressGrid(context: Context, level: SokobanLevel): List<Int>? {
    val prefs = context.getSharedPreferences("sokoban_prefs", Context.MODE_PRIVATE)
    val savedStr = prefs.getString("saved_grid_${level.id}", null) ?: return null
    val parsed = savedStr.split(",").mapNotNull { it.toIntOrNull() }
    return if (parsed.size == level.width * level.height) parsed else null
}

fun loadInProgressMoves(context: Context, levelId: Int): Int {
    val prefs = context.getSharedPreferences("sokoban_prefs", Context.MODE_PRIVATE)
    return prefs.getInt("saved_moves_$levelId", 0)
}

fun clearInProgressLevel(context: Context, levelId: Int) {
    val prefs = context.getSharedPreferences("sokoban_prefs", Context.MODE_PRIVATE)
    prefs.edit()
        .remove("saved_grid_$levelId")
        .remove("saved_moves_$levelId")
        .apply()
}

fun appendCustomLevel(context: Context, levelAscii: String) {
    val prefs = context.getSharedPreferences("sokoban_prefs", Context.MODE_PRIVATE)
    val existing = prefs.getString("custom_levels", "") ?: ""
    val updated = if (existing.isBlank()) levelAscii else "$existing\n\n$levelAscii"
    prefs.edit().putString("custom_levels", updated).apply()
}

@Composable
fun SokobanApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var levels by remember { mutableStateOf(loadAllLevels(context)) }
    var currentScreen by remember { mutableStateOf(AppScreen.MENU) }
    var selectedLevelIndex by remember { mutableIntStateOf(0) }

    when (currentScreen) {
        AppScreen.MENU -> {
            LevelListScreen(
                levels = levels,
                onSelectLevel = { index ->
                    selectedLevelIndex = index
                    currentScreen = AppScreen.GAME
                },
                onOpenEditor = {
                    currentScreen = AppScreen.EDITOR
                },
                modifier = modifier
            )
        }
        AppScreen.GAME -> {
            val currentLevel = levels[selectedLevelIndex]
            GameScreen(
                level = currentLevel,
                onLevelCompleted = {
                    if (selectedLevelIndex + 1 < levels.size) {
                        selectedLevelIndex++
                        Toast.makeText(context, "Level splněn! Přepínám na další.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Gratulace! Dohrál jsi všechny levely!", Toast.LENGTH_LONG).show()
                        currentScreen = AppScreen.MENU
                    }
                },
                onBackToMenu = {
                    currentScreen = AppScreen.MENU
                },
                modifier = modifier
            )
        }
        AppScreen.EDITOR -> {
            LevelEditorScreen(
                nextLevelNumber = levels.size + 1,
                onLevelSaved = {
                    levels = loadAllLevels(context)
                    currentScreen = AppScreen.MENU
                },
                onCancel = {
                    currentScreen = AppScreen.MENU
                },
                modifier = modifier
            )
        }
    }
}

@Composable
fun LevelListScreen(
    levels: List<SokobanLevel>,
    onSelectLevel: (Int) -> Unit,
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF6200EE))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sokoban",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = onOpenEditor) {
                Text("Editor levelů")
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(levels) { index, level ->
                val bestScore = getBestScore(context, level.id)
                val savedGrid = loadInProgressGrid(context, level)
                val previewGrid = savedGrid ?: level.grid

                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectLevel(index) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .border(1.dp, Color.LightGray)
                        ) {
                            GameLevelCanvas(
                                levelState = previewGrid,
                                levelWidth = level.width,
                                levelHeight = level.height,
                                onMove = null
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = level.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (bestScore != null) "Top score: $bestScore" else "Top score: -",
                                fontSize = 14.sp,
                                color = Color.DarkGray
                            )
                            if (savedGrid != null) {
                                Text(
                                    text = "Rozehráno (${loadInProgressMoves(context, level.id)} tahů)",
                                    fontSize = 12.sp,
                                    color = Color(0xFF386377),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = Color.LightGray)
                }
            }
        }
    }
}

@Composable
fun GameScreen(
    level: SokobanLevel,
    onLevelCompleted: () -> Unit,
    onBackToMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val gameState = remember(level.id) {
        val saved = loadInProgressGrid(context, level) ?: level.grid
        mutableStateListOf(*saved.toTypedArray())
    }
    var moves by remember(level.id) {
        mutableIntStateOf(loadInProgressMoves(context, level.id))
    }
    val bestScore = remember(level.id, moves) { getBestScore(context, level.id) }

    Column(modifier = modifier.fillMaxSize()) {
        // Ovládací panel s tlačítky Reset a Menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = level.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Tahy: $moves | Top: ${bestScore ?: "-"}",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        clearInProgressLevel(context, level.id)
                        gameState.clear()
                        gameState.addAll(level.grid)
                        moves = 0
                    }
                ) {
                    Text("Reset")
                }
                Button(onClick = onBackToMenu) {
                    Text("Menu")
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            GameLevelCanvas(
                levelState = gameState,
                levelWidth = level.width,
                levelHeight = level.height,
                onMove = { dx, dy ->
                    val moved = moveHero(gameState, level.width, level.height, dx, dy)
                    if (moved) {
                        moves++
                        // Kontrola výhry: na mapě už není žádná nezařazená bedna (2) a existuje aspoň jedna bedna v cíli (5)
                        val isWon = gameState.none { it == 2 } && gameState.any { it == 5 }
                        if (isWon) {
                            saveBestScore(context, level.id, moves)
                            clearInProgressLevel(context, level.id)
                            onLevelCompleted()
                        } else {
                            saveInProgressLevel(context, level.id, gameState.toList(), moves)
                        }
                    }
                }
            )
        }
    }
}

fun moveHero(state: SnapshotStateList<Int>, width: Int, height: Int, dx: Int, dy: Int): Boolean {

    Log.d("Sokoban", "moveHero called with dx=$dx, dy=$dy")
    val heroIndex = state.indexOfFirst { it == 4 || it == 6 }
    if (heroIndex == -1) return false

    val hx = heroIndex % width
    val hy = heroIndex / width
    val tx = hx + dx
    val ty = hy + dy

    if (tx !in 0 until width || ty !in 0 until height) return false

    val targetTile = state[ty * width + tx]
    if (targetTile == 1) return false // Zeď

    val tileHeroLeaves = if (state[heroIndex] == 6) 3 else 0

    if (targetTile == 0 || targetTile == 3) {
        state[hy * width + hx] = tileHeroLeaves
        state[ty * width + tx] = if (targetTile == 3) 6 else 4
        return true
    } else if (targetTile == 2 || targetTile == 5) {
        val bx = tx + dx
        val by = ty + dy

        if (bx in 0 until width && by in 0 until height) {
            val beyondTile = state[by * width + bx]
            if (beyondTile == 0 || beyondTile == 3) {
                state[by * width + bx] = if (beyondTile == 3) 5 else 2
                state[ty * width + tx] = if (targetTile == 5) 6 else 4
                state[hy * width + hx] = tileHeroLeaves
                return true
            }
        }
    }
    return false
}

// Univerzální vykreslovací komponenta (funguje pro hru i pro malý náhled v LazyColumn)
@Composable
fun GameLevelCanvas(
    levelState: List<Int>,
    levelWidth: Int,
    levelHeight: Int,
    onMove: ((Int, Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val tiles = arrayOf(
        ImageBitmap.imageResource(id = R.drawable.empty),
        ImageBitmap.imageResource(id = R.drawable.wall),
        ImageBitmap.imageResource(id = R.drawable.box),
        ImageBitmap.imageResource(id = R.drawable.goal),
        ImageBitmap.imageResource(id = R.drawable.hero),
        ImageBitmap.imageResource(id = R.drawable.boxok)
    )

    var dragOffset = Offset.Zero

    val gestureModifier = if (onMove != null) {
        modifier
            .fillMaxSize()
            .pointerInput(levelWidth, levelHeight) {
                detectDragGestures(
                    onDragStart = { dragOffset = Offset.Zero },
                    onDragEnd = {
                        if (dragOffset.getDistance() > 50f) {
                            if (abs(dragOffset.x) > abs(dragOffset.y)) {
                                if (dragOffset.x > 0) onMove(1, 0) else onMove(-1, 0)
                            } else {
                                if (dragOffset.y > 0) onMove(0, 1) else onMove(0, -1)
                            }
                        }
                    }
                ) { change, dragAmount ->
                    change.consume()
                    dragOffset += dragAmount
                }
            }
    } else {
        modifier.fillMaxSize()
    }

    Canvas(modifier = gestureModifier) {
        val tileWidthPx = size.width / levelWidth
        val tileHeightPx = size.height / levelHeight
        val tileSize = minOf(tileWidthPx, tileHeightPx)

        val offsetX = (size.width - tileSize * levelWidth) / 2f
        val offsetY = (size.height - tileSize * levelHeight) / 2f

        for (y in 0 until levelHeight) {
            for (x in 0 until levelWidth) {
                val index = y * levelWidth + x
                val tileIndex = if (index < levelState.size) levelState[index] else 0

                val dstOffset = IntOffset(
                    x = (offsetX + x * tileSize).roundToInt(),
                    y = (offsetY + y * tileSize).roundToInt()
                )
                val dstSize = IntSize(
                    width = tileSize.roundToInt(),
                    height = tileSize.roundToInt()
                )

                if (tileIndex == 6) {
                    drawImage(image = tiles[3], dstOffset = dstOffset, dstSize = dstSize)
                    drawImage(image = tiles[4], dstOffset = dstOffset, dstSize = dstSize)
                } else {
                    val img = tiles[tileIndex.coerceIn(0, 5)]
                    drawImage(image = img, dstOffset = dstOffset, dstSize = dstSize)
                }
            }
        }
    }
}

@Composable
fun LevelEditorScreen(
    nextLevelNumber: Int,
    onLevelSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var editorWidth by remember { mutableIntStateOf(8) }
    var editorHeight by remember { mutableIntStateOf(8) }
    var selectedTool by remember { mutableIntStateOf(1) } // 0=Prázdno, 1=Zeď, 2=Bedna, 3=Cíl, 4=Hráč

    val editorGrid = remember(editorWidth, editorHeight) {
        val list = mutableStateListOf<Int>()
        for (y in 0 until editorHeight) {
            for (x in 0 until editorWidth) {
                val isBorder = x == 0 || y == 0 || x == editorWidth - 1 || y == editorHeight - 1
                list.add(if (isBorder) 1 else 0)
            }
        }
        list
    }

    val toolNames = listOf("Prázdno", "Zeď", "Bedna", "Cíl", "Hráč")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Editor levelu (Level - $nextLevelNumber)", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        // Nastavení velikosti mapy
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Rozměr: ${editorWidth}x${editorHeight}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (editorWidth > 5) { editorWidth--; editorHeight-- } }) {
                    Text("- Zmenšit")
                }
                OutlinedButton(onClick = { if (editorWidth < 12) { editorWidth++; editorHeight++ } }) {
                    Text("+ Zvětšit")
                }
            }
        }

        // Paleta nástrojů
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            for (toolIndex in 0..4) {
                val isSelected = selectedTool == toolIndex
                OutlinedButton(
                    onClick = { selectedTool = toolIndex },
                    modifier = if (isSelected) Modifier.background(Color.LightGray) else Modifier
                ) {
                    Text(toolNames[toolIndex], fontSize = 11.sp)
                }
            }
        }

        // Kreslící plátno editoru
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(2.dp, Color.Black)
                .pointerInput(editorWidth, editorHeight, selectedTool) {
                    detectTapGestures { tapOffset ->
                        val tileW = size.width.toFloat() / editorWidth
                        val tileH = size.height.toFloat() / editorHeight
                        val x = (tapOffset.x / tileW).toInt().coerceIn(0, editorWidth - 1)
                        val y = (tapOffset.y / tileH).toInt().coerceIn(0, editorHeight - 1)

                        // Pokud umisťujeme hráče (4), smažeme předchozí pozici hráče
                        if (selectedTool == 4) {
                            for (i in editorGrid.indices) {
                                if (editorGrid[i] == 4) editorGrid[i] = 0
                            }
                        }
                        editorGrid[y * editorWidth + x] = selectedTool
                    }
                }
        ) {
            GameLevelCanvas(
                levelState = editorGrid,
                levelWidth = editorWidth,
                levelHeight = editorHeight,
                onMove = null
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onCancel) {
                Text("Zrušit")
            }
            Button(
                onClick = {
                    val hasHero = editorGrid.contains(4)
                    val boxCount = editorGrid.count { it == 2 }
                    val goalCount = editorGrid.count { it == 3 }

                    if (!hasHero || boxCount == 0 || boxCount != goalCount) {
                        Toast.makeText(
                            context,
                            "Level musí mít 1 hráče a stejný počet beden a cílů (aspoň 1)!",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }

                    // Převedeme mřížku zpět do textového formátu a uložíme
                    val sb = StringBuilder()
                    for (y in 0 until editorHeight) {
                        for (x in 0 until editorWidth) {
                            val ch = when (editorGrid[y * editorWidth + x]) {
                                1 -> '#'
                                2 -> '$'
                                3 -> '.'
                                4 -> '@'
                                5 -> '*'
                                else -> ' '
                            }
                            sb.append(ch)
                        }
                        sb.append("\n")
                    }
                    appendCustomLevel(context, sb.toString().trimEnd())
                    Toast.makeText(context, "Vlastní level byl uložen!", Toast.LENGTH_SHORT).show()
                    onLevelSaved()
                }
            ) {
                Text("Uložit level")
            }
        }
    }
}