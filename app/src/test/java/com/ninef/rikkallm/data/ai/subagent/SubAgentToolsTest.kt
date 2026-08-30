package com.ninef.rikkallm.data.ai.subagent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessagePart
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubAgentToolsTest {

    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun manager(executor: SubAgentExecutor = SubAgentExecutor { delay(50); "sub result" }) =
        SubAgentManager(executor = executor, appScope = scope)

    private fun tools(manager: SubAgentManager): List<Tool> = buildSubAgentTools(
        manager = manager,
        model = Model(),
        provider = ProviderSetting.OpenAI(),
    )

    private fun toolNamed(tools: List<Tool>, name: String): Tool = tools.first { it.name == name }

    private fun textOutput(output: List<UIMessagePart>): String =
        (output.single() as UIMessagePart.Text).text

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `builds exactly three tools`() {
        val names = tools(manager()).map { it.name }.toSet()
        assertEquals(
            setOf(SUBAGENT_START_TOOL_NAME, SUBAGENT_LIST_TOOL_NAME, SUBAGENT_RESULT_TOOL_NAME),
            names,
        )
    }

    @Test
    fun `start returns run id and running status`() {
        val manager = manager()
        val start = toolNamed(tools(manager), SUBAGENT_START_TOOL_NAME)
        val payload = runBlocking {
            parse(textOutput(start.execute(buildJsonObject {
                put("role", "researcher")
                put("task", "analyze the codebase")
            })))
        }
        assertEquals("running", payload["status"]!!.jsonPrimitive.content)
        assertTrue(payload["run_id"]!!.jsonPrimitive.content.startsWith("sa_"))
    }

    @Test
    fun `start with unknown role returns error instead of throwing`() {
        val start = toolNamed(tools(manager()), SUBAGENT_START_TOOL_NAME)
        val payload = runBlocking {
            parse(textOutput(start.execute(buildJsonObject {
                put("role", "unknown_role")
                put("task", "do something")
            })))
        }
        assertTrue(payload["error"]!!.jsonPrimitive.content.contains("Unknown role"))
    }

    @Test
    fun `start accepts valid tool profile enum`() {
        val manager = manager()
        val start = toolNamed(tools(manager), SUBAGENT_START_TOOL_NAME)
        val payload = runBlocking {
            parse(textOutput(start.execute(buildJsonObject {
                put("role", "researcher")
                put("task", "t")
                put("tool_profile", "web_read")
            })))
        }
        assertEquals("running", payload["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `start with invalid tool profile returns error`() {
        val start = toolNamed(tools(manager()), SUBAGENT_START_TOOL_NAME)
        val payload = runBlocking {
            parse(textOutput(start.execute(buildJsonObject {
                put("role", "researcher")
                put("task", "t")
                put("tool_profile", "bogus_profile")
            })))
        }
        assertTrue(payload["error"]!!.jsonPrimitive.content.contains("Unknown tool_profile"))
    }

    @Test
    fun `result returns output after completion`() = runBlocking {
        val manager = manager(SubAgentExecutor { "final answer" })
        val start = toolNamed(tools(manager), SUBAGENT_START_TOOL_NAME)
        val startPayload = parse(textOutput(start.execute(buildJsonObject {
            put("role", "researcher")
            put("task", "t")
        })))
        val runId = startPayload["run_id"]!!.jsonPrimitive.content

        val resultTool = toolNamed(tools(manager), SUBAGENT_RESULT_TOOL_NAME)
        var result = fetchResult(runId, resultTool)
        var attempts = 0
        while (result["status"]?.jsonPrimitive?.content == "running" && attempts < 100) {
            delay(20)
            result = fetchResult(runId, resultTool)
            attempts++
        }
        assertEquals("completed", result["status"]!!.jsonPrimitive.content)
        assertEquals("final answer", result["output"]!!.jsonPrimitive.content)
    }

    private suspend fun fetchResult(runId: String, resultTool: Tool): JsonObject =
        parse(textOutput(resultTool.execute(buildJsonObject { put("run_id", runId) })))

    @Test
    fun `result for unknown run returns error`() {
        val resultTool = toolNamed(tools(manager()), SUBAGENT_RESULT_TOOL_NAME)
        val payload = runBlocking {
            parse(textOutput(resultTool.execute(buildJsonObject { put("run_id", "nope") })))
        }
        assertTrue(payload["error"]!!.jsonPrimitive.content.contains("not found"))
    }

    @Test
    fun `list returns run records`() {
        val manager = manager(SubAgentExecutor { delay(20); "x" })
        val start = toolNamed(tools(manager), SUBAGENT_START_TOOL_NAME)
        runBlocking {
            start.execute(buildJsonObject {
                put("role", "researcher")
                put("task", "t")
            })
        }
        val listTool = toolNamed(tools(manager), SUBAGENT_LIST_TOOL_NAME)
        val payload = runBlocking {
            parse(textOutput(listTool.execute(JsonObject(emptyMap()))))
        }
        assertTrue(payload["runs"]!!.jsonArray.size >= 1)
        val first = payload["runs"]!!.jsonArray.first().jsonObject
        assertTrue(first["run_id"]!!.jsonPrimitive.content.startsWith("sa_"))
    }
}
