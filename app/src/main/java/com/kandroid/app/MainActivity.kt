package com.kandroid.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.kandroid.app.ui.KandroidApp

class MainActivity : ComponentActivity() {
    private val model: KandroidViewModel by viewModels { KandroidViewModel.Factory(application as KandroidApplication) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openRequestedProject(intent)
        setContent {
            KandroidApp(model)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequestedProject(intent)
    }

    private fun openRequestedProject(intent: Intent?) {
        intent?.takeIf { it.hasExtra(EXTRA_PROJECT_ID) }?.getLongExtra(EXTRA_PROJECT_ID, Long.MIN_VALUE)
            ?.takeIf { it != Long.MIN_VALUE }?.let(model::openProject)
    }

    companion object {
        const val EXTRA_PROJECT_ID = "com.kandroid.app.extra.PROJECT_ID"
    }
}
