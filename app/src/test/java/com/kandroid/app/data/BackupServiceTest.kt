package com.kandroid.app.data

import java.lang.reflect.Proxy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupServiceTest {
    private val service = BackupService(Proxy.newProxyInstance(
        KandroidDao::class.java.classLoader, arrayOf(KandroidDao::class.java)
    ) { _, _, _ -> null } as KandroidDao)
    private val json = Json { encodeDefaults = true }

    private fun valid() = BackupEnvelope(
        exportedAt = "2026-08-12T12:00:00Z",
        sourceMode = AppMode.KANBOARD.name,
        projects = listOf(BackupProject(1, "Project")),
        columns = listOf(BackupColumn(2, 1, "Backlog", 1)),
        tasks = listOf(BackupTask(3, 1, 2, "Task", "Details", "2026-08-20", 1, true, 123))
    )

    @Test fun parsesSupportedPortableBackup() {
        val parsed = service.parseAndValidate(json.encodeToString(valid()))
        assertEquals("Task", parsed.tasks.single().title)
        assertEquals(AppMode.KANBOARD.name, parsed.sourceMode)
    }

    @Test fun rejectsUnsupportedVersion() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            service.parseAndValidate(json.encodeToString(valid().copy(schemaVersion = 2)))
        }
        assert(error.message!!.contains("not supported"))
    }

    @Test fun rejectsDuplicateAndBrokenReferences() {
        assertThrows(IllegalArgumentException::class.java) {
            service.parseAndValidate(json.encodeToString(valid().copy(
                columns = listOf(BackupColumn(2, 1, "One", 1), BackupColumn(2, 1, "Two", 2))
            )))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.parseAndValidate(json.encodeToString(valid().copy(tasks = listOf(valid().tasks.single().copy(columnId = 99)))))
        }
    }

    @Test fun rejectsInvalidDueDateWithoutChangingData() {
        assertThrows(IllegalArgumentException::class.java) {
            service.parseAndValidate(json.encodeToString(valid().copy(tasks = listOf(valid().tasks.single().copy(dueDate = "12/08/2026")))))
        }
    }

    @Test fun acceptsEmptyWorkspace() {
        val parsed = service.parseAndValidate(json.encodeToString(valid().copy(projects = emptyList(), columns = emptyList(), tasks = emptyList())))
        assert(parsed.projects.isEmpty())
    }
}
