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

package com.bbm.sdk.support.identity.auth;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.GlobalAuthTokenState;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.outbound.AuthToken;
import com.bbm.sdk.reactive.Mutable;
import com.bbm.sdk.reactive.ObservableMonitor;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.support.util.Logger;
import com.microsoft.identity.client.AuthenticationCallback;
import com.microsoft.identity.client.AuthenticationResult;
import com.microsoft.identity.client.MsalClientException;
import com.microsoft.identity.client.MsalException;
import com.microsoft.identity.client.PublicClientApplication;
import com.microsoft.identity.client.User;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Handles authenticating users with an Azure AD SDK and providing the authentication token to the BBM Enterprise SDK.
 */
public class AzureAdAuthenticationManager {

    private static final String ACTIVE_DIRECTORY_CURRENT_USER_ID = "ad_current_user";
    private static final String TOKEN_USER_ID = "token_user_id";
    private static final String[] MS_GRAPH_SCOPES = {"User.ReadWrite", "User.ReadBasic.All"};

    private PublicClientApplication mPublicClientApplication;
    private static AzureAdAuthenticationManager sInstance;
    private SharedPreferences mPreferences;
    private User mUser;
    private Mutable<String> mUserUid = new Mutable<>("");
    private WeakReference<Activity> mActivity = new WeakReference<>(null);
    private String mAdTenantId;
    private String mBbmeAuthScope;
    private String mLoginAuthority;
    private boolean mTokenRequestInProgress = false;
    private boolean mNeedToPromptUserSignIn = false;

    private ArrayList<TokenCallback> mBBMESDKScopeTokenListeners = new ArrayList<>();
    private ArrayList<TokenCallback> mGraphServicesTokenListeners = new ArrayList<>();

    public interface TokenCallback {
        void onToken(AuthenticationResult authResult);
    }

    /**
     * Monitor the {@link GlobalAuthTokenState} and trigger requests for tokens when necessary
     */
    private final ObservableMonitor mAuthTokenStateObserver = new ObservableMonitor() {

        @Override
        public void run() {
            final GlobalAuthTokenState authTokenState = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalAuthTokenState().get();

            GlobalAuthTokenState.State state = authTokenState.value;

            if (authTokenState.getExists() != Existence.YES) {
                Logger.i("authTokenState exists=" + authTokenState.getExists());
                return;
            }

            boolean forceRefresh = false;
            switch (state) {
                case Ok:
                    Logger.i("BBM SDK indicates auth token is ok!");
                    break;
                case Rejected:
                    forceRefresh = true;
                    //Fall through
                case Needed: {
                    Logger.i("Request token");
                    requestTokenForBBMEnterpriseSDK(forceRefresh);
                    //Also refresh the user list
                    requestTokenForUserSync();
                    break;
                }
                case Unspecified:
                    Logger.user("Unknown auth state encountered");
                    break;
            }
        }
    };

    /**
     * Create a new instance of the AzureAdAuthenticationManager
     * @param context android application context
     * @param adTenantId the active directory tenant id
     * @param bbmeAuthScope the custom authentication scope created for the BBM Enterprise SDK
     * @param loginAuthority the login authority url to authenticate with (ex. https://login.microsoftonline.com/)
     */
    private AzureAdAuthenticationManager(Context context, String adTenantId, String bbmeAuthScope, String loginAuthority) {
        mAdTenantId = adTenantId;
        mBbmeAuthScope = bbmeAuthScope;
        mLoginAuthority = loginAuthority;
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String userId = mPreferences.getString(ACTIVE_DIRECTORY_CURRENT_USER_ID, null);
        String userUid = mPreferences.getString(TOKEN_USER_ID, null);
        if (userUid != null) {
            mUserUid.set(userUid);
        }
        mPublicClientApplication = new PublicClientApplication(context);

        //If we previously logged in we should have a user id saved
        try {
            mUser = userId != null ? mPublicClientApplication.getUser(userId) : null;
        } catch (MsalClientException e) {
            Logger.e(e, "MSAL Exception Generated while user: " + e.toString());
        }

        mAuthTokenStateObserver.activate();
        //Force a sync of the user list
        if (mUser != null) {
            requestTokenForUserSync();
        }
    }

