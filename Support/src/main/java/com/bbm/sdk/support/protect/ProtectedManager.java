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

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.widget.Toast;

import com.bbm.sdk.BBMEConfig;
import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.BbmdsProtocol;
import com.bbm.sdk.bbmds.Chat;
import com.bbm.sdk.bbmds.ChatCriteria;
import com.bbm.sdk.bbmds.GlobalLocalUri;
import com.bbm.sdk.bbmds.GlobalProfileKeysState;
import com.bbm.sdk.bbmds.User;
import com.bbm.sdk.bbmds.UserCriteria;
import com.bbm.sdk.bbmds.inbound.ChatKey;
import com.bbm.sdk.bbmds.inbound.ChatKeysImportFailure;
import com.bbm.sdk.bbmds.inbound.Endpoints;
import com.bbm.sdk.bbmds.inbound.ProfileKeys;
import com.bbm.sdk.bbmds.inbound.ProfileKeysImportFailure;
import com.bbm.sdk.bbmds.inbound.UserKeysImportFailure;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.internal.lists.ObservableList;
import com.bbm.sdk.bbmds.outbound.ChatKeyExport;
import com.bbm.sdk.bbmds.outbound.ChatKeysImport;
import com.bbm.sdk.bbmds.outbound.ChatLeave;
import com.bbm.sdk.bbmds.outbound.EndpointDeregister;
import com.bbm.sdk.bbmds.outbound.EndpointsGet;
import com.bbm.sdk.bbmds.outbound.ProfileKeysExport;
import com.bbm.sdk.bbmds.outbound.ProfileKeysImport;
import com.bbm.sdk.bbmds.outbound.UserKeysImport;
import com.bbm.sdk.reactive.ComputedValue;
import com.bbm.sdk.reactive.Mutable;
import com.bbm.sdk.reactive.ObservableMonitor;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.service.InboundMessageObservable;
import com.bbm.sdk.service.ProtocolMessage;
import com.bbm.sdk.service.ProtocolMessageConsumer;
import com.bbm.sdk.support.R;
import com.bbm.sdk.support.identity.UserIdentityMapper;
import com.bbm.sdk.support.util.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import javax.crypto.Mac;

/**
 * The ProtectedManager handles all user and chat key management.
 * The ProtectedManager will request keys or send keys to the KeyStorageProvider as required by bbmcore.
 * Private keys are encrypted using a symmetric management key generated locally before storage.
 * A derived key is created using a password which should be supplied from a user.
 * The derived key is used to encrypt and MAC the management keys before storing them in the key storage provider.
 * <p>
 * To start the ProtectedManager an application context, keystorage provider, password provider and error handler are required.
 * {@link ProtectedManager#start(Context, String, KeyStorageProvider, PasscodeProvider, ErrorHandler)}
 * <p>
 *
 * <p>
 * This includes:
 * <ul>
 *  <li>- Creating (or restoring) a management key and MAC which are used to secure the private ProfileKeys and ChatKeys before storage
 *  <li>- Prompting the user to provide a password. The password is used to create a derived key for securing the management key and HMAC for storage.
 *  <li>- {@link #forgotPassword()} and {{@link #changePassword()}} are included for password management
 *  <li>- Monitoring the {@link GlobalProfileKeysState}
 *      - if no profile keys are returned from the KeyStorageProvider obtaining keys from bbmcore via {@link ProfileKeysExport}
 *      - if profile keys are provided by the KeyStorageProvider setting keys via {@link ProfileKeysImport}
 *  <li>- Fetching the public keys for all {@link User}s whose keyState == {@link com.bbm.sdk.bbmds.User.KeyState#Import}
 *    and passing their keys to bbmcore via {@link UserKeysImport}
 *  <li>- Fetching the chatKey for all {@link Chat}s whose keyState = {@link com.bbm.sdk.bbmds.Chat.KeyState#Import}
 *    and setting the keys via {@link ChatKeysImport}
 *  <li>- Monitoring the list of {@link Chat}s whose keyState == {@link com.bbm.sdk.bbmds.Chat.KeyState#Export}
 *    and requesting their keys from bbmcore via {@link ChatKeyExport}
 *
 * </ul>
 * <p>
 * Failed key storage actions can be retried using {@link ProtectedManager#retryFailedEvents()}
 * There is no backoff retry imposed by the ProtectedManager.
 * Your application should determine when to retry failed key storage operations.
 * <p>
 */
public class ProtectedManager implements ProtocolMessageConsumer {

    private static final String READ_PRIVATE_KEY_REQUEST = "ReadPrivateKeyRequest";
    private static final String READ_PUBLIC_KEY_REQUEST = "ReadPublicKeyRequest";
    private static final String WRITE_PROFILE_KEY_REQUEST = "WritePrivateKeyRequest";
    private static final String READ_CHAT_KEY_REQUEST = "ReadChatKeyRequest";
    private static final String WRITE_CHAT_KEY_REQUEST = "WriteChatKeyRequest";
    private static final String REMOVE_CHAT_KEY_REQUEST = "RemoveChatKeyRequest";
    private static final String REMOVE_PROFILE_KEYS = "RemoveProfileKeys";
    private static final String READ_MANAGEMENT_KEY_REQUEST = "ReadManagementKeysRequest";
    private static final String WRITE_MANAGEMENT_KEY_REQUEST = "WriteManagementKeysRequest";

    /**
     * Challenge for the existing password
     */
    private static final int PASSWORD_REQUEST_CHALLENGE = 0;
    /**
     * Set a new password (first time setup)
     */
    private static final int PASSWORD_REQUEST_SET = 1;
    /**
     * Change the existing password
     */
    private static final int PASSWORD_REQUEST_CHANGE = 2;
    /**
     * Challenge for the existing password (for the purpose to changing the password)
     */
    private static final int PASSWORD_REQUEST_CHANGE_CHALLENGE = 3;

    private int mPasswordRequestType;

    private static ProtectedManager sInstance;

    private boolean mStarted = false;
    private Context mContext;
    private String mUserUid;

    //Management key is used to encrypt private profile keys and chat keys
    private Key mManagementEncryptionKey;
    private Key mManagementHmacKey;
    //Management MAC used to validate private profile keys and chat keys
    private Mac mManagementMac;

    private Mutable<ProfileKeys> mProfileKeys = new Mutable<>(new ProfileKeys());
    private Mutable<EncryptedManagementKeys> mEncryptedManagementKeys = new Mutable<>(new EncryptedManagementKeys());
    private KeyStorageProvider mStorageProvider;
    private PasscodeProvider mPasscodeProvider;
    private ErrorHandler mErrorHandler;

