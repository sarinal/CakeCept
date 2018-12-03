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

package com.bbm.sdk.support.util;

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.format.DateUtils;

import com.bbm.sdk.common.Equal;
import com.bbm.sdk.support.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class TimeRangeFormatter {

    public static final int ONE_SECOND = 1000;
    public static final int ONE_MINUTE = ONE_SECOND * 60;
    public static final int ONE_HOUR = ONE_MINUTE * 60;
    public static final int ONE_DAY = ONE_HOUR * 24;
    public static final int ONE_WEEK = ONE_DAY * 7;
    public static final long ONE_YEAR = ONE_DAY * 365L;
    //TODAY_SENTINEL represents a variable range (ie. the time between now and 12:00am this morning)
    //it is a sentinel value and not actually a time range in milliseconds.
    private static final int TODAY_SENTINEL = -99;
    private static final int YESTERDAY_SENTINEL = -98;

    public static final int OUT_OF_RANGE = -1;

    private static TimeRangeFormatter mVerboseRangesFormatter = null;
    private static TimeRangeFormatter mShortRangesFormatter = null;
    private static TimeRangeFormatter mLongRangesFormatter = null;
    private static TimeRangeFormatter mChatBubbleHeaderFormatter = null;

    private final List<TimeRange> mRanges;
    private final StringFormatter mNonRelativeFormatter;

    protected static StringFormatter mLessThanHourAgo;
    protected static StringFormatter mLessThanDayAgo;
    protected static StringFormatter mWeekdayNoAt;
    protected static StringFormatter mNonRelative;

    public static synchronized StringFormatter getLessThanHourAgoFormatter() {
        if (mLessThanHourAgo == null) {
            mLessThanHourAgo = new StringFormatter() {

                @Override
                public String format(final Context context, final long timestamp, final long diff) { // Just now, 1 minute ago, 2 minutes ago
                    final int nbrMinutes = (int) (diff / ONE_MINUTE);
                    if (nbrMinutes < 1) {
                        return context.getString(R.string.timestamp_format_just_now);
                    } else if (nbrMinutes == 1) {
                        return context.getString(R.string.timestamp_format_one_minute_ago);
                    } else {
                        return context.getString(R.string.timestamp_format_minutes_ago, String.valueOf(nbrMinutes));
                    }
                }
            };
        }
        return mLessThanHourAgo;
    }


    public static synchronized StringFormatter getLessThanDayFormatter() {
        if (mLessThanDayAgo == null) {
            mLessThanDayAgo = new StringFormatter() {

                @Override
                public String format(final Context context, final long timestamp, final long diff) { // 3:14PM
                    final int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_CAP_AMPM;
                    return DateUtils.formatDateTime(context, timestamp, flags);
                }
            };
        }
        return mLessThanDayAgo;
    }

    public static synchronized StringFormatter getWeekdayNoAtFormatter() {
        if (mWeekdayNoAt == null) {
            mWeekdayNoAt = new StringFormatter() {

                @Override
                public String format(final Context context, final long timestamp, final long diff) { // Tue 3:14PM
                    final int flagsA = DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY;
                    final int flagsB = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_CAP_AMPM;
                    return DateUtils.formatDateTime(context, timestamp, flagsA) + " " + DateUtils.formatDateTime(context, timestamp, flagsB);
                }
            };
        }
        return mWeekdayNoAt;
    }

    public static synchronized StringFormatter getNonRelativeFormatter() {
        if (mNonRelative == null) {
            mNonRelative = new StringFormatter() {
                @Override
                public String format(final Context context, final long timestamp, final long diff) { // Aug 1
                    int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_NO_YEAR;
                    if (diff > ONE_YEAR) {
                        flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_YEAR;
                    }
                    return DateUtils.formatDateTime(context, timestamp, flags);
                }
            };
        }
        return mNonRelative;
    }

    private TimeRangeFormatter(final List<TimeRange> ranges) {
        mNonRelativeFormatter = getNonRelativeFormatter();
        mRanges = ranges;
    }


    //This one can be useful for testing that the time updates in chats
    public static synchronized TimeRangeFormatter getVerboseRangesFormatter() {
        if (mVerboseRangesFormatter == null) {
            final List<TimeRange> ranges = new ArrayList<>();
            ranges.add(new TimeRange(ONE_HOUR, ONE_MINUTE, getLessThanHourAgoFormatter()));
            ranges.add(new TimeRange(TODAY_SENTINEL, ONE_HOUR, getLessThanDayFormatter()));
            ranges.add(new TimeRange(ONE_WEEK, ONE_DAY, getWeekdayNoAtFormatter()));
            mVerboseRangesFormatter = new TimeRangeFormatter(ranges);
        }
        return mVerboseRangesFormatter;
    }

    public static synchronized TimeRangeFormatter getChatBubbleHeaderRangesFormatter() {
        if (mChatBubbleHeaderFormatter == null) {
            final List<TimeRange> ranges = new ArrayList<>();
            ranges.add(new TimeRange(TODAY_SENTINEL, ONE_HOUR, getLessThanDayFormatter()));
            ranges.add(new TimeRange(ONE_WEEK, ONE_DAY, getWeekdayNoAtFormatter()));
            mChatBubbleHeaderFormatter = new TimeRangeFormatter(ranges);
        }
        return mChatBubbleHeaderFormatter;
    }

    /**
     * Formats the timestamp, given the current time.
     *
     * @param timestamp The timestamp to be formatted.
     * @param now The system time to be used for the formatting.
     */
    public FormatResult format(final Context context, final long timestamp, final long now) {
        final FormatResult result = new FormatResult();
        final long diff = now - timestamp;
        int i;
        for (i = 0; i < mRanges.size(); i++) {
            if (diff < mRanges.get(i).getCeiling()) {
                break;
            }
        }

        if (i == mRanges.size()) {
            // The timestamp is older than all ranges and should be formatted non-relative
            result.formattedTime = mNonRelativeFormatter.format(context, timestamp, diff);
        } else {
            result.formattedTime = mRanges.get(i).mFormatter.format(context, timestamp, diff);
            result.expiresIn = mRanges.get(i).mCallbackFrequency - ( Math.abs(diff) % mRanges.get(i).mCallbackFrequency);
        }

        return result;
    }

    public static class FormatResult {
        String formattedTime = "";
        long expiresIn = OUT_OF_RANGE;
    }

    public interface StringFormatter {

        /**
         * Formats the string.
         */
        String format(Context context, long timestamp, long diff);

    }

    public static class TimeRange implements Comparable<TimeRange> {

        public TimeRange(final long ceiling, final long callbackFrequency, final StringFormatter formatter) {
            mCeiling = ceiling;
            mCallbackFrequency = callbackFrequency;
            mFormatter = formatter;
        }

        // Range ceiling
        private final long mCeiling;

        //this should get calc'ed once a day.
        private static long sTodayMidnight;

        // Callback frequency for updating the string
        public final long mCallbackFrequency;

        // String formatter
        public final StringFormatter mFormatter;

        @Override
        public int compareTo(@NonNull final TimeRange another) {

            final long thisCeiling = getCeiling();
            final long anotherCeiling = another.getCeiling();

            if (thisCeiling < anotherCeiling) {
                return -1;
            } else if (thisCeiling > anotherCeiling) {
                return 1;
            } else {
                return 0;
            }
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null) {
                return false;
            }

            if (this.getClass() != o.getClass()) {
                return false;
            }

            if (this == o) {
                return true;
            }

            TimeRange other = (TimeRange) o;
            if(mCeiling != other.mCeiling) {
                return false;
            }

            if(mCallbackFrequency != other.mCallbackFrequency) {
                return false;
            }

            return Equal.isEqual(mFormatter, other.mFormatter);

        }

        @Override
        public int hashCode() {

            final int prime = 31;
            int ret = 1;
            ret = prime * ret + (int)(mCeiling^(mCeiling>>>32));
            ret = prime * ret + (int)(mCallbackFrequency^(mCallbackFrequency>>>32));
            ret = prime * ret + ((mFormatter == null) ? 0 : mFormatter.hashCode());

            return ret;
        }

        static synchronized void setMidnight(final long value) {
            sTodayMidnight = value;
        }

        public long getCeiling() {
            //if mCeiling is one of the sentinel values, calc the "ceiling" value on the fly.
            if (mCeiling == TODAY_SENTINEL || mCeiling==YESTERDAY_SENTINEL) {

                long range = System.currentTimeMillis() - sTodayMidnight;
                //we only need to recalculate the midnight value once every 24hrs
                if (range > ONE_DAY ) {
                    //this sets the ceiling to the time between now and midnight.
                    // today at midnight is the time 0:00:00s am.
                    Calendar c = Calendar.getInstance();
                    final long now = c.getTimeInMillis();
                    c.set(Calendar.HOUR_OF_DAY, 0);
                    c.set(Calendar.MINUTE, 0);
                    c.set(Calendar.SECOND, 0);
                    c.set(Calendar.MILLISECOND, 0);

                    setMidnight(c.getTimeInMillis());
                    range = now - sTodayMidnight;
                }
                //if we are are trying to find the range for "yesterday", add 24 hrs
                //while this may not be strictly correct based on DST, etc. it's close enough
                //for the purposes of ranking the timestamp formatter.
                return range + (mCeiling==YESTERDAY_SENTINEL ? ONE_DAY : 0);

            } else  {
                return mCeiling;
            }
        }

    }

}
