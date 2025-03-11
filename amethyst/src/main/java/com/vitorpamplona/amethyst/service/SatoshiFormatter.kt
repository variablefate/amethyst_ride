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

/**
 * Utility class for handling satoshi formatting and conversions.
 * For the Nostr rideshare NIP-014173 implementation, all fare estimates and payments
 * are handled in satoshis for maximum precision and privacy.
 */
object SatoshiFormatter {
    private const val SATS_PER_USD = 1000L // 1000 satoshis = 1 USD (simplified conversion)
    private const val SATS_PER_BTC = 100_000_000L // 100 million satoshis = 1 BTC

    /**
     * Format a satoshi amount for display with comma separators
     * @param satoshis The amount in satoshis
     * @return Formatted string with commas (e.g., "5,000 sats")
     */
    fun formatSatoshisForDisplay(satoshis: Long): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US)
        return "${formatter.format(satoshis)} sats"
    }

    /**
     * Format a satoshi amount for display with comma separators
     * @param satoshisStr The amount in satoshis as string
     * @return Formatted string with commas (e.g., "5,000 sats")
     */
    fun formatSatoshisForDisplay(satoshisStr: String): String =
        try {
            val satoshis = satoshisStr.toLong()
            formatSatoshisForDisplay(satoshis)
        } catch (e: NumberFormatException) {
            "$satoshisStr sats"
        }

    /**
     * Convert a BTC value to satoshis
     * @param btc Amount in BTC
     * @return Amount in satoshis
     */
    fun btcToSatoshis(btc: Double): Long = (btc * SATS_PER_BTC).toLong()

    /**
     * Convert a satoshi value to BTC
     * @param satoshis Amount in satoshis
     * @return Amount in BTC
     */
    fun satoshisToBtc(satoshis: Long): Double = satoshis.toDouble() / SATS_PER_BTC

    /**
     * Parse a string that might contain a BTC amount or satoshi amount
     * @param str The string to parse
     * @return Amount in satoshis
     */
    fun parseToSatoshis(str: String): Long =
        try {
            // Check if the string is in BTC format (contains decimal point)
            if (str.contains(".")) {
                // Remove the "BTC" suffix if present and trim
                val btcValue = str.replace("BTC", "").trim().toDouble()
                btcToSatoshis(btcValue)
            } else {
                // Just parse as satoshis
                val cleanStr = str.replace("sats", "").replace("sat", "").trim()
                cleanStr.toLong()
            }
        } catch (e: Exception) {
            // Default value if parsing fails
            1000L
        }

    /**
     * Convert USD to satoshis using the 1000 sats = $1 conversion
     * @param usd Amount in USD
     * @return Amount in satoshis
     */
    fun usdToSatoshis(usd: Double): Long = (usd * SATS_PER_USD).toLong()

    /**
     * Convert satoshis to USD using the 1000 sats = $1 conversion
     * @param satoshis Amount in satoshis
     * @return Amount in USD
     */
    fun satoshisToUsd(satoshis: Long): Double = satoshis.toDouble() / SATS_PER_USD
} 
