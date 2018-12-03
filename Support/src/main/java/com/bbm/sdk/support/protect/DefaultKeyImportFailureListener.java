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

package com.bbm.sdk.support.protect;

import android.support.annotation.NonNull;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.Chat;
import com.bbm.sdk.bbmds.inbound.ChatKeysImportFailure;
import com.bbm.sdk.bbmds.inbound.ProfileKeysImportFailure;
import com.bbm.sdk.bbmds.inbound.UserKeysImportFailure;
import com.bbm.sdk.bbmds.outbound.ChatLeave;
import com.bbm.sdk.support.util.Logger;

import java.util.ArrayList;

/**
 * A default implementation of the KeyImportFailureListener.
 * This listener will force a new export of ProfileKeys when a ProfileKeysImportFailure occurs.
 * No action will be taken on UserKeysImportFailures or ChatKeysImportFailures.
 */
public class DefaultKeyImportFailureListener implements KeyImportFailureListener {

    private KeyStorageProvider mKeyStorageProvider;

    public DefaultKeyImportFailureListener(@NonNull KeyStorageProvider keyStorageProvider) {
        mKeyStorageProvider = keyStorageProvider;
    }

    @Override
    public void onProfileKeysImportFailure(ProfileKeysImportFailure profileKeysImportFailure) {
        Logger.e("Failure to import local users profile keys from cloud storage, removing existing keys");
        //If the existing profile keys fail to import there isn't much recourse.
        //We can really only clear the existing keys from our cloud storage and have new keys exported
        mKeyStorageProvider.removeProfileKeys(new KeyStorageResponse<Void>() {
            @Override
            public void onSuccess(Void value) {
                //Trigger ProtectedManager to try again
                Logger.d("Removed local users profile keys, retry to trigger new key export");
                ProtectedManager.getInstance().retryFailedEvents();
            }

            @Override
            public void onFailure() {
                Logger.e("Failed to remove local users profile keys from cloud storage");
            }
        });
    }

    @Override
    public void onUserKeysImportFailure(UserKeysImportFailure userKeysImportFailure) {
        Logger.e("Failure to import keys for user(s) " + userKeysImportFailure.regIds);
        //Recovery from userKeysImportFailure will most likely require the other party to re-generate their profile keys.
    }

    @Override
    public void onChatKeysImportFailure(ChatKeysImportFailure chatKeysImportFailure) {
        Logger.e("Failure to import keys for mailboxes " + chatKeysImportFailure.mailboxIds.toString());
        //If a chat key failed to import then we should leave the chat.
        //We can be re-invited to the chat to restore the content.
        ArrayList<String> chatsToLeave = new ArrayList<>();
        for (String mailboxId : chatKeysImportFailure.mailboxIds) {
            for (Chat chat : BBMEnterprise.getInstance().getBbmdsProtocol().getChatList().get()) {
                if (chat.mailboxId.equals(mailboxId)) {
                    Logger.d("Failed to import chatId " + chat.chatId + " mailboxId " + mailboxId);
                    chatsToLeave.add(chat.chatId);
                }
            }
        }

        if (chatsToLeave.size() > 0) {
            BBMEnterprise.getInstance().getBbmdsProtocol().send(new ChatLeave(chatsToLeave));
        }
    }
}
