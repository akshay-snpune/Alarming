package com.alarming

import android.os.Bundle
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.alarming.ui.AlarmingApp
import com.alarming.ui.AlarmingTheme
import com.alarming.ui.PrototypeViewModel
import com.alarming.ui.AppDestination

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = ViewModelProvider(this)[PrototypeViewModel::class.java]
        if (intent.getBooleanExtra("openReminders", false)) model.selectDestination(AppDestination.REMINDERS)
        setContent {
            AlarmingTheme {
            AlarmingApp(model)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("openReminders", false)) {
            ViewModelProvider(this)[PrototypeViewModel::class.java].selectDestination(AppDestination.REMINDERS)
        }
    }
}
