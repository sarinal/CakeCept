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
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.bbm.sdk.support.R;

/**
 * Not really a button, but serves as a button on the settings screen
 * 2 lines of text (label + summary)
 */
public class SettingView extends CustomView {

    private TextView mLabel;
    private TextView mSummary;

    public SettingView(final Context context) {
        this(context, null);
    }

    public SettingView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SettingView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }


    private void init(final Context context, final AttributeSet attrs, final int defStyle) {

        final View view = LayoutInflater.from(context).inflate(R.layout.view_settings, this, true);

        mLabel = (TextView) findViewById(R.id.setting_label);
        mSummary = (TextView) findViewById(R.id.setting_summary);
        final View viewDivider = findViewById(R.id.setting_divider);

        final TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.Setting);

        try {
            setLabel(a.getString(R.styleable.Setting_settingLabel));
            setSummary(a.getString(R.styleable.Setting_settingSummary));
            viewDivider.setVisibility(a.getBoolean(R.styleable.Setting_hideDivider, false) ? View.GONE : View.VISIBLE);
        } finally {
            a.recycle();
        }

    }

    public void setLabel(final String label) {
        mLabel.setText(label);
    }

    public void setSummary(final String summary) {
        mSummary.setVisibility(TextUtils.isEmpty(summary) ? GONE : VISIBLE);
        mSummary.setText(summary);
    }

}