    private PasscodeProvider.PasscodeError mPreviousPasscodeError = PasscodeProvider.PasscodeError.None;

    //Observable for our own profileKeys
    private InboundMessageObservable<ProfileKeys> mProfileKeysObservable;

    //List of chats for which we have sent a ChatKeyExport
    private HashMap<String, SingleshotMonitor> mChatsPendingKeyExportResponse = new HashMap<>();

    //List of users pending key import
    private HashSet<String> mUsersPendingKeyImport = new HashSet<>();

    //ChatId to Mailbox mapping. Used to track deletion of chats
    private HashMap<String, String> mChatIdToMailbox = new HashMap<>();

    private Handler mHandler = new Handler(Looper.getMainLooper());

    private KeyImportFailureListener mKeyImportFailureListener;

    //List of storage requests that are in progress
    private HashMap<String, AsyncTask> mPendingTasks = new HashMap<>();

    private final ArrayList<UserKeysImport.Keys> mUserKeysImportBatch = new ArrayList<>();

    /**
     * Runnable which sends a UserKeysImport message for all keys in the mUserKeysImportBatch list.
     */
    private Runnable mSendUserKeys = () -> {
        synchronized (mUserKeysImportBatch) {
            if (mUserKeysImportBatch.size() > 0) {
                BBMEnterprise.getInstance().getBbmdsProtocol().send(new UserKeysImport(mUserKeysImportBatch));
                mUserKeysImportBatch.clear();
            }
        }
    };

    private ComputedValue<User> mLocalUser = new ComputedValue<User>() {
        @Override
        protected User compute() {
            BbmdsProtocol protocol = BBMEnterprise.getInstance().getBbmdsProtocol();
            GlobalLocalUri myUri = protocol.getGlobalLocalUri().get();

            if (myUri.exists == Existence.MAYBE) {
                return null;
            }

            final User myUser = protocol.getUser(myUri.value).get();
            if (myUser.exists != Existence.YES || myUser.regId == 0) {
                return null;
            }

            return myUser;
        }
    };

    /**
     * This monitor observes the ProfileKeysState global.
     * If the state is NotSynced that means that we need to provide bbmcore with keys (if they exist).
     * If we do not have keys in our cloud storage we need to request new keys with ProfileKeysExport.
     */
    private ObservableMonitor mKeyStateMonitor = new ObservableMonitor() {
        @Override
        protected void run() {
            BbmdsProtocol protocol = BBMEnterprise.getInstance().getBbmdsProtocol();
            final GlobalProfileKeysState profileKeysState = protocol.getGlobalProfileKeysState().get();

            if (profileKeysState.exists == Existence.MAYBE) {
                return;
            }

            if (mLocalUser.get() == null) {
                return;
            }

            //bbmcore doesn't have any keys for our local user
            if (profileKeysState.value == GlobalProfileKeysState.State.NotSynced) {

                //Step 1
                //Check for management key and management hmac key in the database
                final EncryptedManagementKeys encryptedManagementKeys = mEncryptedManagementKeys.get();
                switch (encryptedManagementKeys.getState()) {
                    case EncryptedManagementKeys.PENDING:
                        //Check for existing management keys in cloud storage
                        readManagementKeys();
                        return;
                    case EncryptedManagementKeys.DECRYPTION_REQUIRED:
                        //We found existing keys and we need to decrypt and verify the keys
                        //Prompt the user for their password to generate the derived key and decrypt the management keys
                        mPasswordRequestType = PASSWORD_REQUEST_CHALLENGE;
                        mPasscodeProvider.provideExistingPasscode(false, mPreviousPasscodeError);
                        return;
                    case EncryptedManagementKeys.NO_KEYS_AVAILABLE:
                        //There are no management keys in the cloud storage
                        //Generate new management encryption keys
                        getManagementKeys(true);
                        //Request a new password
                        mPasswordRequestType = PASSWORD_REQUEST_SET;
                        mPasscodeProvider.requestNewPasscode(false, mPreviousPasscodeError);
                        return;
                    case EncryptedManagementKeys.COMPLETED:
                        //We've successfully retrieved existing keys or created new management keys
                        break;
                }

                //After we have successfully created or restored management keys
                //we can recover or export the ProfileKeys.
                final ProfileKeys profileKeys = mProfileKeys.get();
                if (profileKeys.exists == Existence.MAYBE) {
                    //Get the keys from our key storage provider
                    readLocalProfileKeys();
                } else if (profileKeys.exists == Existence.YES) {
                    importExistingProfileKeys(profileKeys);
                } else if (profileKeys.exists == Existence.NO) {
                    //The storage provider doesn't have any existing keys
                    //We need to request new keys from core
                    ProfileKeys keys = new ProfileKeys();
                    if (mProfileKeysObservable == null) {
                        mProfileKeysObservable = new InboundMessageObservable<>(
                                keys,
                                BBMEnterprise.getInstance().getBbmdsProtocolConnector()
                        );
                        protocol.send(new ProfileKeysExport());
                    }

                    String taskKey = WRITE_PROFILE_KEY_REQUEST + mLocalUser.get().regId;
                    if (mProfileKeysObservable.get().exists == Existence.YES && !mPendingTasks.containsKey(taskKey)) {
                        mProfileKeys.set(mProfileKeysObservable.get());
                        writeProfileKeys(profileKeysState, mProfileKeys.get(), taskKey);
                    }
                }
            }
        }
    };

