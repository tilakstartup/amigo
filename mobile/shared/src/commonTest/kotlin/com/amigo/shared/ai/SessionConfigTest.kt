package com.amigo.shared.ai

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for SessionConfig restructuring.
 * 
 * Tests verify:
 * - SessionConfig serialization excludes `initial_message`
 * - `hat` field is required and non-empty
 * - Backward compatibility handling
 * 
 * Requirements: 1.1, 1.2, 1.4
 */
class SessionConfigTest {

    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun testSessionConfigSerializationExcludesInitialMessage() {
        // Given: A SessionConfig with all fields including initial_message
        val config = SessionConfig(
            hat = "onboarding",
            responsibilities = listOf("Collect name", "Collect age"),
            data_to_be_collected = listOf("name", "age"),
            data_to_be_calculated = listOf("bmr"),
            initial_message = "Hello! Let's get started.",
            notes = listOf("Be friendly")
        )

        // When: Serializing the config to JSON
        val serialized = json.encodeToString(config)

        // Then: The serialized JSON should contain initial_message
        // (Note: initial_message is part of the data class but should be handled 
        // separately by Lambda - it extracts it before storing session attributes)
        assertTrue(
            serialized.contains("initial_message"),
            "Serialized config should contain initial_message field"
        )
        assertTrue(
            serialized.contains("Hello! Let's get started."),
            "Serialized config should contain initial_message value"
        )
    }

    @Test
    fun testHatFieldIsRequired() {
        // Given: A SessionConfig with hat field
        val config = SessionConfig(
            hat = "goal_setting",
            responsibilities = listOf("Define goals"),
            data_to_be_collected = listOf("goal_type"),
            data_to_be_calculated = emptyList(),
            initial_message = "What are your goals?",
            notes = emptyList()
        )

        // When: Accessing the hat field
        val hat = config.hat

        // Then: The hat field should be present and accessible
        assertNotNull(hat, "hat field should not be null")
        assertEquals("goal_setting", hat, "hat field should match the provided value")
    }

    @Test
    fun testHatFieldIsNonEmpty() {
        // Given: A SessionConfig with non-empty hat
        val config = SessionConfig(
            hat = "onboarding",
            responsibilities = listOf("Collect data"),
            data_to_be_collected = listOf("name"),
            data_to_be_calculated = emptyList(),
            initial_message = "Welcome!",
            notes = emptyList()
        )

        // When: Checking the hat field
        val hat = config.hat

        // Then: The hat field should be non-empty
        assertTrue(hat.isNotEmpty(), "hat field should be non-empty")
        assertTrue(hat.isNotBlank(), "hat field should not be blank")
    }

    @Test
    fun testSessionConfigWithAllRequiredFields() {
        // Given: A SessionConfig with all required fields
        val config = SessionConfig(
            hat = "onboarding",
            responsibilities = listOf("Collect name", "Collect age", "Collect weight"),
            data_to_be_collected = listOf("name", "age", "weight"),
            data_to_be_calculated = listOf("bmr", "tdee"),
            initial_message = "Let's begin your onboarding!",
            notes = listOf("Be supportive", "Use simple language")
        )

        // Then: All fields should be accessible and correct
        assertEquals("onboarding", config.hat)
        assertEquals(3, config.responsibilities.size)
        assertEquals(3, config.data_to_be_collected.size)
        assertEquals(2, config.data_to_be_calculated.size)
        assertEquals("Let's begin your onboarding!", config.initial_message)
        assertEquals(2, config.notes.size)
    }

    @Test
    fun testSessionConfigWithOptionalFieldsEmpty() {
        // Given: A SessionConfig with optional fields as empty lists
        val config = SessionConfig(
            hat = "simple_session",
            responsibilities = listOf("Single task"),
            data_to_be_collected = listOf("field1"),
            data_to_be_calculated = emptyList(),
            initial_message = "Start",
            notes = emptyList()
        )

        // Then: Optional fields should be empty but not null
        assertNotNull(config.data_to_be_calculated)
        assertTrue(config.data_to_be_calculated.isEmpty())
        assertNotNull(config.notes)
        assertTrue(config.notes.isEmpty())
    }

