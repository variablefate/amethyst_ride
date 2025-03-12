/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.screen.loggedIn.rideshare

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Route
import com.vitorpamplona.amethyst.service.GraphHopperInitManager
import com.vitorpamplona.amethyst.service.LocationState
import com.vitorpamplona.amethyst.service.SatoshiFormatter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip014173Rideshare.Location
import com.vitorpamplona.quartz.nip014173Rideshare.RideRequestEvent
import kotlinx.coroutines.launch

// Create a CompositionLocal for the RideshareViewModel
val LocalRideshareViewModel =
    staticCompositionLocalOf<RideshareViewModel> {
        error("No RideshareViewModel provided")
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideshareScreen(
    accountViewModel: AccountViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val rideshareViewModel: RideshareViewModel =
        viewModel(
            factory =
                RideshareViewModel.Factory(
                    account = accountViewModel.account,
                    context = context,
                ),
        )

    val state by rideshareViewModel.state.collectAsState()

    // Show past rides screen if needed
    if (state.showPastRidesScreen) {
        PastRidesScreen(
            rideshareViewModel = rideshareViewModel,
            onBack = { rideshareViewModel.navigateBackFromPastRides() },
        )
        return
    }

    // Permission launcher for storage permissions
    val requestPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                rideshareViewModel.onStoragePermissionGranted()
            }
        }

    // Check and request permissions if needed
    LaunchedEffect(state.needsStoragePermission) {
        if (state.needsStoragePermission && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    // Provide the RideshareViewModel to the composition
    CompositionLocalProvider(LocalRideshareViewModel provides rideshareViewModel) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = stringResource(R.string.rideshare)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    actions = {
                        // Show refresh button if in driver mode
                        if (state.isDriverMode) {
                            IconButton(
                                onClick = { rideshareViewModel.refreshRideRequests() },
                                enabled = !state.isRefreshing,
                            ) {
                                if (state.isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = stringResource(R.string.refresh),
                                    )
                                }
                            }
                        }
                    },
                )
            },
        ) { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
            ) {
                // Add Routing Status Card first
                RoutingStatusCard(
                    status = state.routingEngineStatus,
                    onDownloadClick = { rideshareViewModel.downloadMapData() },
                )

                // Add Driver/Rider Mode Toggle
                Spacer(modifier = Modifier.height(8.dp))
                RideModeSelector(
                    isDriverMode = state.isDriverMode,
                    onModeChanged = { rideshareViewModel.setDriverMode(it) },
                )

                Spacer(modifier = Modifier.height(8.dp))

                // For driver mode - show a toggle to start/stop accepting rides
                if (state.isDriverMode) {
                    DriverModeSection(
                        isAvailable = state.isDriverAvailable,
                        onToggle = { isAvailable ->
                            if (isAvailable) {
                                // Make driver available - use Amethyst's location system
                                rideshareViewModel.broadcastDriverAvailability(true)
                            } else {
                                // Stop being available
                                rideshareViewModel.resetRideState()
                            }
                        },
                        isRefreshing = state.isRefreshing,
                        lastRefreshTime = state.lastRefreshTime?.let { rideshareViewModel.formatTimeAgo(it) } ?: "",
                    )

                    // Show the most recent ride request
                    val currentRequest = rideshareViewModel.getCurrentRideRequest()
                    val isHandlingRide = rideshareViewModel.isDriverHandlingRide()

                    if (isHandlingRide && currentRequest != null) {
                        // Show the ride the driver is currently handling
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.current_ride),
                                    style = MaterialTheme.typography.headlineSmall,
                                )

                                Text(
                                    text = "${stringResource(R.string.status)}: ${getRideStatusText(currentRequest.getStatus()?.let { RideRequestStatus.valueOf(it.uppercase()) } ?: RideRequestStatus.PENDING)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = getRideStatusColor(currentRequest.getStatus()?.let { RideRequestStatus.valueOf(it.uppercase()) } ?: RideRequestStatus.PENDING),
                                )

                                // Show ride details
                                AddressRow(
                                    label = stringResource(R.string.pickup_location),
                                    address = currentRequest.getPickupAddress(),
                                )

                                AddressRow(
                                    label = stringResource(R.string.destination),
                                    address = currentRequest.getDestinationAddress(),
                                )

                                // Button to cancel/complete ride
                                Button(
                                    onClick = { rideshareViewModel.resetRideState() },
                                    modifier = Modifier.align(Alignment.End),
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                        ),
                                ) {
                                    Text(text = stringResource(R.string.cancel_ride_button))
                                }
                            }
                        }
                    } else if (!isHandlingRide) {
                        // Show the most recent ride request if not handling any ride
                        val mostRecentRequest = rideshareViewModel.getMostRecentRideRequest()
                        if (mostRecentRequest != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            RideRequestCard(
                                rideRequest = mostRecentRequest,
                                onAccept = { rideshareViewModel.acceptRideRequest(mostRecentRequest) },
                                onDeny = { rideshareViewModel.denyRideRequest(mostRecentRequest) },
                            )
                        }

                        // If no active requests but refreshing, show loading indicator
                        if (mostRecentRequest == null && state.isRefreshing) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        } else if (mostRecentRequest == null && !state.isRefreshing) {
                            // Show a message that there are no active requests
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.no_active_requests),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    // For rider mode - show their current ride or request form
                    val currentRide = state.currentRide
                    if (currentRide != null) {
                        when (currentRide.stage) {
                            RideStage.RIDER_SENT_OFFER -> {
                                Column {
                                    WaitingForDriverCard(
                                        fareEstimate = currentRide.fareEstimate,
                                        onCancel = { rideshareViewModel.resetRideState() },
                                    )

                                    // Add cancellation button
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { rideshareViewModel.cancelAllPendingRideRequests() },
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                        colors =
                                            ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error,
                                            ),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Cancel,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = stringResource(R.string.cancel_ride_request))
                                    }
                                }
                            }
                            RideStage.DRIVER_ACCEPTED_OFFER -> {
                                DriverFoundCard(
                                    driver = currentRide.driver?.displayName ?: "Unknown Driver",
                                    onCancelRide = { rideshareViewModel.resetRideState() },
                                )
                            }
                            else -> {
                                // Other stages like ride in progress, etc.
                                Text("Ride in progress")
                            }
                        }
                    } else {
                        // Rider is not on a ride - show request form
                        RequestRideCard(
                            accountViewModel = accountViewModel,
                            onRideRequest = { pickupAddress, destinationAddress, route ->
                                // Send the ride request
                                if (route != null) {
                                    scope.launch {
                                        rideshareViewModel.sendRideRequest(
                                            pickupAddress = pickupAddress, // This will be empty if using current location
                                            destinationAddress = destinationAddress,
                                            fareEstimateSats = route.fareEstimateSats ?: 2500L,
                                            routeDistance = route.distanceInKm,
                                            routeDuration = route.durationInMs,
                                            onSent = { /* Handle success if needed */ },
                                        )
                                    }
                                }
                            },
                            state = state,
                            rideshareViewModel = rideshareViewModel,
                        )

                        // Add View Past Rides button
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { rideshareViewModel.navigateToPastRides() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(text = stringResource(R.string.view_past_rides))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DriverModeSection(
    isAvailable: Boolean,
    onToggle: (Boolean) -> Unit,
    isRefreshing: Boolean,
    lastRefreshTime: String,
) {
    // Request location permission
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_COARSE_LOCATION)

    // Set location permission in Amethyst
    LaunchedEffect(locationPermissionState.status.isGranted) {
        Amethyst.instance.locationManager.setLocationPermission(locationPermissionState.status.isGranted)
    }

    // Observe location updates
    val locationResult by Amethyst.instance.locationManager.geohashStateFlow
        .collectAsState()

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
        ) {
            Text(
                text = "Driver Mode",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (isAvailable) "Currently Available" else "Currently Offline",
                    style = MaterialTheme.typography.bodyLarge,
                )

                // Toggle switch for driver availability
                Switch(
                    checked = isAvailable,
                    onCheckedChange = { newValue ->
                        if (newValue) {
                            // Request location permission if not granted
                            if (!locationPermissionState.status.isGranted) {
                                locationPermissionState.launchPermissionRequest()
                            } else {
                                // Check if location is available
                                when (locationResult) {
                                    is LocationState.LocationResult.Success -> {
                                        // Use the location from Amethyst
                                        onToggle(true)
                                    }
                                    else -> {
                                        // Show a Toast or some UI indicating location is needed
                                        // We can't use LocalContext.current or LaunchedEffect here
                                        // because we're in a lambda, not a Composable function
                                        // Just toggle off without showing a toast
                                        onToggle(false)
                                    }
                                }
                            }
                        } else {
                            onToggle(false)
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isAvailable) {
                // Show status and last refreshed time
                if (isRefreshing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Refreshing...", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(
                        "Last refreshed: $lastRefreshTime",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // Show current location if available
                if (locationResult is LocationState.LocationResult.Success) {
                    val geoHash = (locationResult as LocationState.LocationResult.Success).geoHash
                    // Approximate coordinates from the geohash
                    val coords = approximateCoordinatesFromGeoHash(geoHash.toString())
                    val lat = coords.first
                    val lon = coords.second

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Current Location: ${String.format("%.6f, %.6f", lat, lon)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else if (!locationPermissionState.status.isGranted) {
                // Show location permission message
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Location Permission",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Location permission is required to be available for rides",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { locationPermissionState.launchPermissionRequest() },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

// Helper function to approximate coordinates from a geohash
private fun approximateCoordinatesFromGeoHash(geohash: String): Pair<Double, Double> {
    // Simple approximation based on first character
    if (geohash.isEmpty()) return Pair(0.0, 0.0)

    // This is just an approximation for testing purposes
    return when (geohash.first()) {
        '9' -> Pair(45.0, -90.0) // North America
        'd' -> Pair(37.0, -95.0) // USA
        'g' -> Pair(51.0, 0.0) // Europe
        's' -> Pair(-25.0, 135.0) // Australia
        'w' -> Pair(0.0, 20.0) // Africa
        else -> Pair(0.0, 0.0) // Default (ocean)
    }
}

@Composable
fun PermissionRequestCard(onRequestPermission: () -> Unit) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Storage Permission Required",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Rideshare needs storage access to download and save map data for routing calculations.",
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
fun RoutingStatusCard(
    status: GraphHopperInitManager.InitStatus,
    onDownloadClick: () -> Unit,
) {
    val statusText =
        when (status.state) {
            GraphHopperInitManager.InitState.NOT_INITIALIZED -> "Maps not downloaded"
            GraphHopperInitManager.InitState.DOWNLOADING -> "Downloading maps (${(status.progress * 100).toInt()}%)"
            GraphHopperInitManager.InitState.PROCESSING -> "Processing map data (${(status.progress * 100).toInt()}%)"
            GraphHopperInitManager.InitState.INITIALIZED -> "Maps ready"
            GraphHopperInitManager.InitState.ERROR ->
                "Error: ${status.errorMessage ?: "Unknown error"}"
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    when (status.state) {
                        GraphHopperInitManager.InitState.INITIALIZED -> MaterialTheme.colorScheme.primaryContainer
                        GraphHopperInitManager.InitState.ERROR -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (status.state == GraphHopperInitManager.InitState.DOWNLOADING ||
                    status.state == GraphHopperInitManager.InitState.PROCESSING
                ) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        progress = { status.progress },
                    )
                } else if (status.state == GraphHopperInitManager.InitState.ERROR) {
                    Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = "Error",
                        modifier = Modifier.padding(end = 8.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                } else if (status.state == GraphHopperInitManager.InitState.INITIALIZED) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Success",
                        modifier = Modifier.padding(end = 8.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }

            // Show download button if not initialized and not currently downloading
            if (status.state == GraphHopperInitManager.InitState.NOT_INITIALIZED) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDownloadClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).padding(end = 4.dp),
                    )
                    Text("Download Local Maps")
                }
            }

            // Show retry button if there was an error
            if (status.state == GraphHopperInitManager.InitState.ERROR) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDownloadClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).padding(end = 4.dp),
                    )
                    Text("Retry Download")
                }
            }
        }
    }
}

