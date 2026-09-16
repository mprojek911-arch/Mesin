package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.autoremix.djslow.logchat.LogChatManager
import com.autoremix.djslow.logchat.LogModule
import com.autoremix.djslow.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    LogChatManager.init(applicationContext)
    LogChatManager.info(LogModule.SYSTEM, "APP_START: Aplikasi AUTO REMIX DJ SLOW diinisialisasi.")
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Scaffold(
          modifier = Modifier.fillMaxSize(),
          contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
          MainScreen(modifier = Modifier.padding(innerPadding))
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    LogChatManager.info(LogModule.SYSTEM, "APP_RESUME: Aplikasi kembali ke foreground.")
  }

  override fun onPause() {
    super.onPause()
    LogChatManager.info(LogModule.SYSTEM, "APP_PAUSE: Aplikasi masuk ke background.")
  }

  override fun onDestroy() {
    LogChatManager.info(LogModule.SYSTEM, "APP_STOP: Aplikasi dihentikan.")
    super.onDestroy()
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  MyApplicationTheme {
    MainScreen()
  }
}

