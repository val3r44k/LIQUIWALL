package com.val3r44k.vallport

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.val3r44k.vallport.ui.DropWallConfigScreen
import com.val3r44k.vallport.ui.theme.VallportTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VallportTheme {
                DropWallConfigScreen()
            }
        }
    }
}
