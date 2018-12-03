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
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.bbm.sdk.reactive.Mutable;

import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Timer;
import java.util.TimerTask;

public class TimestampScheduler {

    private static TimestampScheduler mInstance = null;
    private final PriorityQueue<TimeObservable> mQueue = new PriorityQueue<>();
    private final HashMap<TimeObservableKey, TimeObservable> mObservableCache = new HashMap<>();
    private final Timer mTimer = new Timer();
    private TimerTask mTimerTask;

    private final Handler mHandler;

    public static synchronized TimestampScheduler getInstance() {
        if (mInstance == null) {
            mInstance = new TimestampScheduler();
        }
        return mInstance;
    }

    private TimestampScheduler() {
        mHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Returns the current timestamp string for the given timestamp and
     * formatter, and arranges for observers to be notified when the timestamp
     * string should change.
     * For convenience, 'timestamp' should be in seconds, since that's what
     * Model classes use. (TimeRangeFormatter wants milliseconds)
     */
    public String process(final Context context, final long timestamp, final TimeRangeFormatter formatter) {
        /* For any given timestamp/formatter pair, until it expires we should
         * always get the same string and expiry time, so should only need a
         * single TimeObservable instance. If we already have one, it's already
         * scheduled properly.  */
        final TimeObservableKey key = new TimeObservableKey(timestamp, formatter);
        TimeObservable obs = mObservableCache.get(key);
        if (obs == null) {
            obs = new TimeObservable(context, timestamp, formatter, this);
            mObservableCache.put(new TimeObservableKey(obs), obs);
        }
        /* An exception exists, when the user change the 12/24 format in his settings.
         * To prevent creating a new TimeObservable each time,
         * we check if we need to update the formatter.
         */
        else if (needToUpdateObservable(context, obs, formatter, timestamp)) {
            obs.updateFormatter(context, formatter);
        }

        return obs.getFormattedTime();
    }

    /* Check if the given TimeObservable need to have its formatter updated
     * by comparing its formatted time, and formatted time with given formatter
     */
    private boolean needToUpdateObservable(final Context context, final TimeObservable obs, final TimeRangeFormatter trf, final long timestamp) {
        return !obs.getFormattedTime().equals(trf.format(context, timestamp * 1000, System.currentTimeMillis()).formattedTime);
    }


    private void add(final TimeObservable observable) {
        mQueue.add(observable);
        reschedule();
    }


    private void reschedule() {
        final TimeObservable newFirst = mQueue.peek();
        if (newFirst != null) {
            final long now = System.currentTimeMillis();
            final long deadlineIn = Math.max(0, newFirst.mExpiryTime - now);
            scheduleTimer(deadlineIn);
        }
    }


    private void scheduleTimer(final long delay) {
        if (mTimerTask != null) {
            mTimerTask.cancel();
        }
        mTimerTask = new TimerTask() {
            @Override
            public void run() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onTimeout();
                    }
                });
            }
        };
        mTimer.schedule(mTimerTask, delay);
    }

    private void onTimeout() {
        /* for efficiency's sake, we drain the queue of all expired
         * items in one shot, instead of just scheduling 0-time timers.
         * This will allow batching observable notifications. */
        TimeObservable next = mQueue.peek();
        final long now = System.currentTimeMillis();
        while (next != null && next.mExpiryTime <= now) {
            mQueue.remove();
            mObservableCache.remove(new TimeObservableKey(next));
            next.mExpired.set(true); // to notify monitors
            next = mQueue.peek();
        }
        reschedule();
    }

    private static class TimeObservable implements Comparable<TimeObservable> {

        private final Mutable<Boolean> mExpired = new Mutable<>(false);
        private String mFormattedTime;
        private final TimestampScheduler mScheduler;
        private long mExpiryTime;
        private final long mTimestamp;
        private TimeRangeFormatter mFormatter;

        public TimeObservable(final Context context, final long timestamp,
                              final TimeRangeFormatter formatter,
                              final TimestampScheduler scheduler) {
            mTimestamp = timestamp;
            mFormatter = formatter;
            mScheduler = scheduler;

            formatterUpdated(context);
        }

        private String getFormattedTime() {
            /* the get() is so callers will be notified when we expire. */
            if (mExpired.get()) {
                Logger.e("getFormattedTime called on expired TimeObservable: " + mFormattedTime);
            }

            return mFormattedTime;
        }

        /**
         * For PriorityQueue
         */
        @Override
        public int compareTo(@NonNull final TimeObservable observable) {
            if (mExpiryTime < observable.mExpiryTime) {
                return -1;
            } else if (mExpiryTime > observable.mExpiryTime) {
                return 1;
            }
            return 0;
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

            TimeObservable other = (TimeObservable) o;

            if (mExpired.get() == other.mExpired.get()) {
                return false;
            }

            if (mFormattedTime == null && other.mFormattedTime != null) {
                return false;
            } else {
                if (mFormattedTime != null && !mFormattedTime.equals(other.mFormattedTime)) {
                    return false;
                }
            }

            if (mScheduler != other.mScheduler) {
                return false;
            }

            if (mExpiryTime != other.mExpiryTime) {
                return false;
            }

            if (mTimestamp != other.mTimestamp) {
                return false;
            }

            return mFormatter == other.mFormatter;

        }

        @Override
        public int hashCode() {
            int retValue = 21;

            retValue += 21 * mExpired.hashCode();
            retValue += 21 * (mFormattedTime == null ? 0 : mFormattedTime.hashCode());
            retValue += 21 * mScheduler.hashCode();
            retValue += 21 * (int) (mExpiryTime ^ (mExpiryTime >>> 32));
            retValue += 21 * (int) (mTimestamp ^ (mTimestamp >>> 32));
            retValue += 21 * mFormatter.hashCode();

            return retValue;
        }

        public void updateFormatter(final Context context, final TimeRangeFormatter formatter) {
            mFormatter = formatter;
            formatterUpdated(context);
        }

        public void formatterUpdated(final Context context) {
            final long now = System.currentTimeMillis();
            final TimeRangeFormatter.FormatResult result = mFormatter.format(context, mTimestamp * 1000, now);

            mFormattedTime = result.formattedTime;
            if (result.expiresIn != TimeRangeFormatter.OUT_OF_RANGE) {
                mExpiryTime = now + result.expiresIn;
                mScheduler.add(this);
            }
        }
    }

    private static class TimeObservableKey {
        private final long mTimestamp;
        private final TimeRangeFormatter mFormatter;

        public TimeObservableKey(final long timestamp, final TimeRangeFormatter formatter) {
            mTimestamp = timestamp;
            mFormatter = formatter;
        }

        public TimeObservableKey(final TimeObservable o) {
            mTimestamp = o.mTimestamp;
            mFormatter = o.mFormatter;
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

            final TimeObservableKey other = (TimeObservableKey) o;
            return mTimestamp == other.mTimestamp && mFormatter == other.mFormatter;
        }

        @Override
        public int hashCode() {
            return (int) mTimestamp * 31 + mFormatter.hashCode();
        }
    }
}
