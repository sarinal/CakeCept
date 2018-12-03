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
import android.content.SharedPreferences;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.support.annotation.NonNull;
import android.util.Base64;

import com.bbm.sdk.support.util.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.UnrecoverableEntryException;
import java.util.Calendar;
import java.util.GregorianCalendar;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;

/**
 * Utilities for encrypting and decryption using symmetric keys.
 */
public class EncryptionHelper {

    /**
     * Represents a derived root key obtained from a user provided password
     */
    public static class DerivedRootKey {
        public Key derivedEncryptionKey;
        public Key derivedHmacKey;
    }

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    static final String KEYSPEC_AES = "AES";
    static final String KEYSPEC_HMACSHA256 = "HmacSHA256";
    private static final String TRANSFORMATION = "AES/CTR/NoPadding";
    //byte value of "BBME SDK Pre-KMS DRK"
    private static final byte[] DERIVED_KEY_DIVERSIFIER = {66, 66, 77, 69, 32, 83, 68, 75, 32, 80,
            114, 101, 45, 75, 77, 83, 32, 68, 82, 75};
    private static final String MANAGEMENT_ENCRYPTION_KEY_ID = "MANAGEMENT_ENCRYPTION_KEY";
    private static final String MANAGEMENT_HMAC_KEY_ID = "MANAGEMENT_HMAC_KEY";

    private static final String RSA_KEY_PAIR_ID = "RSA_KEY_PAIR_ID";
    private static final String RSA_KEY_ALGORITHM = "RSA";
    private static final int MIN_KEY_LENGTH = 2048;
    private static final String RSA_CIPHER_TRANSFORMATION = "RSA/ECB/PKCS1Padding";

    /**
     * Return the symmetric AES Management key.
     * The management key is used to encrypt the users private ProfileKeys and ChatKeys.
     * If a key has previously been created the existing key is returned.
     *
     * For API 23+ the key is stored directly in the AndroidKeyStore
     *  - The AndroidKeyStore shall retain ownership over the storage of the key.
     *  - After the key is stored, the key material cannot be retrieved (the raw key bytes)
     * For API 19 - 22 the key is stored in the application shared preferences
     *  - Before being written to the SharedPrefs the key is encrypted using an RSA key pair
     *    generated from the AndroidKeyStore.
     * @return 256 bit length AES Key
     * @throws GeneralSecurityException if an error occurs creating the key
     * @throws IOException if the key cannot be saved
     */
    public static Key getManagementEncryptionKey(@NonNull Context context)
            throws GeneralSecurityException, IOException {

        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
                keyStore.containsAlias(MANAGEMENT_ENCRYPTION_KEY_ID)) {
            //Our key exists already in the keystore
            //Note: the raw key data is not available when retrieving the key from storage.
            return keyStore.getKey(MANAGEMENT_ENCRYPTION_KEY_ID, null);
        } else {
            //The AndroidKeyStore < API 23 doesn't support symmetric keys
            //Instead we must generate the key and securely store it ourselves in the shared preferences

            //First look for a saved value (encrypted symmetric management key) in the shared preferences
            SharedPreferences sharedPreferences = context.getSharedPreferences("com.bbm.sdk.support.protect", Context.MODE_PRIVATE);
            String encryptedManagementKey = sharedPreferences.getString(MANAGEMENT_ENCRYPTION_KEY_ID, null);

            if (encryptedManagementKey != null) {
                //We have a saved symmetric key, need to decrypt the key
                if (!keyStore.containsAlias(RSA_KEY_PAIR_ID)) {
                    //We have an encrypted symmetric key stored but no RSA key pair to decrypt it with
                    throw new UnrecoverableEntryException("No encryption/decryption keypair exists for saved symmetric key");
                } else {
                    //Use the RSA KeyPair from the KeyStore to decrypt the saved symmetric key value
                    byte[] decryptedSymmetricKeyBytes = decryptRSA(encryptedManagementKey, getRSAKeyPair(context, keyStore));

                    Logger.i("Retrieve existing management key from shared preferences");
                    //Create the AES symmetric key using the decrypted bytes
                    return new SecretKeySpec(decryptedSymmetricKeyBytes, KEYSPEC_AES);
                }
            }
        }


        //We don't have any saved saved symmetric key.
        //Generate some random bytes to use for the management key
        byte[] secureBytes = new byte[32];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(secureBytes);

