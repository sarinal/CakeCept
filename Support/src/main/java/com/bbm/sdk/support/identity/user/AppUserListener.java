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

/**
 * This interface is used to listen for changes to the remote app users and local app user
 * from the {@Link AppUserSource}
 */
public interface AppUserListener {
    /**
     * Called when the local user is updated.
     *
     * @param localAppUser the local app user
     */
    void localUserUpdated(AppUser localAppUser);

    /**
     * Called when a remote app user is added.
     *
     * @param remoteAppUser the remote app user
     */
    void remoteUserAdded(AppUser remoteAppUser);

    /**
     * Called when a remote app user is changed.
     *
     * @param remoteAppUser the remote app user
     */
    void remoteUserChanged(AppUser remoteAppUser);

    /**
     * Called when a remote app user is removed.
     *
     * @param remoteAppUser the remote app user
     */
    void remoteUserRemoved(AppUser remoteAppUser);

    /**
     * Called when the app user source has changed.
     * @param source the source of app users
     */
    void setAppUserSource(AppUserSource source);
}
