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

package com.bbm.sdk.support.ui.widgets.chats;


import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.bbm.sdk.support.util.Logger;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Define the JSON for per-message priority
 */
public class MessagePriority {

    private static final String PRIORITY = "priority";

    /**
     * Message Priority Levels
     */
    public enum Level {
        Low,
        Normal,
        High;

        public Level next() {
            int nextIndex = ordinal() + 1;
            Level[] all = values();
            if (nextIndex >= all.length) {
                nextIndex = 0;
            }
            return all[nextIndex];
        }
    }

    /**
     * Add a message priority value to the data JSON object.
     * @param data the JSON object to add the priority to, if null is provided a new JSON object will be created
     * @param priority the message priority level
     * @return the JSON object with the priority added
     */
    public static JSONObject setPriority(JSONObject data, Level priority) {
        if (priority != Level.Normal) {
            try {
                if (data == null) {
                    data = new JSONObject();
                }
                data.put(PRIORITY, priority.toString());
            } catch (JSONException je) {
                Logger.w(je, "Failed to set priority");
            }
        }
        return data;
    }

    /**
     * Parse the message priority from the data JSON object.
     * @param data the JSON object containing the priority value
     * @return the priority level, if no priority exists in the JSON object {@link Level#Normal} is returned
     */
    public static Level getPriority(@NonNull JSONObject data) {
        String priority = data.optString(PRIORITY);
        if (!TextUtils.isEmpty(priority)) {
            return MessagePriority.Level.valueOf(data.optString(PRIORITY));
        }
        return Level.Normal;
    }
}
