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

import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonHelper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Kind 3000: Driver Availability Event
 * Description: Driver broadcasts availability to potential riders.
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
    )

    data class Location(
        val lat: Double,
        val lon: Double,
    )

    fun getContent(): Content? =
        try {
            JacksonHelper.mapper.readValue(content, Content::class.java)
        } catch (e: Exception) {
            null
        }

    fun getApproxLocation(): Location? = getContent()?.approx_location

    companion object {
        const val KIND = RideshareEventInterface.DRIVER_AVAILABILITY
        const val ALT = "Driver availability"

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
            val tags =
                arrayOf(
                    AltTag.assemble(ALT),
                    // P tag with driver's pubkey will be added when the event is signed
                )

            val content: String = JacksonHelper.mapper.writeValueAsString(Content(approxLocation))

            signer.sign<DriverAvailabilityEvent>(createdAt, KIND, tags, content) { signedEvent ->
                onReady(signedEvent)
            }
        }
    }
} 
