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

import android.support.v7.widget.RecyclerView;
import android.view.ContextMenu;
import android.view.View;
import android.widget.FrameLayout;

public class RecyclerContextMenuInfoWrapperView extends FrameLayout {
    private RecyclerView.ViewHolder mHolder;
    private final View mView;
    private final int mContextViewIndex;

    public RecyclerContextMenuInfoWrapperView(View view, int contextViewIndex) {
        super(view.getContext());
        setLayoutParams(view.getLayoutParams());
        mView = view;
        addView(mView);
        mContextViewIndex = contextViewIndex;
    }

    public void setHolder(RecyclerView.ViewHolder holder) {
        mHolder = holder;
    }

    @Override
    protected ContextMenu.ContextMenuInfo getContextMenuInfo() {
        return new RecyclerContextMenuInfo(mHolder.getPosition(), mHolder.getItemId(), mHolder.getItemViewType(), mContextViewIndex);
    }

    public static class RecyclerContextMenuInfo implements ContextMenu.ContextMenuInfo {

        public RecyclerContextMenuInfo(int position, long id, int type, int contextViewIndex) {
            this.position = position;
            this.id = id;
            this.type = type;
            this.contextViewIndex = contextViewIndex;
        }

        final public int position;
        final public long id;
        final public int type;
        final public int contextViewIndex;
    }
}
