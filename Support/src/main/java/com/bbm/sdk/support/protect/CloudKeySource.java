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
 * Base class for cloud key sources (eg. Firebase or Azure).
 * Sub classes must override {@link KeySource#start()}
 */
public abstract class CloudKeySource implements KeySource {

    @Override
    public void retryFailedEvents() {
        ProtectedManager.getInstance().retryFailedEvents();
    }

    @Override
    public void stop() {
        ProtectedManager.getInstance().stop();
    }

    @Override
    public void changePasscode() {
        ProtectedManager.getInstance().changePassword();
    }

    @Override
    public void setPasscode(@NonNull String passcode) {
        ProtectedManager.getInstance().setPasscode(passcode);
    }

    @Override
    public void forgotPasscode() {
        ProtectedManager.getInstance().forgotPassword();
    }
}
