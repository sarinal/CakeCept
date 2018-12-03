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

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bbm.sdk.reactive.ObservableMonitor;
import com.bbm.sdk.support.util.Logger;

import java.util.List;

/**
 * Tightly integrated with {@link MonitoredRecyclerAdapter} and {@link RecyclerViewHolder}.
 * It provides inline monitors for each visible row and handles live-update of each row.
 * @param <T> the DataModel type
 */
public class MonitoredHolder<T> extends RecyclerView.ViewHolder {
    final Handler mHandler = new Handler();
    final Context mContext;
    final RecyclerViewHolder<T> mRecyclerViewHolder;
    final RecyclerView mRecyclerView;

    final MonitoredRecyclerAdapter<T> mMonitoredRecyclerViewAdapter;

    private T mItem;

    final ObservableMonitor mObservableMonitor = new ObservableMonitor() {
        @Override
        protected void run() {
            final int position = getAdapterPosition();
            final int holderType = getItemViewType();
            final int itemType = mMonitoredRecyclerViewAdapter.getItemViewType(position);
            Logger.v("Updateview @posn: "+position+" hType: "+holderType+" iType: " +itemType);
            if (itemType == holderType) {
                mRecyclerViewHolder.updateView(mItem, position);
            } else {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final int adapterPosition = getAdapterPosition();

                            if (adapterPosition == -1) {
                                Logger.e("Updating view with invalid position: -1");
                                return;
                            }

                            mMonitoredRecyclerViewAdapter.notifyItemRangeChanged(adapterPosition, 1);

                            RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();
                            if (mMonitoredRecyclerViewAdapter.shouldAutoScrollOnNewItem() &&
                                    layoutManager instanceof LinearLayoutManager &&
                                    ((LinearLayoutManager) layoutManager).findLastVisibleItemPosition() == position) {
                                mRecyclerView.smoothScrollToPosition(position);
                            }

                        } catch (Exception e) {
                            Logger.e(e);
                        }
                    }
                });
            }
        }
    };

    public MonitoredHolder(Context context,
                           RecyclerView recyclerView,
                           MonitoredRecyclerAdapter<T> monitoredRecyclerViewAdapter,
                           RecyclerViewHolder<T> recyclerViewHolder,
                           LayoutInflater inflater) {
        super(recyclerViewHolder.createView(inflater, recyclerView));
        mContext = context;
        mRecyclerViewHolder = recyclerViewHolder;
        mRecyclerView = recyclerView;
        mMonitoredRecyclerViewAdapter = monitoredRecyclerViewAdapter;

        if (mContext != null && mContext instanceof Activity && recyclerViewHolder instanceof ContextMenuAwareHolder) {
            final Activity activity = (Activity) mContext;
            final List<View> views = ((ContextMenuAwareHolder) recyclerViewHolder).getContextMenuAwareView();
            if (views == null) {
                return;
            }
            for(int i=0; i< views.size(); i++) {
                final View view = views.get(i);
                ViewGroup viewGroup = (ViewGroup) view.getParent();
                if (viewGroup != null) {
                    viewGroup.removeView(view);
                }
                RecyclerContextMenuInfoWrapperView wrapperView = new RecyclerContextMenuInfoWrapperView(view, i);
                wrapperView.setHolder(this);
                if (viewGroup != null) {
                    viewGroup.addView(wrapperView);
                }
                activity.registerForContextMenu(wrapperView);
            }
        }
    }

    public void updateView(T item) {
        mItem = item;
        mObservableMonitor.activate();
    }

    /**
     * This callback is for resetting the Holder, prepare for the next reuse.
     */
    public void onRecycled() {
        mObservableMonitor.dispose();
        mRecyclerViewHolder.onRecycled();
    }

    public RecyclerViewHolder<T> getRecyclerViewHolder() {
        return mRecyclerViewHolder;
    }

}
