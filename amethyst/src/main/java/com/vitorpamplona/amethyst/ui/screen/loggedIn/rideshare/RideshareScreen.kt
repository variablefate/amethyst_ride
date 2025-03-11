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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.GraphHopperInitManager
import com.vitorpamplona.amethyst.service.SatoshiFormatter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip014173Rideshare.DriverAvailabilityEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideshareScreen(
    accountViewModel: AccountViewModel,
    onBack: () -> Unit,
) {
    val account = accountViewModel.account

    val rideshareViewModel: RideshareViewModel =
        viewModel(
            factory = RideshareViewModel.Factory(account),
        )

    val state by rideshareViewModel.state.collectAsState()
    val context = LocalContext.current

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

    DisappearingScaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringRes(R.string.rideshare)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringRes(R.string.back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Storage Permission Card
            if (state.needsStoragePermission) {
                PermissionRequestCard {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                }
            }

            // Routing Engine Status
            RoutingStatusCard(state.routingEngineStatus)

            // Driver/Rider Mode Switch
            RideModeSelector(
                isDriverMode = state.isDriverMode,
                onModeChanged = { rideshareViewModel.setDriverMode(it) },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Main content based on mode
            if (state.isDriverMode) {
                DriverModeContent(
                    state = state,
                    rideshareViewModel = rideshareViewModel,
                )
            } else {
                RiderModeContent(
                    state = state,
                    rideshareViewModel = rideshareViewModel,
                )
            }
        }
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
fun RoutingStatusCard(status: GraphHopperInitManager.InitStatus) {
    val statusText =
        when (status.state) {
            GraphHopperInitManager.InitState.NOT_INITIALIZED -> "Routing engine not initialized"
            GraphHopperInitManager.InitState.DOWNLOADING ->
                "Downloading map data (${(status.progress * 100).toInt()}%)"
            GraphHopperInitManager.InitState.PROCESSING -> "Processing map data"
            GraphHopperInitManager.InitState.INITIALIZED -> "Routing engine ready"
            GraphHopperInitManager.InitState.ERROR ->
                "Routing engine error: ${status.errorMessage ?: "Unknown error"}"
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
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                )
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
            )
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
                            DriverAvailabilityEvent.Location(
                                lat = 37.7749,
                                lon = -122.4194,
                            )
                        rideshareViewModel.broadcastDriverAvailability(currentLocation)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Go Online")
                }
            }
        }
    } else {
        // Driver is available or on a ride - display based on stage
        when (currentRide.rideStage) {
            RideStage.DRIVER_AVAILABLE -> {
                DriverAvailableCard {
                    rideshareViewModel.resetRideState()
                }
            }
            RideStage.RIDER_SENT_OFFER -> {
                RideRequestCard(
                    rider = currentRide.rider?.toBestDisplayName() ?: "Unknown Rider",
                    fareEstimate = currentRide.fareEstimate,
                    onAccept = {
                        // In a real implementation, we would get the RideOfferEvent
                        // For now, this is a placeholder
                    },
                    onDecline = {
                        rideshareViewModel.resetRideState()
                    },
                )
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
fun RiderModeContent(
    state: RideshareState,
    rideshareViewModel: RideshareViewModel,
) {
    val currentRide = state.currentRide

    if (currentRide == null) {
        // Rider is not on a ride - show request form
        RequestRideCard(
            isSearching = state.isSearching,
            hasAvailableDrivers = state.availableDrivers.isNotEmpty(),
            onSearchClick = {
                rideshareViewModel.refreshDriverList()
            },
            onRideRequest = { destination, pickup ->
                // In a real implementation, we would create a ride offer
                // For now, this is a placeholder
            },
        )
    } else {
        // Rider is on a ride - display based on stage
        when (currentRide.rideStage) {
            RideStage.RIDER_SENT_OFFER -> {
                WaitingForDriverCard(
                    fareEstimate = currentRide.fareEstimate,
                    onCancel = {
                        rideshareViewModel.resetRideState()
                    },
                )
            }
            RideStage.DRIVER_ACCEPTED_OFFER -> {
                DriverAcceptedCard(
                    driver = currentRide.driver?.toBestDisplayName() ?: "Unknown Driver",
                    fareEstimate = currentRide.fareEstimate,
                    onConfirm = {
                        // In a real implementation, we would confirm the ride
                        // For now, this is a placeholder
                    },
                    onCancel = {
                        rideshareViewModel.resetRideState()
                    },
                )
            }
            RideStage.DRIVER_ON_THE_WAY, RideStage.DRIVER_GETTING_CLOSE -> {
                DriverEnRouteCard(
                    status =
                        if (currentRide.rideStage == RideStage.DRIVER_GETTING_CLOSE) {
                            "Driver is getting close"
                        } else {
                            "Driver is on the way"
                        },
                    fareEstimate = currentRide.fareEstimate,
                    onCancel = {
                        rideshareViewModel.resetRideState()
                    },
                )
            }
            RideStage.RIDE_COMPLETED -> {
                PaymentRequestCard(
                    finalFare = currentRide.finalFare,
                    invoice = currentRide.lightningInvoice,
                    onPay = {
                        currentRide.lightningInvoice?.let {
                            rideshareViewModel.sendPayment(it)
                        }
                    },
                )
            }
            RideStage.PAYMENT_COMPLETE -> {
                PaymentCompleteCard {
                    rideshareViewModel.resetRideState()
                }
            }
            else -> {
                // Handle other states if needed
            }
        }
    }
}

// Driver Cards
@Composable
fun DriverAvailableCard(onGoOffline: () -> Unit) {
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

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Waiting for ride requests...",
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(24.dp))

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
fun RideRequestCard(
    rider: String,
    fareEstimate: String?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
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
                text = "Ride Request",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("From: $rider")

            Spacer(modifier = Modifier.height(8.dp))

            fareEstimate?.let {
                Text("Fare Estimate: ${SatoshiFormatter.formatSatoshisForDisplay(it)}")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                ) {
                    Text("Decline")
                }

                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                ) {
                    Text("Accept")
                }
            }
        }
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
                Text("Fare Estimate: ${SatoshiFormatter.formatSatoshisForDisplay(it)}")
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
                Text("Cancel Ride")
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
                Text("Final Fare: ${SatoshiFormatter.formatSatoshisForDisplay(it)}")
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
fun RequestRideCard(
    isSearching: Boolean,
    hasAvailableDrivers: Boolean,
    onSearchClick: () -> Unit,
    onRideRequest: (String, String) -> Unit,
) {
    var destination by remember { mutableStateOf("") }
    var pickup by remember { mutableStateOf("") }

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
                text = "Request a Ride",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = pickup,
                onValueChange = { pickup = it },
                label = { Text("Pickup Location") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = destination,
                onValueChange = { destination = it },
                label = { Text("Destination") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                if (!hasAvailableDrivers) {
                    Button(
                        onClick = onSearchClick,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Find Drivers")
                    }
                } else {
                    Button(
                        onClick = { onRideRequest(destination, pickup) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = destination.isNotBlank() && pickup.isNotBlank(),
                    ) {
                        Text("Request Ride")
                    }
                }
            }
        }
    }
}

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
                text = "Waiting for Driver",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.height(16.dp))

            CircularProgressIndicator(modifier = Modifier.padding(8.dp))

            Text("Your ride offer has been sent. Waiting for driver to accept...")

            Spacer(modifier = Modifier.height(16.dp))

            fareEstimate?.let {
                Text("Fare Estimate: ${SatoshiFormatter.formatSatoshisForDisplay(it)}")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel Request")
            }
        }
    }
}

