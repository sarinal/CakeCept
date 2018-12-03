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

import android.text.TextUtils;

import com.bbm.sdk.bbmds.User;
import com.bbm.sdk.bbmds.internal.Existence;

/**
 * This class contains some data from the BBM User, some data from the authentication provider, and potentially for the application also.
 */
public class AppUser {
    /**
     * The Registration ID which is the identifier used with BBM SDK
     */
    private long regId;

    /**
     * The display name used with the authentication system.
     */
    private String name;

    /**
     * The user identifier provided by the identity service.
     */
    private String uid;

    /**
     * The optional email name used with the authentication system.
     */
    private String email;

    /**
     * The optional avatar URL used with the authentication system.
     */
    private String avatarUrl;

    /**
     * don't want this to be automatically serialized to a DB or other storage so it is transient
     */
    private transient Existence exists;

    /**
     * Empty constructor needed for some persistance and serialization methods
     */
    public AppUser() {
        exists = Existence.MAYBE;
    }

    /**
     * Just to be used by UserManager when a user is requested by regId that is not found yet.
     *
     * @param regId The Registration ID which is the identifier used with BBM SDK
     */
    protected AppUser(long regId, Existence existence) {
        this.regId = regId;
        exists = existence;
    }

    /**
     * Just to be used by UserManager when a user is requested by UID that is not found yet.
     *
     * @param uid The application user identifier for the user
     */
    protected AppUser(String uid, Existence existence) {
        this.uid = uid;
        exists = existence;
    }

    /**
     * Create a new App User
     *
     * @param regId The Registration ID which is the identifier used with BBM SDK
     * @param uid  The user identifier provided by the identity service.
     * @param name  The display name used with the authentication system.
     * @param email  The optional email name used with the authentication system.
     * @param avatarUrl  The optional avatar URL used with the authentication system.
     */
    public AppUser(long regId, String uid, String name, String email, String avatarUrl) {
        this.email = email;
        this.name = name;
        this.uid = uid;
        this.regId = regId;
        this.avatarUrl = avatarUrl;
        exists = Existence.YES;
    }

    /**
     * Create a new App User
     *
     * @param user The BBM SDK user.
     * @param uid the user identifier provided by the identity service.
     * @param name The display name used with the authentication system.
     * @param email The optional email name used with the authentication system.
     * @param avatarUrl The optional avatar URL used with the authentication system.
     */
    public AppUser(User user, String uid, String name, String email, String avatarUrl) {
        this.regId = user.regId;
        this.uid = uid;
        this.email = email;

        if (!TextUtils.isEmpty(name)) {
            //override User.displayName with this one
            this.name = name;
        }

        this.avatarUrl = avatarUrl;
        exists = Existence.YES;
    }

    public long getRegId() {
        return regId;
    }

    public String getName() {
        return name;
    }

    public String getUid() {
        return uid;
    }

    public String getEmail() {
        return email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Existence getExists() {
        return exists;
    }

    /**
     * Only to be used by the source syncs with the AppUser source/DB.
     */
    public void setExists(Existence exists) {
        this.exists = exists;
    }

    @Override
    public String toString() {
        return "AppUser{" +
                "regId=" + regId +
                ", name='" + name + '\'' +
                ", uid='" + uid + '\'' +
                ", email='" + email + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", exists='" + exists + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AppUser appUser = (AppUser) o;

        if (regId != appUser.regId) return false;
        if (name != null ? !name.equals(appUser.name) : appUser.name != null) return false;
        if (uid != null ? !uid.equals(appUser.uid) : appUser.uid != null) return false;
        if (email != null ? !email.equals(appUser.email) : appUser.email != null) return false;
        return avatarUrl != null ? avatarUrl.equals(appUser.avatarUrl) : appUser.avatarUrl == null;

    }

    @Override
    public int hashCode() {
        int result = (int) (regId ^ (regId >>> 32));
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (uid != null ? uid.hashCode() : 0);
        result = 31 * result + (email != null ? email.hashCode() : 0);
        result = 31 * result + (avatarUrl != null ? avatarUrl.hashCode() : 0);
        return result;
    }
}