    public void setPasscode(String password) {
        switch (mPasswordRequestType) {
            case PASSWORD_REQUEST_CHALLENGE:
                //Attempt to decrypt the management keys, if we succeed update the state of the encrypted management keys
                boolean success = decryptManagementKeys(password, mEncryptedManagementKeys.get());
                if (success) {
                    mEncryptedManagementKeys.get().setState(EncryptedManagementKeys.COMPLETED);
                    mPreviousPasscodeError = PasscodeProvider.PasscodeError.None;
                } else {
                    mPreviousPasscodeError = PasscodeProvider.PasscodeError.IncorrectPasscode;
                }
                //Notify any observers of the changes (or trigger another password prompt)
                mHandler.post(() -> mEncryptedManagementKeys.dirty());
                break;
            case PASSWORD_REQUEST_SET:
                //Attempt to encrypt the management keys, if we succeed update the state of the encrypted management keys
                encryptManagementKeys(password);
                mPreviousPasscodeError = PasscodeProvider.PasscodeError.None;
                //Mark the encrypted management keys state as completed
                //After successfully creating the management keys we can move on to storing the keys
                mEncryptedManagementKeys.get().setState(EncryptedManagementKeys.COMPLETED);
                //Notify any observers of the changes
                mHandler.post(() -> mEncryptedManagementKeys.dirty());
                break;
            case PASSWORD_REQUEST_CHANGE:
                //Re-encrypt the management keys with the newly provided password
                encryptManagementKeys(password);
                //Write the management keys back to the cloud storage provider
                writeManagementKeys();
                mPreviousPasscodeError = PasscodeProvider.PasscodeError.None;
                break;
            case PASSWORD_REQUEST_CHANGE_CHALLENGE:
                //Challenge the user for the existing password
                //This is required so we can obtain an un-encrypted version of the management keys
                success = decryptManagementKeys(password, mEncryptedManagementKeys.get());
                if (success) {
                    //Prompt the user for a new password
                    mPasswordRequestType = PASSWORD_REQUEST_CHANGE;
                    mPasscodeProvider.requestNewPasscode(true, mPreviousPasscodeError);
                    mPreviousPasscodeError = PasscodeProvider.PasscodeError.None;
                } else {
                    mPreviousPasscodeError = PasscodeProvider.PasscodeError.IncorrectPasscode;
                    mPasswordRequestType = PASSWORD_REQUEST_CHANGE_CHALLENGE;
                    mPasscodeProvider.provideExistingPasscode(true, mPreviousPasscodeError);
                }
                break;
        }
    }

    private void importExistingProfileKeys(ProfileKeys profileKeys) {
        //The storage provider had an existing set of keys
        ProfileKeysImport.PrivateKeys privateKeys =
                new ProfileKeysImport.PrivateKeys(
                        profileKeys.privateKeys.encryption,
                        profileKeys.privateKeys.signing
                );

        ProfileKeysImport.PublicKeys publicKeys =
                new ProfileKeysImport.PublicKeys(
                        profileKeys.publicKeys.encryption,
                        profileKeys.publicKeys.signing
                );

        //Tell core the private and public keys
        ProfileKeysImport importKeys = new ProfileKeysImport(privateKeys, publicKeys);
        BBMEnterprise.getInstance().getBbmdsProtocol().send(importKeys);
    }

    private void writeProfileKeys(GlobalProfileKeysState profileKeysState, ProfileKeys profileKeys, String taskKey) {
        mProfileKeys.set(mProfileKeysObservable.get());
        //Send keys to our key storage provider
        @SuppressLint("StaticFieldLeak") AsyncTaskStorageRequest<Void> writeProfileKeysRequest =
                new AsyncTaskStorageRequest<Void>(taskKey, mPendingTasks) {
            @Override
            public void onSuccess(Void value) {
                //Set the profileKeysState to synced
                GlobalProfileKeysState.AttributesBuilder mStateChange = new GlobalProfileKeysState.AttributesBuilder();
                mStateChange.value(GlobalProfileKeysState.State.Synced);
                BBMEnterprise.getInstance().getBbmdsProtocol().send(profileKeysState.requestListChange(mStateChange));
            }

            @Override
            public void onFailure() {
                Logger.e("ProtectedManager - Failed to write profile keys");
                onError(ErrorHandler.KEY_STORAGE_PROVIDER_ERROR);
            }

            @Override
            public void run() {
                try {
                    //Encrypt the Profile private key
                    EncryptedPayload privateEncKeyPayload = EncryptionHelper.protect(
                            profileKeys.privateKeys.encryption,
                            mManagementEncryptionKey,
                            mManagementMac);

                    //Encrypt the Profile private signing key using the management key
                    EncryptedPayload privateSigningKeyPayload = EncryptionHelper.protect(
                            profileKeys.privateKeys.signing,
                            mManagementEncryptionKey,
                            mManagementMac);

                    PrivateKeyPair privateKeyPair = new PrivateKeyPair(privateEncKeyPayload, privateSigningKeyPayload);
                    KeyPair publicKeyPair = new KeyPair(
                            new PlaintextKey(profileKeys.publicKeys.encryption),
                            new PlaintextKey(profileKeys.publicKeys.signing)
                    );
                    EncryptedProfileKeys encProfileKeys = new EncryptedProfileKeys(privateKeyPair, publicKeyPair);

                    //Write the profile keys to cloud storage
                    mStorageProvider.writeProfileKeys(encProfileKeys, mEncryptedManagementKeys.get(), this);
                } catch (GeneralSecurityException | UnsupportedEncodingException e) {
                    Logger.e(e, "Unable to encrypt profile keys for storage");
                    onError(ErrorHandler.ENCRYPTION_ERROR);
                }
            }
        };
        executeTask(writeProfileKeysRequest);
    }

    /**
     * Monitor the users with keyState = Import and fetch keys for any users in that list
     */
    private ObservableMonitor mUserKeysImportMonitor = new ObservableMonitor() {
        @Override
        protected void run() {
            UserCriteria criteria = new UserCriteria();
            criteria.keyState(User.KeyState.Import);
            ObservableList<User> observableUserList = BBMEnterprise.getInstance().getBbmdsProtocol().getUserList(criteria);

            if (observableUserList == null) {
                return;
            }

            List<User> usersRequiringKeyImport = observableUserList.get();

            for (final User user : usersRequiringKeyImport) {
                executePublicKeyRequestForUser(user.regId);
                mUsersPendingKeyImport.add(Long.toString(user.regId));
            }
        }
    };

