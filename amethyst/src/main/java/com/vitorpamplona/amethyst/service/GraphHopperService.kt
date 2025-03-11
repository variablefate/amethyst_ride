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
import com.graphhopper.GHRequest
import com.graphhopper.GHResponse
import com.graphhopper.GraphHopper
import com.graphhopper.config.CHProfile
import com.graphhopper.config.Profile
import com.graphhopper.util.Parameters
import com.vitorpamplona.quartz.nip014173Rideshare.DriverAvailabilityEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Service for calculating routes, estimated arrival times, and fare estimates using GraphHopper locally.
 * This implementation uses the GraphHopper library to calculate routes on the device without
 * requiring internet connectivity or API keys.
 */
class GraphHopperService(
    private val context: Context,
) {
    companion object {
        private const val TAG = "GraphHopperService"

        // Directory where GraphHopper will store its data
        private const val GH_DIR = "graphhopper"

        // GraphHopper profiles for different vehicles
        private val CAR_PROFILE = "car"
        private val BIKE_PROFILE = "bike"
        private val FOOT_PROFILE = "foot"
    }

    private var hopper: GraphHopper? = null
    private var isInitialized = false

    /**
     * Initializes the GraphHopper engine with the given OSM file.
     * This method should be called before any routing requests.
     *
     * @param osmFile The OpenStreetMap data file
     * @return True if initialization was successful, false otherwise
     */
    suspend fun initialize(osmFile: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Create GraphHopper instance
                val gh =
                    GraphHopper().apply {
                        // Set the location where GraphHopper will store its data
                        val ghDir = File(context.getExternalFilesDir(null), GH_DIR)
                        ghDir.mkdirs()
                        setGraphHopperLocation(ghDir.absolutePath)

                        // Set the OSM file
                        setOSMFile(osmFile.absolutePath)

                        // Configure routing profiles
                        setProfiles(
                            Profile(CAR_PROFILE).setVehicle(CAR_PROFILE).setTurnCosts(true),
                            Profile(BIKE_PROFILE).setVehicle(BIKE_PROFILE).setTurnCosts(false),
                            Profile(FOOT_PROFILE).setVehicle(FOOT_PROFILE).setTurnCosts(false),
                        )

                        // Enable contraction hierarchies for faster routing
                        setCHProfiles(
                            CHProfile(CAR_PROFILE),
                            CHProfile(BIKE_PROFILE),
                            CHProfile(FOOT_PROFILE),
                        )
                    }

                // Import and process the data
                gh.importOrLoad()

                hopper = gh
                isInitialized = true
                Log.i(TAG, "GraphHopper initialized successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing GraphHopper", e)
                isInitialized = false
                false
            }
        }

    /**
     * Downloads an OSM file for the given area if it doesn't already exist.
     * This is a simplified version that assumes the file is provided by the application
     * or downloaded separately.
     *
     * @param areaName The name of the area (used for the filename)
     * @return The OSM file, or null if the file doesn't exist
     */
    fun getOsmFile(areaName: String): File? {
        val osmFile = File(context.getExternalFilesDir(null), "$areaName.osm.pbf")
        return if (osmFile.exists()) osmFile else null
    }

    /**
     * Calculates the route information between two points.
     *
     * @param origin The starting location (lat/lon)
     * @param destination The ending location (lat/lon)
     * @return RouteInfo object containing distance, duration and points of the route
     * @throws IllegalStateException if GraphHopper has not been initialized
     * @throws IOException if routing fails
     */
    suspend fun calculateRoute(
        origin: DriverAvailabilityEvent.Location,
        destination: DriverAvailabilityEvent.Location,
        profile: String = CAR_PROFILE,
    ): RouteInfo =
        withContext(Dispatchers.IO) {
            if (!isInitialized || hopper == null) {
                throw IllegalStateException("GraphHopper is not initialized. Call initialize() first.")
            }

            try {
                // Create a routing request
                val request =
                    GHRequest(
                        origin.lat,
                        origin.lon,
                        destination.lat,
                        destination.lon,
                    ).apply {
                        // Set the vehicle profile
                        setProfile(profile)
                        // Enable path details like road type, surface, etc.
                        setPathDetails(
                            listOf(
                                Parameters.Details.AVERAGE_SPEED,
                                Parameters.Details.STREET_NAME,
                                Parameters.Details.ROAD_CLASS,
                                Parameters.Details.ROAD_ENVIRONMENT,
                            ),
                        )
                        // Request distances in meters and time in seconds
                        setLocale("en")
                    }

                // Execute routing request
                val response: GHResponse = hopper!!.route(request)

                // Check for errors
                if (response.hasErrors()) {
                    val errorMessage = response.errors.joinToString("\n") { it.message ?: "Unknown error" }
                    throw IOException("Routing error: $errorMessage")
                }

                // Get the best path
                val path = response.best

                // Extract points from the path
                val points = mutableListOf<DriverAvailabilityEvent.Location>()
                for (i in 0 until path.points.size()) {
                    val point = path.points.get(i)
                    points.add(DriverAvailabilityEvent.Location(point.lat, point.lon))
                }

                RouteInfo(
                    distanceMeters = path.distance,
                    durationSeconds = path.time / 1000.0, // Convert from milliseconds to seconds
                    points = points,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating route", e)
                throw IOException("Failed to calculate route: ${e.message}", e)
            }
        }

    /**
     * Calculates a fare estimate based on distance and duration.
     * This is a simple implementation - a real app would have more sophisticated pricing.
     *
     * @param distanceMeters Distance in meters
     * @param durationSeconds Duration in seconds
     * @return String representation of the fare in satoshis
     */
    fun calculateFareEstimate(
        distanceMeters: Double,
        durationSeconds: Double,
    ): String {
        // Convert to kilometers and minutes for calculation
        val distanceKm = distanceMeters / 1000.0
        val durationMinutes = durationSeconds / 60.0

        // Simple fare formula in USD:
        // Base fare: $5.00
        // Per km: $1.00
        // Per minute: $0.50
        val baseFareUsd = 5.00
        val distanceFareUsd = distanceKm * 1.00
        val timeFareUsd = durationMinutes * 0.50

        val totalFareUsd = baseFareUsd + distanceFareUsd + timeFareUsd

        // Convert to satoshis using the SatoshiFormatter utility
        val totalFareSats = SatoshiFormatter.usdToSatoshis(totalFareUsd)

        // Return as string
        return totalFareSats.toString()
    }

    /**
     * Cleans up resources used by GraphHopper.
     * This should be called when the service is no longer needed.
     */
    fun close() {
        hopper?.close()
        hopper = null
        isInitialized = false
    }

    /**
     * Data class representing route information.
     */
    data class RouteInfo(
        val distanceMeters: Double,
        val durationSeconds: Double,
        val points: List<DriverAvailabilityEvent.Location>,
    )
} 
