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
 * Used to filter on a list of app users.
 */
public interface AppUserFilter {
    /**
     * Check if specified appUser matches the criteria.
     *
     * @param appUser the app user to check
     * @return true if it matches, false otherwise
     */
    boolean matches(AppUser appUser);
}
