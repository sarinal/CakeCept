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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bbm.sdk.reactive.TrackedGetter;

/**
 * This is the interface used as a generic recycler view holder. It only exposed necessary
 * methods and hides most of the details.
 */
public interface RecyclerViewHolder<T> {

    /**
     * Create view layout
     * @return the created view layout
     */
    View createView(LayoutInflater inflater, ViewGroup parent);


    /**
     * Update the contents of of the view
     */
    @TrackedGetter
    void updateView(final T t, final int position);

    /**
     * A Callback for subclasses to reset the Holder, so it is ready to be reused.
     */
    void onRecycled();

}
