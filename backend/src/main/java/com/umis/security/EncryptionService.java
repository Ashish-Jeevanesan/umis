package com.umis.security;

import com.umis.config.UmisProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for AES-256-GCM envelope encryption of sensitive mailbox credentials.
 */
@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    private final SecretKeySpec primaryKeySpec;
    private final SecretKeySpec previousKeySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionService(UmisProperties properties) {
        String strPrimary = properties.getSecurity().getEncryptionKey();
        byte[] primaryBytes = derive256BitKey(strPrimary);
        this.primaryKeySpec = new SecretKeySpec(primaryBytes, "AES");

        String strPrevious = properties.getSecurity().getPreviousEncryptionKey();
        if (strPrevious != null && !strPrevious.isBlank()) {
            byte[] previousBytes = derive256BitKey(strPrevious);
            this.previousKeySpec = new SecretKeySpec(previousBytes, "AES");
        } else {
            this.previousKeySpec = null;
        }
    }

    /**
     * Encrypts plaintext string using AES-GCM and prepends IV.
     *
     * @param strPlainText raw sensitive text
     * @return Base64 encoded payload with IV and ciphertext prefixed by key version
     */
    public String encrypt(String strPlainText) {
        if (strPlainText == null || strPlainText.isEmpty()) {
            return null;
        }
        try {
            byte[] ivBytes = new byte[IV_LENGTH_BYTE];
            secureRandom.nextBytes(ivBytes);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, ivBytes);
            cipher.init(Cipher.ENCRYPT_MODE, primaryKeySpec, parameterSpec);

            byte[] cipherText = cipher.doFinal(strPlainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer byteBuffer = ByteBuffer.allocate(ivBytes.length + cipherText.length);
            byteBuffer.put(ivBytes);
            byteBuffer.put(cipherText);

            return "v1:" + Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encrypt sensitive data", exception);
        }
    }

    /**
     * Decrypts ciphertext using AES-GCM, falling back to previous encryption key if needed.
     *
     * @param strEncryptedText Base64 encoded ciphertext (optionally version-prefixed)
     * @return raw decrypted plaintext
     */
    public String decrypt(String strEncryptedText) {
        if (strEncryptedText == null || strEncryptedText.isEmpty()) {
            return null;
        }

        String strPayload = strEncryptedText.startsWith("v1:")
                ? strEncryptedText.substring(3)
                : strEncryptedText;

        try {
            return decryptWithKey(strPayload, primaryKeySpec);
        } catch (Exception primaryException) {
            if (previousKeySpec != null) {
                try {
                    return decryptWithKey(strPayload, previousKeySpec);
                } catch (Exception previousException) {
                    throw new IllegalStateException("Decryption failed with both primary and previous keys");
                }
            }
            throw new IllegalStateException("Failed to decrypt sensitive data: " + primaryException.getMessage());
        }
    }

    private String decryptWithKey(String strEncoded, SecretKeySpec keySpec) throws Exception {
        byte[] decodedBytes = Base64.getDecoder().decode(strEncoded);
        ByteBuffer byteBuffer = ByteBuffer.wrap(decodedBytes);

        byte[] ivBytes = new byte[IV_LENGTH_BYTE];
        byteBuffer.get(ivBytes);

        byte[] cipherBytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherBytes);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, ivBytes);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

        byte[] plainBytes = cipher.doFinal(cipherBytes);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    private byte[] derive256BitKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm missing", exception);
        }
    }
}
