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

package com.bbm.sdk.support.util;


import android.support.annotation.NonNull;

import com.bbm.sdk.support.protect.KeySource;

import javax.annotation.Nullable;

public class KeySourceManager {

    private static KeySource sKeySource;

    /**
     * Set the key source. (see {@link com.bbm.sdk.support.kms.BlackBerryKMSSource} or {@link com.bbm.sdk.support.protect.CloudKeySource}
     */
    public static void setKeySource(@NonNull KeySource keySource) {
        sKeySource = keySource;
    }

    /**
     * Get the key source.
     * @return the active key source, may return null if no key source was set.
     */
    @Nullable
    public static KeySource getKeySource() {
        return sKeySource;
    }
}
