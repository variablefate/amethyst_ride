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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Manages the initialization of GraphHopper, including downloading and setting up OSM data.
 */
class GraphHopperInitManager(
    private val context: Context,
    private val graphHopperService: GraphHopperService,
) {
    companion object {
        private const val TAG = "GraphHopperInitManager"
        private const val DOWNLOAD_TIMEOUT_SECONDS = 600L // 10 minutes

        // Sample OSM file URLs for different regions
        // In a production app, you'd want to allow users to select their region
        private val OSM_URLS =
            mapOf(
                "san-francisco" to "https://download.geofabrik.de/north-america/us/california/san-francisco-bay.osm.pbf",
                "new-york" to "https://download.geofabrik.de/north-america/us/new-york.osm.pbf",
                "london" to "https://download.geofabrik.de/europe/great-britain/england/greater-london.osm.pbf",
                "berlin" to "https://download.geofabrik.de/europe/germany/berlin.osm.pbf",
                "tokyo" to "https://download.geofabrik.de/asia/japan.osm.pbf",
                "sydney" to "https://download.geofabrik.de/australia-oceania/australia.osm.pbf",
            )
    }

    // States for the initialization process
    enum class InitState {
        NOT_INITIALIZED,
        DOWNLOADING,
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

    // OkHttpClient for downloading OSM files
    private val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    /**
     * Initializes GraphHopper with the OSM data for the specified region.
     * If the data doesn't exist, it will be downloaded first.
     *
     * @param region The region to initialize (e.g., "san-francisco")
     * @return True if initialization was successful, false otherwise
     */
    suspend fun initialize(region: String): Boolean {
        try {
            _statusFlow.value = InitStatus(InitState.NOT_INITIALIZED)

            // Get the OSM file
            val osmFile =
                getOsmFile(region)
                    ?: return false.also {
                        _statusFlow.value =
                            InitStatus(
                                state = InitState.ERROR,
                                errorMessage = "Failed to get OSM file for region: $region",
                            )
                    }

            // Update status to processing
            _statusFlow.value = InitStatus(state = InitState.PROCESSING)

            // Initialize GraphHopper
            val result = graphHopperService.initialize(osmFile)
            _statusFlow.value =
                if (result) {
                    InitStatus(state = InitState.INITIALIZED)
                } else {
                    InitStatus(state = InitState.ERROR, errorMessage = "Failed to initialize GraphHopper")
                }

            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing GraphHopper", e)
            _statusFlow.value = InitStatus(state = InitState.ERROR, errorMessage = e.message)
            return false
        }
    }

    /**
     * Gets the OSM file for the specified region. If the file doesn't exist,
     * it will be downloaded.
     *
     * @param region The region to get the OSM file for (e.g., "san-francisco")
     * @return The OSM file, or null if the file doesn't exist and couldn't be downloaded
     */
    private suspend fun getOsmFile(region: String): File? =
        withContext(Dispatchers.IO) {
            val osmFile = File(context.getExternalFilesDir(null), "$region.osm.pbf")

            // If file already exists, return it
            if (osmFile.exists() && osmFile.length() > 0) {
                return@withContext osmFile
            }

            // Get the download URL for the region
            val url =
                OSM_URLS[region] ?: return@withContext null.also {
                    Log.e(TAG, "No OSM URL defined for region: $region")
                }

            // Download the file
            return@withContext downloadOsmFile(url, osmFile)
        }

    /**
     * Downloads an OSM file from the given URL.
     *
     * @param url The URL to download from
     * @param outputFile The file to save the downloaded data to
     * @return The downloaded file, or null if the download failed
     */
    private suspend fun downloadOsmFile(
        url: String,
        outputFile: File,
    ): File? =
        withContext(Dispatchers.IO) {
            try {
                _statusFlow.value = InitStatus(state = InitState.DOWNLOADING, progress = 0f)

                // Create the request
                val request = Request.Builder().url(url).build()

                // Execute the request
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _statusFlow.value =
                            InitStatus(
                                state = InitState.ERROR,
                                errorMessage = "Download failed with code: ${response.code}",
                            )
                        return@withContext null
                    }

                    // Get the total size
                    val totalSize = response.body?.contentLength() ?: -1L

                    // Create output stream
                    FileOutputStream(outputFile).use { outputStream ->
                        response.body?.byteStream()?.use { inputStream ->
                            // Copy the data with progress updates
                            copyWithProgress(inputStream, outputStream, totalSize)
                        }
                    }

                    return@withContext outputFile
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading OSM file", e)
                _statusFlow.value = InitStatus(state = InitState.ERROR, errorMessage = "Download error: ${e.message}")
                // Delete partial file if download failed
                if (outputFile.exists()) {
                    outputFile.delete()
                }
                return@withContext null
            }
        }

    /**
     * Copies data from input stream to output stream with progress updates.
     *
     * @param input The input stream
     * @param output The output stream
     * @param totalSize The total size of the data (for progress calculation)
     * @throws IOException If an I/O error occurs
     */
    private suspend fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        totalSize: Long,
    ) {
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalBytesRead = 0L
        var lastProgressUpdate = 0f

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead

            // Update progress if totalSize is known
            if (totalSize > 0) {
                val progress = totalBytesRead.toFloat() / totalSize

                // Only update if progress has changed significantly (to avoid too many updates)
                if (progress - lastProgressUpdate > 0.01f) {
                    _statusFlow.value = InitStatus(state = InitState.DOWNLOADING, progress = progress)
                    lastProgressUpdate = progress
                }
            }
        }

        // Final progress update
        if (totalSize > 0) {
            _statusFlow.value = InitStatus(state = InitState.DOWNLOADING, progress = 1f)
        }
    }

    /**
     * Checks if a region's OSM data is available locally.
     *
     * @param region The region to check
     * @return True if the data is available, false otherwise
     */
    fun isRegionDataAvailable(region: String): Boolean {
        val osmFile = File(context.getExternalFilesDir(null), "$region.osm.pbf")
        return osmFile.exists() && osmFile.length() > 0
    }

    /**
     * Gets the current initialization status.
     *
     * @return The current status
     */
    fun getCurrentStatus(): InitStatus = _statusFlow.value
} 
