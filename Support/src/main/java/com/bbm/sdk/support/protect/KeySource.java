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

import android.support.annotation.NonNull;

/**
 * Generic interface for representing a 'KeySource'. The Keysource consumes passcodes to provide keys to bbmcore.
 */
public interface KeySource {

    /**
     * Start the key source to begin monitoring for keys.
     */
    void start();

    /**
     * Stop the key source, the key source should stop providing keys.
     */
    void stop();

    /**
     * Trigger any pending failed key storage requests to occur.
     */
    void retryFailedEvents();

    /**
     * Change the passcode used to encrypt the keys.
     */
    void changePasscode();

    /**
     * Set a passcode to be used to encrypt and decrypt keys.
     * @param passcode the passcode provided by the user.
     */
    void setPasscode(@NonNull String passcode);

    /**
     * Clear the existing keys in storage and de-register all active endpoints.
     * The user can then choose a new passcode upon setup.
     */
    void forgotPasscode();

}
