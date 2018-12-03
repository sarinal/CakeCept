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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.Chat;
import com.bbm.sdk.bbmds.ChatMessage;
import com.bbm.sdk.bbmds.ChatParticipant;
import com.bbm.sdk.bbmds.ChatParticipantCriteria;
import com.bbm.sdk.bbmds.GlobalLocalUri;
import com.bbm.sdk.bbmds.Typing;
import com.bbm.sdk.bbmds.User;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.internal.lists.ObservableList;
import com.bbm.sdk.bbmds.outbound.ChatMessageRead;
import com.bbm.sdk.common.IOHelper;
import com.bbm.sdk.reactive.ComputedList;
import com.bbm.sdk.reactive.ObservableTracker;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.reactive.TrackedGetter;
import com.bbm.sdk.support.identity.user.AppUser;
import com.bbm.sdk.support.identity.user.UserManager;
import com.bbm.sdk.support.reactive.AbstractObservableValue;
import com.bbm.sdk.support.reactive.ObserveConnector;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Utility methods to help use the BBM SDK.
 * These methods may change or be removed in future releases as the BBM SDK API's are simplified so that they are no longer needed.
 */
public class BbmUtils {
    /**
     * The prefix to convert a chat ID to a chat URI.
     * This is only needed for BBM SDK API's that require a chat URI instead of chat ID.
     * The need for this will be removed in a future release.
     */
    public static final String CHAT_URI_PREFIX = "bbmpim://chat/";

    private static final MessageManager mMessageManager = new MessageManager();


    /**
     * Convenience method to get the local user object since first you need to get the
     * GlobalLocalUri and wait for it to exist before you can get the User for it.
     */
    public static ObservableValue<User> getLocalBbmUser() {
        return new AbstractObservableValue<User>() {
            User user;

            /**
             * Returns the current value of the observable.
             *
             * @return The current value of the observable.
             * @trackedgetter This method is a {@link TrackedGetter}
             */
            @TrackedGetter
            @Override
            public User get() {
                ObservableTracker.getterCalled(this);
                if (user == null) {
                    //set with dummy data for now
                    user = new User();
                    final ObservableValue<GlobalLocalUri> localUri = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalLocalUri();
                    ObserveConnector.getsSharedInstance().connect(localUri, new Observer() {
                        @Override
                        public void changed() {
                            if (localUri.get().getExists() == Existence.YES && !localUri.get().value.isEmpty()) {
                                //have the local URI, now get the local BBM user for it
                                final ObservableValue<User> localUser = BBMEnterprise.getInstance().getBbmdsProtocol().getUser(localUri.get().value);
                                //this observer for localUri isn't needed, remove it and add one for the user
                                ObserveConnector.getsSharedInstance().remove(this);
                                ObserveConnector.getsSharedInstance().connect(localUser, new Observer() {
                                    @Override
                                    public void changed() {
                                        if (localUser.get().getExists() != Existence.MAYBE) {
                                            //have the real data, set it
                                            user = localUser.get();
                                            //notify observers of this
                                            notifyObservers();
                                        }
                                    }
                                }, true); //call the changed immediately in case it exists now
                            }
                        }
                    }, true); //call the changed immediately in case it exists now
                }

                return user;
            }
        };
    }

