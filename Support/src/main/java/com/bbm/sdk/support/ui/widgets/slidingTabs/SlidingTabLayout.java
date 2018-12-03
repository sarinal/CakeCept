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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.IntDef;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bbm.sdk.support.R;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.SOURCE;


/**
 * The layout object used for the SlidingFragment.
 */
public class SlidingTabLayout extends HorizontalScrollView {

    public static final int ICON_ONLY = 0;
    public static final int TITLE_ONLY = 1;
    public static final int ICON_AND_DISPLAY_TITLE = 2;
    @Retention(SOURCE)
    @IntDef({ICON_ONLY, TITLE_ONLY, ICON_AND_DISPLAY_TITLE})
    public @interface DisplayStyle{}

    private @DisplayStyle int mDisplayStyle = ICON_ONLY;

    /**
     * Interface to handle clicking on the current selected tab page icon.
     */
    public interface SlidingIconClickListener {
        void onSlidingIconClicked(final int position);
    }

    private SlidingIconClickListener mSlidingIconClickListener;

    private static final int BASE_PADDING_HORIZONTAL_SLIDING_ICON = 16;
    private static final int BASE_PADDING_VERTICAL_SLIDING_ICON = 10;

    private ViewPager mViewPager;
    private StripLayout mLinearStrip;
    private int mTitleOffset;

    private InternalViewPagerListener mPageListener;

    public SlidingTabLayout(Context context) {
        super(context);
        initalize(context);
    }

