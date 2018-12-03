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

import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.bbm.sdk.bbmds.User;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.reactive.ObservableTracker;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.reactive.TrackedGetter;
import com.bbm.sdk.support.identity.UserIdentityMapper;
import com.bbm.sdk.support.identity.auth.AuthenticatedAccountData;
import com.bbm.sdk.support.identity.auth.LocalAuthDataListener;
import com.bbm.sdk.support.reactive.AbstractObservableValue;
import com.bbm.sdk.support.util.BbmUtils;
import com.bbm.sdk.support.util.Logger;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Example implementation of a app storing its app user data in a Firebase DB.
 * This will automatically sync the necessary local BBM SDK User data to the remote Firebase DB when it changes.
 * It listens for all other remote users as they are added to or change in the remote Firebase DB and will
 * sync the remote app users locally.
 * <p>
 * If an app stores its users somewhere other than a Firebase DB it could replace this implementation
 * with one that implements AppUserSource to provide updates as the remote app user data changes.
 */
public class FirebaseUserDbSync extends AppSourceNotifier implements LocalAuthDataListener {
    private static final String BASE_DB_PATH = "bbmsdk";
    private static final String SAMPLEAPPS_DB_KEY = "identity";
    private static final String USERS_DB_KEY = "users";
    private static final String USERS_DB_PATH = BASE_DB_PATH + '/' + SAMPLEAPPS_DB_KEY + '/' + USERS_DB_KEY;

    private static final String CONNECTION_STATE_PATH = ".info/connected";

    private static FirebaseUserDbSync sInstance;
    private FirebaseDatabase mFirebaseDatabase;

    private boolean mInitDone;
    private boolean mListeningToDb;
    //the values that we sent to firebase DB for local user
    private ObservableValue<User> mLocalBbmUser;
    private AuthenticatedAccountData mAccountData;

    //listen for changes to local user to send to DB
    private final Observer mLocalBbmUserObserver = new Observer() {
        @Override
        public void changed() {
            syncLocalUser();
        }
    };

    private ObservableFirebaseUser mObservableFirebaseUser = new ObservableFirebaseUser();

    //Create an Observable to track the FirebaseUser
    private class ObservableFirebaseUser extends AbstractObservableValue<FirebaseUser> {
        private FirebaseUser mFirebaseUser = null;

        public void set(FirebaseUser user) {
            mFirebaseUser = user;
            notifyObservers();
        }

        @Override
        @TrackedGetter
        public FirebaseUser get() {
            //Register as TrackedGetter
            ObservableTracker.getterCalled(this);
            return mFirebaseUser;
        }
    }

    private ChildEventListener mDatabaseEventListener = new ChildEventListener() {
        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String prev) {
            Logger.priv("onChildAdded: dataSnapshot=" + dataSnapshot + " prev=" + prev);
            notifyAppUserListeners(EventToNotify.ADD, dataSnapshot);
        }

        @Override
        public void onChildChanged(DataSnapshot dataSnapshot, String prev) {
            Logger.priv("onChildChanged: dataSnapshot=" + dataSnapshot + " prev=" + prev);
            notifyAppUserListeners(EventToNotify.CHANGE, dataSnapshot);
        }

        @Override
        public void onChildRemoved(DataSnapshot dataSnapshot) {
            Logger.priv("onChildRemoved: dataSnapshot=" + dataSnapshot);
            notifyAppUserListeners(EventToNotify.REMOVE, dataSnapshot);
        }

        @Override
        public void onChildMoved(DataSnapshot dataSnapshot, String prev) {
            Logger.priv("Ignoring move: dataSnapshot=" + dataSnapshot + " prev=" + prev);
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {
            Logger.i("onCancelled: addChildEventListener: databaseError=" + databaseError + " Details=" + databaseError.getDetails() + " Code=" + databaseError.getCode());
            mListeningToDb = false;
        }
    };

