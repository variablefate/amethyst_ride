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
 * Kind 3002: Ride Acceptance Event
 * Description: Driver accepts the rider's offer.
 */
@Immutable
class RideAcceptanceEvent(
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
    )

    fun getContent(): Content? =
        try {
            JacksonHelper.mapper.readValue(content, Content::class.java)
        } catch (e: Exception) {
            null
        }

    fun getStatus(): String? = getContent()?.status

    fun getRideOfferId(): String? = tags.mapValues("e").firstOrNull()

    fun getRiderPubKey(): String? = tags.mapValues("p").firstOrNull()

    companion object {
        const val KIND = RideshareEventInterface.RIDE_ACCEPTANCE
        const val ALT = "Ride acceptance"
        const val STATUS_ACCEPTED = "accepted"

        /**
         * Creates a new ride acceptance event.
         *
         * @param rideOfferEvent The ride offer event this acceptance responds to
         * @param signer The signer to use for signing the event
         * @param createdAt The time the event was created
         * @param onReady Callback for when the event is ready
         */
        fun create(
            rideOfferEvent: RideOfferEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (RideAcceptanceEvent) -> Unit,
        ) {
            val tags =
                arrayOf(
                    arrayOf("e", rideOfferEvent.id),
                    arrayOf("p", rideOfferEvent.pubKey),
                    AltTag.assemble(ALT),
                )

            val content: String =
                JacksonHelper.mapper.writeValueAsString(
                    Content(
                        status = STATUS_ACCEPTED,
                    ),
                )

            signer.sign<RideAcceptanceEvent>(createdAt, KIND, tags, content) { signedEvent ->
                onReady(signedEvent)
            }
        }
    }
} 
