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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

/**
 * Empty implementation of AuthIdentityHelper for noAuthProvider config
 */
public class AuthIdentityHelper {

    public static void initIdentity(Context context) {
    }

    public static void stopIdentityProvider() {
    }

    public static void handleEndpointDeregistered(Context context) {
    }

    public static void setActivity(Activity activity) {
    }

    public static void handleAuthenticationResult(Context context, int requestCode, int resultCode, Intent data) {
    }
}
