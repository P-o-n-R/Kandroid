@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kandroid.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.kandroid.app.KandroidApplication
import com.kandroid.app.MainActivity
import com.kandroid.app.data.ProjectEntity
import com.kandroid.app.ui.KandroidTheme
import kotlinx.coroutines.launch

class WidgetConfigurationActivity : ComponentActivity() {
    private var resumeToken by mutableIntStateOf(0)
    private val appWidgetId: Int
        get() = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_CANCELED, result)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            KandroidTheme {
                val application = application as KandroidApplication
                var projects by remember { mutableStateOf<List<ProjectEntity>?>(null) }
                var selectedId by remember { mutableStateOf<Long?>(null) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(resumeToken) {
                    projects = application.database.dao().activeProjects()
                    val glanceId = GlanceAppWidgetManager(this@WidgetConfigurationActivity).getGlanceIdBy(appWidgetId)
                    selectedId = getAppWidgetState(
                        this@WidgetConfigurationActivity,
                        PreferencesGlanceStateDefinition,
                        glanceId
                    )[WidgetPreferences.projectId]
                }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("Choose a project") }) },
                    bottomBar = {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                        ) {
                            TextButton(onClick = { finish() }) { Text("Cancel") }
                            Button(enabled = selectedId != null, onClick = {
                                val projectId = selectedId ?: return@Button
                                scope.launch { completeConfiguration(projectId) }
                            }) { Text("Add widget") }
                        }
                    }
                ) { padding ->
                    when (val available = projects) {
                        null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        else -> if ((application.credentialStore.load() == null) || available.isEmpty()) {
                            Column(
                                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(if (application.credentialStore.load() == null) "Sign in to Kandroid before adding this widget." else "Open Kandroid to load your projects.")
                                Button(
                                    onClick = { startActivity(Intent(this@WidgetConfigurationActivity, MainActivity::class.java)) },
                                    modifier = Modifier.padding(top = 16.dp)
                                ) { Text("Open Kandroid") }
                            }
                        } else LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
                            items(available, key = { it.id }) { project ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selectedId == project.id, onClick = { selectedId = project.id })
                                    TextButton(onClick = { selectedId = project.id }, modifier = Modifier.weight(1f)) {
                                        Text(project.name, Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeToken++
    }

    private suspend fun completeConfiguration(projectId: Long) {
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        WidgetUpdater.configure(this, glanceId, projectId)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}
