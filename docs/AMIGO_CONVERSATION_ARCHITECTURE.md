# Amigo Base Conversation Engine & Tool System

## Summary

Created a modular, reusable conversation engine architecture that allows Amigo to have context-specific conversations throughout the app using a consistent JSON response format and tool invocation system.

## What Was Implemented

### 1. **Base Conversation Engine** (`AmigoConversationEngine.kt`)

A context-agnostic conversation engine that:
- Manages conversation flow and state
- Handles AI responses in standardized JSON format
- Executes tool calls requested by AI
- Maintains conversation history
- Provides consistent UI patterns across contexts

**Key Features:**
- Tool integration for AI to fetch data or perform actions
- Structured message flow with typing indicators  
- Support for multiple reply types (text, quick pills, date, number, yes/no)
- Feature card introductions within conversations
- Automatic state management (idle, active, complete, error)

### 2. **Conversation Models** (`ConversationModels.kt`)

Standardized data structures for all Amigo conversations:

```kotlin
data class AmigoMessage(
    val id: String,
    val text: String,
    val isFromAmigo: Boolean,
    val replyType: ReplyType,  // TEXT, QUICK_PILLS, DATE, NUMBER, YES_NO, etc.
    val replies: List<String>?,
    val feature: FeatureCard?,
    ...
)

data class AmigoResponse(
    val message: String,
    val replyType: ReplyType,
    val replies: List<String>?,
    val featureIntro: FeatureIntro?,
    val data: Map<String, String>?,
    val toolCalls: List<ToolCall>?
)
```

### 3. **Conversation Context Interface** (`ConversationContext.kt`)

Defines how different conversation types behave:

```kotlin
interface ConversationContext {
    val contextId: String
    val contextName: String
    
    fun buildSystemPrompt(): String
    fun buildUserPrompt(...): String
    fun getToolRegistry(): AmigoToolRegistry?
    fun isComplete(collectedData: Map<String, String>): Boolean
    fun processAIResponse(...): Map<String, String>
    ...
}
```

### 4. **Amigo Tools System**

Enhanced tool registry with new tools:

**Existing Tools:**
- `GetUserProfileTool` - Get user's profile data
- `CalculateBMITool` - Calculate BMI
- `CalculateBMRTool` - Calculate Basal Metabolic Rate
- `CalculateTDEETool` - Calculate Total Daily Energy Expenditure
- `ValidateGoalTool` - Validate if goal is realistic

**New Tools:**
- `GetCurrentGoalTool` - Get user's current health goal
- `SaveGoalTool` - Save user's health goal
- `GetRecentMealsTool` - Get recent meal logs (stub for now)

**Tool Registry Factory:**
- `createRegistry()` - Full tool set for general conversations
- `createGoalPlanningRegistry()` - Goal-specific tools only

### 5. **Goal Setting Context** (`GoalSettingContext.kt`)

First implementation of a conversation context for setting health goals:

**Features:**
- Uses tools to fetch user data instead of asking
- Validates goals are safe (max 1kg/week weight loss)
- Calculates realistic timelines
- Saves goal directly through `SaveGoalTool`
- Collects: goalType, targetWeight, targetDate, activityLevel

**System Prompt Highlights:**
- Guides AI to be supportive and realistic
- Instructs AI to call tools for data instead of asking user
- Enforces safe weight loss guidelines
- Returns structured JSON responses

### 6. **iOS Implementation**

**New Reusable Component:** `AmigoConversationView.swift`
- Universal conversation UI that works with any context
- Feature card display
- Multiple reply type support (pills, date picker, yes/no, text)
- Typing indicators and smooth animations
- Polls engine state and updates UI

**Updated:** `TalkToAmigoGoalView.swift`
- Now uses base conversation engine with `GoalSettingContext`
- Much simpler implementation (from 500+ lines to ~120 lines)
- Automatically saves goals through tool calls
- Consistent UI with rest of app

