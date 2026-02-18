package com.dev.sillycrypt

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.dev.libsillycript.android.AndroidVeracryptMaster
import com.dev.libsillycript.core.blockCiphers.BlockCipherType
import com.dev.libsillycript.core.kdfs.KDFType
import com.dev.sillycrypt.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        AndroidVeracryptMaster(MutableStateFlow(100L)).create(
            context.filesDir.resolve("testNew"),1048576,charArrayOf('a','b','c'), listOf(
                BlockCipherType.AES), KDFType.PBKDF2)
        val thirdVolume = AndroidVeracryptMaster(MutableStateFlow(100L)).openRaw(context.filesDir.resolve("testNew"),charArrayOf('a','b','c'),
            KDFType.PBKDF2, listOf(BlockCipherType.AES))
        val data = context.filesDir.resolve("/data/data/com.dev.libsillycrypt/files/Disk Image of mapper_veracrypt1 (2025-07-14 0110).img").readBytes()
        thirdVolume.write(data)
        thirdVolume.seek(0)
        Log.w("vera","end")
    }
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}