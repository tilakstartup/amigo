package com.amigo.android.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amigo.shared.auth.SessionManager
import com.amigo.shared.data.models.GoalType
import com.amigo.shared.profile.ProfileManagerFactory
import com.amigo.shared.profile.ProfileUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalManagementScreen(
    sessionManager: SessionManager,
    onNavigateBack: () -> Unit
) {
    val viewModel = remember { GoalManagementViewModel(sessionManager) }
    val currentGoal by viewModel.currentGoal.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val showConfirmation by viewModel.showConfirmation.collectAsState()
    val selectedGoal by viewModel.selectedGoal.collectAsState()
    var showSmartGoalPlanning by remember { mutableStateOf(false) }
    var selectedGoalForPlanning by remember { mutableStateOf<GoalType?>(null) }
    
    LaunchedEffect(Unit) {
        viewModel.loadCurrentGoal()
    }
    
    // If no goal exists and not loading, show goal selection immediately
    LaunchedEffect(currentGoal, isLoading, errorMessage) {
        if (!isLoading && currentGoal == null && errorMessage == null) {
            // No goal set yet - this is expected for new users
            // The UI will show goal selection options
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Goal") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Loading...")
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Current Goal Section
                    currentGoal?.let { goal ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Current Goal",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            GoalCard(
                                goalType = goal,
                                isSelected = true,
                                onSelect = {}
                            )
                        }
                    }
                    
                    // Manual Goal Selection Section
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = if (currentGoal == null) "Choose your goal" else "Change your goal",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        GoalType.values().filter { it != currentGoal }.forEach { goalType ->
                            GoalCard(
                                goalType = goalType,
                                isSelected = false,
                                onSelect = {
                                    selectedGoalForPlanning = goalType
                                    showSmartGoalPlanning = true
                                }
                            )
                        }
                    }
                    
                    // Only show error if it's not a "no profile" error
                    errorMessage?.let { error ->
                        if (!error.contains("Failed to load goal")) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Confirmation Dialog
    if (showConfirmation && selectedGoal != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissConfirmation() },
            title = { Text("Change Goal?") },
            text = {
                Text("Are you sure you want to change your goal to ${goalTypeDisplayName(selectedGoal!!)}?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmGoalChange()
                    }
                ) {
                    Text("Change Goal")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissConfirmation() }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Smart Goal Planning Screen
    if (showSmartGoalPlanning && selectedGoalForPlanning != null) {
        SmartGoalPlanningScreen(
            sessionManager = sessionManager,
            goalType = selectedGoalForPlanning!!.name.lowercase(),
            onDismiss = {
                showSmartGoalPlanning = false
                selectedGoalForPlanning = null
                viewModel.loadCurrentGoal() // Reload to show updated goal
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalCard(
    goalType: GoalType,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null,
        enabled = !isSelected
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Surface(
                modifier = Modifier.size(50.dp),
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = goalIcon(goalType),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
            
            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = goalTypeDisplayName(goalType),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    text = goalDescription(goalType),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun goalIcon(goalType: GoalType): String {
    return when (goalType) {
        GoalType.WEIGHT_LOSS -> "⚖️"
        GoalType.MUSCLE_GAIN -> "💪"
        GoalType.MAINTENANCE -> "🎯"
        GoalType.IMPROVED_ENERGY -> "⚡️"
        GoalType.BETTER_SLEEP -> "😴"
    }
}

private fun goalTypeDisplayName(goalType: GoalType): String {
    return when (goalType) {
        GoalType.WEIGHT_LOSS -> "Weight Loss"
        GoalType.MUSCLE_GAIN -> "Muscle Gain"
        GoalType.MAINTENANCE -> "Maintenance"
        GoalType.IMPROVED_ENERGY -> "Improved Energy"
        GoalType.BETTER_SLEEP -> "Better Sleep"
    }
}

private fun goalDescription(goalType: GoalType): String {
    return when (goalType) {
        GoalType.WEIGHT_LOSS -> "Lose weight and improve body composition"
        GoalType.MUSCLE_GAIN -> "Build muscle and increase strength"
        GoalType.MAINTENANCE -> "Maintain current weight and health"
        GoalType.IMPROVED_ENERGY -> "Boost energy levels throughout the day"
        GoalType.BETTER_SLEEP -> "Improve sleep quality and duration"
    }
}

class GoalManagementViewModel(
    private val sessionManager: SessionManager
) : ViewModel() {
    
    private val _currentGoal = MutableStateFlow<GoalType?>(null)
    val currentGoal: StateFlow<GoalType?> = _currentGoal.asStateFlow()
    
    private val _selectedGoal = MutableStateFlow<GoalType?>(null)
    val selectedGoal: StateFlow<GoalType?> = _selectedGoal.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _showConfirmation = MutableStateFlow(false)
    val showConfirmation: StateFlow<Boolean> = _showConfirmation.asStateFlow()
    
    private val profileManager = ProfileManagerFactory.create()
    
    fun loadCurrentGoal() {
        viewModelScope.launch {
            val userId = sessionManager.getCurrentUser()?.id ?: run {
                _errorMessage.value = "No user session found"
                return@launch
            }
        
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val result = profileManager.getProfile(userId)
                result.onSuccess { profile ->
                    _currentGoal.value = profile.goalType
                }.onFailure { error ->
                    // Don't show error for missing profile - this is expected for new users
                    // Just leave currentGoal as null
                    _currentGoal.value = null
                }
            } catch (e: Exception) {
                // Don't show error for missing profile - this is expected for new users
                _currentGoal.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun selectGoal(goalType: GoalType) {
        _selectedGoal.value = goalType
        _showConfirmation.value = true
    }
    
    fun dismissConfirmation() {
        _showConfirmation.value = false
        _selectedGoal.value = null
    }
    
    fun confirmGoalChange() {
        viewModelScope.launch {
            val userId = sessionManager.getCurrentUser()?.id ?: return@launch
            val newGoal = _selectedGoal.value ?: return@launch
        
            _isLoading.value = true
            _errorMessage.value = null
            _showConfirmation.value = false
            
            try {
                val update = ProfileUpdate(
                    goalType = newGoal
                )
                
                val result = profileManager.updateProfile(userId, update)
                result.onSuccess {
                    _currentGoal.value = newGoal
                    _selectedGoal.value = null
                }.onFailure { error ->
                    _errorMessage.value = "Failed to update goal: ${error.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update goal: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long
)
