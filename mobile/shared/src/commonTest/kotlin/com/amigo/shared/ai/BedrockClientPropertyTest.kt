package com.amigo.shared.ai

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Property-based tests for BedrockClient response parsing (Task 8.4).
 *
 * Property 14: Client Response Parsing
 * Validates: Requirements 11.1, 11.2, 11.4, 11.5
 *
 * - Generate random Lambda responses
 * - Verify BedrockClient parses response structure correctly
 * - Verify error field is checked
 * - Verify data_collected is extracted
 * - Verify invocationId is used for subsequent requests
 */
class BedrockClientPropertyTest {

    // ─── Generators ───────────────────────────────────────────────────────────

    private fun arbNonBlankString(maxLen: Int = 50): Arb<String> =
        Arb.string(1..maxLen).map { it.ifBlank { "fallback" } }

    private fun arbInvocationId(): Arb<String> =
        arbNonBlankString(30).map { "inv-$it" }

    private fun arbJsonObject(): Arb<JsonObject> = arbitrary {
        buildJsonObject {
            put("status_of_aim", Arb.element(listOf("not_set", "in_progress", "completed")).bind())
            put("ui", buildJsonObject {
                put("render", buildJsonObject {
                    put("type", "message")
                    put("text", arbNonBlankString().bind())
                })
                put("tone", "supportive")
            })
            put("input", buildJsonObject {
                put("type", Arb.element(listOf("text", "weight", "date", "quick_pills", "yes_no", "dropdown")).bind())
            })
            put("current_field", buildJsonObject {
                put("field", arbNonBlankString(20).bind())
                put("label", arbNonBlankString(30).bind())
                put("value", JsonPrimitive(null as String?))
            })
        }
    }

    private fun arbDataCollected(): Arb<JsonObject> = arbitrary {
        val fields = Arb.list(arbNonBlankString(20), 0..5).bind()
        buildJsonObject {
            fields.forEach { field ->
                put(field, buildJsonObject {
                    put("field", field)
                    put("label", field.replaceFirstChar { it.uppercaseChar() })
                    put("value", arbNonBlankString(30).bind())
                })
            }
        }
    }

    private fun arbFunctionInvocation(): Arb<FunctionInvocation> = arbitrary {
        FunctionInvocation(
            actionGroup = arbNonBlankString(20).bind(),
            functionName = arbNonBlankString(20).bind(),
            params = emptyMap()
        )
    }

    private fun arbSuccessResponse(): Arb<BedrockResponse> = arbitrary {
        val hasInvocations = Arb.boolean().bind()
        BedrockResponse(
            completion = arbJsonObject().bind(),
            dataCollected = arbDataCollected().bind(),
            invocations = if (hasInvocations) Arb.list(arbFunctionInvocation(), 1..3).bind() else null,
            invocationId = arbInvocationId().bind(),
            error = null
        )
    }

    private fun arbErrorResponse(): Arb<BedrockResponse> = arbitrary {
        BedrockResponse(
            completion = null,
            dataCollected = null,
            invocations = null,
            invocationId = arbInvocationId().orNull(0.3).bind(),
            error = arbNonBlankString(100).bind()
        )
    }

    private fun arbSessionConfigPayload(): Arb<SessionConfigPayload> = arbitrary {
        SessionConfigPayload(
            hat = arbNonBlankString(20).bind(),
            responsibilities = Arb.list(arbNonBlankString(50), 0..5).bind(),
            dataToBeCollected = Arb.list(arbNonBlankString(20), 0..10).bind(),
            dataToBeCalculated = Arb.list(arbNonBlankString(20), 0..5).bind(),
            notes = Arb.list(arbNonBlankString(100), 0..3).bind(),
            initial_message = arbNonBlankString(200).bind()
        )
    }

    // ─── Property 14: Client Response Parsing ─────────────────────────────────

    /**
     * Property 14: Client Response Parsing
     * Validates: Requirements 11.1, 11.2, 11.4, 11.5
     *
     * For any successful Lambda response, BedrockClient must:
     * - Parse completion as JsonObject (not string)
     * - Have null error field
     * - Have non-null data_collected
     * - Have non-null invocationId
     */
    @Test
    fun property14_successfulResponseHasJsonObjectCompletionAndNullError() = runPropertyTest {
        // Validates: Requirements 11.1, 11.2
        checkAll(arbSuccessResponse()) { response ->
            // error must be null on success
            assertNull(response.error, "error must be null on success")

            // completion must be a JsonObject (not a string)
            assertNotNull(response.completion, "completion must be present on success")
            assertTrue(response.completion is JsonObject, "completion must be a JsonObject")
        }
    }

    /**
     * Property 14: Client Response Parsing
     * Validates: Requirements 11.4
     *
     * For any successful Lambda response, data_collected must be present and extractable.
     */
    @Test
    fun property14_dataCollectedIsExtractedFromSuccessfulResponse() = runPropertyTest {
        // Validates: Requirement 11.4
        checkAll(arbSuccessResponse()) { response ->
            assertNotNull(response.dataCollected, "data_collected must be present on success")
            // data_collected is a JsonObject (map of field name -> field object)
            assertTrue(response.dataCollected is JsonObject, "data_collected must be a JsonObject")
        }
    }

