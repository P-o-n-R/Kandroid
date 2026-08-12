package com.kandroid.app.data

import com.kandroid.app.network.KanboardApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

class BackupService(private val dao: KandroidDao) {
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }

    suspend fun exportLocal(): String = encode(
        mode = AppMode.LOCAL,
        projects = dao.allProjects(), columns = dao.allColumns(), tasks = dao.allTasks()
    )

    suspend fun exportKanboard(api: KanboardApi): String {
        val projects = api.projects().map { it.entity() }.filter { it.isActive }
        val columns = mutableListOf<ColumnEntity>()
        val tasks = mutableListOf<TaskEntity>()
        projects.forEach { project ->
            columns += api.columns(project.id).map { it.entity(project.id) }
            tasks += api.tasks(project.id, true).map { it.entity() }
            tasks += api.tasks(project.id, false).map { it.entity() }
        }
        return encode(AppMode.KANBOARD, projects, columns, tasks)
    }

    fun parseAndValidate(value: String): BackupEnvelope {
        val backup = runCatching { json.decodeFromString<BackupEnvelope>(value) }
            .getOrElse { throw IllegalArgumentException("This is not a valid Kandroid backup.", it) }
        require(backup.format == BackupEnvelope.FORMAT) { "This file is not a Kandroid backup." }
        require(backup.schemaVersion == BackupEnvelope.VERSION) { "Backup version ${backup.schemaVersion} is not supported." }
        require(runCatching { Instant.parse(backup.exportedAt) }.isSuccess) { "The export timestamp is invalid." }
        require(backup.sourceMode in setOf(AppMode.LOCAL.name, AppMode.KANBOARD.name)) { "The backup source mode is invalid." }
        require(backup.projects.map { it.id }.distinct().size == backup.projects.size) { "Project IDs must be unique." }
        require(backup.columns.map { it.id }.distinct().size == backup.columns.size) { "Column IDs must be unique." }
        require(backup.tasks.map { it.id }.distinct().size == backup.tasks.size) { "Task IDs must be unique." }
        require(backup.projects.all { it.name.isNotBlank() }) { "Every project needs a title." }
        require(backup.columns.all { it.title.isNotBlank() && it.position >= 0 }) { "Every column needs a title and valid position." }
        require(backup.tasks.all { it.title.isNotBlank() && it.position >= 0 }) { "Every task needs a title and valid position." }
        val projectIds = backup.projects.map { it.id }.toSet()
        require(backup.columns.all { it.projectId in projectIds }) { "A column refers to a missing project." }
        val columnKeys = backup.columns.map { it.id to it.projectId }.toSet()
        require(backup.tasks.all { it.projectId in projectIds && (it.columnId to it.projectId) in columnKeys }) {
            "A task refers to a missing project or column."
        }
        require(backup.tasks.all { it.dueDate == null || runCatching { LocalDate.parse(it.dueDate) }.isSuccess }) {
            "A task has an invalid due date."
        }
        return backup
    }

    suspend fun import(backup: BackupEnvelope) = dao.replaceWorkspace(
        backup.projects.map { ProjectEntity(it.id, it.name, it.isActive) },
        backup.columns.map { ColumnEntity(it.id, it.projectId, it.title, it.position) },
        backup.tasks.map { TaskEntity(it.id, it.projectId, it.columnId, it.title, it.description,
            it.dueDate, it.position, it.isActive, it.updatedAt) }
    )

    private fun encode(mode: AppMode, projects: List<ProjectEntity>, columns: List<ColumnEntity>, tasks: List<TaskEntity>) =
        json.encodeToString(BackupEnvelope(
            exportedAt = Instant.now().toString(), sourceMode = mode.name,
            projects = projects.map { BackupProject(it.id, it.name, it.isActive) },
            columns = columns.map { BackupColumn(it.id, it.projectId, it.title, it.position) },
            tasks = tasks.map { BackupTask(it.id, it.projectId, it.columnId, it.title, it.description,
                it.dueDate, it.position, it.isActive, it.updatedAt) }
        ))
}