### 7. **Android Readiness**

The base conversation engine and models are in KMP shared module, so Android can use them with similar Compose UI:

```kotlin
@Composable
fun AmigoConversationScreen(
    engine: AmigoConversationEngine,
    onComplete: (Map<String, String>) -> Unit
) {
    // Can collect engine.messages, engine.isTyping, etc.
    // Display messages with different reply types
    // Similar to iOS implementation
}
```

## Architecture Benefits

### 1. **Consistency**
- Same JSON response format everywhere Amigo appears
- Same conversation patterns and UX
- Predictable behavior for users

### 2. **Reusability**
- Create new conversation contexts by implementing `ConversationContext`
- Reuse `AmigoConversationEngine` and UI components
- Share tools across different contexts

### 3. **Modularity**
- Tools are independent and testable
- Contexts define behavior without touching engine code
- UI components are decoupled from business logic

### 4. **Flexibility**
- AI can call tools to get data instead of asking user
- Different contexts can have different tool sets
- Easy to add new reply types or features

## How to Create New Conversation Contexts

### Example: Onboarding Context

```kotlin
class OnboardingContext(
    private val userProfile: UserProfile?,
    private val toolRegistry: AmigoToolRegistry
) : ConversationContext {
    
    override val contextId = "onboarding"
    override val contextName = "User Onboarding"
    
    override fun buildSystemPrompt(): String {
        return """
            You are Amigo helping a new user get started.
            Collect: name, age, height, weight, dietary preferences.
            Be warm and encouraging.
            Use tools to avoid asking for data that exists.
            Return JSON only.
        """.trimIndent()
    }
    
    override fun buildUserPrompt(
        userInput: String,
        conversationHistory: List<AmigoMessage>,
        collectedData: Map<String, String>
    ): String {
        // Build prompt with history and current data
    }
    
    override fun getToolRegistry() = toolRegistry
    
    override fun isComplete(collectedData: Map<String, String>): Boolean {
        return requiredFields.all { collectedData[it]?.isNotBlank() == true }
    }
    
    override fun processAIResponse(
        response: AmigoResponse,
        currentData: Map<String, String>
    ): Map<String, String> {
        // Extract and merge data from AI response
    }
}
```

### Use it anywhere:

```kotlin
// In iOS
let context = OnboardingContext(userProfile: profile, toolRegistry: registry)
let engine = AmigoConversationEngine(bedrockClient: client, context: context)
// Show AmigoConversationView with engine

// In Android
val context = OnboardingContext(userProfile, toolRegistry)
val engine = AmigoConversationEngine(bedrockClient, context)
// Show AmigoConversationScreen with engine
```

## JSON Response Format

All Amigo responses follow this structure:

```json
{
  "message": "Great! Let's set your target weight. What weight would you like to reach?",
  "replyType": "NUMBER",
  "replies": null,
  "data": {
    "goalType": "WEIGHT_LOSS",
    "activityLevel": "Moderately Active"
  },
  "toolCalls": [
    {
      "toolName": "get_user_profile",
      "parameters": {}
    }
  ],
  "nextAction": "continue"
}
```

## Tool Call Flow

1. **AI decides to call a tool:**
   ```json
   {
     "message": "Let me check your current metrics...",
     "toolCalls": [
       {"toolName": "get_user_profile", "parameters": {}}
     ]
   }
   ```

2. **Engine executes tool:**
   ```kotlin
   val result = toolRegistry.executeTool("get_user_profile", {})
   // Returns: {weight_kg: 75.0, height_cm: 170, age: 30, ...}
   ```

3. **Engine sends tool results back to AI:**
   ```
   User said: "I want to lose weight"
   
   Tool Execution Results:
   - get_user_profile: {weight_kg: 75.0, height_cm: 170, age: 30}
   
   Continue conversation with this data.
   ```

