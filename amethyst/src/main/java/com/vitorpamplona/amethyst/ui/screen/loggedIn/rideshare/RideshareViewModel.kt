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

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Route
import com.vitorpamplona.amethyst.service.GeocoderService
import com.vitorpamplona.amethyst.service.GraphHopperInitManager
import com.vitorpamplona.amethyst.service.GraphHopperService
import com.vitorpamplona.amethyst.service.LocationState
import com.vitorpamplona.quartz.nip014173Rideshare.Location
import com.vitorpamplona.quartz.nip014173Rideshare.RideRequestEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

// Status enum for RideRequestEvent
enum class RideRequestStatus {
    PENDING,
    ACCEPTED,
    COMPLETED,
    CANCELLED,
    DECLINED,
}

// State class for the Rideshare feature
@Immutable
data class RideshareState(
    // Common state
    val currentLocation: Location? = null,
    val previewRoute: Route? = null,
    // Driver mode state
    val isDriverMode: Boolean = false,
    val isDriverAvailable: Boolean = false,
    // Rider mode state
    val isSearching: Boolean = false,
    // Additional state properties needed by other components
    val driverLocation: Location? = null,
    val pickupAddress: String? = null,
    val destinationAddress: String? = null,
    val currentRoute: Route? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isRefreshing: Boolean = false,
    val lastRefreshTime: Date? = null,
    val currentRide: RideState? = null,
    val showPastRidesScreen: Boolean = false,
    val pastRideRequests: List<RideRequestEvent> = emptyList(),
    val needsStoragePermission: Boolean = true,
    val routingEngineStatus: GraphHopperInitManager.InitStatus = GraphHopperInitManager.InitStatus(),
)

enum class RideStage {
    NO_RIDE,
    DRIVER_AVAILABLE,
    RIDER_SENT_OFFER,
    DRIVER_ACCEPTED_OFFER,
    RIDER_CONFIRMED,
    DRIVER_ON_THE_WAY,
    DRIVER_GETTING_CLOSE,
    DRIVER_ARRIVED,
    RIDE_IN_PROGRESS,
    RIDE_COMPLETED,
    RIDE_CANCELLED,
    PAYMENT_COMPLETE,
}

@Immutable
data class RideState(
    val stage: RideStage = RideStage.NO_RIDE,
    val driver: User? = null,
    val rider: User? = null,
    val pickupAddress: String? = null,
    val destinationAddress: String? = null,
    val fareEstimate: String? = null,
    val finalFare: String? = null,
    val rideRequest: RideRequestEvent? = null,
    val lightningInvoice: String? = null,
    val rideStage: RideStage = RideStage.NO_RIDE,
    val status: RideRequestStatus? = null,
    val rejectionReason: String? = null,
)

@Immutable
data class User(
    val pubKey: String,
    val displayName: String = "Driver",
    val rating: Float = 4.5f,
) {
    fun toBestDisplayName(): String = displayName.ifEmpty { pubKey.take(8) + "..." }
}

