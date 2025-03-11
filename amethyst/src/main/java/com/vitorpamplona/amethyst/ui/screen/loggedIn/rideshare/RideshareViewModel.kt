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
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip014173Rideshare.DriverAvailabilityEvent
import com.vitorpamplona.quartz.nip014173Rideshare.DriverStatusEvent
import com.vitorpamplona.quartz.nip014173Rideshare.RideAcceptanceEvent
import com.vitorpamplona.quartz.nip014173Rideshare.RideConfirmationEvent
import com.vitorpamplona.quartz.nip014173Rideshare.RideOfferEvent
import com.vitorpamplona.quartz.nip014173Rideshare.RideshareEventInterface
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
class RideshareState(
    val availableDrivers: List<User> = emptyList(),
    val currentRide: RideState? = null,
    val isDriverMode: Boolean = false,
    val isSearching: Boolean = false,
    val routingEngineStatus: GraphHopperInitManager.InitStatus = GraphHopperInitManager.InitStatus(),
    val needsStoragePermission: Boolean = false,
)

@Immutable
class RideState(
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
) : ViewModel() {
    private val rideshareRelays =
        setOf(
            "wss://relay.damus.io/",
            "wss://relay.nostr.band/",
            "wss://nostr.wine/",
        )

    // GraphHopper services
    private val graphHopperService = GraphHopperService(account.context)
    private val graphHopperInitManager = GraphHopperInitManager(account.context, graphHopperService)

    private val _state = MutableStateFlow(RideshareState())
    val state: StateFlow<RideshareState> = _state.asStateFlow()

    private val searchEventOrUserDataSource = NostrSearchEventOrUserDataSource()
    private val baseEventDataSource = account.localRelayPool.baseDataSource

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

            // Subscribe to driver availability events (kind 3000)
            listenForDriverAvailability()
        }
    }

    private fun hasStoragePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10+ we don't need explicit storage permission for app-specific directories
            true
        } else {
            // For older Android versions, check WRITE_EXTERNAL_STORAGE
            ContextCompat.checkSelfPermission(
                account.context,
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

    fun refreshDriverList() {
        viewModelScope.launch(Dispatchers.IO) {
            checkNotInMainThread()
            _state.update { it.copy(isSearching = true) }

            try {
                // Subscribe to Kind 3000 events (Driver Availability)
                // This creates a filter for the last 24 hours
                val oneHourAgo = System.currentTimeMillis() / 1000 - 3600
                baseEventDataSource.requestNewChannel().apply {
                    typedFilters =
                        listOf(
                            TypedFilter(
                                types = setOf(FeedType.FOLLOWS),
                                filter =
                                    SincePerRelayFilter(
                                        kinds = listOf(RideshareEventInterface.DRIVER_AVAILABILITY),
                                        since = oneHourAgo,
                                    ),
                            ),
                        )
                }

                // Allow some time for events to be received
                delay(2000)

                // Get events from local repository
                val driverEvents =
                    account.localRelayPool.repository
                        .getEvents()
                        .filter { it.value.kind() == RideshareEventInterface.DRIVER_AVAILABILITY }
                        .filter { (System.currentTimeMillis() / 1000 - it.value.createdAt()) < 3600 } // Only events from last hour
                        .map { it.value }

                // Convert events to users
                val drivers =
                    driverEvents
                        .mapNotNull { event ->
                            account.localRelayPool.repository.getUserByPubkeyHex(event.pubKey())
                        }.distinct()

                _state.update { it.copy(availableDrivers = drivers, isSearching = false) }
            } catch (e: Exception) {
                Log.e("RideshareViewModel", "Error refreshing driver list", e)
                _state.update { it.copy(isSearching = false) }
            }
        }
    }

    private fun listenForDriverAvailability() {
        viewModelScope.launch(Dispatchers.IO) {
            checkNotInMainThread()

            // Subscribe to relevant event kinds for the rideshare protocol
            val eventKinds =
                listOf(
                    RideshareEventInterface.DRIVER_AVAILABILITY,
                    RideshareEventInterface.RIDE_OFFER,
                    RideshareEventInterface.RIDE_ACCEPTANCE,
                    RideshareEventInterface.RIDE_CONFIRMATION,
                    RideshareEventInterface.DRIVER_STATUS,
                )

            // Create a filter for these event kinds
            val filter =
                TypedFilter(
                    types = setOf(FeedType.GLOBAL),
                    filter =
                        SincePerRelayFilter(
                            kinds = eventKinds,
                            since = System.currentTimeMillis() / 1000 - 86400, // Last 24 hours
                        ),
                )

            // Subscribe to the filter
            val channel = account.localRelayPool.baseDataSource.requestNewChannel()
            channel.typedFilters = listOf(filter)

            // Process incoming events
            baseEventDataSource.live.observeForever { newEvents ->
                processNewEvents(newEvents)
            }
        }
    }

    private fun processNewEvents(newEvents: Set<HexKey>) {
        // Process each new event and update state accordingly
        val repository = account.localRelayPool.repository

        for (eventId in newEvents) {
            val event = repository.get(eventId) ?: continue

            when (event.kind()) {
                RideshareEventInterface.DRIVER_AVAILABILITY -> {
                    // Update available drivers if we're in rider mode
                    if (!state.value.isDriverMode) {
                        refreshDriverList()
                    }
                }

                RideshareEventInterface.RIDE_OFFER -> {
                    // If we're a driver, check if this offer is for us
                    if (state.value.isDriverMode) {
                        val rideOfferEvent = event as? RideOfferEvent ?: continue
                        if (rideOfferEvent.getDriverPubKey() == account.userProfile().pubkeyHex) {
                            // Update state with the new ride offer
                            val rider = repository.getUserByPubkeyHex(event.pubKey())
                            _state.update { currentState ->
                                val rideState =
                                    RideState(
                                        rideStage = RideStage.RIDER_SENT_OFFER,
                                        rider = rider,
                                        driver = account.userProfile(),
                                        fareEstimate = rideOfferEvent.getFareEstimate(),
                                        destinationLocation = rideOfferEvent.getDestination(),
                                        pickupLocation = rideOfferEvent.getApproxPickup(),
                                    )
                                currentState.copy(currentRide = rideState)
                            }
                        }
                    }
                }

                RideshareEventInterface.RIDE_ACCEPTANCE -> {
                    // If we're a rider, check if this acceptance is for our offer
                    if (!state.value.isDriverMode) {
                        val rideAcceptanceEvent = event as? RideAcceptanceEvent ?: continue
                        // Check if we're the rider
                        if (rideAcceptanceEvent.getRiderPubKey() == account.userProfile().pubkeyHex) {
                            // Update state with the accepted ride
                            val driver = repository.getUserByPubkeyHex(event.pubKey())
                            _state.update { currentState ->
                                val currentRide = currentState.currentRide
                                if (currentRide != null && currentRide.rideStage == RideStage.RIDER_SENT_OFFER) {
                                    currentState.copy(
                                        currentRide =
                                            currentRide.copy(
                                                rideStage = RideStage.DRIVER_ACCEPTED_OFFER,
                                                driver = driver,
                                            ),
                                    )
                                } else {
                                    currentState
                                }
                            }
                        }
                    }
                }

                RideshareEventInterface.RIDE_CONFIRMATION -> {
                    // If we're a driver, check if this confirmation is for our acceptance
                    if (state.value.isDriverMode) {
                        val rideConfirmationEvent = event as? RideConfirmationEvent ?: continue
                        // Check if we're the driver
                        if (rideConfirmationEvent.getDriverPubKey() == account.userProfile().pubkeyHex) {
                            // Update state with the confirmed ride
                            _state.update { currentState ->
                                val currentRide = currentState.currentRide
                                if (currentRide != null && currentRide.rideStage == RideStage.DRIVER_ACCEPTED_OFFER) {
                                    currentState.copy(
                                        currentRide =
                                            currentRide.copy(
                                                rideStage = RideStage.RIDER_CONFIRMED,
                                                precisePickupLocation = rideConfirmationEvent.getPrecisePickup(),
                                            ),
                                    )
                                } else {
                                    currentState
                                }
                            }
                        }
                    }
                }

                RideshareEventInterface.DRIVER_STATUS -> {
                    // If we're a rider, check if this status is for our ride
                    if (!state.value.isDriverMode) {
                        val driverStatusEvent = event as? DriverStatusEvent ?: continue
                        val currentRide = state.value.currentRide ?: continue

                        // Check if we're the rider
                        if (driverStatusEvent.getRiderPubKey() == account.userProfile().pubkeyHex) {
                            // Update state based on driver status
                            when (driverStatusEvent.getStatus()) {
                                DriverStatusEvent.STATUS_ON_THE_WAY -> {
                                    _state.update { currentState ->
                                        currentState.copy(
                                            currentRide =
                                                currentRide.copy(
                                                    rideStage = RideStage.DRIVER_ON_THE_WAY,
                                                    driverLocation = driverStatusEvent.getApproxLocation(),
                                                ),
                                        )
                                    }
                                }
                                DriverStatusEvent.STATUS_GETTING_CLOSE -> {
                                    _state.update { currentState ->
                                        currentState.copy(
                                            currentRide =
                                                currentRide.copy(
                                                    rideStage = RideStage.DRIVER_GETTING_CLOSE,
                                                    driverLocation = driverStatusEvent.getApproxLocation(),
                                                ),
                                        )
                                    }
                                }
                                DriverStatusEvent.STATUS_COMPLETED -> {
                                    _state.update { currentState ->
                                        currentState.copy(
                                            currentRide =
                                                currentRide.copy(
                                                    rideStage = RideStage.RIDE_COMPLETED,
                                                    driverLocation = driverStatusEvent.getApproxLocation(),
                                                    finalFare = driverStatusEvent.getFinalFare(),
                                                    lightningInvoice = driverStatusEvent.getLightningInvoice(),
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun setDriverMode(isDriverMode: Boolean) {
        _state.update { it.copy(isDriverMode = isDriverMode) }
    }

    fun broadcastDriverAvailability(location: DriverAvailabilityEvent.Location) {
        viewModelScope.launch(Dispatchers.IO) {
            checkNotInMainThread()

            account.signer?.let { signer ->
                DriverAvailabilityEvent.create(
                    approxLocation = location,
                    signer = signer,
                ) { event ->
                    account.sendNewEvent(event, rideshareRelays)

                    // Update local state to reflect that we're available as a driver
                    _state.update {
                        it.copy(
                            currentRide =
                                RideState(
                                    rideStage = RideStage.DRIVER_AVAILABLE,
                                    driver = account.userProfile(),
                                    driverLocation = location,
                                ),
                        )
                    }
                }
            }
        }
    }

    private suspend fun calculateFare(
        origin: DriverAvailabilityEvent.Location,
        destination: DriverAvailabilityEvent.Location,
    ): String {
        try {
            // Check if GraphHopper is initialized
            if (state.value.routingEngineStatus.state != GraphHopperInitManager.InitState.INITIALIZED) {
                return "10000" // Default fare if routing engine is not available (10,000 sats)
            }

            // Calculate route
            val routeInfo = graphHopperService.calculateRoute(origin, destination)

            // Calculate fare based on route - now returns satoshis directly
            return graphHopperService.calculateFareEstimate(
                routeInfo.distanceMeters,
                routeInfo.durationSeconds,
            )
        } catch (e: Exception) {
            Log.e("RideshareViewModel", "Error calculating fare", e)
            return "10000" // Default fare on error (10,000 sats)
        }
    }

    fun sendRideOffer(
        driverAvailabilityEvent: DriverAvailabilityEvent,
        destination: DriverAvailabilityEvent.Location,
        approxPickup: DriverAvailabilityEvent.Location,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            checkNotInMainThread()

            account.signer?.let { signer ->
                // Calculate fare estimate using GraphHopper
                val fareEstimate = calculateFare(approxPickup, destination)

                RideOfferEvent.create(
                    driverAvailabilityEvent = driverAvailabilityEvent,
                    fareEstimate = fareEstimate,
                    destination = destination,
                    approxPickup = approxPickup,
                    signer = signer,
                ) { event ->
                    account.sendNewEvent(event, rideshareRelays)

                    // Update local state to reflect that we've sent an offer
                    _state.update { currentState ->
                        currentState.copy(
                            currentRide =
                                RideState(
                                    rideStage = RideStage.RIDER_SENT_OFFER,
                                    rider = account.userProfile(),
                                    destinationLocation = destination,
                                    pickupLocation = approxPickup,
                                    fareEstimate = fareEstimate,
                                ),
                        )
                    }
                }
            }
        }
    }

    fun acceptRideOffer(rideOfferEvent: RideOfferEvent) {
        viewModelScope.launch(Dispatchers.IO) {
            checkNotInMainThread()

            account.signer?.let { signer ->
                RideAcceptanceEvent.create(
                    rideOfferEvent = rideOfferEvent,
                    signer = signer,
                ) { event ->
                    account.sendNewEvent(event, rideshareRelays)

                    // Update local state to reflect that we've accepted the offer
                    _state.update { currentState ->
                        val rideState =
                            currentState.currentRide?.copy(
                                rideStage = RideStage.DRIVER_ACCEPTED_OFFER,
                                destinationLocation = rideOfferEvent.getDestination(),
                                pickupLocation = rideOfferEvent.getApproxPickup(),
                                fareEstimate = rideOfferEvent.getFareEstimate(),
                            ) ?: RideState(
                                rideStage = RideStage.DRIVER_ACCEPTED_OFFER,
                                driver = account.userProfile(),
                                destinationLocation = rideOfferEvent.getDestination(),
                                pickupLocation = rideOfferEvent.getApproxPickup(),
                                fareEstimate = rideOfferEvent.getFareEstimate(),
                            )

                        currentState.copy(currentRide = rideState)
                    }
                }
            }
        }
    }

    fun confirmRide(
        rideAcceptanceEvent: RideAcceptanceEvent,
        precisePickup: RideConfirmationEvent.PreciseLocation,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            checkNotInMainThread()

            account.signer?.let { signer ->
                RideConfirmationEvent.create(
                    rideAcceptanceEvent = rideAcceptanceEvent,
                    precisePickup = precisePickup,
                    signer = signer,
                ) { event ->
                    account.sendNewEvent(event, rideshareRelays)

                    // Update local state to reflect that we've confirmed the ride
                    _state.update { currentState ->
                        val rideState =
                            currentState.currentRide?.copy(
                                rideStage = RideStage.RIDER_CONFIRMED,
                                precisePickupLocation = precisePickup,
                            ) ?: RideState(
                                rideStage = RideStage.RIDER_CONFIRMED,
                                rider = account.userProfile(),
                                precisePickupLocation = precisePickup,
                            )

                        currentState.copy(currentRide = rideState)
                    }
                }
            }
        }
    }

    fun updateDriverStatus(
        rideConfirmationEvent: RideConfirmationEvent,
        status: String,
        currentLocation: DriverAvailabilityEvent.Location,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            checkNotInMainThread()

            account.signer?.let { signer ->
                DriverStatusEvent.createStatusUpdate(
                    rideConfirmationEvent = rideConfirmationEvent,
                    status = status,
                    approxLocation = currentLocation,
                    signer = signer,
                ) { event ->
                    account.sendNewEvent(event, rideshareRelays)

                    // Update local state to reflect the driver's status
                    val rideStage =
                        when (status) {
                            DriverStatusEvent.STATUS_ON_THE_WAY -> RideStage.DRIVER_ON_THE_WAY
                            DriverStatusEvent.STATUS_GETTING_CLOSE -> RideStage.DRIVER_GETTING_CLOSE
                            else -> currentState.currentRide?.rideStage ?: RideStage.DRIVER_ON_THE_WAY
                        }

                    val currentState = _state.value
                    _state.update {
                        val rideState =
                            currentState.currentRide?.copy(
                                rideStage = rideStage,
                                driverLocation = currentLocation,
                            ) ?: RideState(
                                rideStage = rideStage,
                                driver = account.userProfile(),
                                driverLocation = currentLocation,
                            )

                        currentState.copy(currentRide = rideState)
                    }
                }
            }
        }
    }

    fun completeRide(
        rideConfirmationEvent: RideConfirmationEvent,
        finalLocation: DriverAvailabilityEvent.Location,
        finalFare: String,
        lightningInvoice: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            checkNotInMainThread()

            account.signer?.let { signer ->
                DriverStatusEvent.createRideCompletion(
                    rideConfirmationEvent = rideConfirmationEvent,
                    approxLocation = finalLocation,
                    finalFare = finalFare,
                    lightningInvoice = lightningInvoice,
                    signer = signer,
                ) { event ->
                    account.sendNewEvent(event, rideshareRelays)

                    // Update local state to reflect ride completion
                    val currentState = _state.value
                    _state.update {
                        val rideState =
                            currentState.currentRide?.copy(
                                rideStage = RideStage.RIDE_COMPLETED,
                                driverLocation = finalLocation,
                                finalFare = finalFare,
                                lightningInvoice = lightningInvoice,
                            ) ?: RideState(
                                rideStage = RideStage.RIDE_COMPLETED,
                                driver = account.userProfile(),
                                driverLocation = finalLocation,
                                finalFare = finalFare,
                                lightningInvoice = lightningInvoice,
                            )

                        currentState.copy(currentRide = rideState)
                    }
                }
            }
        }
    }

    fun resetRideState() {
        _state.update { it.copy(currentRide = null) }
    }

    override fun onCleared() {
        super.onCleared()
        graphHopperService.close() // Cleanup resources
    }

    // Factory for creating the ViewModel with dependencies
    class Factory(
        private val account: Account,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RideshareViewModel::class.java)) {
                return RideshareViewModel(account) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    fun sendPayment(invoice: String) {
        // In a real app, this would initiate a lightning payment via Nostr Wallet Connect
        // For this implementation, we'll just simulate a successful payment
        viewModelScope.launch(Dispatchers.IO) {
            checkNotInMainThread()

            // Update local state to reflect payment completion
            _state.update { currentState ->
                val currentRide = currentState.currentRide ?: return@update currentState
                currentState.copy(
                    currentRide =
                        currentRide.copy(
                            rideStage = RideStage.PAYMENT_COMPLETE,
                        ),
                )
            }

            // In a real implementation, you would:
            // 1. Connect to the user's Nostr wallet
            // 2. Submit the lightning invoice for payment
            // 3. Wait for confirmation
            // 4. Send a DM to the driver confirming payment was made
        }
    }
} 
