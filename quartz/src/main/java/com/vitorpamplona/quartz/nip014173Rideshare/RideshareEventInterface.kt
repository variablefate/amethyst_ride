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

import com.vitorpamplona.quartz.nip01Core.core.IEvent

/**
 * Base interface for all NIP-014173 rideshare protocol events.
 * This protocol defines a decentralized ridesharing system using Nostr events.
 */
interface RideshareEventInterface : IEvent {
    companion object {
        // Event kinds as defined in NIP-014173
        const val DRIVER_AVAILABILITY = 3000
        const val RIDE_OFFER = 3001
        const val RIDE_ACCEPTANCE = 3002
        const val RIDE_CONFIRMATION = 3003
        const val DRIVER_STATUS = 3004

        // Standard hashtag for rideshare events
        const val RIDESHARE_HASHTAG = "rideshare"
    }
} 
