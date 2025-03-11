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

import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.Size23dp
import com.vitorpamplona.amethyst.ui.theme.Size24dp
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.net.URLEncoder

@Immutable
sealed class Route(
    val route: String,
    val base: String = route.substringBefore("?"),
    val icon: Int,
    val notifSize: Modifier = Modifier.size(Size23dp),
    val iconSize: Modifier = Modifier.size(Size20dp),
    val contentDescriptor: Int = R.string.route,
    val arguments: ImmutableList<NamedNavArgument> = persistentListOf(),
) {
    object Home :
        Route(
            route = "Home",
            icon = R.drawable.ic_home,
            notifSize = Modifier.size(Size25dp),
            iconSize = Modifier.size(Size24dp),
            contentDescriptor = R.string.route_home,
        )

    object Global :
        Route(
            route = "Global",
            icon = R.drawable.ic_globe,
            contentDescriptor = R.string.route_global,
        )

    object Search :
        Route(
            route = "Search",
            icon = R.drawable.ic_moments,
            contentDescriptor = R.string.route_search,
        )

    object Video :
        Route(
            route = "Video",
            icon = R.drawable.ic_video,
            contentDescriptor = R.string.route_video,
        )

    object Discover :
        Route(
            route = "Discover",
            icon = R.drawable.ic_sensors,
            contentDescriptor = R.string.route_discover,
        )

    object Notification :
        Route(
            route = "Notification",
            icon = R.drawable.ic_notifications,
            contentDescriptor = R.string.route_notifications,
        )

    object Message :
        Route(
            route = "Message",
            icon = R.drawable.ic_dm,
            contentDescriptor = R.string.route_messages,
        )

    object BlockedUsers :
        Route(
            route = "BlockedUsers",
            icon = R.drawable.ic_security,
            contentDescriptor = R.string.route_security_filters,
        )

    object Bookmarks :
        Route(
            route = "Bookmarks",
            icon = R.drawable.ic_bookmarks,
            contentDescriptor = R.string.route_home,
        )

    object ContentDiscovery :
        Route(
            icon = R.drawable.ic_bookmarks,
            contentDescriptor = R.string.discover_content,
            route = "ContentDiscovery/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList(),
        )

    object Drafts :
        Route(
            route = "Drafts",
            icon = R.drawable.ic_topics,
            contentDescriptor = R.string.drafts,
        )

    object Profile :
        Route(
            route = "User/{id}",
            icon = R.drawable.ic_profile,
            arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList(),
        )

    object Note :
        Route(
            route = "Note/{id}",
            icon = R.drawable.ic_moments,
            arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList(),
        )

    object Hashtag :
        Route(
            route = "Hashtag/{id}",
            icon = R.drawable.ic_moments,
            arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList(),
        )

    object Geohash :
        Route(
            route = "Geohash/{id}",
            icon = R.drawable.ic_moments,
            arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList(),
        )

    object Community :
        Route(
            route = "Community/{id}",
            icon = R.drawable.ic_moments,
            arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList(),
        )

    object Room :
        Route(
            route = "Room/{id}?message={message}&replyId={replyId}&draftId={draftId}",
            icon = R.drawable.ic_moments,
            arguments =
                listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("message") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("replyId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("draftId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ).toImmutableList(),
        )

    object RoomByAuthor :
        Route(
            route = "RoomByAuthor/{id}",
            icon = R.drawable.ic_moments,
            arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList(),
        )

    object Channel :
        Route(
            route = "Channel/{id}",
            icon = R.drawable.ic_moments,
            arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList(),
        )

    object Event :
        Route(
            route = "Event/{id}",
            icon = R.drawable.ic_moments,
            arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList(),
        )

    object Settings :
        Route(
            route = "Settings",
            icon = R.drawable.ic_settings,
        )

    object Rideshare :
        Route(
            route = "rideshare",
            icon = R.drawable.ic_directions_car,
            contentDescriptor = R.string.rideshare,
        )

    object EditProfile :
        Route(
            route = "EditProfile",
            icon = R.drawable.ic_settings,
        )

    object EditRelays :
        Route(
            route = "EditRelays?toAdd={toAdd}",
            icon = R.drawable.ic_globe,
            contentDescriptor = R.string.relays,
            arguments =
                listOf(
                    navArgument("toAdd") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ).toImmutableList(),
        )

    object NIP47Setup :
        Route(
            route = "NIP47Setup?nip47={nip47}",
            icon = R.drawable.ic_home,
            arguments =
                listOf(
                    navArgument("nip47") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ).toImmutableList(),
        )

    object NewPost :
        Route(
            route = "NewPost?message={message}&attachment={attachment}&baseReplyTo={baseReplyTo}&quote={quote}&fork={fork}&version={version}&draft={draft}&enableGeolocation={enableGeolocation}&enableMessageInterface={enableMessageInterface}",
            icon = R.drawable.ic_moments,
            arguments =
                listOf(
                    navArgument("message") { type = NavType.StringType },
                    navArgument("attachment") { type = NavType.StringType },
                    navArgument("baseReplyTo") { type = NavType.StringType },
                    navArgument("quote") { type = NavType.StringType },
                    navArgument("fork") { type = NavType.StringType },
                    navArgument("version") { type = NavType.StringType },
                    navArgument("draft") { type = NavType.StringType },
                    navArgument("enableGeolocation") { type = NavType.BoolType },
                    navArgument("enableMessageInterface") { type = NavType.BoolType },
                ).toImmutableList(),
        )
}

