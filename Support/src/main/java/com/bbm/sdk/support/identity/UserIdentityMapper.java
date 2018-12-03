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

package com.bbm.sdk.support.identity;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.inbound.Identities;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.outbound.IdentitiesGet;
import com.bbm.sdk.reactive.Mutable;
import com.bbm.sdk.reactive.ObservableMonitor;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.service.InboundMessageObservable;
import com.bbm.sdk.support.util.IOUtils;
import com.bbm.sdk.support.util.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.UUID;

/**
 * Maps application user ids to BBM Enterprise SDK registration ids.
 */
public class UserIdentityMapper {

    private static int MAX_ITEMS_PER_REQUEST = 50;
    private static String IDENTITY_CACHE_FILENAME = "identity_map_cache";

    /**
     * Represents an Identity mapping result.
     */
    public static class IdentityMapResult {
        public Existence existence = Existence.MAYBE;
        public long regId;
        public String uid;
        public boolean failed = false;

        public IdentityMapResult(long regId, String uid) {
            this.regId = regId;
            this.uid = uid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            IdentityMapResult that = (IdentityMapResult) o;

            if (regId != that.regId) {
                return false;
            }
            if (failed != that.failed) {
                return false;
            }
            if (existence != that.existence) {
                return false;
            }
            return uid != null ? uid.equals(that.uid) : that.uid == null;
        }

        @Override
        public int hashCode() {
            int result = existence.hashCode();
            result = 31 * result + (int) (regId ^ (regId >>> 32));
            result = 31 * result + (uid != null ? uid.hashCode() : 0);
            result = 31 * result + (failed ? 1 : 0);
            return result;
        }
    }

    private static UserIdentityMapper sInstance;
    private File mCacheFile;
    private boolean mRequestInProgress = false;
    private boolean mPopulatedFromFileCache = false;
    private final Map<String, Mutable<IdentityMapResult>> mMappedResults = new HashMap<>();
    private final Map<String, Mutable<IdentityMapResult>> mPendingRequestMapResults = new HashMap<>();
    private final Map<String, Mutable<IdentityMapResult>> mRequestedMapResults = new HashMap<>();

    private static final String IDENTITIES_REQUEST_COOKIE = UUID.randomUUID().toString();
    private final InboundMessageObservable<Identities> mIdentitiesObservable = new InboundMessageObservable<>(
            new Identities(),
            IDENTITIES_REQUEST_COOKIE,
            BBMEnterprise.getInstance().getBbmdsProtocolConnector()
    );

    private final ObservableMonitor mMapResultMonitor = new ObservableMonitor() {
        @Override
        protected void run() {
            Identities identities = mIdentitiesObservable.get();
            if (identities.exists == Existence.MAYBE) {
                return;
            }
            if (identities.result == Identities.Result.Success) {
                FileWriter writer = null;
                try {
                    if (mCacheFile != null) {
                        writer = new FileWriter(mCacheFile, true);
                    }
                } catch (IOException e) {
                    Logger.e(e);
                }

                Logger.d("UserIdentityMapper - handle results " + mIdentitiesObservable.get().info.size());

                for (Identities.Info info : mIdentitiesObservable.get().info) {
                    IdentityMapResult updatedResult = new IdentityMapResult(info.regId, info.appUserId);
                    updatedResult.existence = Existence.YES;

                    updateMappedValue(mRequestedMapResults, updatedResult);
                    updateMappedValue(mPendingRequestMapResults, updatedResult);

                    //Check for a pending request using the uid
                    Mutable<IdentityMapResult> observableMapResult = mRequestedMapResults.remove(getUidKey(info.appUserId));
                    if (observableMapResult == null) {
                        //Check for a pending request using the regid
                        observableMapResult = mRequestedMapResults.remove(getRegIdKey(info.regId));
                    }

                    //Shouldn't really happen but if somehow we don't have a pending request create a new observable value
                    if (observableMapResult == null) {
                        observableMapResult = new Mutable<>(updatedResult);
                    }

                    addMapping(observableMapResult);

                    if (writer != null) {
                        try {
                            //Write the regId and uid as a new entry
                            writer.append(Long.toString(info.regId)).append(' ').append(info.appUserId).append('\n');
                        } catch (IOException e) {
                            Logger.e(e);
                        }
                    }
                }

                if (writer != null) {
                    IOUtils.safeClose(writer);
                }
            }

            for (Mutable<IdentityMapResult> pendingMapResult : mRequestedMapResults.values()) {
                //No result was found for any map result left in the requested list
                pendingMapResult.get().existence = Existence.NO;
                if (identities.result == Identities.Result.Failure) {
                    pendingMapResult.get().failed = true;
                }
                synchronized (mMappedResults) {
                    if (pendingMapResult.get().regId != 0) {
                        mMappedResults.put(
                                getRegIdKey(pendingMapResult.get().regId),
                                pendingMapResult
                        );
                    }

                    if (!TextUtils.isEmpty(pendingMapResult.get().uid)) {
                        mMappedResults.put(getUidKey(pendingMapResult.get().uid), pendingMapResult);
                    }
                }
                pendingMapResult.dirty();
            }

            //Clear the requested map
            mRequestedMapResults.clear();
            mRequestInProgress = false;
            //We might be waiting to request again
            requestUids();
        }
    };

