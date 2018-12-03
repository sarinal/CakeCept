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

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.GlobalSetupState;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.service.BBMEnterpriseState;
import com.bbm.sdk.support.identity.auth.AzureAdAuthenticationManager;
import com.bbm.sdk.support.identity.user.AzureAdUserSync;

public class AuthIdentityHelper {

    public static final int TOKEN_REQUEST_CODE = 1001;

    /**
     * The identity management is tied to the AzureAdAuthenticationManager.
     * @param context the android application context
     */
    public static void initIdentity(Context context) {
        SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
            @Override
            public boolean run() {
                GlobalSetupState setupState = BBMEnterprise.getInstance().
                        getBbmdsProtocol().getGlobalSetupState().get();
                if (setupState.state == GlobalSetupState.State.Success) {
                    AzureAdUserSync.getInstance().start();
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * The identity management is tied to the AzureAdAuthenticationManager.
     */
    public static void stopIdentityProvider() {
        AzureAdUserSync.getInstance().stop();
    }


    /**
     * Handle an 'EndpointDeregistered' event from the BBM Enterprise SDK
     * When called the AzureAdAuthenticationManager will clear saved tokens.
     * @param context android context
     */
    public static void handleEndpointDeregistered(Context context) {
        //Stop the identity provider
        stopIdentityProvider();
        if (KeySourceManager.getKeySource() != null) {
            KeySourceManager.getKeySource().stop();
        }
        AzureAdAuthenticationManager.getInstance().handleEndpointDeregistered(context);

        //Restart the BBME SDK once it has stopped
        SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
            @Override
            public boolean run() {
                if (BBMEnterprise.getInstance().getState().get() == BBMEnterpriseState.STOPPED) {
                    //Restart BBME
                    BBMEnterprise.getInstance().start();
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * Set the activity context in the AzureAdAuthenticationManager.
     * If an auth token is needed but could be retrieved silently this will prompt the user to provide credentials.
     * @param activity android activity context
     */
    public static void setActivity(Activity activity) {
        AzureAdAuthenticationManager.getInstance().setActivity(activity);
    }

    /**
     * Handle return from onActivityResult if the user was prompted for credentials.
     * @param context android context
     * @param requestCode the code provided in the intent
     * @param resultCode the result code ({@link Activity#RESULT_OK} on success)
     * @param data the result data
     */
    public static void handleAuthenticationResult(Context context, int requestCode, int resultCode, Intent data) {
        AzureAdAuthenticationManager.getInstance().handleOnActivityResult(requestCode, resultCode, data);
    }

}
