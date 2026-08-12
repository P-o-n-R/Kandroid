package com.kandroid.app.data

import com.kandroid.app.network.KanboardApi
import kotlinx.coroutines.flow.Flow

class KanboardRepository(private val dao: KandroidDao) {
    fun projects(): Flow<List<ProjectEntity>> = dao.observeProjects()
    fun columns(projectId: Long): Flow<List<ColumnEntity>> = dao.observeColumns(projectId)
    fun tasks(projectId: Long, active: Boolean): Flow<List<TaskEntity>> = dao.observeTasks(projectId, active)

    suspend fun refreshProjects(api: KanboardApi) {
        dao.upsertProjects(api.projects().map { it.entity() })
    }

    suspend fun createProject(api: KanboardApi, name: String): Long {
        val id = api.createProject(name)
        refreshProjects(api)
        return id
    }

    suspend fun createLocalProject(name: String): Long {
        val id = nextNegative(dao.minimumProjectId())
        val firstColumn = nextNegative(dao.minimumColumnId())
        dao.upsertProject(ProjectEntity(id, name.trim()))
        dao.upsertColumns(LOCAL_COLUMNS.mapIndexed { index, title ->
            ColumnEntity(firstColumn - index, id, title, index + 1)
        })
        return id
    }

    suspend fun archiveLocalProject(projectId: Long) {
        dao.project(projectId)?.let { dao.upsertProject(it.copy(isActive = false)) }
    }

    suspend fun archiveProject(api: KanboardApi, projectId: Long) {
        val old = dao.project(projectId) ?: return
        dao.upsertProject(old.copy(isActive = false))
        try {
            check(api.archiveProject(projectId)) { "Project archiving was rejected." }
        } catch (error: Throwable) {
            dao.upsertProject(old)
            throw error
        }
        refreshProjects(api)
    }

    suspend fun refreshBoard(api: KanboardApi, projectId: Long) {
        val columns = api.columns(projectId).map { it.entity(projectId) }
        val active = api.tasks(projectId, true).map { it.entity() }
        dao.replaceColumns(projectId, columns)
        dao.replaceTasks(projectId, true, active)
    }

    suspend fun refreshClosed(api: KanboardApi, projectId: Long) {
        dao.replaceTasks(projectId, false, api.tasks(projectId, false).map { it.entity() })
    }

    suspend fun create(api: KanboardApi, projectId: Long, columnId: Long, draft: TaskDraft) {
        api.createTask(projectId, columnId, draft)
        refreshBoard(api, projectId)
    }

    suspend fun createLocal(projectId: Long, columnId: Long, draft: TaskDraft) {
        val position = dao.widgetTasks(projectId).count { it.columnId == columnId } + 1
        dao.upsertTask(TaskEntity(nextNegative(dao.minimumTaskId()), projectId, columnId,
            draft.title, draft.description, draft.dueDate, position, true))
    }

    suspend fun updateLocal(id: Long, draft: TaskDraft) {
        dao.task(id)?.let { dao.upsertTask(it.copy(title = draft.title, description = draft.description,
            dueDate = draft.dueDate, updatedAt = System.currentTimeMillis())) }
    }

    suspend fun moveLocal(id: Long, columnId: Long, position: Int) {
        dao.task(id)?.let { dao.upsertTask(it.copy(columnId = columnId, position = position,
            updatedAt = System.currentTimeMillis())) }
    }

    suspend fun closeLocal(id: Long) = setLocalStatus(id, false)
    suspend fun reopenLocal(id: Long) = setLocalStatus(id, true)
    private suspend fun setLocalStatus(id: Long, active: Boolean) {
        dao.task(id)?.let { dao.upsertTask(it.copy(isActive = active, updatedAt = System.currentTimeMillis())) }
    }
    suspend fun deleteLocal(id: Long) = dao.deleteTask(id)

    suspend fun update(api: KanboardApi, id: Long, draft: TaskDraft) {
        val old = dao.task(id) ?: return
        dao.upsertTask(old.copy(title = draft.title, description = draft.description, dueDate = draft.dueDate))
        try {
            check(api.updateTask(id, draft)) { "Task update was rejected." }
        } catch (error: Throwable) {
            dao.upsertTask(old); throw error
        }
        refreshBoard(api, old.projectId)
    }

    suspend fun move(api: KanboardApi, id: Long, columnId: Long, position: Int) {
        val old = dao.task(id) ?: return
        dao.upsertTask(old.copy(columnId = columnId, position = position))
        try {
            check(api.moveTask(old.projectId, id, columnId, position)) { "Task move was rejected." }
        } catch (error: Throwable) {
            dao.upsertTask(old); throw error
        }
        refreshBoard(api, old.projectId)
    }

    suspend fun close(api: KanboardApi, id: Long) = changeStatus(api, id, false)
    suspend fun reopen(api: KanboardApi, id: Long) = changeStatus(api, id, true)

    private suspend fun changeStatus(api: KanboardApi, id: Long, active: Boolean) {
        val old = dao.task(id) ?: return
        dao.upsertTask(old.copy(isActive = active))
        try {
            val ok = if (active) api.reopenTask(id) else api.closeTask(id)
            check(ok) { "Task status change was rejected." }
        } catch (error: Throwable) { dao.upsertTask(old); throw error }
        refreshBoard(api, old.projectId); refreshClosed(api, old.projectId)
    }

    suspend fun delete(api: KanboardApi, id: Long) {
        val old = dao.task(id) ?: return
        dao.deleteTask(id)
        try { check(api.deleteTask(id)) { "Task deletion was rejected." } }
        catch (error: Throwable) { dao.upsertTask(old); throw error }
    }

    private fun nextNegative(minimum: Long?): Long = minimum?.takeIf { it <= -1 }?.minus(1) ?: -1

    companion object { val LOCAL_COLUMNS = listOf("Backlog", "Ready", "Work in progress", "Done") }
}