@Composable
fun RideModeSelector(
    isDriverMode: Boolean,
    onModeChanged: (Boolean) -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(text = "Rider Mode")
            }

            Switch(
                checked = isDriverMode,
                onCheckedChange = onModeChanged,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(text = "Driver Mode")
            }
        }
    }
}

@Composable
fun DriverModeContent(
    state: RideshareState,
    rideshareViewModel: RideshareViewModel,
) {
    val currentRide = state.currentRide

    if (currentRide == null) {
        // Driver is not available or on a ride
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Go Online as Driver",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Make yourself available for ride requests in your area.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        // In a real app, you would get the current location
                        // For demo purposes, use a fixed location
                        val currentLocation =
                            Location(
                                latitude = 37.7749,
                                longitude = -122.4194,
                                approximateRadius = 500,
                            )
                        rideshareViewModel.broadcastDriverAvailability(true)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Go Online")
                }
            }
        }
    } else {
        // Driver is available or on a ride - display based on stage
        when (currentRide.stage) {
            RideStage.DRIVER_AVAILABLE -> {
                DriverAvailableCard(
                    onGoOffline = { rideshareViewModel.resetRideState() },
                    route = state.currentRoute,
                )
            }
            RideStage.RIDER_SENT_OFFER -> {
                currentRide.rideRequest?.let { request ->
                    RideRequestCard(
                        rideRequest = request,
                        onAccept = { rideshareViewModel.acceptRideRequest(request) },
                        onDeny = { rideshareViewModel.denyRideRequest(request) },
                    )
                } ?: Text("No ride request available")
            }
            RideStage.DRIVER_ACCEPTED_OFFER,
            RideStage.RIDER_CONFIRMED,
            RideStage.DRIVER_ON_THE_WAY,
            RideStage.DRIVER_GETTING_CLOSE,
            -> {
                ActiveRideCard(
                    rideStage = currentRide.rideStage,
                    fareEstimate = currentRide.fareEstimate,
                    isFinalStage = currentRide.rideStage == RideStage.DRIVER_GETTING_CLOSE,
                    onNext = {
                        // In a real implementation, we would handle the actual ride progression
                        // For now, this is a placeholder
                    },
                    onCancel = {
                        rideshareViewModel.resetRideState()
                    },
                )
            }
            RideStage.RIDE_COMPLETED -> {
                RideCompletedCard(
                    finalFare = currentRide.finalFare,
                    onFinish = {
                        rideshareViewModel.resetRideState()
                    },
                )
            }
            else -> {
                // Handle other states if needed
            }
        }
    }
}

