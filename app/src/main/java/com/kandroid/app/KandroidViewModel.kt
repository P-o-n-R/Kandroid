package com.kandroid.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kandroid.app.data.*
import com.kandroid.app.network.KanboardApi
import com.kandroid.app.network.KanboardException
import com.kandroid.app.widget.WidgetUpdater
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AppUiState(
    val configured: Boolean = false,
    val credentials: Credentials? = null,
    val projects: List<ProjectEntity> = emptyList(),
    val selectedProjectId: Long? = null,
    val columns: List<ColumnEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val closedTasks: List<TaskEntity> = emptyList(),
    val loading: Boolean = false,
    val offline: Boolean = false,
    val message: String? = null,
    val showClosed: Boolean = false
)

class KandroidViewModel(private val app: KandroidApplication) : ViewModel() {
    private val repo = app.repository
    private val mutable = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutable.asStateFlow()
    private var projectJob: kotlinx.coroutines.Job? = null
    private var requestedProjectId: Long? = null

    init {
        val saved = app.credentialStore.load()
        mutable.update { it.copy(configured = saved != null, credentials = saved) }
        viewModelScope.launch {
            repo.projects().collect { projects ->
                mutable.update { state -> state.copy(projects = projects) }
                requestedProjectId?.let { requested ->
                    if (projects.any { it.id == requested }) {
                        requestedProjectId = null
                        selectProject(requested)
                        return@collect
                    } else if (projects.isNotEmpty()) {
                        requestedProjectId = null
                    }
                }
                val selected = mutable.value.selectedProjectId
                if (selected == null && projects.isNotEmpty()) selectProject(projects.first().id)
                else if (selected != null && projects.none { it.id == selected }) {
                    projects.firstOrNull()?.let { selectProject(it.id) } ?: clearProjectSelection()
                }
            }
        }
        if (saved != null) refreshProjects()
    }

    fun testConnection(credentials: Credentials, result: (Result<String>) -> Unit) = viewModelScope.launch {
        val normalized = credentials.copy(serverUrl = credentials.serverUrl.trim().trimEnd('/'))
        runCatching { val api = KanboardApi(normalized); api.me(); api.version() }.also(result)
    }

    fun saveCredentials(credentials: Credentials) {
        val normalized = credentials.copy(serverUrl = credentials.serverUrl.trim().trimEnd('/'))
        app.credentialStore.save(normalized)
        mutable.update { it.copy(configured = true, credentials = normalized, message = null) }
        refreshProjects()
    }

    fun logout() {
        app.credentialStore.clear()
        mutable.update { AppUiState() }
        viewModelScope.launch { WidgetUpdater.updateAll(app) }
    }

    fun openProject(projectId: Long) {
        val projects = mutable.value.projects
        if (projects.any { it.id == projectId }) {
            requestedProjectId = null
            selectProject(projectId)
        } else if (projects.isEmpty()) {
            requestedProjectId = projectId
        }
    }

    fun selectProject(projectId: Long) {
        if (mutable.value.selectedProjectId == projectId && projectJob != null) return
        projectJob?.cancel()
        mutable.update { it.copy(selectedProjectId = projectId, showClosed = false) }
        projectJob = viewModelScope.launch {
            combine(repo.columns(projectId), repo.tasks(projectId, true), repo.tasks(projectId, false)) { c, t, closed ->
                Triple(c, t, closed)
            }.collect { (columns, tasks, closed) ->
                mutable.update { it.copy(columns = columns, tasks = tasks, closedTasks = closed) }
            }
        }
        refreshBoard()
    }

    fun refreshProjects() = perform {
        repo.refreshProjects(api())
        mutable.value.selectedProjectId?.let { repo.refreshBoard(api(), it) }
    }

    fun refreshBoard() = perform {
        val id = requireNotNull(mutable.value.selectedProjectId)
        if (mutable.value.showClosed) repo.refreshClosed(api(), id) else repo.refreshBoard(api(), id)
    }

    fun showClosed(show: Boolean) {
        mutable.update { it.copy(showClosed = show) }
        if (show) refreshBoard()
    }

    fun create(columnId: Long, draft: TaskDraft) = perform {
        repo.create(api(), requireNotNull(mutable.value.selectedProjectId), columnId, draft)
    }

    fun createProject(name: String) = perform {
        val id = repo.createProject(api(), name.trim())
        selectProject(id)
    }

    fun archiveProject(projectId: Long) = perform { repo.archiveProject(api(), projectId) }

    fun update(id: Long, draft: TaskDraft) = perform { repo.update(api(), id, draft) }
    fun move(id: Long, columnId: Long, position: Int) = perform { repo.move(api(), id, columnId, position) }
    fun close(id: Long) = perform { repo.close(api(), id) }
    fun reopen(id: Long) = perform { repo.reopen(api(), id) }
    fun delete(id: Long) = perform { repo.delete(api(), id) }
    fun clearMessage() = mutable.update { it.copy(message = null) }

    private fun clearProjectSelection() {
        projectJob?.cancel()
        projectJob = null
        mutable.update { it.copy(selectedProjectId = null, columns = emptyList(), tasks = emptyList(), closedTasks = emptyList(), showClosed = false) }
    }

    private fun api() = KanboardApi(requireNotNull(mutable.value.credentials))
    private fun perform(block: suspend () -> Unit) = viewModelScope.launch {
        mutable.update { it.copy(loading = true, message = null) }
        try {
            block()
            try { WidgetUpdater.updateAll(app) } catch (_: Throwable) { }
            mutable.update { it.copy(loading = false, offline = false) }
        }
        catch (error: Throwable) {
            mutable.update { it.copy(loading = false, offline = error is KanboardException.Network, message = friendly(error)) }
        }
    }

    private fun friendly(error: Throwable): String = when (error) {
        is KanboardException -> error.message ?: "Kanboard request failed."
        is IllegalArgumentException -> error.message ?: "Invalid setup."
        else -> error.message ?: "Synchronization failed."
    }

    class Factory(private val app: KandroidApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = KandroidViewModel(app) as T
    }
}
