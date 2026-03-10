package com.amigo.shared.ai.actions

import com.amigo.shared.ai.FunctionInvocation
import io.github.jan.supabase.SupabaseClient
import kotlinx.serialization.json.JsonObject

/**
 * Registry for Bedrock Agent action groups and their functions
 * Maps action group names and function names to their implementations
 */
object ActionGroupRegistry {
    
    private val actionGroups = mutableMapOf<String, ActionGroup>()
    
    init {
        // Register all action groups
        registerActionGroup(DataOperationsActionGroup)
        registerActionGroup(HealthCalculationsActionGroup)
    }
    
    private fun registerActionGroup(actionGroup: ActionGroup) {
        actionGroups[actionGroup.name] = actionGroup
    }
    
    /**
     * Execute a function invocation from Bedrock Agent
     */
    suspend fun executeInvocation(
        invocation: FunctionInvocation,
        context: ActionContext
    ): Result<JsonObject> {
        val actionGroup = actionGroups[invocation.actionGroup]
            ?: return Result.failure(
                IllegalArgumentException("Unknown action group: ${invocation.actionGroup}")
            )
        
        return actionGroup.executeFunction(invocation.functionName, invocation.params, context)
    }
    
    /**
     * Get all registered action groups
     */
    fun getActionGroups(): List<ActionGroup> = actionGroups.values.toList()
    
    /**
     * Get a specific action group by name
     */
    fun getActionGroup(name: String): ActionGroup? = actionGroups[name]
}

/**
 * Base interface for action groups
 */
interface ActionGroup {
    val name: String
    val description: String
    val functions: List<ActionFunction>
    
    suspend fun executeFunction(
        functionName: String,
        params: Map<String, String>,
        context: ActionContext
    ): Result<JsonObject>
}

/**
 * Metadata for an action function
 */
data class ActionFunction(
    val name: String,
    val description: String,
    val parameters: List<ActionParameter>
)

/**
 * Parameter definition for an action function
 */
data class ActionParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = false
)

/**
 * Context passed to action functions
 */
data class ActionContext(
    val userId: String?,
    val sessionId: String,
    val isAuthenticated: Boolean,
    val supabaseClient: SupabaseClient? = null,
    val additionalContext: Map<String, Any> = emptyMap()
)
