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

import android.support.annotation.NonNull;

import org.json.JSONObject;

/**
 * Represents an encrypted payload.
 */
public class EncryptedPayload {

    public static final String PAYLOAD = "payload";
    public static final String NONCE = "nonce";
    public static final String MAC = "mac";

    private String payload;
    private String nonce;
    private String mac;

    /**
     * Create a new encrypted payload.
     * @param payload the encrypted payload
     * @param nonce the nonce (initialization vector) created when encrypting
     * @param hmac the generated hmac value
     */
    public EncryptedPayload(@NonNull String payload,@NonNull String nonce,@NonNull String hmac) {
        this.payload = payload;
        this.nonce = nonce;
        this.mac = hmac;
    }

    public EncryptedPayload(@NonNull JSONObject payloadAsJSON) {
        this.payload = payloadAsJSON.optString(PAYLOAD);
        this.nonce = payloadAsJSON.optString(NONCE);
        this.mac = payloadAsJSON.optString(MAC);
    }

    public EncryptedPayload() {
    }

    public String getPayload() {
        return payload;
    }

    public String getNonce() {
        return nonce;
    }

    public String getMac() {
        return mac;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (payload == null ? 0 : payload.hashCode());
        result = prime * result + (nonce == null ? 0 : nonce.hashCode());
        result = prime * result + (mac == null ? 0 : mac.hashCode());

        return result;
    }

    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }
        if (!payload.equals(((EncryptedPayload) obj).payload)) {
            return false;
        }
        if (!nonce.equals(((EncryptedPayload) obj).nonce)) {
            return false;
        }
        if (!mac.equals(((EncryptedPayload) obj).mac)) {
            return false;
        }
        return true;
    }
}