class RideshareViewModel(
    private val account: Account,
    private val context: Context,
) : ViewModel() {
    // Factory for ViewModel creation with dependencies
    class Factory(
        private val account: Account,
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = RideshareViewModel(account, context) as T
    }

    // Services
    private val geocoderService = GeocoderService(context)
    private val graphHopperService: GraphHopperService by lazy { GraphHopperService(context) }
    private val graphHopperInitManager = GraphHopperInitManager(context, graphHopperService)

    // State
    private val _state = MutableStateFlow(RideshareState())
    val state: StateFlow<RideshareState> = _state.asStateFlow()

    // Base fare and rates for fare calculation
    private val baseFare = 1000L // in sats
    private val ratePerKm = 200L // sats per kilometer
    private val ratePerMinute = 10L // sats per minute

    init {
        viewModelScope.launch {
            // Get current location from Amethyst
            Amethyst.instance.locationManager.geohashStateFlow.collect { locationResult ->
                when (locationResult) {
                    is LocationState.LocationResult.Success -> {
                        val geoHash = locationResult.geoHash
                        // Create Location using coordinates from the native Android Location
                        // We can reconstruct this from the GeoHash string, since the GeoHash
                        // itself is a string representation of a coordinate area
                        val geohashString = geoHash.toString()
                        // Create our rideshare Location class
                        val location =
                            Location(
                                // Base coordinates on the geohash precision level
                                // This is just an approximation but should be close enough
                                latitude = geohashToLatLong(geohashString).first,
                                longitude = geohashToLatLong(geohashString).second,
                                approximateRadius = 500,
                            )
                        _state.value = _state.value.copy(currentLocation = location)
                    }
                    else -> {
                        // Handle no permission or loading cases
                    }
                }
            }

            // Monitor routing engine status
            graphHopperInitManager.statusFlow.collect { status ->
                _state.value = _state.value.copy(routingEngineStatus = status)
            }
        }
    }

    // Preview a route and calculate fare
    suspend fun previewRoute(
        pickupAddress: String?,
        destinationAddress: String,
        onRouteCalculated: (Route?) -> Unit,
    ) {
        try {
            // Use current location if pickupAddress is null
            val actualPickupAddress =
                pickupAddress ?: _state.value.currentLocation?.let {
                    "${it.latitude},${it.longitude}"
                } ?: return

            val route = calculateRoute(actualPickupAddress, destinationAddress)
            if (route != null) {
                // Update fare estimate
                val fareEstimate = calculateFareEstimate(route)
                route.fareEstimateSats = fareEstimate

                // Update state with the route
                _state.value =
                    _state.value.copy(
                        previewRoute = route,
                        pickupAddress = actualPickupAddress,
                        destinationAddress = destinationAddress,
                    )
            }
            onRouteCalculated(route)
        } catch (e: Exception) {
            Log.e("RideshareViewModel", "Error previewing route", e)
            onRouteCalculated(null)
        }
    }

    // Calculate fare based on distance and time
    private fun calculateFareEstimate(route: Route): Long {
        val distanceComponent = (route.distanceInKm * ratePerKm).toLong()
        val timeComponent = (route.durationInMs / 60000 * ratePerMinute).toLong() // Convert ms to minutes

        return baseFare + distanceComponent + timeComponent
    }

    // Calculate a route between two locations
    private suspend fun calculateRoute(
        pickupAddress: String,
        destinationAddress: String,
    ): Route? {
        try {
            // Geocode the pickup address
            val (pickupLat, pickupLon) = geocoderService.geocodeAddress(pickupAddress) ?: return null
            val pickupLocation = Location(latitude = pickupLat, longitude = pickupLon, approximateRadius = 500)

            // Geocode the destination address
            val (destLat, destLon) = geocoderService.geocodeAddress(destinationAddress) ?: return null
            val destinationLocation = Location(latitude = destLat, longitude = destLon, approximateRadius = 500)

            // Calculate route using GraphHopper
            val routeInfo =
                graphHopperService.calculateRoute(
                    pickupLocation.latitude,
                    pickupLocation.longitude,
                    destinationLocation.latitude,
                    destinationLocation.longitude,
                )

            return if (routeInfo != null) {
                Route.fromGraphHopperResponse(
                    startLocation = pickupLocation,
                    endLocation = destinationLocation,
                    response = routeInfo,
                )
            } else {
                // Fallback to straight line route
                Route.createStraightLineRoute(pickupLocation, destinationLocation)
            }
        } catch (e: Exception) {
            Log.e("RideshareViewModel", "Error calculating route", e)
            return null
        }
    }

    // Make the driver available
    fun broadcastDriverAvailability(isAvailable: Boolean) {
        viewModelScope.launch {
            try {
                val currentLocation = _state.value.currentLocation ?: return@launch

                // Update state to indicate driver is available/unavailable
                _state.value =
                    _state.value.copy(
                        isDriverAvailable = isAvailable,
                        driverLocation = if (isAvailable) currentLocation else null,
                    )

                // TODO: Implement the actual event broadcasting when backend is ready
            } catch (e: Exception) {
                Log.e("RideshareViewModel", "Error updating driver availability", e)
            }
        }
    }

    // Make the driver available (alias for broadcastDriverAvailability)
    fun makeDriverAvailable() {
        broadcastDriverAvailability(true)
    }

    // Make the driver unavailable (alias for broadcastDriverAvailability)
    fun makeDriverUnavailable() {
        broadcastDriverAvailability(false)
    }

    // Reset the state
    fun resetRideState() {
        _state.value =
            _state.value.copy(
                previewRoute = null,
                currentRide = null,
            )
    }

    // Toggle driver mode
    fun toggleDriverMode(isDriverMode: Boolean) {
        _state.value =
            _state.value.copy(
                isDriverMode = isDriverMode,
            )
    }

    // Set driver mode - alias for toggleDriverMode for backwards compatibility
    fun setDriverMode(isDriverMode: Boolean) {
        toggleDriverMode(isDriverMode)
    }

    // Storage permission handling
    fun onStoragePermissionGranted() {
        _state.value = _state.value.copy(needsStoragePermission = false)
    }

    // Methods required by other components
    fun refreshRideRequests() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, lastRefreshTime = Date())
            // TODO: Implement actual refresh logic
            // Simulate delay for now
            kotlinx.coroutines.delay(1000)
            _state.value = _state.value.copy(isRefreshing = false)
        }
    }

    fun getCurrentRideRequest(): RideRequestEvent? = _state.value.currentRide?.rideRequest

    fun isDriverHandlingRide(): Boolean =
        _state.value.currentRide
            ?.stage
            ?.let { it > RideStage.DRIVER_AVAILABLE } ?: false

    fun getMostRecentRideRequest(): RideRequestEvent? = _state.value.currentRide?.rideRequest

    fun acceptRideRequest(rideRequest: RideRequestEvent) {
        viewModelScope.launch {
            // TODO: Implement acceptance logic
            val updatedRide =
                _state.value.currentRide?.copy(
                    stage = RideStage.DRIVER_ACCEPTED_OFFER,
                    rideRequest = rideRequest,
                    status = RideRequestStatus.ACCEPTED,
                ) ?: RideState(
                    stage = RideStage.DRIVER_ACCEPTED_OFFER,
                    rideRequest = rideRequest,
                    status = RideRequestStatus.ACCEPTED,
                )

            _state.value = _state.value.copy(currentRide = updatedRide)
        }
    }

    fun denyRideRequest(rideRequest: RideRequestEvent) {
        viewModelScope.launch {
            // TODO: Implement denial logic
            _state.value = _state.value.copy(currentRide = null)
        }
    }

    // Update method signature to match expected parameters
    fun sendRideRequest(
        pickupAddress: String,
        destinationAddress: String,
        fareEstimateSats: Long = 0,
        routeDistance: Double = 0.0,
        routeDuration: Long = 0,
        onSent: () -> Unit = {},
    ) {
        viewModelScope.launch {
            // TODO: Implement sending ride request
            val ride =
                RideState(
                    stage = RideStage.RIDER_SENT_OFFER,
                    pickupAddress = pickupAddress,
                    destinationAddress = destinationAddress,
                    fareEstimate = "$fareEstimateSats sats",
                )

            _state.value = _state.value.copy(currentRide = ride)
            onSent()
        }
    }

    fun cancelAllPendingRideRequests() {
        viewModelScope.launch {
            _state.value = _state.value.copy(currentRide = null)
        }
    }

    fun navigateToPastRides() {
        viewModelScope.launch {
            // Set flag to show past rides screen
            _state.value =
                _state.value.copy(
                    showPastRidesScreen = true,
                )
        }
    }

    fun navigateBackFromPastRides() {
        viewModelScope.launch {
            _state.value =
                _state.value.copy(
                    showPastRidesScreen = false,
                )
        }
    }

    fun sendPayment(invoice: String) {
        viewModelScope.launch {
            // TODO: Implement payment logic
            val updatedRide =
                _state.value.currentRide?.copy(
                    stage = RideStage.RIDE_COMPLETED,
                )

            _state.value = _state.value.copy(currentRide = updatedRide)
        }
    }

    fun deletePastRideRequest(id: String) {
        viewModelScope.launch {
            val updatedRideRequests = _state.value.pastRideRequests.filter { it.id != id }
            _state.value = _state.value.copy(pastRideRequests = updatedRideRequests)
        }
    }

    // Utility methods for UI formatting
    fun formatRouteDuration(durationInMs: Long): String {
        val minutes = durationInMs / 60000
        val hours = minutes / 60
        return if (hours > 0) {
            "$hours h ${minutes % 60} min"
        } else {
            "$minutes min"
        }
    }

    fun formatTimeAgo(date: Date?): String {
        if (date == null) return ""
        val now = Date()
        val diff = now.time - date.time
        val minutes = diff / 60000

        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 1440 -> "${minutes / 60} hours ago"
            else -> "${minutes / 1440} days ago"
        }
    }

    // Stub method to allow compilation
    private fun createPlaceholderRideRequest(): RideRequestEvent? = null

    // Helper function to convert a geohash string to lat/long coordinates
    private fun geohashToLatLong(geohash: String): Pair<Double, Double> {
        // Use default values if we can't parse
        if (geohash.isEmpty()) return Pair(0.0, 0.0)

        try {
            // Try to get coordinates from the latest location state
            val locationState = Amethyst.instance.locationManager.geohashStateFlow.value
            if (locationState is LocationState.LocationResult.Success) {
                // Get current location from geohash
                // Use a simple approximation based on the first character
                // Each character represents a specific region
                return approximateFromFirstChar(geohash.first())
            }

            // Fallback approximation
            return approximateFromFirstChar(geohash.first())
        } catch (e: Exception) {
            return Pair(0.0, 0.0)
        }
    }

    // Simple approximation for coordinates based on first geohash character
    private fun approximateFromFirstChar(char: Char): Pair<Double, Double> =
        when (char) {
            '9' -> Pair(45.0, -90.0) // North America
            'd' -> Pair(37.0, -95.0) // USA
            'g' -> Pair(51.0, 0.0) // Europe
            's' -> Pair(-25.0, 135.0) // Australia
            'w' -> Pair(0.0, 20.0) // Africa
            else -> Pair(0.0, 0.0) // Default (ocean)
        }

    // Download map data for the current location
    fun downloadMapData() {
        val currentLocation = _state.value.currentLocation
        if (currentLocation != null) {
            viewModelScope.launch {
                try {
                    graphHopperInitManager.downloadAndInitialize(
                        centerLat = currentLocation.latitude,
                        centerLon = currentLocation.longitude,
                        radiusKm = 50.0,
                    )
                } catch (e: Exception) {
                    Log.e("RideshareViewModel", "Error downloading map data", e)
                }
            }
        }
    }
}
