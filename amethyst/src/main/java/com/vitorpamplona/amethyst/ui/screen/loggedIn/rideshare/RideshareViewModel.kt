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
import android.widget.Toast
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Route
import com.vitorpamplona.amethyst.service.GeocoderService
import com.vitorpamplona.amethyst.service.GraphHopperInitManager
import com.vitorpamplona.amethyst.service.GraphHopperService
import com.vitorpamplona.amethyst.service.LocationState
import com.vitorpamplona.quartz.nip014173Rideshare.DriverAvailabilityEvent
import com.vitorpamplona.quartz.nip014173Rideshare.Location
import com.vitorpamplona.quartz.nip014173Rideshare.RideRequestEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.ArrayList
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
            Log.d("RideshareViewModel", "Previewing route from $pickupAddress to $destinationAddress")

            // Update state to show loading
            _state.value =
                _state.value.copy(
                    isLoading = true,
                    error = null,
                )

            // Use current location if pickupAddress is null
            val actualPickupAddress =
                pickupAddress ?: _state.value.currentLocation?.let {
                    "${it.latitude},${it.longitude}"
                } ?: "37.7749,-122.4194" // San Francisco as default if no location

            Log.d("RideshareViewModel", "Using pickup address: $actualPickupAddress")

            val route = calculateRoute(actualPickupAddress, destinationAddress)
            if (route != null) {
                // Update fare estimate
                val fareEstimate = calculateFareEstimate(route)
                route.fareEstimateSats = fareEstimate

                Log.d("RideshareViewModel", "Route calculated: ${route.distanceInKm} km, ${route.durationInMs} ms, $fareEstimate sats")

                // Update state with the route
                _state.value =
                    _state.value.copy(
                        previewRoute = route,
                        pickupAddress = actualPickupAddress,
                        destinationAddress = destinationAddress,
                        isLoading = false,
                    )
                onRouteCalculated(route)
            } else {
                Log.e("RideshareViewModel", "Failed to calculate route")
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = "Could not calculate route. Please try different addresses.",
                    )
                onRouteCalculated(null)
            }
        } catch (e: Exception) {
            Log.e("RideshareViewModel", "Error previewing route", e)
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    error = "Error: ${e.message ?: "Unknown error"}",
                )
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
            Log.d("RideshareViewModel", "Calculating route from $pickupAddress to $destinationAddress")

            // Geocode the pickup address
            val pickupCoords = geocoderService.geocodeAddress(pickupAddress)
            if (pickupCoords == null) {
                Log.e("RideshareViewModel", "Could not geocode pickup address: $pickupAddress")

                // Try parsing coordinates directly if it's in "lat,lon" format
                val pickupParts = pickupAddress.split(",").map { it.trim() }
                if (pickupParts.size == 2) {
                    try {
                        val lat = pickupParts[0].toDouble()
                        val lon = pickupParts[1].toDouble()
                        Log.d("RideshareViewModel", "Parsed coordinates from pickup address: $lat, $lon")
                        val pickupLocation = Location(latitude = lat, longitude = lon, approximateRadius = 500)

                        // Geocode the destination address
                        val destCoords = geocoderService.geocodeAddress(destinationAddress)
                        if (destCoords == null) {
                            Log.e("RideshareViewModel", "Could not geocode destination address: $destinationAddress")
                            return null
                        }

                        val destinationLocation = Location(latitude = destCoords.first, longitude = destCoords.second, approximateRadius = 500)

                        // Use straight line route as fallback
                        Log.d("RideshareViewModel", "Using straight line route as fallback")
                        return Route.createStraightLineRoute(pickupLocation, destinationLocation)
                    } catch (e: Exception) {
                        Log.e("RideshareViewModel", "Error parsing coordinates", e)
                        return null
                    }
                } else {
                    return null
                }
            }

            val pickupLocation = Location(latitude = pickupCoords.first, longitude = pickupCoords.second, approximateRadius = 500)

            // Geocode the destination address
            val destCoords = geocoderService.geocodeAddress(destinationAddress)
            if (destCoords == null) {
                Log.e("RideshareViewModel", "Could not geocode destination address: $destinationAddress")
                return null
            }
            val destinationLocation = Location(latitude = destCoords.first, longitude = destCoords.second, approximateRadius = 500)

            // Calculate route using GraphHopper
            val routeInfo =
                graphHopperService.calculateRoute(
                    pickupLocation.latitude,
                    pickupLocation.longitude,
                    destinationLocation.latitude,
                    destinationLocation.longitude,
                )

            return if (routeInfo != null) {
                Log.d("RideshareViewModel", "GraphHopper route calculated successfully")
                Route.fromGraphHopperResponse(
                    startLocation = pickupLocation,
                    endLocation = destinationLocation,
                    response = routeInfo,
                )
            } else {
                // Fallback to straight line route
                Log.d("RideshareViewModel", "GraphHopper routing failed, using straight line route as fallback")
                Route.createStraightLineRoute(pickupLocation, destinationLocation)
            }
        } catch (e: Exception) {
            Log.e("RideshareViewModel", "Error calculating route", e)
            return null
        }
    }

    // Toggle driver mode
    fun toggleDriverMode(isDriverMode: Boolean) {
        Log.d("RideshareViewModel", "toggleDriverMode called with: isDriverMode=$isDriverMode")

        viewModelScope.launch {
            try {
                // Update state to reflect the new mode
                Log.d("RideshareViewModel", "Setting state.isDriverMode=$isDriverMode")
                _state.value =
                    _state.value.copy(
                        isDriverMode = isDriverMode,
                        // When switching to rider mode, ensure driver is unavailable
                        isDriverAvailable = if (!isDriverMode) false else _state.value.isDriverAvailable,
                    )
                Log.d("RideshareViewModel", "State updated: isDriverMode=${_state.value.isDriverMode}, isDriverAvailable=${_state.value.isDriverAvailable}")

                // If changing to driver mode, check if we have the current location
                if (isDriverMode) {
                    // Check if we have location access
                    val locationAvailable = Amethyst.instance.locationManager.geohashStateFlow.value
                    Log.d("RideshareViewModel", "Location check: locationAvailable=$locationAvailable")

                    if (locationAvailable !is LocationState.LocationResult.Success) {
                        Log.d("RideshareViewModel", "No location available for driver mode")

                        // Get a default location
                        val defaultLocation =
                            Location(
                                latitude = 37.7749, // San Francisco as default
                                longitude = -122.4194,
                                approximateRadius = 500,
                            )

                        // Update the current location for driver mode
                        Log.d("RideshareViewModel", "Setting default location: $defaultLocation")
                        _state.value =
                            _state.value.copy(
                                currentLocation = defaultLocation,
                            )
                        Log.d("RideshareViewModel", "Default location set")
                    }
                }

                Log.d("RideshareViewModel", "Driver mode toggled successfully")
            } catch (e: Exception) {
                Log.e("RideshareViewModel", "Error toggling driver mode", e)
            }
        }
    }

    // Set driver mode - alias for toggleDriverMode for backwards compatibility
    fun setDriverMode(isDriverMode: Boolean) {
        Log.d("RideshareViewModel", "setDriverMode called with: isDriverMode=$isDriverMode")
        toggleDriverMode(isDriverMode)
    }

    // Make the driver available
    fun broadcastDriverAvailability(isAvailable: Boolean) {
        viewModelScope.launch {
            try {
                Log.d("RideshareViewModel", "Starting driver availability broadcast process: isAvailable=$isAvailable")

                // First, check if account is writeable
                val isWriteable = account.isWriteable()
                Log.d("RideshareViewModel", "Account writeable check: $isWriteable")

                if (!isWriteable) {
                    Log.e("RideshareViewModel", "Account is not writeable. Cannot broadcast driver availability.")
                    Toast
                        .makeText(
                            context,
                            "Cannot toggle driver mode: Account is read-only",
                            Toast.LENGTH_LONG,
                        ).show()
                    // Don't update state, so the toggle stays off
                    return@launch
                }

                // Check if we have any active relays with write permission
                val writeRelays = account.activeWriteRelays()
                Log.d("RideshareViewModel", "Active write relays count: ${writeRelays.size}")

                if (writeRelays.isEmpty()) {
                    Log.e("RideshareViewModel", "No active relays with write permission. Cannot broadcast driver availability.")
                    Toast
                        .makeText(
                            context,
                            "Cannot broadcast driver availability: No relays with write permission",
                            Toast.LENGTH_LONG,
                        ).show()
                    return@launch
                }

                // Use all write relays - we'll let the relay pool handle connection status
                val connectedWriteRelays = writeRelays

                // Use current location or default if not available
                val currentLocation =
                    _state.value.currentLocation ?: Location(
                        latitude = 37.7749, // San Francisco as default
                        longitude = -122.4194,
                        approximateRadius = 500,
                    )

                Log.d("RideshareViewModel", "Using location: lat=${currentLocation.latitude}, lon=${currentLocation.longitude}")

                if (isAvailable) {
                    // Create and broadcast a DriverAvailabilityEvent (Kind 3000)
                    val signer = account.signer
                    if (signer != null) {
                        Log.d("RideshareViewModel", "Signer is available: ${signer.javaClass.simpleName}")

                        try {
                            Log.d("RideshareViewModel", "Broadcasting driver availability with location: ${currentLocation.latitude}, ${currentLocation.longitude}")

                            // Update state before sending the event to show UI response immediately
                            Log.d("RideshareViewModel", "Updating state to show driver available")
                            _state.value =
                                _state.value.copy(
                                    isDriverAvailable = true,
                                    driverLocation = currentLocation,
                                    currentRide = RideState(rideStage = RideStage.DRIVER_AVAILABLE),
                                )

                            // Create the event using the DriverAvailabilityEvent class
                            // This will publish a Kind 3000 event as specified in NIP-014173
                            Log.d("RideshareViewModel", "Creating DriverAvailabilityEvent")

                            DriverAvailabilityEvent.create(
                                approxLocation = currentLocation,
                                signer = signer,
                                createdAt = TimeUtils.now(),
                                onReady = { event ->
                                    try {
                                        Log.d("RideshareViewModel", "Driver availability event created successfully: id=${event.id}, kind=${event.kind}")
                                        Log.d("RideshareViewModel", "Event content: ${event.content}")
                                        Log.d("RideshareViewModel", "Tags: ${event.tags.joinToString()}")
                                        Log.d("RideshareViewModel", "Broadcasting to relays via Amethyst client")

                                        // Show toast to indicate we're sending the event
                                        Toast
                                            .makeText(
                                                context,
                                                "Broadcasting driver availability to ${connectedWriteRelays.size} relays...",
                                                Toast.LENGTH_SHORT,
                                            ).show()

                                        // Directly send to connected active write relays to ensure it goes through
                                        Amethyst.instance.client.send(event, connectedWriteRelays)
                                        Log.d("RideshareViewModel", "Event sent to client successfully")

                                        LocalCache.justConsume(event, null)
                                        Log.d("RideshareViewModel", "Event consumed by LocalCache")

                                        // Set last refresh time
                                        _state.value =
                                            _state.value.copy(
                                                lastRefreshTime = Date(),
                                            )
                                        Log.d("RideshareViewModel", "Driver availability event successfully sent to relays")

                                        // Show success message
                                        Toast
                                            .makeText(
                                                context,
                                                "Driver mode activated successfully",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    } catch (e: Exception) {
                                        Log.e("RideshareViewModel", "Error sending driver availability event to relays", e)
                                        // If we fail to send, revert the state
                                        _state.value =
                                            _state.value.copy(
                                                isDriverAvailable = false,
                                                driverLocation = null,
                                                currentRide = null,
                                            )
                                        Log.d("RideshareViewModel", "State reverted after send failure")

                                        // Show error message
                                        Toast
                                            .makeText(
                                                context,
                                                "Failed to broadcast availability: ${e.message}",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                    }
                                },
                            )

                            Log.d("RideshareViewModel", "DriverAvailabilityEvent.create called, waiting for callback")
                        } catch (e: Exception) {
                            Log.e("RideshareViewModel", "Error creating driver availability event", e)
                            // If we fail to create the event, revert the state
                            _state.value =
                                _state.value.copy(
                                    isDriverAvailable = false,
                                    driverLocation = null,
                                    currentRide = null,
                                )
                            Log.d("RideshareViewModel", "State reverted after creation failure")

                            // Show error message
                            Toast
                                .makeText(
                                    context,
                                    "Failed to create driver availability event: ${e.message}",
                                    Toast.LENGTH_LONG,
                                ).show()
                        }
                    } else {
                        Log.e("RideshareViewModel", "Failed to broadcast driver availability: No signer available")
                        // If no signer is available, keep the toggle off
                        _state.value =
                            _state.value.copy(
                                isDriverAvailable = false,
                                driverLocation = null,
                                currentRide = null,
                            )
                        Log.d("RideshareViewModel", "State reset due to missing signer")

                        // Show error message
                        Toast
                            .makeText(
                                context,
                                "Failed to broadcast availability: No signer available",
                                Toast.LENGTH_LONG,
                            ).show()
                    }
                } else {
                    // Update state to indicate driver is unavailable
                    Log.d("RideshareViewModel", "Setting driver to unavailable")
                    _state.value =
                        _state.value.copy(
                            isDriverAvailable = false,
                            driverLocation = null,
                            currentRide = null,
                        )

                    // If driver is going offline, delete the last driver availability event
                    Log.d("RideshareViewModel", "Deleting previous driver availability events")
                    deleteDriverAvailabilityEvent()

                    // Show success message
                    Toast
                        .makeText(
                            context,
                            "Driver mode deactivated",
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            } catch (e: Exception) {
                Log.e("RideshareViewModel", "Error updating driver availability", e)
                // If any exception occurs, make sure the toggle is off
                _state.value =
                    _state.value.copy(
                        isDriverAvailable = false,
                        driverLocation = null,
                        currentRide = null,
                    )
                Log.d("RideshareViewModel", "State reset due to exception")

                // Show error message
                Toast
                    .makeText(
                        context,
                        "Error updating driver availability: ${e.message}",
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }
    }

    // Reset the state
    fun resetRideState() {
        _state.value =
            _state.value.copy(
                previewRoute = null,
                currentRide = null,
            )
    }

    // New method to delete driver availability events when going offline
    private fun deleteDriverAvailabilityEvent() {
        viewModelScope.launch {
            try {
                Log.d("RideshareViewModel", "Deleting previous driver availability events")

                // Find the driver availability events from this user
                val myDriverAvailabilityEvents = ArrayList<Event>()

                // Since we don't have a direct way to query by kind and author in LocalCache,
                // we need to go through the notes to find our driver availability events
                val userPubkey = account.userProfile().pubkeyHex

                LocalCache.notes.forEach { _, note ->
                    val event = note.event
                    if (event is DriverAvailabilityEvent && event.pubKey == userPubkey) {
                        myDriverAvailabilityEvents.add(event)
                    }
                }

                if (myDriverAvailabilityEvents.isNotEmpty()) {
                    Log.d("RideshareViewModel", "Found ${myDriverAvailabilityEvents.size} driver availability events to delete")

                    // Create deletion event
                    account.signer?.sign(
                        DeletionEvent.build(myDriverAvailabilityEvents),
                    ) { deletionEvent ->
                        Amethyst.instance.client.send(deletionEvent)
                        LocalCache.justConsume(deletionEvent, null)
                        Log.d("RideshareViewModel", "Driver availability deletion event sent")
                    }
                } else {
                    Log.d("RideshareViewModel", "No driver availability events found to delete")
                }
            } catch (e: Exception) {
                Log.e("RideshareViewModel", "Error deleting driver availability events", e)
            }
        }
    }

    // New method to cancel a ride and delete associated events
    fun cancelRide(rideRequest: RideRequestEvent?) {
        viewModelScope.launch {
            try {
                if (rideRequest == null) {
                    Log.d("RideshareViewModel", "No ride request to cancel")
                    return@launch
                }

                Log.d("RideshareViewModel", "Cancelling ride and deleting associated events")

                // Find all events associated with this ride
                val rideId = rideRequest.id
                val eventsToDelete = ArrayList<Event>()

                // Add the ride request itself if it's ours
                val userPubkey = account.userProfile().pubkeyHex
                if (rideRequest.pubKey == userPubkey) {
                    eventsToDelete.add(rideRequest)
                }

                // We need to search for events related to this ride request
                // This involves checking each note in the cache
                LocalCache.notes.forEach { _, note ->
                    val event = note.event
                    if (event != null && event.pubKey == userPubkey) {
                        // Check if this event references our ride request
                        val tags = event.tags
                        for (tag in tags) {
                            if (tag.size > 1 && tag[0] == "e" && tag[1] == rideId) {
                                eventsToDelete.add(event)
                                break
                            }
                        }
                    }
                }

                // Create deletion event if we have events to delete
                if (eventsToDelete.isNotEmpty()) {
                    Log.d("RideshareViewModel", "Found ${eventsToDelete.size} events to delete related to ride $rideId")

                    account.signer?.sign(
                        DeletionEvent.build(eventsToDelete),
                    ) { deletionEvent ->
                        Amethyst.instance.client.send(deletionEvent)
                        LocalCache.justConsume(deletionEvent, null)
                        Log.d("RideshareViewModel", "Ride cancellation and deletion events sent")
                    }

                    // Clear the current ride state
                    _state.value = _state.value.copy(currentRide = null)
                } else {
                    Log.d("RideshareViewModel", "No events found to delete for this ride")
                }
            } catch (e: Exception) {
                Log.e("RideshareViewModel", "Error cancelling ride", e)
            }
        }
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

    // Update the existing cancel ride method to use our new deletion method
    fun cancelAllPendingRideRequests() {
        viewModelScope.launch {
            // Get the current ride request and cancel it
            val currentRideRequest = getCurrentRideRequest()
            if (currentRideRequest != null) {
                cancelRide(currentRideRequest)
            } else {
                // Just update the state if there's no actual ride request
                _state.value = _state.value.copy(currentRide = null)
            }
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
        viewModelScope.launch {
            try {
                // Use current location if available, or fallback to a default location
                val location =
                    _state.value.currentLocation ?: Location(
                        latitude = 37.7749, // San Francisco as default
                        longitude = -122.4194,
                        approximateRadius = 500,
                    )

                Log.d("RideshareViewModel", "Attempting to download map data for location: ${location.latitude}, ${location.longitude}")

                // Update state to show downloading
                _state.value =
                    _state.value.copy(
                        isLoading = true,
                        error = null,
                    )

                // Call the download function
                val result =
                    graphHopperInitManager.downloadAndInitialize(
                        centerLat = location.latitude,
                        centerLon = location.longitude,
                        radiusKm = 50.0,
                    )

                if (result) {
                    Log.d("RideshareViewModel", "Map data downloaded successfully")
                } else {
                    Log.e("RideshareViewModel", "Failed to download map data")
                    _state.value =
                        _state.value.copy(
                            error = "Failed to download map data. Please try again.",
                        )
                }

                // Update state to show download completed
                _state.value = _state.value.copy(isLoading = false)
            } catch (e: Exception) {
                Log.e("RideshareViewModel", "Error downloading map data", e)
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = "Error: ${e.message ?: "Unknown error"}",
                    )
            }
        }
    }
}
