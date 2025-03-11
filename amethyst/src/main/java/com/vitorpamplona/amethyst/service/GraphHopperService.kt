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
import com.vitorpamplona.quartz.nip014173Rideshare.DriverAvailabilityEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Service for calculating routes, estimated arrival times, and fare estimates.
 * This implementation uses a simple straight-line distance calculation with
 * estimated driving speeds, no map data required.
 */
class GraphHopperService(
    private val context: Context,
) {
    companion object {
        private const val TAG = "GraphHopperService"

        // Average driving speeds in different environments (in km/h)
        private const val URBAN_SPEED_KMH = 30.0 // 30 km/h in urban areas
        private const val HIGHWAY_SPEED_KMH = 90.0 // 90 km/h on highways

        // Earth's radius in kilometers
        private const val EARTH_RADIUS_KM = 6371.0
    }

    private var isInitialized = true // Always initialized

    /**
     * Initialize the routing service.
     * This simplified implementation doesn't require real initialization.
     *
     * @param osmFile Ignored in this implementation
     * @return Always returns true
     */
    suspend fun initialize(osmFile: File): Boolean = true

    /**
     * Gets a dummy OSM file, just to maintain API compatibility.
     *
     * @param areaName The name of the area (ignored)
     * @return A dummy file
     */
    fun getOsmFile(areaName: String): File {
        val dummyFile = File(context.getExternalFilesDir(null), "dummy.osm")
        if (!dummyFile.exists()) {
            dummyFile.createNewFile()
        }
        return dummyFile
    }

    /**
     * Calculates a route between two points using straight-line distance.
     *
     * @param origin The starting location (lat/lon)
     * @param destination The ending location (lat/lon)
     * @return RouteInfo object containing distance, duration and points of the route
     */
    suspend fun calculateRoute(
        origin: DriverAvailabilityEvent.Location,
        destination: DriverAvailabilityEvent.Location,
        profile: String = "car", // Ignored in this implementation
    ): RouteInfo =
        withContext(Dispatchers.IO) {
            try {
                // Calculate the straight-line distance in meters
                val distanceKm =
                    calculateHaversineDistance(
                        origin.lat,
                        origin.lon,
                        destination.lat,
                        destination.lon,
                    )
                val distanceMeters = distanceKm * 1000

                // Estimate travel time based on distance and estimated speed
                // Use urban speed for shorter distances, highway speed for longer distances
                val speedKmh = if (distanceKm < 10) URBAN_SPEED_KMH else HIGHWAY_SPEED_KMH
                val durationHours = distanceKm / speedKmh
                val durationSeconds = durationHours * 3600

                // Generate a simple route with just start and end points
                val points = mutableListOf(origin, destination)

                // If the distance is over 5km, add a midpoint
                if (distanceKm > 5) {
                    val midLat = (origin.lat + destination.lat) / 2
                    val midLon = (origin.lon + destination.lon) / 2
                    points.add(1, DriverAvailabilityEvent.Location(midLat, midLon))
                }

                RouteInfo(
                    distanceMeters = distanceMeters,
                    durationSeconds = durationSeconds,
                    points = points,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating route", e)
                throw IOException("Failed to calculate route: ${e.message}", e)
            }
        }

    /**
     * Calculates the great-circle distance between two coordinates using the Haversine formula.
     *
     * @param lat1 Latitude of the first point
     * @param lon1 Longitude of the first point
     * @param lat2 Latitude of the second point
     * @param lon2 Longitude of the second point
     * @return Distance in kilometers
     */
    private fun calculateHaversineDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        // Convert latitude and longitude from degrees to radians
        val lat1Rad = Math.toRadians(lat1)
        val lon1Rad = Math.toRadians(lon1)
        val lat2Rad = Math.toRadians(lat2)
        val lon2Rad = Math.toRadians(lon2)

        // Calculate differences
        val dLat = lat2Rad - lat1Rad
        val dLon = lon2Rad - lon1Rad

        // Haversine formula
        val a = sin(dLat / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        // Distance in kilometers
        return EARTH_RADIUS_KM * c
    }

    /**
     * Calculates a fare estimate based on distance and duration.
     * This is a simple implementation with a base fare plus per-km and per-minute rates.
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
     * Cleans up resources.
     * This simplified implementation doesn't need cleanup.
     */
    fun close() {
        // No resources to clean up
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