    private UserIdentityMapper() {
        mMapResultMonitor.activate();
    }

    public synchronized static UserIdentityMapper getInstance() {
        if (sInstance == null) {
            sInstance = new UserIdentityMapper();
        }
        return sInstance;
    }

    /**
     * Delete the file cache if it exists.
     */
    public void deleteCache() {
        if (mCacheFile != null) {
            mCacheFile.delete();
        }
    }

    /**
     * Stop listening to Identities messages from bbmcore
     */
    public void stop() {
        mMapResultMonitor.dispose();
    }

    /**
     * Initialize the file cache for the UserIdentityMapper
     * @param context android app context
     */
    public void initializeFileCache(Context context) {
        Logger.d("UserIdentityMapper - Initialize file cache");
        mCacheFile = new File(context.getFilesDir(), IDENTITY_CACHE_FILENAME);
    }

    private String getRegIdKey(long regId) {
        return "regId." + regId;
    }

    private String getUidKey(String uid) {
        return "uid." + uid;
    }

    private void addMapping(Mutable<IdentityMapResult> obsResult) {
        synchronized (mMappedResults) {
            mMappedResults.put(getRegIdKey(obsResult.get().regId), obsResult);
            mMappedResults.put(getUidKey(obsResult.get().uid), obsResult);
        }
    }

    private void updateMappedValue(Map<String, Mutable<IdentityMapResult>> mapToUpdate, IdentityMapResult updatedResult) {
        //Update
        Mutable<IdentityMapResult> observableMapResult = mapToUpdate.remove(getUidKey(updatedResult.uid));
        if (observableMapResult != null) {
            observableMapResult.set(updatedResult);
        }

        //Check for a pending request using the regid
        observableMapResult = mapToUpdate.remove(getRegIdKey(updatedResult.regId));
        if (observableMapResult != null) {
            observableMapResult.set(updatedResult);
        }
    }

    private Mutable<IdentityMapResult> getExistingMapResult(String key) {
        Mutable<IdentityMapResult> mResult = mPendingRequestMapResults.get(key);
        if (mResult == null) {
            mResult = mRequestedMapResults.get(key);
        }
        if (mResult == null) {
            mResult = mMappedResults.get(key);
        }

        return mResult;
    }

    /**
     * Find the application user identifier for a provided BBM Enterprise SDK registration id.
     * The UidResult will be populated with the Uid when it becomes known.
     * @param regId a BBM Enterprise SDK registration id
     * @param retryMapping retry the map request if an IdentityMapResult with Existence.NO is found
     * @return an ObservableValue of type {@link IdentityMapResult}.
     */
    @NonNull
    public ObservableValue<IdentityMapResult> getUidForRegId(long regId, boolean retryMapping) {
        if (regId == 0) {
            Logger.e("Cannot perform identity map request, invalid regId=" + regId);
            IdentityMapResult mapResult = new IdentityMapResult(0,"");
            mapResult.existence = Existence.NO;
            return new Mutable<>(mapResult);
        }
        Logger.d("Requesting uid for regId " + regId + ", forceRetry " + retryMapping);
        synchronized (mMappedResults) {
            String regIdKey = getRegIdKey(regId);
            Mutable<IdentityMapResult> obsMapResult = getExistingMapResult(regIdKey);
            if (obsMapResult == null) {
                //If we don't have a matching valid map entry or pending request then create a new request
                IdentityMapResult mapResult = new IdentityMapResult(regId, "");
                obsMapResult = new Mutable<>(mapResult);
                mPendingRequestMapResults.put(regIdKey, obsMapResult);
                requestUids();
            } else if (retryMapping) {
                requestUids();
            }

            return obsMapResult;
        }
    }

    /**
     * Find the BBM Enterprise SDK registration id for the provided application user identifier.
     * The RegIdResult will be populated with the registration id when it becomes known.
     * @param uid an application user identifier
     * @param retryMapping retry the map request if an IdentityMapResult with Existence.NO is found
     * @return an ObservableValue of type {@link IdentityMapResult}.
     */
    @NonNull
    public ObservableValue<IdentityMapResult> getRegIdForUid(String uid, boolean retryMapping) {
        Logger.d("Requesting regId for uid " + uid + ", forceRetry " + retryMapping);
        synchronized (mMappedResults) {
            String uidKey = getUidKey(uid);
            Mutable<IdentityMapResult> obsMapResult = getExistingMapResult(uidKey);
            if (obsMapResult == null) {
                //If we don't have a matching valid map entry or pending request then create a new request
                IdentityMapResult mapResult = new IdentityMapResult(0, uid);
                obsMapResult = new Mutable<>(mapResult);
                mPendingRequestMapResults.put(uidKey, obsMapResult);
                requestUids();
            } else if (retryMapping) {
                requestUids();
            }

            return obsMapResult;
        }
    }

