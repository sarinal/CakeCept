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


import android.accounts.Account;
import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.bbm.sdk.bbmds.outbound.AuthToken;
import com.bbm.sdk.support.util.Logger;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import java.io.IOException;

/**
 * Helper task to fetch the access token from the provided {@link GoogleSignInAccount}. Once
 * fetched use the {@link OnGetAccessTokenResult} to obtain the results.
 */
public final class GoogleGetAccessTokenTask extends AsyncTask<Void, Void, String> {

    private final Context mContext;
    private final GoogleSignInAccount mSignInAccount;
    private final OnGetAccessTokenResult mCallback;
    private boolean mClearToken;

    public GoogleGetAccessTokenTask(@NonNull Context context,
                                    @NonNull final GoogleSignInAccount googleSigninAccount,
                                    final OnGetAccessTokenResult callback,
                                    boolean clearToken) {
        mContext = context.getApplicationContext();
        mSignInAccount = googleSigninAccount;
        mCallback = callback;
        mClearToken = clearToken;
    }

    @Override
    protected String doInBackground(Void... voids) {
        Account account = mSignInAccount.getAccount();
        String accessToken = null;
        try {
            if (mClearToken) {
                // The previous auth token has expired or been rejected, clear the current token.
                Logger.i("Clearing rejected auth token");
                GoogleAuthUtil.clearToken(mContext, "oauth2:openid");
            }
            if (account != null) {
                accessToken = GoogleAuthUtil.getToken(mContext, account, "oauth2:openid");
            } else {
                Logger.w("Could not get the user account from Google Sign-in");
            }
        } catch (IOException e) {
            Logger.e(e, "An IOException has occurred. Unable to get a access token");
        } catch (UserRecoverableAuthException e) {
            Logger.e(e, "Could not recover user. Unable to get access token");
            accessToken = null;
        } catch (GoogleAuthException e) {
            Logger.e(e, "Sign-in error. Unable to get access token");
        }

        return accessToken;
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        if(!TextUtils.isEmpty(result)) {
            // Have a access token, so lets create a new AuthToken and trigger
            // mAuthTokenStateObserver to use it. Just in case check if the
            // service is started.
            Logger.i("Non-Empty access token has been provided!");
            mCallback.onSuccess(new AuthToken(result, mSignInAccount.getId()));
        } else {
            Logger.w("Empty access token has been provided");
            mCallback.onFail();
        }
    }

    /**
     * Callback with the results of fetching the access token
     */
    public interface OnGetAccessTokenResult {

        /**
         * Called if a access token was granted. This will return the {@link AuthToken} message
         * @param token The token to be sent to BBM SDK core.
         */
        void onSuccess(@NonNull final AuthToken token);

        /**
         * Called if the access token was not granted.
         */
        void onFail();
    }
}
