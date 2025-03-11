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
import com.vitorpamplona.quartz.nip01Core.core.mapValues
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonHelper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Kind 3001: Ride Offer Event
 * Description: Rider sends a ride offer to a specific driver.
 */
@Immutable
class RideOfferEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    RideshareEventInterface {
    data class Content(
        val fare_estimate: String,
        val destination: DriverAvailabilityEvent.Location,
        val approx_pickup: DriverAvailabilityEvent.Location,
    )

    fun getContent(): Content? =
        try {
            JacksonHelper.mapper.readValue(content, Content::class.java)
        } catch (e: Exception) {
            null
        }

    fun getFareEstimate(): String? = getContent()?.fare_estimate

    fun getDestination(): DriverAvailabilityEvent.Location? = getContent()?.destination

    fun getApproxPickup(): DriverAvailabilityEvent.Location? = getContent()?.approx_pickup

    fun getDriverAvailabilityId(): String? = tags.mapValues("e").firstOrNull()

    fun getDriverPubKey(): String? = tags.mapValues("p").firstOrNull()

    companion object {
        const val KIND = RideshareEventInterface.RIDE_OFFER
        const val ALT = "Ride offer"

        /**
         * Creates a new ride offer event.
         *
         * @param driverAvailabilityEvent The driver availability event this offer responds to
         * @param fareEstimate Estimated fare for the ride
         * @param destination Destination coordinates
         * @param approxPickup Approximate pickup location
         * @param signer The signer to use for signing the event
         * @param createdAt The time the event was created
         * @param onReady Callback for when the event is ready
         */
        fun create(
            driverAvailabilityEvent: DriverAvailabilityEvent,
            fareEstimate: String,
            destination: DriverAvailabilityEvent.Location,
            approxPickup: DriverAvailabilityEvent.Location,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (RideOfferEvent) -> Unit,
        ) {
            val tags =
                arrayOf(
                    arrayOf("e", driverAvailabilityEvent.id),
                    arrayOf("p", driverAvailabilityEvent.pubKey),
                    AltTag.assemble(ALT),
                )

            val content: String =
                JacksonHelper.mapper.writeValueAsString(
                    Content(
                        fare_estimate = fareEstimate,
                        destination = destination,
                        approx_pickup = approxPickup,
                    ),
                )

            signer.sign<RideOfferEvent>(createdAt, KIND, tags, content) { signedEvent ->
                onReady(signedEvent)
            }
        }
    }
} 
