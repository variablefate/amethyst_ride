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
 * Kind 3004: Driver Status Event
 * Description: Driver sends periodic updates during the ride, including status and approximate location.
 * Upon completion, it includes payment details.
 */
@Immutable
class DriverStatusEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    RideshareEventInterface {
    data class Content(
        val status: String,
        val approx_location: DriverAvailabilityEvent.Location,
        val final_fare: String? = null,
        val invoice: String? = null,
    )

    fun getContent(): Content? =
        try {
            JacksonHelper.mapper.readValue(content, Content::class.java)
        } catch (e: Exception) {
            null
        }

    fun getStatus(): String? = getContent()?.status

    fun getApproxLocation(): DriverAvailabilityEvent.Location? = getContent()?.approx_location

    fun getFinalFare(): String? = getContent()?.final_fare

    fun getInvoice(): String? = getContent()?.invoice

    fun isRideCompleted(): Boolean = getStatus() == STATUS_COMPLETED

    fun getRideConfirmationId(): String? = tags.mapValues("e").firstOrNull()

    fun getRiderPubKey(): String? = tags.mapValues("p").firstOrNull()

    companion object {
        const val KIND = RideshareEventInterface.DRIVER_STATUS
        const val ALT = "Driver status"
        const val STATUS_ON_THE_WAY = "on the way"
        const val STATUS_GETTING_CLOSE = "getting close"
        const val STATUS_COMPLETED = "completed"

        /**
         * Creates a new driver status update event.
         *
         * @param rideConfirmationEvent The ride confirmation event this status update is for
         * @param status The current status of the ride
         * @param approxLocation The approximate current location of the driver
         * @param signer The signer to use for signing the event
         * @param createdAt The time the event was created
         * @param onReady Callback for when the event is ready
         */
        fun createStatusUpdate(
            rideConfirmationEvent: RideConfirmationEvent,
            status: String,
            approxLocation: DriverAvailabilityEvent.Location,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (DriverStatusEvent) -> Unit,
        ) {
            val tags =
                arrayOf(
                    arrayOf("e", rideConfirmationEvent.id),
                    arrayOf("p", rideConfirmationEvent.pubKey),
                    AltTag.assemble(ALT),
                )

            val content: String =
                JacksonHelper.mapper.writeValueAsString(
                    Content(
                        status = status,
                        approx_location = approxLocation,
                    ),
                )

            signer.sign<DriverStatusEvent>(createdAt, KIND, tags, content) { signedEvent ->
                onReady(signedEvent)
            }
        }

        /**
         * Creates a new driver status event for ride completion with payment details.
         *
         * @param rideConfirmationEvent The ride confirmation event this completion is for
         * @param approxLocation The current location (destination) of the driver
         * @param finalFare The final fare for the ride
         * @param lightningInvoice The Lightning Network invoice for payment
         * @param signer The signer to use for signing the event
         * @param createdAt The time the event was created
         * @param onReady Callback for when the event is ready
         */
        fun createRideCompletion(
            rideConfirmationEvent: RideConfirmationEvent,
            approxLocation: DriverAvailabilityEvent.Location,
            finalFare: String,
            lightningInvoice: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (DriverStatusEvent) -> Unit,
        ) {
            val tags =
                arrayOf(
                    arrayOf("e", rideConfirmationEvent.id),
                    arrayOf("p", rideConfirmationEvent.pubKey),
                    AltTag.assemble(ALT),
                )

            val content: String =
                JacksonHelper.mapper.writeValueAsString(
                    Content(
                        status = STATUS_COMPLETED,
                        approx_location = approxLocation,
                        final_fare = finalFare,
                        invoice = lightningInvoice,
                    ),
                )

            signer.sign<DriverStatusEvent>(createdAt, KIND, tags, content) { signedEvent ->
                onReady(signedEvent)
            }
        }
    }
} 
