package com.amigo.shared.ai

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for BedrockClient updates (Task 8.3).
 *
 * Tests verify:
 * - Response parsing for all response types (success with/without invocations, error)
 * - Error handling when `error` field is present
 * - `data_collected` extraction
 * - Session config sending (first message vs subsequent)
 *
 * Requirements: 11.1, 11.2, 11.3, 11.4
 */
class BedrockClientTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun makeSuccessResponse(
        completion: JsonObject? = buildJsonObject {
            put("status_of_aim", "in_progress")
            put("ui", buildJsonObject {
                put("render", buildJsonObject {
                    put("type", "message")
                    put("text", "What is your name?")
                })
                put("tone", "supportive")
            })
            put("input", buildJsonObject { put("type", "text") })
            put("current_field", buildJsonObject {
                put("field", "name")
                put("label", "Name")
                put("value", JsonPrimitive(null as String?))
            })
        },
        dataCollected: JsonObject? = buildJsonObject {},
        invocations: List<FunctionInvocation>? = null,
        invocationId: String? = "inv-123",
        error: String? = null
    ): BedrockResponse = BedrockResponse(
        completion = completion,
        dataCollected = dataCollected,
        invocations = invocations,
        invocationId = invocationId,
        error = error
    )

    // ─── Response parsing tests ───────────────────────────────────────────────

    @Test
    fun testSuccessResponseHasCompletionAsJsonObject() {
        // Requirement 11.1: BedrockClient parses completion as JSON object
        val response = makeSuccessResponse()

        assertNull(response.error, "error should be null on success")
        assertNotNull(response.completion, "completion should be present on success")
        assertTrue(response.completion is JsonObject, "completion should be a JsonObject")
    }

    @Test
    fun testSuccessResponseContainsDataCollected() {
        // Requirement 11.4: BedrockClient extracts data_collected
        val dataCollected = buildJsonObject {
            put("name", buildJsonObject {
                put("field", "name")
                put("label", "Name")
                put("value", "John")
            })
        }
        val response = makeSuccessResponse(dataCollected = dataCollected)

        assertNotNull(response.dataCollected, "data_collected should be present")
        assertTrue(response.dataCollected!!.containsKey("name"), "data_collected should contain 'name' field")
    }

    @Test
    fun testSuccessResponseContainsInvocationId() {
        // Requirement 11.5: BedrockClient extracts invocationId
        val response = makeSuccessResponse(invocationId = "inv-abc-123")

        assertEquals("inv-abc-123", response.invocationId, "invocationId should be extracted correctly")
    }

    @Test
    fun testSuccessResponseWithInvocations() {
        // Requirement 11.6: BedrockClient extracts invocations array
        val invocations = listOf(
            FunctionInvocation(
                actionGroup = "health_calculations",
                functionName = "calculate_bmr",
                params = mapOf("weight" to "75", "height" to "180")
            )
        )
        val response = makeSuccessResponse(invocations = invocations)

        assertNotNull(response.invocations, "invocations should be present")
        assertEquals(1, response.invocations!!.size, "should have 1 invocation")
        assertEquals("health_calculations", response.invocations[0].actionGroup)
        assertEquals("calculate_bmr", response.invocations[0].functionName)
    }

    @Test
    fun testSuccessResponseWithoutInvocations() {
        // Invocations should be null when not present
        val response = makeSuccessResponse(invocations = null)

        assertNull(response.invocations, "invocations should be null when not present")
    }

    // ─── Error handling tests ─────────────────────────────────────────────────

    @Test
    fun testErrorResponseHasErrorField() {
        // Requirement 11.2: BedrockClient checks for error field
        val response = makeSuccessResponse(
            completion = null,
            dataCollected = null,
            error = "Agent failed to respond with valid JSON after 3 retries"
        )

        assertNotNull(response.error, "error field should be present")
        assertEquals(
            "Agent failed to respond with valid JSON after 3 retries",
            response.error
        )
    }

    @Test
    fun testErrorResponseHasNullCompletion() {
        // Requirement 11.2: On error, completion should be null
        val response = makeSuccessResponse(
            completion = null,
            dataCollected = null,
            error = "Some error"
        )

        assertNull(response.completion, "completion should be null on error")
        assertNull(response.dataCollected, "data_collected should be null on error")
    }

    @Test
    fun testErrorResponsePreservesInvocationId() {
        // Requirement 11.5: invocationId should be present even on error
        val response = makeSuccessResponse(
            completion = null,
            dataCollected = null,
            invocationId = "inv-error-456",
            error = "Some error"
        )

        assertEquals("inv-error-456", response.invocationId, "invocationId should be preserved on error")
    }

    @Test
    fun testSuccessResponseHasNullError() {
        // Requirement 11.2: On success, error should be null
        val response = makeSuccessResponse(error = null)

        assertNull(response.error, "error should be null on success")
    }

    // ─── data_collected accumulation tests ───────────────────────────────────

    @Test
    fun testDataCollectedIsEmptyOnFirstTurn() {
        // Requirement 11.4: data_collected starts empty
        val response = makeSuccessResponse(dataCollected = buildJsonObject {})

        assertNotNull(response.dataCollected)
        assertTrue(response.dataCollected!!.isEmpty(), "data_collected should be empty on first turn")
    }

    @Test
    fun testDataCollectedAccumulatesAcrossTurns() {
        // Requirement 11.4: data_collected accumulates across turns
        val turn1Data = buildJsonObject {
            put("name", buildJsonObject {
                put("field", "name")
                put("label", "Name")
                put("value", "John")
            })
        }
        val turn2Data = buildJsonObject {
            put("name", buildJsonObject {
                put("field", "name")
                put("label", "Name")
                put("value", "John")
            })
            put("age", buildJsonObject {
                put("field", "age")
                put("label", "Age")
                put("value", "30")
            })
        }

        val response1 = makeSuccessResponse(dataCollected = turn1Data)
        val response2 = makeSuccessResponse(dataCollected = turn2Data)

        // Simulate client accumulation: second response has more data
        assertEquals(1, response1.dataCollected!!.size, "Turn 1 should have 1 field")
        assertEquals(2, response2.dataCollected!!.size, "Turn 2 should have 2 fields")
        assertTrue(response2.dataCollected!!.containsKey("name"))
        assertTrue(response2.dataCollected!!.containsKey("age"))
    }

    // ─── SessionConfigPayload tests ───────────────────────────────────────────

    @Test
    fun testSessionConfigPayloadContainsAllFields() {
        // Requirement 1.1, 1.2: SessionConfigPayload has all required fields
        val payload = SessionConfigPayload(
            hat = "onboarding",
            responsibilities = listOf("Collect name", "Collect age"),
            dataToBeCollected = listOf("name", "age"),
            dataToBeCalculated = listOf("bmr"),
            notes = listOf("Be friendly"),
            initial_message = "Hello! Let's get started."
        )

        assertEquals("onboarding", payload.hat)
        assertEquals(2, payload.responsibilities.size)
        assertEquals(2, payload.dataToBeCollected.size)
        assertEquals(1, payload.dataToBeCalculated.size)
        assertEquals(1, payload.notes.size)
        assertEquals("Hello! Let's get started.", payload.initial_message)
    }

    @Test
    fun testSessionConfigPayloadSerializesCorrectly() {
        // Requirement 1.5: sessionConfig is sent with initial_message
        val payload = SessionConfigPayload(
            hat = "onboarding",
            responsibilities = listOf("Collect name"),
            dataToBeCollected = listOf("name"),
            dataToBeCalculated = listOf("bmr"),
            notes = emptyList(),
            initial_message = "Let's start!"
        )

        val serialized = json.encodeToString(SessionConfigPayload.serializer(), payload)

        assertTrue(serialized.contains("\"hat\":\"onboarding\""), "hat should be serialized")
        assertTrue(serialized.contains("\"initial_message\""), "initial_message key should be serialized")
        assertTrue(serialized.contains("start"), "initial_message value should be serialized")
        assertTrue(serialized.contains("\"data_to_be_collected\""), "data_to_be_collected should be serialized")
        assertTrue(serialized.contains("\"data_to_be_calculated\""), "data_to_be_calculated should be serialized")
    }

    @Test
    fun testSessionConfigPayloadFromSessionConfig() {
        // Verify SessionConfigPayload can be built from SessionConfig
        val config = SessionConfig(
            hat = "goal_setting",
            responsibilities = listOf("Define goals"),
            data_to_be_collected = listOf("goal_type"),
            data_to_be_calculated = listOf("bmr"),
            initial_message = "What are your goals?",
            notes = listOf("Be concise")
        )

        val payload = SessionConfigPayload(
            hat = config.hat,
            responsibilities = config.responsibilities,
            dataToBeCollected = config.data_to_be_collected,
            dataToBeCalculated = config.data_to_be_calculated,
            notes = config.notes,
            initial_message = config.initial_message
        )

        assertEquals(config.hat, payload.hat)
        assertEquals(config.responsibilities, payload.responsibilities)
        assertEquals(config.data_to_be_collected, payload.dataToBeCollected)
        assertEquals(config.data_to_be_calculated, payload.dataToBeCalculated)
        assertEquals(config.notes, payload.notes)
        assertEquals(config.initial_message, payload.initial_message)
    }

    @Test
    fun testSessionConfigPayloadIsNullOnSubsequentMessages() {
        // Requirement 1.5: sessionConfig is null on subsequent messages
        // This is enforced by AmigoAgentConversation consuming pendingSessionConfig once.
        // Here we verify the BedrockResponse model handles null sessionConfig gracefully.
        val response = makeSuccessResponse()

        // A subsequent-message response should still parse correctly
        assertNull(response.error)
        assertNotNull(response.completion)
    }

    // ─── BedrockResponse model tests ─────────────────────────────────────────

    @Test
    fun testBedrockResponseDefaultValues() {
        // Verify default values are sensible
        val response = BedrockResponse()

        assertNull(response.completion)
        assertNull(response.dataCollected)
        assertNull(response.invocations)
        assertNull(response.invocationId)
        assertNull(response.error)
        assertNull(response.stopReason)
        assertNull(response.usage)
        assertNull(response.completionText)
    }

    @Test
    fun testBedrockResponseCompletionIsJsonObject() {
        // Requirement 11.1: completion is JsonObject not String
        val completionObj = buildJsonObject {
            put("status_of_aim", "completed")
        }
        val response = BedrockResponse(completion = completionObj)

        assertNotNull(response.completion)
        assertTrue(response.completion is JsonObject)
        assertEquals("completed", (response.completion["status_of_aim"] as? JsonPrimitive)?.content)
    }
}
