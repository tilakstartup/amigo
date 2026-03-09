package com.amigo.shared.ai.conversation

import kotlinx.serialization.Serializable

/**
 * Refined response structure following explicit JSON schema
 */
@Serializable
data class SessionAmigoResponse(
    val message: String,
    val takesInput: Boolean = true,
    val inputType: InputType = InputType.TEXT,
    val options: List<String> = emptyList(),
    val toolInvocation: Boolean = false,
    val toolInvocationDetails: ToolInvocationDetails? = null,
    val dataCollected: Map<String, String> = emptyMap(),
    val nextAction: NextAction = NextAction.USER_INPUT
) {
    @Serializable
    enum class InputType {
        TEXT,
        QUICK_PILLS,
        YES_NO,
        DROPDOWN,
        DATE,
        NUMBER,
        NONE
    }

    @Serializable
    enum class NextAction {
        USER_INPUT,
        TOOL_INVOCATION,
        CONFIRMATION,
        COMPLETED
    }
}

/**
 * Tool invocation details
 */
@Serializable
data class ToolInvocationDetails(
    val toolName: String,
    val params: Map<String, String>
)

/**
 * Metadata about a data field that can be collected
 */
data class CollectableField(
    val fieldName: String,
    val description: String,
    val required: Boolean = true,
    val inputType: SessionAmigoResponse.InputType = SessionAmigoResponse.InputType.TEXT,
    val options: List<String> = emptyList(),
    val pattern: String? = null,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val depends_on: List<String> = emptyList(),
    val priority: Int = 100
)

/**
 * Metadata about a tool
 */
data class ToolMetadata(
    val toolName: String,
    val description: String,
    val parameters: Map<String, ToolParameterMetadata>,
    val provides: List<String> // Output field names this tool provides
)

/**
 * Parameter metadata for a tool
 */
data class ToolParameterMetadata(
    val name: String,
    val type: String, // "number", "string", "boolean"
    val description: String,
    val required: Boolean,
    val sourceField: String? = null // If null, must be asked; if set, auto-resolved from this field
)

/**
 * Session state for a conversation
 */
data class ConversationSession(
    val sessionId: String,
    val contextId: String,
    val collectedData: Map<String, String> = emptyMap(),
    val pendingFields: List<String> = emptyList(),
    val calculatedFields: Map<String, String> = emptyMap(),
    val isComplete: Boolean = false,
    val toolHistory: List<ToolExecution> = emptyList()
)

/**
 * Record of a tool execution
 */
data class ToolExecution(
    val toolName: String,
    val params: Map<String, String>,
    val result: Map<String, Any>,
    val timestamp: Long
)
