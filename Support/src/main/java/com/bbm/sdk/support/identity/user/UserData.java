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
 * Create a local class to receive the data. It is similar to
 * the format of an AppUser, but not identical. It excludes the 'exists'
 * field, and records the regId as a string rather than a long. The
 * firebase database stores data in JSON, and not all JSON parsers support
 * numbers as large as those used for regId, which are supported in a long.
 */
public class UserData {

    /**
     * The display name used with the authentication system.
     */
    private String name;

    /**
     * The optional email name used with the authentication system.
     */
    private String email;

    /**
     * The optional avatar URL used with the authentication system.
     */
    private String avatarUrl;

    /**
     * Empty constructor required by firebase
     */
    public UserData() {

    }

    /**
     * Create UserData from values.
     *
     * @param name  The display name used with the authentication system.
     * @param email  The optional email name used with the authentication system.
     * @param avatarUrl  The optional avatar URL used with the authentication system.
     */
    public UserData(String name, String email, String avatarUrl) {
        this.name = name;
        this.email = email;
        this.avatarUrl = avatarUrl;
    }

    /**
     * Create UserData from AppData.
     *
     * @param user The appData with values to copy.
     */
    public UserData(AppUser user) {
        this.name = user.getName();
        this.email = user.getEmail();
        this.avatarUrl = user.getAvatarUrl();
    }

    // Public getters for each property to satisfy the firebase interface.
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getAvatarUrl() { return avatarUrl; }
}
