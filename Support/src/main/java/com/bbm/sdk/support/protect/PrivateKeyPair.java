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

/**
 * Private KeyPair, includes the regId of the key pair owner. Used for reading/writing to the KeyStorageProvider.
 */
public class PrivateKeyPair {
    private EncryptedPayload sign;
    private EncryptedPayload encrypt;

    public PrivateKeyPair() {

    }

    public PrivateKeyPair(EncryptedPayload encryptionKey, EncryptedPayload signingKey) {
        if (signingKey != null) {
            this.sign = signingKey;
        }

        if (encryptionKey != null) {
            this.encrypt = encryptionKey;
        }
    }

    public EncryptedPayload getSign() {
        return sign;
    }

    public EncryptedPayload getEncrypt() {
        return encrypt;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (sign == null ? 0 : sign.hashCode());
        result = prime * result + (encrypt == null ? 0 : encrypt.hashCode());

        return result;
    }

    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }
        if (!encrypt.equals(((PrivateKeyPair) obj).encrypt)) {
            return false;
        }
        if (!sign.equals(((PrivateKeyPair) obj).sign)) {
            return false;
        }
        return true;
    }
}
