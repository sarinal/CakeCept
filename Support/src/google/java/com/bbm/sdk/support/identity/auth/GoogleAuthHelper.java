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

package com.bbm.sdk.support.identity.auth;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.outbound.AuthToken;
import com.bbm.sdk.support.util.Logger;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResultCallback;

import java.lang.ref.WeakReference;

public class GoogleAuthHelper {
    public static final int RC_GOOGLE_SIGN_IN_ACTIVITY = 9001;

    public interface SilentSignResultCallback {
        void onSilentSignResult(@NonNull GoogleSignInResult googleSignInResult);
    }

    /**
     * Google sign in needs an activity to prompt the user to sign in, but the init could start from the app without an activity
     * so we keep the reference if available.
     * Only keep as a weak reference to avoid potential memory leak.
     */
    private static WeakReference<Activity> sActivity = new WeakReference(null);

    /**
     * Remember if google needs to prompt user to sign in before we have the activity.
     */
    private static boolean sNeedToPromptUserSignIn;

    /**
     * The application must call this as soon as an activity is loaded if you want to be able
     * to handle google sign in prompting the user.
     * <p>
     * The specified activity must implement onActivityResult() and pass parameters to
     * handleOnActivityResult() when requestCode is RC_GOOGLE_SIGN_IN_ACTIVITY}
     *
     * @param activity
     */
    public static synchronized void setActivity(Activity activity) {
        Logger.d("setActivity: old=" + sActivity.get() + " new=" + activity);

        sActivity = new WeakReference(activity);

        if (sNeedToPromptUserSignIn && activity != null) {
            Logger.d("setActivity: will prompt user to sign in activity=" + activity);
            Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(GoogleAccessTokenUpdater.getInstance().getGoogleApiClient());
            activity.startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN_ACTIVITY);
        }
    }

    /**
     * Init google sign in.
     * <p>
     * If context is not an activity then you must call setActivity() as soon as an activity is loaded if you want to be able
     * to handle google sign in prompting the user.
     */
    public static void initGoogleSignIn(final Context context, final LocalAuthDataListener localUserUpdatedListener, String clientServerId) {
        Logger.d("initGoogleSignIn: context=" + context);
        if (context instanceof Activity) {
            setActivity((Activity) context);
        }

        //start the Google sign in so it will get the auth token from Google and send to BBM
        if (GoogleAccessTokenUpdater.getInstance() == null) {
            GoogleAccessTokenUpdater.createInstance(context, clientServerId, new GoogleAuthHelper.SilentSignResultCallback() {
                @Override
                public void onSilentSignResult(@NonNull GoogleSignInResult googleSignInResult) {
                    GoogleSignInAccount acct = googleSignInResult.getSignInAccount();
                    if (googleSignInResult.isSuccess() && acct != null) {
                        //pass to our user management
                        String avatarUrl = acct.getPhotoUrl() != null ? acct.getPhotoUrl().toString() : null;
                        Logger.d("onSilentSignResult: will update UserManager");
                        AuthenticatedAccountData accountData = new AuthenticatedAccountData(acct.getId(), acct.getIdToken(),
                                acct.getDisplayName(), acct.getEmail(), avatarUrl);
                        localUserUpdatedListener.localAuthDataChanged(accountData);
                    } else {
                        Activity activity = sActivity.get();
                        if (activity != null) {
                            Logger.d("onSilentSignResult: will prompt user to sign in activity=" + activity);
                            Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(GoogleAccessTokenUpdater.getInstance().getGoogleApiClient());
                            activity.startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN_ACTIVITY);
                        } else {
                            Logger.d("onSilentSignResult: activity is NULL will prompt user to sign in later when/if activity is set...");
                            sNeedToPromptUserSignIn = true;
                        }
                    }
                }
            });
        } else {
            //Force retry token sync
            GoogleAccessTokenUpdater.getInstance().forceUpdate();
        }
    }

    public static void handleOnActivityResult(final Context context, final LocalAuthDataListener localUserUpdatedListener, int requestCode, int resultCode, Intent data) {
        //handle response from Google sign in
        if (requestCode == RC_GOOGLE_SIGN_IN_ACTIVITY) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);

            final GoogleSignInAccount acct = result.getSignInAccount();
            if (result.isSuccess() && acct != null) {
                Logger.d("handleOnActivityResult: success signing into google account, Google result :" + result);
                String avatarUrl = acct.getPhotoUrl() != null ? acct.getPhotoUrl().toString() : null;
                AuthenticatedAccountData accountData = new AuthenticatedAccountData(acct.getId(), acct.getIdToken(),
                        acct.getDisplayName(), acct.getEmail(), avatarUrl);
                localUserUpdatedListener.localAuthDataChanged(accountData);

                GoogleGetAccessTokenTask task = new GoogleGetAccessTokenTask(context, acct, new GoogleGetAccessTokenTask.OnGetAccessTokenResult() {
                    @Override
                    public void onSuccess(@NonNull AuthToken token) {
                        Logger.i("onSuccess: have AuthToken=" + token + " from google sign in");

                        BBMEnterprise.getInstance().getBbmdsProtocol().send(token);
                    }

                    @Override
                    public void onFail() {
                        Logger.user("Failed to get access token from google account=" + acct);
                    }
                }, false);
                task.execute();
            } else {
                Logger.user("FAILED signing into google account, Google result status=" + result.getStatus() + " acct=" + acct);
            }
        }
    }

    /**
     * Helper to get a GoogleApiClient to be used for sign-in
     *
     * @param context A non-null context that can be used.
     * @return {@link GoogleApiClient}
     */
    public static GoogleApiClient getApiClient(@NonNull final Context context, String clientServerId) {
        // Configure sign-in to request the user's ID, email address, and its Id token.
        // ID and basic profile are included in DEFAULT_SIGN_IN.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(clientServerId)
                .requestEmail()
                .build();

        // Build a GoogleApiClient with access to the Google Sign-In API and the
        // options specified by gso.
        return new GoogleApiClient.Builder(context.getApplicationContext())
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();
    }

    /**
     * Helper to silently sign-in using Google Services. As per {@link GoogleApiClient} this will attempt to
     * reuse an existing sign-in. Once sign-in is complete the {@link SilentSignResultCallback} will be called.
     *
     * @param apiClient A valid google api client that can be used.
     * @param callback  The callback to be used to notify of the sign-in result when completed.
     */
    public static void handleSilentSignin(@NonNull final GoogleApiClient apiClient, @NonNull final SilentSignResultCallback callback) {
        Logger.i("GoogleAuthHelper perform silentSignIn");
        OptionalPendingResult<GoogleSignInResult> opr = Auth.GoogleSignInApi.silentSignIn(apiClient);
        if (opr.isDone()) {
            // If the user's cached credentials are valid, the OptionalPendingResult will be "done"
            // and the GoogleSignInResult will be available instantly.
            GoogleSignInResult result = opr.get();
            callback.onSilentSignResult(result);
        } else {
            // If the user has not previously signed in on this device or the sign-in has expired,
            // this asynchronous branch will attempt to sign in the user silently.  Cross-device
            // single sign-on will occur in this branch.
            opr.setResultCallback(new ResultCallback<GoogleSignInResult>() {
                @Override
                public void onResult(@NonNull GoogleSignInResult googleSignInResult) {
                    callback.onSilentSignResult(googleSignInResult);
                }
            });
        }
    }
}
