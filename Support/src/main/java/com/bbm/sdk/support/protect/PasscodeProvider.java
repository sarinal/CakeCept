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
 * Interface to abstract the passcode challenge when using BlackBerry KMS or a CloudKey key source.
 */
public interface PasscodeProvider {

    /**
     * List of possible errors that can occur while setting the passcode
     */
    public enum PasscodeError {
        /**
         * The incorrect passcode was provided
         */
        IncorrectPasscode,
        /**
         * Syncing the keys failed due to a timeout
         */
        SyncTimeout,
        /**
         * Syncing the endpoint failed
         */
        SyncFailure,
        /**
         * A temporary failure occurred
         */
        TemporaryFailure,
        /**
         * No error
         */
        None
    }

    /**
     * Challenge the user to provide a previously created passcode.
     * @param allowCancel true if the passcode prompt can be cancelled
     * @param previousError an error which occurred on a previous passcode challenge, {@link PasscodeError#None} if no error is present
     */
    void provideExistingPasscode(boolean allowCancel, @NonNull PasscodeError previousError);

    /**
     * Ask the user to create a new passcode.
     * Password rules such as length or character requirements are the responsibility of the PasscodeProvider.
     * @param allowCancel true if the passcode prompt can be cancelled
     * @param previousError an error which occurred on a previous passcode challenge, {@link PasscodeError#None} if no error is present
     */
    void requestNewPasscode(boolean allowCancel, @NonNull PasscodeError previousError);

}