@Composable
fun DriverAvailableCard(
    onGoOffline: () -> Unit,
    route: Route? = null,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "You're Online",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Waiting for ride requests...",
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Show the route view
            RideRouteView(
                route = route,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(250.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onGoOffline,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Go Offline")
            }
        }
    }
}

@Composable
fun RequestRideCard(
    accountViewModel: AccountViewModel,
    onRideRequest: (String, String, Route?) -> Unit,
    state: RideshareState,
    rideshareViewModel: RideshareViewModel,
) {
    var pickupText by remember { mutableStateOf("") }
    var destinationText by remember { mutableStateOf("") }
    var useCurrentLocation by remember { mutableStateOf(true) }
    var showPreview by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Handle state error
    LaunchedEffect(state.error) {
        error = state.error
    }

    // Handle state loading
    LaunchedEffect(state.isLoading) {
        isLoading = state.isLoading
    }

    // Remember scope
    val scope = rememberCoroutineScope()

    // Route data
    var route by remember { mutableStateOf<Route?>(null) }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.request_a_ride),
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Current location toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = useCurrentLocation,
                    onCheckedChange = { useCurrentLocation = it },
                )
                Text(
                    text = stringResource(R.string.use_current_location),
                    modifier = Modifier.clickable { useCurrentLocation = !useCurrentLocation },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Pickup location - show only if not using current location
            if (!useCurrentLocation) {
                Text(
                    text = stringResource(R.string.pickup_location),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = pickupText,
                    onValueChange = { pickupText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.enter_pickup_address)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                        )
                    },
                    enabled = !isLoading,
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Destination
            Text(
                text = stringResource(R.string.destination),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = destinationText,
                onValueChange = { destinationText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.enter_destination_address)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                    )
                },
                enabled = !isLoading,
            )

            // Show any errors
            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Preview ride button
            Button(
                onClick = {
                    if (destinationText.isNotBlank()) {
                        isLoading = true
                        showPreview = true
                        error = null

                        scope.launch {
                            rideshareViewModel.previewRoute(
                                if (useCurrentLocation) null else pickupText,
                                destinationText,
                            ) { calculatedRoute ->
                                route = calculatedRoute
                                isLoading = false

                                // If route failed, show error
                                if (calculatedRoute == null) {
                                    error = "Could not calculate route. Please try different addresses."
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = destinationText.isNotBlank() && !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.preview_ride))
                }
            }

            // Show route preview if available
            if (showPreview && route != null) {
                Spacer(modifier = Modifier.height(16.dp))

                // Route preview card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                    ) {
                        // Route details
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.distance),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = String.format("%.1f %s", route?.distanceInKm ?: 0.0, stringResource(R.string.km)),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Column {
                                Text(
                                    text = stringResource(R.string.duration),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = formatDuration(route?.durationInMs ?: 0),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Column {
                                Text(
                                    text = stringResource(R.string.fare_estimate),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "${route?.fareEstimateSats ?: 0} ${stringResource(R.string.sats)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Request ride button
                        Button(
                            onClick = {
                                onRideRequest(
                                    if (useCurrentLocation) {
                                        state.currentLocation?.let { "${it.latitude},${it.longitude}" } ?: ""
                                    } else {
                                        pickupText
                                    },
                                    destinationText,
                                    route,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading,
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(stringResource(R.string.request_ride))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper function to format duration
private fun formatDuration(durationMs: Long): String {
    val minutes = (durationMs / (1000 * 60)).toInt()
    val hours = minutes / 60
    val remainingMinutes = minutes % 60

    return if (hours > 0) {
        "$hours h $remainingMinutes min"
    } else {
        "$remainingMinutes min"
    }
}

@Composable
fun ActiveRideCard(
    rideStage: RideStage,
    fareEstimate: String?,
    isFinalStage: Boolean,
    onNext: () -> Unit,
    onCancel: () -> Unit,
) {
    val stageTitle =
        when (rideStage) {
            RideStage.DRIVER_ACCEPTED_OFFER -> "Ride Accepted"
            RideStage.RIDER_CONFIRMED -> "Ride Confirmed"
            RideStage.DRIVER_ON_THE_WAY -> "On the Way"
            RideStage.DRIVER_GETTING_CLOSE -> "Getting Close"
            else -> "Active Ride"
        }

    val stageDescription =
        when (rideStage) {
            RideStage.DRIVER_ACCEPTED_OFFER -> "Waiting for rider to confirm..."
            RideStage.RIDER_CONFIRMED -> "Rider has confirmed the ride. Head to pickup location."
            RideStage.DRIVER_ON_THE_WAY -> "You're on the way to the pickup location."
            RideStage.DRIVER_GETTING_CLOSE -> "You're getting close to pickup location."
            else -> "Ride in progress."
        }

    val buttonText =
        when (rideStage) {
            RideStage.RIDER_CONFIRMED -> "Start Ride"
            RideStage.DRIVER_ON_THE_WAY -> "Almost There"
            RideStage.DRIVER_GETTING_CLOSE -> "Complete Ride"
            else -> "Next"
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stageTitle,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(stageDescription)

            Spacer(modifier = Modifier.height(16.dp))

            fareEstimate?.let {
                Text("Fare Estimate: ${SatoshiFormatter.format(it.toLong())}")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(buttonText)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.cancel_current_ride))
            }
        }
    }
}

@Composable
fun RideCompletedCard(
    finalFare: String?,
    onFinish: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Ride Completed",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("You've completed the ride. Waiting for payment...")

            Spacer(modifier = Modifier.height(16.dp))

            finalFare?.let {
                Text("Final Fare: ${SatoshiFormatter.format(it.toLong())}")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Finish")
            }
        }
    }
}

// Rider Cards
@Composable
fun WaitingForDriverCard(
    fareEstimate: String?,
    onCancel: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.waiting_for_driver),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.height(16.dp))

            CircularProgressIndicator(modifier = Modifier.padding(8.dp))

            Text(stringResource(R.string.searching_for_drivers))

            Spacer(modifier = Modifier.height(16.dp))

            fareEstimate?.let {
                Text("Fare Estimate: ${SatoshiFormatter.format(it.toLong())}")
            }

            // Add auto-cancellation notice
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.auto_cancellation_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.cancel_current_ride))
            }
        }
    }
}

