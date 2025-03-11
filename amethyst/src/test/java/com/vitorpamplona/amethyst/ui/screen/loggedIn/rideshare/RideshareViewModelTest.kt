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

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.nip014173Rideshare.DriverAvailabilityEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class RideshareViewModelTest {
    private lateinit var account: Account
    private lateinit var viewModel: RideshareViewModel

    @Before
    fun setup() {
        account = mock(Account::class.java)
        viewModel = RideshareViewModel(account)
    }

    @Test
    fun `initial state has driver mode off and no current ride`() {
        val state = viewModel.state.value

        assertFalse(state.isDriverMode)
        assertNull(state.currentRide)
        assertTrue(state.availableDrivers.isEmpty())
        assertFalse(state.isSearching)
    }

    @Test
    fun `setDriverMode updates driver mode state`() {
        viewModel.setDriverMode(true)

        assertTrue(viewModel.state.value.isDriverMode)

        viewModel.setDriverMode(false)

        assertFalse(viewModel.state.value.isDriverMode)
    }

    @Test
    fun `broadcastDriverAvailability creates a ride state with DRIVER_AVAILABLE stage`() {
        val location = DriverAvailabilityEvent.Location(lat = 37.7749, lon = -122.4194)

        viewModel.broadcastDriverAvailability(location)

        val currentRide = viewModel.state.value.currentRide

        assertTrue(currentRide != null)
        assertEquals(RideStage.DRIVER_AVAILABLE, currentRide?.rideStage)
        assertEquals(location, currentRide?.driverLocation)
    }

    @Test
    fun `resetRideState clears the current ride`() {
        // First set up a ride
        val location = DriverAvailabilityEvent.Location(lat = 37.7749, lon = -122.4194)
        viewModel.broadcastDriverAvailability(location)

        // Verify ride exists
        assertTrue(viewModel.state.value.currentRide != null)

        // Reset the ride state
        viewModel.resetRideState()

        // Verify ride is cleared
        assertNull(viewModel.state.value.currentRide)
    }
} 