        //Save the new key (either to the AndroidKeyStore in API 23+ or shared preferences)
        return saveManagementEncryptionKey(context, secureBytes);
    }

    /**
     * Return the HMAC Management key.
     * The hmac management key is used to validate the users encrypted private ProfileKeys and ChatKeys.
     * If a key has previously been created the existing key is returned.
     *
     * For API 23+ the key is stored directly in the AndroidKeyStore
     *  - The AndroidKeyStore shall retain ownership over the storage of the key.
     *  - After the key is stored, the key material cannot be retrieved (the raw key bytes)
     * For API 19 - 22 the key is stored in the application shared preferences
     *  - Before being written to the SharedPrefs the key is encrypted using an RSA key pair
     *    generated from the AndroidKeyStore.
     * @param context android application context
     * @return 256 bit length HmacSHA256 key
     * @throws GeneralSecurityException if an error occurs creating the key
     * @throws IOException if the key cannot be saved
     */
    public static Key getManagementHMACKey(@NonNull Context context)
            throws GeneralSecurityException, IOException  {

        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                keyStore.containsAlias(MANAGEMENT_HMAC_KEY_ID)) {
            //Our key exists already in the keystore
            //Note: the raw key data is not available when retrieving the key from storage.
            return keyStore.getKey(MANAGEMENT_HMAC_KEY_ID, null);
        } else {
            //First look for a saved value (encrypted HMAC management key)
            SharedPreferences sharedPreferences = context.getSharedPreferences("com.bbm.sdk.support.protect", Context.MODE_PRIVATE);
            String encryptedHMACKey = sharedPreferences.getString(MANAGEMENT_HMAC_KEY_ID, null);

            //To securely store the key we will generate an RSA keypair from the KeyStore.
            //The Keypair will be used to encrypt/decrypt the symmetric key which is stored in the SharedPreferences.
            // Check if our RSA key pair exists, otherwise create a new one
            if (encryptedHMACKey != null) {
                //We have a saved HMAC key, need to decrypt the key
                if (!keyStore.containsAlias(RSA_KEY_PAIR_ID)) {
                    //We have an encrypted HMAC key stored but no RSA key pair to decrypt it with
                    throw new UnrecoverableEntryException("No encryption/decryption keypair exists for saved symmetric key");
                } else {
                    //Use the keypair to decrypt the HMAC key
                    byte[] decryptedSymmetricKeyBytes = decryptRSA(encryptedHMACKey, getRSAKeyPair(context, keyStore));

                    Logger.i("Retrieve existing HMAC key from shared preferences");
                    //Create the HMAC key using the decrypted bytes
                    return new SecretKeySpec(decryptedSymmetricKeyBytes, KEYSPEC_HMACSHA256);
                }
            }
        }


        //We don't have a saved HMAC key
        //Generate some random bytes to use for the HMAC key
        byte[] secureBytes = new byte[32];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(secureBytes);

        //Save the new HMAC Management key (either to AndroidKeyStore for API 23+ or shared preferences).
        return saveHMACManagementKey(context, secureBytes);
    }

    /**
     * Have the AndroidKeyStore generate an RSA KeyPair.
     * This RSA KeyPair is used to encrypt the management key and hmac key before writing them
     * into the shared preferences.
     * @param context application context
     * @return a KeyPair to be used for encrypting data for storage in SharedPreferences.
     * @throws NoSuchProviderException if the 'RSA' provider cannot be found
     * @throws NoSuchAlgorithmException if the 'RSA/ECB/PKCS1Padding' algorithm cannot be found
     * @throws InvalidAlgorithmParameterException if the key pair cannot be generated
     */
    private static KeyPair getRSAKeyPair(@NonNull Context context, @NonNull KeyStore keyStore)
            throws NoSuchProviderException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, KeyStoreException, UnrecoverableEntryException {

        if (keyStore.containsAlias(RSA_KEY_PAIR_ID)) {
            // Get the existing RSA Keypair
            KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(RSA_KEY_PAIR_ID, null);
            return new java.security.KeyPair(entry.getCertificate().getPublicKey(), entry.getPrivateKey());
        } else {
            // Get a new RSA Keypair from the key store
            final Calendar start = new GregorianCalendar();
            final Calendar end = new GregorianCalendar();
            end.add(Calendar.YEAR, 100);

            KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(context)
                    .setAlias(RSA_KEY_PAIR_ID)
                    .setSubject(new X500Principal("CN=" + RSA_KEY_PAIR_ID))
                    .setSerialNumber(BigInteger.ONE)
                    .setStartDate(start.getTime())
                    .setEndDate(end.getTime())
                    .setKeySize(MIN_KEY_LENGTH)
                    .build();

            // Create the RSA Key pair
            KeyPairGenerator gen = KeyPairGenerator.getInstance(RSA_KEY_ALGORITHM, ANDROID_KEYSTORE);
            gen.initialize(spec);
            return gen.generateKeyPair();
        }
    }

    /**
     * Decrypt using the RSA Key pair obtained from the AndroidKeyStore
     * @param encryptedData a Base64 encoded string to decrypt
     * @return decrypted bytes
     * @throws GeneralSecurityException if the decryption fails using the provided key pair
     * @throws IOException if the cipher input stream cannot be read
     */
    private static byte[] decryptRSA(@NonNull String encryptedData, @NonNull KeyPair keyPair)
            throws GeneralSecurityException, IOException {
        Cipher cipher = Cipher.getInstance(RSA_CIPHER_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
        cipher.update(Base64.decode(encryptedData, Base64.DEFAULT));
        return cipher.doFinal();
    }

    /**
     * Encrypt using the RSA Key pair obtained from the AndroidKeyStore
     * @param data bytes to encrypt
     * @return the encrypted data as a base64 encoded string
     * @throws GeneralSecurityException if the encryption fails using the provided key pair
     * @throws IOException if the cipher input stream cannot be written to
     */
    private static String encryptRSA(@NonNull byte[] data, @NonNull KeyPair keyPair)
            throws GeneralSecurityException, IOException {
        Cipher cipher = Cipher.getInstance(RSA_CIPHER_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic());
        cipher.update(data);
        return Base64.encodeToString(cipher.doFinal(), Base64.DEFAULT);
    }

    /**
     * Clear saved management and hmac keys.
     * @param context application context
     */
    public static void clearKeys(@NonNull Context context) {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                keyStore.deleteEntry(MANAGEMENT_HMAC_KEY_ID);
                keyStore.deleteEntry(MANAGEMENT_ENCRYPTION_KEY_ID);
            } else {
                SharedPreferences sharedPreferences = context.getSharedPreferences("com.bbm.sdk.support.protect", Context.MODE_PRIVATE);
                //Remove saved keys from shared prefs
                sharedPreferences.edit().remove(MANAGEMENT_HMAC_KEY_ID).apply();
                sharedPreferences.edit().remove(MANAGEMENT_ENCRYPTION_KEY_ID).apply();
                //Remove RSA key
                keyStore.deleteEntry(RSA_KEY_PAIR_ID);
            }
        } catch(Exception e){
            Logger.e(e, "Unable to clear RSA key from keystore");
        }
    }

    /**
     * Save the management encryption key.
     * On API 23+ the key is saved to the AndroidKeyStore
     * On older API versions the key is first encrypted with an RSA key pair obtained from the key store
     * and then written to the applications shared preferences.
     * @param context application context
     * @param keyBytes key material
     * @return an AES security Key
     * @throws GeneralSecurityException if the SecretKey cannot be created
     * @throws IOException an error occurs when saving the key data
     */
    @SuppressLint("ApplySharedPref")
    public static Key saveManagementEncryptionKey(@NonNull Context context, @NonNull byte[] keyBytes)
            throws GeneralSecurityException, IOException {

        SecretKey key = new SecretKeySpec(keyBytes, KEYSPEC_AES);
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            KeyProtection protection = new KeyProtection.Builder(
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CTR)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build();
            keyStore.setEntry(MANAGEMENT_ENCRYPTION_KEY_ID, new KeyStore.SecretKeyEntry(key), protection);
        } else {
            //Encrypt the new management key and save it
            SharedPreferences sharedPreferences = context.getSharedPreferences("com.bbm.sdk.support.protect", Context.MODE_PRIVATE);
            String encryptedHMACKey = encryptRSA(keyBytes, getRSAKeyPair(context, keyStore));
            sharedPreferences.edit().putString(MANAGEMENT_ENCRYPTION_KEY_ID, encryptedHMACKey).commit();
        }

        return key;
    }

    /**
     * Save the management hmac key.
     * On API 23+ the key is saved to the AndroidKeyStore
     * On older API versions the key is first encrypted with an RSA key pair obtained from the key store
     * and then written to the applications shared preferences.
     * @param context application context
     * @param keyBytes key material
     * @return an hmac security Key
     * @throws GeneralSecurityException if the SecretKey cannot be created
     * @throws IOException if an error occurs when saving the key data
     */
    @SuppressLint("ApplySharedPref")
    public static Key saveHMACManagementKey(@NonNull Context context, @NonNull byte[] keyBytes)
            throws GeneralSecurityException, IOException {
        SecretKey key = new SecretKeySpec(keyBytes, KEYSPEC_HMACSHA256);
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            KeyProtection protection = new KeyProtection.Builder(
                    KeyProperties.PURPOSE_SIGN)
                    .build();
            keyStore.setEntry(MANAGEMENT_HMAC_KEY_ID, new KeyStore.SecretKeyEntry(key), protection);
        } else {
            //Encrypt the new HMAC key and save it
            SharedPreferences sharedPreferences = context.getSharedPreferences("com.bbm.sdk.support.protect", Context.MODE_PRIVATE);
            String encryptedHMACKey = encryptRSA(keyBytes, getRSAKeyPair(context, keyStore));
            sharedPreferences.edit().putString(MANAGEMENT_HMAC_KEY_ID, encryptedHMACKey).commit();
        }

        return key;
    }

    /**
     * Create a HmacSHA256 Mac using the provided key
     * @param key a 256bit length key
     * @return a Mac which can be used to generate the MAC result
     * @throws NoSuchAlgorithmException if the 'HmacSHA256' mac provider cannot be found
     * @throws InvalidKeyException if an invalid Key (not 'HmacSHA256') is provided
     */
    public static Mac createHMAC(@NonNull Key key) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        sha256_HMAC.init(key);
        return sha256_HMAC;
    }

    /**
     * Encrypt the data string after converting to a UTF-8 byte array using AES/CTR/NoPadding and generate a mac.
     * @param data data to be encrypted
     * @param key 256 bit length AES key
     * @param mac HmacSHA256 Mac
     * @return an EncyptedPayload containing the encrypted data, nonce and mac.
     * @throws GeneralSecurityException if an error occurs when encrypting the data
     * @throws UnsupportedEncodingException if the UTF-8 encoding cannot be found
     */
    public static EncryptedPayload protect(@NonNull String data, @NonNull Key key, @NonNull Mac mac)
            throws GeneralSecurityException, UnsupportedEncodingException {
        byte[] dataBytes = data.getBytes("UTF-8");

        return protect(dataBytes, key, mac);
    }

    /**
     * Encrypt the data using AES/CTR/NoPadding and generate a mac.
     * @param data data to be encrypted
     * @param key 256 bit length AES key
     * @param mac HmacSHA256 Mac
     * @return an EncyptedPayload containing the encrypted data, nonce and mac.
     * @throws GeneralSecurityException if an error occurs when encrypting the data
     * @throws UnsupportedEncodingException if the UTF-8 encoding cannot be found
     */
    public static EncryptedPayload protect(@NonNull byte[] data, @NonNull Key key, @NonNull Mac mac)
            throws GeneralSecurityException, UnsupportedEncodingException {

        byte[] encryptedBytes;
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        //Get the initialization vector (nonce)
        byte[] initVector = cipher.getIV();
        if (initVector == null) {
            //This should never happen, but just in case throw an exception if the cipher hasn't provided the IV/Nonce
            throw new GeneralSecurityException("Initialization vector is not populated");
        }
        //Encrypt the data
        encryptedBytes = cipher.doFinal(data);

        //Create the EncryptedPayload result
        String payload = toBase64(encryptedBytes);
        String nonce = toBase64(initVector);
        String macResult = generateHMAC(encryptedBytes, mac);
        return new EncryptedPayload(payload, nonce, macResult);
    }

    /**
     * Validate the MAC and decrypt an encrypted payload and return the decrypted value as a String.
     * @param encValue the encrypted payload to decrypt
     * @param key the AES key to decrypt with
     * @param mac the MAC used to authenticate the result
     * @return a new string created using the decrypted bytes
     * @throws GeneralSecurityException if an error occurs during decryption
     */
    public static String unprotectToString(@NonNull EncryptedPayload encValue, @NonNull Key key, @NonNull Mac mac)
            throws GeneralSecurityException {
        return new String(unprotectToByteArray(encValue, key, mac));
    }

    /**
     * Validate the MAC and decrypt the provided EncryptedPayload and return the decrypted bytes.
     * @param encValue the encrypted payload to decrypt
     * @param key the AES key to decrypt with
     * @param mac the MAC used to authenticate the result
     * @return the decrypted bytes
     * @throws GeneralSecurityException if an error occurs during decryption
     */
    @NonNull
    public static byte[] unprotectToByteArray(@NonNull EncryptedPayload encValue, @NonNull Key key, @NonNull Mac mac)
            throws GeneralSecurityException {


        byte[] dataBytes = fromBase64(encValue.getPayload());

        if (dataBytes != null) {
            //Generate the MAC from the encrypted bytes.
            String hmac = toBase64(mac.doFinal(dataBytes));
            //Confirm that MAC we generated matches the value in the EncryptedPayload
            if (!hmac.equals(encValue.getMac())) {
                throw new InvalidKeyException("HMAC does not match expected value");
            }

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(fromBase64(encValue.getNonce())));

            return cipher.doFinal(dataBytes, 0, dataBytes.length);
        }

        return new byte[]{};
    }

    /**
     * Generate a MAC value for the provided string.
     * The result is base64 encoded before being returned.
     * @param data the encrypted value for which the MAC should be generated
     * @param mac the Mac provider
     * @return a base64 encoded MAC
     */
    private static String generateHMAC(@NonNull byte[] data, @NonNull Mac mac) {
        byte[] hmac = mac.doFinal(data);
        return toBase64(hmac);
    }

    /**
     * Create a derived root key from the provided password and diversifier
     * @param password the user supplied password
     * @param regId the users registration id
     * @return a DerivedRootKey containing an encryption key and an hmac key
     * @throws UnsupportedEncodingException if the UTF-8 encoding cannot be found
     * @throws NoSuchAlgorithmException if the SHA-512 provider cannot be found
     */
    public static DerivedRootKey createDerivedKey(@NonNull String password, @NonNull String regId, @NonNull String userDomain)
            throws UnsupportedEncodingException, NoSuchAlgorithmException {
        byte[] prefixBytes = regId.getBytes("UTF-8");
        byte[] domainBytes = userDomain.getBytes("UTF-8");
        byte[] secretBytes = password.getBytes("UTF-8");
        byte[] separator = {0, 0, 0, 1};
        int length = prefixBytes.length + domainBytes.length + secretBytes.length + DERIVED_KEY_DIVERSIFIER.length + 4;
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(length);

        //Append together prefix, secret, separator and diversifier
        byteStream.write(prefixBytes, 0, prefixBytes.length);
        byteStream.write(domainBytes, 0, domainBytes.length);
        byteStream.write(secretBytes, 0, secretBytes.length);
        byteStream.write(separator, 0, separator.length);
        byteStream.write(DERIVED_KEY_DIVERSIFIER, 0, DERIVED_KEY_DIVERSIFIER.length);
        byte[] data = byteStream.toByteArray();

        MessageDigest sha512Digest = MessageDigest.getInstance("SHA-512");

        //Create a SHA512 digest using the combined data
        byte[] keyData = sha512Digest.digest(data);

        //Use the first 32 bytes as the encryption key and the next 32 bytes as the HMAC key
        DerivedRootKey drk = new DerivedRootKey();
        byte[] encryptionKeyBytes = new byte[32];
        byte[] hmacKeyBytes = new byte[32];

        System.arraycopy(keyData, 0, encryptionKeyBytes, 0, 32);
        System.arraycopy(keyData, 32, hmacKeyBytes, 0, 32);
        drk.derivedEncryptionKey = new SecretKeySpec(encryptionKeyBytes, KEYSPEC_AES);
        drk.derivedHmacKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA256");

        return drk;
    }

    /**
     * Encode a byte array to base64
     * @param bytes the bytes to encode
     * @return base64 encoded string
     */
    private static String toBase64(@NonNull byte[] bytes) {
        if (bytes.length > 0) {
            return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        }
        return "";
    }

    /**
     * Decode a base64 encoded string into a byte array
     * @param s the string to decode
     * @return decoded byte
     */
    private static byte[] fromBase64(@NonNull String s) {
        if (s.length() > 0) {
            return Base64.decode(s, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        }
        return new byte[]{};
    }

}
