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
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import com.bbm.sdk.support.util.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

/**
 * Implementation of BaseSlidingAdapter that used to contain a ArrayList of
 * AdapterItem
 */
public class MainSlidingAdapter extends BaseSlidingAdapter {

    private final ArrayList<AdapterItem> mItems = new ArrayList<>();

    public MainSlidingAdapter(FragmentManager manager) {
        super(manager);
    }

    /**
     * Adds an item to the sliding adapter. Callers must ensure the item is
     * not null. Also this will not check for duplicate items.
     *
     * @param item A Non-null AdapterItem object.
     */
    public void addItem(@NonNull final AdapterItem item) {
        mItems.add(item);
    }

    @Override
    public Fragment getItem(int position) {
        if (position < 0 || position >= mItems.size()) {
            return null;
        }

        Fragment fragment = null;
        AdapterItem item = mItems.get(position);

        try {
            Constructor<? extends Fragment> ctor = item.mClazz.getConstructor();
            fragment = ctor.newInstance();
        } catch (NoSuchMethodException e) {
            Logger.e(e, "Cannot find class");
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            Logger.e(e, "Unable to create the object from the class item");
        }

        return fragment;
    }

    public @DrawableRes
    int getTabIconResource(int position) {
        if (position < 0 || position >= mItems.size()) {
            return 0;
        }

        AdapterItem item = mItems.get(position);
        return item.mDrawableRes;
    }

    public @StringRes
    int getTabContentDescriptionResource(int position) {
        if (position < 0 || position >= mItems.size()) {
            return 0;
        }

        AdapterItem item = mItems.get(position);
        return item.mStringRes;
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    /**
     * Helper to match a position to the expected TabFragment.
     */
    @Override
    public Class getType(final int position) {
        if (position < 0 || position >= mItems.size()) {
            return null;
        }

        AdapterItem item = mItems.get(position);
        return item.mClazz;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        if (position < 0 || position >= mItems.size()) {
            return "";
        }

        AdapterItem item = mItems.get(position);
        return item.mDisplayedText;
    }

    /**
     * The AdapterItem data class used to associate drawable & string resourced to the associated
     * Class file that would use them.
     */
    public static class AdapterItem {
        final public int mType;
        final public @DrawableRes int mDrawableRes;
        final public @StringRes int mStringRes;
        final public String mDisplayedText;
        final public Class mClazz;

        /**
         * Constructor
         * @param type The type of the item.
         * @param drawableRes The identifier to the drawable resource to use
         * @param stringRes The identifier to the string resource to use
         * @param displayedText The display test to use for the page Title.
         * @param clazz The child class which extends v4 fragment class to use.
         */
        public AdapterItem(int type, @DrawableRes int drawableRes, @StringRes int stringRes,
                           @NonNull final String displayedText, @NonNull final Class<? extends Fragment> clazz) {
            mType = type;
            mDrawableRes = drawableRes;
            mStringRes = stringRes;
            mDisplayedText = displayedText;
            mClazz = clazz;
        }
    }
}
