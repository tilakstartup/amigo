package com.amigo.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.amigo.shared.ai.BedrockClient
import com.amigo.shared.ai.BedrockClientFactory
import com.amigo.shared.ai.BedrockResponse
import com.amigo.shared.ai.BedrockUsage
import kotlinx.coroutines.launch

@Composable
fun BedrockTestScreen(
    viewModel: BedrockTestViewModel = viewModel()
) {
    var prompt by remember { mutableStateOf("Hello, how are you?") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Bedrock Lambda Test",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        // Prompt input
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Prompt:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text("Enter your prompt...") }
            )
        }
        
        // Send button
        Button(
            onClick = { viewModel.testBedrock(prompt) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.isLoading
        ) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Text("Send to Bedrock")
            }
        }
        
        // Response display
        viewModel.response?.let { response ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Response:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFE8F5E9),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = response,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        
        // Error display
        viewModel.error?.let { error ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Error:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFFEBEE),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        // Usage info
        viewModel.usage?.let { usage ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Token Usage:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFE3F2FD),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Input: ${usage.inputTokens} tokens",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Output: ${usage.outputTokens} tokens",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

class BedrockTestViewModel : ViewModel() {
    var response by mutableStateOf<String?>(null)
        private set
    
    var error by mutableStateOf<String?>(null)
        private set
    
    var usage by mutableStateOf<BedrockUsage?>(null)
        private set
    
    var isLoading by mutableStateOf(false)
        private set
    
    private var bedrockClient: BedrockClient? = null
    
    init {
        setupBedrockClient()
    }
    
    private fun setupBedrockClient() {
        // Get API endpoint from BuildConfig or environment
        val apiEndpoint = System.getenv("BEDROCK_API_ENDPOINT") 
            ?: "https://YOUR_API_ID.execute-api.us-east-1.amazonaws.com/dev/invoke"
        
        // Create auth token provider
        val getAuthToken: suspend () -> String? = {
            // Get current session from SessionManager
            // For now, we'll use a placeholder - in real app, get from SessionManager
            getAccessToken()
        }
        
        bedrockClient = BedrockClientFactory.getInstance(
            apiEndpoint = apiEndpoint,
            getAuthToken = getAuthToken
        )
    }
    
    private suspend fun getAccessToken(): String? {
        // In a real app, you would get this from your SessionManager instance
        // For testing, you can hardcode a token or get it from SharedPreferences
        // This is just a placeholder implementation
        return null
    }
    
    fun testBedrock(prompt: String) {
        val client = bedrockClient ?: run {
            error = "BedrockClient not initialized"
            return
        }
        
        isLoading = true
        response = null
        error = null
        usage = null
        
        viewModelScope.launch {
            try {
                val result = client.invokeModel(
                    modelId = "anthropic.claude-3-5-sonnet-20241022-v2:0",
                    prompt = prompt,
                    maxTokens = 500,
                    temperature = 0.7,
                    systemPrompt = "You are Amigo, a friendly health coach."
                )
                
                result.onSuccess { bedrockResponse ->
                    response = bedrockResponse.completion
                    usage = bedrockResponse.usage
                }.onFailure { exception ->
                    error = exception.message ?: "Unknown error"
                }
            } catch (e: Exception) {
                error = e.message ?: "Unknown error"
            } finally {
                isLoading = false
            }
        }
    }
}
