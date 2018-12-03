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
 * Authentication data from an authentication provider.
 */
public class AuthenticatedAccountData {
    /**
     * The unique identifier from the authentication provider.
     */
    private String id;

    /**
     * The token from the authentication provider that can be sent to BBM SDK.
     */
    private String idToken;

    /**
     * The display name for the user
     */
    private String name;

    /**
     * The optional email address for the user.
     */
    private String email;

    /**
     * The optional URL to a display avatar for the user.
     */
    private String avatarUrl;

    /**
     * Create a new AuthenticatedAccountData
     *
     * @param id The unique identifier from the authentication provider
     * @param idToken The token from the authentication provider that can be sent to BBM SDK
     * @param name The display name for the user
     * @param email The optional email address for the user
     * @param avatarUrl The optional URL to a display avatar for the user
     */
    public AuthenticatedAccountData(String id, String idToken, String name, String email, String avatarUrl) {
        this.avatarUrl = avatarUrl;
        this.email = email;
        this.id = id;
        this.idToken = idToken;
        this.name = name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getEmail() {
        return email;
    }

    public String getId() {
        return id;
    }

    public String getIdToken() {
        return idToken;
    }
    public String getName() {
        return name;
    }
}
