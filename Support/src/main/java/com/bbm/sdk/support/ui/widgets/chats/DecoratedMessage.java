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


import android.support.annotation.Nullable;

import com.bbm.sdk.bbmds.ChatMessage;

/**
 * The Decorated Message DataModel for display purpose. In theory, this should suffice the need
 * to render a chat bubble on screen. Therefore, any property to be rendered, should be put in here.
 * Even if some properties are not coming from the Message itself, if it contributes to rendering,
 * it belongs here.
 */
public class DecoratedMessage {
    private boolean mMergeBefore = false;
    private boolean mIsConference;
    private ChatBubbleColors mColors;
    private ChatMessage mChatMessage;

    public DecoratedMessage() {
        super();
    }

    public DecoratedMessage(
            final ChatMessage message,
            final boolean mergeBefore,
            final boolean isConference,
            final ChatBubbleColors colors) {
        super();
        mChatMessage = message;
        mMergeBefore = mergeBefore;
        mIsConference = isConference;
        mColors = colors;
    }

    /**
     * @return the ChatMessage whose content should be displayed
     */
    public @Nullable ChatMessage getChatMessage() {
        return mChatMessage;
    }

    /**
     * @return the timestamp of the message
     */
    public long getTimestamp() {
        return mChatMessage == null ? 0 : mChatMessage.timestamp;
    }

    /**
     * @return the sender uri of the message
     */
    public @Nullable String getSenderUri() {
        return mChatMessage == null ? null : mChatMessage.senderUri;
    }

    /**
     * @return true if the message has the incoming flag
     */
    public boolean isIncoming() {
        return mChatMessage != null && mChatMessage.hasFlag(ChatMessage.Flags.Incoming);
    }

    /**
     * @return true if this message should be merged visually with the message before it.
     */
    public final boolean shouldMergeBefore() {
        return mMergeBefore;
    }

    /**
     * @return true if this message belongs to a conference
     */
    public final boolean isConference() {
        return mIsConference;
    }

    /**
     * @return the colors to be used when displaying this message
     */
    public final @Nullable ChatBubbleColors getColors() {
        return mColors;
    }

    /**
     * @return true if the senders avatar should be shown when displaying this message
     */
    public final boolean showAvatar() {
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (mMergeBefore ? 1231 : 1237);
        result = prime * result + (mIsConference ? 1231 : 1237);
        result = prime * result + (mColors == null ? 0 : mColors.backgroundColor);
        result = prime * result + ((mChatMessage == null) ? 0 : mChatMessage.hashCode());

        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        final DecoratedMessage other = (DecoratedMessage) obj;
        if (mMergeBefore != other.mMergeBefore) {
            return false;
        }
        if (mIsConference != other.mIsConference) {
            return false;
        }
        if (mColors != other.mColors) {
            return false;
        }
        if (mChatMessage == null) {
            if (other.mChatMessage != null) {
                return false;
            }
        } else if (!mChatMessage.equals(other.mChatMessage)) {
            return false;
        }
        return true;
    }


    @Override
    public String toString() {
        return "DecoratedMessage{" +
                "mChatMessage.content=" + mChatMessage.content +
                ", mMergeBefore=" + mMergeBefore +
                ", mIsConference=" + mIsConference +
                ", mColors=" + mColors +
                '}';
    }
}
