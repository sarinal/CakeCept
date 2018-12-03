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
 * Contains the users Profile keys processed for storage in the cloud key storage provider.
 */
public class EncryptedProfileKeys {

    private static final String PROFILE = "profile";
    private static final String PRIVATE = "private";
    private static final String PUBLIC = "public";

    private PrivateKeyPair privateKeyPair;
    private KeyPair publicKeyPair;

    public EncryptedProfileKeys() {
    }

    public EncryptedProfileKeys(PrivateKeyPair privateKeyPair, KeyPair publicKeyPair) {
        this.privateKeyPair = privateKeyPair;
        this.publicKeyPair = publicKeyPair;
    }

    public PrivateKeyPair getPrivateKeyPair() {
        return privateKeyPair;
    }

    public KeyPair getPublicKeyPair() {
        return publicKeyPair;
    }

}