    /**
     * Monitor the list of chats with keyState = Import
     * Request the chatKeys for those chat mailboxes from the key storage and provide them to bbmcore via ChatKeysImport
     */
    private ObservableMonitor mChatKeyImportMonitor = new ObservableMonitor() {
        @Override
        protected void run() {
            ChatCriteria criteria = new ChatCriteria().keyState(Chat.KeyState.Import);
            ObservableList<Chat> observableChatList = BBMEnterprise.getInstance().getBbmdsProtocol().getChatList(criteria);

            if (observableChatList == null) {
                return;
            }

            List<Chat> chatsRequiringKeyImport = observableChatList.get();
            for (final Chat chat : chatsRequiringKeyImport) {
                String taskKey = READ_CHAT_KEY_REQUEST + chat.mailboxId;
                if (!TextUtils.isEmpty(chat.mailboxId) && !mPendingTasks.containsKey(taskKey)) {
                    @SuppressLint("StaticFieldLeak") AsyncTaskStorageRequest<EncryptedPayload> chatKeyRequest =
                            new AsyncTaskStorageRequest<EncryptedPayload>(taskKey, mPendingTasks) {

                        @Override
                        public void run() {
                            if (mStorageProvider != null) {
                                mStorageProvider.readChatKey(chat.mailboxId, this);
                            }
                        }

                        @Override
                        public void onSuccess(EncryptedPayload value) {
                            // Tell core about the new key
                            if (value != null && value.getPayload() != null) {
                                try {
                                    String chatKey = EncryptionHelper.unprotectToString(value, mManagementEncryptionKey, mManagementMac);
                                    ArrayList<ChatKeysImport.Keys> keys = new ArrayList<>();
                                    keys.add(new ChatKeysImport.Keys(chatKey, chat.mailboxId));
                                    BBMEnterprise.getInstance().getBbmdsProtocol().send(new ChatKeysImport(keys));
                                    return;
                                } catch (GeneralSecurityException e) {
                                    Logger.e(e, "Unable to decrypt chat key for chatId " + chat.chatId);
                                    onError(ErrorHandler.DECRYPTION_ERROR);
                                }
                            } else {
                                Logger.w("ProtectedManager - No key found for mailbox id " + chat.mailboxId);
                            }
                            //If we don't have the keys in storage we should leave this chat.
                            //This gives an opportunity for remaining chat participants to re-invite us
                            Logger.w("ProtectedManager - Leaving chat with missing keys " + chat.chatId);
                            BBMEnterprise.getInstance().getBbmdsProtocol().send(new ChatLeave(Collections.singletonList(chat.chatId)));
                        }

                        @Override
                        public void onFailure() {
                            Logger.e("ProtectedManager - Failed to read chat key for chatId " + chat.chatId);
                            onError(ErrorHandler.KEY_STORAGE_PROVIDER_ERROR);
                        }

                    };
                    executeTask(chatKeyRequest);
                }
            }
        }
    };

    /**
     * Monitor the list of chats which have keyState = Export
     * for each of those chats request the keys via chatKeyExport and write the keys to the key storage
     */
    private ObservableMonitor mChatKeyExportMonitor = new ObservableMonitor() {
        @Override
        protected void run() {
            ChatCriteria criteria = new ChatCriteria().keyState(Chat.KeyState.Export);
            ObservableList<Chat> observableChatList = BBMEnterprise.getInstance().getBbmdsProtocol().getChatList(criteria);

            if (observableChatList == null) {
                return;
            }

            List<Chat> chatsRequiringKeyExport = observableChatList.get();
            for (final Chat chat : chatsRequiringKeyExport) {
                final String taskKey = WRITE_CHAT_KEY_REQUEST + chat.mailboxId;
                if (!TextUtils.isEmpty(chat.mailboxId) && !mChatsPendingKeyExportResponse.containsKey(chat.chatId) && !mPendingTasks.containsKey(taskKey)) {

                    final String cookie = UUID.randomUUID().toString();
                    final InboundMessageObservable<ChatKey> chatKeyObserver = new InboundMessageObservable<>(
                            new ChatKey(),
                            cookie,
                            BBMEnterprise.getInstance().getBbmdsProtocolConnector()
                    );

                    final SingleshotMonitor monitorChatKeyExport = new SingleshotMonitor() {
                        @Override
                        protected boolean runUntilTrue() {
                            final ChatKey chatKey = chatKeyObserver.get();
                            if (chatKey.exists == Existence.YES) {
                                //Create a request to write the new chat key
                                @SuppressLint("StaticFieldLeak") AsyncTaskStorageRequest<Void> request =
                                        new AsyncTaskStorageRequest<Void>(taskKey, mPendingTasks) {

                                    @Override
                                    public void run() {
                                        if (mStorageProvider != null) {
                                            try {
                                                //Encrypt the chat key
                                                EncryptedPayload encChatKey = EncryptionHelper.protect(chatKey.key, mManagementEncryptionKey, mManagementMac);
                                                mStorageProvider.writeChatKey(chat.mailboxId, encChatKey, this);
                                            } catch (GeneralSecurityException| UnsupportedEncodingException e) {
                                                Logger.e(e, "Error encrypting chat key for storage");
                                                onError(ErrorHandler.ENCRYPTION_ERROR);
                                            }
                                        }
                                    }

                                    @Override
                                    public void onSuccess(Void value) {
                                        mHandler.post(() -> {
                                            mChatsPendingKeyExportResponse.remove(chat.chatId);
                                            //Update chat key state to synced
                                            Chat.AttributesBuilder keyStateChange = new Chat.AttributesBuilder();
                                            keyStateChange.keyState(Chat.KeyState.Synced);
                                            BBMEnterprise.getInstance().getBbmdsProtocol().send(chat.requestListChange(keyStateChange));
                                        });
                                    }

                                    @Override
                                    public void onFailure() {
                                        Logger.e("ProtectedManager - Failed to write chat key for chat " + chat.chatId);
                                        onError(ErrorHandler.KEY_STORAGE_PROVIDER_ERROR);
                                    }
                                };
                                executeTask(request);
                            } else {
                                return false;
                            }
                            return true;
                        }
                    };

                    mChatsPendingKeyExportResponse.put(chat.chatId, monitorChatKeyExport);

                    // Start this from a new runnable to avoid tracking the result in this monitor
                    mHandler.post(() -> {
                        monitorChatKeyExport.activate();
                        BBMEnterprise.getInstance().getBbmdsProtocol().send(new ChatKeyExport(chat.chatId, cookie));
                    });
                }
            }
        }
    };


