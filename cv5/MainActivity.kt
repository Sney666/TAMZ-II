package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(all = 24.dp)
                    )
                }
            }
        }
    }
}

// UPC-A L a R patterny
@Suppress("unused")
private val Lp = intArrayOf(
    0x0D, // 0001101
    0x19, // 0011001
    0x13, // 0010011
    0x3D, // 0111101
    0x23, // 0100011
    0x31, // 0110001
    0x2F, // 0101111
    0x3B, // 0111011
    0x37, // 0110111
    0x0B, // 0001011
)

@Suppress("unused")
private val Rp = intArrayOf(
    0x72, // 1110010
    0x66, // 1100110
    0x6C, // 1101100
    0x42, // 1000010
    0x5C, // 1011100
    0x4E, // 1001110
    0x50, // 1010000
    0x44, // 1000100
    0x48, // 1001000
    0x74, // 1110100
)

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    var zadanyText by remember { mutableStateOf("036000291452") }
    var kodProVykresleni by remember { mutableStateOf("036000291452") }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Generátor UPC-A kódu", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = zadanyText,
            onValueChange = { novyText ->
                if (novyText.length <= 12 && novyText.all { it.isDigit() }) {
                    zadanyText = novyText
                }
            },
            label = { Text("Zadejte 12místný kód (${zadanyText.length}/12)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (zadanyText.length == 12) {
            if (jePlatnyUpcKod(zadanyText)) {
                Text("UPC-A kód je platný", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
            } else {
                Text("Neplatná kontrolní číslice", color = Color.Red, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { kodProVykresleni = zadanyText },
            enabled = zadanyText.length == 12,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generovat čárový kód")
        }

        val context = LocalContext.current

        val options = remember {
            GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_UPC_A)
                .build()
        }
        val scanner = remember { GmsBarcodeScanning.getClient(context, options) }

        Button(
            onClick = {
                scanner.startScan()
                    .addOnSuccessListener { barcode ->
                        val nactenyKod = barcode.rawValue
                        if (nactenyKod != null && nactenyKod.length == 12) {
                            zadanyText = nactenyKod
                            kodProVykresleni = nactenyKod
                        } else {
                            Toast.makeText(context, "Načtený kód není platný UPC-A", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnCanceledListener {}
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Chyba kamery: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Naskenovat kód z kamery")
        }

        Button(
            onClick = {
                val bitmap = vygenerujBarcodeBitmap(kodProVykresleni)
                ulozBitmapDoGalerie(context, bitmap, kodProVykresleni)
            },
            enabled = jePlatnyUpcKod(kodProVykresleni),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Uložit kód do galerie")
        }

        Spacer(modifier = Modifier.height(16.dp))

        BarcodeView(
            code = kodProVykresleni,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        )
    }
}

@Composable
fun BarcodeView(
    code: String,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawRect(
            color = Color.White,
            topLeft = Offset(0f, 0f),
            size = Size(size.width, size.height)
        )

        if (code.length != 12 || !code.all { it.isDigit() }) return@Canvas

        val pocetModuluCelkem = 105
        val sirkaJednotky = size.width / pocetModuluCelkem
        val odsazeniZleva = sirkaJednotky * 5

        val vyskaBezneCary = size.height * 0.75f
        val vyskaDlouheCary = size.height * 0.90f

        var aktualniModul = 0

        fun vykresliModul(jeCara: Boolean, barva: Color = Color.Black, jeDlouha: Boolean = false) {
            if (jeCara) {
                drawRect(
                    color = barva,
                    topLeft = Offset(odsazeniZleva + aktualniModul * sirkaJednotky, 10f),
                    size = Size(
                        width = sirkaJednotky,
                        height = if (jeDlouha) vyskaDlouheCary else vyskaBezneCary
                    )
                )
            }
            aktualniModul++
        }

        vykresliModul(true, jeDlouha = true)
        vykresliModul(false, jeDlouha = true)
        vykresliModul(true, jeDlouha = true)

        for (i in 0 until 6) {
            val cislice = code[i].digitToInt()
            val vzor = Lp[cislice]

            for (bit in 6 downTo 0) {
                val jeCara = ((vzor shr bit) and 1) == 1
                vykresliModul(jeCara)
            }
        }

        vykresliModul(false, Color.Red, jeDlouha = true)
        vykresliModul(true, Color.Red, jeDlouha = true)
        vykresliModul(false, Color.Red, jeDlouha = true)
        vykresliModul(true, Color.Red, jeDlouha = true)
        vykresliModul(false, Color.Red, jeDlouha = true)

        for (i in 6 until 12) {
            val cislice = code[i].digitToInt()
            val vzor = Rp[cislice]

            for (bit in 6 downTo 0) {
                val jeCara = ((vzor shr bit) and 1) == 1
                vykresliModul(jeCara)
            }
        }

        vykresliModul(true, jeDlouha = true)
        vykresliModul(false, jeDlouha = true)
        vykresliModul(true, jeDlouha = true)
    }
}

fun jePlatnyUpcKod(kod: String): Boolean {
    if (kod.length != 12 || !kod.all { it.isDigit() }) return false

    var soucetSudychIndexu = 0
    var soucetLichychIndexu = 0

    for (i in 0 until 11) {
        val cislice = kod[i].digitToInt()

        if (i % 2 == 0) {
            soucetSudychIndexu += cislice
        } else {
            soucetLichychIndexu += cislice
        }
    }

    val celkovySoucet =  soucetSudychIndexu * 3 + soucetLichychIndexu

    val zbytek = celkovySoucet % 10
    val vypocitanaKontrolniCislice = if (zbytek == 0) 0 else (10 - zbytek)

    val zadanaKontrolniCislice = kod[11].digitToInt()

    return vypocitanaKontrolniCislice == zadanaKontrolniCislice
}

fun vygenerujBarcodeBitmap(kod: String): Bitmap {
    val sirkaCelkem = 1050f
    val vyskaCelkem = 300f

    val bitmap = Bitmap.createBitmap(sirkaCelkem.toInt(), vyskaCelkem.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()

    paint.color = android.graphics.Color.WHITE
    canvas.drawRect(0f, 0f, sirkaCelkem, vyskaCelkem, paint)

    if (kod.length != 12 || !kod.all { it.isDigit() }) return bitmap

    val sirkaJednotky = sirkaCelkem / 105f
    val odsazeniZleva = sirkaJednotky * 5
    var aktualniModul = 0

    fun vykresliModulBitmap(jeCara: Boolean, barva: Int = android.graphics.Color.BLACK, jeDlouha: Boolean = false) {
        if (jeCara) {
            paint.color = barva
            val vyskaCary = if (jeDlouha) vyskaCelkem * 0.90f else vyskaCelkem * 0.75f

            val left = odsazeniZleva + aktualniModul * sirkaJednotky
            val top = 20f
            val right = left + sirkaJednotky
            val bottom = top + vyskaCary

            canvas.drawRect(left, top, right, bottom, paint)
        }
        aktualniModul++
    }

    vykresliModulBitmap(true, jeDlouha = true)
    vykresliModulBitmap(false, jeDlouha = true)
    vykresliModulBitmap(true, jeDlouha = true)

    for (i in 0 until 6) {
        val cislice = kod[i].digitToInt()
        val vzor = Lp[cislice]

        for (bit in 6 downTo 0) {
            val jeCara = ((vzor shr bit) and 1) == 1
            vykresliModulBitmap(jeCara)
        }
    }

    vykresliModulBitmap(false, android.graphics.Color.RED, jeDlouha = true)
    vykresliModulBitmap(true, android.graphics.Color.RED, jeDlouha = true)
    vykresliModulBitmap(false, android.graphics.Color.RED, jeDlouha = true)
    vykresliModulBitmap(true, android.graphics.Color.RED, jeDlouha = true)
    vykresliModulBitmap(false, android.graphics.Color.RED, jeDlouha = true)

    for (i in 6 until 12) {
        val cislice = kod[i].digitToInt()
        val vzor = Rp[cislice]

        for (bit in 6 downTo 0) {
            val jeCara = ((vzor shr bit) and 1) == 1
            vykresliModulBitmap(jeCara)
        }
    }

    vykresliModulBitmap(true, jeDlouha = true)
    vykresliModulBitmap(false, jeDlouha = true)
    vykresliModulBitmap(true, jeDlouha = true)

    return bitmap
}

fun ulozBitmapDoGalerie(context: Context, bitmap: Bitmap, nazevKodu: String) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "UPC_$nazevKodu.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Barcodes")
    }

    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

    if (uri != null) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            Toast.makeText(context, "Čárový kód byl uložen do galerie", Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(context, "Chyba při ukládání obrázku", Toast.LENGTH_SHORT).show()
    }
}