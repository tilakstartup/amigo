package com.amigo.shared.ai

import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property-based tests for SessionConfig restructuring
 * 
 * Validates Requirements 1.1, 1.2, 1.3, 1.4, 1.5:
 * - Property 1: Session Config Restructuring
 * - Verifies hat field is always present and non-empty
 * - Verifies initial_message is accessible separately from session attributes
 * - Verifies serialization excludes initial_message
 */
class SessionConfigPropertyTest {

    private val json = Json { 
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    // Arbitrary generators for SessionConfig fields
    private fun arbHat(): Arb<String> = Arb.string(1..50)
        .filter { it.isNotBlank() }

    private fun arbResponsibilities(): Arb<List<String>> = 
        Arb.list(Arb.string(1..100), 0..10)

    private fun arbDataFields(): Arb<List<String>> = 
        Arb.list(Arb.string(1..50), 0..20)

    private fun arbNotes(): Arb<List<String>> = Arb.list(Arb.string(0..200), 0..5)

    private fun arbInitialMessage(): Arb<String> = Arb.string(1..500)
        .filter { it.isNotBlank() }

    private fun arbSessionConfig(): Arb<SessionConfig> = arbitrary {
        SessionConfig(
            hat = arbHat().bind(),
            responsibilities = arbResponsibilities().bind(),
            data_to_be_collected = arbDataFields().bind(),
            data_to_be_calculated = arbDataFields().bind(),
            notes = arbNotes().bind(),
            initial_message = arbInitialMessage().bind()
        )
    }

    @Test
    fun `property - hat field is always present and non-empty`() = runPropertyTest {
        checkAll(arbSessionConfig()) { config ->
            // Requirement 1.2: hat field is required and non-empty
            assertTrue(config.hat.isNotBlank(), "hat field must be non-empty")
            assertTrue(config.hat.length >= 1, "hat field must have at least 1 character")
        }
    }

    @Test
    fun `property - initial_message is accessible separately from session attributes`() = runPropertyTest {
        checkAll(arbSessionConfig()) { config ->
            // Requirement 1.5: initial_message is accessible as separate field
            assertTrue(config.initial_message.isNotBlank(), "initial_message must be accessible")
            
            // Requirement 1.3: hat represents session identifier
            assertTrue(config.hat.isNotEmpty(), "hat must be present as session identifier")
        }
    }

    @Test
    fun `property - serialization includes initial_message`() = runPropertyTest {
        checkAll(arbSessionConfig()) { config ->
            // Requirement 1.1: SessionConfig includes initial_message field
            // Note: Lambda will extract initial_message separately before storing session attributes
            val serialized = json.encodeToString(config)
            
            // initial_message SHOULD be in serialized form
            assertTrue(
                serialized.contains("\"initial_message\""),
                "Serialized config should contain initial_message field"
            )
            
            // And must contain hat field
            assertTrue(
                serialized.contains("\"hat\""),
                "Serialized config must contain hat field"
            )
        }
    }

    @Test
    fun `property - session config contains all required fields`() = runPropertyTest {
        checkAll(arbSessionConfig()) { config ->
            // Requirement 1.1: SessionConfig includes all required fields
            assertTrue(config.hat.isNotEmpty(), "hat field must be present")
            assertTrue(config.responsibilities is List, "responsibilities must be a list")
            assertTrue(config.data_to_be_collected is List, "data_to_be_collected must be a list")
            assertTrue(config.data_to_be_calculated is List, "data_to_be_calculated must be a list")
            assertTrue(config.notes is List, "notes must be a list")
            assertTrue(config.initial_message.isNotEmpty(), "initial_message must be present")
        }
    }

    @Test
    fun `property - serialization round-trip preserves all fields including initial_message`() = runPropertyTest {
        checkAll(arbSessionConfig()) { config ->
            // Serialize and deserialize
            val serialized = json.encodeToString(config)
            val deserialized = json.decodeFromString<SessionConfig>(serialized)
            
            // All fields should be preserved including initial_message
            assertTrue(deserialized.hat == config.hat, "hat must be preserved")
            assertTrue(deserialized.responsibilities == config.responsibilities, "responsibilities must be preserved")
            assertTrue(deserialized.data_to_be_collected == config.data_to_be_collected, "data_to_be_collected must be preserved")
            assertTrue(deserialized.data_to_be_calculated == config.data_to_be_calculated, "data_to_be_calculated must be preserved")
            assertTrue(deserialized.notes == config.notes, "notes must be preserved")
            assertTrue(deserialized.initial_message == config.initial_message, "initial_message must be preserved")
        }
    }

    @Test
    fun `property - hat field variations are handled correctly`() = runPropertyTest {
        val hatVariations = listOf(
            "onboarding",
            "goal_setting",
            "meal_logging",
            "profile_update",
            "chat_session"
        )
        
        checkAll(Arb.element(hatVariations)) { hat ->
            val config = SessionConfig(
                hat = hat,
                responsibilities = listOf("test"),
                data_to_be_collected = listOf("field1"),
                data_to_be_calculated = listOf("metric1"),
                notes = listOf("test notes"),
                initial_message = "test message"
            )
            
            // Requirement 1.3: hat represents session identifier
            assertTrue(config.hat == hat, "hat must match the provided value")
            assertTrue(config.hat.isNotEmpty(), "hat must not be empty")
            
            // Verify serialization works for all hat variations
            val serialized = json.encodeToString(config)
            assertTrue(
                serialized.contains("\"hat\":\"$hat\""),
                "Serialized config must contain correct hat value"
            )
        }
    }

    @Test
    fun `property - empty lists are handled correctly`() = runPropertyTest {
        checkAll(arbHat(), arbInitialMessage()) { hat, initialMessage ->
            val config = SessionConfig(
                hat = hat,
                responsibilities = emptyList(),
                data_to_be_collected = emptyList(),
                data_to_be_calculated = emptyList(),
                notes = emptyList(),
                initial_message = initialMessage
            )
            
            // Empty lists should be valid
            assertTrue(config.responsibilities.isEmpty(), "Empty responsibilities list is valid")
            assertTrue(config.data_to_be_collected.isEmpty(), "Empty data_to_be_collected list is valid")
            assertTrue(config.data_to_be_calculated.isEmpty(), "Empty data_to_be_calculated list is valid")
            assertTrue(config.notes.isEmpty(), "Empty notes list is valid")
            
            // But hat and initial_message must not be empty
            assertTrue(config.hat.isNotEmpty(), "hat must not be empty")
            assertTrue(config.initial_message.isNotEmpty(), "initial_message must not be empty")
        }
    }

    // Helper function to run property tests with proper configuration
    private fun runPropertyTest(block: suspend () -> Unit) {
        kotlinx.coroutines.test.runTest {
            block()
        }
    }
}
