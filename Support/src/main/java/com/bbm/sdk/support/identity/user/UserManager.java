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

package com.bbm.sdk.support.identity.user;

import android.support.annotation.UiThread;
import android.text.TextUtils;
import android.util.LongSparseArray;

import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.internal.lists.ObservableList;
import com.bbm.sdk.reactive.Mutable;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.support.identity.UserIdentityMapper;
import com.bbm.sdk.support.reactive.ArrayObservableList;
import com.bbm.sdk.support.reactive.StateAwareComputedList;
import com.bbm.sdk.support.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;


/**
 * This manages the app user data.
 * The data source for app users should update this with its user data.
 * For sample app purposes only.
 * This must only be used from the main UI thread.
 */
public class UserManager implements AppUserListener {
    private static UserManager sInstance;

    public static synchronized UserManager getInstance() {
        if(sInstance == null) {
            sInstance = new UserManager();
        }
        return sInstance;
    }

    /**
     * Store by regId to user.
     * This contains the same objects as mUserList
     */
    private final LongSparseArray<Mutable<AppUser>> mRegIdToUserMap = new LongSparseArray<>();
    private final HashMap<String, Mutable<AppUser>> mUidToUserMap = new HashMap<>();

    /**
     * Keep list of users so that we can return them by constant index (unless one is removed) by order they were added
     * This contains the same objects as mRegIdToUserMap, except the AppUser objects are not wrapped in observerables
     */
    private ArrayObservableList<AppUser> mUserList = new ArrayObservableList<>();

    private Mutable<AppUser> mLocalAppUser = new Mutable<>(new AppUser());

    private AppUserSource mAppUserSource;

    private UserManager() {
    }

    /**
     * Clears all the data from the manager. Should only be called when the user has
     * decided to logout.
     */
    public void clear() {
        mRegIdToUserMap.clear();
        mUidToUserMap.clear();
        mUserList.clear();
    }

    @UiThread
    @Override
    public void localUserUpdated(AppUser localAppUser) {
        //we don't want the local user in our list otherwise it would need to be filtered from everywhere
        //if needed we could store it separately (to display in profile UI...)
        //remove does nothing if already removed
        removeUser(localAppUser);
        mLocalAppUser.set(localAppUser);
    }

    @UiThread
    @Override
    public void remoteUserAdded(AppUser remoteAppUser) {
        remoteUserUpdated(remoteAppUser);
    }

    @UiThread
    @Override
    public void remoteUserChanged(AppUser remoteAppUser) {
        remoteUserUpdated(remoteAppUser);
    }

    @UiThread
    @Override
    public void remoteUserRemoved(AppUser remoteAppUser) {
        removeUser(remoteAppUser);
    }

    @Override
    public void setAppUserSource(AppUserSource source) {
        mAppUserSource = source;
    }

    @UiThread
    public void remoteUserUpdated(AppUser appUser) {
        synchronized (mRegIdToUserMap) {
            final long regId = appUser.getRegId();
            if (regId == 0 || TextUtils.isEmpty(appUser.getUid())) {
                Logger.w("Ignoring invalid (missing regId or uid) AppUser="+appUser);
            } else {
                //Get any existing user from the regId map and uid map
                Mutable<AppUser> regIdAppUserOv = mRegIdToUserMap.get(regId);
                Mutable<AppUser> uidAppUserOv = mUidToUserMap.get(appUser.getUid());

                if (regIdAppUserOv == null && uidAppUserOv == null) {
                    //it wasn't in map, so create new one and add to end of list
                    Mutable<AppUser> appUserOv = new Mutable<>(appUser);
                    mRegIdToUserMap.put(regId, appUserOv);
                    mUidToUserMap.put(appUser.getUid(), appUserOv);
                    mUserList.add(appUser);
                    Logger.d("add: added, have " + mUserList.size() + " users. " + appUser);
                } else {
                    //it was already in map, so just set the new value in it
                    //This updates the same OV in both the map and list, and will trigger notifications for any listeners
                    if (regIdAppUserOv != null) {
                        //If the existing AppUser had existence "maybe" then we need to add it to the user list
                        if (regIdAppUserOv.get().getExists() == Existence.MAYBE) {
                            mUserList.add(appUser);
                        }
                        regIdAppUserOv.set(appUser);
                    }
                    if (uidAppUserOv != null) {
                        uidAppUserOv.set(appUser);
                    }
                    Logger.d("add: updated, have " + mUserList.size() + " users. " + appUser);
                }
            }

            if (TextUtils.isEmpty(appUser.getUid())) {
                Logger.w("Ignoring appUser with empty regId AppUser="+appUser);
            } else {
                Mutable<AppUser> appUserOv = mUidToUserMap.get(appUser.getUid());
                if (appUserOv == null) {

                }
            }
        }
    }

