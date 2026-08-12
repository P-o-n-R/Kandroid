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
