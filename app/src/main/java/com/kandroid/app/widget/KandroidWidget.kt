package com.kandroid.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kandroid.app.KandroidApplication
import com.kandroid.app.MainActivity
import com.kandroid.app.data.ColumnEntity
import com.kandroid.app.data.TaskEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal object WidgetPreferences {
    val projectId = longPreferencesKey("project_id")
    val snapshot = stringPreferencesKey("snapshot")
    const val IDLE = "idle"
    const val SYNCING = "syncing"
    const val FAILED = "failed"
}

@Serializable
internal data class WidgetSnapshot(
    val signedIn: Boolean = false,
    val configured: Boolean = false,
    val projectName: String? = null,
    val projectId: Long? = null,
    val taskCount: Int = 0,
    val rows: List<WidgetRow> = emptyList(),
    val refreshStatus: String = WidgetPreferences.IDLE,
    val lastSuccess: Long? = null
)

@Serializable
internal data class WidgetRow(
    val type: WidgetRowType,
    val title: String,
    val id: Long? = null,
    val dueDate: String? = null
) {
    companion object {
        fun heading(title: String) = WidgetRow(WidgetRowType.HEADING, title)
        fun task(id: Long, title: String, dueDate: String?) = WidgetRow(WidgetRowType.TASK, title, id, dueDate)
    }
}

@Serializable
internal enum class WidgetRowType { HEADING, TASK }

internal object WidgetSnapshotCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(snapshot: WidgetSnapshot): String = json.encodeToString(snapshot)
    fun decode(value: String?): WidgetSnapshot = value?.let {
        runCatching { json.decodeFromString<WidgetSnapshot>(it) }.getOrNull()
    } ?: WidgetSnapshot()
}

internal fun buildWidgetRows(columns: List<ColumnEntity>, tasks: List<TaskEntity>): List<WidgetRow> = buildList {
    val tasksByColumn = tasks.groupBy { it.columnId }
    columns.sortedWith(compareBy<ColumnEntity> { it.position }.thenBy { it.id }).forEach { column ->
        val columnTasks = tasksByColumn[column.id].orEmpty().sortedWith(compareBy<TaskEntity> { it.position }.thenBy { it.id })
        if (columnTasks.isNotEmpty()) {
            add(WidgetRow.heading(column.title))
            columnTasks.forEach { add(WidgetRow.task(it.id, it.title, it.dueDate)) }
        }
    }
}

class KandroidWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        WidgetUpdater.ensureSnapshot(context, id)

        provideContent {
            val livePreferences = currentState<androidx.datastore.preferences.core.Preferences>()
            KandroidWidgetContent(WidgetSnapshotCodec.decode(livePreferences[WidgetPreferences.snapshot]))
        }
    }
}

