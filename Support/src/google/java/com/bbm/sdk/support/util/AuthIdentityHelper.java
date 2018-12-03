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
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.GlobalSetupState;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.service.BBMEnterpriseState;
import com.bbm.sdk.support.identity.auth.GoogleAccessTokenUpdater;
import com.bbm.sdk.support.identity.auth.GoogleAuthHelper;
import com.bbm.sdk.support.identity.user.FirebaseUserDbSync;
import com.bbm.sdk.support.identity.user.UserManager;
import com.bbm.sdk.support.protect.KeySource;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

/**
 * Provides access to the Firebase identity and implementation.
 */
public class AuthIdentityHelper {

    public static final int TOKEN_REQUEST_CODE = GoogleAuthHelper.RC_GOOGLE_SIGN_IN_ACTIVITY;

    /**
     * Initialize the Firebase services (user sync, protected) including requesting an FCM push token
     */
    public static void initIdentity(Context context) {
        SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
            @Override
            public boolean run() {
                //Wait until we have completed setup to sync the user list.
                //This ensures that we have public keys registered in the cloud key storage before adding our user entry.
                GlobalSetupState setupState = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalSetupState().get();
                if (setupState.state == GlobalSetupState.State.Success) {
                    //connect the user manager to our firebase user DB implementation before starting it
                    FirebaseUserDbSync.getInstance().addListener(UserManager.getInstance());
                    //start sync with firebase DB so our local user account is sent to it, and we get all other appUsers
                    FirebaseUserDbSync.getInstance().initialize();
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * Stop the Firebase identity services
     */
    public static void stopIdentityProvider() {
        // Stop Firebase.
        // Remove the listener
        FirebaseUserDbSync.getInstance().removeListener(UserManager.getInstance());

        // Clear the User Manager.
        UserManager.getInstance().clear();

        // Stop the DB.
        FirebaseUserDbSync.getInstance().stop();
    }

    /**
     * Handle an 'EndpointDeregistered' event from the BBM Enterprise SDK
     * When called the GoogleAuthHelper will trigger a signout event and then restart the BBME SDK
     * @param context android context
     */
    public static void handleEndpointDeregistered(Context context) {
        //Stop the identity provider
        stopIdentityProvider();

        KeySource activeKeySource = KeySourceManager.getKeySource();
        if (activeKeySource != null) {
            activeKeySource.stop();
        }

        // Log the google account out of the app.
        final GoogleApiClient client = GoogleAuthHelper.getApiClient(context, GoogleAccessTokenUpdater.getInstance().getClientServerId());
        client.connect();
        client.registerConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
            @Override
            public void onConnected(@Nullable Bundle bundle) {

                // Revoke access from user account to the app.
                Auth.GoogleSignInApi.revokeAccess(client).setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {

                        // Now sign out
                        Auth.GoogleSignInApi.signOut(client).setResultCallback(
                                new ResultCallback<Status>() {
                                    @Override
                                    public void onResult(@NonNull Status status) {
                                        client.disconnect();

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
                                });
                    }
                });
            }

            @Override
            public void onConnectionSuspended(int i) {

            }
        });
    }

    /**
     * Set the activity context in the GoogleAuthHelper.
     * If an auth token is needed but could be retrieved silently this will prompt the user to provide credentials.
     * @param activity android activity context
     */
    public static void setActivity(Activity activity) {
        GoogleAuthHelper.setActivity(activity);
    }

    /**
     * Handle return from onActivityResult if the user was prompted for credentials.
     * @param context android context
     * @param requestCode the code provided in the intent
     * @param resultCode the result code ({@link Activity#RESULT_OK} on success)
     * @param data the result data
     */
    public static void handleAuthenticationResult(Context context, int requestCode, int resultCode, Intent data) {
        GoogleAuthHelper.handleOnActivityResult(context, FirebaseUserDbSync.getInstance(), requestCode, resultCode, data);
    }
}
