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
import android.text.TextUtils;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.service.BBMEnterpriseState;
import com.bbm.sdk.support.identity.auth.AzureAdAuthenticationManager;
import com.bbm.sdk.support.protect.providers.AzureKeyStorageProvider;

public class AzureCloudKeySource extends CloudKeySource {

    private SingleshotMonitor mStartMonitor;

    public AzureCloudKeySource(@NonNull Context context,
                               @NonNull String azureKeyProviderServerUrl,
                               @NonNull PasscodeProvider passcodeProvider) {
        mStartMonitor = new SingleshotMonitor() {
            @Override
            protected boolean runUntilTrue() {
                String uid = AzureAdAuthenticationManager.getInstance().getUserIdentifier().get();
                if (!TextUtils.isEmpty(uid) && BBMEnterprise.getInstance().getState().get() == BBMEnterpriseState.STARTED) {
                    AzureKeyStorageProvider azureKeyStorageProvider = new AzureKeyStorageProvider(azureKeyProviderServerUrl);
                    //Set a default KeyImportFailureListener.
                    ProtectedManager.getInstance().setKeyImportFailureListener(new DefaultKeyImportFailureListener(azureKeyStorageProvider));
                    //Once we know the UID for the user trigger the protected manager to start syncing their keys
                    ProtectedManager.getInstance().start(
                            context,
                            uid,
                            azureKeyStorageProvider,
                            passcodeProvider,
                            new ProtectedManagerErrorHandler()
                    );
                    return true;
                }
                return false;
            }
        };
    }

    public void start() {
        mStartMonitor.activate();
    }

    public void stop() {
        mStartMonitor.dispose();
        super.stop();
    }
}
