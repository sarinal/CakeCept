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

import com.bbm.sdk.support.util.Logger;
import com.bbm.sdk.support.util.SetupHelper;

public class ProtectedManagerErrorHandler implements ErrorHandler {

    @Override
    public void onError(int errorType) {
        switch (errorType) {
            case DECRYPTION_ERROR:
            case ENCRYPTION_ERROR:
            case DEVICE_KEYSTORE_ERROR:
                //For simplicity this error handler treats all decryption, encryption or keystore errors as fatal
                //We will kick this endpoint and restart.
                //Note: if errors persist the user can choose the "forgot password" option when setting up
                //  Forgot password will remove all data stored in the key storage provider
                //  The user would be required to re-join any existing chats
                handleFatalError();
                break;
            case KEY_STORAGE_PROVIDER_ERROR:
                //Keystorage actions are currently retried automatically based on user actions like opening a chat.
                //An application could choose to implement a different approach
                break;
        }
    }

    /**
     * To handle a fatal error we will remove ourselves as an active endpoint
     * This will result in the application being shutdown.
     * Upon restarting the user will be required to login again and provide their application password (or create a new password)
     */
    private void handleFatalError() {
        Logger.e("ProtectedManagerErrorHandler - handleFatalError");
        SetupHelper.deregisterCurrentEndpoint(new SetupHelper.DeregisterFailedCallback() {
            @Override
            public void deregisterFailed() {
                Logger.e("Unable to deregister user after fatal protected manager error");
            }
        });
    }
}
