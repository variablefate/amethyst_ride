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
package com.vitorpamplona.amethyst.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Simplified manager for the routing service.
 * This class provides initialization status for the routing service,
 * but doesn't actually need to download or process any data.
 */
class GraphHopperInitManager(
    private val context: Context,
    private val graphHopperService: GraphHopperService,
) {
    companion object {
        private const val TAG = "GraphHopperInitManager"
    }

    // States for the initialization process
    enum class InitState {
        NOT_INITIALIZED,
        PROCESSING,
        INITIALIZED,
        ERROR,
    }

    // Data class to represent the current status
    data class InitStatus(
        val state: InitState = InitState.NOT_INITIALIZED,
        val progress: Float = 0f,
        val errorMessage: String? = null,
    )

    // StateFlow to observe the initialization status
    private val _statusFlow = MutableStateFlow(InitStatus())
    val statusFlow: StateFlow<InitStatus> = _statusFlow.asStateFlow()

    /**
     * Initializes the routing service.
     * This simplified version doesn't actually need to download or process any map data.
     *
     * @param region The region name (ignored in this implementation)
     * @return True if initialization was successful
     */
    suspend fun initialize(region: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Update status to processing
                _statusFlow.value = InitStatus(state = InitState.PROCESSING)

                // Small delay to simulate processing
                kotlinx.coroutines.delay(500)

                // Get dummy file
                val dummyFile = graphHopperService.getOsmFile(region)

                // Initialize the service
                val result = graphHopperService.initialize(dummyFile)

                // Update status
                _statusFlow.value =
                    if (result) {
                        InitStatus(state = InitState.INITIALIZED)
                    } else {
                        InitStatus(state = InitState.ERROR, errorMessage = "Failed to initialize routing service")
                    }

                result
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing routing service", e)
                _statusFlow.value = InitStatus(state = InitState.ERROR, errorMessage = e.message)
                false
            }
        }

    /**
     * Checks if a region's data is available.
     * This simplified version always returns true.
     *
     * @param region The region to check (ignored)
     * @return Always returns true
     */
    fun isRegionDataAvailable(region: String): Boolean = true

    /**
     * Gets the current initialization status.
     *
     * @return The current status
     */
    fun getCurrentStatus(): InitStatus = _statusFlow.value
} 