    private ValueEventListener mDatabaseConnectionStateListener = new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot snapshot) {
            boolean connected = snapshot.getValue(Boolean.class);
            if (connected) {
                Logger.d("FirebaseDatabase connected");
                //When we reconnect make sure we are authenticated and have updated the local user
                if (mAccountData != null && mObservableFirebaseUser.get() == null) {
                    signInAndUpdate();
                }
            } else {
                Logger.d("FirebaseDatabase disconnected");
            }
        }

        @Override
        public void onCancelled(DatabaseError error) {
            Logger.e("Listener was cancelled at .info/connected");
        }
    };

    public static synchronized FirebaseUserDbSync getInstance() {
        if (sInstance == null) {
            sInstance = new FirebaseUserDbSync();
        }
        return sInstance;
    }

    private FirebaseUserDbSync() {
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        Logger.d("mFirebaseDatabase=" + mFirebaseDatabase);

        //Add a listener for the firebase database connected state
        final DatabaseReference connectedRef = mFirebaseDatabase.getReference(CONNECTION_STATE_PATH);
        connectedRef.addValueEventListener(mDatabaseConnectionStateListener);
    }

    //called when local user authenticates
    @Override
    public void localAuthDataChanged(AuthenticatedAccountData accountData) {
        if (accountData != null) {
            mAccountData = accountData;
            signInAndUpdate();
        }
    }

    private void signInAndUpdate() {
        if (mObservableFirebaseUser.get() == null) {
            AuthCredential authCredential = GoogleAuthProvider.getCredential(mAccountData.getIdToken(), null);
            FirebaseAuth.getInstance().signInWithCredential(authCredential).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().getUser() != null) {
                        FirebaseUser localFirebaseUser = task.getResult().getUser();
                        mObservableFirebaseUser.set(localFirebaseUser);
                        Logger.d("onComplete: signInWithCredential: Successful. ProviderId=" + localFirebaseUser.getProviderId() + " Uid=" + localFirebaseUser.getUid() + " Providers=" + localFirebaseUser.getProviders());

                        syncLocalUser();
                    } else {
                        Logger.i(task.getException(), "onComplete: Failed to signInWithCredential task=" + task);
                        String message = "Failed to sign in with credential ";
                        if (task.getException() != null && task.getException().getMessage() != null) {
                            message += task.getException().getMessage();
                        }
                        Logger.user(message);
                    }
                }
            });
        } else {
            syncLocalUser();
        }
    }

    //send data for local user from both the BBM User object and the authentication account data to DB
    private void syncLocalUser() {
        if (mLocalBbmUser == null) {
            return;
        }

        User user = mLocalBbmUser.get();

        Logger.d("syncLocalUser: " + user + " exists=" + user.getExists());
        final FirebaseUser localFirebaseUser = mObservableFirebaseUser.get();

        if (user.getExists() == Existence.YES && user.regId != 0 && localFirebaseUser != null) {
            final AppUser appUser = new AppUser(
                    user,
                    mAccountData.getId(),
                    mAccountData.getName(),
                    mAccountData.getEmail(),
                    mAccountData.getAvatarUrl()
            );

            notifyAppUserListeners(EventToNotify.LOCAL_UPDATED, appUser);

            final UserData userData = new UserData(appUser);
            DatabaseReference dbRef = mFirebaseDatabase.getReference(USERS_DB_PATH);
            //use the auth ID as the key to the data
            Task<Void> setTask = dbRef.child(mAccountData.getId()).setValue(userData);
            setTask.addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task task) {
                    try {
                        Logger.d("onComplete: DB setValue: task=" + task + " appUser=" + appUser + " isComplete=" + task.isComplete() + " isSuccessful=" + task.isSuccessful()
                                + " getException=" + task.getException());

                        if (task.isComplete() && !task.isSuccessful()) {
                            String err = "";
                            if (task.getException() != null) {
                                err = task.getException().toString();
                            }
                            Logger.user("Failed to sync local user to remote DB " + err);

                            Logger.w(task.getException(), "Error writing to FB DB USERS_DB_PATH=" + USERS_DB_PATH + " UID=" + localFirebaseUser.getUid());
                        } else {
                            listenToDb();
                        }
                    } catch (DatabaseException dbEx) {
                        Logger.i(dbEx, "Error reading FB DB");
                    }
                }
            });
        } else {
            Logger.i("syncLocalUser: not ready yet localFireBaseUser=" + localFirebaseUser + " user.getExists()=" + user.getExists() + " regId=" + user.regId);
        }
    }

    /**
     * Request a user from firebase.
     * @param uid the user identifier for the requested user.
     */
    @Override
    public void requestUser(@NonNull String uid) {
        Logger.i("Request firebase user with uid " + uid);
        SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
            @Override
            public boolean run() {
                //Wait until we have authenticated before checking the db for this user
                if (mObservableFirebaseUser.get() == null) {
                    return false;
                }

                mFirebaseDatabase.getReference(USERS_DB_PATH).child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        Logger.priv("onDataChange: dataSnapshot=" + dataSnapshot);
                        notifyAppUserListeners(EventToNotify.ADD, dataSnapshot);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Logger.e("onCancelled: databaseError=" + databaseError);
                    }
                });

                return true;
            }
        });

    }

    public ObservableValue<FirebaseUser> getFireBaseUser() {
        return mObservableFirebaseUser;
    }

    public void initialize() {
        if (mInitDone) {
            Logger.d("init already done");
            return;
        }
        mLocalBbmUser = BbmUtils.getLocalBbmUser();
        mLocalBbmUser.addObserver(mLocalBbmUserObserver);

        syncLocalUser();

        mInitDone = true;
    }

    public void stop() {
        mObservableFirebaseUser.set(null);

        if (mLocalBbmUser != null) {
            mLocalBbmUser.removeObserver(mLocalBbmUserObserver);
        }

        mAccountData = null;

        if (mFirebaseDatabase != null) {
            DatabaseReference dbRef = mFirebaseDatabase.getReference(USERS_DB_PATH);
            dbRef.removeEventListener(mDatabaseEventListener);
        }

        mListeningToDb = false;
        mInitDone = false;
    }

    private void listenToDb() {
        FirebaseUser localFirebaseUser = mObservableFirebaseUser.get();
        //if we try to listen to the DB before authenticated we will be rejected
        if (localFirebaseUser != null && !mListeningToDb) {
            DatabaseReference dbRef = mFirebaseDatabase.getReference(USERS_DB_PATH);

            Logger.d("listenToDb dbRef=" + dbRef + " local UID=" + localFirebaseUser.getUid());
            dbRef.addChildEventListener(mDatabaseEventListener);
            mListeningToDb = true;
        }
    }

    private void notifyAppUserListeners(EventToNotify event, DataSnapshot dataSnapshot) {
        try {
            UserData userData = dataSnapshot.getValue(UserData.class);
            if (userData != null) {
                String uid = dataSnapshot.getKey();
                if (!TextUtils.isEmpty(uid)) {
                    if (mAccountData.getId().equals(uid)) {
                        Logger.d("skipping local user from DB");
                    } else {
                        //Request the regId for the UID of the user data
                        ObservableValue<UserIdentityMapper.IdentityMapResult> mapResult =
                                UserIdentityMapper.getInstance().getRegIdForUid(uid, false);
                        SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
                            @Override
                            public boolean run() {
                                if (mapResult.get().existence == Existence.MAYBE) {
                                    return false;
                                }
                                //If we found the regId we can add this user to the list
                                if (mapResult.get().existence == Existence.YES) {
                                    AppUser appUser = new AppUser(
                                            mapResult.get().regId,
                                            uid, userData.getName(),
                                            userData.getEmail(),
                                            userData.getAvatarUrl()
                                    );
                                    //exists isn't stored in DB, so we need to set it here
                                    appUser.setExists(Existence.YES);
                                    notifyAppUserListeners(event, appUser);
                                } else {
                                    Logger.e("Failed to retrieve regId for uid " + uid);
                                }
                                return true;
                            }
                        });
                    }
                } else {
                    Logger.i("Ignoring invalid appUser=" + dataSnapshot);
                }
            }
        } catch (DatabaseException ex) {
            Logger.i(ex, "Firebase error reading database user");
        }
    }
}