    @UiThread
    public void removeUser(AppUser appUser) {
        ObservableValue<AppUser> toBeRemoved;
        Logger.d("remove appUser="+appUser);
        synchronized (mRegIdToUserMap) {
            toBeRemoved = mRegIdToUserMap.get(appUser.getRegId());
            if (toBeRemoved == null) {
                toBeRemoved = mUidToUserMap.get(appUser.getUid());
            }
            mRegIdToUserMap.remove(appUser.getRegId());
            mUidToUserMap.remove(appUser.getUid());
            if (toBeRemoved != null) {
                if (!mUserList.remove(toBeRemoved.get())) {
                    Logger.e("remove: Failed to find remove user in list old=" + toBeRemoved.get());
                }
            }
        }
    }

    /**
     * Get a user by their Application user identifier
     * @param uid application user identifier
     * @return observable appUser
     */
    @UiThread
    public ObservableValue<AppUser> getUserByUid(String uid) {
        synchronized (mUidToUserMap) {
            Mutable<AppUser> appUser = mUidToUserMap.get(uid);
            if (appUser == null) {
                ObservableValue<UserIdentityMapper.IdentityMapResult> mapResult =
                        UserIdentityMapper.getInstance().getRegIdForUid(uid, true);
                if (mapResult.get().existence == Existence.YES) {
                    //Check if we have this user mapped by regId
                    appUser = mRegIdToUserMap.get(mapResult.get().regId);
                }
                if (appUser == null) {
                    appUser = new Mutable<>(new AppUser(uid, Existence.MAYBE));
                    mUidToUserMap.put(uid, appUser);
                    //Ask the app user source to fetch this user
                    //If the user is found the result will be added to the user list via remoteUserAdded
                    mAppUserSource.requestUser(mapResult.get().uid);
                }
            }
            return appUser;
        }
    }

    /**
     * Get a user by their BBM Enterprise SDK registration id
     * @param regId the users BBM Enterprise SDK registration id
     * @return observable appUser
     */
    @UiThread
    public ObservableValue<AppUser> getUser(long regId) {
        if (regId == 0) {
            Logger.e("UserManager getUser, invalid regId=" + regId);
            return new Mutable<>(new AppUser(regId, Existence.NO));
        }
        synchronized (mRegIdToUserMap) {
            Mutable<AppUser> appUser = mRegIdToUserMap.get(regId);

            if (appUser == null) {
                ObservableValue<UserIdentityMapper.IdentityMapResult> mapResult =
                        UserIdentityMapper.getInstance().getUidForRegId(regId, true);
                if (mapResult.get().existence == Existence.YES) {
                    //Check if we have the user mapped by UID
                    appUser = mUidToUserMap.get(mapResult.get().uid);
                }
                if (appUser == null) {
                    //we can't just return null, need to return OV with default
                    appUser = new Mutable<>(new AppUser(regId, Existence.MAYBE));
                    //save the OV in our collections so the caller can observe the value and be notified if it is added later
                    mRegIdToUserMap.put(regId, appUser);

                    if (mAppUserSource != null) {
                        SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
                            @Override
                            public boolean run() {
                                if (mapResult.get().existence == Existence.MAYBE) {
                                    return false;
                                } else if (mapResult.get().existence == Existence.YES) {
                                    //Ask the app user source to fetch this user
                                    //If the user is found the result will be added to the user list via remoteUserAdded
                                    mAppUserSource.requestUser(mapResult.get().uid);
                                }
                                return true;
                            }
                        });
                    }
                }

                Logger.d("getUser: created placeholder appUser for regId=" + regId);
            }
            return appUser;
        }
    }

    /**
     * The list of all users, unordered.
     * @return unordered list of users
     */
    public ObservableList<AppUser> getUsers() {
        return mUserList;
    }

    /**
     * The list of users sorted alphabetically.
     * @return sorted list of users
     */
    public StateAwareComputedList<AppUser> getSortedUsers() {
        return new StateAwareComputedList<AppUser>() {
            @Override
            public boolean isPending() {
                return mUserList.isPending();
            }

            @Override
            protected List<AppUser> compute() {
                List<AppUser> sortedList = new ArrayList<>(mUserList.size());
                sortedList.addAll(mUserList.get());
                Collections.sort(sortedList, new Comparator<AppUser>() {
                    @Override
                    public int compare(AppUser user1, AppUser user2) {
                        return user1.getName().compareToIgnoreCase(user2.getName());
                    }
                });
                return sortedList;
            }
        };
    }

    /**
     * The AppUser for the local user. This appUser is not included in the getUsers or getSortedUsers list.
     * @return appUser for local user.
     */
    public ObservableValue<AppUser> getLocalAppUser() {
        return mLocalAppUser;
    }
}