4. **AI responds with data:**
   ```json
   {
     "message": "Got it! You're currently at 75kg. What's your target weight?",
     "replyType": "NUMBER",
     "data": {
       "currentWeight": "75",
       "goalType": "WEIGHT_LOSS"
     }
   }
   ```

## UI Components

### iOS

- `AmigoConversationView` - Main conversation container
- `AmigoMessageBubbleView` - Individual message bubble
- `FeatureCardView` - Feature introduction card
- `QuickReplyPillsView` - Button-style options
- `DatePickerView` - Date selection
- `YesNoButtonsView` - Yes/No buttons
- `TypingIndicatorView` - Amigo is typing...

### Android (To Be Built)

- `AmigoConversationScreen` - Main conversation container
- `MessageBubble` - Individual message
- `FeatureCard` - Feature introduction
- `QuickReplyChips` - Chip-style options
- `DatePickerDialog` - Date selection
- `YesNoButtons` - Yes/No buttons
- `TypingIndicator` - Amigo is typing...

## What's Next

### Immediate Actions

1. **Build Android UI Components**
   - Create `AmigoConversationScreen.kt` 
   - Implement reply type composables
   - Wire up to conversation engine

2. **Test Goal Setting Flow**
   - Test on iOS simulator
   - Verify goal is saved correctly
   - Ensure tools are called properly

3. **Migrate Onboarding**
   - Create `OnboardingContext.kt`
   - Replace old `OnboardingConversationEngine` with new architecture
   - Reuse conversation UI

### Future Enhancements

1. **More Contexts**
   - Meal logging assistance
   - Fasting timer guidance
   - Progress check-ins
   - Motivation and advice

2. **More Tools**
   - `GetWeightHistoryTool` - Weight trends
   - `GetFastingStatsTool` - Fasting patterns
   - `CalculateMacrosTool` - Macro recommendations
   - `GetProgressSummaryTool` - Overall progress

3. **Enhanced Features**
   - Image upload in conversations
   - Voice input support
   - Multi-language support
   - Conversation history persistence

## Files Created/Modified

### Created:
- `shared/ai/conversation/ConversationModels.kt` - Data structures
- `shared/ai/conversation/ConversationContext.kt` - Context interface
- `shared/ai/conversation/AmigoConversationEngine.kt` - Base engine
- `shared/ai/conversation/contexts/GoalSettingContext.kt` - Goal setting context
- `shared/ai/conversation/TimeUtils.android.kt` - Platform time
- `shared/ai/conversation/TimeUtils.ios.kt` - Platform time
- `shared/data/repositories/MealLogRepository.kt` - Meal repo stub
- `ios/Amigo/Shared/AmigoConversationView.swift` - Reusable iOS UI

### Modified:
- `shared/ai/tools/AmigoToolFactory.kt` - Added tool creation methods
- `shared/profile/ProfileManager.kt` - Added `updateGoal()` and `getUserProfile()`
- `ios/Amigo/Profile/TalkToAmigoGoalView.swift` - Simplified using base engine

## Testing Guide

### iOS Testing:

1. Run iOS simulator
2. Navigate to Profile > My Goal
3. Tap "Talk to Amigo"
4. Observe conversation:
   - Amigo should use tools to get your current weight
   - Should ask for target weight
   - Should ask for target date
   - Should ask about activity level
   - Should offer to save the goal
5. Complete flow and verify goal is saved

### Key Logs to Watch:

```
AmigoConversationEngine: Context: goal_setting
AmigoConversationEngine: Executing tool: get_user_profile
AmigoToolRegistry: Executing tool: get_user_profile with params: {}
AmigoConversationEngine: Tool get_user_profile succeeded
AmigoConversationEngine: Executing tool: save_goal
```

## Conclusion

We now have a solid, extensible foundation for Amigo conversations throughout the app. The base conversation engine provides consistency, the tool system gives Amigo powerful capabilities, and the context interface makes it easy to create new conversation types. The iOS implementation is ready to test, and Android can follow the same pattern with Compose UI.
