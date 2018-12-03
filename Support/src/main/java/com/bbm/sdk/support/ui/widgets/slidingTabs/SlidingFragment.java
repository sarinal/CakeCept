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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bbm.sdk.support.R;

/**
 * This implements all the functionality need to have a ViewPager with the sliding tabs. Users of this
 * object must implement the addFragments() method, which will populate the container with the Fragments. This
 * works by creating a number of {@link MainSlidingAdapter.AdapterItem} and adding it to the {@link MainSlidingAdapter}
 * that is contained in this class.
 */
public abstract class SlidingFragment extends Fragment implements SlidingTabLayout.SlidingIconClickListener {

    protected ViewPager mViewPager;
    protected SlidingTabLayout mSlidingTabLayout;
    protected MainSlidingAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /**
     * Inflates the {@link View} which will be displayed by this {@link Fragment}, from the app's
     * resources.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sliding_main_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {

        mViewPager = (ViewPager) view.findViewById(R.id.main_viewpager);
        mSlidingTabLayout = (SlidingTabLayout) view.findViewById(R.id.main_tabstrip);

        mAdapter = new MainSlidingAdapter(getChildFragmentManager());
        addFragments();

        mViewPager.setAdapter(mAdapter);

        // The default is 1 but set it to account for difference lib versions.
        mViewPager.setOffscreenPageLimit(1);

        mSlidingTabLayout.setViewPager(mViewPager);
        mSlidingTabLayout.setViewClickListener(this);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mSlidingTabLayout.removeAllViews();
        mViewPager.removeAllViews();

        mSlidingTabLayout.setViewPager(null);
        mSlidingTabLayout.setViewClickListener(null);

        mSlidingTabLayout = null;
        mViewPager = null;
        mAdapter = null;
    }

    /**
     * Helper to set the current Page to be displayed in the View Pager.
     */
    public void setPage(final int position) {
        if(mViewPager == null || mAdapter == null || position < 0) {
            return;
        }
        mViewPager.setCurrentItem(position, true);
        setSplat(position, false);
    }

    public final int getCurrentPage() {
        if(mViewPager == null) {
            return -1;
        }
        return mViewPager.getCurrentItem();
    }

    public final void setSplat(final int position, final boolean splat) {
        if(mSlidingTabLayout != null && mAdapter != null) {
            mSlidingTabLayout.setSplat(position, splat);
        }
    }

    @Override
    public void onSlidingIconClicked(final int position) {
    }

    protected abstract void addFragments();
}
