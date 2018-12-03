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

package com.bbm.sdk.support.protect.providers;

import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Base64;

import com.bbm.sdk.support.identity.auth.AzureAdAuthenticationManager;
import com.bbm.sdk.support.protect.EncryptedManagementKeys;
import com.bbm.sdk.support.protect.EncryptedPayload;
import com.bbm.sdk.support.protect.EncryptedProfileKeys;
import com.bbm.sdk.support.protect.KeyPair;
import com.bbm.sdk.support.protect.KeyStorageProvider;
import com.bbm.sdk.support.protect.KeyStorageResponse;
import com.bbm.sdk.support.protect.PlaintextKey;
import com.bbm.sdk.support.protect.PrivateKeyPair;
import com.bbm.sdk.support.util.IOUtils;
import com.bbm.sdk.support.util.Logger;
import com.microsoft.identity.client.AuthenticationResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Stores BBM Enterprise Keys in an Azure Cosmos DB.
 */
public class AzureKeyStorageProvider implements KeyStorageProvider {

    private static final String PROFILE = "profile";
    private static final String PRIVATE = "private";
    private static final String PUBLIC = "public";
    private static final String MANAGE = "manage";
    private static final String MAILBOXES = "mailboxes";
    private static final String KEYS = "keys";
    private static final String REPLACE = "replace";
    public static final String SIGN = "sign";
    public static final String ENCRYPT = "encrypt";
    public static final String KEY = "key";

    private JSONObject mCachedKeys;
    private String mKmsUrl;

    /**
     * Create a new AzureKeyStorageProvider
     * @param kmsUrl the url pointing to the KMS
     */
    public AzureKeyStorageProvider(String kmsUrl) {
        mKmsUrl = kmsUrl;
    }

