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

package com.bbm.sdk.support.ui.widgets.chats.holders;


import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bbm.sdk.bbmds.ChatMessage;
import com.bbm.sdk.support.R;
import com.bbm.sdk.support.util.Logger;
import com.bbm.sdk.support.ui.widgets.chats.ChatBubbleColors;
import com.bbm.sdk.support.ui.widgets.chats.DecoratedMessage;
import com.bbm.sdk.support.ui.widgets.chats.MessagePriority;
import com.bbm.sdk.support.ui.widgets.chats.MessageStatusIcons;
import com.bbm.sdk.support.ui.widgets.util.DateUtil;


/**
 * A generic base for a chat bubble holder.
 * A message specific layout can be included using the {@link #setContentSpecificView(LayoutInflater, int)}
 */
public abstract class BaseBubbleHolder {

    private final View mView;
    private final ViewGroup mContentContainer;
    private final View mMessageHeader;
    private final TextView mDateTextView;
    private ImageView mMessageStatus;
    private ImageView mPriorityState;
    private View mBackgroundView;

    public BaseBubbleHolder(final LayoutInflater layoutInflater, final ViewGroup parent, int layoutResId) {
        mView = layoutInflater.inflate(layoutResId, parent, false);
        mDateTextView = (TextView) mView.findViewById(R.id.message_date);
        mContentContainer = (ViewGroup) mView.findViewById(R.id.content_specific_container);
        mBackgroundView = mView.findViewById(R.id.chat_bubble_background_container);
        mMessageHeader = mView.findViewById(R.id.message_header);
        mPriorityState = (ImageView) mView.findViewById(R.id.priority);
    }

    protected Context getContext() {
        return mView.getContext();
    }

    /**
     * Add message specific content
     *
     * @param layoutInflater to be used to inflate the layout
     * @param contentResId   the content specific view id to inflate and add
     * @return the layout holding the specific content
     */
    public View setContentSpecificView(final LayoutInflater layoutInflater, int contentResId) {
        View mContentView = layoutInflater.inflate(contentResId, mContentContainer, false);
        mContentContainer.addView(mContentView);
        mMessageStatus = (ImageView) mContentView.findViewById(R.id.message_status);
        return mContentView;
    }

    /**
     * Update the generic message bubble components including:
     * - date
     * - status icon
     * - priority icon
     *
     * @param decoratedMessage the message to update
     */
    public void updateGeneric(final DecoratedMessage decoratedMessage) {
        if (decoratedMessage.shouldMergeBefore()) {
            mMessageHeader.setVisibility(View.GONE);
        } else {
            // Message date
            mMessageHeader.setVisibility(View.VISIBLE);
            mDateTextView.setText(DateUtil.observableChatBubbleHeaderTimestamp(mView.getContext(), decoratedMessage.getTimestamp()));
        }

        ChatMessage chatMessage = decoratedMessage.getChatMessage();

        if (chatMessage != null) {
            if (mMessageStatus != null) {
                // Message status
                mMessageStatus.setImageResource(MessageStatusIcons.getMessageStatusIconId(chatMessage));
            }
            Logger.v("Update view for message: " + chatMessage.chatId + ", status: " + chatMessage.state + BaseBubbleHolder.class);

            MessagePriority.Level priorityLevel = MessagePriority.Level.Normal;
            if (chatMessage.data != null) {
                priorityLevel = MessagePriority.getPriority(chatMessage.data);
            }
            showPriority(priorityLevel);

            if (decoratedMessage.getColors() != null) {
                mBackgroundView.setBackgroundResource(decoratedMessage.getColors().backgroundId);
            }
        }
    }

    /**
     * The root view for the bubble
     *
     * @return root view
     */
    public View getRootView() {
        return mView;
    }


    /**
     * Update the textstyle for the textview
     *
     * @param textView view to update
     * @param colors   colors to be used
     */
    public void updateTextViewStyle(@NonNull final TextView textView, @NonNull final ChatBubbleColors colors) {
        textView.setLinkTextColor(getContext().getResources().getColor(colors.highlightedColor));
        textView.setTextColor(getContext().getResources().getColor(colors.normalTextColor));
    }

    /**
     * Forces the content container to consume all of the width
     */
    public void useFullWidth() {
        mContentContainer.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
    }

    /**
     * Show the provided priority
     */
    private void showPriority(MessagePriority.Level priorityLevel) {
        if (mPriorityState != null) {
            // Show when the non retracted message is either a High Priority or a Ping
            if (priorityLevel == MessagePriority.Level.High || priorityLevel == MessagePriority.Level.Low) {
                if (priorityLevel == MessagePriority.Level.High) {
                    mPriorityState.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.high_priority_message));
                } else {
                    mPriorityState.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.low_priority_message));
                }

                mPriorityState.setBackgroundResource(R.drawable.priority_border_left);
                mPriorityState.setImageResource(R.drawable.priority);
                mPriorityState.setVisibility(View.VISIBLE);
            } else {
                mPriorityState.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Call when the specific child holder has been recycled
     */
    public void onRecycled() {
        //clear date and status icon (if exists)
        mDateTextView.setText(null);
        if (mMessageStatus != null) {
            mMessageStatus.setImageDrawable(null);
        }
        if (mPriorityState != null) {
            mPriorityState.setImageDrawable(null);
            mPriorityState.setBackgroundResource(0);
        }
    }
}
