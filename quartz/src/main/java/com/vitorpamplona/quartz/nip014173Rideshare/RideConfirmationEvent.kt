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
 * Kind 3003: Ride Confirmation Event
 * Description: Rider confirms the ride and shares their precise pickup location (encrypted).
 */
@Immutable
class RideConfirmationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    RideshareEventInterface {
    data class PreciseLocation(
        val lat: Double,
        val lon: Double,
        val address: String,
    )

    data class Content(
        val precise_pickup: PreciseLocation,
    )

    fun getRideAcceptanceId(): String? = tags.mapValues("e").firstOrNull()

    fun getDriverPubKey(): String? = tags.mapValues("p").firstOrNull()

    fun decryptContent(
        signer: NostrSigner,
        onReady: (Content?) -> Unit,
    ) {
        val driverPubkey = getDriverPubKey() ?: return onReady(null)

        signer.nip44Decrypt(content, driverPubkey) { decrypted ->
            try {
                val decryptedContent = JacksonHelper.mapper.readValue(decrypted, Content::class.java)
                onReady(decryptedContent)
            } catch (e: Exception) {
                e.printStackTrace()
                onReady(null)
            }
        }
    }

    companion object {
        const val KIND = RideshareEventInterface.RIDE_CONFIRMATION
        const val ALT = "Ride confirmation"

        /**
         * Creates a new ride confirmation event with encrypted precise pickup location.
         *
         * @param rideAcceptanceEvent The ride acceptance event this confirmation responds to
         * @param precisePickup The precise pickup location to be encrypted
         * @param signer The signer to use for signing the event
         * @param createdAt The time the event was created
         * @param onReady Callback for when the event is ready
         */
        fun create(
            rideAcceptanceEvent: RideAcceptanceEvent,
            precisePickup: PreciseLocation,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (RideConfirmationEvent) -> Unit,
        ) {
            val driverPubkey = rideAcceptanceEvent.pubKey
            val tags =
                arrayOf(
                    arrayOf("e", rideAcceptanceEvent.id),
                    arrayOf("p", driverPubkey),
                    AltTag.assemble(ALT),
                )

            // Create content to be encrypted
            val contentJson: String =
                JacksonHelper.mapper.writeValueAsString(
                    Content(
                        precise_pickup = precisePickup,
                    ),
                )

            // Encrypt content using NostrSigner's nip44Encrypt method
            signer.nip44Encrypt(contentJson, driverPubkey) { encryptedContent ->
                // Sign the event with the encrypted content
                signer.sign<RideConfirmationEvent>(createdAt, KIND, tags, encryptedContent) { signedEvent ->
                    onReady(signedEvent)
                }
            }
        }
    }
} 