@Composable
fun DriverFoundCard(
    driver: String,
    onCancelRide: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.driver_found),
                style = MaterialTheme.typography.headlineSmall,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = driver,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                text = stringResource(R.string.driver_on_the_way),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onCancelRide,
                modifier = Modifier.align(Alignment.End),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(text = stringResource(R.string.cancel_current_ride))
            }
        }
    }
}

@Composable
fun getRideStatusText(status: RideRequestStatus): String =
    when (status) {
        RideRequestStatus.PENDING -> stringResource(R.string.pending)
        RideRequestStatus.ACCEPTED -> stringResource(R.string.accepted)
        RideRequestStatus.COMPLETED -> stringResource(R.string.completed)
        RideRequestStatus.CANCELLED -> stringResource(R.string.cancelled)
        RideRequestStatus.DECLINED -> stringResource(R.string.declined)
    }

@Composable
fun getRideStatusColor(status: RideRequestStatus): Color =
    when (status) {
        RideRequestStatus.PENDING -> MaterialTheme.colorScheme.primary
        RideRequestStatus.ACCEPTED -> Color(0xFF2E7D32) // Green
        RideRequestStatus.COMPLETED -> Color(0xFF2E7D32) // Green
        RideRequestStatus.CANCELLED -> Color(0xFFD32F2F) // Red
        RideRequestStatus.DECLINED -> Color(0xFFD32F2F) // Red
    }

