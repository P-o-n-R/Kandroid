package com.kandroid.app.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.currentState
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTextEqualTo
import com.kandroid.app.data.ColumnEntity
import com.kandroid.app.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KandroidWidgetTest {
    @Test
    fun snapshotCodec_roundTripsCompleteRenderState() {
        val snapshot = WidgetSnapshot(
            signedIn = true,
            configured = true,
            projectName = "Android Tasks",
            projectId = 42,
            taskCount = 1,
            rows = listOf(WidgetRow.heading("Doing"), WidgetRow.task(7, "Ship widget", "2026-08-15")),
            refreshStatus = WidgetPreferences.FAILED,
            lastSuccess = 1234
        )

        assertEquals(snapshot, WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(snapshot)))
    }

    @Test
    fun signedOutWidget_hidesCachedTaskContent() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(180.dp, 80.dp))
        provideComposable {
            KandroidWidgetContent(WidgetSnapshot(
                signedIn = false,
                configured = true,
                projectName = "Private project",
                projectId = 1,
                taskCount = 1,
                rows = listOf(WidgetRow.task(1, "Secret task", null)),
                refreshStatus = WidgetPreferences.IDLE
            ))
        }

        onNode(hasTextEqualTo("Sign in to show tasks")).assertExists()
        onNode(hasTextEqualTo("Secret task")).assertDoesNotExist()
    }

    @Test
    fun expandedWidget_showsGroupedTasksAndDueDate() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(300.dp, 200.dp))
        provideComposable {
            KandroidWidgetContent(WidgetSnapshot(
                signedIn = true,
                configured = true,
                projectName = "Android Tasks",
                projectId = 1,
                taskCount = 1,
                rows = listOf(WidgetRow.heading("Doing"), WidgetRow.task(1, "Ship widget", "2026-08-15")),
                refreshStatus = WidgetPreferences.IDLE
            ))
        }

        onNode(hasTextEqualTo("Android Tasks")).assertExists()
        onNode(hasTextEqualTo("Doing")).assertExists()
        onNode(hasTextEqualTo("Ship widget")).assertExists()
        onNode(hasTextEqualTo("Due 2026-08-15")).assertExists()
    }

    @Test
    fun configuredSnapshot_isRenderedDirectlyFromGlanceState() = runGlanceAppWidgetUnitTest {
        val configured = WidgetSnapshot(
            signedIn = true,
            configured = true,
            projectName = "Android Tasks",
            projectId = 1,
            taskCount = 1,
            rows = listOf(WidgetRow.heading("Doing"), WidgetRow.task(1, "Appears immediately", null))
        )
        setAppWidgetSize(DpSize(300.dp, 200.dp))
        setState(mutablePreferencesOf(WidgetPreferences.snapshot to WidgetSnapshotCodec.encode(configured)))
        provideComposable {
            val preferences = currentState<Preferences>()
            KandroidWidgetContent(WidgetSnapshotCodec.decode(preferences[WidgetPreferences.snapshot]))
        }

        onNode(hasTextEqualTo("Choose a project")).assertDoesNotExist()
        onNode(hasTextEqualTo("Android Tasks")).assertExists()
        onNode(hasTextEqualTo("Appears immediately")).assertExists()
    }

    @Test
    fun rows_areGroupedByBoardOrder_andEmptyColumnsAreOmitted() {
        val columns = listOf(
            ColumnEntity(id = 20, projectId = 1, title = "Doing", position = 2),
            ColumnEntity(id = 30, projectId = 1, title = "Empty", position = 3),
            ColumnEntity(id = 10, projectId = 1, title = "Backlog", position = 1)
        )
        val tasks = listOf(
            task(id = 2, columnId = 10, title = "Second", position = 2),
            task(id = 3, columnId = 20, title = "In progress", position = 1, dueDate = "2026-08-15"),
            task(id = 1, columnId = 10, title = "First", position = 1)
        )

        assertEquals(
            listOf(
                WidgetRow.heading("Backlog"),
                WidgetRow.task(1, "First", null),
                WidgetRow.task(2, "Second", null),
                WidgetRow.heading("Doing"),
                WidgetRow.task(3, "In progress", "2026-08-15")
            ),
            buildWidgetRows(columns, tasks)
        )
    }

    @Test
    fun rows_ignoreTasksWhoseColumnIsNotAvailable() {
        val rows = buildWidgetRows(
            columns = listOf(ColumnEntity(id = 10, projectId = 1, title = "Backlog", position = 1)),
            tasks = listOf(task(id = 1, columnId = 99, title = "Orphan", position = 1))
        )

        assertEquals(emptyList<WidgetRow>(), rows)
    }

    private fun task(
        id: Long,
        columnId: Long,
        title: String,
        position: Int,
        dueDate: String? = null
    ) = TaskEntity(
        id = id,
        projectId = 1,
        columnId = columnId,
        title = title,
        description = "",
        dueDate = dueDate,
        position = position,
        isActive = true
    )
}