@Composable
fun DriverAcceptedCard(
    driver: String,
    fareEstimate: String?,
    onConfirm: () -> Unit,
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
        ) {
            Text(
                text = "Driver Accepted",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Driver: $driver has accepted your ride request!")

            Spacer(modifier = Modifier.height(16.dp))

            fareEstimate?.let {
                Text("Fare Estimate: ${SatoshiFormatter.formatSatoshisForDisplay(it)}")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Confirm Ride")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel Ride")
            }
        }
    }
}

@Composable
fun DriverEnRouteCard(
    status: String,
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
        ) {
            Text(
                text = "Driver En Route",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(status)

            Spacer(modifier = Modifier.height(16.dp))

            fareEstimate?.let {
                Text("Fare Estimate: ${SatoshiFormatter.formatSatoshisForDisplay(it)}")
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel Ride")
            }
        }
    }
}

@Composable
fun PaymentRequestCard(
    finalFare: String?,
    invoice: String?,
    onPay: () -> Unit,
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

            Text("Your ride has been completed. Please make payment.")

            Spacer(modifier = Modifier.height(16.dp))

            finalFare?.let {
                Text("Final Fare: ${SatoshiFormatter.formatSatoshisForDisplay(it)}")
            }

            Spacer(modifier = Modifier.height(16.dp))

            invoice?.let {
                // In a real app, you might use a QR code here
                // or connect to a lightning wallet
                Text(
                    "Invoice: ${it.take(20)}...",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onPay,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Pay Now")
            }
        }
    }
}

@Composable
fun PaymentCompleteCard(onFinish: () -> Unit) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Payment Complete",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Thank you for using Amethyst Ride! Your payment has been processed.",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Done")
            }
        }
    }
} 