    /**
     * Monitor the list of chats, when a chat is removed then remove the corresponding mailbox from the key storage.
     */
    private ObservableMonitor mChatsRequiringMailboxRemovalMonitor = new ObservableMonitor() {
        @Override
        protected void run() {
            // Get the list of chats
            ObservableList<Chat> chatList = BBMEnterprise.getInstance().getBbmdsProtocol().getChatList();

            if (chatList == null || chatList.isPending()) {
                return;
            }

            // Create a list of known chatIds
            HashMap<String, String> knownChatIdToMailbox = new HashMap<>(chatList.size());
            for (Chat chat : chatList.get()) {
                if (!TextUtils.isEmpty(chat.mailboxId)) {
                    knownChatIdToMailbox.put(chat.chatId, chat.mailboxId);
                    //Remove all of the known chatIds from the chatId to mailbox map
                    mChatIdToMailbox.remove(chat.chatId);
                }
            }

            // We are left with the list of chat ids that are no longer in the chat list
            // We want to remove those mailboxes from our cloud storage.
            if (mChatIdToMailbox.size() > 0) {
                for (final String chatId : mChatIdToMailbox.keySet()) {
                    final String mailboxId = mChatIdToMailbox.get(chatId);
                    String removeChatKey = REMOVE_CHAT_KEY_REQUEST + mailboxId;
                    if (!mPendingTasks.containsKey(removeChatKey)) {
                        @SuppressLint("StaticFieldLeak") AsyncTaskStorageRequest<Void> request =
                                new AsyncTaskStorageRequest<Void>(removeChatKey, mPendingTasks) {
                            @Override
                            public void onSuccess(Void result) {
                                //Nothing to do
                            }

                            @Override
                            public void onFailure() {
                                Logger.e("ProtectedManager - Failed to remove chat key for chatId " + chatId);
                            }

                            @Override
                            public void run() {
                                if (mStorageProvider != null) {
                                    mStorageProvider.removeChatKey(mailboxId, this);
                                }
                            }
                        };
                        executeTask(request);
                    }
                }
            }

            mChatIdToMailbox.clear();
            // Repopulate the chatId to mailbox mapping
            mChatIdToMailbox.putAll(knownChatIdToMailbox);
        }
    };

    private ProtectedManager() {
    }

    /**
     * Check if the protected manager has been started.
     * @return true if the protected manager has been started.
     */
    public boolean isStarted() {
        return mStarted;
    }


    /**
     * Start the key manager which will then act on any key related bbmds requests from bbmcore.
     * @param context application context
     * @param userUid the uid of the local user obtained from the identity service
     * @param provider an implementation
     * @throws IllegalStateException if the ProtectedManager is activated without a KeyStorageProvider being set
     */
    public void start(@NonNull Context context,
                      @NonNull String userUid,
                      @NonNull KeyStorageProvider provider,
                      @NonNull PasscodeProvider passcodeProvider,
                      @NonNull ErrorHandler errorHandler) {
        Logger.i("ProtectedManager - start");
        mStorageProvider = provider;
        mPasscodeProvider = passcodeProvider;
        mErrorHandler = errorHandler;
        mContext = context;
        mUserUid = userUid;
        //Get the management encryption keys
        getManagementKeys(false);
        mHandler.post(() -> {
            mStarted = true;
            mKeyStateMonitor.activate();
            mChatKeyExportMonitor.activate();
            mChatKeyImportMonitor.activate();
            mUserKeysImportMonitor.activate();
            mChatsRequiringMailboxRemovalMonitor.activate();
        });
        BBMEnterprise.getInstance().getBbmdsProtocolConnector().addMessageConsumer(this);
    }

    /**
     * Get the instance of the key manager.
     * @return singleton instance of ProtectedManager.
     */
    public static synchronized ProtectedManager getInstance() {
        if (sInstance == null) {
            sInstance = new ProtectedManager();
        }
        return sInstance;
    }

    /**
     * Stop all monitoring of key related bbmds messages.
     */
    public void stop() {
        Logger.i("ProtectedManager - stop");
        mStarted = false;
        mProfileKeysObservable = null;
        mKeyStateMonitor.dispose();
        mChatKeyExportMonitor.dispose();
        mChatKeyImportMonitor.dispose();
        mUserKeysImportMonitor.dispose();
        mChatsRequiringMailboxRemovalMonitor.dispose();
        mChatsPendingKeyExportResponse.clear();
        mUsersPendingKeyImport.clear();
        mChatIdToMailbox.clear();
        //Reset the saved profile keys for the local user.
        mProfileKeys.set(new ProfileKeys());
        mEncryptedManagementKeys.set(new EncryptedManagementKeys());
        //Note: in progress tasks will still run to completion
        mPendingTasks.clear();
    }

    /**
     * Cancel all pending key storage requests and re-compute any required key management.
     */
    public void retryFailedEvents() {
        if (mStorageProvider != null && mStarted) {
            //Clear any pending tasks
            mChatsPendingKeyExportResponse.clear();
            mUsersPendingKeyImport.clear();
            mProfileKeysObservable = null;
            //Reset the current profile keys (create a new mutable to avoid triggering the monitor to run again)
            mProfileKeys = new Mutable<>(new ProfileKeys());

            //Trigger all of our monitors to run again which will recreate any key import/export needed
            mKeyStateMonitor.activate();
            mUserKeysImportMonitor.activate();
            mChatKeyImportMonitor.activate();
            mChatKeyExportMonitor.activate();
            mChatsRequiringMailboxRemovalMonitor.activate();
        }
    }

    /**
     * Get the management key and MAC from the keystore/shared preferences.
     * If no keys currently exist new keys are created.
     * @param clearExisting if true any existing keys saved in the keystore/shared preferences are cleared
     */
    private void getManagementKeys(boolean clearExisting) {
        if (clearExisting) {
            EncryptionHelper.clearKeys(mContext);
        }
        try {
            mManagementEncryptionKey = EncryptionHelper.getManagementEncryptionKey(mContext);
            mManagementHmacKey = EncryptionHelper.getManagementHMACKey(mContext);
            mManagementMac = EncryptionHelper.createHMAC(mManagementHmacKey);
        } catch (GeneralSecurityException e) {
            Logger.e(e,"SecurityException when creating or retrieving management keys from the keystore");
            onError(ErrorHandler.DEVICE_KEYSTORE_ERROR);
        } catch (IOException e) {
            Logger.e(e, "IOException creating new management keys");
        }
    }

    /**
     * Return the users registration id as a string.
     */
    private String getRegIdAsString() {
        return Long.toString(mLocalUser.get().regId);
    }

