package com.umis.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Cryptographic hash utilities for email deduplication and attachment verification.
 */
public final class HashUtils {

    private HashUtils() {
        // Utility class
    }

    /**
     * Computes the SHA-256 hex string for a given text.
     *
     * @param input raw input string
     * @return hex encoded SHA-256 digest
     */
    public static String sha256Hex(String input) {
        if (input == null) {
            return "";
        }
        return sha256Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes the SHA-256 hex string for given bytes.
     *
     * @param bytes raw byte array
     * @return hex encoded SHA-256 digest
     */
    public static String sha256Hex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(bytes);
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }
}
