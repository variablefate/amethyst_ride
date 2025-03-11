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
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.GraphHopperInitManager
import com.vitorpamplona.amethyst.service.GraphHopperService
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.quartz.nip014173Rideshare.DriverAvailabilityEvent
import com.vitorpamplona.quartz.nip014173Rideshare.RideConfirmationEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class RideshareState(
    val availableDrivers: List<User> = emptyList(),
    val currentRide: RideState? = null,
    val isDriverMode: Boolean = false,
    val isSearching: Boolean = false,
    val routingEngineStatus: GraphHopperInitManager.InitStatus = GraphHopperInitManager.InitStatus(),
    val needsStoragePermission: Boolean = false,
)

@Immutable
data class User(
    val pubKey: String,
    val displayName: String = "Driver",
    val rating: Float = 4.5f,
)

@Immutable
data class RideState(
    val rideStage: RideStage,
    val rider: User? = null,
    val driver: User? = null,
    val driverLocation: DriverAvailabilityEvent.Location? = null,
    val destinationLocation: DriverAvailabilityEvent.Location? = null,
    val pickupLocation: DriverAvailabilityEvent.Location? = null,
    val precisePickupLocation: RideConfirmationEvent.PreciseLocation? = null,
    val fareEstimate: String? = null,
    val finalFare: String? = null,
    val lightningInvoice: String? = null,
)

enum class RideStage {
    DRIVER_AVAILABLE, // Driver has made themselves available
    RIDER_SENT_OFFER, // Rider has sent an offer to a driver
    DRIVER_ACCEPTED_OFFER, // Driver has accepted the rider's offer
    RIDER_CONFIRMED, // Rider has confirmed and sent precise pickup location
    DRIVER_ON_THE_WAY, // Driver is on the way to pickup
    DRIVER_GETTING_CLOSE, // Driver is close to pickup location
    RIDE_COMPLETED, // Ride has been completed, payment requested
    PAYMENT_COMPLETE, // Payment has been completed
}

class RideshareViewModel(
    private val account: Account,
    private val context: Context,
) : ViewModel() {
    private val rideshareRelays =
        setOf(
            "wss://relay.damus.io/",
            "wss://relay.nostr.band/",
            "wss://nostr.wine/",
        )

    // GraphHopper services
    private val graphHopperService = GraphHopperService(context)
    private val graphHopperInitManager = GraphHopperInitManager(context, graphHopperService)

    private val _state = MutableStateFlow(RideshareState())
    val state: StateFlow<RideshareState> = _state.asStateFlow()

    // Simplified approach without relying on NostrSearchDataSource
    private val availableDrivers = mutableListOf<User>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            checkNotInMainThread()

            // Check storage permissions first
            if (hasStoragePermission()) {
                // Initialize GraphHopper in the background
                initializeGraphHopper()

                // Collect the initialization status
                graphHopperInitManager.statusFlow.collect { status ->
                    _state.update { it.copy(routingEngineStatus = status) }
                }
            } else {
                // Update state to indicate that we need permission
                _state.update { it.copy(needsStoragePermission = true) }
            }

            // In a real implementation, we would subscribe to driver availability events here
            // For now, we'll simulate this with a delay and dummy data
            simulateDriverAvailability()
        }
    }

    private fun hasStoragePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10+ we don't need explicit storage permission for app-specific directories
            true
        } else {
            // For older Android versions, check WRITE_EXTERNAL_STORAGE
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }

    fun onStoragePermissionGranted() {
        _state.update { it.copy(needsStoragePermission = false) }

        viewModelScope.launch(Dispatchers.IO) {
            checkNotInMainThread()
            initializeGraphHopper()

            // Collect the initialization status
            graphHopperInitManager.statusFlow.collect { status ->
                _state.update { it.copy(routingEngineStatus = status) }
            }
        }
    }

    private suspend fun initializeGraphHopper() {
        // Initialize with a default region (e.g., "new-york")
        // In a real app, you'd determine the appropriate region based on user location
        graphHopperInitManager.initialize("new-york")
    }

    private suspend fun simulateDriverAvailability() {
        delay(2000) // Simulate network delay

        // Create some dummy driver users
        val dummyDrivers =
            listOf(
                User("npub1abc123..."), // In a real app, these would be actual pubkeys
                User("npub1def456..."),
                User("npub1ghi789..."),
            )

        _state.update {
            it.copy(
                availableDrivers = dummyDrivers,
                isSearching = false,
            )
        }
    }

    fun refreshDriverList() {
        viewModelScope.launch(Dispatchers.IO) {
            checkNotInMainThread()
            _state.update { it.copy(isSearching = true) }

            try {
                // In a real implementation, this would fetch driver availability events
                // For now, we'll just simulate with a delay
                delay(1500)
                simulateDriverAvailability()
            } catch (e: Exception) {
                Log.e("RideshareViewModel", "Error refreshing driver list", e)
                _state.update {
                    it.copy(
                        isSearching = false,
                    )
                }
            }
        }
    }

    fun setDriverMode(isDriverMode: Boolean) {
        _state.update {
            it.copy(
                isDriverMode = isDriverMode,
            )
        }
    }

    fun broadcastDriverAvailability(location: DriverAvailabilityEvent.Location) {
        // In a real implementation, this would create and broadcast a DriverAvailabilityEvent
        // For now, we'll just update the state to simulate this
        _state.update {
            it.copy(
                currentRide =
                    RideState(
                        rideStage = RideStage.DRIVER_AVAILABLE,
                        driverLocation = location,
                    ),
            )
        }
    }

    fun resetRideState() {
        _state.update {
            it.copy(
                currentRide = null,
            )
        }
    }

    fun sendPayment(invoice: String) {
        // In a real implementation, this would handle the lightning payment
        // For now, we'll just update the state to simulate payment completion
        _state.update {
            val currentRide = it.currentRide
            if (currentRide != null) {
                it.copy(
                    currentRide =
                        currentRide.copy(
                            rideStage = RideStage.PAYMENT_COMPLETE,
                        ),
                )
            } else {
                it
            }
        }
    }

    // Additional functions for handling ride states, offers, etc. would go here

    // Factory to create ViewModel with account
    class Factory(
        private val account: Account,
        private val context: Context,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = RideshareViewModel(account, context) as T
    }
} 
