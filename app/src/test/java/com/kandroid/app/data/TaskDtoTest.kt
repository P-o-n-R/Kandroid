package com.kandroid.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskDtoTest {
    @Test fun `maps epoch due date to date only`() {
        val dto = TaskDto("7", "Ship", projectId = "2", columnId = "3", dateDue = "1786492800")
        assertEquals("2026-08-12", dto.entity().dueDate)
    }

    @Test fun `maps zero due date to null`() {
        val dto = TaskDto("7", "Ship", projectId = "2", columnId = "3", dateDue = "0")
        assertNull(dto.entity().dueDate)
    }
}