    /**
     * Request a token and pass the result to the AzureAdSync instance
     */
    private void requestTokenForUserSync() {
        getGraphAccessToken(new TokenCallback() {
            @Override
            public void onToken(AuthenticationResult result) {
                //We don't really need this data (we will get the name, email, etc through the graph services SDK)
                //This is just to match the API setup for Firebase.
                AuthenticatedAccountData accountData = new AuthenticatedAccountData(
                        result.getUniqueId(),
                        result.getAccessToken(),
                        result.getUser().getName(),
                        "",
                        ""
                );
            }
        }, false);
    }

    /**
     * Request and provide a token to bbmcore to authenticate with the BBM Enterprise SDK
     * @param forceTokenRefresh true if the token should be force refreshed (don't use any cached values)
     */
    private void requestTokenForBBMEnterpriseSDK(boolean forceTokenRefresh) {
        getBBMScopeToken(new TokenCallback() {
            @Override
            public void onToken(AuthenticationResult result) {
                SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
                    @Override
                    public boolean run() {
                        final GlobalAuthTokenState authTokenState = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalAuthTokenState().get();
                        if (authTokenState.exists == Existence.MAYBE) {
                            return false;
                        }
                        if (authTokenState.value != GlobalAuthTokenState.State.Ok) {
                            //Only pass the token value to the BBM SDK if its required.
                            BBMEnterprise.getInstance().getBbmdsProtocol().send(new AuthToken(result.getAccessToken(), result.getUniqueId()));
                        }
                        return true;
                    }
                });
            }
        }, forceTokenRefresh);
    }

    /**
     * Create a Scope for BBME SDK authentication
     */
    private String[] getBBMESdkScope() {
        return new String[]{mBbmeAuthScope};
    }

    /**
     * Get the authority URL for requesting tokens
     */
    private String getAuthorityUrl() {
        return mLoginAuthority + mAdTenantId;
    }

    /**
     * @return the user identifier saved from an authentication token.
     */
    public Mutable<String> getUserIdentifier() {
        return mUserUid;
    }

    /**
     * Request a token from the MSAL library
     * If we have a cached 'user' we can attempt to request the token silently.
     * @param callback called with the authentication result
     * @param scope the scopes that should be included in the token
     * @param forceRefresh true if the token should be force refreshed ignoring any cached values
     * @param forceLogin true if the user should be forced to provide credentials
     */
    private void requestToken(AuthenticationCallback callback, String[] scope, boolean forceRefresh, boolean forceLogin) {
        if (mUser != null && !forceLogin) {
            //Perform a silent token request
            callAcquireTokenSilent(mUser, callback, scope, forceRefresh);
        } else {
            if (mActivity != null && mActivity.get() != null) {
                //Do an interactive token request (prompt user)
                mPublicClientApplication.acquireToken(
                        mActivity.get(),
                        scope,
                        "",
                        null,
                        "",
                        null,
                        getAuthorityUrl(),
                        callback
                );
            } else {
                mNeedToPromptUserSignIn = true;
            }
        }
    }

    /**
     * If available return an access token to be used when making requests to the MicrosoftGraph SDK.
     *
     * @param tokenCallback the callback will be notified when the token is obtained
     * @param forceRefresh true if the token should be force refreshed ignoring any cached values
     */
    public synchronized void getGraphAccessToken(TokenCallback tokenCallback, boolean forceRefresh) {
        mGraphServicesTokenListeners.add(tokenCallback);
        //If we already have a token request in progress then don't create another
        if (!mTokenRequestInProgress) {
            mTokenRequestInProgress = true;
            requestToken(getMsGraphAuthCallback(), MS_GRAPH_SCOPES, forceRefresh, false);
        }
    }

    /**
     * If available return an access token using the BBME SDK scope
     *
     * @param tokenCallback the callback will be notified when the token is obtained
     * @param forceRefresh true if the token should be force refreshed ignoring any cached values
     */
    public synchronized void getBBMScopeToken(TokenCallback tokenCallback, boolean forceRefresh) {
        mBBMESDKScopeTokenListeners.add(tokenCallback);
        if (!mTokenRequestInProgress) {
            //If we already have a token request in progress then don't create another
            mTokenRequestInProgress = true;
            requestToken(getBbmeSdkAuthCallback(), getBBMESdkScope(), forceRefresh, false);
        }
    }

    /**
     * Initialize this auth provider. Start an observer to monitor the {@link GlobalAuthTokenState}.
     *
     * @param context android context
     * @param adTenantId The tenant id of the active directory instance being used for user authentication
     * @param bbmeAuthScope the scope provided in the authentication token matching the scope configured in the BBM Enterprise SDK domain
     * @param loginAuthority the login authority url to authenticate with (ex. https://login.microsoftonline.com/)
     */
    public static synchronized void initialize(@NonNull Context context,
                                               @NonNull String adTenantId,
                                               @NonNull String bbmeAuthScope,
                                               @NonNull String loginAuthority) {
        if (sInstance == null) {
            sInstance = new AzureAdAuthenticationManager(context, adTenantId, bbmeAuthScope, loginAuthority);
        } else {
            Logger.d("The AzureAdAuthenticationManager has already been initialized");
        }
    }

    /**
     * Get the AzureAuthenticationManager instance which can be used to request auth tokens.
     *
     * @return the AzureAuthenticationManager instance.
     * @throws IllegalStateException if the {@link #initialize(Context, String, String, String)} method has not been called
     */
    public static AzureAdAuthenticationManager getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("AzureAdAuthenticationManager must be initialized");
        }
        return sInstance;
    }

    /**
     * If an auth token is required trigger a request for a token and allow prompting the user for credentials.
     *
     * @param activity the active activity
     */
    public void setActivity(Activity activity) {
        mActivity = new WeakReference<>(activity);
        if (mNeedToPromptUserSignIn) {
            //Always get a BBME SDK Token and then if necessary we will also get a graph services token
            requestToken(getBbmeSdkAuthCallback(), getBBMESdkScope(), false, false);
            mNeedToPromptUserSignIn = false;
        }
    }

    /**
     * Handle a result for an authentication request.
     *
     * @param requestCode request code provided in the outgoing intent
     * @param resultCode  the result ({@link Activity#RESULT_OK} on success)
     * @param data        the intent data containing the response
     */
    public void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        mPublicClientApplication.handleInteractiveRequestRedirect(requestCode, resultCode, data);
    }

    /**
     * Handle an 'EndpointDeregistered' message from the BBM Enterprise SDK
     */
    public void handleEndpointDeregistered(Context context) {
        disconnect(context);
    }

    /**
     * Clear saved tokens
     */
    @SuppressLint("ApplySharedPref")
    private void disconnect(Context context) {
        if (mUser != null) {
            mPublicClientApplication.remove(mUser);
            mPublicClientApplication = new PublicClientApplication(context);
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().remove(ACTIVE_DIRECTORY_CURRENT_USER_ID).apply();
        preferences.edit().remove(TOKEN_USER_ID).apply();
        preferences.edit().commit();
        mUser = null;
        mGraphServicesTokenListeners.clear();
        mTokenRequestInProgress = false;
        mBBMESDKScopeTokenListeners.clear();
    }

    /**
     * Attempt to acquire an auth token silently,
     * if a token cannot be obtained the user must be prompted for credentials via callAcquireToken.
     *
     * @param user the current user
     * @param callback an AuthenticationCallback to be notified of the token request result
     * @param scopes the scopes to be included in the token request
     * @param forceRefresh true if a new token should be fetched (ignore any cached token)
     */
    public void callAcquireTokenSilent(User user, AuthenticationCallback callback, String[] scopes, boolean forceRefresh) {
        mPublicClientApplication.acquireTokenSilentAsync(
                scopes,
                user,
                getAuthorityUrl(),
                forceRefresh,
                callback
        );
    }

    /**
     * Callback method for acquireToken calls.
     * If a token is retrieved it will be provided to the BBM Enterprise SDK.
     */
    private AuthenticationCallback getBbmeSdkAuthCallback() {
        return new AuthenticationCallback() {
            @Override
            public void onSuccess(AuthenticationResult authenticationResult) {
                /* Successfully got a token, call Graph now */
                Logger.d("Retreived BBME SDK Authentication Token");
                notifyTokenListener(true, authenticationResult, mBBMESDKScopeTokenListeners);
                mUser = authenticationResult.getUser();
                //Save the user identifier
                mPreferences.edit().putString(ACTIVE_DIRECTORY_CURRENT_USER_ID, authenticationResult.getUser().getUserIdentifier()).apply();
                mPreferences.edit().putString(TOKEN_USER_ID, authenticationResult.getUniqueId()).apply();
                if (mGraphServicesTokenListeners.size() > 0) {
                    requestToken(getMsGraphAuthCallback(), MS_GRAPH_SCOPES, false, false);
                } else {
                    mTokenRequestInProgress = false;
                }
            }

            @Override
            public void onError(MsalException exception) {
                //Could not acquire token
                Logger.e(exception, "BBM Scope Token Authentication failed");
                //Reset the user to null and prompt for token again, this will force credentials to be requested from the user
                mUser = null;
                requestToken(getBbmeSdkAuthCallback(), getBBMESdkScope(), true, true);
            }

            @Override
            public void onCancel() {
                //Authentication request was cancelled.
                mTokenRequestInProgress = false;
                Logger.i("User cancelled login.");
            }
        };
    }

    /**
     * Callback method for aquiring an MS Graph token.
     * If a token is retrieved it will trigger us to sync the local user and contact list from the active directory
     */
    private AuthenticationCallback getMsGraphAuthCallback() {
        return new AuthenticationCallback() {
            @Override
            public void onSuccess(AuthenticationResult authenticationResult) {
                /* Successfully got a token, call Graph now */
                Logger.d("Retreived MSGraph Authentication Token");
                notifyTokenListener(false, authenticationResult, mGraphServicesTokenListeners);
                mUser = authenticationResult.getUser();
                //Save the user identifier (which is a combination of the uid and some other value)
                mPreferences.edit().putString(ACTIVE_DIRECTORY_CURRENT_USER_ID, authenticationResult.getUser().getUserIdentifier()).apply();
                //Save just the user UID
                mPreferences.edit().putString(TOKEN_USER_ID, authenticationResult.getUniqueId()).apply();
                mUserUid.set(authenticationResult.getUniqueId());
                if (mBBMESDKScopeTokenListeners.size() > 0) {
                    requestToken(getBbmeSdkAuthCallback(), getBBMESdkScope(), false, false);
                } else {
                    mTokenRequestInProgress = false;
                }
            }

            @Override
            public void onError(MsalException exception) {
                //Could not acquire token
                Logger.e(exception, "MS Graph Token Scope Authentication failed");
                //Request a new token (still silently, if this fails we will prompt the user for credentials)
                requestToken(getMsGraphAuthCallback(), MS_GRAPH_SCOPES, true, true);
            }

            @Override
            public void onCancel() {
                //Authentication request was cancelled.
                mTokenRequestInProgress = false;
                Logger.i("User cancelled login.");
            }
        };
    }

    /**
     * Notify token listeners of
     */
    private synchronized void notifyTokenListener(boolean bbmToken, AuthenticationResult result, ArrayList<TokenCallback> tokenCallbacks) {
        for (TokenCallback callback : tokenCallbacks) {
            callback.onToken(result);
        }
        //After informing the token callbacks they can be removed
        tokenCallbacks.clear();
    }
}