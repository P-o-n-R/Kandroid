package com.kandroid.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.kandroid.app.data.*
import com.kandroid.app.network.KanboardApi
import com.kandroid.app.network.KanboardException
import com.kandroid.app.widget.WidgetUpdater
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AppUiState(
    val mode: AppMode = AppMode.UNCONFIGURED,
    val credentials: Credentials? = null,
    val projects: List<ProjectEntity> = emptyList(),
    val selectedProjectId: Long? = null,
    val columns: List<ColumnEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val closedTasks: List<TaskEntity> = emptyList(),
    val loading: Boolean = false,
    val offline: Boolean = false,
    val message: String? = null,
    val showClosed: Boolean = false,
    val pendingImport: BackupEnvelope? = null
) { val configured: Boolean get() = mode != AppMode.UNCONFIGURED }

class KandroidViewModel(private val app: KandroidApplication) : ViewModel() {
    private val repo = app.repository
    private val mutable = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutable.asStateFlow()
    private var projectJob: Job? = null
    private var requestedProjectId: Long? = null

    init {
        val mode = app.credentialStore.mode()
        val saved = app.credentialStore.load()
        mutable.update { it.copy(mode = if (mode == AppMode.KANBOARD && saved == null) AppMode.UNCONFIGURED else mode,
            credentials = saved) }
        viewModelScope.launch {
            repo.projects().collect { projects ->
                mutable.update { it.copy(projects = projects) }
                requestedProjectId?.let { requested ->
                    if (projects.any { it.id == requested }) { requestedProjectId = null; selectProject(requested); return@collect }
                    if (projects.isNotEmpty()) requestedProjectId = null
                }
                val selected = mutable.value.selectedProjectId
                if (selected == null && projects.isNotEmpty()) selectProject(projects.first().id)
                else if (selected != null && projects.none { it.id == selected }) {
                    projects.firstOrNull()?.let { selectProject(it.id) } ?: clearProjectSelection()
                }
            }
        }
        if (mutable.value.mode == AppMode.KANBOARD) refreshProjects()
    }

    fun testConnection(credentials: Credentials, result: (Result<String>) -> Unit) = viewModelScope.launch {
        val normalized = normalize(credentials)
        runCatching { val api = KanboardApi(normalized); api.me(); api.version() }.also(result)
    }

    fun saveCredentials(credentials: Credentials) {
        val normalized = normalize(credentials)
        app.credentialStore.save(normalized); app.credentialStore.setMode(AppMode.KANBOARD)
        mutable.update { it.copy(mode = AppMode.KANBOARD, credentials = normalized, message = null) }
        refreshProjects()
    }

    fun connectInitial(credentials: Credentials) = perform {
        val normalized = normalize(credentials)
        clearProjectSelection()
        app.database.withTransaction { app.database.dao().clearAll() }
        app.credentialStore.save(normalized); app.credentialStore.setMode(AppMode.KANBOARD)
        mutable.update { it.copy(mode = AppMode.KANBOARD, credentials = normalized, offline = false) }
        WidgetUpdater.resetAll(app)
        repo.refreshProjects(KanboardApi(normalized))
    }

    fun startLocal() = perform {
        clearProjectSelection()
        app.database.withTransaction { app.database.dao().clearAll(); repo.createLocalProject("My project") }
        app.credentialStore.clearCredentials(); app.credentialStore.setMode(AppMode.LOCAL)
        mutable.update { it.copy(mode = AppMode.LOCAL, credentials = null, offline = false) }
        WidgetUpdater.resetAll(app)
    }

    fun switchToLocal() = startLocal()

    fun switchToKanboard(credentials: Credentials) = perform {
        val normalized = normalize(credentials)
        clearProjectSelection()
        app.database.withTransaction { app.database.dao().clearAll() }
        app.credentialStore.save(normalized); app.credentialStore.setMode(AppMode.KANBOARD)
        mutable.update { it.copy(mode = AppMode.KANBOARD, credentials = normalized, offline = false) }
        WidgetUpdater.resetAll(app)
        repo.refreshProjects(KanboardApi(normalized))
    }

    fun openProject(projectId: Long) {
        val projects = mutable.value.projects
        if (projects.any { it.id == projectId }) { requestedProjectId = null; selectProject(projectId) }
        else if (projects.isEmpty()) requestedProjectId = projectId
    }

    fun selectProject(projectId: Long) {
        if (mutable.value.selectedProjectId == projectId && projectJob != null) return
        projectJob?.cancel(); mutable.update { it.copy(selectedProjectId = projectId, showClosed = false) }
        projectJob = viewModelScope.launch {
            combine(repo.columns(projectId), repo.tasks(projectId, true), repo.tasks(projectId, false)) { c, t, closed -> Triple(c, t, closed) }
                .collect { (columns, tasks, closed) -> mutable.update { it.copy(columns = columns, tasks = tasks, closedTasks = closed) } }
        }
        refreshBoard()
    }

