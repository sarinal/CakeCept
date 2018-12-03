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


import android.text.TextUtils;

/**
 * Encryption key and signing key pair. Used for read/writing to the KeyStorageProvider.
 */
public class KeyPair {

    private PlaintextKey sign;
    private PlaintextKey encrypt;

    public KeyPair() {

    }

    public KeyPair(PlaintextKey encryptionKey, PlaintextKey signingKey) {
        if (!TextUtils.isEmpty(signingKey.getKey())) {
            this.sign = signingKey;
        }

        if (!TextUtils.isEmpty(encryptionKey.getKey())) {
            this.encrypt = encryptionKey;
        }
    }

    public PlaintextKey getSign() {
        return sign;
    }

    public PlaintextKey getEncrypt() {
        return encrypt;
    }
}
