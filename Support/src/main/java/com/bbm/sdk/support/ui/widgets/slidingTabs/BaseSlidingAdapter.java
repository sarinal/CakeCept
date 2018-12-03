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

package com.bbm.sdk.support.ui.widgets.slidingTabs;

import android.support.annotation.DrawableRes;
import android.support.annotation.StringRes;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

/**
 * The basic interface used to support the sliding fragments.
 */
public abstract class BaseSlidingAdapter extends FragmentStatePagerAdapter {

    public BaseSlidingAdapter(FragmentManager fm) {
        super(fm);
    }

    /**
     * Returns the drawable resource Id of the item at the given position
     * @param position The position of the drawable resource Id requested
     * @return The drawable resource ID at the requested position
     */
    public abstract @DrawableRes int getTabIconResource(int position);

    /**
     * Returns the string resource Id to describe the item at the given position
     * @param position The position of the string resource Id requested
     * @return The string resource ID at the requested position
     */
    public abstract @StringRes int getTabContentDescriptionResource(int position);

    /**
     * Returns the Class identifier at the given position
     * @param position The position of the Class requested
     * @return The Class object at the given position
     */
    public abstract Class getType(final int position);
}
