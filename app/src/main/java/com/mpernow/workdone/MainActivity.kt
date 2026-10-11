package com.mpernow.workdone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mpernow.workdone.ui.WorkDoneApp
import com.mpernow.workdone.ui.theme.WorkDoneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorkDoneTheme {
                WorkDoneApp()
            }
        }
    }
}
