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


import android.view.ViewGroup;

import com.bbm.sdk.bbmds.ChatMessage;
import com.bbm.sdk.support.ui.widgets.recycler.RecyclerViewHolder;


/**
 * Provides views to a {@link ChatMessageRecyclerViewAdapter}
 */
public interface ChatMessageViewProvider {

    /**
     * Return true if the messages can be merged into a single bubble
     * @param m1 message one
     * @param m2 message two
     * @return true if the messages can be merged
     */
    boolean canMerge(final ChatMessage m1, final ChatMessage m2);

    /**
     * Create a view holder for the provided type
     * @param viewGroup
     * @param viewType the type previously provided by {@link #getItemTypeForMessage(ChatMessage)}
     * @return
     */
    RecyclerViewHolder<DecoratedMessage> onCreateRecyclerViewHolder(ViewGroup viewGroup, int viewType);

    /**
     * Return the type for the given chat message
     * @param item a ChatMessage to be displayed
     * @return a int representing the type of view to be bound by the {@link ChatMessageRecyclerViewAdapter}
     */
    int getItemTypeForMessage(final ChatMessage item);

}
