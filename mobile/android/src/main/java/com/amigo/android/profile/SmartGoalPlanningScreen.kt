package com.amigo.android.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amigo.shared.auth.SessionManager
import com.amigo.shared.goals.*
import com.amigo.shared.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartGoalPlanningScreen(
    sessionManager: SessionManager,
    goalType: String,
    onDismiss: () -> Unit
) {
    var showAIPlanning by remember { mutableStateOf(false) }
    var showManualPlanning by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Your Goal") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Choose how you'd like to plan your journey",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Talk to Amigo Option
            PlanningOptionCard(
                icon = Icons.Default.Person,  // Using Person icon
                title = "Talk to Amigo",
                description = "Let Amigo guide you through setting a realistic goal with personalized calculations",
                color = MaterialTheme.colorScheme.primary,
                onClick = { showAIPlanning = true }
            )
            
            // Manual Planning Option
            PlanningOptionCard(
                icon = Icons.Default.Edit,
                title = "Set Manually",
                description = "Enter your target weight and date yourself with real-time calculations",
                color = MaterialTheme.colorScheme.secondary,
                onClick = { showManualPlanning = true }
            )
        }
    }
    
    if (showAIPlanning) {
        AIGoalPlanningDialog(
            sessionManager = sessionManager,
            goalType = goalType,
            onDismiss = { showAIPlanning = false },
            onComplete = onDismiss
        )
    }
    
    if (showManualPlanning) {
        ManualGoalPlanningDialog(
            sessionManager = sessionManager,
            goalType = goalType,
            onDismiss = { showManualPlanning = false },
            onComplete = onDismiss
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.1f),
                modifier = Modifier.size(60.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.ArrowForward,  // Using ArrowForward instead of ChevronRight
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIGoalPlanningDialog(
    sessionManager: SessionManager,
    goalType: String,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    val viewModel: AIGoalPlanningViewModel = viewModel(
        factory = AIGoalPlanningViewModelFactory(sessionManager, goalType)
    )
    
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val currentPlan by viewModel.currentPlan.collectAsState()
    val state by viewModel.state.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.startConversation()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Talk to Amigo") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
            )
        },
        bottomBar = {
            Column {
                currentPlan?.let { plan ->
                    GoalPlanSummaryCard(plan = plan)
                }
                
                if (state == GoalPlanningState.ReviewingPlan) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.requestAdjustment() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Adjust")
                        }
                        Button(
                            onClick = {
                                viewModel.acceptPlan()
                                onComplete()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Accept Plan")
                        }
                    }
                }
                
                if (state == GoalPlanningState.Collecting || state == GoalPlanningState.ReviewingPlan) {
                    MessageInputBar(
                        value = viewModel.userInput,
                        onValueChange = { viewModel.userInput = it },
                        onSend = { viewModel.sendMessage() },
                        enabled = !isTyping
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message = message)
            }
            
            if (isTyping) {
                item {
                    TypingIndicator()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualGoalPlanningDialog(
    sessionManager: SessionManager,
    goalType: String,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    val viewModel: ManualGoalPlanningViewModel = viewModel(
        factory = ManualGoalPlanningViewModelFactory(sessionManager, goalType)
    )
    
    val targetWeight by viewModel.targetWeight.collectAsState()
    val targetDate by viewModel.targetDate.collectAsState()
    val activityLevel by viewModel.activityLevel.collectAsState()
    val calculatedPlan by viewModel.calculatedPlan.collectAsState()
    val validationErrors by viewModel.validationErrors.collectAsState()
    val canSave by viewModel.canSave.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.initialize()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Your Goal") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    viewModel.savePlan()
                    onComplete()
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Save Goal")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Goal Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Target Weight
                        OutlinedTextField(
                            value = targetWeight?.toString() ?: "",
                            onValueChange = { viewModel.updateTargetWeight(it.toDoubleOrNull()) },
                            label = { Text("Target Weight (kg)") },
                            isError = validationErrors.containsKey("targetWeight"),
                            supportingText = validationErrors["targetWeight"]?.let { { Text(it) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Target Date
                        // Note: In production, use a proper date picker
                        OutlinedTextField(
                            value = targetDate,
                            onValueChange = { viewModel.updateTargetDate(it) },
                            label = { Text("Target Date (YYYY-MM-DD)") },
                            isError = validationErrors.containsKey("targetDate"),
                            supportingText = validationErrors["targetDate"]?.let { { Text(it) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Activity Level
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = activityLevel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Activity Level") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                listOf("sedentary", "light", "moderate", "active", "very_active").forEach { level ->
                                    DropdownMenuItem(
                                        text = { Text(level.capitalize()) },
                                        onClick = {
                                            viewModel.updateActivityLevel(level)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            calculatedPlan?.let { plan ->
                item {
                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Your Plan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            if (!plan.validation.isRealistic) {
                                ValidationWarningCard(validation = plan.validation)
                            }
                            
                            MetricsGrid(plan = plan)
                            
                            ProgressChart(milestones = plan.milestones)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: GoalMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(8.dp)
                    ) {}
                }
            }
        }
    }
}

@Composable
fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Type your response...") },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            
            IconButton(
                onClick = onSend,
                enabled = enabled && value.isNotEmpty()
            ) {
                Icon(Icons.Default.Send, "Send")
            }
        }
    }
}

@Composable
fun GoalPlanSummaryCard(plan: GoalPlan) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Your Plan Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(label = "Daily Calories", value = "${plan.calculations.dailyCalories.toInt()}")
                MetricItem(label = "Weekly Rate", value = "%.1f kg".format(plan.calculations.weeklyWeightLossRate))
            }
            
            if (!plan.validation.isRealistic) {
                Text(
                    text = "⚠️ ${plan.validation.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun ValidationWarningCard(validation: GoalValidationResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Goal Adjustment Needed",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                text = validation.reason,
                style = MaterialTheme.typography.bodySmall
            )
            
            validation.recommendedTargetDays?.let { days ->
                Text(
                    text = "Recommended: $days days",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun MetricsGrid(plan: GoalPlan) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricCard(
            title = "BMR",
            value = "${plan.calculations.bmr.toInt()}",
            unit = "cal/day",
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            title = "TDEE",
            value = "${plan.calculations.tdee.toInt()}",
            unit = "cal/day",
            modifier = Modifier.weight(1f)
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricCard(
            title = "Daily Calories",
            value = "${plan.calculations.dailyCalories.toInt()}",
            unit = "cal",
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            title = "Weekly Rate",
            value = "%.1f".format(plan.calculations.weeklyWeightLossRate),
            unit = "kg/week",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ProgressChart(milestones: List<WeeklyMilestone>) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Progress Projection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // Simple line chart representation
            // In production, use a proper charting library like Vico or MPAndroidChart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Chart: ${milestones.size} weeks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ViewModels
class AIGoalPlanningViewModel(
    private val sessionManager: SessionManager,
    private val goalType: String
) : ViewModel() {
    private val bedrockClient = com.amigo.shared.ai.BedrockClientFactory.create(
        apiEndpoint = "https://n96755fzqk.execute-api.us-east-1.amazonaws.com/dev/invoke",
        getAuthToken = { sessionManager.getAccessToken() }
    )
    private val calculationEngine = GoalCalculationEngine()
    private val conversationEngine = GoalPlanningConversationEngine(
        bedrockClient, sessionManager, calculationEngine
    )
    
    val messages = conversationEngine.messages
    val isTyping = conversationEngine.isTyping
    val state = conversationEngine.conversationState
    val currentPlan = conversationEngine.currentPlan
    
    var userInput by mutableStateOf("")
    
    fun startConversation() {
        viewModelScope.launch {
            val userId = sessionManager.getCurrentUser()?.id ?: return@launch
            val profileManager = com.amigo.shared.profile.ProfileManagerFactory.create()
            
            profileManager.getProfile(userId).onSuccess { profile ->
                conversationEngine.startGoalPlanning(profile, goalType)
            }.onFailure { error ->
                com.amigo.shared.utils.Logger.e("AIGoalPlanning", "Failed to load profile: ${error.message}")
            }
        }
    }
    
    fun sendMessage() {
        if (userInput.isBlank()) return
        
        viewModelScope.launch {
            val message = userInput
            userInput = ""
            conversationEngine.processUserResponse(message)
        }
    }
    
    fun requestAdjustment() {
        viewModelScope.launch {
            conversationEngine.requestAdjustment("I'd like to adjust the plan")
        }
    }
    
    fun acceptPlan() {
        viewModelScope.launch {
            val plan = conversationEngine.acceptPlan()
            if (plan != null) {
                // Save plan to database
                savePlanToDatabase(plan)
            }
        }
    }
    
    private suspend fun savePlanToDatabase(plan: GoalPlan) {
        try {
            val userId = sessionManager.getCurrentUser()?.id ?: run {
                Logger.e("AIGoalPlanning", "No user found, cannot save plan")
                return
            }
            
            val profileManager = com.amigo.shared.profile.ProfileManagerFactory.create()
            
            // Prepare milestone data
            val milestonePayload = plan.milestones.map { milestone ->
                mapOf(
                    "week" to milestone.week,
                    "projected_weight_kg" to milestone.projectedWeightKg,
                    "percent_complete" to milestone.percentComplete
                )
            }
            
            // Use the shared ProfileManager.updateGoal method
            val result = profileManager.updateGoal(
                userId = userId,
                goalType = when (plan.goalType) {
                    "weight_loss" -> com.amigo.shared.data.models.GoalType.WEIGHT_LOSS
                    "muscle_gain" -> com.amigo.shared.data.models.GoalType.MUSCLE_GAIN
                    "improved_energy" -> com.amigo.shared.data.models.GoalType.IMPROVED_ENERGY
                    "maintenance" -> com.amigo.shared.data.models.GoalType.MAINTENANCE
                    else -> com.amigo.shared.data.models.GoalType.WEIGHT_LOSS
                },
                targetDate = plan.targetMetrics.targetDate,
                targetWeightKg = plan.targetMetrics.weightKg,
                currentWeightKg = plan.currentMetrics.weightKg,
                currentHeightCm = plan.currentMetrics.heightCm,
                activityLevel = plan.currentMetrics.activityLevel,
                calculatedBmr = plan.calculations.bmr,
                calculatedTdee = plan.calculations.tdee,
                calculatedDailyCalories = plan.calculations.dailyCalories,
                calculatedBmiStart = plan.currentMetrics.bmi,
                calculatedBmiTarget = plan.targetMetrics.targetBMI,
                weeklyMilestones = milestonePayload.toString(),
                isRealistic = plan.validation.isRealistic,
                recommendedTargetDate = null,
                validationReason = plan.validation.reason,
                goalContext = null
            )
            
            if (result.isSuccess) {
                Logger.i("AIGoalPlanning", "Plan accepted and saved: ${plan.goalType}")
            } else {
                Logger.e("AIGoalPlanning", "Failed to save plan: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Logger.e("AIGoalPlanning", "Error saving plan: ${e.message}")
        }
    }
}

class ManualGoalPlanningViewModel(
    private val sessionManager: SessionManager,
    private val goalType: String
) : ViewModel() {
    private val calculationEngine = GoalCalculationEngine()
    private val planningManager = ManualGoalPlanningManager(calculationEngine)
    
    val targetWeight = planningManager.formState.map { it.targetWeight }
    val targetDate = planningManager.formState.map { it.targetDate ?: "" }
    val activityLevel = planningManager.formState.map { it.activityLevel }
    val calculatedPlan = planningManager.calculatedPlan
    val validationErrors = planningManager.validationErrors
    
    private val _canSave = MutableStateFlow(false)
    val canSave: StateFlow<Boolean> = _canSave.asStateFlow()
    
    fun initialize() {
        viewModelScope.launch {
            val userId = sessionManager.getCurrentUser()?.id ?: return@launch
            val profileManager = com.amigo.shared.profile.ProfileManagerFactory.create()
            
            profileManager.getProfile(userId).onSuccess { profile ->
                planningManager.initialize(profile, goalType)
                updateCanSave()
            }.onFailure { error ->
                com.amigo.shared.utils.Logger.e("ManualGoalPlanning", "Failed to load profile: ${error.message}")
            }
        }
    }
    
    fun updateTargetWeight(weight: Double?) {
        weight?.let {
            planningManager.updateTargetWeight(it)
            updateCanSave()
        }
    }
    
    fun updateTargetDate(date: String) {
        planningManager.updateTargetDate(date)
        updateCanSave()
    }
    
    fun updateActivityLevel(level: String) {
        planningManager.updateActivityLevel(level)
        updateCanSave()
    }
    
    private fun updateCanSave() {
        _canSave.value = planningManager.validate()
    }
    
    fun savePlan() {
        viewModelScope.launch {
            val plan = planningManager.getGoalPlan()
            if (plan != null) {
                // Save plan to database
                savePlanToDatabase(plan)
            }
        }
    }
    
    private suspend fun savePlanToDatabase(plan: GoalPlan) {
        try {
            val userId = sessionManager.getCurrentUser()?.id ?: run {
                Logger.e("ManualGoalPlanning", "No user found, cannot save plan")
                return
            }
            
            val profileManager = com.amigo.shared.profile.ProfileManagerFactory.create()
            
            // Prepare milestone data
            val milestonePayload = plan.milestones.map { milestone ->
                mapOf(
                    "week" to milestone.week,
                    "projected_weight_kg" to milestone.projectedWeightKg,
                    "percent_complete" to milestone.percentComplete
                )
            }
            
            // Use the shared ProfileManager.updateGoal method
            val result = profileManager.updateGoal(
                userId = userId,
                goalType = when (plan.goalType) {
                    "weight_loss" -> com.amigo.shared.data.models.GoalType.WEIGHT_LOSS
                    "muscle_gain" -> com.amigo.shared.data.models.GoalType.MUSCLE_GAIN
                    "improved_energy" -> com.amigo.shared.data.models.GoalType.IMPROVED_ENERGY
                    "maintenance" -> com.amigo.shared.data.models.GoalType.MAINTENANCE
                    else -> com.amigo.shared.data.models.GoalType.WEIGHT_LOSS
                },
                targetDate = plan.targetMetrics.targetDate,
                targetWeightKg = plan.targetMetrics.weightKg,
                currentWeightKg = plan.currentMetrics.weightKg,
                currentHeightCm = plan.currentMetrics.heightCm,
                activityLevel = plan.currentMetrics.activityLevel,
                calculatedBmr = plan.calculations.bmr,
                calculatedTdee = plan.calculations.tdee,
                calculatedDailyCalories = plan.calculations.dailyCalories,
                calculatedBmiStart = plan.currentMetrics.bmi,
                calculatedBmiTarget = plan.targetMetrics.targetBMI,
                weeklyMilestones = milestonePayload.toString(),
                isRealistic = plan.validation.isRealistic,
                recommendedTargetDate = null,
                validationReason = plan.validation.reason,
                goalContext = null
            )
            
            if (result.isSuccess) {
                Logger.i("ManualGoalPlanning", "Plan saved successfully: ${plan.goalType}")
            } else {
                Logger.e("ManualGoalPlanning", "Failed to save plan: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Logger.e("ManualGoalPlanning", "Error saving plan: ${e.message}")
        }
    }
}

private fun <T, R> StateFlow<T>.map(transform: (T) -> R): StateFlow<R> {
    val result = MutableStateFlow(transform(this.value))
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
        this@map.collect { value ->
            result.value = transform(value)
        }
    }
    return result.asStateFlow()
}

// ViewModel Factories
class AIGoalPlanningViewModelFactory(
    private val sessionManager: SessionManager,
    private val goalType: String
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AIGoalPlanningViewModel(sessionManager, goalType) as T
    }
}

class ManualGoalPlanningViewModelFactory(
    private val sessionManager: SessionManager,
    private val goalType: String
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ManualGoalPlanningViewModel(sessionManager, goalType) as T
    }
}
