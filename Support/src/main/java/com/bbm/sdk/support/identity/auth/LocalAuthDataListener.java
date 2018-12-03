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

package com.bbm.sdk.support.identity.auth;


/**
 * Interface for anything that needs to be notified of changes to the auth data for the local user.
 */
public interface LocalAuthDataListener {
    void localAuthDataChanged(AuthenticatedAccountData accountData);
}

