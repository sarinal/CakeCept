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

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.Chat;
import com.bbm.sdk.bbmds.ChatMessage;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.internal.lists.IncrementalListObserver;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.support.reactive.ChatMessageList;
import com.bbm.sdk.support.util.Logger;
import com.bbm.sdk.support.ui.widgets.recycler.MonitoredRecyclerAdapter;
import com.bbm.sdk.support.ui.widgets.recycler.RecyclerViewHolder;

/**
 * An adapter which displays chat messages.
 */
public class ChatMessageRecyclerViewAdapter extends MonitoredRecyclerAdapter<DecoratedMessage>
        implements ViewTreeObserver.OnGlobalLayoutListener {

    private boolean mIsFirstTimeUpdate;
    private ChatMessageList mMessageList;
    private ChatMessageViewProvider mChatMessageViewProvider;
    private ChatBubbleColorProvider mColorProvider;
    private boolean mIsConference;
    private String mChatId;

    /**
     * Observer the {@link ChatMessageList} and informs the adapter of changes to the chat content
     */
    @SuppressWarnings({"FieldCanBeLocal"})
    private final IncrementalListObserver mDataSetListener = new IncrementalListObserver() {
        @Override
        public void onItemsInserted(int fromPosition, int itemCount) {

            // If the view is calculating the layout, don't do any operation right now,
            // it will cause the recycling view to throw an illegal state exception.
            if (mRecyclerView.isComputingLayout()) {
                Logger.d("onItemsInserted is computing layout");
                return;
            }

            notifyItemRangeInserted(fromPosition, itemCount);
            final int lastPosition = getItemCount() - 1;

            RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();
            if (shouldAutoScrollOnNewItem() &&
                    layoutManager instanceof LinearLayoutManager && mRecyclerView.isLaidOut() &&
                    ((LinearLayoutManager) layoutManager).findLastVisibleItemPosition() < fromPosition - 1) {
                mRecyclerView.smoothScrollToPosition(lastPosition);
            }

            Logger.d("onItemsInserted(fromPosition " + fromPosition + ", itemCount " + itemCount + ")");
        }

        @Override
        public void onItemsRemoved(int fromPosition, int itemCount) {
            notifyItemRangeRemoved(fromPosition, itemCount);
            Logger.d("onItemsRemoved(fromPosition " + fromPosition + ", itemCount " + itemCount + ")");
        }

        @Override
        public void onItemsChanged(int fromPosition, int itemCount) {
            // The RecyclerView will just update the changes on specific range,
            // which could miss some updates when it is paused.
            // So update the all views at first time start.
            if (mIsFirstTimeUpdate) {
                onDataSetChanged();
                mIsFirstTimeUpdate = false;
                return;
            }
            notifyItemRangeChanged(fromPosition, itemCount);
            Logger.v("onItemsChanged(fromPosition " + fromPosition + ", itemCount " + itemCount + ")");
        }

        @Override
        public void onDataSetChanged() {
            notifyDataSetChanged();
            Logger.d("onDataSetChanged()");
        }
    };

    /**
     * Create a new ChatMessageRecyclerViewAdapter
     * @param context android context
     * @param recyclerView the recycler view that this adapter is attached to
     * @param chatId the id of the chat whose messages should be loaded
     * @param chatMessageViewProvider provider for message views
     * @param colorProvider provider for chat bubble colors
     */
    public ChatMessageRecyclerViewAdapter(@NonNull final Context context,
                                          @NonNull final RecyclerView recyclerView,
                                          @NonNull final String chatId,
                                          @NonNull final ChatMessageViewProvider chatMessageViewProvider,
                                          @NonNull final ChatBubbleColorProvider colorProvider) {
        super(context, recyclerView);

        mChatId = chatId;

        mMessageList = new ChatMessageList(chatId);
        mMessageList.addIncrementalListObserver(mDataSetListener);

        setHasStableIds(true);
        setAutoScrollOnNewItem(true);

        mChatMessageViewProvider = chatMessageViewProvider;
        mColorProvider = colorProvider;
        mRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(this);
        //Remove our GlobalLayoutListener when then user first touches the recycler view
        mRecyclerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                mRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(ChatMessageRecyclerViewAdapter.this);
                mRecyclerView.setOnTouchListener(null);
                return false;
            }
        });
    }

    /**
     * Start loading messages from the chat
     */
    public void start() {

        mIsFirstTimeUpdate = true;

        //This monitor determines if the chat is a one to one chat or a multi-person chat
        SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
            @Override
            public boolean run() {
                final Chat chat = BBMEnterprise.getInstance().getBbmdsProtocol().getChat(mChatId).get();
                if (chat.exists == Existence.MAYBE) {
                    return false;
                }
                if (chat.exists == Existence.YES) {
                    if (mIsConference == chat.hasFlag(Chat.Flags.OneToOne)) {
                        mIsConference = !chat.hasFlag(Chat.Flags.OneToOne);
                        notifyDataSetChanged();
                    }
                }
                return true;
            }
        });

        if (mMessageList != null) {
            mMessageList.start();
        }
    }

    /**
     * Stop loading or updating messages from the chat
     */
    public void pause() {

        mIsFirstTimeUpdate = false;

        if (mMessageList != null) {
            mMessageList.stop();
        }
    }

    /**
     * Get the id for the view (chat bubble) at the provided position
     * @param position the position.
     * @return id provided by the {@link ChatMessageList}
     */
    @Override
    public long getItemId(final int position) {
        return mMessageList.getId(position);
    }

    /**
     * Get the number of messages to be displayed
     * @return the number of messages provided by the {@link ChatMessageList}
     */
    @Override
    public int getItemCount() {
        return mMessageList.size();
    }

    /**
     * Provides a {@link RecyclerViewHolder} to be displayed.
     * @param viewGroup the viewgroup in which the viewholder will be added
     * @param viewType the type of viewholder required
     * @return a {@link RecyclerViewHolder} matching the view type
     */
    @Override
    public RecyclerViewHolder<DecoratedMessage> onCreateRecyclerViewHolder(ViewGroup viewGroup, int viewType) {
        return mChatMessageViewProvider.onCreateRecyclerViewHolder(viewGroup, viewType);
    }

    /**
     * Provide the type of view for the ChatMessage at position
     * @param position the position.
     * @return integer representing the viewtype for the message at the position
     */
    @Override
    public int getItemViewType(final int position) {
        if (position >= 0 && mMessageList.isInitialized()) {
            ChatMessage message = mMessageList.get(position);
            return mChatMessageViewProvider.getItemTypeForMessage(message);
        }

        return 0;
    }

    /**
     * Provide the DecoratedMessage to be used
     * @param position the position
     * @return a {@link DecoratedMessage} containing the message and metadata for displaying the chat bubble
     */
    public DecoratedMessage getItem(final int position) {
        ChatMessage message = mMessageList.get(position);
        if (message.exists == Existence.YES) {
            // Merge with before message?
            boolean mergeBefore = false;
            if (position > 0) {
                final ChatMessage beforeMessage = mMessageList.get(position - 1);
                if (beforeMessage.exists == Existence.YES) {
                    mergeBefore = mChatMessageViewProvider.canMerge(message, beforeMessage);
                }
            }
            final ChatBubbleColors color;
            if (!message.hasFlag(ChatMessage.Flags.Incoming)) {
                color = mColorProvider.getOutgoingMessageColors(message);
            } else {
                color = mIsConference ?
                        mColorProvider.getMpcMessageColors(message) :
                        mColorProvider.getOneToOneIncomingMessageColors(message);
            }

            return new DecoratedMessage(message, mergeBefore, mIsConference, color);
        }
        return new DecoratedMessage();
    }

    /**
     * Override onGlobalLayout to force our scroll position to the bottom
     */
    @Override
    public void onGlobalLayout() {
        final int itemCount = getItemCount();
        if (itemCount > 0) {
            //Force scroll to the end of the list
            mRecyclerView.scrollToPosition(itemCount - 1);
        }
    }
}