    fun refreshProjects() = perform {
        if (isKanboard()) {
            val api = api(); repo.refreshProjects(api)
            mutable.value.selectedProjectId?.let { repo.refreshBoard(api, it) }
        }
    }

    fun refreshBoard() = perform {
        if (isKanboard()) {
            val id = requireNotNull(mutable.value.selectedProjectId)
            if (mutable.value.showClosed) repo.refreshClosed(api(), id) else repo.refreshBoard(api(), id)
        }
    }

    fun showClosed(show: Boolean) { mutable.update { it.copy(showClosed = show) }; if (show) refreshBoard() }

    fun create(columnId: Long, draft: TaskDraft) = perform {
        val project = requireNotNull(mutable.value.selectedProjectId)
        if (isKanboard()) repo.create(api(), project, columnId, draft) else repo.createLocal(project, columnId, draft)
    }
    fun createProject(name: String) = perform {
        val id = if (isKanboard()) repo.createProject(api(), name.trim()) else repo.createLocalProject(name.trim())
        selectProject(id)
    }
    fun archiveProject(projectId: Long) = perform {
        if (isKanboard()) repo.archiveProject(api(), projectId) else repo.archiveLocalProject(projectId)
    }
    fun update(id: Long, draft: TaskDraft) = perform { if (isKanboard()) repo.update(api(), id, draft) else repo.updateLocal(id, draft) }
    fun move(id: Long, columnId: Long, position: Int) = perform { if (isKanboard()) repo.move(api(), id, columnId, position) else repo.moveLocal(id, columnId, position) }
    fun close(id: Long) = perform { if (isKanboard()) repo.close(api(), id) else repo.closeLocal(id) }
    fun reopen(id: Long) = perform { if (isKanboard()) repo.reopen(api(), id) else repo.reopenLocal(id) }
    fun delete(id: Long) = perform { if (isKanboard()) repo.delete(api(), id) else repo.deleteLocal(id) }

    fun createExport(result: (Result<String>) -> Unit) = viewModelScope.launch {
        mutable.update { it.copy(loading = true, message = null) }
        val exported = runCatching {
            if (isKanboard()) app.backupService.exportKanboard(api()) else app.backupService.exportLocal()
        }
        mutable.update { it.copy(loading = false, offline = exported.exceptionOrNull() is KanboardException.Network) }
        result(exported)
    }

    fun prepareImport(value: String) {
        runCatching { app.backupService.parseAndValidate(value) }
            .onSuccess { backup -> mutable.update { it.copy(pendingImport = backup) } }
            .onFailure { error -> mutable.update { it.copy(message = friendly(error)) } }
    }
    fun cancelImport() = mutable.update { it.copy(pendingImport = null) }
    fun confirmImport() = perform {
        val backup = requireNotNull(mutable.value.pendingImport)
        clearProjectSelection(); app.backupService.import(backup)
        mutable.update { it.copy(pendingImport = null) }
        WidgetUpdater.resetAll(app)
    }
    fun showMessage(message: String) = mutable.update { it.copy(message = message) }
    fun clearMessage() = mutable.update { it.copy(message = null) }

    private fun clearProjectSelection() {
        projectJob?.cancel(); projectJob = null
        mutable.update { it.copy(selectedProjectId = null, columns = emptyList(), tasks = emptyList(), closedTasks = emptyList(), showClosed = false) }
    }
    private fun normalize(value: Credentials) = value.copy(serverUrl = value.serverUrl.trim().trimEnd('/'))
    private fun isKanboard() = mutable.value.mode == AppMode.KANBOARD
    private fun api() = KanboardApi(requireNotNull(mutable.value.credentials))
    private fun perform(block: suspend () -> Unit) = viewModelScope.launch {
        mutable.update { it.copy(loading = true, message = null) }
        try {
            block(); try { WidgetUpdater.updateAll(app) } catch (_: Throwable) { }
            mutable.update { it.copy(loading = false, offline = false) }
        } catch (error: Throwable) {
            mutable.update { it.copy(loading = false, offline = isKanboard() && error is KanboardException.Network, message = friendly(error)) }
        }
    }
    private fun friendly(error: Throwable): String = when (error) {
        is KanboardException -> error.message ?: "Kanboard request failed."
        is IllegalArgumentException -> error.message ?: "Invalid data."
        else -> error.message ?: "Operation failed."
    }

    class Factory(private val app: KandroidApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = KandroidViewModel(app) as T
    }
}
