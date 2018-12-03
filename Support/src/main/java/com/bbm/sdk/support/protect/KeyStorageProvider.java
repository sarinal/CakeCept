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

/**
 * Set of methods which a key storage must provide to be used with the ProtectedManager.
 * KeyStorageProvider methods invoked from ProtectedManager will always be executed from a background thread.
 */
public interface KeyStorageProvider {

    /**
     * Write the provided keys to the cloud key storage service.
     * @param keys public/private encryption and signing keys for the local user.
     *             Obtained from bbmcore via the {@link com.bbm.sdk.bbmds.outbound.ProfileKeysExport} message
     * @param managementKeys the generated management keys (encryption and hmac) which have been encrypted using a derived key.
     * @param writeResponse the result of the write (success/fail) must be passed back to the request
     */
    void writeProfileKeys(@NonNull EncryptedProfileKeys keys, @NonNull EncryptedManagementKeys managementKeys, @NonNull KeyStorageResponse<Void> writeResponse);

    /**
     * Remove the private keys,public keys and management keys for the user.
     * @param removeResponse the result of the remove (success/fail) must be passed back to the request
     */
    void removeProfileKeys(@NonNull KeyStorageResponse<Void> removeResponse);

    /**
     * Write the chat key for the given mailboxId to the cloud key storage service.
     * @param mailboxId the mailbox id of the chat using this key
     * @param chatKey the encrypted chat key obtained from bbmcore via the {@link com.bbm.sdk.bbmds.outbound.ChatKeyExport} message
     * @param writeResponse the result of the write (success/fail) must be passed back to the request
     */
    void writeChatKey(@NonNull String mailboxId, @NonNull EncryptedPayload chatKey, @NonNull KeyStorageResponse<Void> writeResponse);

    /**
     * Return a chat key from the cloud key storage service.
     * The result of the read operation must be placed into the KeyStorageResponse.
     * @param mailboxId the mailbox id of the chat requesting the key
     * @param chatKeyStorageResponse the result of the read operation must be returned via the {@link KeyStorageResponse#onSuccess(Object)} or {@link KeyStorageResponse#onFailure()}
     */
    void readChatKey(@NonNull String mailboxId, @NonNull KeyStorageResponse<EncryptedPayload> chatKeyStorageResponse);

    /**
     * Remove the chat key for the provided mailbox id from the cloud storage service.
     * @param mailboxId the mailbox for the chat whose key must be removed.
     * @param writeResponse the result of the write (success/fail) must be passed back to the request
     */
    void removeChatKey(@NonNull String mailboxId, @NonNull KeyStorageResponse<Void> writeResponse);

    /**
     * Return the local users private keys from the cloud storage service.
     * @param privateKeysStorageResponse the result of the read operation must be returned via the {@link KeyStorageResponse#onSuccess(Object)} or {@link KeyStorageResponse#onFailure()}
     */
    void readPrivateKeys(@NonNull KeyStorageResponse<PrivateKeyPair> privateKeysStorageResponse);

    /**
     * Return the public keys for the provided registration id from the cloud storage service.
     * @param uid the user id value provided by the identity service.
     * @param publicKeysStorageResponse the result of the read operation must be returned via the {@link KeyStorageResponse#onSuccess(Object)} or {@link KeyStorageResponse#onFailure()}
     */
    void readPublicKeys(@NonNull String uid, @NonNull KeyStorageResponse<KeyPair> publicKeysStorageResponse);

    /**
     * Return the local users encryption management keys (encryption and HMAC key) from the cloud storage service.
     * @param managementKeysKeyStorageResponse the result of the read operation must be returned via the {@link KeyStorageResponse#onSuccess(Object)} or {@link KeyStorageResponse#onFailure()}
     */
    void readManagementKeys(@NonNull KeyStorageResponse<EncryptedManagementKeys> managementKeysKeyStorageResponse);

    /**
     * Write the provided management keys to the cloud storage service.
     * @param encryptedManagementKeys management keys which have been encrypted using a derived key from a user supplied password.
     * @param writeResponse the result of the write (success/fail) must be passed back to the request
     */
    void writeManagementKeys(@NonNull EncryptedManagementKeys encryptedManagementKeys, @NonNull KeyStorageResponse<Void> writeResponse);
}
