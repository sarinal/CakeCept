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

import com.bbm.sdk.bbmds.ChatMessage;
import com.bbm.sdk.support.R;

public class MessageStatusIcons {

    /**
     * Get the message status icon matching the {@link ChatMessage.State}
     * @param message The chat message whose state should be checked
     * @return a resource identifier for the drawable matching the state of the message
     */
    public static int getMessageStatusIconId(final ChatMessage message) {
        final ChatMessage.State status = message.state;
        final boolean incoming = message.hasFlag(ChatMessage.Flags.Incoming);

        int messageIcon = R.drawable.ic_item_message_unread;

        if (incoming) {
            if (message.recall == ChatMessage.Recall.Recalled) {
                messageIcon =  R.drawable.ic_item_message_retract;
            } else if (status == ChatMessage.State.Read) {
                messageIcon = R.drawable.ic_item_message_read;
            } else {
                messageIcon = R.drawable.ic_item_message_unread;
            }
        } else {
            if (message.recall != ChatMessage.Recall.Unspecified && message.recall != ChatMessage.Recall.None) {
                messageIcon =  R.drawable.ic_item_message_retract;
            } else if (status == ChatMessage.State.Sending) {
                messageIcon =  R.drawable.ic_item_message_sending;
            } else if (status == ChatMessage.State.Sent) {
                messageIcon =  R.drawable.ic_item_message_sent;
            } else if (status == ChatMessage.State.Read) {
                if (message.stateIsPartial) {
                    messageIcon =  R.drawable.ic_item_message_r_partial;
                } else {
                    messageIcon =  R.drawable.ic_item_message_r;
                }
            } else if (status == ChatMessage.State.Delivered) {
                if (message.stateIsPartial) {
                    messageIcon =  R.drawable.ic_item_message_delivered_partial;
                } else {
                    messageIcon =  R.drawable.ic_item_message_delivered;
                }
            } else if (status == ChatMessage.State.Failed) {
                messageIcon =  R.drawable.ic_item_message_fail;
            }
        }
        return messageIcon;
    }
}
