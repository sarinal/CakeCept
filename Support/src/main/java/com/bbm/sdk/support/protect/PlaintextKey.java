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

import org.json.JSONObject;

/**
 * Represents a plain text key
 */
public class PlaintextKey {

    private String key;

    public PlaintextKey() {

    }

    public PlaintextKey(String key) {
        this.key = key;
    }

    public PlaintextKey(JSONObject plainTextJSONKey) {
        this.key = plainTextJSONKey.optString("key");
    }

    public String getKey() {
        return key;
    }

}
