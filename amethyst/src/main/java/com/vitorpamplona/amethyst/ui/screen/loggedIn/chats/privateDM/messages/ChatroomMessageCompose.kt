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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.DisplayDraftChat
import com.vitorpamplona.amethyst.ui.note.FollowingIcon
import com.vitorpamplona.amethyst.ui.note.InnerUserPicture
import com.vitorpamplona.amethyst.ui.note.LikeReaction
import com.vitorpamplona.amethyst.ui.note.LoadDecryptedContentOrNull
import com.vitorpamplona.amethyst.ui.note.NoteQuickActionMenu
import com.vitorpamplona.amethyst.ui.note.ObserveDraftEvent
import com.vitorpamplona.amethyst.ui.note.RelayBadgesHorizontal
import com.vitorpamplona.amethyst.ui.note.ReplyReaction
import com.vitorpamplona.amethyst.ui.note.WatchBlockAndReport
import com.vitorpamplona.amethyst.ui.note.WatchNoteEvent
import com.vitorpamplona.amethyst.ui.note.WatchUserFollows
import com.vitorpamplona.amethyst.ui.note.ZapReaction
import com.vitorpamplona.amethyst.ui.note.timeAgoShort
import com.vitorpamplona.amethyst.ui.note.types.RenderEncryptedFile
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.Size5Modifier
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.chatAuthorBox
import com.vitorpamplona.amethyst.ui.theme.incognitoIconModifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip02FollowList.EmptyTagList
import com.vitorpamplona.quartz.nip02FollowList.ImmutableListOfLists
import com.vitorpamplona.quartz.nip02FollowList.toImmutableListOfLists
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip17Dm.base.NIP17Group
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftEvent

@Composable
fun ChatroomMessageCompose(
    baseNote: Note,
    routeForLastRead: String?,
    innerQuote: Boolean = false,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
) {
    WatchNoteEvent(baseNote = baseNote, accountViewModel = accountViewModel) {
        WatchBlockAndReport(
            note = baseNote,
            showHiddenWarning = innerQuote,
            modifier = Modifier.fillMaxWidth(),
            accountViewModel = accountViewModel,
            nav = nav,
        ) { canPreview ->
            NormalChatNote(
                baseNote,
                routeForLastRead,
                innerQuote,
                canPreview,
                parentBackgroundColor,
                accountViewModel,
                nav,
                onWantsToReply,
                onWantsToEditDraft,
            )
        }
    }
}

