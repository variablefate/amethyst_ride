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
package com.vitorpamplona.amethyst.ui.actions.relays

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.Nip11CachedRetriever
import com.vitorpamplona.ammolite.relays.Constants
import com.vitorpamplona.ammolite.relays.Constants.activeTypesRideshare
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.ammolite.relays.RelayStats
import com.vitorpamplona.quartz.nip65RelayList.RelayUrlFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
class RideshareRelayListViewModel : ViewModel() {
    private lateinit var account: Account

    private val _relays = MutableStateFlow<List<BasicRelaySetupInfo>>(emptyList())
    val relays = _relays.asStateFlow()

    var hasModified = false

    fun load(account: Account) {
        this.account = account
        clear()
        loadRelayDocuments()
    }

    fun create() {
        if (hasModified) {
            viewModelScope.launch(Dispatchers.IO) {
                // Save the rideshare relays to account settings
                val updatedRelays = account.settings.localRelays.toMutableSet()
                // Remove any existing rideshare relays
                updatedRelays.removeAll { relay ->
                    relay.feedTypes == activeTypesRideshare ||
                        relay.feedTypes.any { it in activeTypesRideshare }
                }

                // Add the new rideshare relays
                relays.value.forEach { relay ->
                    updatedRelays.add(
                        RelaySetupInfo(
                            relay.url,
                            true, // Default to read enabled
                            true, // Default to write enabled
                            activeTypesRideshare,
                        ),
                    )
                }

                account.settings.localRelays = updatedRelays
                clear()
            }
        }
    }

    fun loadRelayDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            _relays.value.forEach { item ->
                Nip11CachedRetriever.loadRelayInfo(
                    dirtyUrl = item.url,
                    forceProxy = account.shouldUseTorForDirty(item.url),
                    onInfo = {
                        togglePaidRelay(item, it.limitation?.payment_required ?: false)
                    },
                    onError = { url, errorCode, exceptionMessage -> },
                )
            }
        }
    }

    fun clear() {
        hasModified = false
        _relays.update {
            // Find all rideshare relays in local settings
            account.settings.localRelays
                .filter { relay ->
                    relay.feedTypes == activeTypesRideshare ||
                        relay.feedTypes.any { it in activeTypesRideshare }
                }.map {
                    BasicRelaySetupInfo(
                        url = RelayUrlFormatter.normalize(it.url),
                        relayStat = RelayStats.get(it.url),
                    )
                }.distinctBy { it.url }
                .sortedBy { it.relayStat.receivedBytes }
                .reversed()
        }
    }

    fun addDefaultRelays() {
        hasModified = true

        _relays.update {
            Constants.defaultRelays
                .filter { relay ->
                    relay.feedTypes == activeTypesRideshare ||
                        relay.feedTypes.any { it in activeTypesRideshare }
                }.map {
                    BasicRelaySetupInfo(
                        url = RelayUrlFormatter.normalize(it.url),
                        relayStat = RelayStats.get(it.url),
                    )
                }.distinctBy { it.url }
                .sortedBy { it.relayStat.receivedBytes }
                .reversed()
        }
    }

    fun addRelay(relay: BasicRelaySetupInfo) {
        if (relays.value.any { it.url == relay.url }) return

        _relays.update { it.plus(relay) }
        hasModified = true
    }

    fun deleteRelay(relay: BasicRelaySetupInfo) {
        _relays.update { it.minus(relay) }
        hasModified = true
    }

    fun deleteAll() {
        _relays.update { relays -> emptyList() }
        hasModified = true
    }

    fun togglePaidRelay(
        relay: BasicRelaySetupInfo,
        isPaid: Boolean,
    ) {
        _relays.update { it.updated(relay, relay.copy(paidRelay = isPaid)) }
    }

    private fun List<BasicRelaySetupInfo>.updated(
        old: BasicRelaySetupInfo,
        new: BasicRelaySetupInfo,
    ): List<BasicRelaySetupInfo> =
        map {
            if (it.url == old.url) {
                new
            } else {
                it
            }
        }
} 
