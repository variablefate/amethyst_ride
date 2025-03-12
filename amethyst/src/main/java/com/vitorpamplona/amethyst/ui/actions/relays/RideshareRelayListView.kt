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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.rememberExtendedNav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer

@Composable
fun RideshareRelayList(
    postViewModel: RideshareRelayListViewModel,
    accountViewModel: AccountViewModel,
    onClose: () -> Unit,
    nav: INav,
) {
    val newNav = rememberExtendedNav(nav, onClose)
    val feedState by postViewModel.relays.collectAsStateWithLifecycle()

    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyColumn(
            contentPadding = FeedPadding,
        ) {
            renderRideshareItems(feedState, postViewModel, accountViewModel, newNav)
        }
    }
}

fun LazyListScope.renderRideshareItems(
    feedState: List<BasicRelaySetupInfo>,
    postViewModel: RideshareRelayListViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    itemsIndexed(feedState, key = { _, item -> "Rideshare" + item.url }) { index, item ->
        BasicRelaySetupInfoDialog(
            item,
            onDelete = { postViewModel.deleteRelay(item) },
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    item {
        Spacer(modifier = StdVertSpacer)
        RelayUrlEditField { postViewModel.addRelay(it) }
    }
} 
