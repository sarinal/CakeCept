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
import android.util.AttributeSet;

import com.bbm.sdk.support.R;

/**
 * Helper image view to show icons with a splat state. Users of this class are required
 * to create drawable selector and specify which image should be used when the splat
 * state is true and which image to use when splat state if false.
 */
public class StateImageView extends android.support.v7.widget.AppCompatImageView {

    private static final int[] STATE_NEW = {R.attr.state_new};

    private boolean mStateNew;

    public StateImageView(Context context) {
        super(context);
    }
    public StateImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        if(mStateNew){
            int[] basic = super.onCreateDrawableState(extraSpace + 1);
            mergeDrawableStates(basic, STATE_NEW);
            return basic;
        }

        return super.onCreateDrawableState(extraSpace);
    }

    public void setNewState(final boolean state) {
        if(mStateNew != state) {
            mStateNew = state;
            refreshDrawableState();
        }
    }

    public boolean getNewState() {
        return mStateNew;
    }
}