// Fix the AddressRow composable
@Composable
fun AddressRow(
    label: String,
    address: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!address.isNullOrEmpty()) {
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    text = stringResource(R.string.not_specified),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
fun RideRequestCard(
    rideRequest: RideRequestEvent,
    onAccept: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header with title and timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.ride_request),
                    style = MaterialTheme.typography.headlineSmall,
                )

                Text(
                    text = rideRequest.formatTimeAgo(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Rider info
            Text(
                text = "Rider: ${rideRequest.pubKey.take(8)}...",
                style = MaterialTheme.typography.bodyMedium,
            )

            // Pickup and destination
            AddressRow(
                label = stringResource(R.string.pickup_location),
                address = rideRequest.getPickupAddress(),
            )

            AddressRow(
                label = stringResource(R.string.destination),
                address = rideRequest.getDestinationAddress(),
            )

            // Distance, duration and fare estimate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (rideRequest.getRouteDistance() != null) {
                    Text(
                        text = "${String.format("%.1f", rideRequest.getRouteDistance())} km",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                rideRequest.formatRouteDuration()?.let { duration ->
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Text(
                    text = "${rideRequest.getFareEstimateSats() ?: 0} sats",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDeny,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.deny))
                }

                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.accept))
                }
            }
        }
    }
} 
