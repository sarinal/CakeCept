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

package com.bbm.sdk.support.reactive;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.Chat;
import com.bbm.sdk.bbmds.ChatMessage;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.internal.WeakReferenceSet;
import com.bbm.sdk.bbmds.internal.lists.BaseObservable;
import com.bbm.sdk.bbmds.internal.lists.IncrementalListObservable;
import com.bbm.sdk.bbmds.internal.lists.IncrementalListObserver;
import com.bbm.sdk.reactive.ObservableMonitor;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.reactive.TrackedGetter;
import com.bbm.sdk.support.util.Logger;
import com.google.common.collect.MapMaker;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A observing list that can be used with RecyclerView.Adapter. The list will lazily load elements whenever
 * {@link ChatMessageList#get(int)} is called. In addition to the element
 * at the requested index, the list will eagerly load a number of neighboring elements to facilitate smooth scrolling.
 */
public class ChatMessageList extends BaseObservable implements IncrementalListObservable {

    // prefetch this amount of items so the scrolling can be smooth
    private static final int PREFETCH_DISTANCE = 30;
    private final WeakReferenceSet<IncrementalListObserver> mIncrementalListObservers = new WeakReferenceSet<>();

    private final String mChatId;
    private boolean mIsInitialized = false;
    private int mCount;
    private long mFirst;
    private final Map<Long, ObservableValue<ChatMessage>> mDataMap = new MapMaker().concurrencyLevel(1).makeMap();

    // Observer set, used to fire individual item update events itemsChanged(index, 1)
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final Set<MapEntryObserver> mMapEntryObservers = new HashSet<>();

    private final class MapEntryObserver implements Observer {
        private final long id;

        private MapEntryObserver(long id) {
            this.id = id;
        }

        @Override
        public void changed() {
            itemsChanged(getIndex(id), 1);
        }

        @Override
        public int hashCode() {
            return (int) (id ^ (id >>> 32));
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof ChatMessageList.MapEntryObserver) &&
                    (((ChatMessageList.MapEntryObserver) o).id == id);
        }
    }

    // Observing the chat list to handle adding or removing new items.
    private final ObservableMonitor mChatMonitor = new ObservableMonitor() {
        @Override
        protected void run() {
            if (TextUtils.isEmpty(mChatId)) {
                return;
            }

            final Chat chat = BBMEnterprise.getInstance().getBbmdsProtocol().getChat(mChatId).get();
            if (chat.exists == Existence.YES) {
                final long lastMessage = chat.lastMessage;

                // safe to cast to int, num of messages will fit into 32 bit
                final int numMessages = (int) chat.numMessages;
                final long firstMessage = Math.max(0, lastMessage - numMessages) + 1;
                setData(firstMessage, numMessages);
                mIsInitialized = true;
            }
        }
    };

    /**
     * Constructor
     *
     * @param chatId The chat identifier from {@link Chat#chatId} Must be non-null and
     *               non empty.
     * @throws IllegalArgumentException if the chatId is empty.
     */
    public ChatMessageList(@NonNull final String chatId) {
        if (TextUtils.isEmpty(chatId)) {
            throw new IllegalArgumentException("The provided chat Id is empty.");
        }

        mChatId = chatId;
    }

    /**
     * Returns if the list has been initialized and has obtained data about the chat.
     * This should be checked before accessing any list items as it may return empty
     * or null data.
     *
     * @return true if the list is initialized
     */
    public boolean isInitialized() {
        return mIsInitialized;
    }

    /**
     * Starts the observations needed to support the list. This should
     * be called in a activity onResume method.
     */
    public void start() {
        if (!mChatMonitor.isActive()) {
            mChatMonitor.activate();
        }
    }

    /**
     * Stops the automatic observations. This should be called in a
     * activity onPause method.
     */
    public void stop() {
        mChatMonitor.dispose();
    }

    /**
     * Sets the necessary parameters for the List to load elements lazily.
     *
     * @param first the primary key of the first element of the list.
     * @param count the number of elements in the list.
     */
    private void setData(long first, int count) {
        if (mFirst != first) {
            mFirst = first;
            mCount = count;
            dataSetChanged();
        } else if (mCount < count) {
            final int newItemStartPosition = mCount;
            final int newItemCount = count - mCount;
            mCount = count;
            itemsInserted(newItemStartPosition, newItemCount);
            Logger.d("onItemsInserted(fromPosition " + newItemStartPosition + ", itemCount " + newItemCount + ")");
        } else if (mCount > count) {
            final int removedItemCount = mCount - count;
            mCount = count;
            itemsRemoved(count, removedItemCount);
            Logger.d("onItemsRemoved(fromPosition " + count + ", itemCount " + removedItemCount + ")");
        }
    }

    /**
     * The mapping required to transform a primary key to a list element.
     *
     * @param id the primary key of the list element.
     * @return an {@link ObservableValue} of the list element.
     */
    private ObservableValue<ChatMessage> idToListItem(long id) {
        final ChatMessage.ChatMessageKey lookupKey = new ChatMessage.ChatMessageKey(mChatId, id);
        return BBMEnterprise.getInstance().getBbmdsProtocol().getChatMessage(lookupKey);
    }

    private int getIndex(long id) {
        return (int) (id - mFirst);
    }

    /**
     * Returns the primary key of the element at the specified index.
     *
     * @param index the index of the element to return.
     * @return the primary key of the element at the specified index.
     */
    public long getId(int index) {
        return mFirst + index;
    }

    /**
     * Returns the number of elements in the list.
     *
     * @return the number of elements in the list.
     */
    public int size() {
        return mCount;
    }

    /**
     * Returns the element at the specified index.
     *
     * @param index the index of the element to return.
     * @return the element at the specified index.
     * @trackedgetter This method is a {@link com.bbm.sdk.reactive.TrackedGetter}
     */
    @TrackedGetter
    public ChatMessage get(int index) {
        // fetch K items before index if not already cached
        final int checkPointBeforeIndex = Math.max(0, index - PREFETCH_DISTANCE);
        for (int i = checkPointBeforeIndex; i <= index; i++) {
            final long id = getId(i);
            if (mDataMap.containsKey(id)) {
                continue;
            }
            ObservableValue<ChatMessage> item = idToListItem(id);
            MapEntryObserver mapEntryObserver = new MapEntryObserver(id);
            item.addObserver(mapEntryObserver);
            mMapEntryObservers.add(mapEntryObserver);
            mDataMap.put(id, item);
        }
        // fetch K items after index if not already cached
        final int checkPointAfterIndex = Math.min(mCount - 1, index + PREFETCH_DISTANCE);
        for (int i = checkPointAfterIndex; i >= index; i--) {
            final long id = getId(i);
            if (mDataMap.containsKey(id)) {
                continue;
            }
            ObservableValue<ChatMessage> item = idToListItem(id);
            MapEntryObserver mapEntryObserver = new MapEntryObserver(id);
            item.addObserver(mapEntryObserver);
            mMapEntryObservers.add(mapEntryObserver);
            mDataMap.put(id, item);
        }
        final long itemId = getId(index);
        return mDataMap.get(itemId).get();
    }

    @Override
    public void itemsInserted(int fromPosition, int itemCount) {
        final List<IncrementalListObserver> copy = new LinkedList<>();
        for (final IncrementalListObserver listListener : mIncrementalListObservers) {
            copy.add(listListener);
        }
        for (final IncrementalListObserver listListener : copy) {
            listListener.onItemsInserted(fromPosition, itemCount);
        }
        notifyObservers();
    }

    @Override
    public void itemsRemoved(int fromPosition, int itemCount) {
        final List<IncrementalListObserver> copy = new LinkedList<>();
        for (final IncrementalListObserver listListener : mIncrementalListObservers) {
            copy.add(listListener);
        }
        for (final IncrementalListObserver listListener : copy) {
            listListener.onItemsRemoved(fromPosition, itemCount);
        }
        notifyObservers();
    }

    @Override
    public void itemsChanged(int fromPosition, int itemCount) {
        final List<IncrementalListObserver> copy = new LinkedList<>();
        for (final IncrementalListObserver listListener : mIncrementalListObservers) {
            copy.add(listListener);
        }
        for (final IncrementalListObserver listListener : copy) {
            listListener.onItemsChanged(fromPosition, itemCount);
        }
        notifyObservers();
    }

    @Override
    public void dataSetChanged() {
        final List<IncrementalListObserver> copy = new LinkedList<>();
        for (final IncrementalListObserver listListener : mIncrementalListObservers) {
            copy.add(listListener);
        }
        for (final IncrementalListObserver listListener : copy) {
            listListener.onDataSetChanged();
        }
        notifyObservers();
    }

    @Override
    public void addIncrementalListObserver(IncrementalListObserver listObserver) {
        mIncrementalListObservers.add(listObserver);
    }

    @Override
    public void removeIncrementalListObserver(IncrementalListObserver listObserver) {
        mIncrementalListObservers.remove(listObserver);
    }
}
