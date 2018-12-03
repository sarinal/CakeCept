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

package com.bbm.sdk.support.ui.widgets.recycler;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;


/**
 * The base adapter that provides Monitoring functionality. It creates a MonitoredHolder for each
 * row, which fulfills the Observing needs of any Observable touched during updateView. It automatically
 * updates the corresponding row when the DataModel object associated with it changes.
 *
 * Subclass need to implement {@link #onCreateRecyclerViewHolder(ViewGroup, int)} method to return a
 * {@link RecyclerViewHolder<T>} instance. The RecyclerViewHolder only exposes necessary methods the
 * client class needs to worry about. All monitoring and updating logic is done by MonitoredRecyclerAdapter.
 *
 */
public abstract class MonitoredRecyclerAdapter<T> extends RecyclerView.Adapter<MonitoredHolder<T>> {
    protected final Context mContext;
    protected final LayoutInflater mInflater;
    protected final RecyclerView mRecyclerView;
    private boolean mAutoScrollOnNewItem = false;

    public MonitoredRecyclerAdapter(final Context context,
                                    final RecyclerView recyclerView) {
        mContext = context;
        mRecyclerView = recyclerView;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        // Stable ids are important for incremental updates to be smooth, we assume all
        // of our lists have stable ids
        setHasStableIds(true);
    }

    public abstract T getItem(final int position);

    /**
     * A unique id for item at position. Note that this should generally identify the object and not
     * change when some of the object updates. Therefore, DO NOT use hashcode for this.
     * @param position the position.
     * @return the unique id at position
     */
    @Override
    public abstract long getItemId(final int position);

    @Override
    public abstract int getItemCount();

    public abstract RecyclerViewHolder<T> onCreateRecyclerViewHolder(ViewGroup viewGroup, int viewType);

    public final MonitoredHolder<T> onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        final RecyclerViewHolder<T> mvh = onCreateRecyclerViewHolder(viewGroup, viewType);
        return new MonitoredHolder<>(mContext, mRecyclerView, this, mvh, mInflater);
    }

    @Override
    public void onBindViewHolder(MonitoredHolder<T> vh, int position) {
        vh.updateView(getItem(position));
    }

    @Override
    public void onViewRecycled(MonitoredHolder<T> holder) {
        super.onViewRecycled(holder);
        holder.onRecycled();
    }

    /**
     * returns the corresponding viewType for item at position.
     * RecyclerView automatically computes the number of different types and create separate
     * recycling pools for each type.
     * @param position the position.
     * @return the value of type, usually from 0 to n
     */
    @Override
    public abstract int getItemViewType(final int position);

    /**
     * Should the recyclerView automatically scroll to bottom when a new item is available AND
     * it is currently sitting at its bottom. Live updating list could find this very useful. Like
     * when a new message comes in, while you are looking at the previous latest message.
     * @param autoScroll should it auto scroll
     */
    public void setAutoScrollOnNewItem(boolean autoScroll) {
        mAutoScrollOnNewItem = autoScroll;
    }

    public boolean shouldAutoScrollOnNewItem() {
        return mAutoScrollOnNewItem;
    }

}

