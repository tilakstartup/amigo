package com.amigo.shared.ai.tools

import com.amigo.shared.ai.tools.impl.*
import com.amigo.shared.data.repositories.MealLogRepository
import com.amigo.shared.goals.GoalCalculationEngine
import com.amigo.shared.profile.ProfileManager

/**
 * Factory for creating and registering Amigo tools
 */
object AmigoToolFactory {
    /**
     * Create a tool registry with all available tools
     */
    fun createRegistry(
        profileManager: ProfileManager,
        userId: String,
        calculationEngine: GoalCalculationEngine,
        mealLogRepository: MealLogRepository? = null
    ): AmigoToolRegistry {
        val registry = AmigoToolRegistry()
        
        // Register profile tools
        registry.registerTool(GetUserProfileTool(profileManager, userId))
        registry.registerTool(GetCurrentGoalTool(profileManager, userId))
        registry.registerTool(SaveGoalTool(profileManager, userId))
        
        // Register calculation tools
        registry.registerTool(CalculateBMITool(calculationEngine))
        registry.registerTool(CalculateBMRTool(calculationEngine))
        registry.registerTool(CalculateTDEETool(calculationEngine))
        registry.registerTool(ValidateGoalTool(calculationEngine))
        
        // Register meal logging tools if repository is provided
        if (mealLogRepository != null) {
            registry.registerTool(GetRecentMealsTool(mealLogRepository, userId))
        }
        
        return registry
    }
    
    /**
     * Create a registry with only goal-related tools
     */
    fun createGoalPlanningRegistry(
        profileManager: ProfileManager,
        userId: String,
        calculationEngine: GoalCalculationEngine
    ): AmigoToolRegistry {
        val registry = AmigoToolRegistry()
        
        // Register only tools needed for goal planning
        registry.registerTool(GetUserProfileTool(profileManager, userId))
        registry.registerTool(GetCurrentGoalTool(profileManager, userId))
        registry.registerTool(SaveGoalTool(profileManager, userId))
        registry.registerTool(CalculateBMITool(calculationEngine))
        registry.registerTool(CalculateBMRTool(calculationEngine))
        registry.registerTool(CalculateTDEETool(calculationEngine))
        registry.registerTool(ValidateGoalTool(calculationEngine))
        
        return registry
    }
}