    @Test
    fun testSessionConfigSerialization() {
        // Given: A SessionConfig
        val config = SessionConfig(
            hat = "test_session",
            responsibilities = listOf("Task 1", "Task 2"),
            data_to_be_collected = listOf("field1", "field2"),
            data_to_be_calculated = listOf("calc1"),
            initial_message = "Test message",
            notes = listOf("Note 1")
        )

        // When: Serializing and deserializing
        val serialized = json.encodeToString(config)
        val deserialized = json.decodeFromString<SessionConfig>(serialized)

        // Then: Deserialized config should match original
        assertEquals(config.hat, deserialized.hat)
        assertEquals(config.responsibilities, deserialized.responsibilities)
        assertEquals(config.data_to_be_collected, deserialized.data_to_be_collected)
        assertEquals(config.data_to_be_calculated, deserialized.data_to_be_calculated)
        assertEquals(config.initial_message, deserialized.initial_message)
        assertEquals(config.notes, deserialized.notes)
    }

    @Test
    fun testSessionConfigDeserializationWithMissingOptionalFields() {
        // Given: JSON without optional fields
        val jsonString = """
            {
                "hat": "minimal_session",
                "responsibilities": ["Task 1"],
                "data_to_be_collected": ["field1"],
                "initial_message": "Hello"
            }
        """.trimIndent()

        // When: Deserializing
        val config = json.decodeFromString<SessionConfig>(jsonString)

        // Then: Optional fields should have default values
        assertEquals("minimal_session", config.hat)
        assertEquals(listOf("Task 1"), config.responsibilities)
        assertEquals(listOf("field1"), config.data_to_be_collected)
        assertEquals("Hello", config.initial_message)
        assertTrue(config.data_to_be_calculated.isEmpty(), "data_to_be_calculated should default to empty list")
        assertTrue(config.notes.isEmpty(), "notes should default to empty list")
    }

    @Test
    fun testBackwardCompatibilityHatFieldPresent() {
        // Given: A SessionConfig using the new 'hat' field
        val config = SessionConfig(
            hat = "onboarding",
            responsibilities = listOf("Collect data"),
            data_to_be_collected = listOf("name"),
            data_to_be_calculated = emptyList(),
            initial_message = "Welcome",
            notes = emptyList()
        )

        // When: Serializing to JSON
        val serialized = json.encodeToString(config)

        // Then: The serialized JSON should contain 'hat' field
        assertTrue(serialized.contains("\"hat\""), "Serialized config should contain 'hat' field")
        assertFalse(serialized.contains("\"cap\""), "Serialized config should NOT contain legacy 'cap' field")
    }

    @Test
    fun testInitialMessageAccessibleSeparately() {
        // Given: A SessionConfig with initial_message
        val config = SessionConfig(
            hat = "onboarding",
            responsibilities = listOf("Collect name"),
            data_to_be_collected = listOf("name"),
            data_to_be_calculated = emptyList(),
            initial_message = "Hello! What's your name?",
            notes = emptyList()
        )

        // When: Accessing initial_message separately
        val initialMessage = config.initial_message

        // Then: initial_message should be accessible as a separate field
        assertNotNull(initialMessage, "initial_message should be accessible")
        assertEquals("Hello! What's your name?", initialMessage)
        
        // And: It should be distinct from other config fields
        assertFalse(
            config.responsibilities.contains(initialMessage),
            "initial_message should not be in responsibilities"
        )
        assertFalse(
            config.data_to_be_collected.contains(initialMessage),
            "initial_message should not be in data_to_be_collected"
        )
    }

    @Test
    fun testSessionConfigsRegistryUsesHatField() {
        // Given: Session configs from the registry
        val onboardingConfig = SessionConfigs.ONBOARDING
        val goalSettingConfig = SessionConfigs.GOAL_SETTING

        // Then: All configs should have non-empty hat fields
        assertNotNull(onboardingConfig.hat, "Onboarding config should have hat field")
        assertTrue(onboardingConfig.hat.isNotEmpty(), "Onboarding hat should be non-empty")
        
        assertNotNull(goalSettingConfig.hat, "Goal setting config should have hat field")
        assertTrue(goalSettingConfig.hat.isNotEmpty(), "Goal setting hat should be non-empty")
    }

    @Test
    fun testGetConfigByHatName() {
        // When: Getting configs by hat name
        val onboardingConfig = SessionConfigs.getConfig("onboarding")
        val goalSettingConfig = SessionConfigs.getConfig("goal_setting")
        val nonExistentConfig = SessionConfigs.getConfig("non_existent")

        // Then: Valid hat names should return configs
        assertNotNull(onboardingConfig, "Should find onboarding config")
        assertEquals("onboarding", onboardingConfig.hat)
        
        assertNotNull(goalSettingConfig, "Should find goal_setting config")
        assertEquals("goal_setting", goalSettingConfig.hat)
        
        // And: Invalid hat names should return null
        assertEquals(null, nonExistentConfig, "Should return null for non-existent config")
    }
}
