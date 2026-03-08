package com.amigo.android.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsSheet(
    onComplete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    var cameraStatus by remember { 
        mutableStateOf(
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                PermissionStatus.GRANTED
            else
                PermissionStatus.NOT_DETERMINED
        )
    }
    
    var notificationStatus by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                    PermissionStatus.GRANTED
                else
                    PermissionStatus.NOT_DETERMINED
            } else {
                PermissionStatus.GRANTED // Notifications don't need permission on older Android
            }
        )
    }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        cameraStatus = if (isGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED
    }
    
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationStatus = if (isGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = Color(0xFFE91E63)
            )
            
            Text(
                text = "Just a Few Permissions",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "To give you the best experience, I need access to a few things",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            // Permission cards
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PermissionCard(
                    icon = Icons.Default.Add, // Using Add as placeholder for camera
                    title = "Camera",
                    description = "Snap photos of meals for easy logging",
                    status = cameraStatus,
                    onRequest = {
                        cameraLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    PermissionCard(
                        icon = Icons.Default.Notifications,
                        title = "Notifications",
                        description = "Get reminders for water and fasting",
                        status = notificationStatus,
                        onRequest = {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    )
                }
                
                PermissionCard(
                    icon = Icons.Default.Favorite,
                    title = "Health Connect",
                    description = "Track activity and health metrics",
                    status = PermissionStatus.NOT_DETERMINED,
                    onRequest = {
                        // Health Connect integration would go here
                        // For now, just mark as granted
                    }
                )
            }
            
            // Continue button
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE91E63)
                )
            ) {
                Text(
                    text = "Continue",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            TextButton(onClick = onComplete) {
                Text(
                    text = "Skip",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    status: PermissionStatus,
    onRequest: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color(0xFFE91E63)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            when (status) {
                PermissionStatus.NOT_DETERMINED -> {
                    Button(
                        onClick = onRequest,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE91E63)
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("Enable")
                    }
                }
                PermissionStatus.GRANTED -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Granted",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                }
                PermissionStatus.DENIED -> {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Denied",
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

enum class PermissionStatus {
    NOT_DETERMINED,
    GRANTED,
    DENIED
}
