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

package com.bbm.sdk.support.protect.providers;

import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Base64;

import com.bbm.sdk.support.protect.EncryptedManagementKeys;
import com.bbm.sdk.support.protect.EncryptedPayload;
import com.bbm.sdk.support.protect.EncryptedProfileKeys;
import com.bbm.sdk.support.protect.KeyPair;
import com.bbm.sdk.support.protect.KeyStorageProvider;
import com.bbm.sdk.support.protect.KeyStorageResponse;
import com.bbm.sdk.support.protect.PrivateKeyPair;
import com.bbm.sdk.support.util.Logger;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;


/**
 * Implementation of the KeyStorageProvider interface to store keys using Firebase database
 */
public class FirebaseKeyStorageProvider implements KeyStorageProvider {

    private static final String KEY_STORE_PATH = "keyStore";
    private static final String PROFILE_KEYS_DB_KEY = "profile";
    private static final String PUBLIC_KEYS_DB_KEY = "public";
    private static final String MAILBOXES_DB_KEY = "mailboxes";
    private static final String PRIVATE_KEYS_DB_KEY = "private";
    private static final String MANAGEMENT_DB_KEY = "manage";

    private FirebaseDatabase mFirebaseDatabase;
    private FirebaseUser mFirebaseUser;
    private String mLocalUserUid;

    public FirebaseKeyStorageProvider(@NonNull FirebaseUser firebaseUser, @NonNull String uid) {
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mFirebaseUser = firebaseUser;
        mLocalUserUid = uid;
    }