    /**
     * Method to save a draft message to the chat indicated in the chatId.
     *
     * @param chatId  The chatId, should not be null
     * @param message The message to be saved
     */
    public static void saveDraftMessage(final String chatId, final String message) {

        if (TextUtils.isEmpty(chatId)) {
            return;
        }
        try {
            final JSONObject draft = new JSONObject();
            draft.put("message", message == null ? "" : message);

            final JSONObject data = new JSONObject();
            data.put("draft", draft);

            //the chat might not be loaded yet so use a monitor to set the data either now if chat exists, or later when it does
            SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
                @Override
                public boolean run() {
                    Chat chat = BBMEnterprise.getInstance().getBbmdsProtocol().getChat(chatId).get();

                    if (chat == null || chat.getExists() != Existence.YES) {
                        return false;
                    }

                    Chat.AttributesBuilder builder = new Chat.AttributesBuilder().localData(data);
                    BBMEnterprise.getInstance().getBbmdsProtocol().send(chat.requestListChange(builder));
                    return true;
                }
            });
        } catch (JSONException e) {
            Logger.e(e);
        }
    }

    public static String getUserName(final User user) {
        if (user.exists == Existence.YES) {
            AppUser appUser = UserManager.getInstance().getUser(user.regId).get();
            if (appUser.getExists() == Existence.YES && !TextUtils.isEmpty(appUser.getName())) {
                return appUser.getName();
            }
        }

        return String.valueOf(user.regId);
    }

    public static String getAppUserName(final AppUser appUser) {
        AppUser localUser = UserManager.getInstance().getLocalAppUser().get();

        if (appUser.getRegId() == localUser.getRegId()
                && !TextUtils.isEmpty(localUser.getName())) {
            return localUser.getName();
        }

        if (!TextUtils.isEmpty(appUser.getName())) {
            return appUser.getName();
        }

        return String.valueOf(appUser.getRegId());
    }

    /**
     * Mark all the incoming chat messages as read for the specified chat.
     * This results in the sender getting updated that the messages they sent are now read by the local user.
     *
     * @param chatId The chat ID
     */
    public static void markChatMessagesAsRead(String chatId) {
        mMessageManager.markChatMessagesAsRead(chatId);
    }

    private static final ConcurrentMap<String, ComputedList<ChatParticipant>> mConversationParticipantListMap = new com.google.common.collect.MapMaker().weakKeys().weakValues().makeMap();

    public static ComputedList<ChatParticipant> getChatParticipantList(final String chatId) {
        if (TextUtils.isEmpty(chatId)) {
            return new ComputedList<ChatParticipant>() {
                @Override
                protected List<ChatParticipant> compute() {
                    return Collections.emptyList();
                }
            };
        }

        ComputedList<ChatParticipant> list = mConversationParticipantListMap.get(chatId);
        if (list == null) {
            list = new ComputedList<ChatParticipant>() {

                private ChatParticipantCriteria mCriteria;

                @Override
                protected List<ChatParticipant> compute() {
                    if (mCriteria == null) {
                        mCriteria = new ChatParticipantCriteria().chatId(chatId);
                    }
                    ObservableList<ChatParticipant> list = BBMEnterprise.getInstance().getBbmdsProtocol().getChatParticipantList(mCriteria);
                    if (list.isPending()) {
                        return Collections.emptyList();
                    } else {
                        return list.get();
                    }
                }
            };
            mConversationParticipantListMap.put(chatId, list);
        }
        return list;
    }

    private static final ConcurrentMap<String, ComputedList<User>> mTypingUsersMap = new com.google.common.collect.MapMaker().weakKeys().weakValues().makeMap();

    /**
     * Provides a computed list of users who are currently typing in the provided chat id.
     * If no users are typing an empty list is returned.
     *
     * @param chatId a chat id
     * @return computed list of typing Users.
     */
    public static ComputedList<User> getUsersTypingInChat(@NonNull final String chatId) {

        if (TextUtils.isEmpty(chatId)) {
            return new ComputedList<User>() {
                @Override
                protected List<User> compute() {
                    return Collections.emptyList();
                }
            };
        }

        ComputedList<User> list = mTypingUsersMap.get(chatId);
        if (list == null) {
            list = new ComputedList<User>() {

                @Override
                protected List<User> compute() {
                    List<Typing> typingList = BBMEnterprise.getInstance().getBbmdsProtocol().getTypingList().get();
                    ArrayList<User> typingUsers = new ArrayList<>();
                    for (Typing typing : typingList) {
                        if (typing.chatId.equals(chatId)) {
                            User user = BBMEnterprise.getInstance().getBbmdsProtocol().getUser(typing.userUri).get();
                            if (user.getExists() == Existence.NO) {
                                return Collections.emptyList();
                            }
                            typingUsers.add(user);
                        }
                    }
                    return typingUsers;
                }
            };
            mTypingUsersMap.put(chatId, list);
        }
        return list;
    }

    private static class MessageManager {

        private final MarkMessageAsReadMonitor sMarkMessageAsReadMonitor = new MarkMessageAsReadMonitor();

        private class MarkMessageAsReadMonitor extends SingleshotMonitor {

            private String mChatId;

            public void setChatId(final String chatId) {
                mChatId = chatId;
            }

            @Override
            protected boolean runUntilTrue() {
                final Chat chat = BBMEnterprise.getInstance().getBbmdsProtocol().getChat(mChatId).get();

                if (chat.exists == Existence.YES) {
                    if (chat.numMessages == 0 || chat.lastMessage == 0) {
                        // Exit out of the monitor, since there are no messages in this conversation
                        return true;
                    }

                    final ChatMessage.ChatMessageKey lookupKey = new ChatMessage.ChatMessageKey(mChatId, chat.lastMessage);
                    final ChatMessage lastMessage = BBMEnterprise.getInstance().getBbmdsProtocol().getChatMessage(lookupKey).get();

                    if (lastMessage.exists == Existence.YES) {
                        if ((lastMessage.hasFlag(ChatMessage.Flags.Incoming))
                                && lastMessage.state != ChatMessage.State.Read) {
                            BBMEnterprise.getInstance().getBbmdsProtocol().send(
                                    new ChatMessageRead(mChatId, chat.lastMessage)
                            );
                        }

                        return true;
                    }
                }


                return false;
            }

        }

        private void markChatMessagesAsRead(final String chatId) {
            sMarkMessageAsReadMonitor.setChatId(chatId);
            sMarkMessageAsReadMonitor.activate();
        }
    }

    /**
     * Zip up the current BBM SDK log files from files and open in intent to be shared
     * with email, BBM, or any other app that can handle it.
     *
     * @param applicationId The android application id of the app. Needed to access the fileprovider
     * @param context       The android context.
     */
    public static void sendBbmLogFiles(@NonNull final String applicationId, @NonNull final Context context) {
        File zipFile = null;
        FileOutputStream fos = null;
        ZipOutputStream zos = null;
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        try {
            //for the sample we just save the logs to a public directory but a real app might want to use
            //a FileProvider or other other more secure method
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            zipFile = File.createTempFile("BBM_SDK_LOGS", ".zip", dir);
            zipFile.setReadable(true, false);
            zipFile.deleteOnExit();

            fos = new FileOutputStream(zipFile);
            zos = new ZipOutputStream(fos);

            File bbmLogsDir = new File(BBMEnterprise.getInstance().getLogFileBasePath());

            if (bbmLogsDir.exists()) {
                File[] logFiles = bbmLogsDir.listFiles();
                if (logFiles != null && logFiles.length > 0) {
                    for (File logFile : logFiles) {
                        fis = new FileInputStream(logFile);
                        bis = new BufferedInputStream(fis);
                        ZipEntry zipEntry = new ZipEntry(logFile.getName());
                        zos.putNextEntry(zipEntry);
                        IOUtils.copy(bis, zos);
                    }
                } else {
                    Logger.user("Did not find BBM log files to send (Logs dir empty)");
                    return;
                }
            } else {
                Logger.user("Did not find BBM log files to send (Logs dir not found)");
                return;
            }

            IOHelper.safeClose(zos);
            Intent intent = new Intent(Intent.ACTION_SEND);

            Uri zipUri = FileProvider.getUriForFile(context, applicationId + ".fileprovider", zipFile);
            intent.putExtra(Intent.EXTRA_STREAM, zipUri);

            intent.setType("*/*");
            context.startActivity(intent);

            Logger.d("started intent " + intent + " to share " + zipFile + " with len=" + zipFile.length());
        } catch (IOException ioe) {
            Logger.user(ioe, "Failed to create zip file with BBM Logs");
        } catch (ActivityNotFoundException nfe) {
            Logger.user(nfe, "Failed to send zip file " + zipFile);
        } finally {
            //ensure everything is closed if something fails
            IOHelper.safeClose(fos);
            IOHelper.safeClose(zos);
            IOHelper.safeClose(fis);
            IOHelper.safeClose(bis);
        }
    }

    /**
     * Provides the draft message of the hosted chat
     *
     * @param chat The hosted chat item
     * @return the draft message if available, otherwise null
     */
    public static String getDraftMessage(final Chat chat) {
        if (chat == null) {
            return null;
        }
        JSONObject data = chat.localData;
        try {
            if (data != null && data.has("draft")) {
                JSONObject draft = data.getJSONObject("draft");
                if (draft.has("message")) {
                    return draft.getString("message");
                }
            }
        } catch (JSONException e) {
            Logger.e(e);
        }

        return null;
    }


    /**
     * Provides the draft view time of the hosted chat
     *
     * @param chat The hosted chat item
     * @return the draft view time if available, otherwise 0
     **/
    public static long getDraftViewTime(final Chat chat) {
        if (chat == null) {
            return 0;
        }
        JSONObject data = chat.localData;
        try {
            if (data.has("draft")) {
                JSONObject draft = data.getJSONObject("draft");
                if (draft.has("viewTime")) {
                    return draft.getLong("viewTime");
                }
            }
        } catch (JSONException e) {
            Logger.e(e);
        }

        return 0;
    }
}
