package com.kandroid.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.ZoneOffset

enum class AppMode { UNCONFIGURED, KANBOARD, LOCAL }

@Entity(tableName = "projects")
data class ProjectEntity(@PrimaryKey val id: Long, val name: String, val isActive: Boolean = true)

@Entity(tableName = "columns", primaryKeys = ["id", "projectId"])
data class ColumnEntity(val id: Long, val projectId: Long, val title: String, val position: Int)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: Long,
    val projectId: Long,
    val columnId: Long,
    val title: String,
    val description: String,
    val dueDate: String?,
    val position: Int,
    val isActive: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
)

data class Credentials(val serverUrl: String, val username: String, val token: String)
data class TaskDraft(val title: String, val description: String = "", val dueDate: String? = null)

@Serializable
data class BackupEnvelope(
    val format: String = FORMAT,
    val schemaVersion: Int = VERSION,
    val exportedAt: String,
    val sourceMode: String,
    val projects: List<BackupProject> = emptyList(),
    val columns: List<BackupColumn> = emptyList(),
    val tasks: List<BackupTask> = emptyList()
) {
    companion object { const val FORMAT = "kandroid-backup"; const val VERSION = 1 }
}

@Serializable data class BackupProject(val id: Long, val name: String, val isActive: Boolean = true)
@Serializable data class BackupColumn(val id: Long, val projectId: Long, val title: String, val position: Int)
@Serializable data class BackupTask(
    val id: Long, val projectId: Long, val columnId: Long, val title: String,
    val description: String = "", val dueDate: String? = null, val position: Int,
    val isActive: Boolean, val updatedAt: Long
)

@Serializable
data class ProjectDto(
    @Serializable(with = FlexibleStringSerializer::class) val id: String,
    val name: String,
    @SerialName("is_active") @Serializable(with = FlexibleStringSerializer::class) val isActive: String = "1"
) {
    fun entity() = ProjectEntity(id.toLong(), name, isActive == "1")
}

@Serializable
data class ColumnDto(
    @Serializable(with = FlexibleStringSerializer::class) val id: String,
    val title: String,
    @Serializable(with = FlexibleStringSerializer::class) val position: String = "0"
) {
    fun entity(projectId: Long) = ColumnEntity(id.toLong(), projectId, title, position.toIntOrNull() ?: 0)
}

@Serializable
data class TaskDto(
    @Serializable(with = FlexibleStringSerializer::class) val id: String,
    val title: String,
    val description: String = "",
    @SerialName("project_id") @Serializable(with = FlexibleStringSerializer::class) val projectId: String,
    @SerialName("column_id") @Serializable(with = FlexibleStringSerializer::class) val columnId: String,
    @Serializable(with = FlexibleStringSerializer::class) val position: String = "0",
    @SerialName("is_active") @Serializable(with = FlexibleStringSerializer::class) val isActive: String = "1",
    @SerialName("date_due") @Serializable(with = FlexibleStringSerializer::class) val dateDue: String = "0"
) {
    fun entity() = TaskEntity(
        id = id.toLong(), projectId = projectId.toLong(), columnId = columnId.toLong(),
        title = title, description = description, dueDate = dateDue.toLongOrNull()?.takeIf { it > 0 }
            ?.let { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
            ?: dateDue.takeUnless { it == "0" || it.isBlank() },
        position = position.toIntOrNull() ?: 0, isActive = isActive == "1"
    )
}

@Serializable
data class UserDto(
    @Serializable(with = FlexibleLongSerializer::class) val id: Long,
    val username: String,
    val name: String? = null
)

object FlexibleLongSerializer : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("FlexibleLong", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
    override fun deserialize(decoder: Decoder): Long {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeLong()
        val value = jsonDecoder.decodeJsonElement().jsonPrimitive
        return value.longOrNull ?: value.content.toLong()
    }
}

object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return jsonDecoder.decodeJsonElement().jsonPrimitive.content
    }
}
