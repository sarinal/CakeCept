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

package com.bbm.sdk.support.identity.user;


import android.support.annotation.NonNull;

import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.reactive.ObservableMonitor;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.support.identity.UserIdentityMapper;
import com.bbm.sdk.support.identity.auth.AzureAdAuthenticationManager;
import com.bbm.sdk.support.util.BbmUtils;
import com.bbm.sdk.support.util.Logger;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.concurrency.ICallback;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.core.DefaultClientConfig;
import com.microsoft.graph.core.IClientConfig;
import com.microsoft.graph.extensions.GraphServiceClient;
import com.microsoft.graph.extensions.IGraphServiceClient;
import com.microsoft.graph.extensions.IUserCollectionPage;
import com.microsoft.graph.extensions.User;
import com.microsoft.graph.http.IHttpRequest;
import com.microsoft.identity.client.AuthenticationResult;

import java.util.List;

/**
 * Handles syncing the user list from the Azure Active Directory
 * and syncing the local users registration id into the AD.
 */
public class AzureAdUserSync extends AppSourceNotifier {

    private static AzureAdUserSync sInstance;

    /**
     * Observe the local user and sync any time it changes
     */
    private final ObservableMonitor mLocalBbmUserObserver = new ObservableMonitor() {
        @Override
        protected void run() {
            com.bbm.sdk.bbmds.User localUser = BbmUtils.getLocalBbmUser().get();
            if (localUser.exists == Existence.YES && localUser.regId != 0) {
                syncLocalUser(localUser);
            }
        }
    };

    /**
     * Get the AzureAdUserSync instance
     * @return instance of AzureAdUserSync
     */
    public static synchronized AzureAdUserSync getInstance() {
        if (sInstance == null) {
            sInstance = new AzureAdUserSync();
        }
        return sInstance;
    }

    /**
     * Start the AzureAdUserSync
     */
    public void start() {
        //connect the user manager to our azure user DB implementation
        addListener(UserManager.getInstance());
        updateUsers();
    }

    /**
     * Stop the AzureAdUserSync
     */
    public void stop() {
        removeListener(UserManager.getInstance());
        mLocalBbmUserObserver.dispose();
    }

    /**
     * Creates an instance of IGraphServiceClient that can be used to make requests to retrieve and update users from the directory.
     * @return instance of IGraphServiceClient
     */
    private synchronized IGraphServiceClient getGraphServiceClient(AuthenticationResult authResult) {
        IClientConfig clientConfig = DefaultClientConfig.createWithAuthenticationProvider(new IAuthenticationProvider() {
            @Override
            public void authenticateRequest(IHttpRequest request) {
                //Add the token and uid to the authentication header
                request.addHeader("Authorization", "Bearer "
                        + authResult.getAccessToken());

                Logger.d("Request: " + request.toString());
            }
        });
        return new GraphServiceClient.Builder().fromConfig(clientConfig).buildClient();
    }

    /**
     * Fetch the user list and update the values stored for the local user
     */
    private void updateUsers() {
        mLocalBbmUserObserver.activate();
        syncUsersList();
    }

    /**
     * Update the stored registration id for the local user
     * @param localUser the local bbmds user obtained from bbmcore
     */
    private void syncLocalUser(com.bbm.sdk.bbmds.User localUser) {
        AzureAdAuthenticationManager.getInstance().getGraphAccessToken(new AzureAdAuthenticationManager.TokenCallback() {
            @Override
            public void onToken(AuthenticationResult authResult) {
                IGraphServiceClient graphServiceClient = getGraphServiceClient(authResult);
                //Request our own user
                graphServiceClient.getMe().buildRequest().get(new ICallback<User>() {
                    @Override
                    public void success(com.microsoft.graph.extensions.User msGraphUser) {

                        //Update our own user information in the UserManager
                        final AppUser appUser = new AppUser(localUser, msGraphUser.id, msGraphUser.displayName, msGraphUser.mail, null);
                        appUser.setExists(Existence.YES);
                        notifyAppUserListeners(EventToNotify.LOCAL_UPDATED, appUser);
                    }

                    @Override
                    public void failure(ClientException ex) {
                        Logger.e(ex,"Failure to read local user (me) from MicrosoftGraph");
                    }
                });
            }
        }, false);
    }

    /**
     * Sync the active directory user list with the UserManager.
     * Any user which has a registration id is included.
     */
    private void syncUsersList() {
        AzureAdAuthenticationManager.getInstance().getGraphAccessToken(new AzureAdAuthenticationManager.TokenCallback() {
            @Override
            public void onToken(AuthenticationResult authResult) {
                //Build a request to get the extensions for all users
                getGraphServiceClient(authResult).getUsers().buildRequest().get(new ICallback<IUserCollectionPage>() {
                    @Override
                    public void success(IUserCollectionPage iUserCollectionPage) {
                        List<User> users = iUserCollectionPage.getCurrentPage();

                        //For each user
                        for (com.microsoft.graph.extensions.User user : users) {
                            //Check if the uid is ourselves (ignore)
                            if (!user.id.equals(UserManager.getInstance().getLocalAppUser().get().getUid())) {
                                //Add the user to the UserManager
                                SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
                                    @Override
                                    public boolean run() {
                                        UserIdentityMapper.IdentityMapResult result =
                                                UserIdentityMapper.getInstance().getRegIdForUid(user.id, false).get();
                                        if (result.existence == Existence.MAYBE) {
                                            return false;
                                        }
                                        if (result.existence == Existence.YES) {
                                            AppUser appUser = new AppUser(result.regId, user.id, user.displayName, user.mail, "");
                                            appUser.setExists(Existence.YES);
                                            Logger.d("Add user " + appUser.toString());
                                            notifyAppUserListeners(EventToNotify.ADD, appUser);
                                        }
                                        return true;
                                    }
                                });
                            }
                        }
                    }

                    @Override
                    public void failure(ClientException ex) {
                        Logger.e(ex,"Failure to read user list from MicrosoftGraph");
                    }
                });
            }
        }, false);
    }

    @Override
    public void requestUser(@NonNull String uid) {
        AzureAdAuthenticationManager.getInstance().getGraphAccessToken(new AzureAdAuthenticationManager.TokenCallback() {
            @Override
            public void onToken(AuthenticationResult authResult) {
                //Build a request to get the extensions for all users
                getGraphServiceClient(authResult).getUsers(uid).buildRequest().get(new ICallback<User>() {
                    @Override
                    public void success(User user) {

                        //Check if the uid is ourselves (ignore)
                        if (!user.id.equals(UserManager.getInstance().getLocalAppUser().get().getUid())) {
                            //Add the user to the UserManager
                            SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
                                @Override
                                public boolean run() {
                                    UserIdentityMapper.IdentityMapResult result =
                                            UserIdentityMapper.getInstance().getRegIdForUid(user.id, false).get();
                                    if (result.existence == Existence.MAYBE) {
                                        return false;
                                    }
                                    if (result.existence == Existence.YES) {
                                        AppUser appUser = new AppUser(result.regId, user.id, user.displayName, user.mail, "");
                                        appUser.setExists(Existence.YES);
                                        Logger.d("Add user " + appUser.toString());
                                        notifyAppUserListeners(EventToNotify.ADD, appUser);
                                    }
                                    return true;
                                }
                            });
                        }
                    }

                    @Override
                    public void failure(ClientException ex) {
                        Logger.e(ex,"Failure to read user " + uid);
                    }
                });
            }
        }, false);
    }
}