    public SlidingTabLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initalize(context);
    }

    public SlidingTabLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initalize(context);
    }

    private void initalize(final Context context) {
        // Disable the Scroll Bar
        setHorizontalScrollBarEnabled(false);

        // Make sure that the Tab Strips fills this View
        setFillViewport(true);

        mTitleOffset = (int) (BASE_PADDING_HORIZONTAL_SLIDING_ICON * getResources().getDisplayMetrics().density);

        mLinearStrip = new StripLayout(context);
        mLinearStrip.setGravity(Gravity.CENTER);
        addView(mLinearStrip, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    public void setViewPager(final ViewPager pager) {
        if(mLinearStrip != null) {
            mLinearStrip.removeAllViews();
        }

        if(mViewPager != null) {
            mViewPager.removeOnPageChangeListener(mPageListener);
        }

        mViewPager = pager;
        if (mViewPager != null) {
            mPageListener = new InternalViewPagerListener();
            mViewPager.addOnPageChangeListener(mPageListener);
            populateTabStrip();
        }
    }

    /**
     * Set the listener that handles user clicking on current pages tab icon.
     */
    public void setViewClickListener(final SlidingIconClickListener listener) {
        mSlidingIconClickListener = listener;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (mViewPager != null) {
            mViewPager.post(new Runnable() {
                @Override
                public void run() {
                    if(mViewPager != null) {
                        selectItem(mViewPager.getCurrentItem(), 0, ViewPager.SCROLL_STATE_IDLE);
                    }
                }
            });
        }
    }

    private ImageView createTabIcon(final Context context, final int resId, final int contentDescriptionResId) {
        ImageView imageView = new StateImageView(context);
        imageView.setImageResource(resId);

        // dynamically get the padding needed
        int hPadding = (int) (BASE_PADDING_HORIZONTAL_SLIDING_ICON * getResources().getDisplayMetrics().density);
        int vPadding = (int) (BASE_PADDING_VERTICAL_SLIDING_ICON * getResources().getDisplayMetrics().density);

        // Set the layout params to weight so we this can be evenly distributed in the layout.
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.width = 0;
        params.weight = 1;

        imageView.setLayoutParams(params);
        imageView.setPadding(hPadding, vPadding, hPadding, vPadding);

        if (contentDescriptionResId > 0) {
            imageView.setContentDescription(context.getString(contentDescriptionResId));
        }

        return imageView;
    }

    private View createTabWithDisplayNameOnly(final Context context, final CharSequence title) {
        //Create view group which will keep the tab icon and text
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        params.weight = 1;
        layout.setLayoutParams(params);

        // Create tab text view
        TextView tabText = new TextView(context);
        tabText.setText(title);
        tabText.setGravity(Gravity.CENTER_HORIZONTAL);

        //Add views to the layout
        layout.addView(tabText);

        // dynamically get the padding needed
        int hPadding = (int) (BASE_PADDING_HORIZONTAL_SLIDING_ICON * getResources().getDisplayMetrics().density);
        int vPadding = (int) (BASE_PADDING_VERTICAL_SLIDING_ICON * getResources().getDisplayMetrics().density);
        layout.setPadding(hPadding, vPadding, hPadding, vPadding);

        return layout;
    }

    private View createTabIconWithName(final Context context, final int resId, final CharSequence title) {
        //Create view group which will keep the tab icon and text
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        params.weight = 1;
        layout.setLayoutParams(params);

        //Create tab icon view
        ImageView imageView = new StateImageView(context);

        imageView.setImageResource(resId);
        LinearLayout.LayoutParams imageViewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        imageViewParams.gravity = Gravity.CENTER_HORIZONTAL;
        imageView.setLayoutParams(imageViewParams);

        // Create tab text view
        TextView tabText = new TextView(context);
        tabText.setText(title);
        tabText.setGravity(Gravity.CENTER_HORIZONTAL);

        //Add views to the layout
        layout.addView(imageView);
        layout.addView(tabText);

        // dynamically get the padding needed
        int hPadding = (int) (BASE_PADDING_HORIZONTAL_SLIDING_ICON * getResources().getDisplayMetrics().density);
        int vPadding = (int) (BASE_PADDING_VERTICAL_SLIDING_ICON * getResources().getDisplayMetrics().density);
        layout.setPadding(hPadding, vPadding, hPadding, vPadding);

        return layout;
    }

    /**
     * Main call to add items to the strip, right now all are icons. The line seen below is
     * dynamically drawn, so don't worry about it here.
     */
    private void populateTabStrip() {
        final PagerAdapter adapter = mViewPager.getAdapter();
        final OnClickListener tabClickListener = new TabClickListener();

        if (adapter instanceof BaseSlidingAdapter) {
            BaseSlidingAdapter bbmAdapter = (BaseSlidingAdapter) adapter;
            for (int i = 0; i < adapter.getCount(); i++) {
                View tabView;
                switch (mDisplayStyle) {
                    case ICON_AND_DISPLAY_TITLE:
                        tabView = createTabIconWithName(getContext(), bbmAdapter.getTabIconResource(i), bbmAdapter.getPageTitle(i));
                        break;
                    case TITLE_ONLY:
                        tabView = createTabWithDisplayNameOnly(getContext(), bbmAdapter.getPageTitle(i));
                        break;
                    case ICON_ONLY:
                    default:
                        tabView = createTabIcon(getContext(), bbmAdapter.getTabIconResource(i), bbmAdapter.getTabContentDescriptionResource(i));
                        break;
                }
                if (tabView != null) {
                    tabView.setOnClickListener(tabClickListener);
                    mLinearStrip.addView(tabView);
                }
            }
        }
    }

    public final void setSplat(final int position, final boolean splat) {
        if (mLinearStrip == null) {
            return;
        }

        mLinearStrip.post(new Runnable() {
            @Override
            public void run() {

                if (mLinearStrip == null) {
                    return;
                }

                int count = mLinearStrip.getChildCount();
                if (position < 0 || count == 0 || position >= count) {
                    return;
                }

                View item = mLinearStrip.getChildAt(position);
                if (item instanceof StateImageView) {
                    ((StateImageView) item).setNewState(splat);
                }
            }
        });
    }

    private void clearSplatOnItem(final int position) {
        if (mLinearStrip == null) {
            return;
        }

        mLinearStrip.post(new Runnable() {
            @Override
            public void run() {
                if (mLinearStrip == null) {
                    return;
                }

                View item = mLinearStrip.getChildAt(position);
                if (item instanceof StateImageView) {
                    ((StateImageView) item).setNewState(false);
                }
            }
        });
    }

    /**
     * Main method to select and process movement. Since this layout is using ImageView we need
     * to loop over and activate based on position. Then inform the horizontal scroll view to
     * scroll to the desired position.
     */
    private void selectItem(final int position, final int offset, final int scrollState) {
        if (mLinearStrip == null) {
            return;
        }

        int count = mLinearStrip.getChildCount();
        if (position < 0 || count == 0 || position >= count) {
            return;
        }

        // Set the activated state on the icons in the strip, do this only when the
        // Scroll state is idle.
        if(scrollState == ViewPager.SCROLL_STATE_IDLE) {
            for (int i = 0; i < mLinearStrip.getChildCount(); i++) {
                View item = mLinearStrip.getChildAt(i);
                if (item != null) {
                    item.setActivated(i == position);
                    if (item.isActivated()) {
                        if (item instanceof StateImageView) {
                            ((StateImageView) item).setNewState(false);
                        }
                    }
                }
            }
        }

        View selectedChild = mLinearStrip.getChildAt(position);
        if (selectedChild != null) {
            int targetScrollX = selectedChild.getLeft() + offset;

            if (position > 0 || offset > 0) {
                // If we're not at the first child and are mid-scroll, make sure we obey the offset
                targetScrollX -= mTitleOffset;
            }

            scrollTo(targetScrollX, 0);
        }
    }

    /**
     * Private listener to the Pager to forward events need to keep UI in sync.
     */
    private final class InternalViewPagerListener implements ViewPager.OnPageChangeListener {

        private int mScrollState;
        private int mSelectedPosition;

        @Override
        public void onPageScrolled(final int pos, final float offset, final int offsetInPix) {
            if (mLinearStrip == null) {
                return;
            }

            mLinearStrip.post(new Runnable() {
                @Override
                public void run() {
                    if (mLinearStrip == null) {
                        return;
                    }

                    int tabStripChildCount = mLinearStrip.getChildCount();
                    if ((tabStripChildCount == 0) || (pos < 0) || (pos >= tabStripChildCount)) {
                        return;
                    }

                    View view = mLinearStrip.getChildAt(pos);
                    int extraOffset = (view != null)
                            ? (int) (offset * view.getWidth())
                            : 0;

                    // This is a noisy call, but needed, send the scroll state to help with activation
                    // selection
                    selectItem(pos, extraOffset, mScrollState);
                    mLinearStrip.onViewPagerPageChanged(pos, offset);
                }
            });
        }

        @Override
        public void onPageSelected(final int pos) {
            // Remember the selected page
            clearSplatOnItem(mSelectedPosition);
            mSelectedPosition = pos;
        }

        @Override
        public void onPageScrollStateChanged(int i) {
            // Record the scroll state
            mScrollState = i;
            if(mScrollState == ViewPager.SCROLL_STATE_IDLE) {
                // When the state is IDLE, then reselect the item.

                if((mLinearStrip != null)) {
                    mLinearStrip.post(new Runnable() {
                        @Override
                        public void run() {
                            selectItem(mSelectedPosition, 0, mScrollState);
                        }
                    });
                }
            }
        }
    }

    /**
     * Handles user press to move the pager to the selected item.
     */
    private class TabClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            for (int i = 0; i < mLinearStrip.getChildCount(); i++) {
                if (v == mLinearStrip.getChildAt(i)) {
                    final int previousItemIndex = mViewPager.getCurrentItem();

                    mViewPager.setCurrentItem(i);

                    // If previous tab is same as current tab, then user is tapping on same tab as
                    // current page. Trigger the click listener for it.
                    if (previousItemIndex == i) {
                        if(mSlidingIconClickListener != null) {
                            mSlidingIconClickListener.onSlidingIconClicked(i);
                        }
                    }

                    return;
                }
            }
        }
    }

    /**
     * Helper layout to contain the icons. This will also take care of drawing the
     * underline on the active selection.
     */
    private final class StripLayout extends LinearLayout {

        private static final int DEFAULT_SELECTED_THICKNESS = 3;
        private static final int DEFAULT_UNSELECTED_THICKNESS = 1;

        private int mSelectedPosition;
        private float mSelectionOffset;
        private final Paint mSelectPaint;
        private final Paint mUnselectedPaint;
        private final int mSelectedThickness;
        private final int mUnselectedThickness;

        StripLayout(Context context) {
            this(context, null);
        }

        StripLayout(Context context, AttributeSet attrs) {
            super(context, attrs);
            setWillNotDraw(false);

            mSelectPaint = new Paint();
            mUnselectedPaint = new Paint();

            final float density = getResources().getDisplayMetrics().density;
            mSelectedThickness = (int) (DEFAULT_SELECTED_THICKNESS * density);
            mUnselectedThickness = (int) (DEFAULT_UNSELECTED_THICKNESS * density);

            mUnselectedPaint.setColor(context.getResources().getColor(R.color.divider_color));
            mSelectPaint.setColor(context.getResources().getColor(R.color.primaryColor));
        }

        void onViewPagerPageChanged(int position, float positionOffset) {
            mSelectedPosition = position;
            mSelectionOffset = positionOffset;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int height = getHeight();
            final int childCount = getChildCount();

            // Thick colored underline below the current selection
            if (childCount > 0) {
                View view = getChildAt(mSelectedPosition);
                int left = view.getLeft();
                int right = view.getRight();

                if (mSelectionOffset > 0f && mSelectedPosition < (getChildCount() - 1)) {
                    // Draw the selection partway between the tabs
                    View nextView = getChildAt(mSelectedPosition + 1);
                    left = (int) (mSelectionOffset * nextView.getLeft() +
                            (1.0f - mSelectionOffset) * left);
                    right = (int) (mSelectionOffset * nextView.getRight() +
                            (1.0f - mSelectionOffset) * right);
                }

                canvas.drawRect(left, height - mSelectedThickness, right, height, mSelectPaint);
            }

            // Thin underline along the entire bottom edge
            canvas.drawRect(0, height - mUnselectedThickness, getWidth(), height, mUnselectedPaint);
            super.onDraw(canvas);
        }
    }

    public void setDisplayStyle(@DisplayStyle int displayStyle) {
        mDisplayStyle = displayStyle;
    }
}
