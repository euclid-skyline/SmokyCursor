package com.example.smokycursor

import android.app.WallpaperManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
//import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.smokycursor.ui.theme.SmokyCursorTheme

import android.content.ComponentName
import android.content.Intent




import androidx.compose.foundation.layout.*
import androidx.compose.material3.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmokyCursorTheme {
                WallpaperSetupScreen()
            }
        }
    }
}


//
//class MainActivity : ComponentActivity() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContent {
//            SmokeWallpaperTheme {
//                WallpaperSetupScreen()
//            }
//        }
//    }
//}

@Composable
fun WallpaperSetupScreen() {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = {
                    context.setLiveWallpaper()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3),
                    contentColor = Color.White
                ),
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Set Wallpaper")
            }

            Button(
                onClick = {
                    context.previewWallpaper()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White
                ),
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Preview Wallpaper")
            }
        }
    }
}

//@Composable
//fun SmokeWallpaperTheme(
//    content: @Composable () -> Unit
//) {
//    MaterialTheme(
//        colorScheme = MaterialTheme.colorScheme.copy(
//            background = Color.Black,
//            primary = Color(0xFF2196F3),
//            secondary = Color(0xFF4CAF50)
//        ),
//        content = content
//    )
//}

private fun android.content.Context.setLiveWallpaper() {
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(this@setLiveWallpaper, SmokeWallpaperService::class.java)
        )
    }
    startActivity(intent)
}

private fun android.content.Context.previewWallpaper() {
    val intent = Intent().apply {
        action = WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER
        putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(this@previewWallpaper, SmokeWallpaperService::class.java)
        )
    }
    startActivity(intent)
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SmokyCursorTheme {
        WallpaperSetupScreen()
    }
}