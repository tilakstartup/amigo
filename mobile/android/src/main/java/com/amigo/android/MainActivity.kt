package com.amigo.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.amigo.android.auth.AuthViewModel
import com.amigo.android.auth.LoginScreen
import com.amigo.android.auth.SignUpScreen
import com.amigo.android.ui.theme.AmigoTheme
import com.amigo.shared.auth.AuthFactory
import com.amigo.shared.auth.SecureStorage
import com.amigo.shared.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var authViewModel: AuthViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Supabase client
        val supabaseUrl = AppConfig.SUPABASE_URL
        val supabaseKey = AppConfig.SUPABASE_ANON_KEY
        AuthFactory.initializeSupabase(supabaseUrl, supabaseKey)
        
        // Create authentication components
        val emailAuthenticator = AuthFactory.createEmailAuthenticator()
        val oauthAuthenticator = AuthFactory.createOAuthAuthenticator()
        val secureStorage = SecureStorage(applicationContext)
        val sessionManager = AuthFactory.createSessionManager(secureStorage)
        
        // Initialize view model
        authViewModel = AuthViewModel(emailAuthenticator, oauthAuthenticator, sessionManager)
        
        // Handle deep link if present
        handleDeepLink(intent)
        
        setContent {
            AmigoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AmigoApp(authViewModel)
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }
    
    private fun handleDeepLink(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null && data.scheme == "amigo" && data.host == "auth") {
            Log.d("MainActivity", "Deep link received: $data")
            
            // Extract auth tokens from URL fragment
            val fragment = data.fragment
            if (fragment != null) {
                Log.d("MainActivity", "Fragment: $fragment")
                
                // Parse fragment parameters
                val params = fragment.split("&").associate {
                    val parts = it.split("=")
                    parts[0] to (parts.getOrNull(1) ?: "")
                }
                
                val accessToken = params["access_token"]
                val refreshToken = params["refresh_token"]
                
                if (accessToken != null && refreshToken != null) {
                    Log.d("MainActivity", "Tokens found, handling session")
                    
                    // Handle the session in a coroutine
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            authViewModel.handleDeepLinkSession(accessToken, refreshToken)
                            Log.d("MainActivity", "Session handled successfully")
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error handling session: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AmigoApp(viewModel: AuthViewModel) {
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Check onboarding status
    var hasCompletedOnboarding by remember { mutableStateOf(false) }
    
    // Update onboarding status when authentication changes
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            // Check user-specific onboarding status
            val user = viewModel.getCurrentUser()
            if (user != null) {
                val prefs = context.getSharedPreferences("amigo_prefs", android.content.Context.MODE_PRIVATE)
                hasCompletedOnboarding = prefs.getBoolean("hasCompletedOnboarding_${user.id}", false)
            }
        } else {
            hasCompletedOnboarding = false
        }
    }
    
    when {
        !isAuthenticated -> {
            // Show authentication screens
            NavHost(navController = navController, startDestination = "login") {
                composable("login") {
                    LoginScreen(
                        viewModel = viewModel,
                        onNavigateToSignUp = {
                            navController.navigate("signup")
                        }
                    )
                }
                composable("signup") {
                    SignUpScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
        !hasCompletedOnboarding -> {
            // Show conversational onboarding
            val onboardingViewModel = remember {
                com.amigo.android.onboarding.AgentConversationViewModel(
                    sessionManager = viewModel.sessionManager
                )
            }
            
            com.amigo.android.onboarding.AgentConversationScreen(
                viewModel = onboardingViewModel,
                onComplete = {
                    // Mark onboarding as complete for this user
                    val user = viewModel.getCurrentUser()
                    if (user != null) {
                        val prefs = context.getSharedPreferences("amigo_prefs", android.content.Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("hasCompletedOnboarding_${user.id}", true).apply()
                        
                        // Save profile data to Supabase
                        val profileData = onboardingViewModel.getProfileData()
                        viewModel.saveOnboardingProfile(user.id, profileData)
                        
                        hasCompletedOnboarding = true
                    }
                }
            )
        }
        else -> {
            // Show main app with bottom navigation
            MainScreen(
                authViewModel = viewModel,
                sessionManager = viewModel.sessionManager,
                onSignOut = {
                    // Sign out logic
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        viewModel.signOut()
                    }
                }
            )
        }
    }
}
