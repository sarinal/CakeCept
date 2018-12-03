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

package com.bbm.sdk.support.protect;

/**
 * Simple interface for asynchronous requests to the KeyStorageProvider
 */
public interface KeyStorageResponse<T> {

    /**
     * The key storage action completed successfully
     * @param value the result of the key storage action
     */
    void onSuccess(T value);

    /**
     * The key storage action failed.
     * The sender of key storage action shall be responsible for all failure handling.
     */
    void onFailure();

}
