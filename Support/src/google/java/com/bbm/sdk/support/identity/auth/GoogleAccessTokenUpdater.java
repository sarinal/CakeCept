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


import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.GlobalAuthTokenState;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.outbound.AuthToken;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.support.util.Logger;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.api.GoogleApiClient;

/**
 * Manages the updating the AuthToken and informing BBM Core. Once started, by default this will be running unless
 * paused. It will not post UI to login a device user, that will be the responsibility of
 * the implementing UI.
 */
public final class GoogleAccessTokenUpdater implements GoogleAuthHelper.SilentSignResultCallback, GoogleGetAccessTokenTask.OnGetAccessTokenResult {
    private static GoogleAccessTokenUpdater mInstance;

    private static final int MAX_ATTEMPT = 5;
    private static final int DELAY = 15000;

    private boolean mForced;
    private int mAttemptCount = 0;

    private final Handler mHandler;
    private GoogleApiClient mGoogleApiClient;
    private Context mContext;
    private String mClientServerId;
    private boolean mClearToken;

    private GoogleAuthHelper.SilentSignResultCallback mExtraSilentSignResultCallback;

    //track if we have already requested sign in so that it is done at least once even if not needed for auth token
    private boolean mSignInRequested;

    private final ObservableValue<GlobalAuthTokenState> mAuthTokenState;

    private final Observer mAuthTokenStateObserver = new Observer() {

        @Override
        public void changed() {
            final GlobalAuthTokenState authTokenState = mAuthTokenState.get();

            GlobalAuthTokenState.State state = authTokenState.value;

            if(mForced) {
                state = GlobalAuthTokenState.State.Needed;
                mForced = false;
            } else {
                if (authTokenState.getExists() != Existence.YES) {
                    Logger.i("authTokenState exists="+authTokenState.getExists());
                    return;
                }
            }

            switch (state) {
                case Ok:
                    Logger.i("BBM SDK indicates auth token is ok!");
                    mAttemptCount = 0;
                    mClearToken = false;

                    if (mExtraSilentSignResultCallback != null && !mSignInRequested) {
                        //we have not yet requested sign in, we don't need it for auth, but the other callback might need it
                        //to sync with user management so request now, but only pass that callback and not this
                        start(mExtraSilentSignResultCallback);
                    }

                    break;
                case Needed: {
                    if (mAttemptCount < MAX_ATTEMPT) {
                        scheduleUpdate();
                        Logger.i("Need a new access token for BBM SDK");
                    } else {
                        Logger.user("Max attempt at token refresh hit. Could not update need user action.");
                    }
                    break;
                }
                case Rejected: {
                    if (mAttemptCount < MAX_ATTEMPT) {
                        mClearToken = true;
                        scheduleUpdate();
                        Logger.i("Token has been rejected. Need a new access token for BBM SDK");
                    } else {
                        Logger.user("Max attempt at token refresh hit. Could not update need user action.");
                    }
                    break;
                }
                case Unspecified:
                    Logger.user("Unknown auth state encountered");
                    break;
            }
        }
    };

    private GoogleAccessTokenUpdater(Context context, String clientServerId, GoogleAuthHelper.SilentSignResultCallback extraSilentSignResultCallback) {
        mContext = context;
        mExtraSilentSignResultCallback = extraSilentSignResultCallback;
        mClientServerId = clientServerId;
        mAuthTokenState = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalAuthTokenState();
        mHandler = new Handler(Looper.myLooper());
        resume();
    }

    /**
     *
     * @param context
     * @param clientServerId
     * @param silentSignResultCallback an optional callback to handle Google sign in success or failure.
     *                                 An app might want to specify an implementation that will either prompt the user
     *                                 to sign in on failure, or pass to user management on success
     */
    public static synchronized void createInstance(Context context, String clientServerId, GoogleAuthHelper.SilentSignResultCallback silentSignResultCallback) {
        mInstance = new GoogleAccessTokenUpdater(context, clientServerId, silentSignResultCallback);
    }

    public static GoogleAccessTokenUpdater getInstance() {
        return mInstance;
    }

    /**
     * @return the client server id set in the constructor.
     */
    public String getClientServerId() {
        return mClientServerId;
    }

    public GoogleApiClient getGoogleApiClient() {
        return mGoogleApiClient;
    }

    /**
     * Schedule an update attempt for new new access token. If the attempted
     * failed, schedule a new attempt in the future.
     */
    private void scheduleUpdate() {
        if (mAttemptCount == 0) {
            start(this);
        } else {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    start(GoogleAccessTokenUpdater.this);
                }
            }, DELAY * mAttemptCount);
        }

        mAttemptCount++;
    }

    /**
     * Starts the {@link GoogleApiClient} and attempts a silent sign-in. The results will
     * be trigger {@link GoogleAccessTokenUpdater#onFail()} or {@link GoogleAccessTokenUpdater#onSuccess(AuthToken)}.
     *
     * @param callback the callback to pass.  It should normally be this instance to handle the response
     *                 and call mExtraSilentSignResultCallback if needed, but can be mExtraSilentSignResultCallback if
     *                 the sign in is only being done for mExtraSilentSignResultCallback
     */
    private void start(GoogleAuthHelper.SilentSignResultCallback callback) {
        mGoogleApiClient = GoogleAuthHelper.getApiClient(mContext, mClientServerId);

        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }

        GoogleAuthHelper.handleSilentSignin(mGoogleApiClient, callback);
        mSignInRequested = true;
    }

    /**
     * Finishes & cleans up the {@link GoogleApiClient} that was used.
     */
    private void finish() {
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
            mGoogleApiClient = null;
        }
    }

    /**
     * Paused the automatic updating of the access token.
     */
    public void pause() {
        mAuthTokenState.removeObserver(mAuthTokenStateObserver);
        Logger.i("GoogleAccessTokenUpdater has been paused ");
    }

    /**
     * Resumes the automatic updating of the access token.
     */
    public void resume() {
        mAuthTokenState.addObserver(mAuthTokenStateObserver);
        Logger.i("GoogleAccessTokenUpdater has been resumed ");

        //reset the count and check the state right away
        mAttemptCount = 0;
        mClearToken = false;
        mAuthTokenStateObserver.changed();
    }

    /**
     * Helper to force a token update.
     */
    @SuppressWarnings("unused")
    public void forceUpdate() {
        mForced = true;
        mAuthTokenStateObserver.changed();
    }

    @Override
    public void onSilentSignResult(@NonNull GoogleSignInResult googleSignInResult) {
        Logger.d("Got new google sign in result");
        if (googleSignInResult.isSuccess()) {
            // Signed in successfully, show authenticated UI.
            GoogleSignInAccount acct = googleSignInResult.getSignInAccount();
            if (acct != null) {
                GoogleGetAccessTokenTask task = new GoogleGetAccessTokenTask(mContext, acct, this, mClearToken);
                task.execute();
            } else {
                Logger.w("Unable to fetch google account");
            }
        } else {
            Logger.w("Unable to sign-in google account");
        }

        if (mExtraSilentSignResultCallback != null) {
            mExtraSilentSignResultCallback.onSilentSignResult(googleSignInResult);
        }
    }

    @Override
    public void onSuccess(@NonNull AuthToken token) {
        finish();
        BBMEnterprise.getInstance().getBbmdsProtocol().send(token);
        mAuthTokenStateObserver.changed();
        Logger.i("Updated token sent to BBM SDK.");
    }

    @Override
    public void onFail() {
        finish();
        Logger.w("Unable to get access token");
    }
}
