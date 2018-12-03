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

import android.content.Context;
import android.os.AsyncTask;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.service.BBMEnterpriseState;
import com.bbm.sdk.support.identity.UserIdentityMapper;
import com.google.firebase.iid.FirebaseInstanceId;

public class IdentityUtils {

    private static class FirebasePushTokenTask extends AsyncTask {
        // The call to get the token is blocking so we run it off the main UI thread
        @Override
        protected Object doInBackground(Object[] params) {
            final String token = FirebaseInstanceId.getInstance().getToken();
            if (token != null) {
                Logger.d("Updating firebase push token " + token);
                BBMEnterprise.getInstance().setPushToken(token);
            } else {
                Logger.e("Firebase push token is NULL");
            }
            return null;
        }
    }

    /**
     * This will initialize the Firebase DB for app user storage/sync and
     * will wait until both the Firebase DB and BBM SDK are in a state ready to
     * start the ProtectedManager.
     *
     * @param context Android application context
     * @param updateFcmPushToken Controls if this sBBMEnterpriseStateListener will update the Firebase Cloud Messaging push token when it detects
     *                           that BBMEnterpriseState is in the STARTED state.
     */
    public static void initUserDbSync(Context context, boolean updateFcmPushToken) {
        UserIdentityMapper.getInstance().initializeFileCache(context);
        //start the monitor so the appropriate user sync is started when ready
        SingleshotMonitor.run(() -> {
            BBMEnterpriseState bbmEnterpriseState = BBMEnterprise.getInstance().getState().get();
            Logger.d("bbmEnterpriseState="+bbmEnterpriseState);

            //Monitor the setup state.
            if (bbmEnterpriseState == BBMEnterpriseState.STARTED) {
                AuthIdentityHelper.initIdentity(context);
                if (updateFcmPushToken) {
                    setFirebaseToken();
                }
                return true;
            }
            return false;
        });
    }

    private static void setFirebaseToken() {
        //noinspection unchecked
        new FirebasePushTokenTask().execute();
    }

}
