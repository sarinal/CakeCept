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

package com.bbm.sdk.support.identity.user;

import android.support.annotation.NonNull;

/**
 * A source for app users.
 * This is normally a server, remote DB, cloud storage, etc.
 */
public interface AppUserSource {
    /**
     * Add an AppUserListener.
     * @param listener the listener to add.
     */
    void addListener(@NonNull AppUserListener listener);

    /**
     * Remove a previously registered app user listener.
     * @param listener the listener to remove.
     */
    void removeListener(@NonNull AppUserListener listener);

    /**
     * Attempt to find and add the user matching the uid.
     * @param uid the user identifier for the requested user.
     */
    void requestUser(@NonNull String uid);
}
