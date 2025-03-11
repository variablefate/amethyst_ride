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

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Utility class for formatting and converting between satoshis and fiat currencies.
 */
object SatoshiFormatter {
    // Constants
    private const val SATS_PER_BTC = 100_000_000L

    // Default exchange rate (USD per BTC)
    // In a real app, this would be fetched from an API
    private var btcToUsdRate = 60000.0

    /**
     * Formats a satoshi amount as a string with the appropriate unit.
     *
     * @param satoshis The amount in satoshis
     * @return Formatted string (e.g., "1,234 sats")
     */
    fun format(satoshis: Long): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US)
        return "${formatter.format(satoshis)} sats"
    }

    /**
     * Converts a USD amount to satoshis using the current exchange rate.
     *
     * @param usdAmount The amount in USD
     * @return The equivalent amount in satoshis
     */
    fun usdToSatoshis(usdAmount: Double): Long {
        val btcAmount = usdAmount / btcToUsdRate
        return (btcAmount * SATS_PER_BTC).roundToLong()
    }

    /**
     * Converts a satoshi amount to USD using the current exchange rate.
     *
     * @param satoshis The amount in satoshis
     * @return The equivalent amount in USD
     */
    fun satoshisToUsd(satoshis: Long): Double {
        val btcAmount = satoshis.toDouble() / SATS_PER_BTC
        return btcAmount * btcToUsdRate
    }

    /**
     * Formats a satoshi amount as USD.
     *
     * @param satoshis The amount in satoshis
     * @return Formatted USD string (e.g., "$12.34")
     */
    fun formatAsUsd(satoshis: Long): String {
        val usdAmount = satoshisToUsd(satoshis)
        val formatter = NumberFormat.getCurrencyInstance(Locale.US)
        return formatter.format(usdAmount)
    }

    /**
     * Updates the BTC to USD exchange rate.
     * In a real app, this would be called periodically with data from an exchange rate API.
     *
     * @param newRate The new exchange rate (USD per BTC)
     */
    fun updateExchangeRate(newRate: Double) {
        btcToUsdRate = newRate
    }
} 
