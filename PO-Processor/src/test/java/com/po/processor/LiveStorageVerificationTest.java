package com.po.processor;

import com.po.processor.storage.LocalFilesystemStorageReader;
import com.po.processor.storage.StorageObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Live Storage Integration Verification Test")
public class LiveStorageVerificationTest {

    private final String storagePath = Paths.get("../backend/storage").toAbsolutePath().normalize().toString();

    @Test
    @DisplayName("Verify reading real email body and attachment without mutation")
    void testRealStorageAccess() throws Exception {
        LocalFilesystemStorageReader reader = new LocalFilesystemStorageReader(storagePath);

        // 1. Read real email body
        String bodyRef = "emails/080390dc-5284-4ead-8ea7-b061ea6bdf81/f0e15baf-04d5-4e1d-8925-ffbd6355f813/00a57071-1cbe-4938-ab5d-661453fb54cc/body.txt";
        assertTrue(reader.exists(bodyRef), "Real email body must exist");

        Path bodyDiskPath = reader.resolveSafePath(bodyRef);
        byte[] originalBodyBytes = Files.readAllBytes(bodyDiskPath);
        String bodyText = reader.readText(bodyRef);

        assertNotNull(bodyText);
        assertFalse(bodyText.isBlank());
        assertEquals(originalBodyBytes.length, bodyText.getBytes().length);

        // 2. Read real attachment via streaming
        String attRef = "attachments/29e718b2-c2c8-4e48-853c-48d180a1732a/877a0bd2-3384-42cc-9ba3-d4d4ae5cec49/394dbe58-f248-4c88-bac1-ccae07445a2a/invoice-99.pdf";
        assertTrue(reader.exists(attRef), "Real attachment file must exist");

        Path attDiskPath = reader.resolveSafePath(attRef);
        String hashBefore = calculateSha256(attDiskPath);

        StorageObject streamObject = reader.getObject(attRef);
        assertNotNull(streamObject);
        try (InputStream stream = streamObject.inputStream()) {
            byte[] bytesRead = stream.readAllBytes();
            assertEquals(26, bytesRead.length);
        }

        // Verify original file was not mutated
        String hashAfter = calculateSha256(attDiskPath);
        assertEquals(hashBefore, hashAfter, "Original attachment must remain untouched and unmutated");
    }

    private String calculateSha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(Files.readAllBytes(file));
        return HexFormat.of().formatHex(hash);
    }
}
