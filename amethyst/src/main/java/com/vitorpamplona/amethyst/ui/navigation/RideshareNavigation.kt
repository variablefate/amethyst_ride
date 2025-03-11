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
package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.rideshare.RideshareScreen

/**
 * Interface for defining navigation destinations in the app
 */
interface Destination {
    val route: String
    val icon: ImageVector
    val selectedIcon: ImageVector
    val resourceId: String

    @Composable
    fun Screen(
        navController: NavController,
        accountViewModel: AccountViewModel,
    )
}

fun NavGraphBuilder.rideshareGraph(
    navController: NavHostController,
    accountViewModel: AccountViewModel,
) {
    composable("rideshare") {
        RideshareScreen(
            accountViewModel = accountViewModel,
            onBack = { navController.popBackStack() },
        )
    }
}

/**
 * Represents the Rideshare destination in the app
 */
object RideshareDestination : Destination {
    override val route: String = "rideshare"
    override val icon: ImageVector = Icons.Outlined.DirectionsCar
    override val selectedIcon: ImageVector = Icons.Filled.DirectionsCar
    override val resourceId: String = "routes_rideshare"

    @Composable
    override fun Screen(
        navController: NavController,
        accountViewModel: AccountViewModel,
    ) {
        RideshareScreen(
            accountViewModel = accountViewModel,
            onBack = { navController.popBackStack() },
        )
    }
} 