    /**
     * Decrypt the management keys recovered from the storage provider.
     * @param password the users provided password
     * @param encryptedManagementKeys the encrypted management and hmac key
     * @return true if the management keys were successfully decrypted
     */
    private boolean decryptManagementKeys(String password, EncryptedManagementKeys encryptedManagementKeys) {
        try {
            //First create a derived key from the password
            String userDomain = BBMEConfig.getInstance(mContext).getClientOptions().getUserDomain();
            EncryptionHelper.DerivedRootKey rootKey = EncryptionHelper.createDerivedKey(password, getRegIdAsString(), userDomain);
            Mac derivedMac = EncryptionHelper.createHMAC(rootKey.derivedHmacKey);

            //Decrypt the management encryption key
            byte[] managementKeyBytes = EncryptionHelper.unprotectToByteArray(
                    encryptedManagementKeys.getEncrypt(),
                    rootKey.derivedEncryptionKey,
                    derivedMac);

            //Decrypt the management hmac key
            byte[] hmacKeyBytes = EncryptionHelper.unprotectToByteArray(
                    encryptedManagementKeys.getSign(),
                    rootKey.derivedEncryptionKey,
                    derivedMac);

            //Create new security keys using the decrypted key content
            mManagementEncryptionKey = EncryptionHelper.saveManagementEncryptionKey(mContext, managementKeyBytes);
            mManagementHmacKey = EncryptionHelper.saveHMACManagementKey(mContext, hmacKeyBytes);
            mManagementMac = EncryptionHelper.createHMAC(mManagementHmacKey);
            return true;
        } catch (GeneralSecurityException | IOException e) {
            Logger.e(e, "Unable to decrypt management keys");
        }

        return false;
    }

    /**
     * Encrypt the management keys using the provided password to create a derived key.
     * @param password the user provided password
     */
    private void encryptManagementKeys(String password) {
        try {
            //First create a derived key from the password
            String userDomain = BBMEConfig.getInstance(mContext).getClientOptions().getUserDomain();
            EncryptionHelper.DerivedRootKey rootKey = EncryptionHelper.createDerivedKey(password, getRegIdAsString(), userDomain);
            Mac derivedMac = EncryptionHelper.createHMAC(rootKey.derivedHmacKey);

            //Encrypt the management key
            EncryptedPayload encryptedManagementKey =
                    EncryptionHelper.protect(mManagementEncryptionKey.getEncoded(), rootKey.derivedEncryptionKey, derivedMac);

            //Encrypt the hmac key
            EncryptedPayload encryptedHmacKey =
                    EncryptionHelper.protect(mManagementHmacKey.getEncoded(), rootKey.derivedEncryptionKey, derivedMac);

            final EncryptedManagementKeys combinedKeys =
                    new EncryptedManagementKeys(new PrivateKeyPair(encryptedManagementKey, encryptedHmacKey));
            mEncryptedManagementKeys.set(combinedKeys);
        } catch (UnsupportedEncodingException | GeneralSecurityException e) {
            Logger.e(e, "Unable to encrypt management keys");
            onError(ErrorHandler.ENCRYPTION_ERROR);
        }
    }

    public void setKeyImportFailureListener(KeyImportFailureListener failureListener) {
        mKeyImportFailureListener = failureListener;
    }

