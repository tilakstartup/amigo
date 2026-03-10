package com.amigo.android.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AgentConversationScreen(
    viewModel: AgentConversationViewModel,
    onComplete: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val needsTextInput by viewModel.needsTextInput.collectAsState()
    val isComplete by viewModel.isComplete.collectAsState()
    val shouldRequestPermissions by viewModel.shouldRequestPermissions.collectAsState()
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty() || isTyping) {
            coroutineScope.launch {
                listState.animateScrollToItem(
                    if (isTyping) messages.size else messages.size - 1
                )
            }
        }
    }
    
    // Handle completion
    LaunchedEffect(isComplete) {
        if (isComplete && !shouldRequestPermissions) {
            onComplete()
        }
    }
    
    // Start onboarding on first composition
    LaunchedEffect(Unit) {
        viewModel.startOnboarding()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFCE4EC),
                        Color(0xFFF3E5F5)
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            OnboardingHeader()
            
            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onQuickReply = { reply ->
                            coroutineScope.launch {
                                viewModel.sendQuickReply(reply)
                            }
                        }
                    )
                }
                
                // Typing indicator
                if (isTyping) {
                    item(key = "typing") {
                        TypingIndicator()
                    }
                }
            }
            
            // Input area
            if (needsTextInput) {
                TextInputArea(
                    value = viewModel.userInput.value,
                    onValueChange = { viewModel.userInput.value = it },
                    onSend = {
                        coroutineScope.launch {
                            viewModel.sendMessage()
                        }
                    },
                    placeholder = viewModel.inputPlaceholder.value
                )
            }
        }
        
        // Permissions sheet
        if (shouldRequestPermissions) {
            PermissionsSheet(
                onComplete = onComplete,
                onDismiss = onComplete
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Amigo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Your AI Health Coach",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Amigo",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE91E63))
                    .padding(8.dp),
                tint = Color.White
            )
        }
    }
}


