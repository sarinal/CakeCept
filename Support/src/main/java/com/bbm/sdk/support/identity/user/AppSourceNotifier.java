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

package com.bbm.sdk.support.identity.user;

import android.support.annotation.NonNull;

import java.util.ArrayList;

/**
 * Base class for notifying the UserManager of users being added or changed
 */
public abstract class AppSourceNotifier implements AppUserSource {

    protected enum EventToNotify {
        ADD, REMOVE, CHANGE, LOCAL_UPDATED
    }

    private ArrayList<AppUserListener> mAppUserListeners = new ArrayList<>();

    @Override
    public void addListener(@NonNull AppUserListener listener) {
        if (!mAppUserListeners.contains(listener)) {
            mAppUserListeners.add(listener);
            listener.setAppUserSource(this);
        }
    }

    @Override
    public void removeListener(@NonNull AppUserListener listener) {
        mAppUserListeners.remove(listener);
    }

    protected void notifyAppUserListeners(EventToNotify event, AppUser appUser) {
        for (AppUserListener listener : mAppUserListeners) {
            switch (event) {
                case ADD:
                    listener.remoteUserAdded(appUser);
                    break;
                case REMOVE:
                    listener.remoteUserRemoved(appUser);
                    break;
                case CHANGE:
                    listener.remoteUserChanged(appUser);
                    break;
                case LOCAL_UPDATED:
                    listener.localUserUpdated(appUser);
                    break;
            }
        }
    }

}
