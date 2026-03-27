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
import com.dev.libsillycript.core.VeracryptData
import com.dev.libsillycript.core.VeracryptMode
import com.dev.libsillycript.core.VeracryptOpeningData
import com.dev.libsillycript.core.blockCiphers.BlockCipherType
import com.dev.libsillycript.core.fs.FsType
import com.dev.libsillycript.core.kdfs.KDFType
import com.dev.sillycrypt.theme.MyApplicationTheme
import com.sillycrypt.exfat_android.provider.ExFatDocumentsProvider
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
            context.filesDir.resolve("testNew"),
            listOf(
                VeracryptData(
                    size = 1048576 * 16,
                    VeracryptOpeningData(
                        charArrayOf('a','b','c'),
                        KDFType.PBKDF2,
                        listOf(
                            BlockCipherType.AES)
                    ),
                    FsType.ExFAT,
                    0
                )
            )
        )
        val thirdVolume = AndroidVeracryptMaster(MutableStateFlow(100L)).open(
            context.filesDir.resolve("test4"),
            VeracryptMode.OpenNormal(VeracryptOpeningData(charArrayOf('a','b','c'),
            KDFType.PBKDF2,
            listOf(BlockCipherType.AES))),
            FsType.ExFAT,
            )
        ExFatDocumentsProvider.notifyRootsChanged(context, "${context.packageName}.documents")
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