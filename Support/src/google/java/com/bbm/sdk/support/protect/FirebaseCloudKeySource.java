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
import android.support.annotation.NonNull;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.service.BBMEnterpriseState;
import com.bbm.sdk.support.identity.user.FirebaseUserDbSync;
import com.bbm.sdk.support.protect.providers.FirebaseKeyStorageProvider;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;

public class FirebaseCloudKeySource extends CloudKeySource {

    private SingleshotMonitor mStartMonitor;

    public FirebaseCloudKeySource(@NonNull Context context, @NonNull PasscodeProvider passcodeProvider) {
        mStartMonitor = new SingleshotMonitor() {
            @Override
            public boolean runUntilTrue() {
                BBMEnterpriseState bbmeState = BBMEnterprise.getInstance().getState().get();
                if (bbmeState != BBMEnterpriseState.STARTED) {
                    return false;
                }
                //Once the FirebaseUser is available start the protected manager
                FirebaseUser fbUser = FirebaseUserDbSync.getInstance().getFireBaseUser().get();
                if (fbUser != null) {
                    String uid = "";
                    //Find the google UID for the local user from the firebase user data
                    for (UserInfo info : fbUser.getProviderData()) {
                        if (info.getProviderId().equals(GoogleAuthProvider.PROVIDER_ID)) {
                            uid = info.getUid();
                        }
                    }
                    //Initialize the ProtectedManager with our FirebaseKeyStorageProvider
                    FirebaseKeyStorageProvider fbKeyStorageProvider = new FirebaseKeyStorageProvider(fbUser, uid);
                    //Set a default KeyImportFailureListener.
                    ProtectedManager.getInstance().setKeyImportFailureListener(new DefaultKeyImportFailureListener(fbKeyStorageProvider));
                    //Start the ProtectedManager to provide keys for bbmcore
                    ProtectedManager.getInstance().start(
                            context,
                            uid,
                            fbKeyStorageProvider,
                            passcodeProvider,
                            new ProtectedManagerErrorHandler()
                    );
                    return true;
                }
                return false;
            }
        };
    }

    @Override
    public void start() {
        mStartMonitor.activate();
    }

    @Override
    public void stop() {
        mStartMonitor.dispose();
        super.stop();
    }
}