@Composable
internal fun KandroidWidgetContent(snapshot: WidgetSnapshot) {
    val size = LocalSize.current
    val compact = size.height < 150.dp || size.width < 220.dp
    val openAction = openAppAction(snapshot.projectId)
    val background = ColorProvider(Color(0xFFF9FAFB))
    val foreground = ColorProvider(Color(0xFF182023))
    val secondary = ColorProvider(Color(0xFF527681))
    val primary = ColorProvider(Color(0xFF176B87))

    Column(
        GlanceModifier.fillMaxSize().appWidgetBackground().background(background).cornerRadius(20.dp).padding(14.dp)
    ) {
        when {
            !snapshot.signedIn -> WidgetMessage("Kandroid", "Sign in to show tasks", openAction, foreground, secondary)
            !snapshot.configured -> WidgetMessage("Kandroid", "Choose a project", openAction, foreground, secondary)
            snapshot.projectName == null -> WidgetMessage("Project unavailable", "Reconfigure this widget", openAction, foreground, secondary)
            else -> {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        snapshot.projectName,
                        GlanceModifier.width(size.width - 64.dp).clickable(openAction),
                        style = TextStyle(color = foreground, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Text(
                        if (snapshot.refreshStatus == WidgetPreferences.SYNCING) "…" else "↻",
                        GlanceModifier.padding(8.dp).clickable(actionRunCallback<RefreshWidgetAction>()),
                        style = TextStyle(color = primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    )
                }
                val status = when (snapshot.refreshStatus) {
                    WidgetPreferences.SYNCING -> "Refreshing cached tasks…"
                    WidgetPreferences.FAILED -> "Refresh failed · showing cached tasks"
                    else -> "${snapshot.taskCount} active ${if (snapshot.taskCount == 1) "task" else "tasks"}"
                }
                Text(status, style = TextStyle(color = secondary, fontSize = 12.sp), maxLines = 1)
                if (!compact) {
                    Spacer(GlanceModifier.height(8.dp))
                    if (snapshot.rows.isEmpty()) {
                        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No active tasks", style = TextStyle(color = secondary, fontSize = 14.sp))
                        }
                    } else {
                        LazyColumn(GlanceModifier.fillMaxSize()) {
                            items(snapshot.rows) { row ->
                                when (row.type) {
                                    WidgetRowType.HEADING -> Text(
                                        row.title,
                                        GlanceModifier.fillMaxWidth().padding(top = 8.dp, bottom = 3.dp),
                                        style = TextStyle(color = primary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                        maxLines = 1
                                    )
                                    WidgetRowType.TASK -> Column(
                                        GlanceModifier.fillMaxWidth().clickable(openAction).padding(vertical = 5.dp)
                                    ) {
                                        Text(row.title, style = TextStyle(color = foreground, fontSize = 14.sp), maxLines = 2)
                                        row.dueDate?.let {
                                            Text("Due $it", style = TextStyle(color = secondary, fontSize = 11.sp), maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetMessage(
    title: String,
    detail: String,
    openAction: Action,
    foreground: ColorProvider,
    secondary: ColorProvider
) {
    Column(
        GlanceModifier.fillMaxSize().clickable(openAction),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = TextStyle(color = foreground, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Spacer(GlanceModifier.height(4.dp))
        Text(detail, style = TextStyle(color = secondary, fontSize = 12.sp), maxLines = 2)
    }
}

private val projectIdParameter = ActionParameters.Key<Long>(MainActivity.EXTRA_PROJECT_ID)

private fun openAppAction(projectId: Long?): Action = if (projectId == null) {
    actionStartActivity<MainActivity>()
} else {
    actionStartActivity<MainActivity>(actionParametersOf(projectIdParameter to projectId))
}

class KandroidWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KandroidWidget()
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        val preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val projectId = preferences[WidgetPreferences.projectId] ?: return
        WidgetUpdater.refreshSnapshot(context, glanceId, status = WidgetPreferences.SYNCING)
        KandroidWidget().update(context, glanceId)
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInputData(workDataOf(WidgetRefreshWorker.PROJECT_ID to projectId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "kandroid-widget-refresh-$projectId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}

internal object WidgetUpdater {
    suspend fun ensureSnapshot(context: Context, glanceId: GlanceId) {
        val preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        if (preferences[WidgetPreferences.snapshot] == null) refreshSnapshot(context, glanceId)
    }

    suspend fun configure(context: Context, glanceId: GlanceId, projectId: Long) {
        writeSnapshot(
            context = context,
            glanceId = glanceId,
            projectId = projectId,
            status = WidgetPreferences.IDLE,
            lastSuccess = null
        )
        KandroidWidget().update(context, glanceId)
    }

    suspend fun refreshSnapshot(context: Context, glanceId: GlanceId, status: String? = null, successAt: Long? = null) {
        val preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val oldSnapshot = WidgetSnapshotCodec.decode(preferences[WidgetPreferences.snapshot])
        writeSnapshot(
            context = context,
            glanceId = glanceId,
            projectId = preferences[WidgetPreferences.projectId],
            status = status ?: oldSnapshot.refreshStatus,
            lastSuccess = successAt ?: oldSnapshot.lastSuccess
        )
    }

    suspend fun updateAll(context: Context) {
        GlanceAppWidgetManager(context).getGlanceIds(KandroidWidget::class.java).forEach { glanceId ->
            refreshSnapshot(context, glanceId)
            KandroidWidget().update(context, glanceId)
        }
    }

    suspend fun setProjectStatus(context: Context, projectId: Long, status: String, successAt: Long? = null) {
        GlanceAppWidgetManager(context).getGlanceIds(KandroidWidget::class.java).forEach { glanceId ->
            val preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
            if (preferences[WidgetPreferences.projectId] == projectId) {
                refreshSnapshot(context, glanceId, status, successAt)
                KandroidWidget().update(context, glanceId)
            }
        }
    }

    private suspend fun writeSnapshot(
        context: Context,
        glanceId: GlanceId,
        projectId: Long?,
        status: String,
        lastSuccess: Long?
    ) {
        val snapshot = buildSnapshot(context, projectId, status, lastSuccess)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) {
            it.toMutablePreferences().apply {
                projectId?.let { id -> this[WidgetPreferences.projectId] = id }
                this[WidgetPreferences.snapshot] = WidgetSnapshotCodec.encode(snapshot)
            }
        }
    }

    private suspend fun buildSnapshot(
        context: Context,
        projectId: Long?,
        status: String,
        lastSuccess: Long?
    ): WidgetSnapshot {
        val app = context.applicationContext as KandroidApplication
        val signedIn = app.credentialStore.load() != null
        if (!signedIn) return WidgetSnapshot(
            signedIn = false,
            configured = projectId != null,
            projectId = projectId,
            refreshStatus = status,
            lastSuccess = lastSuccess
        )
        if (projectId == null) return WidgetSnapshot(
            signedIn = true,
            configured = false,
            refreshStatus = status,
            lastSuccess = lastSuccess
        )
        val project = app.database.dao().project(projectId)?.takeIf { it.isActive }
            ?: return WidgetSnapshot(
                signedIn = true,
                configured = true,
                projectId = projectId,
                refreshStatus = status,
                lastSuccess = lastSuccess
            )
        val columns = app.database.dao().widgetColumns(projectId)
        val tasks = app.database.dao().widgetTasks(projectId)
        return WidgetSnapshot(
            signedIn = true,
            configured = true,
            projectName = project.name,
            projectId = projectId,
            taskCount = tasks.size,
            rows = buildWidgetRows(columns, tasks),
            refreshStatus = status,
            lastSuccess = lastSuccess
        )
    }
}