    private void executePublicKeyRequestForUser(final long regId) {
        String taskKey = READ_PUBLIC_KEY_REQUEST + regId;
        // Check if we have already scheduled a request for this user
        // Or if we are still completing the key import
        if (!mPendingTasks.containsKey(taskKey) && !mUsersPendingKeyImport.contains(Long.toString(regId))) {
            SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
                //Find the uid mapping for the regId value
                ObservableValue<UserIdentityMapper.IdentityMapResult> mapResult =
                        UserIdentityMapper.getInstance().getUidForRegId(regId, true);
                @Override
                public boolean run() {
                    if (mapResult.get().existence == Existence.MAYBE) {
                        return false;
                    }

                    if (mapResult.get().existence == Existence.NO) {
                        Logger.e("ProtectedManager - unable to sync keys, could not get uid for regId " + regId);
                        return true;
                    }

                    //Create a request to fetch the keys for the UID we found
                    AsyncTaskStorageRequest<KeyPair> publicKeyRequest = createExternalPublicKeyRequest(
                            mapResult.get().uid,
                            regId,
                            taskKey
                    );
                    executeTask(publicKeyRequest);

                    return true;
                }
            });
        }
    }

    @SuppressLint("StaticFieldLeak")
    private AsyncTaskStorageRequest<KeyPair> createExternalPublicKeyRequest(
            final String uid,
            final long regId,
            final String taskKey) {
        // Create a request
        return new AsyncTaskStorageRequest<KeyPair>(taskKey, mPendingTasks) {

            @Override
            public void run() {
                if (mStorageProvider != null) {
                    //Request the public keys from the storage provider
                    mStorageProvider.readPublicKeys(uid, this);
                }
            }

            @Override
            public void onSuccess(KeyPair value) {
                if (value != null) {
                    //Send keys to core
                    synchronized (mUserKeysImportBatch) {
                        mUserKeysImportBatch.add(new UserKeysImport.Keys(
                                value.getEncrypt().getKey(),
                                Long.toString(regId),
                                value.getSign().getKey()
                        ));
                        mHandler.removeCallbacks(mSendUserKeys);
                        //Slightly delay sending the keys in case we have more keys to send which we can batch.
                        mHandler.postDelayed(mSendUserKeys, 10);
                    }
                } else {
                    // The cloud storage service doesn't have any keys for the given user uri
                    // This shouldn't really happen...
                    Logger.e("ProtectedManager - No keys found in cloud storage for regId=" + regId + ", uid=" + uid);
                }
            }

            @Override
            public void onFailure() {
                Logger.e("ProtectedManager - Failed to read publics keys for regId " +  regId);
            }
        };
    }


    /**
     * Request the public keys for all known users provide them to bbmcore.
     * This is a one time action.
     */
    public void resyncAllUserKeys() {
        SingleshotMonitor.run(() -> {
            UserCriteria importCriteria = new UserCriteria();
            importCriteria.keyState(User.KeyState.Import);
            ObservableList<User> importUsers = BBMEnterprise.getInstance().getBbmdsProtocol().getUserList(importCriteria);

            UserCriteria syncedCriteria = new UserCriteria();
            syncedCriteria.keyState(User.KeyState.Synced);
            ObservableList<User> syncedUsers = BBMEnterprise.getInstance().getBbmdsProtocol().getUserList(syncedCriteria);

            if (importUsers.isPending() || syncedUsers.isPending()) {
                return false;
            }

            ArrayList<User> allUsersToResync = new ArrayList<>(importUsers.size() + syncedUsers.size());
            allUsersToResync.addAll(importUsers.get());
            allUsersToResync.addAll(syncedUsers.get());
            mHandler.post(() -> {
                for (User user : allUsersToResync) {
                    executePublicKeyRequestForUser(user.regId);
                }
            });

            return true;
        });

    }

    /**
     * Prompt the user for the existing password, then allow a new password to be set.
     * Setting a new password will re-encrypt the existing management AES and hmac keys with a new derived key.
     */
    public void changePassword() {
        @SuppressLint("StaticFieldLeak") AsyncTaskStorageRequest<EncryptedManagementKeys> managementKeysRequest =
                new AsyncTaskStorageRequest<EncryptedManagementKeys>(READ_MANAGEMENT_KEY_REQUEST, mPendingTasks) {
            @Override
            public void onSuccess(EncryptedManagementKeys encryptedManagementKeys) {
                mEncryptedManagementKeys.set(encryptedManagementKeys);
                if (encryptedManagementKeys != null && encryptedManagementKeys.getEncrypt() != null
                        && encryptedManagementKeys.getSign() != null) {
                    //By design the Android key store doesn't allow an application to retrieve the raw key data
                    //from a SecretKey if its obtained from the keystore.
                    //See https://stackoverflow.com/questions/42348944/calling-getencoded-on-secretkey-returns-null
                    //This means we have to first get the key data by restoring it from the cloud and decrypting it
                    //First prompt the user for the existing password and confirm
                    mPasswordRequestType = PASSWORD_REQUEST_CHANGE_CHALLENGE;
                    mPasscodeProvider.provideExistingPasscode(true, mPreviousPasscodeError);
                }
            }

            @Override
            public void onFailure() {
                Logger.e("ProtectedManager - Failed to read management keys for local user");
                Toast.makeText(mContext, R.string.password_cant_be_changed, Toast.LENGTH_LONG).show();
            }

            @Override
            public void run() {
                if (mStorageProvider != null) {
                    mStorageProvider.readManagementKeys(this);
                }
            }
        };
        executeTask(managementKeysRequest);
    }

    /**
     * Write the management keys to the cloud storage.
     */
    private void writeManagementKeys() {

        //Write the encrypted management and hmac keys to the cloud storage
        @SuppressLint("StaticFieldLeak") AsyncTaskStorageRequest<Void> request =
                new AsyncTaskStorageRequest<Void>(WRITE_MANAGEMENT_KEY_REQUEST, mPendingTasks) {

                    @Override
                    public void onSuccess(Void value) {
                        Logger.d("successfully stored management keys with updated password");
                    }

                    @Override
                    public void onFailure() {
                        Logger.e("Failed to write encrypted management keys to cloud storage");
                    }

                    @Override
                    public void run() {
                        if (mStorageProvider != null) {
                            mStorageProvider.writeManagementKeys(mEncryptedManagementKeys.get(), this);
                        }
                    }
                };
        request.execute();
    }

    /**
     * Forgot password removes all endpoints (including the active endpoint).
     * The user can re-run the application to create new password.
     * Any existing chats will be lost and must be re-joined.
     */
    public void forgotPassword() {
        if (!mPendingTasks.containsKey(REMOVE_PROFILE_KEYS)) {
            @SuppressLint("StaticFieldLeak") AsyncTaskStorageRequest<Void> removeProfileKeysRequest =
                    new AsyncTaskStorageRequest<Void>(REMOVE_PROFILE_KEYS, mPendingTasks) {

                        @Override
                        public void run() {
                            if (mStorageProvider != null) {
                                mStorageProvider.removeProfileKeys(this);
                            }
                        }

                        @Override
                        public void onSuccess(Void value) {
                            //Log the user out of all endpoints
                            // Create a cookie to track the request.
                            final String requestCookie = UUID.randomUUID().toString();

                            // Create a one time inbound message observer that will have the results.
                            final InboundMessageObservable<Endpoints> endpointsObserver = new InboundMessageObservable<>(
                                    new Endpoints(), requestCookie, BBMEnterprise.getInstance().getBbmdsProtocolConnector());

                            mHandler.post(() ->
                                    SingleshotMonitor.run(() -> {
                                        Endpoints endpoints = endpointsObserver.get();
                                        if (endpoints.exists == Existence.MAYBE) {
                                            return false;
                                        }
                                        removeEndpoints(endpoints);
                                        return true;
                                    }));

                            // Activate the monitor and issue the protocol request.
                            BBMEnterprise.getInstance().getBbmdsProtocol().send(new EndpointsGet(requestCookie));
                        }

                        @Override
                        public void onFailure() {
                            //Unable to forget password
                        }
                    };
            executeTask(removeProfileKeysRequest);
        }
    }

    private void removeEndpoints(Endpoints endpoints) {
        for (Endpoints.RegisteredEndpoints registeredEndpoint : endpoints.registeredEndpoints) {
            String requestCookie = UUID.randomUUID().toString();
            BBMEnterprise.getInstance().getBbmdsProtocol().send(new EndpointDeregister(requestCookie, registeredEndpoint.endpointId));
        }
    }

    /**
     * Fetch the encrypted management (symmetric key and hmac key) values from cloud storage.
     * These keys still need to be decrypted using a derived key from a user supplied password.
     */
    private void readManagementKeys() {
        if (!mPendingTasks.containsKey(READ_MANAGEMENT_KEY_REQUEST)) {
            @SuppressLint("StaticFieldLeak") AsyncTaskStorageRequest<EncryptedManagementKeys> managementKeysRequest =
                    new AsyncTaskStorageRequest<EncryptedManagementKeys>(READ_MANAGEMENT_KEY_REQUEST, mPendingTasks) {
                @Override
                public void onSuccess(EncryptedManagementKeys value) {
                    if (value != null && value.getSign() != null && value.getEncrypt() != null) {
                        //We found existing magenement keys which need to be decrypted
                        value.setState(EncryptedManagementKeys.DECRYPTION_REQUIRED);
                    } else {
                        //We did not find any management keys
                        value = new EncryptedManagementKeys();
                        value.setState(EncryptedManagementKeys.NO_KEYS_AVAILABLE);
                    }
                    mEncryptedManagementKeys.set(value);
                    mEncryptedManagementKeys.dirty();
                }

                @Override
                public void onFailure() {
                    Logger.e("ProtectedManager - Failed to read management keys for local user");
                }

                @Override
                public void run() {
                    if (mStorageProvider != null) {
                        mStorageProvider.readManagementKeys(this);
                    }
                }
            };
            executeTask(managementKeysRequest);
        }
    }

    /**
     * Fetch our private and public keys from the cloud storage service.
     */
    private void readLocalProfileKeys() {

        //Make a new profileKeys object
        final ProfileKeys profileKeys = new ProfileKeys();

        String privateKeysTaskKey = READ_PRIVATE_KEY_REQUEST;
        if (!mPendingTasks.containsKey(privateKeysTaskKey)) {
            @SuppressLint("StaticFieldLeak") AsyncTaskStorageRequest<PrivateKeyPair> privateKeyRequest =
                    new AsyncTaskStorageRequest<PrivateKeyPair>(privateKeysTaskKey, mPendingTasks) {

                @Override
                public void run() {
                    //Fetch the private keys
                    if (mStorageProvider != null) {
                        Logger.i("ProtectedManager - Fetch local user private keys from key storage provider");
                        mStorageProvider.readPrivateKeys(this);
                    }
                }

                @Override
                public void onSuccess(final PrivateKeyPair keys) {
                    mHandler.post(() -> {
                        if (keys != null && keys.getEncrypt() != null && keys.getSign() != null) {
                            Logger.i("ProtectedManager - Found private keys for local user");
                            //Decrypt the keys
                            try {
                                //Decrypt the profile private key
                                String profilePrivateEncKey = EncryptionHelper.unprotectToString(
                                                keys.getEncrypt(),
                                                mManagementEncryptionKey,
                                                mManagementMac);

                                //Decrypt the profile private signing key
                                String profilePrivateSigningKey = EncryptionHelper.unprotectToString(
                                        keys.getSign(),
                                        mManagementEncryptionKey,
                                        mManagementMac);

                                //Add the private keys we read from the key storage
                                ProfileKeys.PrivateKeys privateKeys = new ProfileKeys.PrivateKeys();
                                privateKeys.encryption = profilePrivateEncKey;
                                privateKeys.signing = profilePrivateSigningKey;
                                profileKeys.privateKeys = privateKeys;
                                if (profileKeys.publicKeys != null) {
                                    //If the profileKeys already has some public keys
                                    // then we set the existence to YES now that we have added private keys
                                    profileKeys.exists = Existence.YES;
                                    mProfileKeys.set(profileKeys);
                                }
                            } catch (GeneralSecurityException e) {
                                Logger.e(e, "Unable to decrypt saved profile keys");
                                onError(ErrorHandler.DECRYPTION_ERROR);
                            }
                        } else {
                            Logger.i("ProtectedManager - No private keys found for local user");
                            //We don't have any stored private keys, set the existence of the profileKeys to NO
                            profileKeys.exists = Existence.NO;
                            if (mProfileKeys.get().exists == Existence.MAYBE) {
                                //Only notify if the existence has actually changed
                                Logger.i("ProfileKeys mutable changed - no private keys");
                                mProfileKeys.set(profileKeys);
                            }
                        }

                    });
                }

                @Override
                public void onFailure() {
                    Logger.e("ProtectedManager - Failed to read private keys for local user");
                }
            };
            executeTask(privateKeyRequest);
        }

        String publicKeysTaskKey = READ_PUBLIC_KEY_REQUEST;
        if (!mPendingTasks.containsKey(publicKeysTaskKey)) {
            //Fetch the public keys
            @SuppressLint("StaticFieldLeak") AsyncTaskStorageRequest<KeyPair> publicKeyRequest =
                    new AsyncTaskStorageRequest<KeyPair>(publicKeysTaskKey, mPendingTasks) {

                @Override
                public void run() {
                    if (mStorageProvider != null) {
                        Logger.i("ProtectedManager - Fetch local user public keys from key storage provider");
                        mStorageProvider.readPublicKeys(mUserUid, this);
                    }
                }

                @Override
                public void onFailure() {
                    Logger.e("ProtectedManager - Failed to read public keys for local user");
                }

                @Override
                public void onSuccess(final KeyPair keys) {
                    mHandler.post(() -> {
                        if (keys != null) {
                            Logger.i("ProtectedManager - Found public keys for local user");
                            //Add the public keys we read from the key storage
                            ProfileKeys.PublicKeys publicKeys = new ProfileKeys.PublicKeys();
                            publicKeys.encryption = keys.getEncrypt().getKey();
                            publicKeys.signing = keys.getSign().getKey();
                            profileKeys.publicKeys = publicKeys;
                            if (profileKeys.privateKeys != null) {
                                //If the profileKeys already has some private keys then we set the existence to YES
                                profileKeys.exists = Existence.YES;
                                mProfileKeys.set(profileKeys);
                            }
                        } else {
                            Logger.i("ProtectedManager - No public keys found for local user");
                            //We don't have any stored public keys, set the existence of the profileKeys to NO
                            profileKeys.exists = Existence.NO;
                            if (mProfileKeys.get().exists == Existence.MAYBE) {
                                //Only notify if the existence has actually changed
                                Logger.i("ProfileKeys mutable changed - no public keys");
                                mProfileKeys.set(profileKeys);
                            }
                        }
                    });
                }
            };
            executeTask(publicKeyRequest);
        }
    }

    private void executeTask(AsyncTask<Void, Void, Void> task) {
        task.execute();
    }

    @Override
    public void onMessage(ProtocolMessage protocolMessage) {
        //Listen for incoming protected messages
        switch (protocolMessage.getType()) {
            case "profileKeysImportFailure":
                ProfileKeysImportFailure pkif = new ProfileKeysImportFailure();
                pkif.setAttributes(protocolMessage.getJSON().optJSONObject("profileKeysImportFailure"));
                if (mKeyImportFailureListener != null) {
                    mKeyImportFailureListener.onProfileKeysImportFailure(pkif);
                } else {
                    Logger.e("ProfileKeysImportFailure occurred, no KeyImportFailureListener is registered");
                }
                break;
            case "userKeysImportFailure":
                UserKeysImportFailure ukif = new UserKeysImportFailure();
                ukif.setAttributes(protocolMessage.getJSON().optJSONObject("userKeysImportFailure"));
                if (mKeyImportFailureListener != null) {
                    mKeyImportFailureListener.onUserKeysImportFailure(ukif);
                } else {
                    Logger.e("UserKeysImportFailure occurred, no KeyImportFailureListener is registered");
                }
                break;
            case "chatKeysImportFailure":
                ChatKeysImportFailure ckif = new ChatKeysImportFailure();
                ckif.setAttributes(protocolMessage.getJSON().optJSONObject("chatKeysImportFailure"));
                if (mKeyImportFailureListener != null) {
                    mKeyImportFailureListener.onChatKeysImportFailure(ckif);
                } else {
                    Logger.e("ChatKeysImportFailure occurred, no KeyImportFailureListener is registered");
                }
                break;
        }
    }

    @Override
    public void resync() {
        //Protocol message consumer
    }

    private void onError(@ErrorHandler.ErrorType int error) {
        mHandler.post(() -> mErrorHandler.onError(error));
    }

}
