/*
 * Copyright (c) 2017 BlackBerry.  All Rights Reserved.
 *
 * You must obtain a license from and pay any applicable license fees to
 * BlackBerry before you may reproduce, modify or distribute this
 * software, or any work that includes all or part of this software.
 *
 * This file may contain contributions from others. Please review this entire
 * file for other proprietary rights or license notices.
 */

package com.bbm.sdk.support.ui.widgets.chats;

import android.support.annotation.NonNull;

import com.bbm.sdk.bbmds.ChatMessage;

/**
 * Provide colors to be used in chat bubbles.
 */
public interface ChatBubbleColorProvider {

    /**
     * Provide a {@link ChatBubbleColors} for the given ChatMessage
     * @param message the chat message for which the colors must be provided
     * @return {@link ChatBubbleColors} for the user
     */
    ChatBubbleColors getMpcMessageColors(@NonNull ChatMessage message);

    /**
     * Provide a {@link ChatBubbleColors} to be used for outgoing message bubbles.
     * @param message the chat message for which the colors must be provided
     * @return {@link ChatBubbleColors} for outgoing messages from the local user.
     */
    ChatBubbleColors getOutgoingMessageColors(@NonNull ChatMessage message);

    /**
     * Provide a {@link ChatBubbleColors} for incoming messages to be used in a one to one chat.
     * @param message the chat message for which the colors must be provided
     * @return {@link ChatBubbleColors} for incoming messages in a one to one chat.
     */
    ChatBubbleColors getOneToOneIncomingMessageColors(@NonNull ChatMessage message);

}