fun isBaseRoute(
    navController: NavHostController,
    startsWith: String,
): Boolean =
    navController.currentBackStackEntry
        ?.destination
        ?.route
        ?.startsWith(startsWith) ?: false

fun getRouteWithArguments(navController: NavHostController): String? {
    val currentEntry = navController.currentBackStackEntry ?: return null
    return getRouteWithArguments(currentEntry.destination, currentEntry.arguments)
}

fun getRouteWithArguments(navState: State<NavBackStackEntry?>): String? = navState.value?.let { getRouteWithArguments(it.destination, it.arguments) }

private fun getRouteWithArguments(
    destination: NavDestination,
    arguments: Bundle?,
): String? {
    var route = destination.route ?: return null
    arguments?.let { bundle ->
        destination.arguments.forEach {
            val key = it.key
            val value = it.value.type[bundle, key]?.toString()
            if (value == null) {
                val keyStart = route.indexOf("{$key}")
                // if it is a parameter, removes the complete segment `var={key}` and adjust connectors `#`,
                // `&` or `&`
                if (keyStart > 0 && route[keyStart - 1] == '=') {
                    val end = keyStart + "{$key}".length
                    var start = keyStart
                    for (i in keyStart downTo 0) {
                        if (route[i] == '#' || route[i] == '?' || route[i] == '&') {
                            start = i + 1
                            break
                        }
                    }
                    if (end < route.length && route[end] == '&') {
                        route = route.removeRange(start, end + 1)
                    } else if (end < route.length && route[end] == '#') {
                        route = route.removeRange(start - 1, end)
                    } else if (end == route.length) {
                        route = route.removeRange(start - 1, end)
                    } else {
                        route = route.removeRange(start, end)
                    }
                } else {
                    route = route.replaceFirst("{$key}", "")
                }
            } else {
                route = route.replaceFirst("{$key}", value)
            }
        }
    }
    return route
}

fun buildNewPostRoute(
    draftMessage: String? = null,
    attachment: Uri? = null,
    baseReplyTo: String? = null,
    quote: String? = null,
    fork: String? = null,
    version: String? = null,
    draft: String? = null,
    enableGeolocation: Boolean = false,
    enableMessageInterface: Boolean = false,
): String =
    "NewPost?" +
        "message=${draftMessage?.let { URLEncoder.encode(it, "utf-8") } ?: ""}&" +
        "attachment=${attachment?.let { URLEncoder.encode(it.toString(), "utf-8") } ?: ""}&" +
        "baseReplyTo=${baseReplyTo ?: ""}&" +
        "quote=${quote ?: ""}&" +
        "fork=${fork ?: ""}&" +
        "version=${version ?: ""}&" +
        "draft=${draft ?: ""}&" +
        "enableGeolocation=$enableGeolocation&" +
        "enableMessageInterface=$enableMessageInterface"
