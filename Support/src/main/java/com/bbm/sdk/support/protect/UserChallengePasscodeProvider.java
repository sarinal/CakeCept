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


import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.bbm.sdk.support.ui.widgets.activities.PasscodeChallengeActivity;

/**
 * Used to query the user for a passcode or query the user to create a new passcode
 */
public class UserChallengePasscodeProvider implements PasscodeProvider {

    private Context mContext;

    public UserChallengePasscodeProvider(Context context) {
        mContext = context;
    }

    /**
     * Challenge the user to provide a previously created passcode.
     * @param allowCancel true if the passcode prompt can be cancelled
     * @param previousError an error which occurred on a previous passcode challenge, {@link PasscodeError#None} if no error is present
     */
    public void provideExistingPasscode(boolean allowCancel, @NonNull PasscodeError previousError) {
        //Start PasscodeChallengeActivity
        Intent intent = new Intent(mContext, PasscodeChallengeActivity.class);
        intent.putExtra(PasscodeChallengeActivity.EXTRA_ALLOW_CANCEL, allowCancel);
        intent.putExtra(PasscodeChallengeActivity.EXTRA_PREVIOUS_ERROR, previousError.name());
        //Make sure we only have 1 instance of the Password Challenge activity ever displayed
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    /**
     * Ask the user to create a new passcode.
     * Password rules such as length or character requirements are the responsibility of the PasscodeProvider.
     * @param allowCancel true if the passcode prompt can be cancelled
     * @param previousError an error which occurred on a previous passcode challenge, {@link PasscodeError#None} if no error is present
     */
    public void requestNewPasscode(boolean allowCancel, @NonNull PasscodeError previousError) {
        //Start PasscodeChallengeActivity
        Intent intent = new Intent(mContext, PasscodeChallengeActivity.class);
        intent.putExtra(PasscodeChallengeActivity.EXTRA_ACTION_NEW_PASSWORD, true);
        intent.putExtra(PasscodeChallengeActivity.EXTRA_ALLOW_CANCEL, allowCancel);
        intent.putExtra(PasscodeChallengeActivity.EXTRA_PREVIOUS_ERROR, previousError.name());
        //Make sure we only have 1 instance of the Password Challenge activity ever displayed
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }
}
