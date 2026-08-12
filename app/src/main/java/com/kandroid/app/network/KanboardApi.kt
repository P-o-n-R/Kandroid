package com.kandroid.app.network

import com.kandroid.app.data.ColumnDto
import com.kandroid.app.data.Credentials as AppCredentials
import com.kandroid.app.data.ProjectDto
import com.kandroid.app.data.TaskDraft
import com.kandroid.app.data.TaskDto
import com.kandroid.app.data.UserDto
import com.kandroid.app.data.FlexibleLongSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

sealed class KanboardException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Authentication : KanboardException("Authentication failed. Check your username and API token.")
    class Network(cause: Throwable) : KanboardException("Cannot reach the server. Cached data is still available.", cause)
    class Server(val code: Int, detail: String) : KanboardException("Server rejected the request: $detail")
    class InvalidResponse(detail: String = "The server returned an unexpected response.", cause: Throwable? = null) : KanboardException(detail, cause)
}

class KanboardApi(
    private val credentials: AppCredentials,
    private val client: OkHttpClient = defaultClient(),
    private val allowInsecureForTests: Boolean = false
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val ids = AtomicLong(1)
    private val endpoint = credentials.serverUrl.trimEnd('/').let {
        if (it.endsWith("jsonrpc.php")) it else "$it/jsonrpc.php"
    }

    init { require(endpoint.startsWith("https://") || allowInsecureForTests) { "A secure HTTPS server URL is required." } }

    suspend fun version(): String = call("getVersion", buildJsonObject {}, String.serializer())
    suspend fun me(): UserDto = call("getMe", buildJsonObject {}, UserDto.serializer())
    suspend fun projects(): List<ProjectDto> = call("getMyProjects", buildJsonObject {}, ProjectDto.serializer().list)
    suspend fun createProject(name: String): Long = call(
        "createProject", obj("name" to name), FlexibleLongSerializer
    )
    suspend fun archiveProject(projectId: Long): Boolean = call(
        "disableProject", obj("project_id" to projectId), Boolean.serializer()
    )
    suspend fun columns(projectId: Long): List<ColumnDto> = call("getColumns", obj("project_id" to projectId), ColumnDto.serializer().list)
    suspend fun tasks(projectId: Long, active: Boolean): List<TaskDto> = call("getAllTasks", obj("project_id" to projectId, "status_id" to if (active) 1 else 0), TaskDto.serializer().list)

    suspend fun createTask(projectId: Long, columnId: Long, draft: TaskDraft): Long = call(
        "createTask", obj("title" to draft.title, "project_id" to projectId, "column_id" to columnId,
            "description" to draft.description, "date_due" to (draft.dueDate ?: "")), FlexibleLongSerializer
    )

    suspend fun updateTask(id: Long, draft: TaskDraft): Boolean = call(
        "updateTask", obj("id" to id, "title" to draft.title, "description" to draft.description,
            "date_due" to (draft.dueDate ?: "")), Boolean.serializer()
    )

    suspend fun moveTask(projectId: Long, taskId: Long, columnId: Long, position: Int): Boolean = call(
        "moveTaskPosition", obj("project_id" to projectId, "task_id" to taskId, "column_id" to columnId,
            "position" to position, "swimlane_id" to 0), Boolean.serializer()
    )

    suspend fun closeTask(id: Long): Boolean = call("closeTask", obj("task_id" to id), Boolean.serializer())
    suspend fun reopenTask(id: Long): Boolean = call("openTask", obj("task_id" to id), Boolean.serializer())
    suspend fun deleteTask(id: Long): Boolean = call("removeTask", obj("task_id" to id), Boolean.serializer())

    private suspend fun <T> call(method: String, params: JsonObject, serializer: KSerializer<T>): T = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0"); put("method", method); put("id", ids.getAndIncrement()); put("params", params)
        }
        val request = Request.Builder().url(endpoint)
            .header("Authorization", Credentials.basic(credentials.username, credentials.token))
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) throw KanboardException.Authentication()
                if (!response.isSuccessful) throw KanboardException.Server(response.code, "HTTP ${response.code}")
                val body = response.body.string()
                val root = runCatching { json.parseToJsonElement(body).jsonObject }
                    .getOrElse { throw KanboardException.InvalidResponse("The server returned invalid JSON.", it) }
                root["error"]?.takeUnless { it is JsonNull }?.jsonObject?.let { error ->
                    val code = error["code"]?.jsonPrimitive?.intOrNull ?: -1
                    val message = error["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown JSON-RPC error"
                    if (code == 401 || message.contains("auth", true)) throw KanboardException.Authentication()
                    throw KanboardException.Server(code, message)
                }
                val result = root["result"] ?: throw KanboardException.InvalidResponse("Kanboard returned no result for $method.")
                if (result is JsonPrimitive && result.isString.not() && result.booleanOrNull == false) {
                    throw KanboardException.Server(-1, "$method was rejected by Kanboard.")
                }
                runCatching { json.decodeFromJsonElement(serializer, result) }
                    .getOrElse { throw KanboardException.InvalidResponse("Kanboard returned an unexpected result for $method.", it) }
            }
        } catch (e: KanboardException) { throw e }
        catch (e: IOException) { throw KanboardException.Network(e) }
        catch (e: Exception) { throw KanboardException.InvalidResponse(cause = e) }
    }

    companion object {
        fun defaultClient() = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()

        private fun obj(vararg values: Pair<String, Any>) = buildJsonObject {
            values.forEach { (key, value) -> when (value) {
                is String -> put(key, value); is Long -> put(key, value); is Int -> put(key, value)
                is Boolean -> put(key, value); else -> error("Unsupported parameter")
            } }
        }
    }
}

private val <T> KSerializer<T>.list: KSerializer<List<T>> get() = kotlinx.serialization.builtins.ListSerializer(this)
