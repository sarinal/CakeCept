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
package com.bbm.sdk.support.ui.widgets.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.google.common.base.Optional;

/**
 * <p>Base class for custom views that consist of a custom android layout xml file.
 * Subclasses should extend this, should expose all constructors, and should overload
 * the init method to inflate their layout.</p>
 * <p/>
 * <p>Subclassing this base class is preferable than subclassing one of the layout classes
 * such as LinearLayout or RelativeLayout because this drags along less baggage and should
 * be slightly more cpu and memory efficient.</p>
 */
public class CustomView extends ViewGroup {

    public CustomView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    public CustomView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomView(final Context context) {
        this(context, null);
    }


    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
        int measuredHeight = MeasureSpec.getSize(heightMeasureSpec);

        final Optional<View> optRoot = getView();

        if (optRoot.isPresent()) {
            final View root = optRoot.get();

            root.measure(widthMeasureSpec, heightMeasureSpec);

            measuredWidth = root.getMeasuredWidth();
            measuredHeight = root.getMeasuredHeight();
        }

        // The preferred size of this widget is always 0. It can't be used with wrap_content.
        // This is to avoid having to invoke measure on the embedded list widget, which is
        // costly.
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    private Optional<View> getView() {
        if (getChildCount() > 0) {
            return Optional.of(getChildAt(0));
        }

        return Optional.absent();
    }

    @Override
    protected void onLayout(final boolean changed, final int l, final int t, final int r, final int b) {
        final Optional<View> optRoot = getView();

        if (optRoot.isPresent()) {
            final View root = optRoot.get();
            final int height = b - t;
            final int width = r - l;

            root.layout(0, 0, width, height);
        }
    }

}
