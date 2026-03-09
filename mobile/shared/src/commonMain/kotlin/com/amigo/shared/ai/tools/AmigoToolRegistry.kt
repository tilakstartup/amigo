package com.amigo.shared.ai.tools

import com.amigo.shared.utils.Logger

/**
 * Registry for managing Amigo tools
 * Allows registration, discovery, and execution of tools
 */
class AmigoToolRegistry {
    private val tools = mutableMapOf<String, AmigoTool>()
    
    /**
     * Register a tool
     */
    fun registerTool(tool: AmigoTool) {
        tools[tool.name] = tool
        Logger.d("AmigoToolRegistry", "Registered tool: ${tool.name}")
    }
    
    /**
     * Get a tool by name
     */
    fun getTool(name: String): AmigoTool? {
        return tools[name]
    }
    
    /**
     * Get all registered tools
     */
    fun getAllTools(): List<AmigoTool> {
        return tools.values.toList()
    }
    
    /**
     * Execute a tool by name with parameters
     */
    suspend fun executeTool(name: String, params: Map<String, Any>): ToolResult {
        val tool = tools[name]
        if (tool == null) {
            Logger.e("AmigoToolRegistry", "Tool not found: $name")
            return ToolResult.Error("Tool not found: $name", "TOOL_NOT_FOUND")
        }
        
        return try {
            Logger.d("AmigoToolRegistry", "Executing tool: $name with params: $params")
            tool.execute(params)
        } catch (e: Exception) {
            Logger.e("AmigoToolRegistry", "Tool execution failed: ${e.message}")
            ToolResult.Error("Tool execution failed: ${e.message}", "EXECUTION_ERROR")
        }
    }
    
    /**
     * Get tool definitions in a format suitable for AI prompts
     */
    fun getToolDefinitionsForAI(): String {
        return buildString {
            appendLine("Available Tools:")
            appendLine()
            tools.values.forEach { tool ->
                appendLine("Tool: ${tool.name}")
                appendLine("Description: ${tool.description}")
                appendLine("Parameters:")
                tool.parameters.values.forEach { param ->
                    val requiredStr = if (param.required) "required" else "optional"
                    appendLine("  - ${param.name} (${param.type}, $requiredStr): ${param.description}")
                }
                appendLine()
            }
        }
    }
    
    /**
     * Get tool definitions in JSON format for Claude tool use
     */
    fun getToolDefinitionsForClaude(): List<Map<String, Any>> {
        return tools.values.map { tool ->
            mapOf(
                "name" to tool.name,
                "description" to tool.description,
                "input_schema" to mapOf(
                    "type" to "object",
                    "properties" to tool.parameters.mapValues { (_, param) ->
                        mapOf(
                            "type" to when (param.type) {
                                ParameterType.STRING, ParameterType.DATE -> "string"
                                ParameterType.NUMBER -> "number"
                                ParameterType.BOOLEAN -> "boolean"
                                ParameterType.ARRAY -> "array"
                            },
                            "description" to param.description
                        )
                    },
                    "required" to tool.parameters.values.filter { it.required }.map { it.name }
                )
            )
        }
    }
}