    /**
     * Write the provided keys to the cloud key storage service.
     * @param keys public/private encryption and signing keys for the local user.
     *             Obtained from bbmcore via the {@link com.bbm.sdk.bbmds.outbound.ProfileKeysExport} message
     * @param managementKeys the generated management keys (encryption and hmac) which have been encrypted using a derived key.
     * @param writeRequest the result of the write (success/fail) must be passed back to the request
     */
    @Override
    public void writeProfileKeys(@NonNull final EncryptedProfileKeys keys,
                                 @NonNull EncryptedManagementKeys managementKeys,
                                 @NonNull final KeyStorageResponse<Void> writeRequest) {
        Logger.i("writeProfileKeys for local user");

        //Add the management keys and private profile keys to a new map
        HashMap<String, Object> privateKeysMap = new HashMap<>();
        privateKeysMap.put("manage", managementKeys.getPrivateKeyPair());
        privateKeysMap.put("profile", keys.getPrivateKeyPair());

        HashMap<String, Object> keysMap = new HashMap<>();
        //Add public keys to a new map
        keysMap.put("public", keys.getPublicKeyPair());
        //Add private keys to a new map
        keysMap.put("private", privateKeysMap);

        //Write private (profile + management) and public keys to /keyStore/$uid
        DatabaseReference keyStoreDbRef = mFirebaseDatabase.getReference(KEY_STORE_PATH);
        keyStoreDbRef.child(mLocalUserUid)
                .updateChildren(keysMap, new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                if (databaseError == null) {
                    writeRequest.onSuccess(null);
                } else {
                    Logger.e("error writing profile keys databaseError=" +
                            databaseError + " Details=" + databaseError.getDetails() +
                            " Code=" + databaseError.getCode());
                    writeRequest.onFailure();
                }
            }
        });
    }

    /**
     * Remove the entry at /keystore/$uid
     * @param removeRequest the result of the remove (success/fail) should be passed back to the request
     */
    @Override
    public void removeProfileKeys(@NonNull final KeyStorageResponse<Void> removeRequest) {
        Logger.i("removeProfileKeys for local user");

        //Remove the public keys at /keyStore/$uid
        DatabaseReference publicKeysDbRef = mFirebaseDatabase.getReference(KEY_STORE_PATH);
        publicKeysDbRef.child(mLocalUserUid).removeValue(new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                if (databaseError == null) {
                    //Remove the private keys for the local user at keyStore/$uid
                    removeRequest.onSuccess(null);

                } else {
                    Logger.e("error removing public keys databaseError=" +
                            databaseError+" Details=" + databaseError.getDetails() +
                            " Code="+databaseError.getCode());
                    removeRequest.onFailure();
                }
            }
        });
    }

    /**
     * Write the chat key for the given mailboxId to keyStore/$uid/private/mailboxes
     * @param mailboxId the mailbox id of the chat using this key
     * @param chatKey the encrypted chat key obtained from bbmcore via the {@link com.bbm.sdk.bbmds.outbound.ChatKeyExport} message
     * @param writeRequest the result of the write (success/fail) should be passed back to the request
     */
    public void writeChatKey(@NonNull String mailboxId, @NonNull EncryptedPayload chatKey, @NonNull final KeyStorageResponse<Void> writeRequest) {
        String encodedMailBoxId = base64EncodeValue(mailboxId);
        Logger.i("writeChatKey mailboxId " + encodedMailBoxId);
        //write chat key to /keyStore/$uid/private/mailboxes
        DatabaseReference keyStoreDbRef = mFirebaseDatabase.getReference(KEY_STORE_PATH);
        keyStoreDbRef.child(mLocalUserUid)
                .child(PRIVATE_KEYS_DB_KEY).child(MAILBOXES_DB_KEY).child(encodedMailBoxId)
                .setValue(chatKey, new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                if (databaseError == null) {
                    writeRequest.onSuccess(null);
                } else {
                    Logger.e("error writing chat key databaseError=" + databaseError +
                            " Details=" + databaseError.getDetails() +
                            " Code="+databaseError.getCode());
                    writeRequest.onFailure();
                }
            }
        });
    }

    /**
     * Read a chat key from /keyStore/$uid/private/mailboxes/mailboxId
     * @param mailboxId the mailbox id of the chat requesting the key
     * @param chatKeyStorageResponse the result of the read operation should be returned via the {@link KeyStorageResponse#onSuccess(Object)} or {@link KeyStorageResponse#onFailure()}
     */
    @Override
    public void readChatKey(@NonNull String mailboxId, @NonNull final KeyStorageResponse<EncryptedPayload> chatKeyStorageResponse) {
        String encodedMailBoxId = base64EncodeValue(mailboxId);
        Logger.i("readChatKey for mailboxId :" + encodedMailBoxId);
        //write chat key
        DatabaseReference keyStoreDbRef = mFirebaseDatabase.getReference(KEY_STORE_PATH);
        keyStoreDbRef.child(mLocalUserUid)
                .child(PRIVATE_KEYS_DB_KEY).child(MAILBOXES_DB_KEY).child(encodedMailBoxId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                chatKeyStorageResponse.onSuccess(dataSnapshot.getValue(EncryptedPayload.class));
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Logger.e("error reading chat key onCancelled: databaseError=" + databaseError +
                        " Details=" + databaseError.getDetails() +
                        " Code="+databaseError.getCode());
                chatKeyStorageResponse.onFailure();
            }
        });
    }

    /**
     * Remove the entry at /privateKeyStore/$uid/mailboxes/mailboxId
     * @param mailboxId the mailbox for the chat whose key should be removed.
     * @param writeRequest the result of the write (success/fail) should be passed back to the request
     */
    @Override
    public void removeChatKey(@NonNull String mailboxId, @NonNull final KeyStorageResponse<Void> writeRequest) {
        String encodedMailBoxId = base64EncodeValue(mailboxId);
        Logger.i("removeChatKey for mailboxId :" + encodedMailBoxId);

        //remove mailbox key
        DatabaseReference keyStoreDbRef = mFirebaseDatabase.getReference(KEY_STORE_PATH);
        keyStoreDbRef.child(mLocalUserUid)
                .child(PRIVATE_KEYS_DB_KEY).child("mailboxes").child(encodedMailBoxId)
                .removeValue(new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                if (databaseError == null) {
                    writeRequest.onSuccess(null);
                } else {
                    Logger.e("error deleting chat key databaseError=" + databaseError +
                            " Details=" + databaseError.getDetails() +
                            " Code="+databaseError.getCode());
                    writeRequest.onFailure();
                }
            }
        });
    }

    /**
     * Read the local users private keys from /keyStore/$uid/private/profile
     * @param privateKeysStorageRequest the result of the read operation should be returned via the {@link KeyStorageResponse#onSuccess(Object)} or {@link KeyStorageResponse#onFailure()}
     */
    @Override
    public void readPrivateKeys(@NonNull final KeyStorageResponse<PrivateKeyPair> privateKeysStorageRequest) {
        Logger.i("readPrivateKeys for local user");
        DatabaseReference keyStoreDbRef = mFirebaseDatabase.getReference(KEY_STORE_PATH);
        keyStoreDbRef.child(mLocalUserUid)
                .child(PRIVATE_KEYS_DB_KEY).child(PROFILE_KEYS_DB_KEY)
                .addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                PrivateKeyPair keys = dataSnapshot.getValue(PrivateKeyPair.class);
                privateKeysStorageRequest.onSuccess(keys);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Logger.e("error reading private keys onCancelled: databaseError=" + databaseError +
                        " Details=" + databaseError.getDetails() +
                        " Code="+databaseError.getCode());
                privateKeysStorageRequest.onFailure();
            }
        });
    }

    /**
     * Read the public keys from /keyStore/uid/public
     * @param uid the uid for the user who's keys are needed
     * @param publicKeysStorageRequest the result of the read operation should be returned via the {@link KeyStorageResponse#onSuccess(Object)} or {@link KeyStorageResponse#onFailure()}
     */
    @Override
    public void readPublicKeys(@NonNull String uid, @NonNull final KeyStorageResponse<KeyPair> publicKeysStorageRequest) {
        Logger.i("readPublicKeys for uid " + uid);
        DatabaseReference publicKeysDbRef = mFirebaseDatabase.getReference(KEY_STORE_PATH);
        publicKeysDbRef.child(uid).child(PUBLIC_KEYS_DB_KEY)
                .addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                KeyPair keys = dataSnapshot.getValue(KeyPair.class);
                publicKeysStorageRequest.onSuccess(keys);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Logger.e("error reading private keys onCancelled: databaseError=" + databaseError +
                        " Details=" + databaseError.getDetails() +
                        " Code="+databaseError.getCode());
                publicKeysStorageRequest.onFailure();
            }
        });
    }

    /**
     * Read the management keys from /keyStore/uid/private/manage
     * @param managementKeysKeyStorageResponse the result of the read operation should be returned via the {@link KeyStorageResponse#onSuccess(Object)} or {@link KeyStorageResponse#onFailure()}
     */
    @Override
    public void readManagementKeys(@NonNull KeyStorageResponse<EncryptedManagementKeys> managementKeysKeyStorageResponse) {
        Logger.i("readManagementKeys for local user");
        DatabaseReference keyStoreDbRef = mFirebaseDatabase.getReference(KEY_STORE_PATH);
        keyStoreDbRef.child(mLocalUserUid).child(PRIVATE_KEYS_DB_KEY).child(MANAGEMENT_DB_KEY)
                .addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                PrivateKeyPair keyPair = dataSnapshot.getValue(PrivateKeyPair.class);
                EncryptedManagementKeys managementKeys = keyPair != null ?
                        new EncryptedManagementKeys(keyPair) : null;
                managementKeysKeyStorageResponse.onSuccess(managementKeys);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Logger.e("error reading management keys onCancelled: databaseError=" + databaseError +
                        " Details=" + databaseError.getDetails() +
                        " Code="+databaseError.getCode());
                managementKeysKeyStorageResponse.onFailure();
            }
        });
    }

    /**
     * Write the management keys to /keyStore/uid/private/manage
     * @param encryptedManagementKeys the encrypted management keys
     * @param writeResponse the result of the write operation should be returned via the {@link KeyStorageResponse#onSuccess(Object)} or {@link KeyStorageResponse#onFailure()}
     */
    @Override
    public void writeManagementKeys(@NonNull EncryptedManagementKeys encryptedManagementKeys, @NonNull KeyStorageResponse<Void> writeResponse) {
        Logger.i("writeManagementKeys for local user");
        //write management keys
        DatabaseReference keyStoreDbRef = mFirebaseDatabase.getReference(KEY_STORE_PATH);
        HashMap<String, Object> valuesToUpdate = new HashMap<>();
        valuesToUpdate.put("sign", encryptedManagementKeys.getSign());
        valuesToUpdate.put("encrypt", encryptedManagementKeys.getEncrypt());
        keyStoreDbRef.child(mLocalUserUid).child(PRIVATE_KEYS_DB_KEY).child(MANAGEMENT_DB_KEY)
                .updateChildren(valuesToUpdate, new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                if (databaseError == null) {
                    writeResponse.onSuccess(null);
                } else {
                    Logger.e("error writing management keys databaseError=" + databaseError +
                            " Details=" + databaseError.getDetails() +
                            " Code="+databaseError.getCode());
                    writeResponse.onFailure();
                }
            }
        });
    }

    /**
     * Any value which is to be used as a key in firebase should be URL safe encoded first to avoid any bad characters (ex '.')
     */
    private String base64EncodeValue(String valueToEncode) {
        if (!TextUtils.isEmpty(valueToEncode)) {
            return new String(Base64.encode(valueToEncode.getBytes(), Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP));
        }
        return "";
    }

}