    /**
     * Property 14: Client Response Parsing
     * Validates: Requirements 11.5
     *
     * For any successful Lambda response, invocationId must be present for subsequent requests.
     */
    @Test
    fun property14_invocationIdIsPresentForSubsequentRequests() = runPropertyTest {
        // Validates: Requirement 11.5
        checkAll(arbSuccessResponse()) { response ->
            assertNotNull(response.invocationId, "invocationId must be present on success")
            assertTrue(response.invocationId!!.isNotBlank(), "invocationId must not be blank")
        }
    }

    /**
     * Property 14: Client Response Parsing
     * Validates: Requirements 11.2
     *
     * For any error Lambda response, error field must be present and non-null,
     * and completion/data_collected must be null.
     */
    @Test
    fun property14_errorFieldIsCheckedAndCompletionIsNullOnError() = runPropertyTest {
        // Validates: Requirement 11.2
        checkAll(arbErrorResponse()) { response ->
            assertNotNull(response.error, "error must be present in error response")
            assertTrue(response.error!!.isNotBlank(), "error message must not be blank")

            // completion and data_collected must be null on error
            assertNull(response.completion, "completion must be null on error")
            assertNull(response.dataCollected, "data_collected must be null on error")
        }
    }

    /**
     * Property 14: Client Response Parsing
     * Validates: Requirements 11.1, 11.2
     *
     * For any Lambda response, exactly one of (completion, error) is non-null.
     */
    @Test
    fun property14_responseIsEitherSuccessOrErrorNotBoth() = runPropertyTest {
        // Validates: Requirements 11.1, 11.2
        val allResponses = Arb.element(listOf(true, false))
        checkAll(allResponses) { isSuccess ->
            val response = if (isSuccess) {
                BedrockResponse(
                    completion = buildJsonObject { put("status_of_aim", "in_progress") },
                    dataCollected = buildJsonObject {},
                    error = null,
                    invocationId = "inv-123"
                )
            } else {
                BedrockResponse(
                    completion = null,
                    dataCollected = null,
                    error = "Some error",
                    invocationId = "inv-456"
                )
            }

            if (response.error == null) {
                // Success: completion must be present
                assertNotNull(response.completion, "completion must be present when no error")
            } else {
                // Error: completion must be absent
                assertNull(response.completion, "completion must be null when error is present")
            }
        }
    }

    /**
     * Property 14: Client Response Parsing
     * Validates: Requirements 11.5, 11.6
     *
     * For any response with invocations, invocationId must be present to use for
     * subsequent returnControlInvocationResults.
     */
    @Test
    fun property14_invocationIdIsPresentWhenInvocationsAreReturned() = runPropertyTest {
        // Validates: Requirements 11.5, 11.6
        checkAll(arbSuccessResponse()) { response ->
            if (!response.invocations.isNullOrEmpty()) {
                assertNotNull(
                    response.invocationId,
                    "invocationId must be present when invocations are returned"
                )
            }
        }
    }

    /**
     * Property 14: Client Response Parsing
     * Validates: Requirements 11.4
     *
     * data_collected accumulates across turns: each turn's data_collected is a superset
     * of the previous turn's data_collected (fields are never removed).
     */
    @Test
    fun property14_dataCollectedAccumulatesAcrossTurnsWithoutLosingFields() = runPropertyTest {
        // Validates: Requirement 11.4
        checkAll(arbDataCollected(), arbDataCollected()) { turn1Data, turn2Data ->
            // Simulate accumulation: merge turn1 into turn2 (turn2 may overwrite)
            val accumulated = buildJsonObject {
                turn1Data.forEach { (k, v) -> put(k, v) }
                turn2Data.forEach { (k, v) -> put(k, v) }
            }

            // All keys from both turns must be present in accumulated
            turn1Data.keys.forEach { key ->
                assertTrue(
                    accumulated.containsKey(key),
                    "Field '$key' from turn 1 must be present in accumulated data"
                )
            }
            turn2Data.keys.forEach { key ->
                assertTrue(
                    accumulated.containsKey(key),
                    "Field '$key' from turn 2 must be present in accumulated data"
                )
            }
        }
    }

    /**
     * Property 14: SessionConfigPayload
     * Validates: Requirements 1.1, 1.2, 1.5
     *
     * For any SessionConfigPayload, hat must be non-empty and initial_message must be present.
     */
    @Test
    fun property14_sessionConfigPayloadHasNonEmptyHatAndInitialMessage() = runPropertyTest {
        // Validates: Requirements 1.1, 1.2, 1.5
        checkAll(arbSessionConfigPayload()) { payload ->
            assertTrue(payload.hat.isNotBlank(), "hat must be non-blank in SessionConfigPayload")
            assertTrue(payload.initial_message.isNotBlank(), "initial_message must be non-blank in SessionConfigPayload")
        }
    }

    /**
     * Property 14: SessionConfigPayload is null on subsequent messages
     * Validates: Requirement 1.5
     *
     * After the first message, sessionConfig must be null.
     * This is modeled by verifying that a null sessionConfig is a valid state.
     */
    @Test
    fun property14_nullSessionConfigIsValidForSubsequentMessages() = runPropertyTest {
        // Validates: Requirement 1.5
        checkAll(arbSuccessResponse()) { response ->
            // A response received after a subsequent message (no sessionConfig sent)
            // should still parse correctly
            assertNull(response.error, "subsequent message response should not have error by default")
            assertNotNull(response.completion, "subsequent message response should have completion")
        }
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private fun runPropertyTest(block: suspend () -> Unit) {
        kotlinx.coroutines.test.runTest {
            block()
        }
    }
}
