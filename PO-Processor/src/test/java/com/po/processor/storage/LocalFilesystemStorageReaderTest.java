package com.po.processor.storage;

import com.po.processor.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Storage Reader Tests")
class LocalFilesystemStorageReaderTest {

    @TempDir
    Path tempStorageDir;

    private LocalFilesystemStorageReader storageReader;

    @BeforeEach
    void setUp() {
        storageReader = new LocalFilesystemStorageReader(tempStorageDir.toString());
    }

    @Test
    @DisplayName("Should read existing file content accurately")
    void testReadExistingFile() throws IOException {
        Path testFile = tempStorageDir.resolve("emails/tenant-1/mailbox-1/msg-1/body.txt");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, "Hello, this is a test PO email body.", StandardCharsets.UTF_8);

        String relativeRef = "emails/tenant-1/mailbox-1/msg-1/body.txt";
        assertTrue(storageReader.exists(relativeRef));

        String content = storageReader.readText(relativeRef);
        assertEquals("Hello, this is a test PO email body.", content);
    }

    @Test
    @DisplayName("Should stream object bytes without error")
    void testStreamObject() throws IOException {
        Path attachmentPath = tempStorageDir.resolve("attachments/tenant-1/msg-1/att-1/content");
        Files.createDirectories(attachmentPath.getParent());
        byte[] expectedBytes = "%PDF-1.4 sample PDF bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(attachmentPath, expectedBytes);

        String relativeRef = "attachments/tenant-1/msg-1/att-1/content";
        StorageObject storageObject = storageReader.getObject(relativeRef);

        assertNotNull(storageObject);
        assertEquals(expectedBytes.length, storageObject.contentLength());

        try (InputStream stream = storageObject.inputStream()) {
            byte[] actualBytes = stream.readAllBytes();
            assertEquals(new String(expectedBytes, StandardCharsets.UTF_8), new String(actualBytes, StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("Should return false for non-existent storage references")
    void testMissingFile() {
        assertFalse(storageReader.exists("emails/non-existent/body.txt"));
        assertThrows(StorageException.class, () -> storageReader.getObject("emails/non-existent/body.txt"));
        assertThrows(StorageException.class, () -> storageReader.readText("emails/non-existent/body.txt"));
    }

    @Test
    @DisplayName("Should strictly reject path traversal attempts")
    void testPathTraversalRejection() {
        assertThrows(StorageException.class, () -> storageReader.resolveSafePath("../../../etc/passwd"));
        assertThrows(StorageException.class, () -> storageReader.resolveSafePath("..\\..\\windows\\win.ini"));
        assertThrows(StorageException.class, () -> storageReader.resolveSafePath("emails/../../secret.txt"));
    }
}
