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

package com.bbm.sdk.support.ui.widgets.chats.holders;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.bbm.sdk.support.R;

/**
 * A base for an outgoing message bubble. The message specific content should be injected via {@link #setContentSpecificView(LayoutInflater, int)}
 */
public class BaseOutgoingBubbleHolder extends BaseBubbleHolder {

    public BaseOutgoingBubbleHolder(final LayoutInflater layoutInflater, final ViewGroup parent) {
        super(layoutInflater, parent, R.layout.chat_bubble_base_outgoing_holder);
    }

    /**
     * Add message specific content
     * @param layoutInflater to be used to inflate the layout
     * @param contentResId the content specific view id to inflate and add
     * @return the layout holding the specific content
     */
    @Override
    public View setContentSpecificView(LayoutInflater layoutInflater, int contentResId) {
        View view = super.setContentSpecificView(layoutInflater, contentResId);
        //content in outgoing bubbles should be aligned to the right in the frame layout
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        lp.gravity = Gravity.END;
        return view;
    }

}
