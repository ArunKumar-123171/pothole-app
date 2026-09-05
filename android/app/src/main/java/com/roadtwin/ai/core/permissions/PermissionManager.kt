package com.roadtwin.ai.core.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.roadtwin.ai.core.components.RoadTwinButton
import com.roadtwin.ai.core.components.RoadTwinCard
import com.roadtwin.ai.core.location.LocationManager
import com.roadtwin.ai.core.theme.*

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun PermissionGateway(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val locationManager = remember { LocationManager(context) }

    var cameraGranted by remember {
        mutableStateOf(hasPermission(context, Manifest.permission.CAMERA))
    }
    var locationGranted by remember {
        mutableStateOf(
            hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }
    var locationServicesEnabled by remember {
        mutableStateOf(locationManager.isLocationServicesEnabled())
    }
    var bypassGpsCheck by remember { mutableStateOf(false) }

    // Re-check permissions and location hardware status on every resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameraGranted = hasPermission(context, Manifest.permission.CAMERA)
                locationGranted = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                                  hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                locationServicesEnabled = locationManager.isLocationServicesEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        cameraGranted = permissions[Manifest.permission.CAMERA] ?: hasPermission(context, Manifest.permission.CAMERA)
        locationGranted = (permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false) ||
                          (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false) ||
                          hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        locationServicesEnabled = locationManager.isLocationServicesEnabled()
    }

    val requiredPermissions = remember {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    // Auto-launch permission dialog or GPS prompt on first mount
    LaunchedEffect(Unit) {
        if (!cameraGranted || !locationGranted) {
            launcher.launch(requiredPermissions)
        } else if (!locationServicesEnabled) {
            val activity = context.findActivity()
            if (activity != null) {
                locationManager.requestLocationEnable(activity)
            }
        }
    }

    // Pass-through when permissions are granted and location services are on (or bypassed)
    if (cameraGranted && locationGranted && (locationServicesEnabled || bypassGpsCheck)) {
        content()
    } else {
        val needsLocationToggle = cameraGranted && locationGranted && !locationServicesEnabled

        Scaffold(
            containerColor = BackgroundLight
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.height(36.dp))

                    // Top Badge
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (needsLocationToggle) Color(0xFFFEF3C7) else RoadTwinBlueLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (needsLocationToggle) Icons.Default.LocationOff else Icons.Default.CameraAlt,
                            contentDescription = "Permission Status",
                            tint = if (needsLocationToggle) Color(0xFFD97706) else RoadTwinBlue,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = if (needsLocationToggle) "Turn On Device Location" else "Permissions Required",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (needsLocationToggle) {
                            "Location permission is granted, but your phone's GPS switch is turned OFF. Please enable Location so RoadTwin AI can tag potholes."
                        } else {
                            "To detect potholes and record their location, please allow the following permissions"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMediumGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Item 1: Camera
                    PermissionItemCard(
                        icon = Icons.Default.CameraAlt,
                        title = "Camera Access",
                        subtitle = "Used for real-time pothole AI detection",
                        isGranted = cameraGranted
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Item 2: Location Runtime Permission
                    PermissionItemCard(
                        icon = Icons.Default.LocationOn,
                        title = "Location Permission",
                        subtitle = "Used to capture real GPS coordinates",
                        isGranted = locationGranted
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Item 3: GPS Hardware Master Switch
                    PermissionItemCard(
                        icon = if (locationServicesEnabled) Icons.Default.LocationOn else Icons.Default.LocationOff,
                        title = "Device Location (GPS)",
                        subtitle = if (locationServicesEnabled) "GPS hardware is active and ready" else "Turn ON in quick settings or system settings",
                        isGranted = locationServicesEnabled,
                        onActionClick = if (!locationServicesEnabled) {
                            {
                                val activity = context.findActivity()
                                if (activity != null) {
                                    locationManager.requestLocationEnable(
                                        activity = activity,
                                        onFailed = { LocationManager.openLocationSettings(context) }
                                    )
                                } else {
                                    LocationManager.openLocationSettings(context)
                                }
                            }
                        } else null
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    if (needsLocationToggle) {
                        RoadTwinButton(
                            text = "Turn On Device Location",
                            onClick = {
                                val activity = context.findActivity()
                                if (activity != null) {
                                    locationManager.requestLocationEnable(
                                        activity = activity,
                                        onFailed = { LocationManager.openLocationSettings(context) }
                                    )
                                } else {
                                    LocationManager.openLocationSettings(context)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            height = 54.dp,
                            cornerRadius = 14.dp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        TextButton(
                            onClick = {
                                LocationManager.openLocationSettings(context)
                            }
                        ) {
                            Text(
                                text = "Open Device Settings",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RoadTwinBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        TextButton(
                            onClick = { bypassGpsCheck = true }
                        ) {
                            Text(
                                text = "Continue without GPS (Camera Only)",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMediumGray,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    } else {
                        RoadTwinButton(
                            text = "Grant Permissions",
                            onClick = { launcher.launch(requiredPermissions) },
                            modifier = Modifier.fillMaxWidth(),
                            height = 54.dp,
                            cornerRadius = 14.dp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = { launcher.launch(requiredPermissions) }
                        ) {
                            Text(
                                text = "Maybe Later",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMediumGray,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionItemCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onActionClick: (() -> Unit)? = null
) {
    RoadTwinCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onActionClick != null) Modifier.clickable { onActionClick() } else Modifier),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (!isGranted && onActionClick != null) Color(0xFFFEF3C7) else RoadTwinBlueLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (!isGranted && onActionClick != null) Color(0xFFD97706) else RoadTwinBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!isGranted && onActionClick != null) Color(0xFFDC2626) else TextMediumGray
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = RoadTwinGreen,
                    modifier = Modifier.size(24.dp)
                )
            } else if (onActionClick != null) {
                Text(
                    text = "Turn On",
                    color = RoadTwinBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = "Not Granted",
                    tint = CardBorderColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
