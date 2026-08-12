@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kandroid.app.ui

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kandroid.app.AppUiState
import com.kandroid.app.KandroidViewModel
import com.kandroid.app.data.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun KandroidApp(model: KandroidViewModel) = KandroidTheme {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var exportText by remember { mutableStateOf<String?>(null) }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            runCatching { context.contentResolver.openOutputStream(it)?.bufferedWriter(Charsets.UTF_8)?.use { writer -> writer.write(exportText.orEmpty()) } }
                .onSuccess { model.showMessage("Backup saved.") }
                .onFailure { error -> model.showMessage(error.message ?: "Could not write the backup.") }
        }
        exportText = null
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.openInputStream(it)?.bufferedReader(Charsets.UTF_8)?.use { reader -> reader.readText() } ?: error("Could not read this file.") }
                .onSuccess(model::prepareImport)
                .onFailure { error -> model.showMessage(error.message ?: "Could not read the backup.") }
        }
    }
    val export: () -> Unit = {
        model.createExport { result -> result.onSuccess {
            exportText = it; createDocument.launch("kandroid-${java.time.LocalDate.now()}.json")
        }.onFailure { error -> model.showMessage(error.message ?: "Could not create the backup.") } }
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); model.clearMessage() }
    }
    if (!state.configured) SetupScreen(model) else BoardScaffold(state, model, snackbar, export) {
        openDocument.launch(arrayOf("application/json", "text/json", "text/plain"))
    }
    state.pendingImport?.let { backup ->
        AlertDialog(onDismissRequest = model::cancelImport, title = { Text("Replace local workspace?") },
            text = { Text("This backup contains ${backup.projects.size} projects and ${backup.tasks.size} tasks. Importing it permanently replaces everything currently stored in local-only mode.") },
            confirmButton = { Button(model::confirmImport, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Replace and import") } },
            dismissButton = { TextButton(model::cancelImport) { Text("Cancel") } })
    }
}

@Composable
private fun SetupScreen(model: KandroidViewModel) {
    var url by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var tested by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val credentials = Credentials(url, username, token)
    val valid = url.startsWith("https://") && username.isNotBlank() && token.isNotBlank()

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.ViewKanban, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Welcome to Kandroid", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Connect to your Kanboard server. Your API token is encrypted by Android Keystore on this device.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(url, { url = it; tested = false }, Modifier.fillMaxWidth(), label = { Text("Server URL") }, singleLine = true,
                supportingText = { if (url.isNotEmpty() && !url.startsWith("https://")) Text("HTTPS is required") })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(username, { username = it; tested = false }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(token, { token = it; tested = false }, Modifier.fillMaxWidth(), label = { Text("Personal API token") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation())
            testResult?.let { Text(it, color = if (it.startsWith("Connected")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(enabled = valid && !testing, onClick = {
                    testing = true; tested = false; testResult = null
                    model.testConnection(credentials) { result ->
                        testing = false
                        tested = result.isSuccess
                        testResult = result.fold({ "Connected · Kanboard $it" }, { it.message ?: "Connection failed" })
                    }
                }, modifier = Modifier.weight(1f)) {
                    if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Test connection")
                }
                Button(enabled = valid && tested && !testing, onClick = { model.connectInitial(credentials) }, modifier = Modifier.weight(1f)) { Text("Connect to Kanboard") }
            }
            TextButton(onClick = model::startLocal, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Use locally instead") }
        }
        }
    }
}

@Composable
private fun BoardScaffold(state: AppUiState, model: KandroidViewModel, snackbar: SnackbarHostState,
    export: () -> Unit, importBackup: () -> Unit) {
    var projectMenu by remember { mutableStateOf(false) }
    var settingsMenu by remember { mutableStateOf(false) }
    var createProject by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    var confirmLocal by remember { mutableStateOf(false) }
    var localConnectWarning by remember { mutableStateOf(false) }
    var connectionDialog by remember { mutableStateOf(false) }
    var switchingFromLocal by remember { mutableStateOf(false) }
    val current = state.projects.firstOrNull { it.id == state.selectedProjectId }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        TextButton(onClick = { projectMenu = true }) { Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(current?.name ?: "Select project", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (state.mode == AppMode.KANBOARD) Icons.Default.Cloud else Icons.Default.Storage, null, Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp)); Text(if (state.mode == AppMode.KANBOARD) "Kanboard" else "Local only", style = MaterialTheme.typography.labelSmall)
                            }
                        } }
                        DropdownMenu(projectMenu, { projectMenu = false }) {
                            state.projects.forEach { project ->
                                DropdownMenuItem({ Text(project.name) }, onClick = { projectMenu = false; model.selectProject(project.id) },
                                    leadingIcon = { if (project.id == state.selectedProjectId) Icon(Icons.Default.Check, null) })
                            }
                            HorizontalDivider()
                            DropdownMenuItem({ Text("Create project") }, onClick = { projectMenu = false; createProject = true },
                                leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) })
                            if (current != null) DropdownMenuItem({ Text("Archive project") }, onClick = { projectMenu = false; confirmArchive = true },
                                leadingIcon = { Icon(Icons.Default.Archive, null) })
                        }
                    }
                },
                navigationIcon = { if (state.showClosed) IconButton({ model.showClosed(false) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to board") } },
                actions = {
                    IconButton({ model.refreshBoard() }) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton({ model.showClosed(!state.showClosed) }) { Icon(Icons.Default.Archive, "Closed tasks") }
                    Box {
                        IconButton({ settingsMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(settingsMenu, { settingsMenu = false }) {
                            if (state.mode == AppMode.LOCAL) {
                                DropdownMenuItem({ Text("Export backup") }, { settingsMenu = false; export() }, leadingIcon = { Icon(Icons.Default.SaveAlt, null) })
                                DropdownMenuItem({ Text("Import backup") }, { settingsMenu = false; importBackup() }, leadingIcon = { Icon(Icons.Default.UploadFile, null) })
                                DropdownMenuItem({ Text("Connect to Kanboard") }, { settingsMenu = false; localConnectWarning = true }, leadingIcon = { Icon(Icons.Default.Cloud, null) })
                            } else {
                                DropdownMenuItem({ Text("Export snapshot") }, { settingsMenu = false; export() }, leadingIcon = { Icon(Icons.Default.SaveAlt, null) })
                                DropdownMenuItem({ Text("Connection settings") }, { settingsMenu = false; switchingFromLocal = false; connectionDialog = true }, leadingIcon = { Icon(Icons.Default.Settings, null) })
                                DropdownMenuItem({ Text("Switch to local-only") }, { settingsMenu = false; confirmLocal = true }, leadingIcon = { Icon(Icons.Default.Storage, null) })
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.showClosed -> ClosedScreen(state, model)
                state.selectedProjectId == null && state.projects.isEmpty() && state.loading -> LoadingPane()
                state.projects.isEmpty() -> EmptyPane("No projects", if (state.mode == AppMode.LOCAL) "Create a project to begin." else "Check your Kanboard permissions, then refresh.")
                else -> BoardScreen(state, model)
            }
            if (state.offline) AssistChip({}, { Text("Offline · showing cached data") }, Modifier.align(Alignment.TopCenter).padding(8.dp),
                leadingIcon = { Icon(Icons.Default.CloudOff, null, Modifier.size(18.dp)) })
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
    if (createProject) ProjectNameDialog({ createProject = false }) { name ->
        model.createProject(name); createProject = false
    }
    if (confirmArchive && current != null) AlertDialog(
        onDismissRequest = { confirmArchive = false },
        title = { Text("Archive ${current.name}?") },
        text = { Text(if (state.mode == AppMode.KANBOARD) "The project will be disabled in Kanboard and removed from this active-project list. It will not be deleted." else "The project will be archived and removed from this active-project list.") },
        confirmButton = { Button({ model.archiveProject(current.id); confirmArchive = false }) { Text("Archive") } },
        dismissButton = { TextButton({ confirmArchive = false }) { Text("Cancel") } }
    )
    if (confirmLocal) AlertDialog(onDismissRequest = { confirmLocal = false }, title = { Text("Switch to local-only?") },
        text = { Text("The on-device Kanboard cache will be removed. Your Kanboard server data is not affected. A new local starter project will be created.") },
        confirmButton = { Button({ confirmLocal = false; model.switchToLocal() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Switch to local-only") } },
        dismissButton = { TextButton({ confirmLocal = false }) { Text("Cancel") } })
    if (localConnectWarning) AlertDialog(onDismissRequest = { localConnectWarning = false }, title = { Text("Connect to Kanboard?") },
        text = { Text("All local projects and tasks will be permanently removed after a successful connection test. Export a backup first if you may need this workspace again.") },
        confirmButton = { Button({ localConnectWarning = false; switchingFromLocal = true; connectionDialog = true }) { Text("Continue") } },
        dismissButton = { Row { TextButton(export) { Text("Export backup") }; TextButton({ localConnectWarning = false }) { Text("Cancel") } } })
    if (connectionDialog) ConnectionDialog(state.credentials, switchingFromLocal, model,
        dismiss = { connectionDialog = false }, save = { credentials ->
            connectionDialog = false
            if (switchingFromLocal) model.switchToKanboard(credentials) else model.saveCredentials(credentials)
        })
}

@Composable
private fun ConnectionDialog(existing: Credentials?, switching: Boolean, model: KandroidViewModel,
    dismiss: () -> Unit, save: (Credentials) -> Unit) {
    var url by rememberSaveable { mutableStateOf(existing?.serverUrl.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(existing?.username.orEmpty()) }
    var token by rememberSaveable { mutableStateOf(existing?.token.orEmpty()) }
    var testing by remember { mutableStateOf(false) }
    var tested by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val credentials = Credentials(url, username, token)
    val valid = url.startsWith("https://") && username.isNotBlank() && token.isNotBlank()
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (switching) "Connect to Kanboard" else "Connection settings") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(url, { url = it; tested = false }, label = { Text("Server URL") }, singleLine = true)
            OutlinedTextField(username, { username = it; tested = false }, label = { Text("Username") }, singleLine = true)
            OutlinedTextField(token, { token = it; tested = false }, label = { Text("Personal API token") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            result?.let { Text(it, color = if (tested) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            OutlinedButton(enabled = valid && !testing, onClick = {
                testing = true; tested = false; result = null
                model.testConnection(credentials) { testedResult ->
                    testing = false; tested = testedResult.isSuccess
                    result = testedResult.fold({ "Connected · Kanboard $it" }, { it.message ?: "Connection failed" })
                }
            }) { if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Test connection") }
        } },
        confirmButton = { Button({ save(credentials) }, enabled = valid && (!switching || tested)) { Text(if (switching) "Switch and remove local data" else "Save") } },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}

@Composable
private fun ProjectNameDialog(dismiss: () -> Unit, create: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("New project") },
        text = { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Project name") }, singleLine = true) },
        confirmButton = { Button({ create(name.trim()) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoardScreen(state: AppUiState, model: KandroidViewModel) {
    if (state.columns.isEmpty()) { EmptyPane("No columns", "Pull down to refresh this board."); return }
    val pager = rememberPagerState(pageCount = { state.columns.size })
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var creatingColumn by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = 8.dp, divider = {}) {
            state.columns.forEachIndexed { index, column ->
                Tab(pager.currentPage == index, { scope.launch { pager.animateScrollToPage(index) } },
                    text = { Text("${column.title}  ${state.tasks.count { it.columnId == column.id }}") })
            }
        }
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = model::refreshBoard, modifier = Modifier.weight(1f)) {
            HorizontalPager(pager, Modifier.fillMaxSize(), key = { state.columns[it].id }) { page ->
                val column = state.columns[page]
                val tasks = state.tasks.filter { it.columnId == column.id }.sortedBy { it.position }
                Box(Modifier.fillMaxSize()) {
                    if (tasks.isEmpty()) EmptyPane("Nothing in ${column.title}", "Create a task or drag one here.")
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
                            TaskCard(task, onClick = { editing = task }, onDrag = { dx, dy ->
                                val targetPage = when { dx > 80 && page < state.columns.lastIndex -> page + 1; dx < -80 && page > 0 -> page - 1; else -> page }
                                val targetPosition = (index + when { dy > 60 -> 1; dy < -60 -> -1; else -> 0 }).coerceAtLeast(0) + 1
                                model.move(task.id, state.columns[targetPage].id, targetPosition)
                                if (targetPage != page) scope.launch { pager.animateScrollToPage(targetPage) }
                            })
                        }
                    }
                    FloatingActionButton({ creatingColumn = column.id }, Modifier.align(Alignment.BottomEnd).padding(20.dp)) { Icon(Icons.Default.Add, "Create task") }
                }
            }
        }
    }
    creatingColumn?.let { columnId -> TaskEditor(null, { creatingColumn = null }, { model.create(columnId, it); creatingColumn = null }) }
    editing?.let { task -> TaskEditor(task, { editing = null }, { model.update(task.id, it); editing = null },
        columns = state.columns, onMove = { columnId ->
            val position = state.tasks.count { it.columnId == columnId } + 1
            model.move(task.id, columnId, position); editing = null
        }, onClose = { model.close(task.id); editing = null }, onDelete = { model.delete(task.id); editing = null }) }
}

@Composable
private fun TaskCard(task: TaskEntity, onClick: () -> Unit, onDrag: (Float, Float) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().alpha(if (dragging) .65f else 1f)
        .pointerInput(task.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { dragging = true; dx = 0f; dy = 0f },
                onDragCancel = { dragging = false },
                onDragEnd = { dragging = false; onDrag(dx, dy) },
                onDrag = { change, amount -> change.consume(); dx += amount.x; dy += amount.y }
            )
        }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.DragIndicator, "Hold and drag task", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (task.description.isNotBlank()) Text(task.description, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            task.dueDate?.let { due ->
                Spacer(Modifier.height(8.dp)); SuggestionChip({}, { Text("Due $due") }, icon = { Icon(Icons.Default.Event, null, Modifier.size(16.dp)) })
            }
        }
    }
}

@Composable
private fun ClosedScreen(state: AppUiState, model: KandroidViewModel) {
    var selected by remember { mutableStateOf<TaskEntity?>(null) }
    PullToRefreshBox(state.loading, model::refreshBoard, Modifier.fillMaxSize()) {
        if (state.closedTasks.isEmpty()) EmptyPane("No closed tasks", "Closed tasks from this project appear here.")
        else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.closedTasks.size, key = { state.closedTasks[it].id }) { index ->
                val task = state.closedTasks[index]
                Card({ selected = task }, Modifier.fillMaxWidth()) {
                    ListItem({ Text(task.title) }, supportingContent = { task.dueDate?.let { Text("Due $it") } }, leadingContent = { Icon(Icons.Default.Inventory2, null) })
                }
            }
        }
    }
    selected?.let { task -> ClosedTaskDialog(task, { selected = null }, { model.reopen(task.id); selected = null }, { model.delete(task.id); selected = null }) }
}

@Composable
private fun TaskEditor(task: TaskEntity?, dismiss: () -> Unit, save: (TaskDraft) -> Unit,
    columns: List<ColumnEntity> = emptyList(), onMove: ((Long) -> Unit)? = null,
    onClose: (() -> Unit)? = null, onDelete: (() -> Unit)? = null) {
    var title by rememberSaveable(task?.id) { mutableStateOf(task?.title.orEmpty()) }
    var description by rememberSaveable(task?.id) { mutableStateOf(task?.description.orEmpty()) }
    var due by rememberSaveable(task?.id) { mutableStateOf(task?.dueDate) }
    var confirmDelete by remember { mutableStateOf(false) }
    var moveMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val initial = runCatching { due?.let(LocalDate::parse) }.getOrNull() ?: LocalDate.now()
    AlertDialog(onDismissRequest = dismiss,
        title = { Text(if (task == null) "New task" else "Task details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().semantics { contentDescription = "Task title" }, label = { Text("Title") })
                OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp), label = { Text("Description") }, minLines = 3)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        DatePickerDialog(context, { _, year, month, day -> due = LocalDate.of(year, month + 1, day).toString() },
                            initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
                    }) { Icon(Icons.Default.Event, null); Spacer(Modifier.width(6.dp)); Text(due ?: "Add due date") }
                    if (due != null) IconButton({ due = null }) { Icon(Icons.Default.Close, "Clear due date") }
                }
                if (task != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onMove != null && columns.size > 1) Box {
                        TextButton({ moveMenu = true }) { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null); Text("Move") }
                        DropdownMenu(moveMenu, { moveMenu = false }) {
                            columns.filter { it.id != task.columnId }.forEach { column ->
                                DropdownMenuItem({ Text(column.title) }, { moveMenu = false; onMove(column.id) })
                            }
                        }
                    }
                    onClose?.let { TextButton(it) { Icon(Icons.Default.Archive, null); Text("Close") } }
                    onDelete?.let { TextButton({ confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.Delete, null); Text("Delete") } }
                }
            }
        },
        confirmButton = { Button({ save(TaskDraft(title.trim(), description.trim(), due)) }, enabled = title.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } })
    if (confirmDelete) AlertDialog({ confirmDelete = false }, title = { Text("Delete task permanently?") }, text = { Text("This cannot be undone.") },
        confirmButton = { Button({ confirmDelete = false; onDelete?.invoke() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } })
}

@Composable
private fun ClosedTaskDialog(task: TaskEntity, dismiss: () -> Unit, reopen: () -> Unit, delete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(dismiss, title = { Text(task.title) }, text = { Column { if (task.description.isNotBlank()) Text(task.description); task.dueDate?.let { Text("Due $it") } } },
        confirmButton = { Button(reopen) { Icon(Icons.Default.Unarchive, null); Text("Reopen") } },
        dismissButton = { TextButton({ confirm = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") } })
    if (confirm) AlertDialog({ confirm = false }, title = { Text("Delete task permanently?") }, confirmButton = { Button(delete) { Text("Delete") } }, dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
}

@Composable private fun LoadingPane() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

@Composable private fun EmptyPane(title: String, detail: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.ViewKanban, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp)); Text(title, style = MaterialTheme.typography.titleMedium); Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