    private void requestUids() {
        //Check to see if there are still any pending map lookups to complete
        if (!mRequestInProgress && mPendingRequestMapResults.size() > 0) {
            mRequestInProgress = true;
            AsyncTask.execute(() -> {
                //First check if we should populate our map from the file cache
                if (!mPopulatedFromFileCache && mCacheFile != null) {
                    populateCache();
                }

                synchronized (mMappedResults) {
                    List<Long> regIds = new ArrayList<>();
                    List<String> uids = new ArrayList<>();
                    for (Mutable<IdentityMapResult> obsMapResult : mPendingRequestMapResults.values()) {
                        IdentityMapResult mapResult = obsMapResult.get();
                        if (mapResult.regId != 0) {
                            regIds.add(mapResult.regId);
                        } else if (!TextUtils.isEmpty(mapResult.uid)) {
                            uids.add(mapResult.uid);
                        }

                        //If we reach the max requests then stop, we will send another request when this one has completed.
                        if (regIds.size() == MAX_ITEMS_PER_REQUEST || uids.size() == MAX_ITEMS_PER_REQUEST) {
                            break;
                        }
                    }

                    //Perform any regId lookups
                    if (!uids.isEmpty()) {
                        Logger.d("Requesting " + uids.size() + " regIds");
                        //Move observable result from the pending map to the requested map
                        for (String uid : uids) {
                            String key = getUidKey(uid);
                            mRequestedMapResults.put(key, mPendingRequestMapResults.remove(key));
                        }
                        IdentitiesGet getIdentities = new IdentitiesGet(IDENTITIES_REQUEST_COOKIE).appUserIds(uids);
                        BBMEnterprise.getInstance().getBbmdsProtocol().send(getIdentities);
                        //Only request one type (regid or uid at a time)
                        return;
                    }

                    //If we have any uid lookups to perform
                    if (!regIds.isEmpty()) {
                        Logger.d("Requesting " + regIds.size() + " uids");
                        //Move observable result from the pending map to the requested map
                        for (Long regId : regIds) {
                            String key = getRegIdKey(regId);
                            mRequestedMapResults.put(key, mPendingRequestMapResults.remove(key));
                        }
                        IdentitiesGet getIdentities = new IdentitiesGet(IDENTITIES_REQUEST_COOKIE).regIds(regIds);
                        BBMEnterprise.getInstance().getBbmdsProtocol().send(getIdentities);
                        //Only request one type (regid or uid at a time)
                        return;
                    }

                    //If we didn't request either regIds or uids set requestInProgress to false
                    mRequestInProgress = false;
                }
            });
        }
    }

    private void populateCache() {
        Logger.d("UserIdentityMapper - populate file cache");
        BufferedReader reader = null;
        try {
            if (!mCacheFile.exists()) {
                mCacheFile.createNewFile();
            }
            reader = new BufferedReader(new FileReader(mCacheFile));
            String line;
            while ((line = reader.readLine()) != null){
                parseCacheLine(line);
            }
        } catch (IOException e) {
            Logger.e(e);
        } finally {
            IOUtils.safeClose(reader);
        }

        mPopulatedFromFileCache = true;
    }

    private void parseCacheLine(String line) {
        StringTokenizer tokenizer = new StringTokenizer(line, " ");
        if (tokenizer.countTokens() == 2) {
            try {
                String regIdString = tokenizer.nextToken();
                long regId = Long.parseLong(regIdString);
                String uid = tokenizer.nextToken();
                IdentityMapResult mapResult = new IdentityMapResult(regId, uid);
                mapResult.existence = Existence.YES;
                synchronized (mMappedResults) {
                    String regIdKey = getRegIdKey(regId);
                    String uidKey = getUidKey(mapResult.uid);
                    Mutable<IdentityMapResult> obsMapResult = mPendingRequestMapResults.remove(regIdKey);
                    if (obsMapResult == null) {
                        obsMapResult = mPendingRequestMapResults.remove(uidKey);
                    }
                    if (obsMapResult == null) {
                        obsMapResult = new Mutable<>(mapResult);
                    } else {
                        obsMapResult.set(mapResult);
                    }
                    mMappedResults.put(regIdKey, obsMapResult);
                    mMappedResults.put(uidKey, obsMapResult);
                }
            } catch (NumberFormatException nfe) {
                Logger.e("Invalid identity map cache entry " + line);
            }
        }
    }
}