@Composable
fun NormalChatNote(
    note: Note,
    routeForLastRead: String?,
    innerQuote: Boolean = false,
    canPreview: Boolean = true,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
) {
    val isLoggedInUser =
        remember(note.author) {
            accountViewModel.isLoggedUser(note.author)
        }

    if (routeForLastRead != null) {
        LaunchedEffect(key1 = routeForLastRead) {
            accountViewModel.loadAndMarkAsRead(routeForLastRead, note.createdAt())
        }
    }

    val drawAuthorInfo by
        remember(note) {
            derivedStateOf {
                val noteEvent = note.event
                if (accountViewModel.isLoggedUser(note.author)) {
                    false // never shows the user's pictures
                } else if (noteEvent is PrivateDmEvent) {
                    false // one-on-one, never shows it.
                } else if (noteEvent is ChatroomKeyable) {
                    // only shows in a group chat.
                    noteEvent.chatroomKey(accountViewModel.userProfile().pubkeyHex).users.size > 1
                } else {
                    true
                }
            }
        }

    ChatBubbleLayout(
        isLoggedInUser = isLoggedInUser,
        isDraft = note.event is DraftEvent,
        innerQuote = innerQuote,
        isComplete = accountViewModel.settings.featureSet == FeatureSetType.COMPLETE,
        hasDetailsToShow = note.zaps.isNotEmpty() || note.zapPayments.isNotEmpty() || note.reactions.isNotEmpty(),
        drawAuthorInfo = drawAuthorInfo,
        parentBackgroundColor = parentBackgroundColor,
        onClick = {
            if (note.event is ChannelCreateEvent) {
                nav.nav("Channel/${note.idHex}")
                true
            } else {
                false
            }
        },
        onAuthorClick = {
            note.author?.let {
                nav.nav("User/${it.pubkeyHex}")
            }
        },
        actionMenu = { onDismiss ->
            NoteQuickActionMenu(
                note = note,
                onDismiss = onDismiss,
                onWantsToEditDraft = { onWantsToEditDraft(note) },
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
        drawAuthorLine = {
            DrawAuthorInfo(
                note,
                accountViewModel,
                nav,
            )
        },
        detailRow = {
            IncognitoBadge(note)
            ChatTimeAgo(note)
            RelayBadgesHorizontal(note, accountViewModel, nav = nav)

            Spacer(modifier = DoubleHorzSpacer)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = RowColSpacing) {
                if (!note.isDraft()) {
                    ReplyReaction(
                        baseNote = note,
                        grayTint = MaterialTheme.colorScheme.placeholderText,
                        accountViewModel = accountViewModel,
                        showCounter = false,
                        iconSizeModifier = Size18Modifier,
                    ) {
                        onWantsToReply(note)
                    }
                    Spacer(modifier = StdHorzSpacer)
                    LikeReaction(note, MaterialTheme.colorScheme.placeholderText, accountViewModel, nav)

                    ZapReaction(note, MaterialTheme.colorScheme.placeholderText, accountViewModel, nav = nav)
                } else {
                    DisplayDraftChat()
                }
            }
        },
    ) { backgroundBubbleColor ->
        MessageBubbleLines(
            note,
            innerQuote,
            backgroundBubbleColor,
            onWantsToReply,
            onWantsToEditDraft,
            canPreview,
            accountViewModel,
            nav,
        )
    }
}

@Composable
private fun MessageBubbleLines(
    baseNote: Note,
    innerQuote: Boolean,
    backgroundBubbleColor: MutableState<Color>,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    canPreview: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (baseNote.event !is DraftEvent) {
        RenderReplyRow(
            note = baseNote,
            innerQuote = innerQuote,
            backgroundBubbleColor = backgroundBubbleColor,
            accountViewModel = accountViewModel,
            nav = nav,
            onWantsToReply = onWantsToReply,
            onWantsToEditDraft = onWantsToEditDraft,
        )
    }

    NoteRow(
        note = baseNote,
        canPreview = canPreview,
        innerQuote = innerQuote,
        onWantsToReply = onWantsToReply,
        onWantsToEditDraft = onWantsToEditDraft,
        backgroundBubbleColor = backgroundBubbleColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
private fun RenderReplyRow(
    note: Note,
    innerQuote: Boolean,
    backgroundBubbleColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
) {
    if (!innerQuote && note.replyTo?.lastOrNull() != null) {
        RenderReply(note, backgroundBubbleColor, accountViewModel, nav, onWantsToReply, onWantsToEditDraft)
    }
}

@Composable
private fun RenderReply(
    note: Note,
    backgroundBubbleColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        @Suppress("ProduceStateDoesNotAssignValue")
        val replyTo =
            produceState(initialValue = note.replyTo?.lastOrNull()) {
                accountViewModel.unwrapIfNeeded(value) {
                    value = it
                }
            }

        replyTo.value?.let { note ->
            ChatroomMessageCompose(
                baseNote = note,
                routeForLastRead = null,
                innerQuote = true,
                parentBackgroundColor = backgroundBubbleColor,
                accountViewModel = accountViewModel,
                nav = nav,
                onWantsToReply = onWantsToReply,
                onWantsToEditDraft = onWantsToEditDraft,
            )
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    canPreview: Boolean,
    innerQuote: Boolean,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    backgroundBubbleColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (note.event) {
            is ChannelCreateEvent -> RenderCreateChannelNote(note)
            is ChannelMetadataEvent -> RenderChangeChannelMetadataNote(note)
            is DraftEvent ->
                RenderDraftEvent(
                    note,
                    canPreview,
                    innerQuote,
                    onWantsToReply,
                    onWantsToEditDraft,
                    backgroundBubbleColor,
                    accountViewModel,
                    nav,
                )
            is ChatMessageEncryptedFileHeaderEvent ->
                RenderEncryptedFile(
                    note,
                    backgroundBubbleColor,
                    accountViewModel,
                    nav,
                )
            else ->
                RenderRegularTextNote(
                    note,
                    canPreview,
                    innerQuote,
                    backgroundBubbleColor,
                    accountViewModel,
                    nav,
                )
        }
    }
}

@Composable
private fun RenderDraftEvent(
    note: Note,
    canPreview: Boolean,
    innerQuote: Boolean,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    backgroundBubbleColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ObserveDraftEvent(note, accountViewModel) {
        Column {
            RenderReplyRow(
                note = it,
                innerQuote = innerQuote,
                backgroundBubbleColor = backgroundBubbleColor,
                accountViewModel = accountViewModel,
                nav = nav,
                onWantsToReply = onWantsToReply,
                onWantsToEditDraft = onWantsToEditDraft,
            )

            NoteRow(
                note = it,
                canPreview = canPreview,
                innerQuote = innerQuote,
                onWantsToReply = onWantsToReply,
                onWantsToEditDraft = onWantsToEditDraft,
                backgroundBubbleColor = backgroundBubbleColor,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun IncognitoBadge(baseNote: Note) {
    if (baseNote.event is NIP17Group) {
        Icon(
            painter = painterResource(id = R.drawable.incognito),
            null,
            modifier = incognitoIconModifier,
            tint = MaterialTheme.colorScheme.placeholderText,
        )
        Spacer(modifier = StdHorzSpacer)
    } else if (baseNote.event is PrivateDmEvent) {
        Icon(
            painter = painterResource(id = R.drawable.incognito_off),
            null,
            modifier = incognitoIconModifier,
            tint = MaterialTheme.colorScheme.placeholderText,
        )
        Spacer(modifier = StdHorzSpacer)
    }
}

@Composable
fun ChatTimeAgo(baseNote: Note) {
    val nowStr = stringRes(id = R.string.now)
    val time = remember(baseNote) { timeAgoShort(baseNote.createdAt() ?: 0, nowStr) }

    Text(
        text = time,
        color = MaterialTheme.colorScheme.placeholderText,
        fontSize = Font12SP,
        maxLines = 1,
    )
}

@Composable
private fun RenderRegularTextNote(
    note: Note,
    canPreview: Boolean,
    innerQuote: Boolean,
    backgroundBubbleColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadDecryptedContentOrNull(note = note, accountViewModel = accountViewModel) { eventContent ->
        if (eventContent != null) {
            SensitivityWarning(
                note = note,
                accountViewModel = accountViewModel,
            ) {
                val tags = remember(note.event) { note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList }

                TranslatableRichTextViewer(
                    content = eventContent,
                    canPreview = canPreview,
                    quotesLeft = if (innerQuote) 0 else 1,
                    modifier = Modifier,
                    tags = tags,
                    backgroundColor = backgroundBubbleColor,
                    id = note.idHex,
                    callbackUri = note.toNostrUri(),
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        } else {
            TranslatableRichTextViewer(
                content = stringRes(id = R.string.could_not_decrypt_the_message),
                canPreview = true,
                quotesLeft = 0,
                modifier = Modifier,
                tags = EmptyTagList,
                backgroundColor = backgroundBubbleColor,
                id = note.idHex,
                callbackUri = note.toNostrUri(),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun RenderChangeChannelMetadataNote(note: Note) {
    val noteEvent = note.event as? ChannelMetadataEvent ?: return

    val channelInfo = noteEvent.channelInfo()
    val text =
        note.author?.toBestDisplayName().toString() +
            " ${stringRes(R.string.changed_chat_name_to)} '" +
            (channelInfo.name ?: "") +
            "', ${stringRes(R.string.description_to)} '" +
            (channelInfo.about ?: "") +
            "', ${stringRes(R.string.and_picture_to)} '" +
            (channelInfo.picture ?: "") +
            "'"

    CreateTextWithEmoji(
        text = text,
        tags = note.author?.info?.tags,
    )
}

@Composable
private fun RenderCreateChannelNote(note: Note) {
    val noteEvent = note.event as? ChannelCreateEvent ?: return
    val channelInfo = remember { noteEvent.channelInfo() }

    val text =
        note.author?.toBestDisplayName().toString() +
            " ${stringRes(R.string.created)} " +
            (channelInfo.name ?: "") +
            " ${stringRes(R.string.with_description_of)} '" +
            (channelInfo.about ?: "") +
            "', ${stringRes(R.string.and_picture)} '" +
            (channelInfo.picture ?: "") +
            "'"

    CreateTextWithEmoji(
        text = text,
        tags = note.author?.info?.tags,
    )
}

@Composable
private fun DrawAuthorInfo(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    baseNote.author?.let {
        WatchAndDisplayUser(it, accountViewModel, nav)
    }
}

@Composable
fun UserDisplayNameLayout(
    picture: @Composable () -> Unit,
    name: @Composable () -> Unit,
) {
    Box(chatAuthorBox, contentAlignment = Alignment.TopEnd) {
        picture()
    }

    Spacer(modifier = StdHorzSpacer)

    name()
}

@Composable
private fun WatchAndDisplayUser(
    author: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val userState by author.live().userMetadataInfo.observeAsState()

    UserDisplayNameLayout(
        picture = {
            InnerUserPicture(
                userHex = author.pubkeyHex,
                userPicture = userState?.picture,
                userName = userState?.bestName(),
                size = Size20dp,
                modifier = Modifier,
                accountViewModel = accountViewModel,
            )

            WatchUserFollows(author.pubkeyHex, accountViewModel) { newFollowingState ->
                if (newFollowingState) {
                    FollowingIcon(Size5Modifier)
                }
            }
        },
        name = {
            if (userState != null) {
                DisplayMessageUsername(userState?.bestName() ?: author.pubkeyDisplayHex(), userState?.tags ?: EmptyTagList)
            } else {
                DisplayMessageUsername(author.pubkeyDisplayHex(), EmptyTagList)
            }
        },
    )
}

@Composable
private fun DisplayMessageUsername(
    userDisplayName: String,
    userTags: ImmutableListOfLists<String>,
) {
    CreateTextWithEmoji(
        text = userDisplayName,
        tags = userTags,
        maxLines = 1,
        fontWeight = FontWeight.Bold,
    )
}
