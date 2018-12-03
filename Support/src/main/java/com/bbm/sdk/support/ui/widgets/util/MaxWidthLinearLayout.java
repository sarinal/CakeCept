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

package com.bbm.sdk.support.ui.widgets.util;


import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.bbm.sdk.support.R;


public class MaxWidthLinearLayout extends LinearLayout{

    private int mBoundedWidth;

    public MaxWidthLinearLayout(final Context context) {
        super(context);
    }

    public MaxWidthLinearLayout(final Context context, final AttributeSet attributeSet) {
        super(context, attributeSet);
        TypedArray attr = getContext().obtainStyledAttributes(attributeSet, R.styleable.MaxWidthLinearLayout);
        int maxWidth = attr.getDimensionPixelSize(R.styleable.MaxWidthLinearLayout_maxWidth, mBoundedWidth);
        if (maxWidth > 0) {
            mBoundedWidth = maxWidth;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, final int heightMeasureSpec) {
        int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
        if (mBoundedWidth < measuredWidth && mBoundedWidth > 0) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(mBoundedWidth, MeasureSpec.getMode(widthMeasureSpec));
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
