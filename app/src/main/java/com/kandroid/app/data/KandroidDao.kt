package com.kandroid.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface KandroidDao {
    @Query("SELECT * FROM projects WHERE isActive = 1 ORDER BY name")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM columns WHERE projectId = :projectId ORDER BY position, id")
    fun observeColumns(projectId: Long): Flow<List<ColumnEntity>>

    @Query("SELECT * FROM tasks WHERE projectId = :projectId AND isActive = :active ORDER BY columnId, position, id")
    fun observeTasks(projectId: Long, active: Boolean): Flow<List<TaskEntity>>

    @Query("SELECT * FROM projects WHERE isActive = 1 ORDER BY name")
    suspend fun activeProjects(): List<ProjectEntity>

    @Query("SELECT * FROM columns WHERE projectId = :projectId ORDER BY position, id")
    suspend fun widgetColumns(projectId: Long): List<ColumnEntity>

    @Query("SELECT * FROM tasks WHERE projectId = :projectId AND isActive = 1 ORDER BY columnId, position, id")
    suspend fun widgetTasks(projectId: Long): List<TaskEntity>

    @Query("SELECT * FROM projects ORDER BY id") suspend fun allProjects(): List<ProjectEntity>
    @Query("SELECT * FROM columns ORDER BY projectId, position, id") suspend fun allColumns(): List<ColumnEntity>
    @Query("SELECT * FROM tasks ORDER BY projectId, isActive DESC, columnId, position, id") suspend fun allTasks(): List<TaskEntity>
    @Query("SELECT MIN(id) FROM projects") suspend fun minimumProjectId(): Long?
    @Query("SELECT MIN(id) FROM columns") suspend fun minimumColumnId(): Long?
    @Query("SELECT MIN(id) FROM tasks") suspend fun minimumTaskId(): Long?

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun task(id: Long): TaskEntity?

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun project(id: Long): ProjectEntity?

    @Upsert suspend fun upsertProjects(items: List<ProjectEntity>)
    @Upsert suspend fun upsertProject(item: ProjectEntity)
    @Upsert suspend fun upsertColumns(items: List<ColumnEntity>)
    @Upsert suspend fun upsertTasks(items: List<TaskEntity>)
    @Upsert suspend fun upsertTask(item: TaskEntity)

    @Query("DELETE FROM columns WHERE projectId = :projectId")
    suspend fun deleteColumns(projectId: Long)

    @Query("DELETE FROM tasks WHERE projectId = :projectId AND isActive = :active")
    suspend fun deleteTasks(projectId: Long, active: Boolean)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("DELETE FROM tasks") suspend fun deleteAllTasks()
    @Query("DELETE FROM columns") suspend fun deleteAllColumns()
    @Query("DELETE FROM projects") suspend fun deleteAllProjects()

    @Transaction
    suspend fun clearAll() { deleteAllTasks(); deleteAllColumns(); deleteAllProjects() }

    @Transaction
    suspend fun replaceWorkspace(projects: List<ProjectEntity>, columns: List<ColumnEntity>, tasks: List<TaskEntity>) {
        clearAll(); upsertProjects(projects); upsertColumns(columns); upsertTasks(tasks)
    }

    @Transaction
    suspend fun replaceColumns(projectId: Long, items: List<ColumnEntity>) {
        deleteColumns(projectId); upsertColumns(items)
    }

    @Transaction
    suspend fun replaceTasks(projectId: Long, active: Boolean, items: List<TaskEntity>) {
        deleteTasks(projectId, active); upsertTasks(items)
    }
}

@Database(entities = [ProjectEntity::class, ColumnEntity::class, TaskEntity::class], version = 1, exportSchema = true)
abstract class KandroidDatabase : RoomDatabase() {
    abstract fun dao(): KandroidDao
}