    /**
     * Create a connection to the KMS
     * @param token the authentication token obtained using the BBME SDK Scope
     * @param uid the users identifier
     * @return a connection to the KMS to be used for reading or writing keys
     * @throws IOException if the connection could not be created
     */
    private HttpURLConnection createKMSConnection(@NonNull String token, @NonNull String uid) throws IOException {
        Uri.Builder builder = Uri.parse(mKmsUrl).buildUpon().appendPath(uid);
        String urlString = builder.build().toString();
        URL kmsUrl = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection)kmsUrl.openConnection();
        Logger.d("AzureKeyStorageProvider - Open KMS connection " + urlString);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        return connection;
    }

    /**
     * Fetches the keys for the local user and caches the result in mCachedKeys
     * @param authResult an authentication result from requesting a BBME SDK Scope
     * @return true if the keys were obtained successfully
     */
    private boolean refreshKeyCache(AuthenticationResult authResult) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        boolean success = false;
        try {
            connection = createKMSConnection(authResult.getAccessToken(), authResult.getUniqueId());
            connection.setRequestMethod("GET");
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                String response = sb.toString();
                mCachedKeys = new JSONObject(response);
                success = true;
            } else {
                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    //Expected response, there are no keys for our UID
                    Logger.i("AzureKeyStorageProvider - No keys in Azure key storage");
                    success = true;
                    mCachedKeys = new JSONObject();
                } else {
                    Logger.e("AzureKeyStorageProvider - Connection error reading keys responseCode: " + responseCode);
                    //Get the error input stream and read any available response
                    inputStream = connection.getErrorStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;
                    StringBuilder sb = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    String response = sb.toString();
                    Logger.e("AzureKeyStorageProvider - error reading keys: " + response);
                    success = false;
                }
            }
        } catch (JSONException jse) {
            Logger.e(jse, "AzureKeyStorageProvider - Invalid JSON keys");
            mCachedKeys = null;
        } catch(IOException e) {
            Logger.e(e);
        } finally {
            IOUtils.safeClose(inputStream);
            if (connection != null) {
                connection.disconnect();
            }
        }
        return success;
    }

    /**
     * Writes updates to the users keys (public, private, chats)
     * @param keys the keys to be updated
     * @param replace true if all of the keys should be replaced with the provided value
     * @param writeResponse the response to call if the write fails or succeeds
     */
    private void writeToKeyStorage(@NonNull JSONObject keys, boolean replace, @NonNull KeyStorageResponse<Void> writeResponse) {
        AzureAdAuthenticationManager.getInstance().getBBMScopeToken(new AzureAdAuthenticationManager.TokenCallback() {
            @Override
            public void onToken(AuthenticationResult authResult) {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        HttpURLConnection connection = null;
                        OutputStream outputStream = null;
                        InputStream inputStream = null;
                        try {
                            connection = createKMSConnection(authResult.getAccessToken(), authResult.getUniqueId());
                            connection.setDoOutput(true);
                            connection.setDoInput(true);
                            connection.setRequestMethod("PUT");

                            //Create a request body that includes the keys to be updated
                            JSONObject requestBodyJSON = new JSONObject();
                            requestBodyJSON.put(KEYS, keys);
                            requestBodyJSON.put( REPLACE, replace);

                            byte[] body = requestBodyJSON.toString().getBytes("UTF-8");
                            outputStream = new BufferedOutputStream(connection.getOutputStream());
                            //Write the keys
                            outputStream.write(body);
                            outputStream.flush();
                            IOUtils.safeClose(outputStream);

                            //Read the response
                            int responseCode = connection.getResponseCode();
                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                writeResponse.onSuccess(null);
                            } else {
                                Logger.e("AzureKeyStorageProvider - Error writing profile keys: " + responseCode);
                                //Get the error input stream and read any available response
                                inputStream = connection.getErrorStream();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                                String line;
                                StringBuilder sb = new StringBuilder();
                                while ((line = reader.readLine()) != null) {
                                    sb.append(line);
                                }
                                String response = sb.toString();
                                Logger.e("AzureKeyStorageProvider - Error writing profile keys response message: " + response);
                                writeResponse.onFailure();
                            }
                        } catch (IOException | JSONException e) {
                            Logger.e(e);
                            writeResponse.onFailure();
                        } finally {
                            IOUtils.safeClose(outputStream);
                            IOUtils.safeClose(inputStream);
                            if (connection != null) {
                                connection.disconnect();
                            }
                        }
                    }
                });
            }
        }, false);
    }

    /**
     * The profile keys are written as JSON in the following format
     * {
     *     private: {
     *         profile: {
     *             sign: {
     *                 payload: "payload",
     *                 mac: "mac"
     *                 nonce:" nonce"
     *             },
     *             encrypt: {
     *                 payload: "payload",
     *                 mac: "mac"
     *                 nonce: "nonce"
     *             }
     *         },
     *         manage: {
     *             management keypair (format matches profile key pair above)
     *         }
     *     },
     *     public: {
     *         sign: {
     *             key: "plaintext_value"
     *         },
     *         encrypt {
     *             key: "plaintext_value"
     *         }
     *     }
     * }
     */
    @Override
    public void writeProfileKeys(@NonNull EncryptedProfileKeys encProfileKeys,
                                 @NonNull EncryptedManagementKeys managementKeys,
                                 @NonNull KeyStorageResponse<Void> writeResponse) {
        Logger.i("AzureKeyStorageProvider - Write profile keys");
        try {
            JSONObject keysAsJSON = new JSONObject();
            JSONObject privateKeys = new JSONObject();

            //Create the profile keys object
            JSONObject profileKeys = new JSONObject();
            profileKeys.put(SIGN, encryptedPayloadToJSON(encProfileKeys.getPrivateKeyPair().getSign()));
            profileKeys.put(ENCRYPT, encryptedPayloadToJSON(encProfileKeys.getPrivateKeyPair().getEncrypt()));
            //Add the profile keys to the private keys object
            privateKeys.put(PROFILE, profileKeys);

            //Create the management keys object
            JSONObject managementKeysJSON = new JSONObject();
            managementKeysJSON.put(SIGN, encryptedPayloadToJSON(managementKeys.getSign()));
            managementKeysJSON.put(ENCRYPT, encryptedPayloadToJSON(managementKeys.getEncrypt()));
            privateKeys.put(MANAGE, managementKeysJSON);

            //Create public keys object
            JSONObject publicKeys = new JSONObject();
            publicKeys.put(SIGN, convertPlaintextKeyToJSON(encProfileKeys.getPublicKeyPair().getSign()));
            publicKeys.put(ENCRYPT, convertPlaintextKeyToJSON(encProfileKeys.getPublicKeyPair().getEncrypt()));

            //Add the public keys to the root
            keysAsJSON.put(PUBLIC, publicKeys);
            //Put the private keys object into the root
            keysAsJSON.put(PRIVATE, privateKeys);
            writeToKeyStorage(keysAsJSON, true, writeResponse);
        } catch (JSONException e) {
            Logger.e(e, "Invalid profile keys json");
            writeResponse.onFailure();
        }
    }

    @Override
    public void removeProfileKeys(@NonNull KeyStorageResponse<Void> removeResponse) {
        Logger.i("AzureKeyStorageProvider - Remove profile keys");
        try {
            //Create an empty public and private key set to write
            JSONObject keys = new JSONObject();
            keys.put(PUBLIC, new JSONObject());
            keys.put(PRIVATE, new JSONObject());
            writeToKeyStorage(keys, true, removeResponse);
        } catch (JSONException e) {
            Logger.e(e, "Invalid remove profile keys json");
            removeResponse.onFailure();
        }
    }

    /**
     * The chat keys are written in the following format:
     * {
     *      "private": {
     *          "mailboxes": {
     *              "MGZiM2EzYzE3ZDYyNzRhNzdhNzBlNmRhNDY1ZGMxNDVkYTcyMGJhMy4wMDE": {
     *                  "payload": "YGaQ-DZ451uKneSF1XhDfG2erVUfPCeK9DLHeXU_uxHoOzx-OzSB-66dlw",
     *                  "nonce": "0pTq0d0mZ_-tvsJba4kvyg",
     *                  "mac": "6GX8HWzsXjgRfT17AeZLHkhskeYYZ9Ty0WldDY1D_N8"
     *              }
     *          }
     *      }
     * }
     */
    @Override
    public void writeChatKey(@NonNull String mailboxId, @NonNull EncryptedPayload chatKey, @NonNull KeyStorageResponse<Void> writeResponse) {
        Logger.i("AzureKeyStorageProvider - Write chat key");
        JSONObject keys = new JSONObject();
        JSONObject privateKeys = new JSONObject();
        JSONObject mailboxes = new JSONObject();
        try {
            //base64 encode the mailbox id before writing it
            mailboxes.put(base64EncodeValue(mailboxId), encryptedPayloadToJSON(chatKey));
            privateKeys.put(MAILBOXES, mailboxes);
            keys.put(PRIVATE, privateKeys);
            writeToKeyStorage(keys, false, writeResponse);
        } catch (JSONException e) {
            Logger.e(e, "Invalid chat keys json");
            writeResponse.onFailure();
        }
    }

    @Override
    public void readChatKey(@NonNull String mailboxId, @NonNull KeyStorageResponse<EncryptedPayload> chatKeyStorageResponse) {
        Logger.i("AzureKeyStorageProvider - Read chat key " + mailboxId);
        String base64MailboxId = base64EncodeValue(mailboxId);
        //First check if the cached keys already
        if (mCachedKeys != null && mCachedKeys.has(PRIVATE)) {
            EncryptedPayload chatKey = parseChatKey(base64MailboxId, mCachedKeys.optJSONObject(PRIVATE));
            if (chatKey != null) {
                //We found the chat key so pass it back and return
                chatKeyStorageResponse.onSuccess(chatKey);
                return;
            }
        }

        AzureAdAuthenticationManager.getInstance().getBBMScopeToken(new AzureAdAuthenticationManager.TokenCallback() {
            @Override
            public void onToken(AuthenticationResult authResult) {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (refreshKeyCache(authResult)) {
                            EncryptedPayload chatKey = parseChatKey(base64MailboxId, mCachedKeys.optJSONObject(PRIVATE));
                            //Return the chat key or null if it wasn't found
                            chatKeyStorageResponse.onSuccess(chatKey);
                        } else {
                            //Couldn't read the keys
                            Logger.i("AzureKeyStorageProvider - Error reading chat key " + mailboxId);
                            chatKeyStorageResponse.onFailure();
                        }
                    }
                });
            }
        }, false);

    }

    private EncryptedPayload parseChatKey(String mailboxId, JSONObject privateKeys) {
        if (privateKeys != null && privateKeys.has(MAILBOXES)) {
            JSONObject mailboxes = privateKeys.optJSONObject(MAILBOXES);
            if (mailboxes != null && mailboxes.has(mailboxId)) {
                return new EncryptedPayload(mailboxes.optJSONObject(mailboxId));
            }
        }
        return null;
    }

    @Override
    public void removeChatKey(@NonNull String mailboxId, @NonNull KeyStorageResponse<Void> writeResponse) {
        Logger.i("AzureKeyStorageProvider - Remove chat key " + mailboxId);
        JSONObject keys = new JSONObject();
        JSONObject privateKeys = new JSONObject();
        JSONObject mailboxes = new JSONObject();
        try {
            //To erase a chat key we write a null value
            mailboxes.put(base64EncodeValue(mailboxId), JSONObject.NULL);
            privateKeys.put(MAILBOXES, mailboxes);
            keys.put(PRIVATE, privateKeys);
            writeToKeyStorage(keys, false, writeResponse);
        } catch (JSONException e) {
            Logger.e(e, "Invalid remove chat json");
            writeResponse.onFailure();
        }
    }

    @Override
    public void readPrivateKeys(@NonNull KeyStorageResponse<PrivateKeyPair> privateKeysStorageResponse) {
        Logger.i("AzureKeyStorageProvider - Read private keys");
        if (mCachedKeys != null && mCachedKeys.has(PRIVATE)) {
            PrivateKeyPair keyPair = parsePrivateProfileKeys();
            if (keyPair != null) {
                //We found our private keys so pass them back and return
                privateKeysStorageResponse.onSuccess(keyPair);
                return;
            }
        }

        AzureAdAuthenticationManager.getInstance().getBBMScopeToken(new AzureAdAuthenticationManager.TokenCallback() {
            @Override
            public void onToken(AuthenticationResult authResult) {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (refreshKeyCache(authResult)) {
                            PrivateKeyPair keyPair = parsePrivateProfileKeys();
                            //Return the key pair (or null it if wasn't found)
                            privateKeysStorageResponse.onSuccess(keyPair);
                        } else {
                            //Couldn't read the keys
                            Logger.i("AzureKeyStorageProvider - Error reading private keys");
                            privateKeysStorageResponse.onFailure();
                        }
                    }
                });
            }
        }, false);
    }

    private PrivateKeyPair parsePrivateProfileKeys() {
        JSONObject privateKeys = mCachedKeys.optJSONObject(PRIVATE);
        if (privateKeys != null && privateKeys.has(PROFILE)) {
            JSONObject managementKeysJSON = privateKeys.optJSONObject(PROFILE);
            if (managementKeysJSON.has(SIGN) && managementKeysJSON.has(ENCRYPT)) {
                EncryptedPayload signing = new EncryptedPayload(managementKeysJSON.optJSONObject(SIGN));
                EncryptedPayload encryption = new EncryptedPayload(managementKeysJSON.optJSONObject(ENCRYPT));
                return new PrivateKeyPair(encryption, signing);
            }
        }
        return null;
    }

    @Override
    public void readPublicKeys(@NonNull String uid, @NonNull KeyStorageResponse<KeyPair> publicKeysStorageResponse) {
        Logger.i("AzureKeyStorageProvider - Read public keys for uid " + uid);
        //If the UID matches the local users UID we probably have the public keys cached already.
        //For all other users we must make a request to the KMS to get the keys
        if (uid.equals(AzureAdAuthenticationManager.getInstance().getUserIdentifier())) {
            KeyPair pubKeys = parsePublicProfileKeys(mCachedKeys);
            if (pubKeys != null) {
                //We found our public keys so pass them back and return
                publicKeysStorageResponse.onSuccess(pubKeys);
                return;
            }
        }

        AzureAdAuthenticationManager.getInstance().getBBMScopeToken(new AzureAdAuthenticationManager.TokenCallback() {
            @Override
            public void onToken(AuthenticationResult authResult) {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        HttpURLConnection connection = null;
                        InputStream inputStream = null;
                        boolean succeeded = false;
                        try {
                            connection = createKMSConnection(authResult.getAccessToken(), uid);
                            connection.setRequestMethod("GET");
                            connection.connect();
                            int responseCode = connection.getResponseCode();
                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                inputStream = connection.getInputStream();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                                String line;
                                StringBuilder sb = new StringBuilder();
                                while ((line = reader.readLine()) != null) {
                                    sb.append(line);
                                }
                                String response = sb.toString();
                                JSONObject publicKeys = new JSONObject(response);
                                KeyPair pubKeys = parsePublicProfileKeys(publicKeys);
                                //Pass back the public keys we found (or null if they don't exist)
                                publicKeysStorageResponse.onSuccess(pubKeys);
                                succeeded = true;
                            } else {
                                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                                    //Expected response, there are no keys for the given UID
                                    publicKeysStorageResponse.onSuccess(null);
                                } else {
                                    Logger.e("Connection error reading keys responseCode: " + responseCode);
                                    //Get the error input stream and read any available response
                                    inputStream = connection.getErrorStream();
                                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                                    String line;
                                    StringBuilder sb = new StringBuilder();
                                    while ((line = reader.readLine()) != null) {
                                        sb.append(line);
                                    }
                                    String response = sb.toString();
                                    Logger.e("AzureKeyStorageProvider - Error reading public keys response message: " + response);
                                }
                            }
                        } catch (JSONException jse) {
                            Logger.e(jse, "Invalid JSON keys");
                        } catch(IOException e){
                            Logger.e(e);
                        } finally {
                            IOUtils.safeClose(inputStream);
                            if (connection != null) {
                                connection.disconnect();
                            }
                        }
                        if (!succeeded) {
                            Logger.i("AzureKeyStorageProvider - Error reading public keys");
                            publicKeysStorageResponse.onFailure();
                        }
                    }
                });
            }
        }, false);
    }

    private KeyPair parsePublicProfileKeys(JSONObject keys) {
        if (keys != null && keys.has(PUBLIC)) {
            JSONObject publicKeys = keys.optJSONObject(PUBLIC);
            if (publicKeys != null && publicKeys.has(SIGN) && publicKeys.has(ENCRYPT)) {
                PlaintextKey signing = new PlaintextKey(publicKeys.optJSONObject(SIGN));
                PlaintextKey encryption = new PlaintextKey(publicKeys.optJSONObject(ENCRYPT));
                return new KeyPair(encryption, signing);
            }
        }
        return null;
    }

    @Override
    public void readManagementKeys(@NonNull KeyStorageResponse<EncryptedManagementKeys> managementKeysKeyStorageResponse) {
        Logger.i("AzureKeyStorageProvider - Read management keys");
        if (mCachedKeys != null && mCachedKeys.has(PRIVATE)) {
            EncryptedManagementKeys keys = parseManagementKeys();
            if (keys != null) {
                //We found the management keys, pass them back and return
                managementKeysKeyStorageResponse.onSuccess(keys);
                return;
            }
        }

        AzureAdAuthenticationManager.getInstance().getBBMScopeToken(new AzureAdAuthenticationManager.TokenCallback() {
            @Override
            public void onToken(AuthenticationResult authResult) {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (refreshKeyCache(authResult)) {
                            //Pass back the management keys we found (or null if they don't exist)
                            EncryptedManagementKeys keys = parseManagementKeys();
                            managementKeysKeyStorageResponse.onSuccess(keys);
                        } else {
                            //Couldn't read the keys
                            Logger.i("AzureKeyStorageProvider - Error reading management keys");
                            managementKeysKeyStorageResponse.onFailure();
                        }
                    }
                });
            }
        }, false);
    }

    private EncryptedManagementKeys parseManagementKeys() {
        JSONObject privateKeys = mCachedKeys.optJSONObject(PRIVATE);
        if (privateKeys != null && privateKeys.has(MANAGE)) {
            JSONObject managementKeysJSON = privateKeys.optJSONObject(MANAGE);
            if (managementKeysJSON.has(SIGN) && managementKeysJSON.has(ENCRYPT)) {
                EncryptedPayload signing = new EncryptedPayload(managementKeysJSON.optJSONObject(SIGN));
                EncryptedPayload encryption = new EncryptedPayload(managementKeysJSON.optJSONObject(ENCRYPT));
                PrivateKeyPair keyPair = new PrivateKeyPair(encryption, signing);
                return new EncryptedManagementKeys(keyPair);
            }
        }
        //No keys exist in the data
        return null;
    }

    /**
     * The management keys are written in the following format:
     * {
     *      "private": {
     *          "manage": {
     *              sign: {
     *                 payload: "payload",
     *                 mac: "mac"
     *                 nonce:" nonce"
     *             },
     *             encrypt: {
     *                 payload: "payload",
     *                 mac: "mac"
     *                 nonce: "nonce"
     *             }
     *          }
     *      }
     * }
     */
    @Override
    public void writeManagementKeys(@NonNull EncryptedManagementKeys encryptedManagementKeys, @NonNull KeyStorageResponse<Void> writeResponse) {
        Logger.i("AzureKeyStorageProvider - Write management keys");
        JSONObject keys = new JSONObject();
        JSONObject privateKeys = new JSONObject();
        try {
            //Create the management keys object
            JSONObject managementKeysJSON = new JSONObject();
            managementKeysJSON.put(SIGN, encryptedPayloadToJSON(encryptedManagementKeys.getSign()));
            managementKeysJSON.put(ENCRYPT, encryptedPayloadToJSON(encryptedManagementKeys.getEncrypt()));
            privateKeys.put(MANAGE, managementKeysJSON);
            keys.put(PRIVATE, privateKeys);
            writeToKeyStorage(keys, false, writeResponse);
        } catch (JSONException e) {
            Logger.e(e, "Invalid management keys json");
            writeResponse.onFailure();
        }
    }

    private JSONObject encryptedPayloadToJSON(EncryptedPayload payload) throws JSONException {
        JSONObject asJSON = new JSONObject();
        asJSON.put(EncryptedPayload.PAYLOAD, payload.getPayload());
        asJSON.put(EncryptedPayload.NONCE, payload.getNonce());
        asJSON.put(EncryptedPayload.MAC, payload.getMac());
        return asJSON;
    }

    /**
     * Convert the public key pair in a format suitable for storage in a Key-management-system
     */
    public JSONObject convertPlaintextKeyToJSON(PlaintextKey key) throws JSONException {
        JSONObject keyAsJSON = new JSONObject();
        keyAsJSON.put(KEY, key.getKey());
        return keyAsJSON;
    }

    /**
     * Any value which is to be used as a key in firebase should be URL safe encoded first to avoid any bad characters (ex '.')
     */
    private String base64EncodeValue(String valueToEncode) {
        if (!TextUtils.isEmpty(valueToEncode)) {
            return new String(Base64.encode(valueToEncode.getBytes(), Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP));
        }
        return "";
    }
}
