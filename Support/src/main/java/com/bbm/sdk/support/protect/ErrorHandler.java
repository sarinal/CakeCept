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

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Error handler interface for errors which occur in ProtectedManager.
 */
public interface ErrorHandler {

    /**
     * Error decrypting a stored key
     */
    public static final int DECRYPTION_ERROR = 0;

    /**
     * Error encrypting a key using the current management keys
     */
    public static final int ENCRYPTION_ERROR = 1;

    /**
     * Error occurred while trying to store or recover keys from the keystore
     */
    public static final int DEVICE_KEYSTORE_ERROR = 2;

    /**
     * Error occurred while trying to read or write a value to the key storage
     */
    public static final int KEY_STORAGE_PROVIDER_ERROR = 3;

    @Retention(SOURCE)
    @IntDef({DECRYPTION_ERROR, ENCRYPTION_ERROR, DEVICE_KEYSTORE_ERROR, KEY_STORAGE_PROVIDER_ERROR})
    @interface ErrorType {
    }

    void onError(@ErrorType int errorType);
}
