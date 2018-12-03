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

package com.bbm.sdk.support.util;


import android.support.annotation.NonNull;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.Chat;
import com.bbm.sdk.bbmds.inbound.ChatStartFailed;
import com.bbm.sdk.bbmds.outbound.ChatStart;
import com.bbm.sdk.service.ProtocolMessage;
import com.bbm.sdk.service.ProtocolMessageConsumer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Helper utility to simplify starting a new chat and listening for the creation of that chat.
 * This allows the caller to just provide a {@link ChatStartedCallback} instead of needing to
 * implement a ProtocolMessageConsumer.
 */
public class ChatStartHelper {

    /**
     * Start a new one to one chat with the provided regId.
     * @param regId registration id of the user to chat with
     * @param callback notified when the chat is created
     */
    public static void startNewOneToOneChat(long regId, final ChatStartedCallback callback) {
        startChat(new long[]{regId}, null, true, callback);
    }

    /**
     * Start a new chat with the provided list of regIds.
     * @param regIds registration ids of the users to invite to the chat
     * @param chatSubject subject for the chat
     * @param callback notified when the chat is created
     */
    public static void startNewChat(final long[] regIds, String chatSubject, final ChatStartedCallback callback) {
        startChat(regIds, chatSubject, false, callback);
    }

    private static void startChat(final long[] regIds, String chatSubject, boolean oneToOne, final ChatStartedCallback callback) {
        ArrayList<ChatStart.Invitees> invitees = new ArrayList<>();
        for (int i=0;i<regIds.length;++i) {
            if (regIds[i] != 0) {
                invitees.add(new ChatStart.Invitees().regId(regIds[i]));
            }
        }

        final String cookie = UUID.randomUUID().toString();

        if (chatSubject == null) {
            //message will be rejected if chatSubject is null/missing, but empty is ok
            chatSubject = "";
        }

        final ChatStart chatStart = new ChatStart(cookie, invitees, chatSubject).isOneToOne(oneToOne);

        Logger.d("startNewChat: about to send "+chatStart);

        //need to listen for when chat is created/found
        BBMEnterprise.getInstance().getBbmdsProtocolConnector().addMessageConsumer(new ProtocolMessageConsumer() {
            @Override
            public void onMessage(ProtocolMessage message) {
                final JSONObject json = message.getData();
                Logger.d("onMessage: "+message);
                if (cookie.equals(json.optString("cookie",""))) {
                    //this is for us, stop listening
                    BBMEnterprise.getInstance().getBbmdsProtocolConnector().removeMessageConsumer(this);

                    if ("chatStartFailed".equals(message.getType())) {
                        ChatStartFailed chatStartFailed = new ChatStartFailed().setAttributes(json);
                        if (chatStartFailed.reason == ChatStartFailed.Reason.AlreadyExists) {
                            callback.onChatStarted(chatStartFailed.chatId);
                        } else {
                            Logger.i("Failed to create chat with " + chatStart);
                            callback.onChatStartFailed(chatStartFailed.reason);
                        }
                    } else {
                        try {
                            final JSONArray elementsArray = json.getJSONArray("elements");
                            final Chat chat = new Chat().setAttributes(elementsArray.getJSONObject(0));
                            callback.onChatStarted(chat.chatId);
                        } catch (final JSONException e) {
                            Logger.e(e, "Failed to process start chat message "+message);
                            callback.onChatStartFailed(ChatStartFailed.Reason.Unspecified);
                        }
                    }
                }
            }

            @Override
            public void resync() {
                Logger.d("resync: ");
            }
        });

        //now ask to create it
        BBMEnterprise.getInstance().getBbmdsProtocol().send(chatStart);
    }

    /**
     * Callback for when the chat has either been successfully started, or failed to start.
     */
    public interface ChatStartedCallback {
        void onChatStarted(@NonNull String chatId);
        void onChatStartFailed(ChatStartFailed.Reason reason);
    }
}
