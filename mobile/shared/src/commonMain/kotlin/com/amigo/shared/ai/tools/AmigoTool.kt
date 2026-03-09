package com.amigo.shared.ai.tools

import kotlinx.serialization.Serializable

/**
 * Base interface for Amigo tools that can be invoked by AI
 */
interface AmigoTool {
    /**
     * Unique identifier for the tool
     */
    val name: String
    
    /**
     * Human-readable description of what the tool does
     */
    val description: String
    
    /**
     * Map of parameter names to their definitions
     */
    val parameters: Map<String, ToolParameter>
    
    /**
     * Execute the tool with given parameters
     * @param params Map of parameter names to values
     * @return ToolResult with success or error
     */
    suspend fun execute(params: Map<String, Any>): ToolResult
}

/**
 * Parameter definition for a tool
 */
@Serializable
data class ToolParameter(
    val name: String,
    val type: ParameterType,
    val description: String,
    val required: Boolean = true,
    val defaultValue: String? = null
)

/**
 * Parameter types supported by tools
 */
@Serializable
enum class ParameterType {
    STRING,
    NUMBER,
    BOOLEAN,
    DATE,
    ARRAY
}

/**
 * Result of tool execution
 */
sealed class ToolResult {
    data class Success(val data: Map<String, Any>) : ToolResult()
    data class Error(val message: String, val code: String? = null) : ToolResult()
}
