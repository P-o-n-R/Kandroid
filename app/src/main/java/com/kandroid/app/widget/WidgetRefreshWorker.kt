package com.kandroid.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kandroid.app.KandroidApplication
import com.kandroid.app.network.KanboardApi
import kotlinx.coroutines.CancellationException

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val projectId = inputData.getLong(PROJECT_ID, -1L).takeIf { it > 0 } ?: return Result.failure()
        val app = applicationContext as KandroidApplication
        val credentials = app.credentialStore.load()
        if (credentials == null) {
            WidgetUpdater.setProjectStatus(app, projectId, WidgetPreferences.FAILED)
            return Result.failure()
        }
        return try {
            app.repository.refreshBoard(KanboardApi(credentials), projectId)
            WidgetUpdater.setProjectStatus(app, projectId, WidgetPreferences.IDLE, System.currentTimeMillis())
            Result.success()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            WidgetUpdater.setProjectStatus(app, projectId, WidgetPreferences.FAILED)
            Result.failure()
        }
    }

    companion object {
        const val PROJECT_ID = "project_id"
    }
}
