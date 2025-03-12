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
package com.vitorpamplona.quartz.nip014173Rideshare

import android.util.Log
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonHelper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Kind 3000: Driver Availability Event
 * Description: Driver broadcasts availability to potential riders.
 *
 * Standard Nostr event with kind = 3000
 * Content: JSON object with approx_location (latitude, longitude)
 * Tags:
 * - ["t", "rideshare"] for topic/hashtag
 * - ["p", "<driver-pubkey>"] automatically added by signing process
 */
@Immutable
class DriverAvailabilityEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    RideshareEventInterface {
    data class Content(
        val approx_location: Location,
        // Added for debugging purposes
        val app: String = "amethyst",
        val version: String = "1.0.0",
    )

    fun getContent(): Content? =
        try {
            JacksonHelper.mapper.readValue(content, Content::class.java)
        } catch (e: Exception) {
            Log.e("DriverAvailabilityEvent", "Failed to parse content: $content", e)
            null
        }

    fun getApproxLocation(): Location? = getContent()?.approx_location

    companion object {
        const val KIND = RideshareEventInterface.DRIVER_AVAILABILITY
        const val ALT = "Driver availability"
        private const val RIDESHARE_HASHTAG = "rideshare"

        /**
         * Creates a new driver availability event.
         *
         * @param approxLocation The approximate location of the driver (city-level for privacy)
         * @param signer The signer to use for signing the event
         * @param createdAt The time the event was created
         * @param onReady Callback for when the event is ready
         */
        fun create(
            approxLocation: Location,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (DriverAvailabilityEvent) -> Unit,
        ) {
            // Create standard Nostr tags following NIP-01 format
            val tags =
                arrayOf(
                    // Add a standard hashtag tag
                    arrayOf("t", RIDESHARE_HASHTAG),
                    // P tag with driver's pubkey will be added automatically when the event is signed
                )

            // Create the content following standard format
            val content =
                try {
                    JacksonHelper.mapper.writeValueAsString(
                        Content(
                            approx_location = approxLocation,
                        ),
                    )
                } catch (e: Exception) {
                    Log.e("DriverAvailabilityEvent", "Error serializing content", e)
                    // Provide a simpler fallback format if serialization fails
                    val fallbackContent = """{"approx_location":{"lat":${approxLocation.latitude},"lon":${approxLocation.longitude}}}"""
                    fallbackContent
                }

            // Log the event we're about to create for debugging
            Log.d("DriverAvailabilityEvent", "Creating event with content: $content")
            Log.d("DriverAvailabilityEvent", "Tags: ${tags.map { it.joinToString() }}")

            // Sign and create the event
            signer.sign<DriverAvailabilityEvent>(createdAt, KIND, tags, content) { signedEvent ->
                // Log the created event for debugging
                Log.d("DriverAvailabilityEvent", "Created event with ID: ${signedEvent.id}")
                Log.d("DriverAvailabilityEvent", "Final content: ${signedEvent.content}")
                Log.d("DriverAvailabilityEvent", "Final tags: ${signedEvent.tags.map { it.joinToString() }}")

                onReady(signedEvent)
            }
        }
    }
} 
