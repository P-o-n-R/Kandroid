package com.kandroid.app.network

import com.kandroid.app.data.Credentials
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class KanboardApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: KanboardApi

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        api = KanboardApi(Credentials(server.url("/").toString(), "user", "secret"), OkHttpClient(), true)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `serializes json rpc and decodes version`() = runTest {
        server.enqueue(MockResponse().setBody("""{"jsonrpc":"2.0","id":1,"result":"1.2.47"}"""))
        assertEquals("1.2.47", api.version())
        val request = server.takeRequest()
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("getVersion", body.getValue("method").jsonPrimitive.content)
        assertTrue(request.getHeader("Authorization")!!.startsWith("Basic "))
        assertFalse(request.getHeader("Authorization")!!.contains("secret"))
    }

    @Test fun `decodes string ids used by Kanboard`() = runTest {
        server.enqueue(MockResponse().setBody("""{"jsonrpc":"2.0","id":1,"result":[{"id":"4","name":"Board","is_active":"1"}]}"""))
        assertEquals(4L, api.projects().single().entity().id)
    }

    @Test fun `decodes numeric project ids used by newer responses`() = runTest {
        server.enqueue(MockResponse().setBody("""{"jsonrpc":"2.0","id":1,"result":[{"id":4,"name":"Board","is_active":1}]}"""))
        assertEquals(4L, api.projects().single().entity().id)
    }

    @Test fun `getMe accepts the numeric id returned by Kanboard`() = runTest {
        server.enqueue(MockResponse().setBody("""{"jsonrpc":"2.0","id":1,"result":{"id":2,"username":"user","name":""}}"""))
        val user = api.me()
        assertEquals(2L, user.id)
        assertEquals("user", user.username)
    }

    @Test fun `maps http unauthorized to authentication error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        try { api.version(); fail("Expected Authentication") }
        catch (_: KanboardException.Authentication) { }
    }

    @Test fun `maps json rpc error to server error`() = runTest {
        server.enqueue(MockResponse().setBody("""{"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"Invalid params"}}"""))
        try { api.version(); fail("Expected Server") }
        catch (error: KanboardException.Server) { assertEquals(-32602, error.code) }
    }

    @Test fun `maps a false procedure result to a useful server error`() = runTest {
        server.enqueue(MockResponse().setBody("""{"jsonrpc":"2.0","id":1,"result":false}"""))
        try { api.version(); fail("Expected Server") }
        catch (error: KanboardException.Server) { assertTrue(error.message!!.contains("getVersion")) }
    }

    @Test fun `move sends column position and swimlane`() = runTest {
        server.enqueue(MockResponse().setBody("""{"jsonrpc":"2.0","id":1,"result":true}"""))
        assertTrue(api.moveTask(2, 9, 4, 3))
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val params = body.getValue("params").jsonObject
        assertEquals("4", params.getValue("column_id").jsonPrimitive.content)
        assertEquals("3", params.getValue("position").jsonPrimitive.content)
        assertEquals("0", params.getValue("swimlane_id").jsonPrimitive.content)
    }

    @Test fun `creates a project by name`() = runTest {
        server.enqueue(MockResponse().setBody("""{"jsonrpc":"2.0","id":1,"result":"12"}"""))
        assertEquals(12L, api.createProject("Roadmap"))
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("createProject", body.getValue("method").jsonPrimitive.content)
        assertEquals("Roadmap", body.getValue("params").jsonObject.getValue("name").jsonPrimitive.content)
    }

    @Test fun `archives a project without deleting it`() = runTest {
        server.enqueue(MockResponse().setBody("""{"jsonrpc":"2.0","id":1,"result":true}"""))
        assertTrue(api.archiveProject(12))
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("disableProject", body.getValue("method").jsonPrimitive.content)
        assertEquals("12", body.getValue("params").jsonObject.getValue("project_id").jsonPrimitive.content)
    }
}
