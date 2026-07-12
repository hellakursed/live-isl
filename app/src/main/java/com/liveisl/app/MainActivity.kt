package com.liveisl.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.liveisl.app.ui.conversation.ConversationScreen
import com.liveisl.app.ui.theme.LiveIslTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiveIslTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConversationScreen()
                }
            }
        }
    }
}
