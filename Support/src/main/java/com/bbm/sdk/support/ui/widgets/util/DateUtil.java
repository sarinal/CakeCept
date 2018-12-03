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

import com.bbm.sdk.support.util.TimeRangeFormatter;
import com.bbm.sdk.support.util.TimestampScheduler;

public class DateUtil {
    /**
     * Convenience wrapper around TimestampScheduler/TimeRangeFormatter
     * Timestamp must be provided in SECONDS
     */
    public static String observableChatBubbleHeaderTimestamp(final Context context, final long when) {
        final TimeRangeFormatter trf = TimeRangeFormatter.getChatBubbleHeaderRangesFormatter();
        final String timeString = TimestampScheduler.getInstance().process(context, when, trf);
        return timeString;
    }
}
