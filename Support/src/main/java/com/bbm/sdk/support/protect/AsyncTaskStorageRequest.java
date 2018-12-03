/*
 * Copyright (c) 2018 BlackBerry.  All Rights Reserved.
 *
 * You must obtain a license from and pay any applicable license fees to
 * BlackBerry before you may reproduce, modify or distribute this
 * software, or any work that includes all or part of this software.
 *
 * This file may contain contributions from others. Please review this entire
 * file for other proprietary rights or license notices.
 */

package com.bbm.sdk.support.protect;

import android.os.AsyncTask;
import java.util.HashMap;

/**
 * An asynchronous task which executes a key storage request
 * @param <T> the result type
 */
public abstract class AsyncTaskStorageRequest<T> extends AsyncTask<Void, Void, Void> implements Runnable, KeyStorageResponse<T> {

    private String mTaskKey;
    private HashMap<String, AsyncTask> mPendingTasks;

    public AsyncTaskStorageRequest(String taskKey, HashMap<String, AsyncTask> pendingTasks) {
        mTaskKey = taskKey;
        mPendingTasks = pendingTasks;
    }

    @Override
    protected void onPreExecute() {
        //Add this task to the list of pending tasks to avoid performing the same key storage actions multiple times
        mPendingTasks.put(mTaskKey, this);
    }

    @Override
    protected Void doInBackground(Void... params) {
        run();
        return null;
    }

    @Override
    protected void onPostExecute(Void result) {
        //Remove the task now that is has completed from the pending list
        mPendingTasks.remove(mTaskKey);
    }
}
