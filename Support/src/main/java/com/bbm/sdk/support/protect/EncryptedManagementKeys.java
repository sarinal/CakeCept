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

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Represents the set of encrypted management keys (AES management key and HMAC key)
 */
public class EncryptedManagementKeys {

    public static final int PENDING = 1; //No value has been set
    public static final int DECRYPTION_REQUIRED = 2; //Keys have been retrieved from cloud storage and must be decrypted
    public static final int NO_KEYS_AVAILABLE = 3; //No keys are available from cloud storage
    public static final int COMPLETED = 4; //Keys have been decrypted or new keys have been created and stored successfully in the cloud
    @Retention(SOURCE)
    @IntDef({PENDING, DECRYPTION_REQUIRED, NO_KEYS_AVAILABLE, COMPLETED})
    @interface State {
    }

    private PrivateKeyPair managementKeyPair;
    private transient @State int state = PENDING;

    public EncryptedManagementKeys() {
    }

    /**
     * Create EncryptedManagementKeys
     * @param managementKeyPair the management key pair (encryption/signing)
     */
    public EncryptedManagementKeys(@NonNull PrivateKeyPair managementKeyPair) {
        this.managementKeyPair = managementKeyPair;
    }

    public EncryptedPayload getEncrypt() {
        return managementKeyPair.getEncrypt();
    }

    public EncryptedPayload getSign() {
        return managementKeyPair.getSign();
    }

    public PrivateKeyPair getPrivateKeyPair() {
        return managementKeyPair;
    }

    public void setState(@State int state) {
        this.state = state;
    }

    public @State int getState() {
        return state;
    }
}