@Composable
fun MessageBubble(
    message: MessageViewModel,
    onQuickReply: (String) -> Unit
) {
    var showMessage by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        if (message.delayAfterPrevious > 0) {
            kotlinx.coroutines.delay(message.delayAfterPrevious)
        }
        showMessage = true
    }
    
    AnimatedVisibility(
        visible = showMessage,
        enter = fadeIn() + slideInHorizontally(
            initialOffsetX = { if (message.isFromAmigo) -it / 2 else it / 2 }
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.isFromAmigo) Arrangement.Start else Arrangement.End
        ) {
            if (message.isFromAmigo && (message.text.isNotEmpty() || message.isFeatureIntro)) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Amigo",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE91E63))
                        .padding(6.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            Column(
                modifier = Modifier.widthIn(max = 280.dp),
                horizontalAlignment = if (message.isFromAmigo) Alignment.Start else Alignment.End
            ) {
                // Feature intro card
                if (message.isFeatureIntro && message.feature != null) {
                    FeatureCard(feature = message.feature)
                } else {
                    // Feature card before message (if present)
                    if (message.feature != null && !message.isFeatureIntro) {
                        FeatureCard(feature = message.feature)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // Message bubble
                    if (message.text.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (message.isFromAmigo) 
                                MaterialTheme.colorScheme.surfaceVariant 
                            else 
                                Color(0xFFE91E63)
                        ) {
                            Text(
                                text = message.text,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                color = if (message.isFromAmigo) 
                                    MaterialTheme.colorScheme.onSurfaceVariant 
                                else 
                                    Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    
                    // Reply options
                    message.replyType?.let { replyType ->
                        Spacer(modifier = Modifier.height(8.dp))
                        when (replyType) {
                            "quick_pills" -> {
                                message.replies?.let { replies ->
                                    QuickReplyPills(
                                        options = replies,
                                        onSelect = onQuickReply,
                                        isDisabled = message.isDisabled
                                    )
                                }
                            }
                            "yes_no" -> {
                                YesNoButtons(
                                    options = message.replies,
                                    onSelect = onQuickReply,
                                    isDisabled = message.isDisabled
                                )
                            }
                            "date" -> {
                                DatePicker(
                                    onSelect = onQuickReply,
                                    isDisabled = message.isDisabled
                                )
                            }
                            "weight" -> {
                                WeightPicker(
                                    onSelect = onQuickReply,
                                    isDisabled = message.isDisabled
                                )
                            }
                            else -> {
                                // Other reply types not implemented yet
                            }
                        }
                    }
                }
                
                // Timestamp
                if (message.text.isNotEmpty() || message.isFeatureIntro) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message.formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureCard(feature: com.amigo.shared.ai.models.FeatureIntro) {
    Surface(
        modifier = Modifier.fillMaxWidth(0.85f),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color(0xFFE91E63).copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFE91E63).copy(alpha = 0.12f)
            ) {
                Text(
                    text = "Feature Highlight",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE91E63)
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE91E63).copy(alpha = 0.14f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = feature.icon,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
                
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = feature.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = feature.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun QuickReplyPills(
    options: List<String>,
    onSelect: (String) -> Unit,
    isDisabled: Boolean
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            Surface(
                onClick = { if (!isDisabled) onSelect(option) },
                enabled = !isDisabled,
                shape = RoundedCornerShape(20.dp),
                color = if (isDisabled) 
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else 
                    Color(0xFFE91E63).copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDisabled) Color.Gray else Color(0xFFE91E63)
                )
            ) {
                Text(
                    text = option,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDisabled) Color.Gray else Color(0xFFE91E63)
                )
            }
        }
    }
}

@Composable
fun YesNoButtons(
    options: List<String>?,
    onSelect: (String) -> Unit,
    isDisabled: Boolean
) {
    val labels = options
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.distinct()
        ?.take(2)
        ?.takeIf { it.size == 2 }
        ?: listOf("Yes", "No")

    Row(
        modifier = Modifier.fillMaxWidth(0.7f),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { if (!isDisabled) onSelect(labels[0]) },
            enabled = !isDisabled,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDisabled) Color.Gray else Color(0xFFE91E63)
            )
        ) {
            Text(labels[0])
        }
        
        OutlinedButton(
            onClick = { if (!isDisabled) onSelect(labels[1]) },
            enabled = !isDisabled,
            modifier = Modifier.weight(1f)
        ) {
            Text(labels[1])
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePicker(
    onSelect: (String) -> Unit,
    isDisabled: Boolean
) {
    var showDialog by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<Long?>(null) }
    
    OutlinedButton(
        onClick = { if (!isDisabled) showDialog = true },
        enabled = !isDisabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (isDisabled) Color.Gray else Color(0xFFE91E63)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDisabled) Color.Gray else Color(0xFFE91E63)
        )
    ) {
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = "Select date",
            tint = if (isDisabled) Color.Gray else Color(0xFFE91E63)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = selectedDate?.let {
                java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                    .format(java.util.Date(it))
            } ?: "Select a date",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    
    if (showDialog) {
        val datePickerState = rememberDatePickerState()
        
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showDialog = false }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Select Date",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE91E63)
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        DatePicker(
                            state = datePickerState,
                            showModeToggle = false,
                            colors = DatePickerDefaults.colors(
                                selectedDayContainerColor = Color(0xFFE91E63),
                                todayContentColor = Color(0xFFE91E63),
                                todayDateBorderColor = Color(0xFFE91E63)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        
                        Button(
                            onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    selectedDate = millis
                                    val dateString = java.text.SimpleDateFormat(
                                        "yyyy-MM-dd",
                                        java.util.Locale.getDefault()
                                    ).format(java.util.Date(millis))
                                    onSelect(dateString)
                                    showDialog = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE91E63)
                            ),
                            enabled = datePickerState.selectedDateMillis != null
                        ) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeightPicker(
    onSelect: (String) -> Unit,
    isDisabled: Boolean
) {
    var selectedWeight by remember { mutableStateOf(70f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = String.format("%.1f kg", selectedWeight),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Use the slider to set your weight",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Slider(
            value = selectedWeight,
            onValueChange = { value ->
                if (!isDisabled) {
                    selectedWeight = (value * 2f).roundToInt() / 2f
                }
            },
            valueRange = 30f..200f,
            enabled = !isDisabled,
            colors = SliderDefaults.colors(
                activeTrackColor = Color(0xFFE91E63),
                thumbColor = Color(0xFFE91E63)
            )
        )

        Button(
            onClick = { if (!isDisabled) onSelect(String.format("%.1f", selectedWeight)) },
            enabled = !isDisabled,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDisabled) Color.Gray else Color(0xFFE91E63)
            )
        ) {
            Text("Confirm weight")
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = "Amigo",
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFFE91E63))
                .padding(6.dp),
            tint = Color.White
        )
        
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) { index ->
                    val infiniteTransition = rememberInfiniteTransition(label = "typing")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(400),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 133)
                        ),
                        label = "dot_$index"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.Gray.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

@Composable
fun TextInputArea(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    placeholder: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFE91E63),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                singleLine = true
            )
            
            IconButton(
                onClick = onSend,
                enabled = value.trim().isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (value.trim().isEmpty()) 
                        Color.Gray 
                    else 
                        Color(0xFFE91E63)
                )
            }
        }
    }
}

// FlowRow for wrapping pills
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { measurable -> 
            measurable.measure(constraints.copy(minWidth = 0, minHeight = 0)) 
        }
        
        val rows = mutableListOf<List<Placeable>>()
        var currentRow = mutableListOf<Placeable>()
        var currentRowWidth = 0
        val horizontalSpacing = 8
        val verticalSpacing = 8
        
        placeables.forEach { placeable ->
            val placeableWidth = placeable.width + horizontalSpacing
            
            if (currentRowWidth + placeableWidth > constraints.maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow.toList())
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            
            currentRow.add(placeable)
            currentRowWidth += placeableWidth
        }
        
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
        }
        
        var yPosition = 0
        val positions = mutableListOf<Pair<Int, Int>>()
        
        rows.forEach { row ->
            var xPosition = 0
            val maxRowHeight = row.maxOfOrNull { it.height } ?: 0
            
            row.forEach { placeable ->
                positions.add(Pair(xPosition, yPosition))
                xPosition += placeable.width + horizontalSpacing
            }
            
            yPosition += maxRowHeight + verticalSpacing
        }
        
        val totalHeight = if (rows.isNotEmpty()) {
            yPosition - verticalSpacing
        } else {
            0
        }
        
        layout(constraints.maxWidth, totalHeight) {
            placeables.forEachIndexed { index, placeable ->
                val (x, y) = positions[index]
                placeable.placeRelative(x, y)
            }
        }
    }
}
